import org.apache.hadoop.hive.ql.parse.WindowingSpec.WindowSpec
import org.apache.spark.SparkConf
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{IntegerType, StringType, StructType}

object WindowDemo {
  /**
   * 尝试使用api开窗
   * @param args
   */
  def main(args: Array[String]): Unit = {
    val conf = new SparkConf()
      // 测试时设置
      .setMaster("local[*]")
      .setAppName("StructuredDemo")

    val spark = SparkSession
      .builder
      .config(conf)
      .getOrCreate()

    spark.read
      .json("file:///C:\\myDemo\\shop_sink\\key=shop_dsc-tb-0\\part-00002-a0426674-b548-4d51-a04a-eb0545aa1835.c000.txt")
      .withColumn("rm", row_number().over(Window.partitionBy("name").orderBy(lit(1))))
      .where("rm=1")
      .show()
  }
}
