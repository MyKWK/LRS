package LRS.mysql.LR;
import org.apache.calcite.rel.RelNode;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class tester {
    static int k = 0;


    public static ArrayList<String> rewrite(String query) throws Exception {
        k++;
        Rewriter rewriter = new Rewriter(Utils.readJsonFile(System.getProperty("user.dir") + "src/LAQR/schema.json"));
        RelNode testRelNode = rewriter.SQL2RA(query);
        double origin_cost = rewriter.getCostRecordFromRelNode(testRelNode);
        Node resultNode = new Node(query, testRelNode, (float) origin_cost, rewriter, (float) 0.1, null,
                "original query");
        ArrayList<String> RewriteQuery = resultNode.UTCSEARCH(20, resultNode, 1);
        return RewriteQuery;
    }

    public static boolean compareQueries(String query1, String query2) {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/tpcd");
                Statement stmt1 = connection.createStatement(); Statement stmt2 = connection.createStatement()) {
            ResultSet rs1 = null;
            ResultSet rs2 = null;
            Set<List<Object>> resultSet1 = new HashSet<>();
            Set<List<Object>> resultSet2 = new HashSet<>();
            
            try {
                rs1 = stmt1.executeQuery(query1);
                resultSet1 = convertResultSetToSet(rs1);
            } catch (SQLException e) {
                if (e instanceof java.sql.SQLSyntaxErrorException) {
                    System.err.println("Original_bug: " + e.getMessage());
                    return true;
                } else {
                    throw e;
                }
            }
            try {
                rs2 = stmt2.executeQuery(query2);
                resultSet2 = convertResultSetToSet(rs2);
            } catch (SQLException e) {
                if (e instanceof java.sql.SQLSyntaxErrorException) {
                    System.err.println("Rewritten_bug: " + e.getMessage());
                    return true;
                } else {
                    throw e;
                }
            }
            return resultSet1.equals(resultSet2);

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Set<List<Object>> convertResultSetToSet(ResultSet rs) throws SQLException {
        Set<List<Object>> resultSet = new HashSet<>();
        int columnCount = rs.getMetaData().getColumnCount();

        while (rs.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getObject(i));
            }
            resultSet.add(row);
        }

        return resultSet;
    }

    public static void writeStringToFile(String content) {
        String filePath = "src/test_records.txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(content);
            writer.newLine(); 
            System.out.println("Content written to file successfully.");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

}