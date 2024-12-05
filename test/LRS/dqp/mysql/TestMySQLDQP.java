package LRS.dqp.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import LRS.Main;
import LRS.dbms.TestConfig;

public class TestMySQLDQP {

    @Test
    public void testmysqlQPG() {
        String mysql = System.getenv("MYSQL_AVAILABLE");
        boolean mysqlIsAvailable = mysql != null && mysql.equalsIgnoreCase("true");
        assumeTrue(mysqlIsAvailable);
        assertEquals(0, Main.executeMain(new String[] { "--random-seed", "0", "--timeout-seconds", TestConfig.SECONDS,
                "--num-threads", "1", "--num-queries", TestConfig.NUM_QUERIES, "mysql", "--oracle", "LRS" }));
    }

}
