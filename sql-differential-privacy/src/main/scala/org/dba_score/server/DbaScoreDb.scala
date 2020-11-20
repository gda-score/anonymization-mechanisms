package org.dba_score.server

import scala.concurrent.Await
import scala.concurrent.duration._

import slick.jdbc.PostgresProfile.api._

class DbaScoreDb(dbConnString: String) {
  val database = Database.forURL(s"jdbc:postgresql:$dbConnString", driver = "org.postgresql.Driver")

  def runQuery(query: String): Double = {
    Await.result(database.run(sql"""#$query""".as[Double]), 5 seconds).head
  }
}
