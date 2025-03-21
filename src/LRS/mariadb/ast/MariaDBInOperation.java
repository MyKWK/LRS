package LRS.mariadb.ast;

import java.util.List;

public class MariaDBInOperation implements MariaDBExpression {

    private final MariaDBExpression expr;
    private final List<MariaDBExpression> list;
    private final boolean negated;

    public MariaDBInOperation(MariaDBExpression expr, List<MariaDBExpression> list, boolean negated) {
        this.expr = expr;
        this.list = list;
        this.negated = negated;
    }

    public MariaDBExpression getExpr() {
        return expr;
    }

    public List<MariaDBExpression> getList() {
        return list;
    }

    public boolean isNegated() {
        return negated;
    }

}
