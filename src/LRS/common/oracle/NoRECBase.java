package LRS.common.oracle;

import LRS.Main.StateLogger;
import LRS.MainOptions;
import LRS.SQLConnection;
import LRS.SQLGlobalState;
import LRS.common.query.ExpectedErrors;

public abstract class NoRECBase<S extends SQLGlobalState<?, ?>> implements TestOracle<S> {

    protected final S state;
    protected final ExpectedErrors errors = new ExpectedErrors();
    protected final StateLogger logger;
    protected final MainOptions options;
    protected final SQLConnection con;
    protected String optimizedQueryString;
    protected String unoptimizedQueryString;

    protected NoRECBase(S state) {
        this.state = state;
        this.con = state.getConnection();
        this.logger = state.getLogger();
        this.options = state.getOptions();
    }

}
