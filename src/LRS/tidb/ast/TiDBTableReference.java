package LRS.tidb.ast;

import LRS.tidb.TiDBSchema.TiDBTable;

public class TiDBTableReference implements TiDBExpression {

    private final TiDBTable table;

    public TiDBTableReference(TiDBTable table) {
        this.table = table;
    }

    public TiDBTable getTable() {
        return table;
    }

}
