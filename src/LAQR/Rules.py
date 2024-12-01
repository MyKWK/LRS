import json
import os


class Rule:
    def __init__(self, rule_id, rule_str, explanation):
        self.rule_id = rule_id
        self.rule_str = rule_str
        self.explanation = explanation

    def to_dict(self):
        return {
            'rule_id': self.rule_id,
            'rule_str': self.rule_str,
            'explanation': self.explanation
        }

    @classmethod
    def from_dict(cls, data):
        return cls(data['rule_id'], data['rule_str'], data['explanation'])


class RulePool:
    def __init__(self, filename="rule_pool.json"):
        self.filename = filename
        self.pool = []
        self.load_rules_from_file()

    def add_rule(self, rule: Rule):
        self.pool.append(rule)
        self.save_rules_to_file()

def save_rules_to_file(self):
    file_path = os.path.join("src", "LAQR", self.filename)
    with open(file_path, "w") as file:
        json.dump([rule.to_dict() for rule in self.pool], file)

def load_rules_from_file(self):
    try:
        file_path = os.path.join("src", "LAQR", self.filename)
        with open(file_path, "r") as file:
            rules_data = json.load(file)
            self.pool = [Rule.from_dict(rule_data) for rule_data in rules_data]
    except FileNotFoundError:
        self.pool = []

    def get_all_rules(self):
        return self.pool

    def get_rule_by_id(self, rule_id):
        return next((rule for rule in self.pool if rule.rule_id == rule_id), None)

    def remove_rule(self, rule_id):
        rule = self.get_rule_by_id(rule_id)
        if rule:
            self.pool.remove(rule)
            self.save_rules_to_file()
