import json
import os
import sys
import openai
import re
import numpy as np
import hnswlib
import pickle
import os
import sqlparse
from LAQR.Rules import RulePool, Rule
from sentence_transformers import SentenceTransformer

def find_similar_templates(abstracted_query, top_k=5):
    model_name = 'all-MiniLM-L6-v2'
    embedding_dim = 384
    index_path = 'sql_templates_hnsw.bin'
    mapping_path = 'template_id_mapping.pkl'

    model = SentenceTransformer(model_name)

    if not os.path.exists(index_path) or not os.path.exists(mapping_path):
        raise FileNotFoundError("Index files not found. Please build the index first.")

    query_vector = model.encode([abstracted_query])[0]

    index = hnswlib.Index(space='cosine', dim=embedding_dim)
    index.load_index(index_path)

    with open(mapping_path, 'rb') as f:
        id_to_template_map = pickle.load(f)

    labels, distances = index.knn_query(query_vector.reshape(1, -1), k=top_k)

    results = []
    for idx, (label, distance) in enumerate(zip(labels[0], distances[0])):
        template_id = id_to_template_map[label]
        similarity_score = 1 - distance
        results.append({
            'template_id': template_id,
            'similarity_score': similarity_score
        })

    return results

def build_template_index(templates_data):
    model_name = 'all-MiniLM-L6-v2'
    embedding_dim = 384

    model = SentenceTransformer(model_name)

    template_texts = [item['template_text'] for item in templates_data]
    template_ids = [item['template_id'] for item in templates_data]

    embeddings = model.encode(template_texts)

    index = hnswlib.Index(space='cosine', dim=embedding_dim)
    index.init_index(max_elements=len(template_texts), ef_construction=200, M=16)
    index.add_items(embeddings, np.arange(len(template_texts)))

    index.set_ef(50)
    index.save_index('sql_templates_hnsw.bin')

    id_to_template_map = {i: template_id for i, template_id in enumerate(template_ids)}
    with open('template_id_mapping.pkl', 'wb') as f:
        pickle.dump(id_to_template_map, f)

    return "Index built successfully"

def get_most_similar_template_id(abstracted_query):
    similar_templates = find_similar_templates(abstracted_query, top_k=1)
    if similar_templates:
        return similar_templates[0]['template_id']
    return None

def abstract_sql_query(query):
    formatted_query = sqlparse.format(query, keyword_case='upper', identifier_case='lower', reindent=True)

    parsed = sqlparse.parse(formatted_query)[0]

    abstracted = re.sub(r"'[^']*'", "'VALUE'", formatted_query)

    abstracted = re.sub(r'(?<![a-zA-Z0-9_])(\d+(\.\d+)?)(?![a-zA-Z0-9_])', 'NUM', abstracted)

    abstracted = re.sub(r'IN\s*\([^)]*\)', 'IN (LIST)', abstracted)

    abstracted = re.sub(r'BETWEEN\s+\S+\s+AND\s+\S+', 'BETWEEN NUM AND NUM', abstracted)

    date_patterns = [
        r'\d{4}-\d{2}-\d{2}',
        r'\d{2}/\d{2}/\d{4}',
        r'\d{2}-\d{2}-\d{4}'
    ]
    for pattern in date_patterns:
        abstracted = re.sub(pattern, 'DATE', abstracted)

    abstracted = re.sub(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}', 'TIMESTAMP', abstracted)

    query_type = _identify_query_type(parsed)

    abstracted = f"{query_type}:\n{abstracted}"

    return abstracted

