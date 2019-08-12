export PROJECT=$(gcloud info --format='value(config.project)')
export JAR="spark-streaming-pubsub-demo-0.0.1.jar "
export SPARK_PROPERTIES="spark.dynamicAllocation.enabled=false,spark.streaming.receiver.writeAheadLog.enabled=true"
export ARGUMENTS="$PROJECT 60 20 60 hdfs:///user/spark/checkpoint"

gcloud dataproc jobs submit spark \
    --cluster demo-cluster \
    --async \
    --jar target/$JAR \
    --max-failures-per-hour 10 \
    --properties $SPARK_PROPERTIES \
    -- $ARGUMENTS