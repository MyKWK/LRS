package LRS.tidb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import LRS.IgnoreMeException;
import LRS.Randomly;
import LRS.common.gen.UntypedExpressionGenerator;
import LRS.tidb.TiDBProvider.TiDBGlobalState;
import LRS.tidb.TiDBSchema.TiDBColumn;
import LRS.tidb.TiDBSchema.TiDBDataType;
import LRS.tidb.ast.TiDBAggregate;
import LRS.tidb.ast.TiDBAggregate.TiDBAggregateFunction;
import LRS.tidb.ast.TiDBBinaryBitOperation;
import LRS.tidb.ast.TiDBBinaryBitOperation.TiDBBinaryBitOperator;
import LRS.tidb.ast.TiDBBinaryComparisonOperation;
import LRS.tidb.ast.TiDBBinaryComparisonOperation.TiDBComparisonOperator;
import LRS.tidb.ast.TiDBBinaryLogicalOperation;
import LRS.tidb.ast.TiDBBinaryLogicalOperation.TiDBBinaryLogicalOperator;
import LRS.tidb.ast.TiDBCase;
import LRS.tidb.ast.TiDBCastOperation;
import LRS.tidb.ast.TiDBColumnReference;
import LRS.tidb.ast.TiDBConstant;
import LRS.tidb.ast.TiDBExpression;
import LRS.tidb.ast.TiDBFunctionCall;
import LRS.tidb.ast.TiDBFunctionCall.TiDBFunction;
import LRS.tidb.ast.TiDBOrderingTerm;
import LRS.tidb.ast.TiDBRegexOperation;
import LRS.tidb.ast.TiDBRegexOperation.TiDBRegexOperator;
import LRS.tidb.ast.TiDBUnaryPostfixOperation;
import LRS.tidb.ast.TiDBUnaryPostfixOperation.TiDBUnaryPostfixOperator;
import LRS.tidb.ast.TiDBUnaryPrefixOperation;
import LRS.tidb.ast.TiDBUnaryPrefixOperation.TiDBUnaryPrefixOperator;

public class TiDBExpressionGenerator extends UntypedExpressionGenerator<TiDBExpression, TiDBColumn> {

    private final TiDBGlobalState globalState;

    public TiDBExpressionGenerator(TiDBGlobalState globalState) {
        this.globalState = globalState;
    }

    private enum Gen {
        UNARY_PREFIX, //
        UNARY_POSTFIX, //
        CONSTANT, //
        COLUMN, //
        COMPARISON, REGEX, FUNCTION, BINARY_LOGICAL, BINARY_BIT, CAST, DEFAULT, CASE
        // BINARY_ARITHMETIC
    }

