# Set up Scala
https://hortonworks.com/tutorial/setting-up-a-spark-development-environment-with-scala/

# Steps
- DONE: Gather tweets data and store in some files
    - Could get some more
- DONE: Text processing
- DONE: Filtering
- SKIP: Classification of tweets according to their relevance:
    - For Twitter seems like MOST of the work is already done with the hashtags / keywords
    - Further classification / I don't know
- DONE: Setup Spark Scala 
    - DONE: Probably install on my computer for dev
- Infrastructure - HARD
    - https://aws.amazon.com/solutions/real-time-analytics-spark-streaming/
        - HUGE
    - Just run the provided Stream clustering algorithm from SPark
    - Run Spark code on AWS EMR to cluster, output
- Setup Threat Intel Dashboard using open-source tools - COOL
    - Publish
    - Get Freelance jobs
    
# Extra
- Check out https://github.com/HazyResearch/snorkel VERY Useful
- Custom Stream Clustering:
   - Implement my algorithm in Spark Scala

# Lesssons
- AWS Kinesis Stream: ingest data stream
- DynamoDB: key-value and document database for low-latency data access at any scale, Billions of request / second
- VPC: Is the networking layer for EC2
    - NAT device can be used to enable instances in a private subnet to connect to the Internet, but prevent
        the Internet from initiating connections with the instances. Forwards traffic from the instances in the 
        private subnet to the Internet or other AWS services, and then send the responses back to the instances
    - If a subnet traffic is routed to an Internet gateway -> Public subnet
    - If a subnet doesn't have a route to an Internet gateway -> Private subnet
    - VPC Endpoint enables you to privately connect your VPC to supported AWS services. Instances in your
        VPC do not require public IP addresses to communicate with resources in the service. Traffic between
        VPC and other service does not leave the Amazon network
        - Interface endpoint: support EC2 (my use case)
        - Gateway endpointL support S3, DynamoDB