object StructuredDemo {
  def main(args: Array[String]): Unit = {
    import org.apache.spark.sql.functions._
    import org.apache.spark.sql.SparkSession

    val spark = SparkSession
      .builder
      .master("local[*]")
      .appName("StructuredNetworkWordCount")
      .getOrCreate()

    import spark.implicits._
    val a = spark.read.text("file:///C:\\Users\\WangJiaLi\\Desktop\\aa.txt")


    a.write.text("file:///C:\\Users\\WangJiaLi\\Desktop\\bb.txt")
//    val list = List(List(1, 2, 3), List(1, 2, 3), List(1, 2, 3))
//    new Array[Int](Int.MaxValue).toList
//    list.fold(new Array[Int](Int.MaxValue).toList)((x, y) => {
//      x.zip(y).map(t => t._1 + t._2)
//    }).foreach(println)

  }
}
