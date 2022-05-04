import EC2.EC2;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketCannedACL;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static EC2.EC2.createEC2Instance;
import static S3.S3.*;
import static SQS.SQS.deleteOneMessage;
import static SQS.SQS.receiveMessages;
import static java.lang.Thread.sleep;

public class LocalApp {

    public static void main(String[] args) {
// usage: java -jar localApp.jar input-sample2.txt out1.html 1

//        handling args
        final String USAGE = "\n" +
                "Usage:\n" +
                "   <inputFileName> <outputFileName> <n> [terminate]\n\n" +
                "Where:\n" +
                "   inputFileName - name of the input file. \n\n" +
                "   outputFileName - name of the output html file. \n\n" +
                "   n - workers files ratio (max files per worker)." +
                "   terminate - indicates that the application should terminate the manager at the end.";

        if (args.length < 3) {
            System.out.println(USAGE);
            System.exit(1);
        }

        int n = 2;
        try {
            n = Integer.parseInt(args[2]);
            if (n <= 0){
                System.out.println("n cannot be <= 0. exiting.");
                System.exit(1);
            }
        }
        catch (NumberFormatException e) {
            System.out.println("n is not a number. exiting.");
            System.exit(1);
        }

        boolean terminate = false;
        if (args.length == 4){
            if (args[3].toLowerCase().equals("terminate")){
                System.out.println("local app received a termination command.");
                terminate = true;
            }
        }

//        end of handling args


//        Region region = Region.US_WEST_2;
        Region region = Region.US_EAST_1;

        System.out.println("local app running...");

//        System.out.println("setting up a bucket for input file...");
        S3Client s3 = S3Client.builder().region(region).build();

        String bucket = "bucket-from-local-apps-to-manager";
//        String bucket = "bucket" + System.currentTimeMillis();
        String key = "key" + System.currentTimeMillis();

//        tutorialSetup(s3, bucket, region);

        System.out.println("Uploading input file to S3...");

//uploading a file to the bucket
        String inputFilePath = args[0];

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket).acl(String.valueOf(BucketCannedACL.PUBLIC_READ_WRITE)).key(key).build();

        s3.putObject(request, RequestBody.fromFile(new File(inputFilePath)));

        System.out.println("Upload complete.");


// sending msg in SQS stating the location of the input file on S3
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();

        String queueLocalAppsToManager = "https://sqs.us-east-1.amazonaws.com/862438553923/queueLocalAppsToManager";
        SQS.SQS.sendMessage(sqsClient, queueLocalAppsToManager, key + "\t" + n);





//checking if there is an active manager
        Ec2Client ec2 = Ec2Client.builder()
                .region(region)
                .build();

        isThereActiveManagerAndActivate(ec2);

        System.out.println("Waiting for manager response...");

        String bucket_from_manager = "bucket-from-manager-to-local-apps";

//        repeatedly: sleep for 10 seconds and check for new messages in SQS
        String res_key = "";
        boolean while_flag = true;
        while(while_flag){
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String queueManagerToLocalApps = "https://sqs.us-east-1.amazonaws.com/862438553923/queueManagerToLocalApps";
            List<Message> messages = receiveMessages(sqsClient, queueManagerToLocalApps, 5);
//            msg template from manager to local app:
//            "<local app bucket>\t<manager output bucket>"
            for (Message msg : messages){
                if (msg.body().contains(key)){
                    res_key = msg.body().split("\\t")[1];
                    deleteOneMessage(sqsClient, queueManagerToLocalApps, msg);
                    while_flag = false;
                    break;
                }
            }
            System.out.print("...");
        }
        System.out.println("Got a response from manager. Response key: " + res_key);

        System.out.println("Downloading summary file from S3...");
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket_from_manager)
                .key(res_key)
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

        System.out.println("Manager response content:");
        System.out.println(res_content);

        System.out.println("Deleting response key from bucket...");
        deleteBucketObjects(s3, bucket_from_manager, res_key);
        System.out.println("Deleting completed.");

//        create html output file
        System.out.println("Creates HTML output file...");
        try {
            String outputFilePath = args[1];
            if (!outputFilePath.toLowerCase().contains(".html"))
                outputFilePath += ".html";
            File myObj = new File(outputFilePath);
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
                FileWriter myWriter = new FileWriter(outputFilePath);
                myWriter.write("<html><head><title>output file</title></head><body>" + res_content + "</body></html>");
                myWriter.close();
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.out.println("An error occurred while creating output file:");
            e.printStackTrace();
        }

        if (terminate == true){
            System.out.println("Local app sending a terminate message to manager...");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            SQS.SQS.sendMessage(sqsClient, queueLocalAppsToManager, "terminate");
            System.out.println("Terminate message sent.");

        }

        System.out.println("Local app finished - exits.");
    }

    public static boolean checkIfEC2Tagged(Ec2Client ec2,  String resourceId, String key, String value) {
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

    public static void isThereActiveManagerAndActivate(Ec2Client ec2){
        String nextToken = null;

        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                System.out.println("describeEC2Instances:");
                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        if (!checkIfEC2Tagged(ec2, instance.instanceId(), "role", "manager"))
                            continue;
                        System.out.println("Manager: Instance Id is " + instance.instanceId());
                        System.out.println("Instance state name is "+  instance.state().name());
                        if ( instance.state().name().toString().equals("pending") || instance.state().name().toString().equals("running")){
                            System.out.println("There is an active manager.");
                            return;
                        }
//                        if (instance.state().name().toString().equals("stopped") || instance.state().name().toString().equals("stopping")){
//                            System.out.print("Starting manager...");
//                            StartInstancesRequest start_request = StartInstancesRequest.builder()
//                                    .instanceIds(instance.instanceId())
//                                    .build();
//                            ec2.startInstances(start_request);
//                            System.out.println("Manager started.");
//                            return;
//                        }
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        System.out.println("There is no manager instance. Creating one...");
        createEC2Instance(ec2, "manager", "ami-04505e74c0741db8d", "role", "manager", "manager-jar-v2");
        System.out.println("Manager created.");
    }

}
