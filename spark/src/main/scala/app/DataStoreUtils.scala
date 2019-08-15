package app

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Datastore, DatastoreOptions, FullEntity, IncompleteKey, KeyFactory, ListValue}

object DataStoreUtils {
  case class Cluster(id : Integer, tweet : String)

  private def convertToEntity(keyFactory: KeyFactory,
                              record: Cluster): FullEntity[IncompleteKey] = {
    FullEntity.newBuilder(keyFactory.newKey())
      .set("cluster_id", record.id.toDouble)
      .set("tweet", record.tweet)
      .build()
  }

  private def convertToListEntity(clusters: Array[Cluster],
                              keyFactory: String => KeyFactory): FullEntity[IncompleteKey] = {
    val clusterKeyFactory: KeyFactory = keyFactory("Cluster")
    val listValue = clusters.foldLeft[ListValue.Builder](ListValue.newBuilder())(
      (listValue, cluster) => listValue.addValue(convertToEntity(clusterKeyFactory, cluster))
    )
    val rowKeyFactory: KeyFactory = keyFactory("TrendingClusters")
    FullEntity.newBuilder(rowKeyFactory.newKey())
      .set("datetime", Timestamp.now())
      .set("clusters", listValue.build())
      .build()
  }

  def saveToDatastore(clusters: Array[Cluster], windowLength: Integer ) = {
    val datastore: Datastore = DatastoreOptions.getDefaultInstance().getService()
    val keyFactoryBuilder = (s: String) => datastore.newKeyFactory().setKind(s)
    // Convert to DataStore entity
    val entity: FullEntity[IncompleteKey] = convertToListEntity(clusters, keyFactoryBuilder)
    // Add to DataStore
    datastore.add(entity)
    // Display logs
    println("\n-------------------------")
    println(s"Window ending ${Timestamp.now()} for the past ${windowLength} seconds\n")
    if (clusters.length == 0) {
      println("No clusters in this window.")
    }
    else {
      println("Clusters in this window:")
      clusters.foreach(cluster => println(s"${cluster.id}, ${cluster.tweet}"))
    }
  }
}
