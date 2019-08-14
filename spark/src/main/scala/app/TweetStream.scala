package app

import java.io.{BufferedReader, InputStreamReader}

import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.{HashingTF, IDF, Tokenizer}
import scala.io.Source
import org.apache.spark.rdd.RDD
import org.apache.spark.ml.linalg.{SparseVector, Vector}
import org.apache.spark.sql.functions._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.mllib.clustering.{StreamingKMeans, StreamingKMeansModel}
import org.apache.spark.sql.types.{DoubleType, IntegerType, StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import app.DataStoreUtils.Cluster
import scala.collection.mutable.ListBuffer


object TweetStream {

  def combineLists[A]( ss : List[A]*) = {
    val sa = ss.reverse; (sa.head.map(List(_)) /: sa.tail)(_.zip(_).map(p=>p._2 :: p._1))
  }

  def readFile(file : String) : List[String] = {
    var lines = new ListBuffer[String]()
    var thisLine: String = null
    val reader = new BufferedReader(new InputStreamReader(getClass.getClassLoader.getResourceAsStream(file)))
    while ({thisLine = reader.readLine(); thisLine != null}) {
      lines += thisLine
    }
    reader.close()
    lines.toList
  }

  def processTweet(input : RDD[String], spark : SparkSession) : DataFrame = {
    val keywords = readFile("keywords.txt")
    val stopwords = readFile("stopwords.txt")
    // Preprocessing
    val res = input.map(_.replace("\n", " ")).
      map(_.replaceAll("[@#.,!?:]", "")).
      map(_.replaceAll("%", " percent")).
      map(_.replaceAll("/", " slash ")).
      map(_.replaceAll("-", " hyphen ").toLowerCase).
      map(_.split(" ").toSeq).
      map(_.map(_.trim)).
      map(_.filter(!_.isEmpty)).
      map(_.filter(!_.startsWith("https"))).
      map(_.filter(a => !stopwords.contains(a))). // Remove stop-words
      map(_.mkString(" "))
    // DataFrame
    val data = input.zip(res).map(t => t match {
      case (a: String, b: String) => Row(a, b)
      }
    )
    val schema2 = new StructType()
      .add(StructField("tweet", StringType, true))
      .add(StructField("processed_tweet", StringType, true))
    // Filter tweets based on keywords
    val df = spark.createDataFrame(data, schema2)
      .filter(col("tweet").rlike("(^|\\s)(" + keywords.mkString("|") + ")(\\s|$)"))
    df
  }

  def tfidf_pipeline(input: DataFrame, num_feats: Int): DataFrame = {
    val tk = new Tokenizer()
      .setInputCol("processed_tweet")
      .setOutputCol("tokens")
    val tf = new HashingTF()
      .setNumFeatures(num_feats)
      .setInputCol("tokens")
      .setOutputCol("hashedTF")
    val idf = new IDF()
      .setInputCol("hashedTF")
      .setOutputCol("TFIDF")
    val pipe = new Pipeline()
      .setStages(Array(tk, tf, idf))
    // TODO: Should train IDF on training data, save and load the model to transform in real production
    val out = pipe.fit(input).transform(input)
    out
  }

  def cluster(model : StreamingKMeansModel, input : DataFrame, spark: SparkSession): RDD[Cluster] = {
    import spark.implicits._
    // TODO: Train model on training dataset, save / load / update model in production setting
    val densevector = input.select("TFIDF").rdd.map(_.getAs[SparseVector]("TFIDF").toDense)
    val vector = densevector.map(a => org.apache.spark.mllib.linalg.Vectors.fromML(a))
    // TRAIN and UPDATE
    model.update(vector, 0.5, "points")
    // Make data Frame and compute cluster distances
    val centers = model.clusterCenters.toList
    val preds = model.predict(vector).collect().toList
    val centers_col = preds.map(a => centers(a))
    val vector_list = vector.collect().toList
    val tweets = input.select("tweet").collect().toList.map(a => a(0).toString)
    val distances = centers_col.zip(vector_list).map(t => t match {
      case (a, b) => Vectors.sqdist(b, a)
    })
    //
    val data = combineLists(tweets, preds, distances)
            .map(t => t match {case List(a: String, b: Integer, c: Double) => (a, b, c)})
    val out = data.toDF("tweets", "preds", "distances")
    // Get tweet with closest distance to each centroid
    out.orderBy("preds", "distances")
      .groupBy("preds").agg(
      min("distances"),
      first(col("tweets"))
    ).rdd.map(
      t => t match {
        case Row(a: Integer, b : Double, c : String) =>
          Cluster(a, c)
      }
    )
  }

  def pipeline(input : RDD[String], num_feats : Int, spark : SparkSession,
               model : StreamingKMeansModel): RDD[Cluster]
  = {
    val df = processTweet(input, spark)
    val feats = tfidf_pipeline(df, num_feats)
    cluster(model, feats, spark)
  }

  def processTweetStream(input: DStream[String],
                         windowLength: Int,
                         slidingInterval: Int,
                         model : StreamingKMeans,
                         num_feats : Int,
                         spark : SparkSession,
                         handler: Array[Cluster] => Unit
                        ) = {
    val clusters = input.window(Seconds(windowLength), Seconds(slidingInterval)).
        transform(pipeline(_, num_feats, spark, model.latestModel()))
    //
    clusters.foreachRDD(rdd => handler(rdd.take(5)))
  }
}
