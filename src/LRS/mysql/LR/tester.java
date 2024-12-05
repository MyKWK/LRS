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

    public static void main(String[] args) throws IOException {
        try {
            String sqlPath = "src/sqlancer/mysql/LR/tpch10000.txt";
            ArrayList<String> queries = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(sqlPath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(";");
                    for (String part : parts) {
                        queries.add(part.trim());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            int k = 0;
            for (String query : queries) {
                System.out.println(k++);
                // 重写
                ArrayList<String> rewrite_queries = tester.rewrite(query);
                // 重写出一堆重重写查询
                System.out.println("重写了" + rewrite_queries.size() + "个结果：");
                for (String rewrite_query : rewrite_queries) {
                    if (!compareQueries(query, rewrite_query)) {
                        System.out.println("\033[0;36m" + "发现错误！！！" + "\033[0;36m");
                        String record = query + "@" + rewrite_query + "@@";
                        writeStringToFile(record);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("嘻嘻,bug 了");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/tpcd", "root", "1368");
                Statement stmt1 = connection.createStatement(); Statement stmt2 = connection.createStatement()) {
            ResultSet rs1 = null;
            ResultSet rs2 = null;
            Set<List<Object>> resultSet1 = new HashSet<>();
            Set<List<Object>> resultSet2 = new HashSet<>();
            // 执行第一个查询
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
            writer.newLine(); // 写入新行
            System.out.println("Content written to file successfully.");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

}