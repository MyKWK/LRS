package sqlancer.sqlite3.oracle.tlp;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.gen.ExpressionGenerator;
import sqlancer.common.oracle.TernaryLogicPartitioningOracleBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.sqlite3.SQLite3Errors;
import sqlancer.sqlite3.SQLite3GlobalState;
import sqlancer.sqlite3.ast.SQLite3Expression;
import sqlancer.sqlite3.ast.SQLite3Expression.Join;
import sqlancer.sqlite3.ast.SQLite3Expression.SQLite3ColumnName;
import sqlancer.sqlite3.ast.SQLite3Select;
import sqlancer.sqlite3.gen.SQLite3Common;
import sqlancer.sqlite3.gen.SQLite3ExpressionGenerator;
import sqlancer.sqlite3.schema.SQLite3Schema;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Table;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Tables;

public class SQLite3TLPBase extends TernaryLogicPartitioningOracleBase<SQLite3Expression, SQLite3GlobalState>
        implements TestOracle<SQLite3GlobalState> {

    SQLite3Schema s;
    SQLite3Tables targetTables;
    SQLite3ExpressionGenerator gen;
    SQLite3Select select;

    public SQLite3TLPBase(SQLite3GlobalState state) {
        super(state);
        SQLite3Errors.addExpectedExpressionErrors(errors);
        SQLite3Errors.addQueryErrors(errors);
    }

    @Override
    public void check() throws Exception {
        s = state.getSchema();
        targetTables = s.getRandomTableNonEmptyTables();
        gen = new SQLite3ExpressionGenerator(state).setColumns(targetTables.getColumns());
        initializeTernaryPredicateVariants();

        List<SQLite3Expression> fetchColumns = new ArrayList<>();
        fetchColumns = generateFetchColumns();
        select = new SQLite3Select();
        select.setFetchColumns(fetchColumns);
        List<SQLite3Table> tables = targetTables.getTables();
        List<Join> joinStatements = gen.getRandomJoinClauses(tables);
        List<SQLite3Expression> tableRefs = SQLite3Common.getTableRefs(tables, s);
        select.setJoinClauses(joinStatements.stream().collect(Collectors.toList()));
        select.setFromList(tableRefs);
        select.setWhereClause(null);
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression());
        }
        if (Randomly.getBoolean()) {
            select.setGroupByClause(fetchColumns);
            if (Randomly.getBoolean()) {
                select.setHavingClause(gen.generateExpression());
            }
        }

    }

    List<SQLite3Expression> generateFetchColumns() {
        List<SQLite3Expression> columns = new ArrayList<>();
        columns = Randomly.nonEmptySubset(targetTables.getColumns()).stream()
                .map(c -> new SQLite3ColumnName(c, null)).collect(Collectors.toList());
        return columns;
    }

    @Override
    protected ExpressionGenerator<SQLite3Expression> getGen() {
        return gen;
    }

}
