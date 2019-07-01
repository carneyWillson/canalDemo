import java.net.URI

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

/**
  * @Description 测试, 回忆通过api运行hdfs的文件管理系统
  * @Author WangJiaLi
  * @Date 2019/6/27 9:17
  * @Version 1.0
  */
object HdfsDemo {
  def main(args: Array[String]): Unit = {
    val conf = new Configuration()
    val fileSystem = FileSystem.get(new URI("hdfs://hdfsCluster"), conf, "root")
    println(fileSystem)

//    fileSystem.copyFromLocalFile(
//      new Path("C:\\myDemo\\shop_sink\\key=shop_dsc-tb-UPDATE"),
//      new Path("/wjl/error/"))

    fileSystem.delete(new Path("/wjl/shop"), true)
//    fileSystem.delete(new Path("/wjl/shop/chickpoint"), true)
//    fileSystem.listStatus(new Path("/")).foreach(println)
    fileSystem.close()

  }

}
