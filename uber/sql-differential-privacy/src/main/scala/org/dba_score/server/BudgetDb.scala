package org.dba_score.server

import scala.concurrent.Await
import scala.concurrent.duration._

import slick.jdbc.SQLiteProfile.api._

class BudgetDb(dbFilePath: String) {
  val database = Database.forURL(s"jdbc:sqlite:$dbFilePath", driver = "org.sqlite.JDBC")

  class RemainingBudget(tag: Tag) extends Table[(Int, String, Double)](tag, "REMAINING_BUDGET") {
    def sessionID = column[Int]("SESSION_ID", O.PrimaryKey)
    def dbName = column[String]("DB_NAME")
    def remainingBudget = column[Double]("REMAINING_BUDGET")
    def * = (sessionID, dbName, remainingBudget)
  }
  val remainingBudget = TableQuery[RemainingBudget]

  Await.result(database.run(remainingBudget.schema.createIfNotExists), 5 seconds)

  def initBudget(sessionID: Int, dbName: String, initialBudget: Double): Unit = this.synchronized {
    if (Await.result(database.run(remainingBudget.filter(_.sessionID === sessionID).exists.result), 5 seconds)) {
      throw new IllegalArgumentException(s"Session $sessionID: Cannot init budget. Session ID already exists.")
    }
    Await.result(database.run(remainingBudget += (sessionID, dbName, initialBudget)), 5 seconds)
  }

  def useBudget(sessionID: Int, epsilon: Double): (String, Double) = this.synchronized {
    if (epsilon < 0.0) {
      throw new IllegalArgumentException(s"Session $sessionID: Epsilon is less than zero. " +
        s"(requested=$epsilon) < (min=0.0).")
    }
    if (!Await.result(database.run(remainingBudget.filter(_.sessionID === sessionID).exists.result), 5 seconds)) {
      throw new IllegalArgumentException(s"Session $sessionID: Session ID does not exist.")
    }
    val existing = Await.result(database.run(remainingBudget.filter(_.sessionID === sessionID).take(1).
      map(r => (r.dbName, r.remainingBudget)).result), 5 seconds).head
    val existingDbName = existing._1
    var existingRemainingBudget = existing._2
    if (existingRemainingBudget < epsilon) {
      throw new IllegalArgumentException(s"Session $sessionID: Not enough budget remaining. " +
        s"(remaining=$existingRemainingBudget) < (requested=$epsilon).")
    }
    existingRemainingBudget -= epsilon
    if (epsilon != 0.0) {
      Await.result(database.run((for {r <- remainingBudget if r.sessionID === sessionID} yield r.remainingBudget).
        update(existingRemainingBudget)), 5 seconds)
    }
    (existingDbName, existingRemainingBudget)
  }

  def obtainInfo(sessionID: Int): (String, Double) = {
    useBudget(sessionID, 0.0)
  }

  def destroyBudget(sessionID: Int): (String, Double) = this.synchronized {
    if (!Await.result(database.run(remainingBudget.filter(_.sessionID === sessionID).exists.result), 5 seconds)) {
      throw new IllegalArgumentException(s"Session $sessionID: Cannot delete budget. Session ID does not exist.")
    }
    val res = obtainInfo(sessionID)
    Await.result(database.run(remainingBudget.filter(_.sessionID === sessionID).delete), 5 seconds)
    res
  }
}
