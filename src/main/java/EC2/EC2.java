package EC2;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.RebootInstancesRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import java.util.List;



import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;


public class EC2 {
    public static void main(String[] args) {
        Region region = Region.US_WEST_2;
        Ec2Client ec2 = Ec2Client.builder()
                .region(region)
                .build();

//        ***createEC2Instance***
//        String name = "xenialLidor";
//        String amiId = "ami-0688ba7eeeeefe3cd";
//        String instanceId = createEC2Instance(ec2,name, amiId, "role", "manager") ;
//        System.out.println("The Amazon EC2 Instance ID is "+instanceId);

//        ***terminateInstance***
        terminateEC2(ec2, "i-0141790a795d538df");
        terminateEC2(ec2, "i-0776a249bb84779ce");

//        stopInstance(ec2, "i-0141790a795d538df");

//        ***DescribeInstances***
        describeEC2Instances(ec2);
//
//        describeEC2Tags(ec2, instanceId);

        ec2.close();

    }

    // for list of available AMIs:
    // https://docs.aws.amazon.com/AWSEC2/latest/WindowsGuide/finding-an-ami.html
    public static String createEC2Instance(Ec2Client ec2,String name, String amiId, String tagKey, String tagValue) {

        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T1_MICRO)
                .maxCount(1)
                .minCount(1)
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest1 = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        tag = Tag.builder()
                .key(tagKey)
                .value(tagValue)
                .build();

        CreateTagsRequest tagRequest2 = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        ec2.createTags(tagRequest1);
        ec2.createTags(tagRequest2);


        try {
            ec2.createTags(tagRequest1);
            ec2.createTags(tagRequest2);
            System.out.printf(
                    "Successfully started EC2 Instance %s based on AMI %s",
                    instanceId, amiId);

            return instanceId;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }

//    todo: fix
//    public static String createEC2ManagerInstance(Ec2Client ec2,String name, String amiId, String tagKey, String tagValue) {
//        IamInstanceProfileSpecification iamInstanceProfile = IamInstanceProfileSpecification.builder()
//                .name("LabInstanceProfile")
//                .build();
//
//        RunInstancesRequest runRequest = RunInstancesRequest.builder()
//                .imageId("ami-0ed9277fb7eb570c9")
//                .instanceType(InstanceType.T1_MICRO)
//                .maxCount(1)
//                .minCount(1)
//                .keyName("Management")
//                .userData(encodedUserData)
//                .securityGroups(securityGroups)
//                .iamInstanceProfile(iamInstanceProfile)
//                .tagSpecifications(myTags)
//                .build();
//
//        RunInstancesResponse response = ec2.runInstances(runRequest);
//        String instanceId = response.instances().get(0).instanceId();
//
//        Tag tag = Tag.builder()
//                .key("Name")
//                .value(name)
//                .build();
//
//        CreateTagsRequest tagRequest1 = CreateTagsRequest.builder()
//                .resources(instanceId)
//                .tags(tag)
//                .build();
//
//        tag = Tag.builder()
//                .key(tagKey)
//                .value(tagValue)
//                .build();
//
//        CreateTagsRequest tagRequest2 = CreateTagsRequest.builder()
//                .resources(instanceId)
//                .tags(tag)
//                .build();
//
//        ec2.createTags(tagRequest1);
//        ec2.createTags(tagRequest2);
//
//
//        try {
//            ec2.createTags(tagRequest1);
//            ec2.createTags(tagRequest2);
//            System.out.printf(
//                    "Successfully started EC2 Instance %s based on AMI %s",
//                    instanceId, amiId);
//
//            return instanceId;
//
//        } catch (Ec2Exception e) {
//            System.err.println(e.awsErrorDetails().errorMessage());
//            System.exit(1);
//        }
//
//        return "";
//    }

    public static void describeEC2Instances( Ec2Client ec2){

        boolean done = false;
        String nextToken = null;

        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                System.out.println("describeEC2Instances:");
                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        System.out.println("***");
                        System.out.println("Instance Id is " + instance.instanceId());
                        System.out.println("Image id is "+  instance.imageId());
                        System.out.println("Instance type is "+  instance.instanceType());
                        System.out.println("Instance state name is "+  instance.state().name());
                        System.out.println("monitoring information is "+  instance.monitoring().state());
                        System.out.println("Instance tags:");
                        describeEC2Tags(ec2, instance.instanceId());
                        System.out.println("***");
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void startInstance(Ec2Client ec2, String instanceId) {

        StartInstancesRequest request = StartInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();

        ec2.startInstances(request);
        System.out.printf("Successfully started instance %s", instanceId);
    }
    // snippet-end:[ec2.java2.start_stop_instance.start]

    // snippet-start:[ec2.java2.start_stop_instance.stop]
    public static void stopInstance(Ec2Client ec2, String instanceId) {

        StopInstancesRequest request = StopInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();

        ec2.stopInstances(request);
        System.out.printf("Successfully stopped instance %s", instanceId);
    }

    public static void rebootEC2Instance(Ec2Client ec2, String instanceId) {

        try {
            RebootInstancesRequest request = RebootInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            ec2.rebootInstances(request);
            System.out.printf(
                    "Successfully rebooted instance %s", instanceId);
        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void describeEC2Tags(Ec2Client ec2,  String resourceId ) {

        try {

            Filter filter = Filter.builder()
                    .name("resource-id")
                    .values(resourceId)
                    .build();

            DescribeTagsResponse describeTagsResponse = ec2.describeTags(DescribeTagsRequest.builder().filters(filter).build());
            List<TagDescription> tags = describeTagsResponse.tags();
            System.out.println("describeEC2Tags:");
            System.out.println("tags list size: " + tags.size());
            for (TagDescription tag: tags) {
                System.out.println("Tag key is: "+tag.key());
                System.out.println("Tag value is: "+tag.value());
            }

        } catch ( Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void findRunningEC2Instances(Ec2Client ec2) {

        try {
            String nextToken = null;

            do {
                Filter filter = Filter.builder()
                        .name("instance-state-name")
                        .values("running")
                        .build();

                DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                        .filters(filter)
                        .build();

                DescribeInstancesResponse response = ec2.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        System.out.printf(
                                "Found Reservation with id %s, " +
                                        "AMI %s, " +
                                        "type %s, " +
                                        "state %s " +
                                        "and monitoring state %s",
                                instance.instanceId(),
                                instance.imageId(),
                                instance.instanceType(),
                                instance.state().name(),
                                instance.monitoring().state());
                        System.out.println("");
                    }
                }
                nextToken = response.nextToken();

            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
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

}
