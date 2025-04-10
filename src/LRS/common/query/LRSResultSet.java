package LRS.common.query;

import java.io.Closeable;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LRSResultSet implements Closeable {

    public ResultSet rs;
    private Runnable runnableEpilogue;

    public LRSResultSet(ResultSet rs) {
        this.rs = rs;
    }

    @Override
    public void close() {
        try {
            if (runnableEpilogue != null) {
                runnableEpilogue.run();
            }
            rs.getStatement().close();
            rs.close();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    public boolean next() throws SQLException {
        return rs.next();
    }

    public int getInt(int i) throws SQLException {
        return rs.getInt(i);
    }

    public String getString(int i) throws SQLException {
        try {
            return rs.getString(i);
        } catch (NumberFormatException e) {
            throw new SQLException(e);
        }
    }

    public boolean isClosed() throws SQLException {
        return rs.isClosed();
    }

    public long getLong(int i) throws SQLException {
        return rs.getLong(i);
    }

    public String getType(int i) throws SQLException {
        return rs.getMetaData().getColumnTypeName(i);
    }

    public void registerEpilogue(Runnable runnableEpilogue) {
        this.runnableEpilogue = runnableEpilogue;
    }

}
