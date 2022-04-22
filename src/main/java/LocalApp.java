import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.io.File;

import static S3.S3.tutorialSetup;

public class LocalApp {

    public static void main(String[] args) {

        System.out.println("local app running...");

        System.out.println("setting up a bucket for input file...");
        Region region = Region.US_WEST_2;
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


    }

}
