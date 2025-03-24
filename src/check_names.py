import os
import sys
from typing import List


def get_java_files(directory_path: str) -> List[str]:
    java_files: List[str] = []
    for root, dirs, files in os.walk(directory_path):
        for f in files:
            if f.endswith('.java'):
                java_files.append(f)
    return java_files


def verify_one_db(prefix: str, files: List[str]):
    print('checking database, name: {0}, files: {1}'.format(prefix, files))
    if len(files) == 0:
        print(prefix + ' directory does not contain any files!', file=sys.stderr)
        exit(-1)
    for f in files:
        if not f.startswith(prefix):
            print('The class name of ' + f + ' does not start with ' + prefix, file=sys.stderr)
            exit(-1)
    print('checking database pass: ', prefix)


def verify_all_dbs(name_to_files: dict[str:List[str]]):
    for db_name, files in name_to_files.items():
        verify_one_db(db_name, files)


if __name__ == '__main__':
    cwd = os.getcwd()
    print("Current working directory: {0}".format(cwd))
    name_to_files: dict[str:List[str]] = dict()
    name_to_files["Citus"] = get_java_files(os.path.join(cwd, "src", "lrs", "citus"))
    name_to_files["ClickHouse"] = get_java_files(os.path.join(cwd, "src", "lrs", "clickhouse"))
    name_to_files["CnosDB"] = get_java_files(os.path.join(cwd, "src", "lrs", "cnosdb"))
    name_to_files["CockroachDB"] = get_java_files(os.path.join(cwd, "src", "lrs", "cockroachdb"))
    name_to_files["Databend"] = get_java_files(os.path.join(cwd, "src", "lrs", "databend"))
    name_to_files["DuckDB"] = get_java_files(os.path.join(cwd, "src", "lrs", "duckdb"))
    name_to_files["H2"] = get_java_files(os.path.join(cwd, "src", "lrs", "h2"))
    name_to_files["HSQLDB"] = get_java_files(os.path.join(cwd, "src", "lrs", "hsqldb"))
    name_to_files["MariaDB"] = get_java_files(os.path.join(cwd, "src", "lrs", "mariadb"))
    name_to_files["Materialize"] = get_java_files(os.path.join(cwd, "src", "lrs", "materialize"))
    name_to_files["MySQL"] = get_java_files(os.path.join(cwd, "src", "lrs", "mysql"))
    name_to_files["OceanBase"] = get_java_files(os.path.join(cwd, "src", "lrs", "oceanbase"))
    name_to_files["Postgres"] = get_java_files(os.path.join(cwd, "src", "lrs", "postgres"))
    name_to_files["Presto"] = get_java_files(os.path.join(cwd, "src", "lrs", "presto"))
    name_to_files["QuestDB"] = get_java_files(os.path.join(cwd, "src", "lrs", "questdb"))
    name_to_files["SQLite3"] = get_java_files(os.path.join(cwd, "src", "lrs", "sqlite3"))
    name_to_files["TiDB"] = get_java_files(os.path.join(cwd, "src", "lrs", "tidb"))
    name_to_files["Y"] = get_java_files(os.path.join(cwd, "src", "lrs", "yugabyte"))  # has both YCQL and YSQL prefixes
    name_to_files["Doris"] = get_java_files(os.path.join(cwd, "src", "lrs", "doris"))
    name_to_files["StoneDB"] = get_java_files(os.path.join(cwd, "src", "lrs", "stonedb"))
    verify_all_dbs(name_to_files)
