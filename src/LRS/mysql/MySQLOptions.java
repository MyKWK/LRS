package LRS.mysql;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import LRS.DBMSSpecificOptions;
import LRS.OracleFactory;
import LRS.common.oracle.TestOracle;
import LRS.mysql.MySQLOptions.MySQLOracleFactory;
import LRS.mysql.oracle.MySQLLRSOracle;
import LRS.mysql.oracle.MySQLFuzzer;

@Parameters(separators = "=", commandDescription = "MySQL (default port: " + MySQLOptions.DEFAULT_PORT
        + ", default host: " + MySQLOptions.DEFAULT_HOST + ")")
public class MySQLOptions implements DBMSSpecificOptions<MySQLOracleFactory> {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 3306;

    @Parameter(names = "--oracle")
    public List<MySQLOracleFactory> oracles = Arrays.asList(MySQLOracleFactory.LRS);

    public enum MySQLOracleFactory implements OracleFactory<MySQLGlobalState> {


        FUZZER {
            @Override
            public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws Exception {
                return new MySQLFuzzer(globalState);
            }

        },
        LRS {
            @Override
            public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
                return new MySQLLRSOracle(globalState);
            }
        };
    }

    @Override
    public List<MySQLOracleFactory> getTestOracleFactory() {
        return oracles;
    }

}
