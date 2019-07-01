package com.carney.util

import java.net.URI
import java.util.Objects

import com.carney.constant.Global
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Hdfs, Path}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.StreamingQueryListener
import org.apache.spark.sql.streaming.StreamingQueryListener.{QueryStartedEvent, QueryTerminatedEvent}

// helper代码被设计为高复用, 因此大量使用懒汉式单例, 避免使用不到时浪费资源
object SparkHelper {


  @volatile private var singleSparkSession: SparkSession = null
  /**
   * 懒汉式单例的sparkSession, 以供全局使用
   * @return 支持hive的sparkSession
   */
  def getSparkSession(): SparkSession = {
    if (singleSparkSession == null) {
      synchronized {
        if (singleSparkSession == null) {
          val conf = new SparkConf()
            .setMaster("local[*]")
            .set("spark.local.dir", "C:\\myDemo\\ckpoint")
            // 以上两个是测试环境
            .setAppName("mysql2ods")
            // hive动态分区
            .set("hive.exec.dynamic.partition", "true")
            .set("hive.exec.dynamic.partition.mode", "nonstrict")
            .set("hive.exec.max.dynamic.partitions", "1000")
            .set("hive.exec.max.dynamic.partitions.pernode", "100")
            // 优化
            .set("spark.serilizer", "org.apache.spark.serializer.KryoSerializer")

          singleSparkSession = SparkSession.builder()
            .config(conf)
            .enableHiveSupport()
            .getOrCreate()
          singleSparkSession.sparkContext.setCheckpointDir("/wjl/ckpoint/")
        }
      }
    }
    singleSparkSession
  }

  // 标记主程序是否需要继续运行
  @volatile private var flag: Boolean = true
  /**
   * 关闭资源
   */
  def close: Unit ={
    while (flag) {
      Thread.`yield`()
    }
    // 关闭hdfs连接
    HdfsHelper.close()
    if (singleSparkSession != null) {
      try {
        singleSparkSession.close()
      } catch {
        case ex:Exception => {
          println(s"close singled sparksession failed, msg=$ex")
        }
      }
    }

  }

  /**
   * 用于监控spark流, 根据不同事件触发不同行动
   * @return Listener
   */
  def getStreamsListener(): StreamingQueryListener = {
    new StreamingQueryListener() {
      // writeStream开始时触发
      override def onQueryStarted(queryStarted: QueryStartedEvent): Unit = {
        println("Query started: " + queryStarted.id)
      }
      // writeStream结束时触发
      override def onQueryTerminated(queryTerminated: QueryTerminatedEvent): Unit = {
        println("Query terminated: " + queryTerminated.id)
      }
      // writeStream查询时触发
      override def onQueryProgress(event: StreamingQueryListener.QueryProgressEvent): Unit = {
        val source = event.progress.sources.headOption
        source.map(src => {
          // 将数据从hdfs转移到hive
          HdfsHelper.apply(Global.PATH_SINK).dir2Hive
          // 偏移量入库
          val end_offset: String = src.endOffset
          val start_offset: String = src.startOffset
          MysqlHelper.setOffsetJson(start_offset, end_offset)
          // 检查结束条件
          if (Objects.equals(start_offset, end_offset)) {
            flag = false
          }
          println(s"Start Offset: ${start_offset}, End offset: ${end_offset}")
        })

      }

    }
  }

}
