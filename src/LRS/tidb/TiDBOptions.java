package LRS.tidb;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import LRS.DBMSSpecificOptions;
import LRS.OracleFactory;
import LRS.common.oracle.TestOracle;
import LRS.tidb.TiDBOptions.TiDBOracleFactory;
import LRS.tidb.TiDBProvider.TiDBGlobalState;
import LRS.tidb.oracle.TiDBLRSOracle;

@Parameters(separators = "=", commandDescription = "TiDB (default port: " + TiDBOptions.DEFAULT_PORT
        + ", default host: " + TiDBOptions.DEFAULT_HOST + ")")
public class TiDBOptions implements DBMSSpecificOptions<TiDBOracleFactory> {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 4000;

    @Parameter(names = { "--max-num-tables" }, description = "The maximum number of tables/views that can be created")
    public int maxNumTables = 10;

    @Parameter(names = { "--max-num-indexes" }, description = "The maximum number of indexes that can be created")
    public int maxNumIndexes = 20;

    @Parameter(names = "--oracle")
    public List<TiDBOracleFactory> oracle = Arrays.asList(TiDBOracleFactory.LRS);

    @Parameter(names = "--enable-non-prepared-plan-cache")
    public boolean nonPreparePlanCache;

    @Parameter(names = { "--tiflash" }, description = "Enable TiFlash")
    public boolean tiflash;

    public enum TiDBOracleFactory implements OracleFactory<TiDBGlobalState> {
        LRS {
            @Override
            public TestOracle<TiDBGlobalState> create(TiDBGlobalState globalState) throws SQLException {
                return new TiDBLRSOracle(globalState);
            }
        };

    }

    @Override
    public List<TiDBOracleFactory> getTestOracleFactory() {
        return oracle;
    }
}
