class UberServerRequestError(Exception):
    def __init__(self, status_code, response_text):
        self.status_code = status_code
        self.response_text = response_text
        super().__init__(f"Bad server response (status {status_code}): {response_text}")


class UberServerExecutionError(Exception):
    def __init__(self, json):
        self.json = json
        if json is None or not json or 'Error' not in json:
            super().__init__()
        else:
            super().__init__(f"Bad server execution: {json['Error']}")
