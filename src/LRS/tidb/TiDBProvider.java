package LRS.tidb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import com.google.auto.service.AutoService;

import LRS.AbstractAction;
import LRS.DatabaseProvider;
import LRS.IgnoreMeException;
import LRS.MainOptions;
import LRS.Randomly;
import LRS.SQLConnection;
import LRS.SQLGlobalState;
import LRS.SQLProviderAdapter;
import LRS.StatementExecutor;
import LRS.common.query.ExpectedErrors;
import LRS.common.query.SQLQueryAdapter;
import LRS.common.query.SQLQueryProvider;
import LRS.common.query.SQLancerResultSet;
import LRS.tidb.TiDBOptions.TiDBOracleFactory;
import LRS.tidb.TiDBProvider.TiDBGlobalState;
import LRS.tidb.TiDBSchema.TiDBTable;
import LRS.tidb.gen.TiDBAlterTableGenerator;
import LRS.tidb.gen.TiDBAnalyzeTableGenerator;
import LRS.tidb.gen.TiDBDeleteGenerator;
import LRS.tidb.gen.TiDBDropTableGenerator;
import LRS.tidb.gen.TiDBDropViewGenerator;
import LRS.tidb.gen.TiDBIndexGenerator;
import LRS.tidb.gen.TiDBInsertGenerator;
import LRS.tidb.gen.TiDBSetGenerator;
import LRS.tidb.gen.TiDBTableGenerator;
import LRS.tidb.gen.TiDBUpdateGenerator;
import LRS.tidb.gen.TiDBViewGenerator;

@AutoService(DatabaseProvider.class)
public class TiDBProvider extends SQLProviderAdapter<TiDBGlobalState, TiDBOptions> {

    public TiDBProvider() {
        super(TiDBGlobalState.class, TiDBOptions.class);
    }

    public enum Action implements AbstractAction<TiDBGlobalState> {
        CREATE_TABLE(TiDBTableGenerator::createRandomTableStatement), // 0
        CREATE_INDEX(TiDBIndexGenerator::getQuery), // 1
        VIEW_GENERATOR(TiDBViewGenerator::getQuery), // 2
        INSERT(TiDBInsertGenerator::getQuery), // 3
        ALTER_TABLE(TiDBAlterTableGenerator::getQuery), // 4
        TRUNCATE((g) -> new SQLQueryAdapter("TRUNCATE " + g.getSchema().getRandomTable(t -> !t.isView()).getName())), // 5
        UPDATE(TiDBUpdateGenerator::getQuery), // 6
        DELETE(TiDBDeleteGenerator::getQuery), // 7
        SET(TiDBSetGenerator::getQuery), // 8
        ADMIN_CHECKSUM_TABLE(
                (g) -> new SQLQueryAdapter("ADMIN CHECKSUM TABLE " + g.getSchema().getRandomTable().getName())), // 9
        ANALYZE_TABLE(TiDBAnalyzeTableGenerator::getQuery), // 10
        DROP_TABLE(TiDBDropTableGenerator::dropTable), // 11
        DROP_VIEW(TiDBDropViewGenerator::dropView); // 12

        private final SQLQueryProvider<TiDBGlobalState> sqlQueryProvider;

