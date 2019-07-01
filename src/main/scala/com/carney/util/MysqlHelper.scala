package com.carney.util

import scalikejdbc._

/**
 * 用于处理和mysql的交互
 * 主要是存取数据
 */
object MysqlHelper {

  Class.forName("com.mysql.jdbc.Driver")
  ConnectionPool.singleton("jdbc:mysql://node243:3306/kafka_info", "canal", "canal")
  implicit val session = AutoSession

  def main(args: Array[String]): Unit = {
    setOffsetJson("earliest", "earliest")
  }
  /**
    * 取出json格式的角标
    * @return 用于存储kafka指定主题下, 各分区对应的offset
    */
  def getOffsetJson() = {

    var offsetJson = "earliest"

    // 得到上一次结束的角标
    sql"""
      select end_offset
      from shop_offset
      where status=9
    """.foreach(resultSet => {
      offsetJson = resultSet.get[String](1)
    })

    offsetJson
  }

  /**
    * 存入角标到mysql, 可能一次取多次存
    * @param start_offset json格式
    * @param end_offset json格式
    */
  def setOffsetJson(start_offset: String, end_offset: String): Unit = {

    // 更新status, 把9抹平成1
    sql"""
      update shop_offset
      set status=1
      where status=9
    """.update().apply()

    // 新增offset记录
    sql"""
      insert into shop_offset
      (start_offset, end_offset, status) values
      (${start_offset}, ${end_offset}, 9)
    """.update().apply()
  }

}
