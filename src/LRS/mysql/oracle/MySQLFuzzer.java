package LRS.mysql.oracle;

import LRS.Randomly;
import LRS.common.oracle.TestOracle;
import LRS.common.query.SQLQueryAdapter;
import LRS.mysql.MySQLGlobalState;
import LRS.mysql.MySQLVisitor;
import LRS.mysql.gen.MySQLRandomQuerySynthesizer;

public class MySQLFuzzer implements TestOracle<MySQLGlobalState> {

    private final MySQLGlobalState globalState;

    public MySQLFuzzer(MySQLGlobalState globalState) {
        this.globalState = globalState;
    }

    @Override
    public void check() throws Exception {
        String s = MySQLVisitor.asString(MySQLRandomQuerySynthesizer.generate(globalState, Randomly.smallNumber() + 1))
                + ';';
        try {
            globalState.executeStatement(new SQLQueryAdapter(s));
            globalState.getManager().incrementSelectQueryCount();
        } catch (Error e) {

        }
    }

}
