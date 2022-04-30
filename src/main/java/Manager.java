import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static EC2.EC2.createEC2Instance;
import static EC2.EC2.terminateEC2;
import static SQS.SQS.receiveMessages;

class WorkersStatusChecker extends Thread {
    private boolean terminate_checker = false;
    private Ec2Client ec2;

//    checks for stopped workers, terminates them and creates new workers instead of them.
    public WorkersStatusChecker(Ec2Client ec2) {
        this.ec2 = ec2;
    }

    public void run() {
        while (terminate_checker == false){
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String nextToken = null;
            try {
                do {
                    DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                    DescribeInstancesResponse response = ec2.describeInstances(request);

                    for (Reservation reservation : response.reservations()) {
                        for (Instance instance : reservation.instances()) {
                            if (!checkIfEC2Tagged(instance.instanceId(), "role", "worker"))
                                continue;
                            if (instance.state().name().toString().equals("terminated")){
                                continue;
                            }
                            if (instance.state().name().toString().equals("stopped") || instance.state().name().toString().equals("stopping")){
                                terminateEC2(ec2, instance.instanceId());
                                createEC2Instance(ec2, "worker", "ami-04505e74c0741db8d", "role", "worker", "worker-jar-v1");
                            }
                        }
                    }
                    nextToken = response.nextToken();
                } while (nextToken != null);

            } catch (Ec2Exception e) {
                System.err.println(e.awsErrorDetails().errorMessage());
                System.exit(1);
            }
        }
    }

