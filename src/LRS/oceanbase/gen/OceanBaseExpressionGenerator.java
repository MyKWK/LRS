package LRS.oceanbase.gen;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import LRS.Randomly;
import LRS.common.gen.UntypedExpressionGenerator;
import LRS.oceanbase.OceanBaseGlobalState;
import LRS.oceanbase.OceanBaseSchema;
import LRS.oceanbase.OceanBaseSchema.OceanBaseColumn;
import LRS.oceanbase.OceanBaseSchema.OceanBaseRowValue;
import LRS.oceanbase.ast.OceanBaseBinaryComparisonOperation;
import LRS.oceanbase.ast.OceanBaseBinaryComparisonOperation.BinaryComparisonOperator;
import LRS.oceanbase.ast.OceanBaseBinaryLogicalOperation;
import LRS.oceanbase.ast.OceanBaseBinaryLogicalOperation.OceanBaseBinaryLogicalOperator;
import LRS.oceanbase.ast.OceanBaseCastOperation;
import LRS.oceanbase.ast.OceanBaseColumnReference;
import LRS.oceanbase.ast.OceanBaseComputableFunction;
import LRS.oceanbase.ast.OceanBaseComputableFunction.OceanBaseFunction;
import LRS.oceanbase.ast.OceanBaseConstant;
import LRS.oceanbase.ast.OceanBaseConstant.OceanBaseDoubleConstant;
import LRS.oceanbase.ast.OceanBaseExists;
import LRS.oceanbase.ast.OceanBaseExpression;
import LRS.oceanbase.ast.OceanBaseInOperation;
import LRS.oceanbase.ast.OceanBaseStringExpression;
import LRS.oceanbase.ast.OceanBaseUnaryPostfixOperation;
import LRS.oceanbase.ast.OceanBaseUnaryPrefixOperation;
import LRS.oceanbase.ast.OceanBaseUnaryPrefixOperation.OceanBaseUnaryPrefixOperator;

public class OceanBaseExpressionGenerator extends UntypedExpressionGenerator<OceanBaseExpression, OceanBaseColumn> {

    private OceanBaseGlobalState state;
    private OceanBaseRowValue rowVal;

    public OceanBaseExpressionGenerator(OceanBaseGlobalState state) {
        this.state = state;
    }

    public OceanBaseExpressionGenerator setCon(Connection con) {
        return this;
    }

    public OceanBaseExpressionGenerator setState(OceanBaseGlobalState state) {
        this.state = state;
        return this;
    }

    public OceanBaseExpressionGenerator setOceanBaseColumns(List<OceanBaseSchema.OceanBaseColumn> columns) {
        return this;
    }

    public OceanBaseExpressionGenerator setRowVal(OceanBaseRowValue rowVal) {
        this.rowVal = rowVal;
        return this;
    }

    private enum Actions {
        COLUMN, LITERAL, UNARY_PREFIX_OPERATION, UNARY_POSTFIX, COMPUTABLE_FUNCTION, BINARY_LOGICAL_OPERATOR,
        BINARY_COMPARISON_OPERATION, CAST, IN_OPERATION, EXISTS;
    }

