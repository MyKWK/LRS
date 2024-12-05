package LRS.oceanbase;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import LRS.DBMSSpecificOptions;
import LRS.OracleFactory;
import LRS.common.oracle.TestOracle;
import LRS.oceanbase.OceanBaseOptions.OceanBaseOracleFactory;
import LRS.oceanbase.oracle.OceanBaseLRSOracle;

@Parameters(separators = "=", commandDescription = "OceanBase (default port: " + OceanBaseOptions.DEFAULT_PORT
        + ", default host: " + OceanBaseOptions.DEFAULT_HOST + ")")
public class OceanBaseOptions implements DBMSSpecificOptions<OceanBaseOracleFactory> {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 2881;

    @Parameter(names = "--oracle")
    public List<OceanBaseOracleFactory> oracles = Arrays.asList(OceanBaseOracleFactory.LRS);

    public enum OceanBaseOracleFactory implements OracleFactory<OceanBaseGlobalState> {

        LRS {
            @Override
            public TestOracle<OceanBaseGlobalState> create(OceanBaseGlobalState globalState) throws SQLException {
                return new OceanBaseLRSOracle(globalState);
            }
        }
    }

    @Parameter(names = { "--query-timeout" }, description = "Query timeout")
    public int queryTimeout = 1000000000;
    @Parameter(names = { "--transaction-timeout" }, description = "Transaction timeout")
    public int trxTimeout = 1000000000;

    @Override
    public List<OceanBaseOracleFactory> getTestOracleFactory() {
        return oracles;
    }

}
