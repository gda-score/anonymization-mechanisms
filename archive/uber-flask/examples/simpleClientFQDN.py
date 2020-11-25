#!/usr/bin/env python3

"""
client.py sends some data in JSON format to the server.
It also receives the noisy result of the sent query or an error message and displays it
along with the HTTP response code.
"""

import requests
import pprint
import functools
import config as cfg


url = cfg.url  # Get URL of server from config file

headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}  # Headers to be sent in the client request

sid = ''  # Initialize Session ID variable

querylist = cfg.querylist  # Get querylist from config file

# Client establishes a session
session = requests.Session()
session.get_orig, session.get = session.get, functools.partial(session.get, timeout=100)  # Set timeout factor here

# For loop to send queries from querylist
for k in range(0, len(querylist)):
    budget_flag = False
    for j in range(0, querylist[k]['count']):
        request = {}

        # Exception handling in case exception occurs while connecting to server
        try:
            # In the first query of a session, keep the sid empty. The server will assign and return a new sid with the
            # result.
            # In the first query of a session, provide budget and dbname. The server will ignore changes to these in
            # successive queries with the same sid.
            # In the first query of a session, if the query is empty and epsilon is 0.0, the server will assign and
            # return a new sid, as usual. The returned result will be empty.
            request = {
                'sid': sid if sid is not None else '',  # When sid is Null it indicates start of a session
                'dbname': cfg.dbname,
                'budget': cfg.budget,
                'query': querylist[k]['query'],
                'epsilon': querylist[k]['epsilon'],
            }

            # Client stores the response sent by the simpleServer.py
            response = requests.get(url, json=request, headers=headers, timeout=100)

            resp = response.json()  # Convert response sent by server to JSON
            if 'Error' in resp['Server Response']:
                if 'Budget Exceeded' in resp['Server Response']['Error']:
                    pprint.pprint(resp)
                    budget_flag = True
                    break
                else:
                    pprint.pprint(resp)  # Client prints the data returned by the server
            else:
                pprint.pprint(resp)  # Client prints the data returned by the server
                sid = resp['Server Response']['Session ID']  # Set Session ID to value returned by server

        except requests.ConnectionError as e:
            print("Connection Error. Make sure you are connected to Internet.")
            print(str(e))

        except requests.Timeout as e:
            print("Timeout Error")
            print(str(e))

        except requests.RequestException as e:
            print("General Error")
            print(str(e))

        except KeyboardInterrupt:
            print("Program closed")
    if budget_flag:
        break
