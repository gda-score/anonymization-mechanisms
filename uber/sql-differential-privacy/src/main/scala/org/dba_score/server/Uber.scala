package org.dba_score.server

import com.uber.engsec.dp.rewriting.differential_privacy.{ElasticSensitivityConfig, ElasticSensitivityRewriter}
import com.uber.engsec.dp.schema.Schema

object Uber {
  // delta parameter: use 1/n^2, with n = 100000
  val DELTA: Double = 1 / math.pow(100000, 2)

  def rewriteQuery(dbName: String, query: String, epsilon: Double): String = {
    val database = Schema.getDatabase(dbName)
    println(s"Epsilon: $epsilon")
    println("Original query:")
    printQuery(query)

    // Rewrite the original query to enforce differential privacy using Elastic Sensitivity.
    val config = new ElasticSensitivityConfig(epsilon, DELTA, database)
    val rewrittenQuery = new ElasticSensitivityRewriter(config).run(query)
    val sql = rewrittenQuery.toSql()
    println("Rewritten query:")
    printQuery(sql)
    sql
  }

  def printQuery(query: String): Unit = println(query.replaceAll("\\n", s"\n"))
}
