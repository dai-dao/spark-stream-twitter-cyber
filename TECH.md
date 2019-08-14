# TODO
- Hook everything together
    - Set up cloud and publish the jar
        - Just test that the jar does NOT crash and log is being written
        - THEN hook up the tweets
    - Can check the results on Spark logs and DataStore without turning on the http function
- Tweets generator: Run on a VM instance with service account to publish to Pub/Sub, otherwise no messages will go through
    - Cloud shell is fine for tesing
    - Write my OWN script and hook up with real Twitter feeds
- (Extra) Adjust the results and UI on threat intel platform

# Lessons
- Should create the repo at the VERY beginning
- Spark / Scala is complex, needed lots of time to do it the right way
- Should keep a list of tasks accomplished for task logging purposes, and to refer in the future

# Extra
- Check out https://github.com/HazyResearch/snorkel VERY Useful

# Set up Scala
https://hortonworks.com/tutorial/setting-up-a-spark-development-environment-with-scala/

# Maven
- ManifestResourceTransformer
    - Allows existing entries in the MANIFEST to be replaced and new entries to be added
    - Main class, compile source, compile target, etc ...
- Relocation
    - if the Uber JAR is reused as a dependency of some other projects, directly including classes from the artifact's dependencies in the Uber JAR can cause class loading conflicts due to duplicate classes on the class path. To address this, one can relocate the classes which get included in the shaded artifact in order to create a private copy of their bytecode

# GCP
- https://cloud.google.com/solutions/using-apache-spark-dstreams-with-dataproc-and-pubsub
- Steps
    - Tweet Generator generates tweets and publish to Cloud Pub/Sub topic
    - Spark Streaming app subscribes to that topic for pull deliveries
    - At the end of each sliding window, Spark streaming app saves the latest streaming hashtags
        to Cloud DataStore for persistent storage
    - The HTTP Functions read the latest trending hashtags from Cloud DataStore
    - The HTTP functions generates an HTML page that shows trending hashtags, returns the HTML page
        and display to users
- Pub/Sub is pretty COOL
- Service accounts: Is a special account that can be used by services and applications running on CE instance to interact with other GCP APIs. Applications can use service account credentials to authorize themselves to a set of APIs and perform actions
    - Create a service account that the Cloud Dataproc cluster can use
    - Assign the necessary permissions to allow the cluster instances to access Cloud pub/Sub and DataSore
    - Add the Cloud Dataproc worker IAM role to allow the service account to create clusters and run jobs
    - Add the Cloud Datastore user IAM role to allow allow the service account to read and write to the database
    - Add the Cloud Pub/Sub subscriber IAM role to allow the service account to subscribe to the ``tweets-subscription`` Cloud Pub/Sub subscription
- Cloud DataProc cluster
    - Provide the ``pubsub`` and ``datastore`` access scopes in order to allow cluster instances to access the corresponding APIs for Cloud Pub/Sub and Cloud Datastore
    - Provide the service account created earlier. Dataproc assigns this service account to every instance in the cluster so that all the instances get the correct permission
    - cluster image version for Spark version compatibility
- Sliding window mechanism that the Spark streaming app uses
    - Spark streaming app collects pipeline executions of new tweets every from the tweets Cloud Pub/Sub topic every 20 seconds. It processes new tweets together with all tweets that were collected over a 60-second window
    - SO COOL
- Spark app
    - Written in Scala
    - Uses Maven as build tool, and ``pom.xml`` as build config file
    - ``mvn clean package`` to generate the binary jar to upload to CloudDataproc
    - Submit to CloudDataproc, specifying both Spark properties / config and can pass in arguments to Spark script
    - Use HDFS directory to store periodic checkpoint to increase fault tolerance and help avoid data loss
    - ``--max-failures-per-hour`` lets the job restart on potential failures to increase resilience
    - ``spark.dynamicAllocation.enabled=false``, enabled by default. Adjusts the number of executors based on the workload, which is not effective and may cause data loss in a streaming context
    - ``spark.streaming.receiver.writeAheadLog.enabled=true`` enables write ahead logs to increase fault tolerance and to help avoid data loss. All modifications are written to a log before they are applied
- Maven build