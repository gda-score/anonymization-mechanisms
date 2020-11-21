class Session:
    def __init__(self, db_name, initial_budget, client):
        value_dct = {
            'dbname': db_name,
            'budget': initial_budget,
        }
        response = client.post_json("/session/init", value_dct)
        self.id_ = int(response["Session ID"])
        self.client = client
        print(f"Session {self.id_}: CREATE\n"
              f"   Session created for database {db_name} with initial budget {initial_budget}")

    def _check_exists(self):
        if self.id_ is None:
            raise RuntimeError(f"This session was destroyed and can no longer be used.")

    def info(self):
        self._check_exists()
        value_dct = {
            'sid': self.id_,
        }
        response = self.client.post_json("/session/info", value_dct)
        print(f"Session {self.id_}: INFO\n"
              f"   Session info requested. Session is for database {response['DB Name']} "
              f"with remaining budget {response['Remaining Budget']}")
        return response

    def query(self, query, epsilon):
        self._check_exists()
        value_dct = {
            'sid': self.id_,
            'query': query,
            'epsilon': epsilon,
        }
        response = self.client.post_json("/session/query", value_dct)
        print(f"Session {self.id_}: QUERY\n"
              f"   The following query was sent to the server:\n"
              f"{query}\n"
              f"   The server rewrote the query into:\n"
              f"{response['Private SQL']}\n"
              f"   The server returned result {response['Result']}.\n"
              f"   The remaining budget is {response['Remaining Budget']}.")
        return response

    def destroy(self):
        self._check_exists()
        value_dct = {
            'sid': self.id_,
        }
        response = self.client.post_json("/session/destroy", value_dct)
        print(f"Session {self.id_}: DESTROY\n"
              f"   Session destroyed. Session was for database {response['DB Name']} and had "
              f"{response['Remaining Budget']} budget remaining.")
        self.id_ = None
        return response
