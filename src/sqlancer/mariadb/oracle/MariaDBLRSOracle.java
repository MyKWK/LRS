package sqlancer.mariadb.oracle;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.mariadb.MariaDBErrors;
import sqlancer.mariadb.MariaDBProvider.MariaDBGlobalState;
import sqlancer.mariadb.MariaDBSchema;
import sqlancer.mariadb.MariaDBSchema.MariaDBTables;
import sqlancer.mariadb.ast.MariaDBColumnName;
import sqlancer.mariadb.ast.MariaDBExpression;
import sqlancer.mariadb.ast.MariaDBJoin;
import sqlancer.mariadb.ast.MariaDBSelectStatement;
import sqlancer.mariadb.ast.MariaDBTableReference;
import sqlancer.mariadb.ast.MariaDBVisitor;
import sqlancer.mariadb.gen.MariaDBExpressionGenerator;
import sqlancer.mysql.LR.tester;

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

            // Set the join.
            List<MariaDBJoin> joinExpressions = MariaDBJoin.getRandomJoinClauses(tables.getTables(), state);
            select.setJoinList(joinExpressions.stream().map(j -> (MariaDBExpression) j).collect(Collectors.toList()));

            // Set the from clause from the tables that are not used in the join.
            select.setFromList(
                    tables.getTables().stream().map(t -> new MariaDBTableReference(t)).collect(Collectors.toList()));

            // Get the result of the first query
            String originalQueryString = MariaDBVisitor.asString(select);
            System.out.println(originalQueryString + "\n");
            List<List<Object>> originalResultSet = ComparatorHelper.getResultSet(originalQueryString,errors,state);
            // 进行重写
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void writeToFile(String text) {
        String fileName = "mariadb_sql.txt";
        try (FileWriter writer = new FileWriter(fileName,true)) {
            writer.write(text);
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}