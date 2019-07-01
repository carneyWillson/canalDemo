package com.carney

import com.carney.constant.Global
import org.apache.spark.sql
import org.apache.spark.sql.streaming.Trigger
import com.carney.util.{JsonHelper, SparkHelper}

/**
 * @Description TODO
 * @Author WangJiaLi
 * @Date 2019/6/27 12:07
 * @Version 1.0
 */
object DoMain {

  def main(args: Array[String]): Unit = {

    val spark = SparkHelper.getSparkSession()
    // 设置Listener
    spark.streams.addListener(SparkHelper.getStreamsListener())
    import spark.implicits._

    // 配置kafka, 并从中获取流
    // DataFrame的schma是: key value topic partition offset timestamp timestampType
    val kafkaSource: sql.DataFrame = spark
      // new DataStreamReader
      .readStream
      // 记录了下
      .format("kafka")
      .option("kafka.bootstrap.servers", "node242:9092,node243:9092,node244:9092")
      .option("subscribe", "db_shop,db_shop_dsc")
      // 指定首次运行时(没有过checkpoint时)的偏移量
      .option("startingOffsets", "earliest")
      .load()

    val allTableInfo = kafkaSource
      .selectExpr("CAST(value AS STRING)")
      .as[String]
      // 为了减少文件夹层次, 将表格信息扁平化
      // key是database-table-type
      .flatMap(jsonStr => {
        JsonHelper.readJson(jsonStr)
      })
      .toDF("key", "data")

    import scala.concurrent.duration._
    // 显示到控制台, 用于调试
//    val job1 = allTableInfo
//      .writeStream
//      // "year","month","week","day","hour","minute","second","millisecond","microsecond"
//      .trigger(Trigger.ProcessingTime(10.seconds))
//      .format("console")
//      .option("checkpointLocation", Global.PATH_CHICKPOINT + "kafkaSource")
//      .start()

    // 将数据暂时存储到hdfs
    // 在listener中, 将数据真正落地到hive
    val job2 = allTableInfo
      .writeStream
      // 不支持追加
      // .outputMode("complete")
      .trigger(Trigger.ProcessingTime(1.hour))
      .format("text")
      .option("path", Global.PATH_SINK)
      .option("checkpointLocation", Global.PATH_CHICKPOINT + "query")
      .partitionBy("key")
      .start()

    // 在合适的时候关闭spark
    SparkHelper.close

  }
}
