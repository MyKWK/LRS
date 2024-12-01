package sqlancer.tidb.oracle;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.mysql.LR.tester;
import sqlancer.tidb.TiDBErrors;
import sqlancer.tidb.TiDBExpressionGenerator;
import sqlancer.tidb.TiDBProvider.TiDBGlobalState;
import sqlancer.tidb.TiDBSchema.TiDBTables;
import sqlancer.tidb.ast.*;
import sqlancer.tidb.visitor.TiDBVisitor;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TiDBLRSOracle implements TestOracle<TiDBGlobalState> {
    private TiDBExpressionGenerator gen;
    private final TiDBGlobalState state;
    private TiDBSelect select;
    private final ExpectedErrors errors = new ExpectedErrors();

    public static void main(String[] args) {
        writeToFile("ceshi");
    }
    public TiDBLRSOracle(TiDBGlobalState globalState) {
        state = globalState;
        TiDBErrors.addExpressionErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        try {// Randomly generate a query
            TiDBTables tables = state.getSchema().getRandomTableNonEmptyTables();
            gen = new TiDBExpressionGenerator(state).setColumns(tables.getColumns());
            select = new TiDBSelect();

            List<TiDBExpression> fetchColumns = new ArrayList<>();
            fetchColumns.addAll(Randomly.nonEmptySubset(tables.getColumns()).stream().map(c -> new TiDBColumnReference(c))
                    .collect(Collectors.toList()));
            select.setFetchColumns(fetchColumns);

            List<TiDBExpression> tableList = tables.getTables().stream().map(t -> new TiDBTableReference(t))
                    .collect(Collectors.toList());
            List<TiDBExpression> joins = TiDBJoin.getJoins(tableList, state);
            select.setJoinList(joins);
            select.setFromList(tableList);
            if (Randomly.getBoolean()) {
                select.setWhereClause(gen.generateExpression());
            }
            if (Randomly.getBooleanWithRatherLowProbability()) {
                select.setOrderByClauses(gen.generateOrderBys());
            }
            if (Randomly.getBoolean()) {
                select.setLimitClause(gen.generateExpression());
            }
            if (Randomly.getBoolean()) {
                select.setOffsetClause(gen.generateExpression());
            }

            String originalQueryString = TiDBVisitor.asString(select)
                    .replaceAll("DISTINCTROW", "")
                    .replaceAll("STRAIGHT_JOIN", "JOIN")
                    .replaceAll("&", "AND")
                    .toLowerCase();
            System.out.println(originalQueryString);
//            List<String> originalResult = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);
            List<List<Object>> originalResultSet = ComparatorHelper.getResultSet(originalQueryString,errors,state);
            // 重写
            ArrayList<String> rewriteHintQueries = tester.rewrite(originalQueryString);
            for (String query : rewriteHintQueries) {
                List<List<Object>> rewriteResultSet = ComparatorHelper.getResultSet(query,errors,state);
                boolean flag = ComparatorHelper.compareResultSets(originalResultSet, rewriteResultSet);
                if (!flag) {
                    System.out.println("bug is found : ");
                    writeToFile("Original Query : " + originalQueryString);
                    writeToFile("Rewrite Query : " + query);
                    writeToFile("---------");
                }
            }
//            List<TiDBText> hintList = TiDBHintGenerator.generateAllHints(select, tables.getTables());
//            for (TiDBText hint : hintList) {
//                select.setHint(hint);
//                String queryString = TiDBVisitor.asString(select);
//                List<String> result = ComparatorHelper.getResultSetFirstColumnAsString(queryString, errors, state);
//                ComparatorHelper.assumeResultSetsAreEqual(originalResult, result, originalQueryString, List.of(queryString),
//                        state);
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void writeToFile(String text) {
        // 定义文件名
        String fileName = "待处理"+"tidb_record.txt";

        // 使用 try-with-resources 语句，自动关闭 FileWriter
        try (FileWriter writer = new FileWriter(fileName, true)) {
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
