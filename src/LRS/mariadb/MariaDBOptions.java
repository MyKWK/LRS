package LRS.mariadb;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import LRS.DBMSSpecificOptions;
import LRS.OracleFactory;
import LRS.common.oracle.TestOracle;
import LRS.mariadb.MariaDBOptions.MariaDBOracleFactory;
import LRS.mariadb.MariaDBProvider.MariaDBGlobalState;
import LRS.mariadb.oracle.MariaDBLRSOracle;

@Parameters(separators = "=", commandDescription = "MariaDB (default port: " + MariaDBOptions.DEFAULT_PORT
        + ", default host: " + MariaDBOptions.DEFAULT_HOST + ")")
public class MariaDBOptions implements DBMSSpecificOptions<MariaDBOracleFactory> {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 3306;

    @Parameter(names = "--oracle")
    public List<MariaDBOracleFactory> oracles = Arrays.asList(MariaDBOracleFactory.LRS);

    public enum MariaDBOracleFactory implements OracleFactory<MariaDBGlobalState> {
        LRS {
            @Override
            public TestOracle<MariaDBGlobalState> create(MariaDBGlobalState globalState) throws SQLException {
                return new MariaDBLRSOracle(globalState);
            }
        }
    }

    @Override
    public List<MariaDBOracleFactory> getTestOracleFactory() {
        return oracles;
    }

}