    @Override
    public OceanBaseExpression generateExpression(int depth) {
        if (depth >= state.getOptions().getMaxExpressionDepth()) {
            return generateLeafNode();
        }
        switch (Randomly.fromOptions(Actions.values())) {
        case COLUMN:
            return generateColumn();
        case LITERAL:
            return generateConstant();
        case UNARY_PREFIX_OPERATION:
            OceanBaseExpression subExpr = generateExpression(depth + 1);
            OceanBaseUnaryPrefixOperator random = OceanBaseUnaryPrefixOperator.getRandom();
            return new OceanBaseUnaryPrefixOperation(subExpr, random);
        case UNARY_POSTFIX:
            return new OceanBaseUnaryPostfixOperation(generateExpression(depth + 1),
                    Randomly.fromOptions(OceanBaseUnaryPostfixOperation.UnaryPostfixOperator.values()),
                    Randomly.getBoolean());
        case COMPUTABLE_FUNCTION:
            return getComputableFunction(depth + 1);
        case BINARY_LOGICAL_OPERATOR:
            return new OceanBaseBinaryLogicalOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    OceanBaseBinaryLogicalOperator.getRandom());
        case BINARY_COMPARISON_OPERATION:
            return new OceanBaseBinaryComparisonOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    BinaryComparisonOperator.getRandom());
        case CAST:
            return new OceanBaseCastOperation(generateExpression(depth + 1),
                    OceanBaseCastOperation.CastType.getRandom());
        case IN_OPERATION:
            OceanBaseExpression expr = generateExpression(depth + 1);
            List<OceanBaseExpression> rightList = new ArrayList<>();
            for (int i = 0; i < 1 + Randomly.smallNumber(); i++) {
                rightList.add(generateExpression(depth + 1));
            }
            return new OceanBaseInOperation(expr, rightList, Randomly.getBoolean());
        case EXISTS:
            return getExists();
        default:
            throw new AssertionError();
        }
    }

    private OceanBaseExpression getExists() {
        if (Randomly.getBoolean()) {
            return new OceanBaseExists(new OceanBaseStringExpression("SELECT 1", OceanBaseConstant.createTrue()));
        } else {
            return new OceanBaseExists(
                    new OceanBaseStringExpression("SELECT 1 from dual wHERE FALSE", OceanBaseConstant.createFalse()));
        }
    }

    private OceanBaseExpression getComputableFunction(int depth) {
        OceanBaseFunction func = OceanBaseFunction.getRandomFunction();
        int nrArgs = func.getNrArgs();
        if (func.isVariadic()) {
            nrArgs += Randomly.smallNumber();
        }
        OceanBaseExpression[] args = new OceanBaseExpression[nrArgs];
        for (int i = 0; i < args.length; i++) {
            args[i] = generateExpression(depth + 1);
        }
        return new OceanBaseComputableFunction(func, args);
    }

    private enum ConstantType {
        INT, NULL, STRING, DOUBLE;

        public static ConstantType[] valuesPQS() {
            return new ConstantType[] { INT, NULL, STRING };
        }
    }

    @Override
    public OceanBaseExpression generateConstant() {
        ConstantType[] values;
        if (state.usesPQS()) {
            values = ConstantType.valuesPQS();
        } else {
            values = ConstantType.values();
        }
        OceanBaseConstant constant;
        switch (Randomly.fromOptions(values)) {
        case INT:
            return OceanBaseConstant.createIntConstant((int) state.getRandomly().getInteger());
        case NULL:
            return OceanBaseConstant.createNullConstant();
        case STRING:
            String string = state.getRandomly().getString().replace("\\", "").replace("\n", "").replace("\t", "");
            constant = OceanBaseConstant.createStringConstant(string);
            return constant;
        case DOUBLE:
            double val = state.getRandomly().getDouble();
            constant = new OceanBaseDoubleConstant(val);
            return constant;
        default:
            throw new AssertionError();
        }
    }

    @Override
    public OceanBaseExpression generateColumn() {
        OceanBaseColumn c = Randomly.fromList(columns);
        OceanBaseConstant val;
        if (rowVal == null) {
            val = OceanBaseConstant.createNullConstant();
        } else {
            val = rowVal.getValues().get(c);
        }
        return OceanBaseColumnReference.create(c, val);
    }

    public OceanBaseExpression generateConstant(OceanBaseColumn col) {
        OceanBaseConstant constant;
        switch (col.getType().name()) {
        case "INT":
            return OceanBaseConstant.createIntConstant((int) state.getRandomly().getInteger());
        case "NULL":
            return OceanBaseConstant.createNullConstant();
        case "VARCHAR":
            String string = state.getRandomly().getString().replace("\\", "").replace("\n", "").replace("\t", "");
            constant = OceanBaseConstant.createStringConstant(string);
            return constant;
        case "DOUBLE":
            double val = state.getRandomly().getDouble();
            constant = new OceanBaseDoubleConstant(val);
            return constant;
        case "FLOAT":
            val = state.getRandomly().getDouble();
            constant = new OceanBaseDoubleConstant(val);
            return constant;
        case "DECIMAL":
            val = state.getRandomly().getDouble();
            return new OceanBaseDoubleConstant(val);
        default:
            throw new AssertionError();
        }
    }

    @Override
    public OceanBaseExpression negatePredicate(OceanBaseExpression predicate) {
        return new OceanBaseUnaryPrefixOperation(predicate, OceanBaseUnaryPrefixOperator.NOT);
    }

    @Override
    public OceanBaseExpression isNull(OceanBaseExpression expr) {
        return new OceanBaseUnaryPostfixOperation(expr, OceanBaseUnaryPostfixOperation.UnaryPostfixOperator.IS_NULL,
                false);
    }
}
