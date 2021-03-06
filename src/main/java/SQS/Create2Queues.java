package SQS;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

import static SQS.SQS.*;

public class Create2Queues {

    public static void main(String[] args){

        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();

        // Perform various tasks on the Amazon SQS queue
        // create a new queue
//        String queueName1 = "queueLocalAppsToManager";
//        String queueName2 = "queueManagerToLocalApps";
//        String queueName3 = "queueManagerToWorkers";
//        String queueName4 = "queueWorkersToManager";
////
//        createQueue(sqsClient, queueName1);
//        createQueue(sqsClient, queueName2);
//        createQueue(sqsClient, queueName3);
//        createQueue(sqsClient, queueName4);



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
            for (Message msg : messages){
                System.out.println(msg.body());
            }
//            deleteMessages(sqsClient, url, messages);

            // delete queue by its name
//            deleteSQSQueue(sqsClient, "queueLocalAppAndManager");
//            deleteSQSQueue(sqsClient, "queueWorkersToManager");
//            deleteSQSQueue(sqsClient, "queueLocalAppsToManager");
//
//            deleteSQSQueue(sqsClient, "queueManagerToLocalApps");
//
//            deleteSQSQueue(sqsClient, "queueManagerToWorkers");

        }

        sqsClient.close();
    }


}
