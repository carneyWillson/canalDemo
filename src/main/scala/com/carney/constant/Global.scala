package com.carney.constant

class Global {

}

object Global {
  ///////////////////////////////////////////////////////////////////////////
  // 表格相关的字段
  ///////////////////////////////////////////////////////////////////////////
  final val COL_COMMON_KEY = "id"
  final val COL_COMMON_UPDATE_TIME = "updatetime"
  final val COL_COMMON_CREATE_TIME = "createtime"
  ///////////////////////////////////////////////////////////////////////////
  // mysql连接
  ///////////////////////////////////////////////////////////////////////////
  final val MYSQL_DRIVER = "com.mysql.jdbc.Driver"
  final val MYSQL_URL = "jdbc:mysql//node243:3306/kafka_info"
  final val MYSQL_USERNAME = "hive"
  final val MYSQL_PASSWORD = "hive"
  ///////////////////////////////////////////////////////////////////////////
  // 路径
  ///////////////////////////////////////////////////////////////////////////
  // 本地路径需要"file://"开头, 但是hdfs路径不能有"hdfs://"这个前缀
  //  final val PATH_SINK = "file:///C:/myDemo/shop_sink"
  //  final val PATH_CHICKPOINT = "file:///C:/myDemo/ckpoint/"
  // 别忘记"/"前缀
  final val PATH_SINK = "/wjl/shop/sink/mysql2ods/"
  final val PATH_CHICKPOINT = "/wjl/shop/chickpoint/mysql2ods/"
  final val PATH_ERROW = "/wjl/shop/error/"

}