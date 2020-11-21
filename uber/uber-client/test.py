from client import Client


base_url = "http://localhost:5005/uber"
#base_url = "https://db001.gda-score.org/uber"
db_name = "raw_banking"
initial_budget = 6.0

querylist = [
    {"query": "Select count(*) from transactions where operation LIKE '%KLAD' ", "epsilon": 0.5},
    {"query": "Select count(*) from transactions where operation = 'VKLAD' ", "epsilon": 0.5},
    {"query": "Select count(*) from accounts", "epsilon": 2.0},
]

client = Client(base_url)
session = client.create_session(db_name, initial_budget)

for query_dct in querylist:
    session.query(query_dct['query'], query_dct['epsilon'])

session.info()

session.destroy()