    public boolean checkIfEC2Tagged(String resourceId, String key, String value) {
        try {
            Filter filter = Filter.builder()
                    .name("resource-id")
                    .values(resourceId)
                    .build();

            DescribeTagsResponse describeTagsResponse = ec2.describeTags(DescribeTagsRequest.builder().filters(filter).build());
            List<TagDescription> tags = describeTagsResponse.tags();
            for (TagDescription tag: tags) {
                if(tag.key().toLowerCase().equals(key) && tag.value().toLowerCase().equals(value))
                    return true;
            }
            return false;

        } catch ( Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return false;
    }

    public void terminate_checker(){
        this.terminate_checker = true;
    }

}

class LocalAppHandler extends Thread {
    private Message msg;
    private Manager manager;
    private S3Client s3;
    private SqsClient sqsClient;
    private String queueManagerToLocalApps;
    private String queueManagerToWorkers;
    private String queueWorkersToManager;
    private Ec2Client ec2;
    private int MAX_INSTANCES = 11;
//    private Region region = Region.US_WEST_2;
    private Region region = Region.US_EAST_1;
    private Map<String, Boolean> is_url_processed = new HashMap<>();
    public LocalAppHandler(Message msg, Manager manager, S3Client s3, SqsClient sqsClient, Ec2Client ec2,
                           String queueManagerToLocalApps, String queueManagerToWorkers, String queueWorkersToManager){
        this.msg = msg;
        this.manager = manager;
        this.s3 = s3;
        this.sqsClient = sqsClient;
        this.queueManagerToLocalApps = queueManagerToLocalApps;
        this.queueManagerToWorkers = queueManagerToWorkers;
        this.queueWorkersToManager = queueWorkersToManager;
        this.ec2 = ec2;
    }
    public void run() {
        System.out.println("LocalAppHandler running");
        String msg_content = msg.body();
        if (msg_content.toLowerCase().contains("terminate")){
            System.out.println("LocalAppHandler sends terminate msg");
            manager.terminate();
            return;
        }
        String input_file_bucket = msg_content.split("\\t")[0];
        int n = Integer.parseInt(msg_content.split("\\t")[1]); // workers’ files ratio (max files per worker)
//        int n = 5;

        System.out.println("LocalAppHandler Downloading input file from S3 bucket: " + msg_content);
        String res_content = get_content_from_bucket(input_file_bucket);
        System.out.println("LocalAppHandler: input file contains:\n" + res_content);

//        Creates an SQS message for each URL in the input file together with the operation
//        that should be performed on it
        String lines[] = res_content.split("\\r?\\n");
        for (String line : lines){
            SQS.SQS.sendMessage(sqsClient, queueManagerToWorkers, line);
//            is_url_processed.put(line.split("\\t")[1], false);
            is_url_processed.put(line, false); //in the form: <analysis_type>\t<text_url>

        }

        int running_workers_counter = get_running_workers_count_and_terminate_stopped_instances();

        int workers_needed_for_file = (int) Math.ceil( Double.valueOf(lines.length) / Double.valueOf(n));
        int count_of_additional_workers_to_run = workers_needed_for_file - running_workers_counter;

        if (count_of_additional_workers_to_run > 0){
            if (running_workers_counter <= MAX_INSTANCES) { // AWS restriction
                create_additional_workers(count_of_additional_workers_to_run);
            }
        }

//        waiting for workers to complete their job
        System.out.println("LocalAppHandler: waiting for workers to complete their job");
        boolean is_all_urls_processed = false;
        int url_processed_counter = 0;
        String output = "";
        while(is_all_urls_processed == false){
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            List<Message> messages = receiveMessages(sqsClient, queueWorkersToManager, 10);
            for (Message msg : messages){
                String[] msg_splitted = msg.body().split("\\t");
                String bucket_of_analysis_output = msg_splitted[0];
                String analysis_type = msg_splitted[1];
                String url_input_file = msg_splitted[2];
                if (is_url_processed.keySet().contains(analysis_type + "\t" + url_input_file)
                            && is_url_processed.get(analysis_type + "\t" + url_input_file) == false){
                    is_url_processed.put(analysis_type + "\t" + url_input_file, true);
                    deleteOneMessage(sqsClient, queueWorkersToManager, msg);
                    url_processed_counter ++;
                    String link_to_output_analyzed_file = "";
                    if (bucket_of_analysis_output.toLowerCase().contains("exception:"))
                        link_to_output_analyzed_file = bucket_of_analysis_output;
                    else
                        link_to_output_analyzed_file = get_content_from_bucket(bucket_of_analysis_output);
                    output += analysis_type + "\t" + ":\t" + url_input_file + "\t" + link_to_output_analyzed_file + "\n";
                }
                if (url_processed_counter == is_url_processed.keySet().size()){
                    is_all_urls_processed = true;
                    break;
                }
            }
            System.out.println("LocalAppHandler: ...");
        }

//        uploading output to S3
        String bucket_of_response = "bucket" + System.currentTimeMillis();
        String key = "key";
        tutorialSetup(bucket_of_response);
        s3.putObject(PutObjectRequest.builder().bucket(bucket_of_response).key(key)
                        .build(),
                RequestBody.fromString(output));

//        send msg to local app via SQS
        SQS.SQS.sendMessage(sqsClient, queueManagerToLocalApps, input_file_bucket + "\t" + bucket_of_response);


    }

