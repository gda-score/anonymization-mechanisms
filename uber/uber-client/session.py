class Session:
    def __init__(self, client):
        self.id_ = None
        self.client = client

    def _check_exists(self):
        if self.id_ is None:
            raise RuntimeError(f"This session is either not initialized yet or destroyed already and cannot be used.")

    def init(self, db_name, initial_budget):
        if self.id_ is not None:
            raise RuntimeError(f"This session is already initialized and cannot be initialized again.")
        value_dct = {
            'dbname': db_name,
            'budget': initial_budget,
        }
        response = self.client.post_json("/session/init", value_dct)
        self.id_ = int(response["Session ID"])
        print(f"Session {self.id_}: INIT\n"
              f"   Session initialized for database {db_name} with initial budget {initial_budget}")
        return response

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
        if epsilon <= 0.0:
            raise ValueError(f"epsilon must be greater than zero, was {epsilon}")
        value_dct = {
            'sid': self.id_,
            'query': query,
            'epsilon': epsilon,
        }
        response = self.client.post_json("/session/query", value_dct)
        print(f"Session {self.id_}: QUERY\n"
              f"   The following query was sent to the server:\n"
              f"{query}\n"
              f"   The epsilon to be used for the query was specified with: {epsilon}\n"
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
