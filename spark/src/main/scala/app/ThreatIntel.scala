package app

import java.nio.charset.StandardCharsets

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.mllib.clustering.StreamingKMeans
import org.apache.spark.streaming.pubsub.{PubsubUtils, SparkGCPCredentials}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import TweetStream._
import DataStoreUtils._
import org.apache.spark.sql.{Row, SparkSession}


object ThreatIntel {

  private val tweet_subscription = "tweets-subscription"
  private val kmeans_params = Map[String, Double](
    "num_feats" -> 500.0,
    "K" -> 5.0,
    "decayFactor" -> 0.5,
    "weight" -> 0.0,
    "seed" -> 0.0
  )
  val K : Int = kmeans_params.getOrElse("K", 5.0).toInt
  val decayFactor : Double = kmeans_params.getOrElse("decayFactory", 0.5)
  val numFeats : Int = kmeans_params.getOrElse("num_feats", 500.0).toInt
  val weight : Double = kmeans_params.getOrElse("weight", 0.0)
  val seed : Int = kmeans_params.getOrElse("seed", 0.0).toInt

  def createContext(projectID: String, windowLength : String, slidingInterval: String, checkpointDirectory : String)
  : StreamingContext = {
    //
    val sparkConf = new SparkConf().setAppName("Threat Intelligence Tweet Clustering")
    val ssc = new StreamingContext(sparkConf, Seconds(slidingInterval.toInt))
    val spark = SparkSession
      .builder()
      .appName("Threat Intel")
      .config(sparkConf)
      .getOrCreate()
    val sc = ssc.sparkContext
    // Set the checkpoint directory
    val yarnTags = sparkConf.get("spark.yarn.tags")
    val jobId = yarnTags.split(",").filter(_.startsWith("dataproc_job")).head
    ssc.checkpoint(checkpointDirectory + "/" + jobId)

    // Create stream
    val tweetStream : DStream[String] = PubsubUtils
      .createStream(
        ssc,
        projectID,
        None,
        tweet_subscription,
        SparkGCPCredentials.builder.build(),
        StorageLevel.MEMORY_AND_DISK_SER_2
      ).map(message => new String(message.getData(), StandardCharsets.UTF_8))

    // Streaming KMeans
    val model = new StreamingKMeans()
      .setK(K)
      .setDecayFactor(decayFactor)
      .setRandomCenters(numFeats, weight, seed=seed)

    // Process the stream and save results to DataStore
    processTweetStream(
      tweetStream,
      windowLength.toInt,
      slidingInterval.toInt,
      model,
      numFeats,
      spark,
      saveToDatastore(_, windowLength.toInt)
    )
    //
    ssc
  }

  def main(args: Array[String]) : Unit = {
    if (args.length != 5) {
      System.err.println(
        """
          | Usage: ThreatIntel <projectID> <windowLength> <slidingInterval> <totalRunningTime>
          |
          |     <projectID>: ID of Google Cloud project
          |     <windowLength>: The duration of the window, in seconds
          |     <slidingInterval>: The interval at which the window calculation is performed, in seconds
          |     <totalRunningTime>: Total running time for the application, in minutes. If 0, runs indefinitely until termination.
          |     <checkpointDirectory>: Directory used to store RDD checkpoint data
          |
        """.stripMargin)
      System.exit(1)
    } else {
      val Seq(projectID, windowLength, slidingInterval, totalRunningTime, checkpointDirectory) = args.toSeq

      // Get context
      val ssc = StreamingContext.getOrCreate(checkpointDirectory,
        () => createContext(projectID, windowLength, slidingInterval, checkpointDirectory)
      )

      // Start streaming until we receive explicit termination
      ssc.start()

      // Set termination condition
      if (totalRunningTime.toInt == 0) {
        ssc.awaitTermination()
      } else {
        ssc.awaitTerminationOrTimeout(totalRunningTime.toLong * 1000 * 60)
      }
    }
  }
}
