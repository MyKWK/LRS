package LRS.common.ast;

import LRS.common.visitor.UnaryOperation;

public abstract class UnaryNode<T> implements UnaryOperation<T> {

    protected final T expr;

    protected UnaryNode(T expr) {
        this.expr = expr;
    }

    @Override
    public T getExpression() {
        return expr;
    }

}
