package LRS.tidb.ast;

import LRS.tidb.TiDBSchema.TiDBColumn;

public class TiDBColumnReference implements TiDBExpression {

    private final TiDBColumn c;

    public TiDBColumnReference(TiDBColumn c) {
        this.c = c;
    }

    public TiDBColumn getColumn() {
        return c;
    }

}
