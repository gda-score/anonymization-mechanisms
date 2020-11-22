# Uber Tool
Client and server implementation to make use of the differential privacy library from Uber.

## Client
The client is implemented in Python 3.8 in the [uber-client](uber-client) folder. The files should be self
explanatory. Please see [test.py](uber-client/test.py) for a full working example.

## Server
The server is implemented in Scala in the [sql-differential-privacy](sql-differential-privacy) folder. The folder
is a copy of [Uber's archived repository](https://github.com/uber-archive/sql-differential-privacy) at the time of this
writing. There are no changes to Uber's library. The server is an additional tool simply calling the library functions.

The server provides a web API to receive and answer queries in JSON format. It internally manages a SQLite database to
keep track of privacy budgets. It takes a SQL query, uses the Uber library to rewrite the query into a differentially
private SQL query, connects to the DBA-Score PostgreSQL database to run the query, and finally returns the private
result to the client. It currently runs on *https://db001.dba-score.org* and takes the following requests.

#### Session Init
Listens at */uber/session/init* and expects an HTTPS POST request with a JSON payload containing fields `dbname` of type
String and `budget` of type Double.

###### Example:
```python
{
    "dbname": "raw_banking",
    "budget": 6.0,
}
```

During the lifetime of a session the database and the budget are fix and cannot be modified. The server deducts the
privacy cost epsilon of each query in the session from the session's budget. Once the session is out of sufficient
budget, queries fail to execute, and an appropriate error gets returned to the client. For additional information see
the other method descriptions below.

Session init assigns and returns a session ID. This ID must be used to ask queries in the context of the session. The
returned data is in JSON format and contains fields `Session ID` of type Int, `DB Name` of type String, and 
`Remaining Budget` of type Double.

###### Example:
```python
{
    "Session ID": 131807822,
    "DB Name": "raw_banking",
    "Remaining Budget": 6.0,
}
```

#### Session Query
Listens at */uber/session/query* and expects an HTTPS POST request with a JSON payload containing fields `sid` of type
Int, `query` of type string, and `epsilon` of type Double.

###### Example:
```python
{
    "sid": 131807822,
    "query": "Select count(*) from transactions where operation LIKE '%KLAD'",
    "epsilon": 0.5,
}
```

First, the server checks the internal SQLite database, whether the session exists and whether it has enough budget
remaining. It continues to deduct the epsilon amount and stores the updated budget value. Using the functionality of the
Uber library, the server rewrites the SQL query into a differentially private version. It proceeds to run the private
query on DBA Score's PostgreSQL database server for the database specified in the session. The result `Result` of type
Double is returned to the client together with the private SQL query `Private SQL` of type String, the amount of
remaining budget in the session `Remaining Budget` of type Double , and the session's id `Session ID` of type Int. The
returned data is again JSON formatted.

###### Example:
```python
{
    "Session ID": 131807822,
    "Private SQL": "SELECT COUNT(*) + 4.0 * (CASE WHEN RAND() - 0.5 < 0 THEN -1.0 ELSE 1.0 END * LN(1 - 2 * ABS(RAND() - 0.5)))
                    FROM (SELECT 'operation'
                    FROM 'public'.'transactions') AS 't'
                    WHERE 'operation' LIKE '%KLAD'",
    "Result": 181960.63167800178,
    "Remaining Budget": 5.5,
}
```

#### Session Info
Listens at */uber/session/info* and expects an HTTPS POST request with a JSON payload containing the field `sid` of type
Int.

###### Example:
```python
{
    "sid": 131807822,
}
```

Simply returns the information currently hold by the server with regards to the session. The returned data is in JSON
format and contains fields `Session ID` of type Int, `DB Name` of type String, and `Remaining Budget` of type Double.

###### Example:
```python
{
    "Session ID": 131807822,
    "DB Name": "raw_banking",
    "Remaining Budget": 5.5,
}
```

#### Session Destroy
Listens at */uber/session/destroy* and expects an HTTPS POST request with a JSON payload containing the field `sid` of
type Int.

###### Example:
```python
{
    "sid": 131807822,
}
```

Deletes all data regarding the session. Returns the state before deletion. The returned data is in JSON format and
contains fields `Session ID` of type Int, `DB Name` of type String, and `Remaining Budget` of type Double.

###### Example:
```python
{
    "Session ID": 131807822,
    "DB Name": "raw_banking",
    "Remaining Budget": 5.5,
}
```

#### Compatibility Mode
Listens at */uber/compat* and expects an HTTPS GET request with a RAW payload that may be parsed into JSON format with
fields `sid`, `dbname`, `budget`, `query`, and `epsilon`.

###### Example:
```python
{
    "sid": "131807822",
    "dbname": "raw_banking",
    "budget": "6.0",
    "query": "Select count(*) from transactions where operation LIKE '%KLAD'",
    "epsilon": "0.5",
}
```

Compatibility mode is for clients that worked with the old FLASK server implementation. With this mode clients may
easily be switched over to the new Scala server without any further modifications. Instead of pointing your client to
*https://db001.gda-score.org/ubertool*, point it to *https://db001.gda-score.org/uber/compat* and it should continue
to work. Please make sure your client does not rely on specific features of the returned JSON payloads. The new server
implementation tries to stay close to the old one regarding field names and values, but is not 100% equivalent.

As with the old implementation, not all input fields are always used, however, all must always be present.
When `sid` is an empty string, the server will initialize a session and assign a new ID. Only in that case fields
`dbname` and `budget` are considered. Otherwise, these fields are ignored. When `epsilon` parses into a Double greater
than zero, the query is executed and the budget of the session is reduced accordingly. The JSON answers of this function
are comparable to the regular answers of the on-compatibility functions. Nevertheless, for compatibility reasons the
returned fields are nested one level below a general `Server Response` field.

###### Example:
```python
{
    "Server Response": {
                            "Session ID": 131807822,
                            "DB Name": "raw_banking",
                            "Remaining Budget": 5.5,
    }
}
```

Some errors in compatibility mode will not contain the proper `Session ID` in the response, but rather a static value of
`-2`. For general information on error handling, please read below.

### Error Handling
Most errors that occur during execution lead to a proper response to the client. The returned data is in JSON format and 
contains fields `Session ID` of type Int, `Error` of type String, and `Stack Trace` of type String.

###### Example:
```python
{
    "Session ID": 131807822,
    "Error": "Budget Exceeded. Not enough budget remaining. (remaining=3.0) < (requested=4.0).",
    "Stack Trace": "java.lang.IllegalArgumentException: Session 721256162: Budget Exceeded. Not enough budget remaining. (remaining=3.0) < (requested=4.0).
                    	at org.dba_score.server.BudgetDb.useBudget(BudgetDb.scala:42)
                    	at org.dba_score.server.Server$.queryJson(Server.scala:51)
                    	at org.dba_score.server.Server$.queryFunc$1(Server.scala:105)
                    	at org.dba_score.server.Server$.$anonfun$sessionQuery$1(Server.scala:107)
                    	at org.dba_score.server.Server$.tryAndCatch(Server.scala:70)
                    	at org.dba_score.server.Server$.sessionQuery(Server.scala:107)
                    	at org.dba_score.server.Server$.$anonfun$new$19(Server.scala:102)
                    	at cask.router.Result$Success.map(Result.scala:20)
                    	at cask.router.Result$Success.map(Result.scala:19)
                    	at org.dba_score.server.Server$.$anonfun$new$15(Server.scala:102)
                    	at cask.router.EntryPoint.invoke(EntryPoint.scala:46)
                    	at cask.router.Decorator$.$anonfun$invoke$2(Decorators.scala:59)
                    	at cask.endpoints.postJson.wrapFunction(JsonEndpoint.scala:76)
                    	at cask.router.Decorator$.invoke(Decorators.scala:53)
                    	at cask.main.Main$DefaultHandler.handleRequest(Main.scala:99)
                    	at io.undertow.server.Connectors.executeRootHandler(Connectors.java:360)
                    	at io.undertow.server.HttpServerExchange$1.run(HttpServerExchange.java:830)
                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
                    	at java.lang.Thread.run(Thread.java:748)
    ",
}
```

The depicted error happens when a query is asked with an epsilon that exceeds the remaining budget of the session.
