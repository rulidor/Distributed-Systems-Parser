import EC2.EC2;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static S3.S3.cleanUp;
import static S3.S3.tutorialSetup;
import static EC2.EC2.createEC2Instance;
import static SQS.SQS.receiveMessages;
import static java.lang.Thread.sleep;

public class LocalApp {

    public static void main(String[] args) {
        Region region = Region.US_WEST_2;

        System.out.println("local app running...");

        System.out.println("setting up a bucket for input file...");
        S3Client s3 = S3Client.builder().region(region).build();

        String bucket = "bucket" + System.currentTimeMillis();
        String key = "key";

        tutorialSetup(s3, bucket, region);

        System.out.println("Uploading input file to S3...");

//uploading a file to the bucket
        String fileName = "input-sample.txt";
        String filePath = "" + fileName;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket).key(key).build();

        s3.putObject(request, RequestBody.fromFile(new File(filePath)));

        System.out.println("Upload complete.");


// sending msg in SQS stating the location of the input file on S3
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_WEST_2)
                .build();

//    queue name: queueLocalAppsToManager; url: https://sqs.us-west-2.amazonaws.com/862438553923/queueLocalAppsToManager
//        queue name: queueManagerToLocalApps; url: https://sqs.us-west-2.amazonaws.com/862438553923/queueManagerToLocalApps
        String queueWithManagerUrl = "https://sqs.us-west-2.amazonaws.com/862438553923/queueLocalAppsToManager";
        SQS.SQS.sendMessage(sqsClient, queueWithManagerUrl, bucket);

//checking if there is an active manager
        Ec2Client ec2 = Ec2Client.builder()
                .region(region)
                .build();

        isThereActiveManagerAndActivate(ec2);

        System.out.println("Waiting for manager response...");

//        repeatedly: sleep for 10 seconds and check for new messages in SQS
        String res_bucket = "";
        boolean while_flag = true;
        while(while_flag){
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            List<Message> messages = receiveMessages(sqsClient, "https://sqs.us-west-2.amazonaws.com/862438553923/queueManagerToLocalApps", 5);
            for (Message msg : messages){
                if (msg.body().contains(bucket)){
                    res_bucket = msg.body().split("\t")[1];
                    while_flag = false;
                    break;
                }
            }
            System.out.print("...");
        }
        System.out.println("Got a response from manager. Response bucket name: " + res_bucket);

        System.out.println("Downloading summary file from S3...");
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
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

        System.out.println("Deleting response bucket...");
        cleanUp(s3, bucket, key);
        System.out.println("Deleting completed.");

//        todo: create html output file
        System.out.println("Creates HTML output file...");


//  todo: send a terminate message to the manager if it received terminate as one of
//   its arguments


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
                        if (instance.state().name().toString().equals("stopped") || instance.state().name().toString().equals("stopping")){
                            System.out.print("Starting manager...");
                            StartInstancesRequest start_request = StartInstancesRequest.builder()
                                    .instanceIds(instance.instanceId())
                                    .build();
                            ec2.startInstances(start_request);
                            System.out.println("Manager started.");
                        }
                        System.out.println("There is an active manager.");
                        return;
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        System.out.println("There is no manager instance. Creating one...");
        createEC2Instance(ec2, "manager", "ami-0688ba7eeeeefe3cd", "role", "manager");
        System.out.println("Manager created.");
    }

}
