package org.dba_score.server

import java.io.{PrintWriter, StringWriter}

import ujson._

import scala.collection.mutable

object Server extends cask.MainRoutes {
  override def host: String = "localhost"
  override def port: Int = 5005
  override def debugMode: Boolean = true

  System.setProperty("schema.config.path", "/home/uber-dba-score-server/run_dir/gda-score-schema.yaml")

  val budgetDb = new BudgetDb("/home/uber-dba-score-server/run_dir/budgetDb.sqlite")
  var dbaScoreDbMap = new mutable.HashMap[String, DbaScoreDb]()

  def obtainDbaScoreDb(dbName: String): DbaScoreDb = this.synchronized {
    if (!dbaScoreDbMap.contains(dbName)) {
      dbaScoreDbMap.put(dbName, new DbaScoreDb(s"//db001.gda-score.org:5432/$dbName?ssl=true&" +
        s"sslfactory=org.postgresql.ssl.NonValidatingFactory&user=***REMOVED***&password=***REMOVED***&" +
        s"sslmode=require"))
    }
    dbaScoreDbMap.getOrElse(dbName, null)
  }

  def infoJson(sid: Int, dbName: String, remainingBudget: Double): Obj = {
    val json = ujson.Obj(
      "Session ID" -> s"$sid",
      "DB Name" -> s"$dbName",
      "Remaining Budget" -> s"$remainingBudget",
    )
    println(s"Server returns JSON to client: $json")
    json
  }

  def errorJson(sid: Int, exception: Exception): Obj = {
    val sw = new StringWriter
    exception.printStackTrace(new PrintWriter(sw))
    val json = ujson.Obj(
      "Session ID" -> s"$sid",
      "Error" -> s"${exception.getLocalizedMessage}",
      "Stack Trace" -> s"${sw.toString}",
    )
    println(s"Server returns JSON to client: $json")
    json
  }

  def queryJson(sid: Int, query: String, epsilon: Double): Obj = {
    val existing = budgetDb.useBudget(sid, epsilon)
    val dbName = existing._1
    val remainingBudget = existing._2
    val newSql = Uber.rewriteQuery(dbName, query, epsilon)
    val database = obtainDbaScoreDb(dbName)
    val result = database.runQuery(newSql)
    println(s"Result: $result")
    val json = ujson.Obj(
      "Session ID" -> s"$sid",
      "Private SQL" -> s"$newSql",
      "Result" -> s"$result",
      "Remaining Budget" -> s"$remainingBudget",
    )
    println(s"Server returns JSON to client: $json")
    json
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
      queryJson(sid, query, epsilon)
    }
    tryAndCatch(sid, queryFunc)
  }

  @cask.postJson("/uber/session/destroy")
  def sessionDestroy(sid: Int): Obj = {
    println(s"Client sent JSON to sessionDestroy: sid=$sid")
    def destroyFunc(): Obj = {
      val existing = budgetDb.destroyBudget(sid)
      val dbName = existing._1
      val remainingBudget = existing._2
      infoJson(sid, dbName, remainingBudget)
    }
    tryAndCatch(sid, destroyFunc)
  }

  @cask.getJson("/uber/compat")
  def compat(request: cask.Request): Obj = {
    def compatFunc(): Obj = {
      val requestString = scala.io.Source.fromInputStream(request.data).mkString
      println(s"Client sent RAW request data to compat: request=$requestString")
      val json = ujson.read(requestString)
      val sid = json("sid").str
      val dbname = json("dbname").str
      val budget = json("budget").str
      val query = json("query").str
      val epsilon = json("epsilon").str
      println(s"compat interpreted RAW request as JSON and retrieved its inputs: sid=$sid dbname=$dbname " +
        s"budget=$budget query=$query " +
        s"epsilon=$epsilon")
      var parsedSid = -1
      var parsedBudget = -1.0
      val parsedEpsilon = epsilon.toDouble
      if (sid.isEmpty) {
        parsedSid = scala.util.Random.nextInt(1000000000)
        parsedBudget = budget.toDouble
        budgetDb.initBudget(parsedSid, dbname, parsedBudget)
      } else {
        parsedSid = sid.toInt
      }
      if (parsedEpsilon > 0.0) {
        queryJson(parsedSid, query, parsedEpsilon)
      } else {
        infoJson(parsedSid, dbname, parsedBudget)
      }
    }
    val json = tryAndCatch(-2, compatFunc)
    val compatJson = ujson.Obj(
      "Server Response" -> json,
    )
    println(s"compat adapts JSON for old clients and actually returns: $compatJson")
    compatJson
  }

  @cask.get("/")
  def hello(): String = {
    "DBA Score Server is up and running.\n" +
      "Use /uber/session/init, /uber/session/info, /uber/session/query, and /uber/session/destroy to query the " +
      "DBA Score data protected by the differentially private tool from Uber. These are JSON endpoints and expect a " +
      "JSON POST request.\n" +
      "More information at https://github.com/gda-score/anonymization-mechanisms"
  }

  initialize()
}
