package org.dba_score.server

import com.uber.engsec.dp.rewriting.differential_privacy.{ElasticSensitivityConfig, ElasticSensitivityRewriter}
import com.uber.engsec.dp.schema.Schema

object Uber {
  // delta parameter: use 1/n^2, with n = 100000
  val DELTA: Double = 1 / math.pow(100000, 2)

  def rewriteQuery(dbName: String, query: String, epsilon: Double): String = {
    val database = Schema.getDatabase(dbName)
    println("Original query:")
    printQuery(query)
    println(s"> Epsilon: $epsilon")

    // Rewrite the original query to enforce differential privacy using Elastic Sensitivity.
    println("\nRewritten query:")
    val config = new ElasticSensitivityConfig(epsilon, DELTA, database)
    val rewrittenQuery = new ElasticSensitivityRewriter(config).run(query)
    val sql = rewrittenQuery.toSql()
    printQuery(sql)
    sql
  }

  def printQuery(query: String): Unit = println(s"\n  " + query.replaceAll("\\n", s"\n  ") + "\n")
}
