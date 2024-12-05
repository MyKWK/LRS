package LRS.tidb.ast;

import LRS.Randomly;
import LRS.common.ast.BinaryOperatorNode;
import LRS.tidb.ast.TiDBBinaryBitOperation.TiDBBinaryBitOperator;

public class TiDBBinaryBitOperation extends BinaryOperatorNode<TiDBExpression, TiDBBinaryBitOperator>
        implements TiDBExpression {

    public enum TiDBBinaryBitOperator implements Operator {
        AND("&"), //
        OR("|"), //
        XOR("^"), //
        LEFT_SHIFT("<<"), //
        RIGHT_SHIFT(">>");

        String textRepresentation;

        TiDBBinaryBitOperator(String textRepresentation) {
            this.textRepresentation = textRepresentation;
        }

        public static TiDBBinaryBitOperator getRandom() {
            return Randomly.fromOptions(values());
        }

        @Override
        public String getTextRepresentation() {
            return textRepresentation;
        }
    }

    public TiDBBinaryBitOperation(TiDBExpression left, TiDBExpression right, TiDBBinaryBitOperator op) {
        super(left, right, op);
    }

}
