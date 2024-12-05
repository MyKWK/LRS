package LRS.tidb.oracle;

import LRS.ComparatorHelper;
import LRS.Randomly;
import LRS.common.oracle.TestOracle;
import LRS.common.query.ExpectedErrors;
import LRS.mysql.LR.tester;
import LRS.tidb.TiDBErrors;
import LRS.tidb.TiDBExpressionGenerator;
import LRS.tidb.TiDBProvider.TiDBGlobalState;
import LRS.tidb.TiDBSchema.TiDBTables;
import LRS.tidb.ast.*;
import LRS.tidb.visitor.TiDBVisitor;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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

            List<List<Object>> originalResultSet = ComparatorHelper.getResultSet(originalQueryString,errors,state);
            ArrayList<String> rewrittenQueries = tester.rewrite(originalQueryString);
            int count_invalid = 0;
            for (String query : rewrittenQueries) {
                if(isvalid(rewrittenQueries,query)){
                    List<List<Object>> rewriteResultSet = ComparatorHelper.getResultSet(query,errors,state);
                    boolean flag = ComparatorHelper.compareResultSets(originalResultSet, rewriteResultSet);
                    if (!flag) {
                        System.out.println("bug is found : ");
                        writeToFile("Original Query : " + originalQueryString);
                        writeToFile("Rewrite Query : " + query);
                        writeToFile("---------");
                    }
                }
                else{
                    count_invalid = count_invalid + 1;
                }
            }
            rewrittenQueries = new ArrayList<>(Arrays.asList(executeLLMGR(originalQueryString, count_invalid).split(";")));
            for (String query : rewrittenQueries) {
                List<List<Object>> rewriteResultSet = ComparatorHelper.getResultSet(query,errors,state);
                boolean flag = ComparatorHelper.compareResultSets(originalResultSet, rewriteResultSet);
                if (!flag) {
                    System.out.println("bug is found : ");
                    writeToFile("Original Query : " + originalQueryString);
                    writeToFile("Rewrite Query : " + query.split("-")[0]);
                    writeToFile("Rule : " + query.split("-")[1]);
                    writeToFile("---------");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public boolean isvalid(ArrayList<String> queries,String query){
        int count = 0;
        for (String q : queries) {
            if (q.equals(query)) {
                count++;
            }
        }
        return count == 1;
    }
    public static String executeLLMGR(String query,int count) {
        StringBuilder result = new StringBuilder();

        try {
            String pythonScriptPath = "src/LAQR/LLMGR.py";
            ProcessBuilder processBuilder = new ProcessBuilder("python", pythonScriptPath, query, String.valueOf(count));
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return result.toString().trim();
    }
    public static void writeToFile(String text) {
        String fileName = "tidb_record.txt";

        try (FileWriter writer = new FileWriter(fileName, true)) {
            writer.write(text);
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
