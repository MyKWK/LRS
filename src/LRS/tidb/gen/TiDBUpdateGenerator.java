package LRS.tidb.gen;

import java.sql.SQLException;
import java.util.List;

import LRS.Randomly;
import LRS.common.gen.AbstractUpdateGenerator;
import LRS.common.query.SQLQueryAdapter;
import LRS.tidb.TiDBErrors;
import LRS.tidb.TiDBExpressionGenerator;
import LRS.tidb.TiDBProvider.TiDBGlobalState;
import LRS.tidb.TiDBSchema.TiDBColumn;
import LRS.tidb.TiDBSchema.TiDBTable;
import LRS.tidb.visitor.TiDBVisitor;

public final class TiDBUpdateGenerator extends AbstractUpdateGenerator<TiDBColumn> {

    private final TiDBGlobalState globalState;
    private TiDBExpressionGenerator gen;

    private TiDBUpdateGenerator(TiDBGlobalState globalState) {
        this.globalState = globalState;
    }

    public static SQLQueryAdapter getQuery(TiDBGlobalState globalState) throws SQLException {
        return new TiDBUpdateGenerator(globalState).generate();
    }

    private SQLQueryAdapter generate() throws SQLException {
        TiDBTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
        List<TiDBColumn> columns = table.getRandomNonEmptyColumnSubset();
        gen = new TiDBExpressionGenerator(globalState).setColumns(table.getColumns());
        sb.append("UPDATE ");
        sb.append(table.getName());
        sb.append(" SET ");
        updateColumns(columns);
        if (Randomly.getBoolean()) {
            sb.append(" WHERE ");
            TiDBErrors.addExpressionErrors(errors);
            sb.append(TiDBVisitor.asString(gen.generateExpression()));
        }
        TiDBErrors.addInsertErrors(errors);

        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected void updateValue(TiDBColumn column) {
        if (Randomly.getBoolean()) {
            sb.append(gen.generateConstant());
        } else {
            sb.append(TiDBVisitor.asString(gen.generateExpression()));
            TiDBErrors.addExpressionErrors(errors);
        }
    }

}
