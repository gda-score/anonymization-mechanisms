from client import Client
from error import UberServerRequestError, UberServerExecutionError


base_url = "https://db001.gda-score.org/uber"
db_name = "raw_banking"
initial_budget = 6.0

querylist = [
    {"query": "Select count(*) from transactions where operation LIKE '%KLAD' ", "epsilon": 0.5},
    {"query": "Select count(*) from transactions where operation = 'VKLAD' ", "epsilon": 0.5},
    {"query": "Select count(*) from accounts", "epsilon": 2.0},
    {"query": "Select count(*) from accounts where 1/(account_id - 2864) = 0", "epsilon": 2.0},
    {"query": "Select count(*) from accounts", "epsilon": 4.0},
]

client = Client(base_url)
session = client.create_session()

try:
    session.init(db_name, initial_budget)
    for query_dct in querylist:
        response = session.query(query_dct['query'], query_dct['epsilon'])
        print(response)
    session.info()
except UberServerRequestError as re:
    print(f"ERROR: {re}\n"
          f"   The status code is: {re.status_code}\n"
          f"   The response text is: {re.response_text}")
except UberServerExecutionError as ee:
    print(f"ERROR: {ee}\n"
          f"   The session ID is: {ee.json['Session ID']}\n"
          f"   The error message is: {ee.json['Error']}\n"
          f"   The full stack trace is: {ee.json['Stack Trace']}")
finally:
    try:
        session.destroy()
    except (UberServerRequestError, UberServerExecutionError) as e:
        print(f"ERROR in finally: {e}")
