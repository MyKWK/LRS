package LRS.oceanbase.gen;

import LRS.common.query.ExpectedErrors;
import LRS.common.query.SQLQueryAdapter;
import LRS.oceanbase.OceanBaseGlobalState;

public final class OceanBaseTruncateTableGenerator {

    private OceanBaseTruncateTableGenerator() {
    }

    public static SQLQueryAdapter generate(OceanBaseGlobalState globalState) {
        StringBuilder sb = new StringBuilder("TRUNCATE TABLE ");
        sb.append(globalState.getSchema().getRandomTable().getName());
        return new SQLQueryAdapter(sb.toString(), ExpectedErrors.from("doesn't have this option"));
    }

}
