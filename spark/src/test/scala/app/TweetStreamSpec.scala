package app

import org.scalatest._
import TweetStream._
import DataStoreUtils.Cluster
import org.apache.spark.mllib.clustering.StreamingKMeans
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.streaming.{Clock, Seconds, StreamingContext}
import org.apache.spark.{FixedClock, SparkConf, SparkContext}
import org.scalatest.concurrent.Eventually

import scala.collection.mutable


class TweetStreamSpec extends WordSpec with MustMatchers with BeforeAndAfter with Eventually {

  private var sc: SparkContext = _
  private var ssc: StreamingContext = _
  private var fixedClock: FixedClock = _
  private var spark : SparkSession = _
  private val kmeans_params = Map[String, Double](
    "num_feats" -> 200.0,
    "K" -> 2.0,
    "decayFactor" -> 0.5,
    "weight" -> 0.0,
    "seed" -> 0.0
  )
  val K : Int = kmeans_params.getOrElse("K", 2.0).toInt
  val decayFactor : Double = kmeans_params.getOrElse("decayFactory", 0.5)
  val numFeats : Int = kmeans_params.getOrElse("num_feats", 200.0).toInt
  val weight : Double = kmeans_params.getOrElse("weight", 0.0)
  val seed : Int = kmeans_params.getOrElse("seed", 0.0).toInt

  before {
    val conf = new SparkConf()
                    .setAppName("unit-testing")
                    .setMaster("local")
                    .set("spark.streaming.clock", "org.apache.spark.FixedClock")
    ssc = new StreamingContext(conf, Seconds(1))
    sc = ssc.sparkContext
    sc.setLogLevel("ERROR")
    fixedClock = Clock.getFixedClock(ssc)
    spark = SparkSession
      .builder()
      .appName("unit-testing")
      .config(conf)
      .getOrCreate()
  }

  after {
    if (ssc != null) {
      ssc.stop(stopSparkContext = true, stopGracefully = false)
    }
  }

  def waitsec(secs: Int): Unit = {
    fixedClock.addTime(Seconds(secs))
  }

  def tweetProcessHelper(input: List[String], expected: List[String]) = {
    val inputRDD: RDD[String] = sc.parallelize(input)
    val res : List[String] = processTweet(inputRDD, spark).select("processed_tweet")
                                              .collect().toList.map(_(0).toString)
    res.size must be > 0
    res must contain theSameElementsInOrderAs expected
  }

  "processTweet op" should {
    "process tweets correctly" in {
      val process_expected = List[String](
        "microsoft dynamicciso among iot adopters 88 percent believe critical business " +
          "success will see 30 percent roi inclusive cost savings efficiencies two " +
          "years now nearly iot adopters security concerns hindering adoption dynamicciso " +
          "dynamiccio slash slash tco slash nkkyrowhwk",
        "dhsgov issued security alert small planes warning modern flight systems " +
          "vulnerable hacking someone manages gain physical access aircraft hackers " +
          "cybersecurity aviation cyberthreat criticalinfrastructure")
      val df = spark.read.format("csv").option("header", true).load("src/main/resources/test1.csv")
      val lines1 = df.collect().toList.map(_(0).toString)
      tweetProcessHelper(lines1, process_expected)
    }
  }

  // https://blog.ippon.tech/testing-strategy-for-spark-streaming/
  "streamCluter op" should {
    "stream kmeans" in {
      val df = spark.read.format("csv").option("header", true).load("src/main/resources/test2.csv")
      val lines2 = df.collect().toList.map(_(0).toString)
      //
      val model = new StreamingKMeans()
        .setK(K)
        .setDecayFactor(decayFactor)
        .setRandomCenters(numFeats, weight, seed=seed)
      //
      // Make input / output stream
      val inputData: mutable.Queue[RDD[String]] = mutable.Queue()
      val inputStream = ssc.queueStream(inputData)
      processTweetStream(
        inputStream,
        1,
        1,
        model,
        numFeats,
        spark,
        {a : Array[Cluster] => a.length must be > 0}
      )
      //
      ssc.start()
      //
      inputData += sc.parallelize(lines2)
      waitsec(5)
    }
  }
}