    public static void deleteOneMessage(SqsClient sqsClient, String queueUrl,  Message message) {
        System.out.println("\nDelete Message");
        // snippet-start:[sqs.java2.sqs_example.delete_message]

        try {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build();
            sqsClient.deleteMessage(deleteMessageRequest);
        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public void tutorialSetup(String bucketName) {
        try {
//            s3.createBucket(CreateBucketRequest
//                    .builder()
//                    .bucket(bucketName)
//                    .createBucketConfiguration(
//                            CreateBucketConfiguration.builder()
//                                    .locationConstraint(region.id())
//                                    .build())
//                    .build());

//            s3.createBucket(CreateBucketRequest
//                    .builder()
//                    .bucket(bucketName)
//                    .createBucketConfiguration(
//                            CreateBucketConfiguration.builder()
//                                    .build())
//                    .build());

            s3.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(CreateBucketConfiguration.builder().build())
                    .acl(BucketCannedACL.PUBLIC_READ_WRITE).build());

            System.out.println("Creating bucket: " + bucketName);
            s3.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            System.out.println(bucketName +" is ready.");
            System.out.printf("%n");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    private void create_additional_workers(int count_of_additional_workers_to_run) {
        for (int i=0; i < count_of_additional_workers_to_run; i++){
            System.out.println("LocalAppHandler: Creating a worker...");
//            createEC2Instance(ec2, "worker", "ami-0688ba7eeeeefe3cd", "role", "worker");
            createEC2Instance(ec2, "worker", "ami-04505e74c0741db8d", "role", "worker", "worker-jar-v1");
            System.out.println("worker created.");
        }

    }

    private int get_running_workers_count_and_terminate_stopped_instances(){
        int running_workers_counter = 0;
        String nextToken = null;
        try {
            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                System.out.println("describeEC2 worker Instances:");
                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        if (checkIfEC2Tagged(instance.instanceId(), "role", "worker")){
                            System.out.println("worker: Instance Id is " + instance.instanceId());
                            System.out.println("Instance state name is "+  instance.state().name());
                            if (instance.state().name().toString().equals("terminated"))
                                continue;
                            if (instance.state().name().toString().equals("stopped") || instance.state().name().toString().equals("stopping")){
                                System.out.print("terminating a stopped worker...");
                                terminateEC2(ec2, instance.instanceId());
                                System.out.println("a stopped worker has been terminated.");
                                continue;
                            }
                            running_workers_counter ++; //case: instance is running
                        }
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return running_workers_counter;
    }

    public static void terminateEC2( Ec2Client ec2, String instanceID) {

        try{
            TerminateInstancesRequest ti = TerminateInstancesRequest.builder()
                    .instanceIds(instanceID)
                    .build();

            TerminateInstancesResponse response = ec2.terminateInstances(ti);
            List<InstanceStateChange> list = response.terminatingInstances();

            for (int i = 0; i < list.size(); i++) {
                InstanceStateChange sc = (list.get(i));
                System.out.println("The ID of the terminated instance is "+sc.instanceId());
            }
        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public boolean checkIfEC2Tagged(String resourceId, String key, String value) {

        try {

            Filter filter = Filter.builder()
                    .name("resource-id")
                    .values(resourceId)
                    .build();

            DescribeTagsResponse describeTagsResponse = ec2.describeTags(DescribeTagsRequest.builder().filters(filter).build());
            List<TagDescription> tags = describeTagsResponse.tags();
            for (TagDescription tag: tags) {
                if(tag.key().toLowerCase().equals(key) && tag.value().toLowerCase().equals(value))
                    return true;
            }
            return false;

        } catch ( Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return false;
    }

    private String get_content_from_bucket(String bucket){
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key("key")
                .build();

        ResponseInputStream<GetObjectResponse> responseInputStream = s3.getObject(getObjectRequest);
        InputStream stream = null;
        String res_content = "";
        try {
            stream = new ByteArrayInputStream(responseInputStream.readAllBytes());
            res_content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res_content;
    }
}


public class Manager {
    public static void main(String[] args) {
        Manager manager = new Manager();
        manager.runner();
    }

    private static boolean terminateManager = false;
    private static String queueLocalAppsToManager = "https://sqs.us-east-1.amazonaws.com/862438553923/queueLocalAppsToManager";
    private String queueManagerToLocalApps = "https://sqs.us-east-1.amazonaws.com/862438553923/queueManagerToLocalApps";
    private String queueManagerToWorkers = "https://sqs.us-east-1.amazonaws.com/862438553923/queueManagerToWorkers";
    private String queueWorkersToManager = "https://sqs.us-east-1.amazonaws.com/862438553923/queueWorkersToManager";


    public void runner() {
//        Region region = Region.US_WEST_2;
        Region region = Region.US_EAST_1;
        S3Client s3 = S3Client.builder().region(region).build();

//        checks for new requests from local apps
        SqsClient sqs = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();

        Ec2Client ec2 = Ec2Client.builder()
                .region(region)
                .build();

        List<Thread> handlers = new ArrayList<>(); //for join threads
        List<Message> handledMessages = new ArrayList<>();
        System.out.println("manager fetching messages from local apps...");
        boolean is_WorkersStatusChecker_running = false;
        WorkersStatusChecker workersStatusChecker = new WorkersStatusChecker(ec2);
        while(terminateManager == false){
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            List<Message> messages = receiveMessages(sqs, queueLocalAppsToManager, 10);
            for (Message msg : messages){
                if (handledMessages.contains(msg))
                    continue;
//                creates a new thread for every local app request
                LocalAppHandler localAppHandler = new LocalAppHandler(msg, this, s3, sqs, ec2, queueManagerToLocalApps, queueManagerToWorkers, queueWorkersToManager);
                System.out.println("manager running a local app handler.");
                localAppHandler.run();
                if (is_WorkersStatusChecker_running == false){
                    workersStatusChecker.run();
                    handlers.add(workersStatusChecker);
                    is_WorkersStatusChecker_running = true;
                }
                handlers.add(localAppHandler);
                deleteOneMessage(sqs, queueLocalAppsToManager, msg);
                handledMessages.add(msg);
            }
            System.out.println("manager: ...");
        }
        //case: manager terminated
        workersStatusChecker.terminate_checker();
//        waits for all LocalAppHandler threads and WorkersStatusChecker thread to finish their jobs
        for (Thread handler : handlers){
            try {
                handler.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
//        terminate all workers
        String active_manager_id = terminate_all_workers(ec2);

//        terminate manager
        terminateEC2(ec2, active_manager_id);

    }

    public static void deleteOneMessage(SqsClient sqsClient, String queueUrl,  Message message) {
        System.out.println("\nDelete Message");
        // snippet-start:[sqs.java2.sqs_example.delete_message]

        try {
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public void terminate(){
        terminateManager = true;
    }

    public static void terminateEC2(Ec2Client ec2, String instanceID) {
        try{
            TerminateInstancesRequest ti = TerminateInstancesRequest.builder()
                    .instanceIds(instanceID)
                    .build();
            TerminateInstancesResponse response = ec2.terminateInstances(ti);
            List<InstanceStateChange> list = response.terminatingInstances();
            for (int i = 0; i < list.size(); i++) {
                InstanceStateChange sc = (list.get(i));
                System.out.println("The ID of the terminated instance is "+sc.instanceId());
            }
        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public String terminate_all_workers(Ec2Client ec2){
        String active_manager_id = "";
        String nextToken = null;
        try {
            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        if (checkIfEC2Tagged(ec2, instance.instanceId(), "role", "manager") &&
                                !instance.state().name().toString().equals("terminated") ){
                            active_manager_id = instance.instanceId();
                            continue;
                        }
                        if (!checkIfEC2Tagged(ec2, instance.instanceId(), "role", "worker") ||
                                instance.state().name().toString().equals("terminated") )
                            continue;
//                        terminate worker
                        terminateEC2(ec2, instance.instanceId());
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return active_manager_id;
    }

    public boolean checkIfEC2Tagged(Ec2Client ec2,  String resourceId, String key, String value) {
        try {
            Filter filter = Filter.builder()
                    .name("resource-id")
                    .values(resourceId)
                    .build();

            DescribeTagsResponse describeTagsResponse = ec2.describeTags(DescribeTagsRequest.builder().filters(filter).build());
            List<TagDescription> tags = describeTagsResponse.tags();
            for (TagDescription tag: tags) {
                if(tag.key().toLowerCase().equals(key) && tag.value().toLowerCase().equals(value))
                    return true;
            }
            return false;

        } catch ( Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return false;
    }
}