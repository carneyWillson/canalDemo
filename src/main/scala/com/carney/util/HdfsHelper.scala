package com.carney.util

import java.net.URI

import com.carney.constant.Global
import org.apache.hadoop.fs.{FileStatus, FileSystem, Path, PathFilter}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.{Dataset, Row, SaveMode}
import com.carney.util.SparkHelper

/**
 * 伴生类用于将dfs上文件夹的json文件读取到hive
 * 伴生对象是操作dfs的公共方法
 * @param pathStr dfs上的文件夹
 */
class HdfsHelper(pathStr: String) {
  import HdfsHelper._

  /**
   * 将给定路径解析为path
   */
  val path: Path = {
    val formattedPath = formatHdfsPath(pathStr)
    new Path(formattedPath)
  }
  ///////////////////////////////////////////////////////////////////////////
  // 核心方法, 可视为当前伴生类的唯一方法
  ///////////////////////////////////////////////////////////////////////////
  /**
   * 将文件夹中的数据对应导入到hive表中, 然后删除文件夹
   */
  def dir2Hive: Unit ={
    // 判断是否是hdfs上存在的文件夹
    if (!isHdfsDir(path)) {
      return
    }

    // 列出指定目录下的以"key="开头的文件
    // 这里列出来的应该都是文件夹, 因此不做判断, 健壮性由相关代码维护
    val dirs: Array[FileStatus] = scanDir(path, "key=", true)
    for (dir <- dirs) {
      val dirPath = dir.getPath
      // 列出所有".txt"结尾的文件
      val fileNames = scanDir(dirPath, ".txt", false)
        // toString会自动增加hdfs的前缀
        .map(fileStatus => fileStatus.getPath.toString)
      fileNames.foreach(println)

      // 将文件夹拆分为表信息, 文件夹命名格式为: key=(database)-(table)-(type)
      val tableInfos = dirPath.getName.substring(4).split("-")
      // 校验合法性
      if (tableInfos(0).equals("none") || tableInfos(1).equals("none")) {
        tableInfos(2) = "none"
      }
      val hiveTable = new StringBuilder("wjl_ods_")
        .append(tableInfos(0))
        .append(".")
        .append(tableInfos(1))
        .toString()
      println(hiveTable)

      // type = insert时的逻辑
      if (tableInfos(2) == "INSERT") {
        JsonHelper.json2Hive(fileNames, hiveTable)
      }
      // type = update时的逻辑
      // 需要有id和updatetime两列, 因此现在无效化了该方法
      else if (tableInfos(2) == "##UPDATE") {
        updateFromFiles(fileNames, hiveTable)
      }
      // 将异常文件移动到指定目录
      else {
        // 保证error目录存在
        getFileSystem().mkdirs(new Path(Global.PATH_ERROW))
        getFileSystem().rename(dirPath, new Path(Global.PATH_ERROW, dirPath.getName))
      }

      // 删除已经处理过的文件夹
      delIfExists(dir.getPath)
    }

  }


}
object HdfsHelper {

  def apply(path: String): HdfsHelper = new HdfsHelper(path)

  ///////////////////////////////////////////////////////////////////////////
  // hdfs和hive的交互
  ///////////////////////////////////////////////////////////////////////////
  /**
   * 从指定文件中读取数据, 并将数据更新到hive表
   * @param fileNames 文件数组
   * @param hiveTable hive中对应的表名字
   */
  def updateFromFiles(fileNames: Array[String], hiveTable: String): Unit = {
    import org.apache.spark.sql.expressions.Window
    import org.apache.spark.sql.functions._

    // 通过分组top1去重, 获取更新后的表
    val updatedData = JsonHelper.json2Frame(fileNames)
      .withColumn("flag",
        row_number().over(Window.partitionBy(Global.COL_COMMON_KEY).orderBy(col(Global.COL_COMMON_UPDATE_TIME)desc)))
      .where("flag=1")
      .drop("flag")
    updatedData.show()

    val sparkSession = SparkHelper.getSparkSession()
    import sparkSession._
    // 获取原表
    val oldData = sparkSession.read.table(hiveTable)
    println("原表为")
    oldData.show()
    // left join + union 更新原表
    val sumData = oldData
      .join(updatedData, Seq(Global.COL_COMMON_KEY), "left_outer")
      .withColumn("flag",
        when(updatedData("utime") isNull, 1))
      //            when(updatedData(Global.COL_COMMON_UPDATE_TIME) isNull, 1))
      .where("flag=1")
      .select(oldData("*"))
      .union(updatedData)
    println("综合后的表为")
    sumData.show()

    // checkpoint切断与源表的联系, 覆盖写入hive
    sumData
      .checkpoint(true)
      // 重分区为4个, 合并小文件
      .repartition(4)
      .write
      .format("hive")
      .mode(SaveMode.Overwrite)
      .saveAsTable(hiveTable)
  }
  ///////////////////////////////////////////////////////////////////////////
  // 纯文件操作
  ///////////////////////////////////////////////////////////////////////////
  /**
   * 列出指定目录下, 所有以指定字符开头或结尾的文件
   * @param dir 指定目录
   * @param startsWith
   * @return
   */
  def scanDir(dir: Path, filter: String, isHead: Boolean) = {
    isHdfsDir(dir)
    getFileSystem().listStatus(dir, new PathFilter {
      override def accept(path: Path): Boolean = {
        if (isHead) {
          path.getName.startsWith(filter)
        } else {
          path.getName.endsWith(filter)
        }
      }
    })

  }

  /**
   * 判断给定路径是否是hdfs上的目录
   * @param path Path类型的文件路径
   * @return true or false
   */
  def isHdfsDir(path: Path): Boolean = {
    // 是否存在, 是否是文件
    getFileSystem().exists(path) && getFileSystem().isDirectory(path)
  }

  /**
   * 判断文件路径是否以"hdfs:". 如果是, 就去掉"hdfs:"字段, 如果不是, 就原样返回
   * @param path 完整路径
   * @return 截取后的路径
   */
  def formatHdfsPath(path: String): String = {
    // 是否以file开头
    if (path.startsWith("hdfs:")) {
      return path.substring(5)
    }
    return path
  }

  /**
   * 如果本地存在给定路径, 就删除
   * @param localPath
   */
  def delIfExists(path: Path) = {
    getFileSystem().delete(path, true)
  }
  ///////////////////////////////////////////////////////////////////////////
  // 获取连接和关闭资源
  ///////////////////////////////////////////////////////////////////////////
  @volatile private var singleDfs: FileSystem = null
  /**
   * 懒汉式单例的hdfs文件管理系统
   * @return 根据spark配置文件创建的hdfs
   */
  def getFileSystem(): FileSystem = {
    if (singleDfs == null) {
      synchronized {
        if (singleDfs == null) {
          singleDfs = FileSystem.get(
            new URI("hdfs://hdfsCluster"),
            // 使用和spark相同的hadoop文件系统
            SparkHelper.getSparkSession().sparkContext.hadoopConfiguration,
            "root")
        }
        println(singleDfs + "已经成功连接")
      }
    }
    singleDfs
  }
  def close(): Unit ={
    if (singleDfs != null) {
      try {
        singleDfs.close()
      } catch {
        case ex:Exception => {
          println(s"close singled dfs failed, msg=$ex")
        }
      }
    }
  }

}
