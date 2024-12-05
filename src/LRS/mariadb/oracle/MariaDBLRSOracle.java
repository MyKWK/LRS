package LRS.mariadb.oracle;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import LRS.ComparatorHelper;
import LRS.Randomly;
import LRS.common.oracle.TestOracle;
import LRS.common.query.ExpectedErrors;
import LRS.mariadb.MariaDBErrors;
import LRS.mariadb.MariaDBProvider.MariaDBGlobalState;
import LRS.mariadb.MariaDBSchema;
import LRS.mariadb.MariaDBSchema.MariaDBTables;
import LRS.mariadb.ast.MariaDBColumnName;
import LRS.mariadb.ast.MariaDBExpression;
import LRS.mariadb.ast.MariaDBJoin;
import LRS.mariadb.ast.MariaDBSelectStatement;
import LRS.mariadb.ast.MariaDBTableReference;
import LRS.mariadb.ast.MariaDBVisitor;
import LRS.mariadb.gen.MariaDBExpressionGenerator;
import LRS.mysql.LR.tester;

public class MariaDBLRSOracle implements TestOracle<MariaDBGlobalState> {
    private final MariaDBGlobalState state;
    private final MariaDBSchema s;
    private MariaDBExpressionGenerator gen;
    private MariaDBSelectStatement select;
    private final ExpectedErrors errors = new ExpectedErrors();

    public MariaDBLRSOracle(MariaDBGlobalState globalState) {
        state = globalState;
        s = globalState.getSchema();
        MariaDBErrors.addCommonErrors(errors);
    }

    @Override
    public void check() throws Exception {
        try {
            MariaDBTables tables = s.getRandomTableNonEmptyTables();
            gen = new MariaDBExpressionGenerator(state.getRandomly()).setColumns(tables.getColumns())
                    .setCon(state.getConnection()).setState(state.getState());

            List<MariaDBExpression> fetchColumns = new ArrayList<>();
            fetchColumns.addAll(Randomly.nonEmptySubset(tables.getColumns()).stream().map(c -> new MariaDBColumnName(c))
                    .collect(Collectors.toList()));

            select = new MariaDBSelectStatement();
            select.setFetchColumns(fetchColumns);

            select.setSelectType(Randomly.fromOptions(MariaDBSelectStatement.MariaDBSelectType.values()));
            if (Randomly.getBoolean()) {
                select.setWhereClause(gen.getRandomExpression());
            }
            if (Randomly.getBoolean()) {
                select.setGroupByClause(fetchColumns);
            }

            List<MariaDBJoin> joinExpressions = MariaDBJoin.getRandomJoinClauses(tables.getTables(), state);
            select.setJoinList(joinExpressions.stream().map(j -> (MariaDBExpression) j).collect(Collectors.toList()));

            select.setFromList(
                    tables.getTables().stream().map(t -> new MariaDBTableReference(t)).collect(Collectors.toList()));

            String originalQueryString = MariaDBVisitor.asString(select);
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
        String fileName = "MariaDB_record.txt";
        try (FileWriter writer = new FileWriter(fileName,true)) {
            writer.write(text);
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}