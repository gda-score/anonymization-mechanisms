package org.dba_score.server

import ujson.Obj

import scala.collection.mutable

object Server extends cask.MainRoutes {
  override def host: String = "localhost"
  override def port: Int = 5005
  override def debugMode: Boolean = true

  System.setProperty("schema.config.path", "src/test/resources/gda-score-schema.yaml")

  val budgetDb = new BudgetDb("/home/gda-score/uber/budgetDb.sqlite")
  var dbaScoreDbMap = new mutable.HashMap[String, DbaScoreDb]()

  def obtainDbaScoreDb(dbName: String): DbaScoreDb = this.synchronized {
    if (!dbaScoreDbMap.contains(dbName)) {
      dbaScoreDbMap.put(dbName, new DbaScoreDb(s"//***REMOVED***/$dbName?ssl=true&" +
        s"sslfactory=org.postgresql.ssl.NonValidatingFactory&user=***REMOVED***&password=***REMOVED***"))
    }
    dbaScoreDbMap.getOrElse(dbName, null)
  }

  def infoJson(sid: Int, dbName: String, remainingBudget: Double): Obj = {
    ujson.Obj(
      "Session ID" -> s"$sid",
      "DB Name" -> s"$dbName",
      "Remaining Budget" -> s"$remainingBudget",
    )
  }

  def errorJson(sid: Int, exception: Exception): Obj = {
    ujson.Obj(
      "Session ID" -> s"$sid",
      "Exception" -> s"${exception.getLocalizedMessage}",
      "Stack Trace" -> s"${exception.printStackTrace()}",
    )
  }

  def tryAndCatch(sid: Int, func: () => Obj): Obj = {
    try {
      func()
    } catch {
      case e: Exception =>
        println(s"Session $sid: Exception occurred: ${e.getLocalizedMessage}\n${e.printStackTrace()}")
        errorJson(sid, e)
    }
  }

  @cask.postJson("/uber/session/init")
  def sessionInit(dbname: String, budget: Double): Obj = {
    println(s"Client sent JSON to sessionInit: dbname=$dbname budget=$budget")
    val sid = scala.util.Random.nextInt(1000000000)
    def initFunc(): Obj = {
      budgetDb.initBudget(sid, dbname, budget)
      infoJson(sid, dbname, budget)
    }
    tryAndCatch(sid, initFunc)
  }

  @cask.postJson("/uber/session/info")
  def sessionInfo(sid: Int): Obj = {
    println(s"Client sent JSON to sessionInfo: sid=$sid")
    def infoFunc(): Obj = {
      val existing = budgetDb.obtainInfo(sid)
      val dbName = existing._1
      val remainingBudget = existing._2
      infoJson(sid, dbName, remainingBudget)
    }
    tryAndCatch(sid, infoFunc)
  }

  @cask.postJson("/uber/session/query")
  def sessionQuery(sid: Int, query: String, epsilon: Double): Obj = {
    println(s"Client sent JSON to sessionQuery: sid=$sid query=$query epsilon=$epsilon")
    def queryFunc(): Obj = {
      val existing = budgetDb.useBudget(sid, epsilon)
      val dbName = existing._1
      val remainingBudget = existing._2
      val newSql = Uber.rewriteQuery(dbName, query, epsilon)
      val database = obtainDbaScoreDb(dbName)
      val result = database.runQuery(newSql)
      ujson.Obj(
        "Session ID" -> s"$sid",
        "Private SQL" -> s"$newSql",
        "Result" -> s"$result",
        "Remaining Budget" -> s"$remainingBudget",
      )
    }
    tryAndCatch(sid, queryFunc)
  }

  @cask.get("/")
  def hello() = {
    "DBA Score Server is up and running.\n" +
      "Use /uber/session/init, /uber/session/info, and /uber/session/query to query the DBA Score data protected " +
      "by the differentially private tool from Uber. These are JSON endpoints and expect a JSON POST request.\n" +
      "More information at https://github.com/gda-score/anonymization-mechanisms"
  }

  initialize()
}
