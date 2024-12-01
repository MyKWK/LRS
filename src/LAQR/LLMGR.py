import json
import os
import sys
import openai

from LAQR.Rules import RulePool, Rule


def query_gpt4(question):
    openai.api_key = "your-api-key-here"
    openai.base_url = 'https://api2.aigcbest.top/v1/'

    try:
        response = openai.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system",
                 "content": """You are a database expert, and your task is to rewrite the given TiDB query with semantic equivalence. 
                  The rewrite should not consider real-world feasibility, only semantic equivalence. 
                  Each table in the database has distinct column names, no two tables share the same column names. 
                  Primary keys contain 'key' in their name, while other columns are regular columns. Primary keys cannot contain NULL, but may contain 0; other columns may contain NULL values, so you must consider these details. 
                  You need to deduce the data types of columns based on their names. If a column contains only strings and doesn't start with a number, it should be treated as a boolean (false in most cases). 
                  All queries are based on a fixed TPC-D database, which contains 8 tables, and no table shares the same column with another. 
                  Your response should only contain the rewritten queries, formatted as `{rule_name: rewritten_query: rule_description}`, with multiple queries separated by semicolons. Each query should end with a semicolon. 
                  Do not include any other content to facilitate further processing. 
                  You should generate at least 10 rewritten queries, and aim to use SQL functions and diverse rewriting techniques. Don't worry about the real-world feasibility of the queries. 
                  Avoid using backticks (``)."""
                 },
                {"role": "user", "content": question}
            ]
        )
        return response.choices[0].message.content
    except Exception as e:
        return str(e)


def rewrite(query: str):
    schema_filename = 'src/LAQR/schema.json'
    try:
        with open(schema_filename, 'r') as schema_file:
            schema_data = json.load(schema_file)
    except FileNotFoundError:
        return f"Error: Schema file '{schema_filename}' not found."
    rulepool_filename = 'src/LAQR/rulepool.json'
    try:
        with open(rulepool_filename, 'r') as rulepool_file:
            rulepool_data = json.load(rulepool_file)
    except FileNotFoundError:
        return f"Error: RulePool file '{rulepool_filename}' not found."

    prompt = f"""
    Original Query: {query}
    Database Schema Information: {json.dumps(schema_data, ensure_ascii=False)}
    Current RulePool Information: {json.dumps(rulepool_data, ensure_ascii=False)}
    """
    rewritten_queries = query_gpt4(prompt)
    return rewritten_queries


def generate(rewritten_queries: str, count: int):
    rewritten_rule_data = rewritten_queries.split(';')

    new_rules = []
    for rule_data in rewritten_rule_data:
        rule_parts = rule_data.strip('{}').split(':')
        if len(rule_parts) == 3:
            rule_name = rule_parts[0].strip()
            rewritten_query = rule_parts[1].strip()
            rule_description = rule_parts[2].strip()

            new_rules.append({
                'rule_name': rule_name,
                'rewritten_query': rewritten_query,
                'rule_description': rule_description
            })

    rulepool_filename = os.path.join("src", "LAQR", "rule_pool.py")
    try:
        with open(rulepool_filename, 'r') as rulepool_file:
            rulepool_data = json.load(rulepool_file)
    except FileNotFoundError:
        rulepool_data = []

    rulepool = RulePool(filename="rule_pool.json")

    for rule in new_rules:
        if rule['rule_name'] not in [r['rule_name'] for r in rulepool_data]:
            rule_obj = Rule(rule['rule_name'], rule['rewritten_query'], rule['rule_description'])
            rulepool.add_rule(rule_obj)
            rulepool_data.append({
                'rule_name': rule['rule_name'],
                'rewritten_query': rule['rewritten_query'],
                'rule_description': rule['rule_description']
            })
            with open(rulepool_filename, 'w') as rulepool_file:
                json.dump(rulepool_data, rulepool_file)

    schema_filename = os.path.join("src", "LAQR", "schema.json")
    bug_filename = os.path.join("src", "record.txt")

    try:
        with open(schema_filename, 'r') as schema_file:
            schema_data = json.load(schema_file)
    except FileNotFoundError:
        schema_data = {}

    try:
        with open(bug_filename, 'r') as bug_file:
            bug_data = json.load(bug_file)
    except FileNotFoundError:
        bug_data = {}

    prompt = f"""
    Database Schema Information: {json.dumps(schema_data, ensure_ascii=False)}

    RulePool Information: {json.dumps(rulepool_data, ensure_ascii=False)}

    Bug Information: {json.dumps(bug_data, ensure_ascii=False)}

    Please sort the rules based on the likelihood of causing new bugs and their diversity. New bugs should be ranked higher.
    """

    sorted_rules = query_gpt4(prompt)

    sorted_rule_names = sorted_rules.split(';')

    selected_rules = []
    for rule_name in sorted_rule_names[:count]:
        for rule in rulepool_data:
            if rule['rule_name'] == rule_name:
                selected_rules.append(f"{rule['rewritten_query']}-{rule['rule_name']}")

    return "; ".join(selected_rules)


def process_query(query, count):
    rewritten_queries = rewrite(query)
    result_queries = generate(rewritten_queries, count)
    return "; ".join(result_queries)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Error: Not enough arguments provided.")
        sys.exit(1)
    query = sys.argv[1]
    count = int(sys.argv[2])
    result = process_query(query, count)
    print(result)
