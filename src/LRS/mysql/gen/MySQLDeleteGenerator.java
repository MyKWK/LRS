package LRS.mysql.gen;

import java.util.Arrays;

import LRS.Randomly;
import LRS.common.query.ExpectedErrors;
import LRS.common.query.SQLQueryAdapter;
import LRS.mysql.MySQLErrors;
import LRS.mysql.MySQLGlobalState;
import LRS.mysql.MySQLSchema.MySQLTable;
import LRS.mysql.MySQLVisitor;

public class MySQLDeleteGenerator {

    private final StringBuilder sb = new StringBuilder();
    private final MySQLGlobalState globalState;

    public MySQLDeleteGenerator(MySQLGlobalState globalState) {
        this.globalState = globalState;
    }

    public static SQLQueryAdapter delete(MySQLGlobalState globalState) {
        return new MySQLDeleteGenerator(globalState).generate();
    }

    private SQLQueryAdapter generate() {
        MySQLTable randomTable = globalState.getSchema().getRandomTable();
        MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState).setColumns(randomTable.getColumns());
        ExpectedErrors errors = new ExpectedErrors();
        sb.append("DELETE");
        if (Randomly.getBoolean()) {
            sb.append(" LOW_PRIORITY");
        }
        if (Randomly.getBoolean()) {
            sb.append(" QUICK");
        }
        if (Randomly.getBoolean()) {
            sb.append(" IGNORE");
        }
        // TODO: support partitions
        sb.append(" FROM ");
        sb.append(randomTable.getName());
        if (Randomly.getBoolean()) {
            sb.append(" WHERE ");
            sb.append(MySQLVisitor.asString(gen.generateExpression()));
            MySQLErrors.addExpressionErrors(errors);
        }
        errors.addAll(Arrays.asList("doesn't have this option",
                "Truncated incorrect DOUBLE value" /*
                                                    * ignore as a workaround for https://bugs.mysql.com/bug.php?id=95997
                                                    */, "Truncated incorrect INTEGER value",
                "Truncated incorrect DECIMAL value", "Data truncated for functional index"));
        // TODO: support ORDER BY
        return new SQLQueryAdapter(sb.toString(), errors);
    }

}
