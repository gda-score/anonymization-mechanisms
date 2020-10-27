#!/usr/bin/python3

"""
server.py listens for incoming requests from the client and extracts the parameters from the url sent by the client.
It then writes the extracted parameters to a file in JSON format.
It waits for the Uber Tool to write the noisy result to a file and reads the file.
It sends the result back to the client.
"""

import json
import pathlib
import time

from random import randint

from flask import Flask, request
from flask_restful import Api, Resource


app = Flask(__name__)
api = Api(app)


# The path where Uber Tool creates the result.txt files
uber_path = pathlib.Path.cwd().parent.joinpath(pathlib.Path("files", "noisyres"))

# The path to where simpleServer.py writes files containing JSON data
request_path = pathlib.Path.cwd().parent.joinpath(pathlib.Path("files", "jsonreq"))

# Variable to store the result file count in Uber Tool directory
file_count = len(list(uber_path.glob('result*.txt')))

# Variable to store the query result generated by Uber Tool
query_result = []


# Method to read .txt files generated by Uber Tool
def read_file():
    global file_count
    global query_result

    # Check if new '*.txt' has been generated by Uber Tool
    file_lst = list(uber_path.glob('result*.txt'))
    if file_count != len(file_lst):
        file_count = len(file_lst)
        latest_file = max(file_lst, key=lambda f: f.stat().st_ctime)  # Get the latest file by timestamp
        print(f"\nLast modified file: {latest_file.absolute()}")
        with open(latest_file, "r") as infile:  # Read the latest file to get the query result and return it
            return infile.readlines()

    return None


def wait_file():
    # Calls read_file method continuously to check if query_result value has changed
    while True:
        qr = read_file()  # Store the returned query_result
        if qr is not None:  # Check if query result value is not None
            return qr  # Return the query result
        else:
            time.sleep(5)


# Method to write Client request (url parameters) in JSON to a file
def write_file(response, sid):
    global query_result

    # Write JSON to file
    outpath = request_path.joinpath(f"data{sid}.json")
    with open(outpath, 'w') as outfile:
        json.dump(response, outfile)
    print(f"\nJSON File created: {outpath.absolute()}")


""" Server method to handle incoming data.
Calls writeFile method to write the url parameters in JSON to a file.
Returns the query result (Noisy Result) as response to Client.
"""


class GetParams(Resource):
    @staticmethod
    def request_json():
        print(f"Request sent by client: {request}")
        if not request.is_json:
            raise ValueError(f"Client request is not valid JSON: {request}")
        client_json = request.json
        if client_json is None:
            raise ValueError(f"Failed to parse client request as JSON: {request}")
        print(f"JSON sent by client: {client_json}")
        return client_json

    @staticmethod
    def create_new(query, initial_budget, epsilon, dbname):
        print("New session started")
        used_budget = 0.0  # Initialize used_budget to 0.0
        sid = randint(0, 1000000000)  # Generate Session ID for new session
        # Create new client request with used_budget
        create_client = {
            'query': query,
            'budget': initial_budget,
            'epsilon': epsilon,
            'sid': sid,
            'used_budget': used_budget,
            'dbname': dbname
        }
        write_file(create_client, sid)  # Write updated client request to a file
        _ = wait_file()
        result_json = {
            "Server Response": {
                "Session ID": sid
            }
        }
        return result_json

    @staticmethod
    def handle_existing(sid, query, epsilon):
        inpath = request_path.joinpath(f"data{sid}.json")
        with open(inpath, "r") as infile:
            data = json.load(infile)

        if data is None:
            raise FileNotFoundError(f"Session {sid}: Did not find existing request file: {inpath.absolute()}")

        # Extract the previous value of used_budget and
        # update the value by adding Epsilon value to it
        tmp_used_budget = data["used_budget"]
        data["used_budget"] = float(tmp_used_budget) + float(epsilon)
        updated_used_budget = data["used_budget"]

        # Extract the original budget and restrict the Client from changing the budget
        file_budget = data['budget']
        file_dbname = data['dbname']

        # Update the Client request with used_budget
        update_client = {
            'query': query,
            'budget': file_budget,
            'epsilon': epsilon,
            'sid': sid,
            'used_budget': updated_used_budget,
            'dbname': file_dbname
        }

        # If used_budget exceeds the threshold file_budget raise an error
        if float(file_budget) < float(updated_used_budget):
            raise ValueError(f"Session {sid}: Budget Exceeded - Cannot process queries")

        write_file(update_client, sid)
        result = wait_file()
        result = result[0]

        # If an error/exception is contained in the Uber Tool results raise an error
        if 'error' in result.lower() or 'exception' in result.lower():
            raise ValueError(f"Session {sid}: Error/Exception in Uber Tool result: {result}")

        result_json = {
            "Server Response": {
                "Result": result,
                "Session ID": sid
            }
        }
        return result_json

    def handle_get(self):
        client_json = self.request_json()  # Stores the request in JSON format

        # Extract the values from the JSON payload sent by Client
        query = client_json['query']  # Query
        initial_budget = client_json['budget']  # Initial budget value
        epsilon = client_json['epsilon']  # Epsilon value
        sid = client_json['sid']  # Session ID
        dbname = client_json['dbname']  # Database name

        print(f"Client provided epsilon: {epsilon}")

        # If Session ID (sid) is not present in payload i.e., it is a new session then
        # start a new session and assign a randomly generated Session ID
        if not sid:
            return self.create_new(query, initial_budget, epsilon, dbname)

        # Otherwise, handle the request in the existing session
        return self.handle_existing(sid, query, epsilon)

    def get(self):
        try:
            result_json = self.handle_get()
        except Exception as e:
            print(f"An error occurred: {repr(e)}")
            result_json = {
                "Server Response": {
                    'Error': repr(e),
                }
            }
        return result_json


api.add_resource(GetParams, '/data')  # Route for get()

if __name__ == '__main__':
    app.run(host='139.19.217.24', port=5002)
