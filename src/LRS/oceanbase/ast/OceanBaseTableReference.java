package LRS.oceanbase.ast;

import LRS.oceanbase.OceanBaseSchema.OceanBaseTable;

public class OceanBaseTableReference implements OceanBaseExpression {

    private final OceanBaseTable table;

    public OceanBaseTableReference(OceanBaseTable table) {
        this.table = table;
    }

    public OceanBaseTable getTable() {
        return table;
    }

}
