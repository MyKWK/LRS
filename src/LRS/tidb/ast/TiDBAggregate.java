package LRS.tidb.ast;

import java.util.List;

import LRS.Randomly;
import LRS.common.ast.FunctionNode;
import LRS.tidb.ast.TiDBAggregate.TiDBAggregateFunction;

public class TiDBAggregate extends FunctionNode<TiDBAggregateFunction, TiDBExpression> implements TiDBExpression {

    public enum TiDBAggregateFunction {
        AVG(1), BIT_AND(1), BIT_OR(1), COUNT(1), SUM(1), MIN(1), MAX(1);

        private int nrArgs;

        TiDBAggregateFunction(int nrArgs) {
            this.nrArgs = nrArgs;
        }

        public static TiDBAggregateFunction getRandom() {
            return Randomly.fromOptions(values());
        }

        public int getNrArgs() {
            return nrArgs;
        }

    }

    public TiDBAggregate(List<TiDBExpression> args, TiDBAggregateFunction func) {
        super(func, args);
    }

}