        Action(SQLQueryProvider<TiDBGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        @Override
        public SQLQueryAdapter getQuery(TiDBGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    public static class TiDBGlobalState extends SQLGlobalState<TiDBOptions, TiDBSchema> {

        @Override
        protected TiDBSchema readSchema() throws SQLException {
            return TiDBSchema.fromConnection(getConnection(), getDatabaseName());
        }

    }

    private static int mapActions(TiDBGlobalState globalState, Action a) {
        Randomly r = globalState.getRandomly();
        switch (a) {
        case ANALYZE_TABLE:
        case CREATE_INDEX:
            return r.getInteger(0, 2);
        case INSERT:
            return r.getInteger(0, globalState.getOptions().getMaxNumberInserts());
        case TRUNCATE:
        case DELETE:
        case ADMIN_CHECKSUM_TABLE:
            return r.getInteger(0, 2);
        case SET:
        case UPDATE:
            return r.getInteger(0, 5);
        case VIEW_GENERATOR:
            // https://github.com/tidb-challenge-program/bug-hunting-issue/issues/8
            return r.getInteger(0, 2);
        case ALTER_TABLE:
            return r.getInteger(0, 10); // https://github.com/tidb-challenge-program/bug-hunting-issue/issues/10
        case CREATE_TABLE:
        case DROP_TABLE:
        case DROP_VIEW:
            return 0;
        default:
            throw new AssertionError(a);
        }

    }

    @Override
    public void generateDatabase(TiDBGlobalState globalState) throws Exception {
        for (int i = 0; i < Randomly.fromOptions(1, 2); i++) {
            boolean success;
            do {
                SQLQueryAdapter qt = new TiDBTableGenerator().getQuery(globalState);
                success = globalState.executeStatement(qt);
            } while (!success);
        }

        StatementExecutor<TiDBGlobalState, Action> se = new StatementExecutor<>(globalState, Action.values(),
                TiDBProvider::mapActions, (q) -> {
                    if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                        throw new IgnoreMeException();
                    }
                });
        try {
            se.executeStatements();
        } catch (SQLException e) {
            if (e.getMessage().contains(
                    "references invalid table(s) or column(s) or function(s) or definer/invoker of view lack rights to use them")) {
                throw new IgnoreMeException(); // TODO: drop view instead
            } else {
                throw new AssertionError(e);
            }
        }

        if (globalState.getDbmsSpecificOptions().getTestOracleFactory().stream()
                .anyMatch((o) -> o == TiDBOracleFactory.LRS)) {
            // Disable strict Group By constraints for ROW oracle
            globalState.executeStatement(new SQLQueryAdapter(
                    "SET @@sql_mode='STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION';"));

            // Enfore statistic collected for all tables
            ExpectedErrors errors = new ExpectedErrors();
            TiDBErrors.addExpressionErrors(errors);
            for (TiDBTable table : globalState.getSchema().getDatabaseTables()) {
                if (!table.isView()) {
                    globalState.executeStatement(new SQLQueryAdapter("ANALYZE TABLE " + table.getName() + ";", errors));
                }
            }
        }

        // TiFlash replication settings
        if (globalState.getDbmsSpecificOptions().tiflash) {
            ExpectedErrors errors = new ExpectedErrors();
            TiDBErrors.addExpressionErrors(errors);
            for (TiDBTable table : globalState.getSchema().getDatabaseTables()) {
                if (!table.isView()) {
                    globalState.executeStatement(
                            new SQLQueryAdapter("ALTER TABLE " + table.getName() + " SET TIFLASH REPLICA 1;", errors));
                }
            }
            if (Randomly.getBoolean()) {
                globalState.executeStatement(new SQLQueryAdapter("set @@tidb_enforce_mpp=1;"));
            }
        }
    }

    @Override
    public SQLConnection createDatabase(TiDBGlobalState globalState) throws SQLException {
        String username = globalState.getOptions().getUserName();
        String password = globalState.getOptions().getPassword();
        String host = globalState.getOptions().getHost();
        int port = globalState.getOptions().getPort();
        if (host == null) {
            host = TiDBOptions.DEFAULT_HOST;
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = TiDBOptions.DEFAULT_PORT;
        }
        String databaseName = "tpcd";
        globalState.getState().logStatement("USE " + databaseName);
        String url = String.format("jdbc:mysql://%s:%d?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
                host, port);
        Connection con = DriverManager.getConnection(url, username, password);
        try (Statement s = con.createStatement()) {
            s.execute("USE " + databaseName);
        }

        return new SQLConnection(con);

//        String host = globalState.getOptions().getHost();
//        int port = globalState.getOptions().getPort();
//        if (host == null) {
//            host = TiDBOptions.DEFAULT_HOST;
//        }
//        if (port == MainOptions.NO_SET_PORT) {
//            port = TiDBOptions.DEFAULT_PORT;
//        }
//
//        String databaseName = globalState.getDatabaseName();
//        String url = String.format("jdbc:mysql://%s:%d/", host, port);
//        Connection con = DriverManager.getConnection(url, globalState.getOptions().getUserName(),
//                globalState.getOptions().getPassword());
//        globalState.getState().logStatement("USE test");
//        globalState.getState().logStatement("DROP DATABASE IF EXISTS " + databaseName);
//        String createDatabaseCommand = "CREATE DATABASE " + databaseName;
//        globalState.getState().logStatement(createDatabaseCommand);
//        globalState.getState().logStatement("USE " + databaseName);
//        try (Statement s = con.createStatement()) {
//            s.execute("DROP DATABASE IF EXISTS " + databaseName);
//            if (globalState.getDbmsSpecificOptions().nonPreparePlanCache) {
//                s.execute("set global tidb_enable_non_prepared_plan_cache=ON;");
//            }
//        }
//        try (Statement s = con.createStatement()) {
//            s.execute(createDatabaseCommand);
//        }
//        con.close();
//        con = DriverManager.getConnection(url + databaseName, globalState.getOptions().getUserName(),
//                globalState.getOptions().getPassword());
//        return new SQLConnection(con);
    }

    @Override
    public String getDBMSName() {
        return "tidb";
    }

    @Override
    public String getQueryPlan(String selectStr, TiDBGlobalState globalState) throws Exception {
        String queryPlan = "";
        if (globalState.getOptions().logEachSelect()) {
            globalState.getLogger().writeCurrent(selectStr);
            try {
                globalState.getLogger().getCurrentFileWriter().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        SQLQueryAdapter q = new SQLQueryAdapter("EXPLAIN FORMAT=brief " + selectStr);
        try (SQLancerResultSet rs = q.executeAndGet(globalState)) {
            if (rs != null) {
                while (rs.next()) {
                    String targetQueryPlan = rs.getString(1).replace("├─", "").replace("└─", "").replace("│", "").trim()
                            + ";"; // Unify format
                    queryPlan += targetQueryPlan;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return queryPlan;
    }

    @Override
    protected double[] initializeWeightedAverageReward() {
        return new double[Action.values().length];
    }

    @Override
    protected void executeMutator(int index, TiDBGlobalState globalState) throws Exception {
        SQLQueryAdapter queryMutateTable = Action.values()[index].getQuery(globalState);
        globalState.executeStatement(queryMutateTable);
    }

    @Override
    public boolean addRowsToAllTables(TiDBGlobalState globalState) throws Exception {
        List<TiDBTable> tablesNoRow = globalState.getSchema().getDatabaseTables().stream()
                .filter(t -> t.getNrRows(globalState) == 0).collect(Collectors.toList());
        for (TiDBTable table : tablesNoRow) {
            SQLQueryAdapter queryAddRows = TiDBInsertGenerator.getQuery(globalState, table);
            globalState.executeStatement(queryAddRows);
        }
        return true;
    }

}
