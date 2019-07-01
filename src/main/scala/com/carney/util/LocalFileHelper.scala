package com.carney.util

import java.io.{File, FileFilter, FilenameFilter}
import java.net.{URI, URL}

import com.carney.constant.Global

import scala.collection.immutable.HashSet

/**
 * 本地文件系统相关的操作, 可用性不确定
 * @param path
 */
class LocalFileHelper(path: String) {
  import LocalFileHelper._

  /**
    * 核心方法, 将文件夹中的数据对应导入到hive表中, 然后删除文件夹
    */
  def dir2Hive: Unit ={
    // 判断是否是本地文件
    if (!isLocalDir(path)) {
      return
    }
    val localDir: File = new File(formatLocalPath(path))
    val dirs = scanDir(localDir, "key=")

    for (dir <- dirs) {
      val headName = dir.getAbsolutePath + "\\"
      val lastNames =  dir.list(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = {
          name.endsWith(".txt")
        }
      })
      val fileNames = lastNames.map("file:///".concat(headName).concat(_))

      // key=shop_dsc-tb-0
      // 将文件夹拆分为表信息
      val tableInfos = dir.getName.substring(4).split("-")
      val hiveTable = new StringBuilder("wjl_ods_")
        .append(tableInfos(0))
        .append(".")
        .append(tableInfos(1))
        .toString()

      // 追加
      if (tableInfos(2) == "wjlINSERT") {
        JsonHelper.json2Hive(fileNames, hiveTable)
      } else if (tableInfos(2) == "UPDATE") {
        import org.apache.spark.sql.expressions.Window
        import org.apache.spark.sql.functions._

        val updatedData = JsonHelper.json2Frame(fileNames)
          .withColumn("flag", row_number().over(Window.partitionBy("id").orderBy(lit("utime")desc)))
          .where("flag=1")
          .drop("flag")
        val sparkSession = SparkHelper.getSparkSession()
        import sparkSession._
        /**
          * *   people.select(when(people("gender") === "male", 0)
          * *     .when(people("gender") === "female", 1)
          * *     .otherwise(2))
          */
        val oldData =sparkSession.read
          .table(hiveTable).as("a")
        oldData
          .join(updatedData as("b"), "id")
          .withColumn("flag", when(updatedData("id") isNull, 1).otherwise(0))
          .where("flag=1")
          .select(oldData("*"))
          .union(updatedData)
          .show()
      } else {

      }
    }

  }

}
object LocalFileHelper {
  // 定义在object中的方法, 可以视为静态方法
  def apply(path: String): LocalFileHelper = new LocalFileHelper(path)

  def main(args: Array[String]): Unit = {

    var str = Global.PATH_SINK

    val helper = apply(str).dir2Hive

  }
  /**
    * 列出指定目录下, 所有以指定字符开头的文件夹
    * @param dir
    * @param startsWith
    * @return
    */
  def scanDir(dir: File, startsWith: String) = {
    dir.listFiles(new FileFilter {
      override def accept(pathname: File): Boolean = {
        pathname.isDirectory && pathname.getName.startsWith(startsWith)
      }
    })
  }
  /**
    * 判断给定路径是否是本地目录
    * @param path 文件路径
    * @return true or false
    */
  def isLocalDir(path: String): Boolean = {
    val localFile = new File(formatLocalPath(path))
    // 是否存在, 是否是文件
    localFile.exists && localFile.isDirectory
  }

  /**
    * 判断文件路径是否以"file:". 如果是, 就去掉"file:"字段, 如果不是, 就原样返回
    * @param path 完整路径
    * @return 截取后的路径
    */
  def formatLocalPath(path: String): String = {
    // 是否以file开头
    if (path.startsWith("file:")) {
      return path.substring(5)
    }
    return path
  }

  /**
    * 如果本地存在给定路径, 就删除
    * @param localPath
    */
  def delIfExists(localPath: String) = {
    val formattedPath = formatLocalPath(localPath)
    // 直接尝试删除即可
    deleteFile(formattedPath)
  }
  /**
    * 如果存在就删除
    * @param path 正常的本地路径, 不以file开头
    */
  private def deleteFile(path: String): Unit = {
    val file = new File(path)
    // 健壮性判断
    if (!file.exists()) {
      return
    }
    // 递归结束条件
    if (file.isFile) {
      file.delete()
      return
    }
    // 递归本体
    val files = file.list()
    for (sonFile <- files) {
      deleteFile(sonFile)
    }
    file.delete()
  }

}