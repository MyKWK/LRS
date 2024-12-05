package LRS.oceanbase.oracle;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import LRS.common.gen.ExpressionGenerator;
import LRS.common.oracle.TernaryLogicPartitioningOracleBase;
import LRS.common.oracle.TestOracle;
import LRS.oceanbase.OceanBaseErrors;
import LRS.oceanbase.OceanBaseGlobalState;
import LRS.oceanbase.OceanBaseSchema;
import LRS.oceanbase.OceanBaseSchema.OceanBaseTable;
import LRS.oceanbase.OceanBaseSchema.OceanBaseTables;
import LRS.oceanbase.ast.OceanBaseColumnReference;
import LRS.oceanbase.ast.OceanBaseExpression;
import LRS.oceanbase.ast.OceanBaseSelect;
import LRS.oceanbase.ast.OceanBaseTableReference;
import LRS.oceanbase.gen.OceanBaseExpressionGenerator;
import LRS.oceanbase.gen.OceanBaseHintGenerator;

public abstract class OceanBaseTLPBase
        extends TernaryLogicPartitioningOracleBase<OceanBaseExpression, OceanBaseGlobalState>
        implements TestOracle<OceanBaseGlobalState> {

    OceanBaseSchema s;
    OceanBaseTables targetTables;
    OceanBaseExpressionGenerator gen;
    OceanBaseSelect select;

    public OceanBaseTLPBase(OceanBaseGlobalState state) {
        super(state);
        OceanBaseErrors.addExpressionErrors(errors);
        errors.add("value is out of range");
    }
    List<OceanBaseExpression> generateFetchColumns() {
        return Arrays.asList(OceanBaseColumnReference.create(targetTables.getColumns().get(0), null));
    }

    @Override
    protected ExpressionGenerator<OceanBaseExpression> getGen() {
        return gen;
    }

}
