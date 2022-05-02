import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketCannedACL;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;

import static S3.S3.tutorialSetup;

public class JarUploader {

    public static void main(String[] args){
        Region region = Region.US_EAST_1;

        System.out.println("uploading jar...");

        System.out.println("setting up a bucket...");
        S3Client s3 = S3Client.builder().region(region).build();

        String bucket = "manager-jar-v2";
//        String bucket = "worker-jar-v1";

        String key = "key";

        tutorialSetup(s3, bucket, region);

        System.out.println("Uploading input file to S3...");

        String fileName = "manager.jar";
        String filePath = "D:\\Users\\rulid\\IdeaProjects\\distributed systems course\\assignment1\\out\\artifacts\\manager\\" + fileName;

//        String fileName = "worker.jar";
//        String filePath = "C:\\Users\\rulid\\Desktop\\" + fileName;


        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket).key(key).acl(ObjectCannedACL.PUBLIC_READ_WRITE).build();

        s3.putObject(request, RequestBody.fromFile(new File(filePath)));

//        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
//                .build(),
//        RequestBody.fromString("Testing!"));

        System.out.println("Upload completed.");

        s3.close();

    }
}
