package SQS;


import com.amazonaws.services.sqs.model.DeleteQueueResult;
import com.amazonaws.services.sqs.*;


import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.List;

import static java.lang.Thread.sleep;

public class SQS {

    public static void main(String[] args){

        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();

        // Perform various tasks on the Amazon SQS queue
        // create a new queue
//        String queueName = "testQueue";
//        String queueUrl= createQueue(sqsClient, queueName );

        // get all queues with prefix
        ListQueuesResponse listQueuesResponse = listQueues(sqsClient, "");
        String queue_name = "";
        String[] split_url;
        for (String url : listQueuesResponse.queueUrls()){
            split_url = url.split("/");
            queue_name = split_url[split_url.length-1];
            System.out.println("url: " + url);
            System.out.println("name: " + queue_name);
//            sendMessage(sqsClient, url, "H5");
            List<Message> messages = receiveMessages(sqsClient, url, 10);
            for (Message msg : messages)
                System.out.println(msg.body());

//            delete all messages from queue by its url
//            PurgeQueueRequest purgeQueueRequest = PurgeQueueRequest.builder().queueUrl("https://sqs.us-west-2.amazonaws.com/862438553923/queueLocalAppsToManager").build();
//            sqsClient.purgeQueue(purgeQueueRequest);

            // delete queue by its name
//            deleteSQSQueue(sqsClient, "queueLocalAppAndManager");
        }
//
//        // receive messages from a queue
//        List<Message> messages = receiveMessages(sqsClient, queueUrl);
//
//        // delete messages from a queue
//        deleteMessages(sqsClient, queueUrl,  messages) ;

        sqsClient.close();
    }

    public static void sendMessage(SqsClient sqsClient, String queueUrl, String msg) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(msg)
                .delaySeconds(2)
                .build());
    }

    public static String createQueue(SqsClient sqsClient, String queueName){
        try {
            System.out.println("\nCreate Queue");
            // snippet-start:[sqs.java2.sqs_example.create_queue]

            CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build();

            sqsClient.createQueue(createQueueRequest);
            // snippet-end:[sqs.java2.sqs_example.create_queue]

            System.out.println("\nGet queue url");

            // snippet-start:[sqs.java2.sqs_example.get_queue]
            GetQueueUrlResponse getQueueUrlResponse =
                    sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
            String queueUrl = getQueueUrlResponse.queueUrl();
            return queueUrl;

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return "";
    }

    public static ListQueuesResponse listQueues(SqsClient sqsClient, String prefix) {
        try {
            ListQueuesRequest listQueuesRequest = ListQueuesRequest.builder().queueNamePrefix(prefix).build();
            ListQueuesResponse listQueuesResponse = sqsClient.listQueues(listQueuesRequest);

            return listQueuesResponse;

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return null;
    }

    public static  List<Message> receiveMessages(SqsClient sqsClient, String queueUrl, int messagesNumber) {

        try {
            // snippet-start:[sqs.java2.sqs_example.retrieve_messages]
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(messagesNumber)
                    .build();
            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
            return messages;
        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return null;
        // snippet-end:[sqs.java2.sqs_example.retrieve_messages]
    }

    public static void deleteMessages(SqsClient sqsClient, String queueUrl,  List<Message> messages) {
        System.out.println("\nDelete Messages");
        // snippet-start:[sqs.java2.sqs_example.delete_message]

        try {
            for (Message message : messages) {
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
            }
            // snippet-end:[sqs.java2.sqs_example.delete_message]

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void deleteSQSQueue(SqsClient sqsClient, String queueName) {

        try {

            GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            String queueUrl = sqsClient.getQueueUrl(getQueueRequest).queueUrl();

            DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder()
                    .queueUrl(queueUrl)
                    .build();

            sqsClient.deleteQueue(deleteQueueRequest);

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }
}
