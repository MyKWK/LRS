package LRS.common.query;

import java.sql.*;

import LRS.GlobalState;
import LRS.Main;
import LRS.SQLConnection;

public class SQLQueryAdapter extends Query<SQLConnection> {

    private final String query;
    private final ExpectedErrors expectedErrors;
    private final boolean couldAffectSchema;

    public SQLQueryAdapter(String query) {
        this(query, new ExpectedErrors());
    }

    public SQLQueryAdapter(String query, boolean couldAffectSchema) {
        this(query, new ExpectedErrors(), couldAffectSchema);
    }

    public SQLQueryAdapter(String query, ExpectedErrors expectedErrors) {
        this(query, expectedErrors, guessAffectSchemaFromQuery(query));
    }

    private static boolean guessAffectSchemaFromQuery(String query) {
        return query.contains("CREATE TABLE") && !query.startsWith("EXPLAIN");
    }

    public SQLQueryAdapter(String query, ExpectedErrors expectedErrors, boolean couldAffectSchema) {
        this(query, expectedErrors, couldAffectSchema, true);
    }

    public SQLQueryAdapter(String query, ExpectedErrors expectedErrors, boolean couldAffectSchema,
            boolean canonicalizeString)
    {
        if (canonicalizeString) {
            this.query = canonicalizeString(query);
        } else {
            this.query = query;
        }
        this.expectedErrors = expectedErrors;
        this.couldAffectSchema = couldAffectSchema;
        checkQueryString();
    }

    // 规范 sql
    private String canonicalizeString(String s) {
        if (s.endsWith(";")) {
            return s;
        } else if (!s.contains("--")) {
            return s + ";";
        } else {
            // query contains a comment
            return s;
        }
    }

    // 不用管
    private void checkQueryString() {
        if (!couldAffectSchema && guessAffectSchemaFromQuery(query)) {
            throw new AssertionError("CREATE TABLE statements should set couldAffectSchema to true");
        }
    }

    @Override
    public String getQueryString() {
        return query;
    }

    @Override
    public String getUnterminatedQueryString() {
        String result;
        if (query.endsWith(";")) {
            result = query.substring(0, query.length() - 1);
        } else {
            result = query;
        }
        assert !result.endsWith(";");
        return result;
    }

    @Override
    public <G extends GlobalState<?, ?, SQLConnection>> boolean execute(G globalState, String... fills)
            throws SQLException {
        Statement s;
        if (fills.length > 0) {
            s = globalState.getConnection().prepareStatement(fills[0]);
            for (int i = 1; i < fills.length; i++) {
                ((PreparedStatement) s).setString(i, fills[i]);
            }
        } else {
            s = globalState.getConnection().createStatement();
        }
        try {
            if (fills.length > 0) {
                ((PreparedStatement) s).execute();
            } else {
                s.execute(query);
            }
            Main.nrSuccessfulActions.addAndGet(1);
            return true;
        } catch (Exception e) {
            Main.nrUnsuccessfulActions.addAndGet(1);
            checkException(e);
            return false;
        } finally {
            s.close();
        }
    }

    public void checkException(Exception e) throws AssertionError {
        Throwable ex = e;

        while (ex != null) {
            if (expectedErrors.errorIsExpected(ex.getMessage())) {
                return;
            } else {
                ex = ex.getCause();
            }
        }

        throw new AssertionError(query, e);
    }

    @Override
    public <G extends GlobalState<?, ?, SQLConnection>> SQLancerResultSet executeAndGet
            (G globalState, String... fills) throws SQLException
    {
        Statement s; //用来存储原始state
        if (fills.length > 0) {
            s = globalState.getConnection().prepareStatement(fills[0]);
            for (int i = 1; i < fills.length; i++) { // 没用，只有一个 query
                ((PreparedStatement) s).setString(i, fills[i]);
            }
        } else {
            s = globalState.getConnection().createStatement();
        }
        ResultSet result = null;
        try {
            if (s instanceof PreparedStatement) {
                PreparedStatement ps = (PreparedStatement) s;
                ps.setQueryTimeout(15); // 设置查询超时为15秒
                result = ps.executeQuery();
            } else {
                s.setQueryTimeout(15); // 对 Statement 设置超时（不适用于所有数据库）
                result = s.executeQuery(query); // 只执行这里
            }
            Main.nrSuccessfulActions.addAndGet(1);
            if (result == null) {
                return null;
            }
            return new SQLancerResultSet(result);
        } catch (SQLTimeoutException e) {
            // 捕捉查询超时异常
            throw new RuntimeException("Query timed out after 15 seconds", e);
        }catch (Exception e) {
            s.close();
            Main.nrUnsuccessfulActions.addAndGet(1);
            checkException(e);
        }
        return null;
    }

    @Override
    public boolean couldAffectSchema() {
        return couldAffectSchema;
    }

    @Override
    public ExpectedErrors getExpectedErrors() {
        return expectedErrors;
    }

    @Override
    public String getLogString() {
        return getQueryString();
    }
}
