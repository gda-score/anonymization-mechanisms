# Overview

This repository is derived from Uber Differential Privacy Tool (https://github.com/uber/sql-differential-privacy) and also contains a Client-Server system written in Python for sending queries.
The files can be found under: code/anon-methods/sql-differential-privacy/src/main/scala/examples/

# Working with the system

For the FLASK webserver and client, please see the Readme in the examples subfolder.

To work with ElasticSensitivityExample.scala - 
1. In Line 57, `val database = Schema.getDatabase("raw_banking")`, enter the database name from the schema depending on which DB to run queries on.
2. In Line 66, provide the path where simpleServer.py is generating the JSON files (should be same path as Line 54 in simpleServer.py). 
3. In Line 130,  provide the appropriate credentials in DB connection section (`val con_str`).

To work with QueryRewritingExample.scala - 
1. In Line 22, `val database = Schema.getDatabase("raw_banking")`, enter the database name from the schema depending on which DB to run queries on.
2. In Line 31, provide the path where simpleServer.py is generating the JSON files (should be same path as Line 54 in simpleServer.py). 
3. In Line 95,  provide the appropriate credentials in DB connection section (`val con_str`).


After these changes, run ElasticSensitivityExample.scala and/or QueryRewritingExample.scala scripts along with simpleServer.py.
Run the Client script to send query, budget, epsilon and sid values. 







