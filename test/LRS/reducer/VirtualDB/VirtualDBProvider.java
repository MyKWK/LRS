package LRS.reducer.VirtualDB;

import com.google.auto.service.AutoService;
import LRS.DatabaseProvider;
import LRS.Reproducer;
import LRS.SQLConnection;
import LRS.SQLProviderAdapter;

@AutoService(DatabaseProvider.class)
public class VirtualDBProvider extends SQLProviderAdapter<VirtualDBGlobalState, VirtualDBOptions> {

    private Reproducer<VirtualDBGlobalState> reproducerForTesting;

    public VirtualDBProvider() {
        super(VirtualDBGlobalState.class, VirtualDBOptions.class);
    }

    @Override
    public SQLConnection createDatabase(VirtualDBGlobalState globalState) throws Exception {
        return new VirtualDBConnection(null);
    }

    @Override
    public String getDBMSName() {
        return "virtual_db";
    }

    @Override
    public void generateDatabase(VirtualDBGlobalState globalState) throws Exception {

    }

    @Override
    public Reproducer<VirtualDBGlobalState> generateAndTestDatabase(VirtualDBGlobalState globalState) throws Exception {
        return state -> {
            if (globalState.getBugInducingCondition() == null)
                return false;
            return globalState.getBugInducingCondition().apply(globalState.getState().getStatements());
        };
    }

    @Override
    public Class<VirtualDBGlobalState> getGlobalStateClass() {
        return super.getGlobalStateClass();
    }

    public Reproducer<VirtualDBGlobalState> getReproducerForTesting() {
        return reproducerForTesting;
    }

    public void setReproducerForTesting(Reproducer<VirtualDBGlobalState> reproducer) {
        this.reproducerForTesting = reproducer;
    }
}
