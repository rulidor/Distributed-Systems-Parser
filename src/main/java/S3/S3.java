package S3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;

import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.S3Client;



public class S3 {

    public static void main(String[] args) throws IOException {
//        Region region = Region.US_WEST_2;
        Region region = Region.US_EAST_1;

        S3Client s3 = S3Client.builder().region(region).build();

//        String bucket = "bucket" + System.currentTimeMillis();
        String key = "key";
//
//        tutorialSetup(s3, bucket, region);
//
//        System.out.println("Uploading object...");

//        ***
//uploading a file to the bucket
//        String fileName = "input-sample.txt";
//        String filePath = "" + fileName;
//
//        PutObjectRequest request = PutObjectRequest.builder()
//                .bucket(bucket).key(key).build();
//
//        s3.putObject(request, RequestBody.fromFile(new File(filePath)));

//        ***

//        inserting text to the bucket
//        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
//                        .build(),
//                RequestBody.fromString("Testing with the {sdk-java}"));

//        System.out.println("Upload complete");
//        System.out.printf("%n");

//        fetching data from bucket
//        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
//                .bucket("test-jar-file-bucket")
//                .key(key)
//                .build();

//        ResponseInputStream<GetObjectResponse> responseInputStream = s3.getObject(getObjectRequest);
//        InputStream stream = new ByteArrayInputStream(responseInputStream.readAllBytes());
//        System.out.println("Content: "+ new String(stream.readAllBytes(), StandardCharsets.UTF_8));



//        cleanUp(s3, bucket, key);

        System.out.println("Closing the connection to {S3.S3}");
        s3.close();
        System.out.println("Connection closed");
        System.out.println("Exiting...");

    }


    public static void tutorialSetup(S3Client s3Client, String bucketName, Region region) {
        try {
//            s3Client.createBucket(CreateBucketRequest
//                    .builder()
//                    .bucket(bucketName)
//                    .createBucketConfiguration(
//                            CreateBucketConfiguration.builder()
//                                    .locationConstraint(region.id())
//                                    .build())
//                    .build());
//            s3Client.createBucket(CreateBucketRequest
//                    .builder()
//                    .bucket(bucketName)
//                    .createBucketConfiguration(
//                            CreateBucketConfiguration.builder()
//                                    .build())
//                    .build());

            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                            .createBucketConfiguration(CreateBucketConfiguration.builder().build())
                    .acl(BucketCannedACL.PUBLIC_READ_WRITE).build());

//            CreateBucketRequest request = CreateBucketRequest.builder()
//                    .bucket(bucketName)
//                    .acl(BucketCannedACL.PUBLIC_READ).build();
//            client.createBucket(request);

            System.out.println("Creating bucket: " + bucketName);
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            System.out.println(bucketName +" is ready.");
            System.out.printf("%n");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void printAllBucketsNames(S3Client s3){
        ListBucketsRequest listBucketsRequest = ListBucketsRequest.builder().build();
        ListBucketsResponse listBucketsResponse = s3.listBuckets(listBucketsRequest);
        listBucketsResponse.buckets().stream().forEach(x -> System.out.println(x.name()));
    }


//    danger - deleting all buckets!
    public static void DeleteAllBuckets(S3Client s3){
        ListBucketsRequest listBucketsRequest = ListBucketsRequest.builder().build();
        ListBucketsResponse listBucketsResponse = s3.listBuckets(listBucketsRequest);
        listBucketsResponse.buckets().stream().forEach(x -> DeleteBucketCompletely(s3, x.name()));
    }


    public static void DeleteBucketCompletely(S3Client s3, String bucket) {
        System.out.println("DeleteBucketCompletely running.");
        try {
            // To delete a bucket, all the objects in the bucket must be deleted first
            ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder().bucket(bucket).build();
            ListObjectsV2Response listObjectsV2Response;

            do {
                listObjectsV2Response = s3.listObjectsV2(listObjectsV2Request);
                for (S3Object s3Object : listObjectsV2Response.contents()) {
                    s3.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Object.key())
                            .build());
                }

                listObjectsV2Request = ListObjectsV2Request.builder().bucket(bucket)
                        .continuationToken(listObjectsV2Response.nextContinuationToken())
                        .build();

            } while(listObjectsV2Response.isTruncated());
            // snippet-end:[s3.java2.s3_bucket_ops.delete_bucket]

            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucket).build();
            s3.deleteBucket(deleteBucketRequest);

        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }


    public static void cleanUp(S3Client s3Client, String bucketName, String keyName) {
        System.out.println("Cleaning up...");
        try {
            System.out.println("Deleting object: " + keyName);
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(keyName).build();
            s3Client.deleteObject(deleteObjectRequest);
            System.out.println(keyName +" has been deleted.");
            System.out.println("Deleting bucket: " + bucketName);
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
            s3Client.deleteBucket(deleteBucketRequest);
            System.out.println(bucketName +" has been deleted.");
            System.out.printf("%n");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        System.out.println("Cleanup complete");
        System.out.printf("%n");
    }

}
