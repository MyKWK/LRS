package sqlancer.sqlite3.oracle.tlp;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.mysql.LR.tester;
import sqlancer.sqlite3.SQLite3GlobalState;
import sqlancer.sqlite3.SQLite3Visitor;

public class SQLite3TLPWhereOracle extends SQLite3TLPBase {

    private String generatedQueryString;

    public SQLite3TLPWhereOracle(SQLite3GlobalState state) {
        super(state);
    }

    @Override
    public void check() throws Exception {
        super.check();
        // select 在这里生成完毕;
        String originalQueryString = SQLite3Visitor.asString(select)
                .replace("ISNULL","IS NULL");
        writeToFile(originalQueryString);
//        List<List<Object>> originalResultSet = ComparatorHelper.getResultSet(originalQueryString,errors,state);
//        ArrayList<String> rewriteHintQueries = tester.rewrite(originalQueryString);
//        for (String query : rewriteHintQueries) {
//            List<List<Object>> rewriteResultSet = ComparatorHelper.getResultSet(query,errors,state);
//            boolean flag = ComparatorHelper.compareResultSets(originalResultSet, rewriteResultSet);
//            if (!flag) {
//                System.out.println("出现错误");
//                writeToFile("Original Query : " + originalQueryString);
//                writeToFile("Rewrite Query : " + query);
//                writeToFile("---------");
//            }
//        }
    }


    @Override
    public String getLastQueryString() {
        return generatedQueryString;
    }
    public static void writeToFile(String text) {
        // 定义文件名
        String fileName = "sqlite_record.txt";

        // 使用 try-with-resources 语句，自动关闭 FileWriter
        try (FileWriter writer = new FileWriter(fileName,true)) {
            // 将文本写入文件
            writer.write(text);
            writer.write("\n");
            // 刷新 writer 确保所有数据被写入
            writer.flush();
        } catch (IOException e) {
            // 打印异常堆栈信息
            e.printStackTrace();
        }
    }
}
