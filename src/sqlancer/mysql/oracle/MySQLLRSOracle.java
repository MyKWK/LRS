package sqlancer.mysql.oracle;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.mysql.LR.tester;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema.MySQLTables;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.*;
import sqlancer.mysql.gen.MySQLExpressionGenerator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
public class MySQLLRSOracle implements TestOracle<MySQLGlobalState> {
    private final MySQLGlobalState state;
    private MySQLExpressionGenerator gen;
    private MySQLSelect select;
    private final ExpectedErrors errors = new ExpectedErrors();

    public MySQLLRSOracle(MySQLGlobalState globalState) {
        state = globalState;
        MySQLErrors.addExpressionErrors(errors);
    }

    @Override
    public void check() throws Exception {
        // Randomly generate a query
        try {
            MySQLTables tables = state.getSchema().getRandomTableNonEmptyTables();
            gen = new MySQLExpressionGenerator(state).setColumns(tables.getColumns());
            List<MySQLExpression> fetchColumns = new ArrayList<>();
            fetchColumns.addAll(Randomly.nonEmptySubset(tables.getColumns()).stream()
                    .map(c -> new MySQLColumnReference(c, null)).collect(Collectors.toList()));
            select = new MySQLSelect();
            select.setFetchColumns(fetchColumns);
            select.setSelectType(Randomly.fromOptions(MySQLSelect.SelectType.values()));
            if (Randomly.getBoolean()) {
                select.setWhereClause(gen.generateExpression());
            }
            if (Randomly.getBoolean()) {
                select.setGroupByExpressions(fetchColumns);
                if (Randomly.getBoolean()) {
                    select.setHavingClause(gen.generateExpression());
                }
            }
            List<MySQLJoin> joinExpressions = MySQLJoin.getRandomJoinClauses(tables.getTables(), state);
            select.setJoinList(joinExpressions.stream().map(j -> (MySQLExpression) j).collect(Collectors.toList()));
            List<MySQLExpression> tableList = tables.getTables().stream().map(t -> new MySQLTableReference(t))
                    .collect(Collectors.toList());
            select.setFromList(tableList);
            String originalQueryString = MySQLVisitor.asString(select)
                    .replaceAll("DISTINCTROW", "")
                    .replaceAll("STRAIGHT_JOIN", "JOIN")
                    .replaceAll("&", "AND")
                    .toLowerCase();
            System.out.println(originalQueryString);
            writeToFile(originalQueryString+"\n");
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
        String fileName = "record.txt";
        try (FileWriter writer = new FileWriter(fileName,true)) {
            writer.write(text);
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}