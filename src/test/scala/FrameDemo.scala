import org.apache.spark.SparkConf
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{IntegerType, StringType, StructType}

/**
 * @Description TODO
 * @Author WangJiaLi
 * @Date 2019/6/28 10:03
 * @Version 1.0
 */
object FrameDemo {

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf()
      // 测试时设置
      .setMaster("local[*]")
      .setAppName("StructuredDemo")

    val spark = SparkSession
      .builder
      .config(conf)
      .getOrCreate()

    /**
     * C:\myDemo\shop_sink\key=shop_tb_1\part-00000-08c7643e-fd8d-48ab-b721-d7313f738e8a.c000.txt
     * C:\myDemo\shop_sink\key=shop_tb_1\part-00001-106601ea-94ad-4607-b794-2ef1ef4193e7.c000.txt
     * C:\myDemo\shop_sink\key=shop_tb_1\part-00002-5f3cbdb3-cf43-4272-bd60-e09a72b6360f.c000.txt
     */

    val list1 = List(Row(1, "w", 12), Row(2, "l", 13), Row(3, "z", 14), Row(4, "f", 15))
    val list2 = List(Row(1, "w", 12), Row(2, "l", 13), Row(3, "z", 14), Row(4, "f", 15))
    val schema = new StructType()
      .add("id", IntegerType)
      .add("name", StringType)
      .add("age", IntegerType)
    val rdd = spark.sparkContext.parallelize(list1)
    val rdd2 = spark.sparkContext.parallelize(list2)
    val df = spark.createDataFrame(rdd, schema)
    val df2 = spark.createDataFrame(rdd2, schema)

    df.join(df2, Seq("id"), "full_outer")
      .select(coalesce(df2("name"), df("name")))
  }
}
