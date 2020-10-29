# FLASK Client and Server

Files [simpleClientFQDN.py](uber/examples/simpleClientFQDN.py) and [simpleServer.py](uber/examples/simpleServer.py) are
a client/server pair for simple, single threaded (only a single concurrent request) interaction with the UBER tool via
the internet.

The client may send requests containing a database name, a query, a privacy budget to the server. The server will
execute the query on the database and return the anonymized result.

Cumulative privacy budget use may be tracked by the server across multiple requests of a session. Request to session
mapping and database / initial maximum privacy budget configuration of a session are decided by the client.

## Client / Server Interaction

A client's request contains the following fields:
```python
request = {
    'sid': sid,
    'dbname': dbname,
    'budget': budget,
    'query': query,
    'epsilon': epsilon,
}
```

The session ID `'sid'` maps the query to a specific session. Requests with empty or missing session ID are assigned to a
newly created session by the server. The session ID gets returned with every result, unless an error occurred and no
session ID was defined yet. In that case, the returned session ID will read 'undefined'. Requests with an unknown
session ID result in an error.

The database name `'dbname'` indicates, which database the query should run on. This field is only read by the server
when a new session gets created for a request. The database name gets assigned to the session. For subsequent requests
with the same session ID, the server ignores the field of the request and uses the session's initially configured
database name instead.

The session privacy budget `'budget'` represents the maximum privacy budget available to a session. This
field is only used when a new session is created for the request. The privacy budget gets assigned to the session.
For subsequent requests with the same session ID, the server ignores the field of the request and uses the session's
initially configured privacy budget instead. For queries without session ID, this cumulative privacy budget must always
be equal or larger than the epsilon privacy budget for the query. Otherwise, the request will fail with a "budget
exceeded" error.

The SQL query `'query'` is run by the server on the database if there is enough privacy budget available. Its result is
anonymized and returned to the client.

The privacy budget `'epsilon'` represents the privacy budget to be used by the query. The query can only be executed if
the corresponding session's cumulative privacy budget use does not exceed the session's maximum privacy budget once the
query's epsilon privacy budget is added. If the maximum is not enough a "budget exceeded" error is returned. Otherwise,
the query's result is anonymized according to the epsilon value and epsilon is added to the session's cumulative privacy
budget use.

## Server configuration
The FLASK server requires to now the paths of two folders:

1. The path where the server creates files containing JSON request data.
2. The path where the UBER tool creates the result.txt files.

```python
request_path = pathlib.Path.cwd().parent.joinpath(pathlib.Path("files", "jsonreq"))
uber_path = pathlib.Path.cwd().parent.joinpath(pathlib.Path("files", "noisyres"))
```

The paths are in this example case relative to the folder the server gets executed in.


## How to use the example client

In `config.py` file, set the values of `budget` and `dbname`.

Only the `epsilon` value, the `query` and the `count` of query can be updated for each query. 
`budget` and `dbname` are ignored once they are set for a particular session.

Make the `querylist` according to the guidelines provided below.

After making the changes in `config.py`, run the script `simpleClientFQDN.py`.

### Querylist in client configuration
Send the first query with an empty sid and `count` 1. If you need to repeat the first query, send a `NULL` query with
`epsilon` 0.0 before. This is necessary to obtain a new session ID.

Make separate `query`, `count` and `epsilon` key-value pairs for each subsequent query.
This query list tells which query to execute how many times and its corresponding `epsilon` value.
The query list can contain as many queries as the user wants. 
What is important is that each `query` *MUST* have a `count` and corresponding `epsilon` value.

When the first `query` is set to `NULL`, it returns a new `Session ID` and an empty result.
Following valid queries return the query result in `Result` field followed by the Session ID in the `Session ID` field.
When the `budget` set initially by the user is exceeded, query execution stops. 

VALID FORMAT: `{"query": "", "count": 1}, {"query": "Some Valid Query", "count": x, "epsilon": "x"}, {"query": "Some Valid Query", "count": x, "epsilon": "x"}]`

*NOTE 1: `count` is INTEGER while `epsilon` is DOUBLE*

*NOTE 2: Since the first `query` is sent as `NULL` just to get back a Session ID, it has no `epsilon`
value associated with it.*



### Invalid formats of Querylist

Invalid format #1: `[{"query": "Invalid Query", "count": 1}, {"query": "Some Valid Query", "count": x, "epsilon": "x"}]`

This returns the `Session ID` followed by an `"Error": "syntax error at or near "Invalid"\n"`


Invalid Format #2: `[{"query": "" , "count": 1}, {"query": "Invalid Query", "count": x, "epsilon": "x"}]`

E.g., `querylist = [{"query": "", "count": 1}, {"query": "SELECT COUNT(*) FROM InvalidTable", "count": 1, "epsilon": "x"}]`

This returns the `Session ID` followed by an `"Error": "relation "invalidtable" does not exist\n"`


Invalid Format #3: `[{"query": "Valid/Invalid Query" , "count": 0}, {"query": "Valid/Invalid Query", "count": x, "epsilon": "x"}]`

If `count` in first query is set to `0`, no queries (valid/invalid) thereafter will be executed.
It will return only the `Session ID`.

Invalid Format #4: If `count` of any query is 0, the queries before the query with `count` `0` will execute i.e., query with count 0 will not execute.
The following executes only upto `SELECT COUNT(uid) FROM accounts` i.e., second query.

E.g., `querylist = [{"query": "", "count": 1}, {"query": "SELECT COUNT(*) FROM accounts", "count": 1, "epsilon": "x"},
              {"query": "SELECT COUNT(uid) FROM accounts", "count": 1, "epsilon": "x"},
              {"query": "SELECT COUNT(*) FROM accounts", "count": 0, "epsilon": "x"}]`
              
Invalid Format #4: If any query in between is an inavalid query, all queries are executed and
`Result` field is displayed for valid queries and `Error` field is displayedd for 
invalid queries.

The following executes all queries and displays and `Error` for the second query.

E.g., `querylist = [{"query": "", "count": 1}, {"query": "SELECT COUNT(*) FROM InvalidTable", "count": , "epsilon": "x"},
              {"query": "SELECT COUNT(uid) FROM accounts", "count": 1, "epsilon": "x"}]`