def _identify_query_type(parsed_stmt):
    first_token = parsed_stmt.token_first()
    if first_token is None:
        return "UNKNOWN"

    keyword = first_token.value.upper()

    if keyword == 'SELECT':
        has_join = any(token.value.upper().find('JOIN') >= 0 for token in parsed_stmt.tokens)

        has_group_by = any(token.value.upper().find('GROUP BY') >= 0 for token in parsed_stmt.tokens)

        has_aggregation = any(token.value.upper().find('COUNT(') >= 0
                             or token.value.upper().find('SUM(') >= 0
                             or token.value.upper().find('AVG(') >= 0
                             or token.value.upper().find('MAX(') >= 0
                             or token.value.upper().find('MIN(') >= 0
                             for token in parsed_stmt.flatten())

        if has_join:
            if has_group_by or has_aggregation:
                return "ANALYTICAL_JOIN_QUERY"
            return "JOIN_QUERY"
        elif has_group_by or has_aggregation:
            return "AGGREGATION_QUERY"
        else:
            return "SIMPLE_SELECT_QUERY"

    elif keyword == 'INSERT':
        return "INSERT_QUERY"
    elif keyword == 'UPDATE':
        return "UPDATE_QUERY"
    elif keyword == 'DELETE':
        return "DELETE_QUERY"
    elif keyword == 'CREATE':
        if any(token.value.upper().find('TABLE') >= 0 for token in parsed_stmt.tokens):
            return "CREATE_TABLE_QUERY"
        elif any(token.value.upper().find('INDEX') >= 0 for token in parsed_stmt.tokens):
            return "CREATE_INDEX_QUERY"
        else:
            return "CREATE_QUERY"
    elif keyword == 'ALTER':
        return "ALTER_QUERY"
    elif keyword == 'DROP':
        return "DROP_QUERY"
    else:
        return "UNKNOWN_QUERY"


def abstract_sql_query(query):
    formatted_query = sqlparse.format(query, keyword_case='upper', identifier_case='lower', reindent=True)

    parsed = sqlparse.parse(formatted_query)[0]

    abstracted = re.sub(r"'[^']*'", "'VALUE'", formatted_query)

    abstracted = re.sub(r'(?<![a-zA-Z0-9_])(\d+(\.\d+)?)(?![a-zA-Z0-9_])', 'NUM', abstracted)

    abstracted = re.sub(r'IN\s*\([^)]*\)', 'IN (LIST)', abstracted)

    abstracted = re.sub(r'BETWEEN\s+\S+\s+AND\s+\S+', 'BETWEEN NUM AND NUM', abstracted)

    date_patterns = [
        r'\d{4}-\d{2}-\d{2}',
        r'\d{2}/\d{2}/\d{4}',
        r'\d{2}-\d{2}-\d{4}'
    ]
    for pattern in date_patterns:
        abstracted = re.sub(pattern, 'DATE', abstracted)

    abstracted = re.sub(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}', 'TIMESTAMP', abstracted)

    query_type = _identify_query_type(parsed)

    abstracted = f"{query_type}:\n{abstracted}"

    return abstracted

def _identify_query_type(parsed_stmt):
    first_token = parsed_stmt.token_first()
    if first_token is None:
        return "UNKNOWN"

    keyword = first_token.value.upper()

    if keyword == 'SELECT':
        has_join = any(token.value.upper().find('JOIN') >= 0 for token in parsed_stmt.tokens)

        has_group_by = any(token.value.upper().find('GROUP BY') >= 0 for token in parsed_stmt.tokens)

        has_aggregation = any(token.value.upper().find('COUNT(') >= 0
                             or token.value.upper().find('SUM(') >= 0
                             or token.value.upper().find('AVG(') >= 0
                             or token.value.upper().find('MAX(') >= 0
                             or token.value.upper().find('MIN(') >= 0
                             for token in parsed_stmt.flatten())

        if has_join:
            if has_group_by or has_aggregation:
                return "ANALYTICAL_JOIN_QUERY"
            return "JOIN_QUERY"
        elif has_group_by or has_aggregation:
            return "AGGREGATION_QUERY"
        else:
            return "SIMPLE_SELECT_QUERY"

    elif keyword == 'INSERT':
        return "INSERT_QUERY"
    elif keyword == 'UPDATE':
        return "UPDATE_QUERY"
    elif keyword == 'DELETE':
        return "DELETE_QUERY"
    elif keyword == 'CREATE':
        if any(token.value.upper().find('TABLE') >= 0 for token in parsed_stmt.tokens):
            return "CREATE_TABLE_QUERY"
        elif any(token.value.upper().find('INDEX') >= 0 for token in parsed_stmt.tokens):
            return "CREATE_INDEX_QUERY"
        else:
            return "CREATE_QUERY"
    elif keyword == 'ALTER':
        return "ALTER_QUERY"
    elif keyword == 'DROP':
        return "DROP_QUERY"
    else:
        return "UNKNOWN_QUERY"


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
