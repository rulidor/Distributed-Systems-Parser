import org.apache.commons.io.FileUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;


import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static S3.S3.tutorialSetup;
import static SQS.SQS.deleteOneMessage;
import static SQS.SQS.receiveMessages;

// todo: important: include englishECFG in Worker jar!

public class Worker {
    private String queueManagerToWorkers = "https://sqs.us-east-1.amazonaws.com/862438553923/queueManagerToWorkers";
    private String queueWorkersToManager = "https://sqs.us-east-1.amazonaws.com/862438553923/queueWorkersToManager";

    private Region region = Region.US_EAST_1;
    private S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build();
    private SqsClient sqs = SqsClient.builder().region(Region.US_EAST_1).build();
    private Ec2Client ec2 = Ec2Client.builder().region(Region.US_EAST_1).build();

    public static void main (String[] args){
        Worker worker = new Worker();
        worker.runner();
    }

    public void runner() {
        System.out.println("worker started running.");
        System.out.println("worker fetching messages from manager...");

        Message curr_msg = null;
        while(true){
            String parsing_method = "";
            String text_url = "";
            try {
                Thread.sleep(5000);
                List<Message> messages = receiveMessages(sqs, queueManagerToWorkers, 1);
                for (Message msg : messages){
//                Download the text file indicated in the message
                    curr_msg = msg;
                    System.out.println("worker: Download the text file indicated in the message...");
                    parsing_method = msg.body().split("\\t")[0];
                    text_url = msg.body().split("\\t")[1];
                    String input_file_path = "input_" + System.currentTimeMillis() + ".txt";
                    File file = new File(input_file_path);
                    URL url = new URL(text_url);
                    FileUtils.copyURLToFile(url, file);

//                Perform the requested analysis on the file
                    String output_file_path = "output_" + System.currentTimeMillis() + ".txt";
                    String res = Parser.parse_into_file(parsing_method,output_file_path, input_file_path);
                    if (! res.toLowerCase().equals("success")){  //case: exception while parsing
                        System.out.println("worker: exception while parsing: " + res);
                        SQS.SQS.sendMessage(sqs, queueWorkersToManager, "exception: " + res + "\t" + parsing_method + "\t" + text_url);
                        deleteOneMessage(sqs, queueManagerToWorkers, msg);
                        continue;
                    }

//                    Upload the resulting analysis file to S3
                    String bucket = "bucket" + System.currentTimeMillis();
                    String key = "parsedfile";

                    tutorialSetup(s3, bucket, region);

                    System.out.println("Uploading input file to S3...");

//uploading a file to the bucket
                    String fileName = "input-sample.txt";
                    String filePath = "" + fileName;

                    PutObjectRequest request = PutObjectRequest.builder()
                            .bucket(bucket).key(key).build();

                    s3.putObject(request, RequestBody.fromFile(new File(filePath)));

                    System.out.println("Upload complete.");

//                only when finished job or when there is an exception, delete the message
                    deleteOneMessage(sqs, queueManagerToWorkers, msg);
                }

            } catch (Exception e) {
                //todo: If an exception occurs, the worker should recover from it, send a message to the manager of
                // the input message that caused the exception together with a short description of the
                // exception, and continue working on the next message
                System.out.println("worker: exception: " + e.getMessage());
                SQS.SQS.sendMessage(sqs, queueWorkersToManager, "exception: " + e.getMessage() + "\t" + parsing_method + "\t" + text_url);
                if (curr_msg != null)
                    deleteOneMessage(sqs, queueManagerToWorkers, curr_msg);
                continue;
            }


            System.out.println("worker: ...");
        }


    }


}