    @Override
    protected TiDBExpression generateExpression(int depth) {
        if (depth >= globalState.getOptions().getMaxExpressionDepth() || Randomly.getBoolean()) {
            return generateLeafNode();
        }
        if (allowAggregates && Randomly.getBoolean()) {
            allowAggregates = false;
            TiDBAggregateFunction func = TiDBAggregateFunction.getRandom();
            List<TiDBExpression> args = generateExpressions(func.getNrArgs());
            return new TiDBAggregate(args, func);
        }
        switch (Randomly.fromOptions(Gen.values())) {
        case DEFAULT:
            if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                throw new IgnoreMeException();
            }
            TiDBColumn column = Randomly.fromList(columns);
            if (column.hasDefault()) {
                return new TiDBFunctionCall(TiDBFunction.DEFAULT, Arrays.asList(new TiDBColumnReference(column)));
            }
            throw new IgnoreMeException();
        case UNARY_POSTFIX:
            return new TiDBUnaryPostfixOperation(generateExpression(depth + 1), TiDBUnaryPostfixOperator.getRandom());
        case UNARY_PREFIX:
            TiDBUnaryPrefixOperator rand = TiDBUnaryPrefixOperator.getRandom();
            return new TiDBUnaryPrefixOperation(generateExpression(depth + 1), rand);
        case COLUMN:
            return generateColumn();
        case CONSTANT:
            return generateConstant();
        case COMPARISON:
            return new TiDBBinaryComparisonOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    TiDBComparisonOperator.getRandom());
        case REGEX:
            return new TiDBRegexOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    TiDBRegexOperator.getRandom());
        case FUNCTION:
            TiDBFunction func = TiDBFunction.getRandom();
            return new TiDBFunctionCall(func, generateExpressions(func.getNrArgs(), depth));
        case BINARY_BIT:
            return new TiDBBinaryBitOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    TiDBBinaryBitOperator.getRandom());
        case BINARY_LOGICAL:
            return new TiDBBinaryLogicalOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    TiDBBinaryLogicalOperator.getRandom());
        case CAST:
            return new TiDBCastOperation(generateExpression(depth + 1), Randomly.fromOptions("BINARY", // https://github.com/tidb-challenge-program/bug-hunting-issue/issues/52
                    "CHAR", "DATE", "DATETIME", "TIME", // https://github.com/tidb-challenge-program/bug-hunting-issue/issues/13
                    "DECIMAL", "SIGNED", "UNSIGNED" /* https://github.com/pingcap/tidb/issues/16028 */));
        case CASE:
            int nr = Randomly.fromOptions(1, 2);
            return new TiDBCase(generateExpression(depth + 1), generateExpressions(nr, depth + 1),
                    generateExpressions(nr, depth + 1), generateExpression(depth + 1));
        default:
            throw new AssertionError();
        }
    }

    @Override
    protected TiDBExpression generateColumn() {
        TiDBColumn column = Randomly.fromList(columns);
        return new TiDBColumnReference(column);
    }

    @Override
    public TiDBExpression generateConstant() {
        TiDBDataType type = TiDBDataType.getRandom();
        if (Randomly.getBooleanWithRatherLowProbability()) {
            return TiDBConstant.createNullConstant();
        }
        switch (type) {
        case INT:
            return TiDBConstant.createIntConstant(globalState.getRandomly().getInteger());
        case BLOB:
        case TEXT:
            return TiDBConstant.createStringConstant(globalState.getRandomly().getString());
        case BOOL:
            return TiDBConstant.createBooleanConstant(Randomly.getBoolean());
        case FLOATING:
            return TiDBConstant.createFloatConstant(globalState.getRandomly().getDouble());
        case CHAR:
            return TiDBConstant.createStringConstant(globalState.getRandomly().getChar());
        case DECIMAL:
        case NUMERIC:
            return TiDBConstant.createIntConstant(globalState.getRandomly().getInteger());
        default:
            throw new AssertionError();
        }
    }

    @Override
    public List<TiDBExpression> generateOrderBys() {
        List<TiDBExpression> expressions = super.generateOrderBys();
        List<TiDBExpression> newExpressions = new ArrayList<>();
        for (TiDBExpression expr : expressions) {
            TiDBExpression newExpr = expr;
            if (Randomly.getBoolean()) {
                newExpr = new TiDBOrderingTerm(expr, Randomly.getBoolean());
            }
            newExpressions.add(newExpr);
        }
        return newExpressions;
    }

    @Override
    public TiDBExpression negatePredicate(TiDBExpression predicate) {
        return new TiDBUnaryPrefixOperation(predicate, TiDBUnaryPrefixOperator.NOT);
    }

    @Override
    public TiDBExpression isNull(TiDBExpression expr) {
        return new TiDBUnaryPostfixOperation(expr, TiDBUnaryPostfixOperator.IS_NULL);
    }

    public TiDBExpression generateConstant(TiDBDataType type) {
        if (Randomly.getBooleanWithRatherLowProbability()) {
            return TiDBConstant.createNullConstant();
        }
        switch (type) {
        case INT:
            return TiDBConstant.createIntConstant(globalState.getRandomly().getInteger());
        case BLOB:
        case TEXT:
            return TiDBConstant.createStringConstant(globalState.getRandomly().getString());
        case BOOL:
            return TiDBConstant.createBooleanConstant(Randomly.getBoolean());
        case FLOATING:
            return TiDBConstant.createFloatConstant(globalState.getRandomly().getDouble());
        case CHAR:
            return TiDBConstant.createStringConstant(globalState.getRandomly().getChar());
        case DECIMAL:
        case NUMERIC:
            return TiDBConstant.createIntConstant(globalState.getRandomly().getInteger());
        default:
            throw new AssertionError();
        }
    }

}
