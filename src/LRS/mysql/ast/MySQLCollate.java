package LRS.mysql.ast;

import LRS.common.ast.UnaryNode;

public class MySQLCollate extends UnaryNode<MySQLExpression> implements MySQLExpression {

    private final String collate;

    public MySQLCollate(MySQLExpression expr, String text) {
        super(expr);
        this.collate = text;
    }

    @Override
    public String getOperatorRepresentation() {
        return String.format("COLLATE '%s'", collate);
    }

    @Override
    public OperatorKind getOperatorKind() {
        return OperatorKind.POSTFIX;
    }

}
