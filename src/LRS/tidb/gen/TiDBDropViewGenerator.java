package LRS.tidb.gen;

import LRS.IgnoreMeException;
import LRS.Randomly;
import LRS.common.query.SQLQueryAdapter;
import LRS.tidb.TiDBProvider.TiDBGlobalState;

public final class TiDBDropViewGenerator {

    private TiDBDropViewGenerator() {
    }

    public static SQLQueryAdapter dropView(TiDBGlobalState globalState) {
        if (globalState.getSchema().getTables(t -> t.isView()).size() == 0) {
            throw new IgnoreMeException();
        }
        StringBuilder sb = new StringBuilder("DROP VIEW ");
        if (Randomly.getBoolean()) {
            sb.append("IF EXISTS ");
        }
        sb.append(globalState.getSchema().getRandomTableOrBailout(t -> t.isView()).getName());
        return new SQLQueryAdapter(sb.toString(), null, true);
    }

}
