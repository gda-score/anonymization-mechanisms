import requests

from session import Session


class Client:
    def __init__(self, base_url):
        self.base_url = base_url

    def post_json(self, path, value_dct):
        url = self.base_url + path
        response = requests.post(url, json=value_dct)
        if response.status_code != 200:
            raise RuntimeError(f"Server encountered a problem with the request.\n"
                               f"Response Code: {response.status_code}\n"
                               f"Message: {response.content}")
        json = response.json()
        if "Exception" in json:
            raise RuntimeError(f"Server encountered an Exception\n"
                               f"Session ID: {json['Session ID']}\n"
                               f"Exception: {json['Exception']}\n"
                               f"Stack Trace: {json['Stack Trace']}")
        return json

    def create_session(self, db_name, initial_budget):
        return Session(db_name, initial_budget, self)
