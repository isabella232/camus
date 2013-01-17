# Intro
Camus is LinkedIn's [Kafka](http://kafka.apache.org "Kafka")->HDFS pipeline. It is a mapreduce job that does distributed data loads out of Kafka. It includes the following features:

* Automatic discovery of topics
* Avro schema management / In progress
* Date partitioning

It is used at LinkedIn where it processes tens of billions of messages per day.

This is a new open source project, so we don't yet have detailed documentation available and there are still a few LinkedIn-specific items we haven't fixed yet. For early adopters, you can get a basic overview from this [Building LinkedIn’s Real-time Activity Data Pipeline](http://sites.computer.org/debull/A12june/pipeline.pdf "Building LinkedIn’s Real-time Activity Data Pipeline"). There is also a google group for discussion that you can email at camus_etl@googlegroups.com, <camus_etl@googlegroups.com> or you can search the [archives](https://groups.google.com/forum/#!forum/camus_etl "Camus Archives"). If you are interested please ask any questions on that mailing list.

# Brief Overview
All work is done within a single Hadoop job divided into three stages:

1. Setup stage fetches available topics and partitions from Zookeeper and the latest offsets from the Kafka Nodes.

2. Hadoop job stage allocates topic pulls among a set number of tasks.  Each task does the following:
*  Fetch events  from Kafka server and collect count statistics..
*  Move data  files to corresponding directories based on time stamps of events.
*  Produce count  events and write to HDFS.  * TODO: Determine status of open sourcing  Kafka Audit.
*  Store updated  offsets in HDFS.

3. Cleanup stage reads counts from all tasks, aggregates the values, and submits the results to Kafka for consumption by Kafka Audit. 

## Setup Stage 

1. Setup stage fetches from Zookeeper Kafka broker urls and topics (in /brokers/id, and /brokers/topics).  This data is transient and will be gone once Kafka server is down.

2. Topic offsets stored in HDFS.  Camus maintains its own status by storing offset for each topic in HDFS. This data is persistent.

3. Setup stage allocates all topics and partitions among a fixed number of tasks.

## Hadoop Stage 

### 1. Pulling the Data 

Each hadoop task uses a list of topic partitions with offsets generated by setup stage as input. It uses them to initialize Kafka requests and fetch events from Kafka brokers. Each task generates four types of outputs (by using a custom MultipleOutputFormat):
Avro data files;
Count statistics files;
Updated offset files.
Error files. * Note, each task generates an error file even if no errors were  encountered.  If no errors occurred, the file is empty.

### 2. Committing the data 

Once a task has successfully completed, all topics pulled are committed to their final output directories. If a task doesn't complete successfully, then none of the output is committed.  This allows the hadoop job to use speculative execution.  Speculative execution happens when a task appears to be running slowly.  In that case the job tracker then schedules the task on a different node and runs both the main task and the speculative task in parallel.  Once one of the tasks completes, the other task is killed.  This prevents a single overloaded hadoop node from slowing down the entire ETL.

### 3. Producing Audit Counts 

Successful tasks also write audit counts to HDFS. 

### 4. Storing the Offsets 

Final offsets are written to HDFS and consumed by the subsequent job.

## Job Cleanup 

Once the hadoop job has completed, the main client reads all the written audit counts and aggregates them.  The aggregated results is then submitted to Kafka.

## Setting up Camus

### First, Create a Custom Kafka Message to Avro Record Decoder

We hope to eventually create a more out of the box solution, but until we get there you will need to create a custom decoder for handling Kafka messages.  You can do this by implementing the abstract class com.linkedin.batch.etl.kafka.coders.KafkaMessageDecoder.  Internally, we use a schema registry that enables obtaining an Avro schema usingan identifier included in the Kafka byte payload. For more information on othe options, you can email camus_etl@googlegroups.com.  Once you have created a decoder, you will need to specify that decoder in the propeties as described below.

### Configuration

Camus can be ran from the command line as Java App. You will need to set some properties either by specifying a properties file on the classpath using -p (filename), or an external properties file using -P (filepath), or from the commandline itself using -D property=value. If the same property is set using more than one of the previously mentioned methods, the order of precedence is command-line, external file, classpath file.

Here is an abbreviated list of commonly used parameters.

* Top-level data output directory, sub-directories will be dynamically created for each topic pulled
 * etl.destination.path=
* HDFS location where you want to keep execution files, i.e. offsets, error logs, and count files
 * etl.execution.base.path=
* Where completed Camus job output directories are kept, usually a sub-dir in the base.path
 * etl.execution.history.path=
* Zookeeper configurations:
 * zookeeper.hosts=
 * zookeeper.broker.topics=/brokers/topics
 * zookeeper.broker.nodes=/brokers/ids
* All files in this dir will be added to the distributed cache and placed on the classpath for hadoop tasks
 * hdfs.default.classpath.dir=
* Max hadoop tasks to use, each task can pull multiple topic partitions
 * mapred.map.tasks=30
* Max historical time that will be pulled from each partition based on event timestamp
 * kafka.max.pull.hrs=1
* Events with a timestamp older than this will be discarded. 
 * kafka.max.historical.days=3
* Max bytes pull for a topic-partition in a single run
 * kafka.max.pull.megabytes.per.topic=4096
* Decoder class for Kafka Messages to Avro Records
 * kafka.message.decoder.class=
* If whitelist has values, only whitelisted topic are pulled.  Nothing on the blacklist is pulled
 * kafka.blacklist.topics=
 * kafka.whitelist.topics=

### Running Camus

Camus can be run from the command line using java jar.  Here is the usage:

usage: CamusJob.java<br/>
 -D <property=value>   use value for given property<br/>
 -P <arg>              external properties filename<br/>
 -p <arg>              properties filename from the classpath<br/>
