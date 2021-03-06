import requests

from error import UberServerRequestError, UberServerExecutionError
from session import Session


class Client:
    def __init__(self, base_url):
        self.base_url = base_url

    def post_json(self, path, value_dct):
        url = self.base_url + path
        response = requests.post(url, json=value_dct)
        if response.status_code != 200:
            raise UberServerRequestError(response.status_code, response.text)
        json = response.json()
        if 'Error' in json:
            raise UberServerExecutionError(json)
        return json

    def create_session(self):
        return Session(self)
