# Distributed-Systems-Parser
An AWS application to distributively process a list of text files, analyze them in various levels, upload the analyzed texts to S3 (AWS storage) and generate an output HTML file

### system startup:
in order to run the system you should:
1. Put the jar file named LocalApp.jar in the folder, along with the input file.
2. The local app must be run via the cmd/terminal, using:
###    java -jar LocalApp.jar <inputFileName> <outputFileName> <n>> terminate>
     
### usage:
      inputFileName - name of the input file (with .txt extension).
      outputFileName - name of the output html file. (with .html extension).
      n - workers files ratio (max files per worker).
      terminate - optional parameter, which indicates whether or not the application should terminate the manager at the end.
    
### example run and work flow:    
    The command used to run the localapp:
        java -jar LocalApp.jar input-sample.txt out1.html 1 terminate
    Initial data::
    1. Run 1 local app
    2. There is no active manager
    3. n = 1
    4. Input file:
    A. input-sample.txt, as provided in the model
B. The number of urls in the input file: 9
During system work, the local app produces a manager if there is no active manager.
The manager checks how many workers are required to work on the input file (s), and produces workers accordingly.
Manager, workers jar files are in S3.
The manager and workers download the manager, worker, and run them at the beginning of their instance run.
When a request is received to process an input file from a local app to a manager, the manager assigns a LocalAppHandler which is basically a thread that handles the input file and is responsible for the workers processing the file.
If there is a worker instance whose state has for some reason changed from running to stopped, the manager has a show of WorkerStatuschecker, which is basically a thread responsible for checking these things, and he will terminate the worker and then create a new worker and run it.
We created EC2 instances: one manager and 9 workers:
 
Our EC2 Instances type:
ami-04505e74c0741db8d
Type
T2_SMALL




The EC2 instances communicate using 4 sqs queues:
 
When the files created by the workers and local apps are uploaded to S3 and are, according to their suitability, in one of the following 4 Buckets:
 

The time it took for the output file to be ready: 37 minutes.
The n we used: 1.
The output file contained:
 


At the end of the run and because we used the terminate parameter, the EC2 instances we created are now terminated:
 

Additional points:
1. Scalability - Our system is scalable, as the manager constantly checks for new requests from local apps, and if so, he assigns a new thread to each such request that handles the request.
Persistence - If a worker instance goes from running to stopped, the manager has the thread called WorkerStatuschecker, which once in a while checks exactly these situations, and passes the stopped worker to terminate, and instead produces a new worker .
If there is an exception during the worker's parsing work (whether because the url of the input text is invalid, or if there is a fault in the parser) - the worker does not "fall", but sends the reason for the exception to manager.
3. Running several local apps at the same time - we tried it and indeed it works and the appropriate output files are obtained.
4. Termination process - When the local app sends a terminate message to the manager, the manager:
A. Stops receiving more file processing requests from local apps.
B. Terminates to WorkersStatusChecker (the thread that is responsible for making sure the worker instances are correct and working, and if not - makes them terminate and creates new workers).
third. Waiting for the local apps' requests to be processed by localAppHandlers already created.
D. Terminate operation for all workers.
God. If there are any messages left in the queueWorkersToManager, delete them.
and. Operation termintate to itself.
     
    
   
