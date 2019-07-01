package com.carney.util

import com.alibaba.fastjson.{JSON, JSONObject}
import org.apache.spark.sql.SaveMode

import scala.collection.mutable.ListBuffer


object JsonHelper {

  /**
   * 特定json的解析, 不属于公共方法, 应该放在伴生类中
   * @param jsonStr
   * @return
   */
  def readJson(jsonStr: String): List[(String, String)] = {
    val map = JSON.parseObject(jsonStr)

//    val map: JSONObject = map
    val database = {
      val temp = map.getString("database")
      if (temp == null) "none" else temp
    }
    val table = {
      val temp = map.getString("table")
      if (temp == null) "none" else temp
    }
    val sqlType = {
      val temp = map.getString("type")
      if (temp == null) "none" else temp
    }

    val key = new StringBuilder()
      .append(database).append("-")
      .append(table).append("-")
      .append(sqlType).toString()

    val dataArray = map.getJSONArray("data")
    if (dataArray == null) {
      return List.empty[(String, String)]
    }
    val it = dataArray.iterator()
    val valueList = new ListBuffer[Tuple2[String, String]]()
    while (it.hasNext) {
      val eachRow = it.next().toString
      valueList.append((key, eachRow))
    }
    valueList.toList
  }
  ///////////////////////////////////////////////////////////////////////////
  // json文件和hive的交互
  ///////////////////////////////////////////////////////////////////////////
  /**
    * 将json文件读成DataFrame
    * @param fileNames
    * @return
    */
  def json2Frame(fileNames: Seq[String]) ={
    val sparkSession = SparkHelper.getSparkSession()
    sparkSession
      .read.json(fileNames:_*)

  }
  /**
    * 将json文件读到hive中去
    * @param fileNames
    * @param hiveTable
    */
  def json2Hive(fileNames: Seq[String], hiveTable: String): Unit = {
    json2Frame(fileNames)
      .repartition(4)
      .write.format("hive")
//      .partitionBy("partition_date")
      .mode(SaveMode.Append)
      .saveAsTable(hiveTable)
  }

}
