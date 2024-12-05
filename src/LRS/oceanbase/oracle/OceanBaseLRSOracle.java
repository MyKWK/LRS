package LRS.oceanbase.oracle;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import LRS.ComparatorHelper;
import LRS.Randomly;
import LRS.mysql.LR.tester;
import LRS.mysql.ast.MySQLExpression;
import LRS.mysql.ast.MySQLJoin;
import LRS.mysql.ast.MySQLTableReference;
import LRS.oceanbase.OceanBaseGlobalState;
import LRS.oceanbase.OceanBaseSchema;
import LRS.oceanbase.OceanBaseVisitor;
import LRS.oceanbase.ast.OceanBaseExpression;
import LRS.oceanbase.ast.OceanBaseJoin;
import LRS.oceanbase.ast.OceanBaseSelect;
import LRS.oceanbase.ast.OceanBaseTableReference;
import LRS.oceanbase.gen.OceanBaseExpressionGenerator;
import LRS.oceanbase.gen.OceanBaseHintGenerator;

public class OceanBaseLRSOracle extends OceanBaseTLPBase {

    public OceanBaseLRSOracle(OceanBaseGlobalState state) {
        super(state);
    }

    @Override
    public void check() throws Exception {
        s = state.getSchema();
        targetTables = s.getRandomTableNonEmptyTables();
        gen = new OceanBaseExpressionGenerator(state).setColumns(targetTables.getColumns());
        initializeTernaryPredicateVariants();
        select = new OceanBaseSelect();
        List<OceanBaseExpression> fetchColumns = generateFetchColumns();
        select.setFetchColumns(fetchColumns);
        List<OceanBaseSchema.OceanBaseTable> tables = targetTables.getTables();
        OceanBaseHintGenerator.generateHints(select, tables);
        List<OceanBaseExpression> tableList = tables.stream().map(t -> new OceanBaseTableReference(t))
                .collect(Collectors.toList());
        select.setFromList(tableList);
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression());
        }
        if (Randomly.getBoolean()) {
            select.setGroupByExpressions(fetchColumns);
            if (Randomly.getBoolean()) {
                select.setHavingClause(gen.generateExpression());
            }
        }
        String originalQueryString = OceanBaseVisitor.asString(select);
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
        String fileName = "OB_record.txt";
        try (FileWriter writer = new FileWriter(fileName,true)) {
            writer.write(text);
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
