package LRS.mysql.LR;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.util.SourceStringReader;

import java.util.*;

public class Node {
    public String state;
    public RelNode state_rel;
    public float reward;
    public List children = new ArrayList();
    public float origin_cost;
    int visits = 1;
    Rewriter rewriter;
    Node parent;
    List rewrite_sequence = new ArrayList();
    int node_num = 1;
    float gamma;
    int selected = 0;
    Map activatedRules = new HashMap();
    String name = ""; // the rule's name
    private static Map<String, Integer> usageCountMap = new HashMap<>();

    public Node(String sql, RelNode state_rel, float origin_cost, Rewriter rewriter, float gamma, Node parent,
            String name) throws Exception {
        this.state = sql;
        this.state_rel = state_rel;
        this.reward = (float) (origin_cost - rewriter.getCostRecordFromRelNode(state_rel));
        this.rewriter = rewriter;
        this.parent = parent;
        this.origin_cost = origin_cost;
        this.gamma = gamma;
        this.name = name;

    }

    public void add_child(String csql, RelNode relNode, float origin_cost, Rewriter rewriter, Map activatedRules,
            String name) throws Exception {
        Node child = new Node(csql, relNode, origin_cost, rewriter, this.gamma, parent, name);
        child.activatedRules = activatedRules;
        this.children.add(child);
    }

    public boolean rule_check(RelNode relNode, Class clazz) {
        if (clazz.isInstance(relNode)) {
            return true;
        }
        List rel_list = relNode.getInputs();
        for (int i = 0; i < rel_list.size(); i++) {
            if (rule_check((RelNode) rel_list.get(i), clazz)) {
                return true;
            }
        }
        return false;
    }

    public void node_children() throws Exception {
        for (String rule : this.rewriter.rule2class.keySet()) {
            if (rule_check(this.state_rel, rewriter.rule2class.get(rule))) {
                List res = this.rewriter.singleRewrite(this.state_rel, rule);
                String csql = (String) res.get(1);
                Map activatedRules = new HashMap();
                double new_cost = this.rewriter.getCostRecordFromRelNode((RelNode) res.get(0));
                if (new_cost == -1) {
                    return;
                }
                if (new_cost <= this.origin_cost) {
                    this.add_child(csql, (RelNode) res.get(0), this.origin_cost, this.rewriter, activatedRules, rule);
                }
            }
        }
    }

    private boolean is_terminal() {
        if (this.children.size() > 0 && this.children != null) {
            return false;
        }
        return true;
    }

    public ArrayList<String> UTCSEARCH(int buget, Node root, int parallel_num) throws Exception {
        ArrayList<String> RewriteList = new ArrayList<>();
        root.selected = 1;
        root.node_children();
        for (int i = 0; i < root.children.size(); i++) {
            Node child = (Node) root.children.get(i);
            child.parent = root;
            RewriteList.add(child.state.replace("\"", ""));
        }
        List front_list = new ArrayList();
        for (int i = 0; i < buget; i++) {
            if (parallel_num == 1 || parallel_num > root.node_num) {
                Node front = TREEPOLICY(root);
                front_list.clear();
                front_list.add(front);
            }
            for (int j = 0; j < front_list.size(); j++) {
                Node front = (Node) front_list.get(j);
                front.selected = 1;
                front.node_children();
                for (int k = 0; k < front.children.size(); k++) {
                    Node c = (Node) front.children.get(k);
                    c.parent = front;
                    RewriteList.add(c.state.replace("\"", ""));
                }
                root.node_num += front.children.size();
            }
        }
        return RewriteList;
    }

    private Node BESTCHILD(Node node) throws SqlParseException {
        float bestscore = Float.NEGATIVE_INFINITY;
        List<Node> bestchildren = new ArrayList<>();

        float w1 = 0.25f;
        float w2 = 0.25f;
        float w3 = 0.25f;
        float w4 = -0.25f;
        String Q = node.state;
        String QPrime;
        for (int i = 0; i < node.children.size(); i++) {
            Node c = (Node) node.children.get(i);
            QPrime = c.state;
            float d1 = calculateTreeEditDistance(Q, QPrime);
            float d2 = calculateOperatorMutation(Q, QPrime);
            float d3 = calculateDataCoverage(QPrime);
            float d4 = calculateHistoricalRuleUsage(c.name);
            float Dvi = w1 * d1 + w2 * d2 + w3 * d3 + w4 * d4;
            if (Dvi > bestscore) {
                bestscore = Dvi;
                bestchildren.clear();
                bestchildren.add(c);
            } else if (Dvi == bestscore) {
                bestchildren.add(c);
            }
        }

        Random random = new Random();
        int i = random.nextInt(bestchildren.size());
        return bestchildren.get(i);
    }
    // d1
    private float calculateTreeEditDistance(String Q, String QPrime) throws SqlParseException {
        SqlNode ast1 = this.rewriter.planner.parse(new SourceStringReader(Q));
        SqlNode ast2 = this.rewriter.planner.parse(new SourceStringReader(QPrime));

        return calculateTreeEditDistance(ast1, ast2);
    }
    private static float calculateTreeEditDistance(SqlNode root1, SqlNode root2) {
        return calculateEditDistance(root1, root2);
    }
    private static float calculateEditDistance(SqlNode node1, SqlNode node2) {
        if (node1 == null && node2 == null) {
            return 0;
        }
        if (node1 == null || node2 == null) {
            return 1;
        }

        if (node1.getClass() == node2.getClass() && node1.toString().equals(node2.toString())) {
            return 0;
        }

        float replaceCost = 1;

        List<SqlNode> children1 = getChildren(node1);
        List<SqlNode> children2 = getChildren(node2);

        float[][] dp = new float[children1.size() + 1][children2.size() + 1];

        for (int i = 0; i <= children1.size(); i++) dp[i][0] = i;
        for (int j = 0; j <= children2.size(); j++) dp[0][j] = j;

        for (int i = 1; i <= children1.size(); i++) {
            for (int j = 1; j <= children2.size(); j++) {
                float cost = (children1.get(i - 1).toString().equals(children2.get(j - 1).toString())) ? 0 : replaceCost;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[children1.size()][children2.size()];
    }
    private static List<SqlNode> getChildren(SqlNode node) {
        List<SqlNode> children = new ArrayList<>();

        if (node instanceof SqlCall) {
            SqlCall sqlCall = (SqlCall) node;
            children.addAll(sqlCall.getOperandList());
        } else if (node instanceof SqlIdentifier) {
        } else if (node instanceof SqlLiteral) {
        }
        return children;
    }
    // d2
    private float calculateOperatorMutation(String Q, String QPrime) throws SqlParseException {
        SqlNode ast1 = this.rewriter.planner.parse(new SourceStringReader(Q));
        SqlNode ast2 = this.rewriter.planner.parse(new SourceStringReader(QPrime));
        Set<String> operators1 = extractOperators(ast1);
        Set<String> operators2 = extractOperators(ast2);
        return jaccardSimilarity(operators1, operators2);
    }
    private static Set<String> extractOperators(SqlNode node) {
        Set<String> operators = new HashSet<>();

        if (node instanceof SqlSelect) {
            SqlSelect select = (SqlSelect) node;
            if (select.getFrom() != null) {
                operators.add("FROM");
            }
            if (select.getWhere() != null) {
                operators.add("WHERE");
            }
            if (select.getGroup() != null) {
                operators.add("GROUP BY");
            }
            if (select.getHaving() != null) {
                operators.add("HAVING");
            }
        }

        if (node instanceof SqlCall) {
            // 对于调用操作符的节点（如 JOIN）
            SqlCall sqlCall = (SqlCall) node;
            String operator = sqlCall.getOperator().getName();
            if ("JOIN".equals(operator)) {
                operators.add("JOIN");
            }
        }

        // 对于其他节点，如 SqlIdentifier, SqlLiteral，可能不包含操作符，直接跳过
        if (node instanceof SqlLiteral || node instanceof SqlIdentifier) {
            return operators;
        }

        List<SqlNode> operands = getOperands(node);
        for (SqlNode child : operands) {
            operators.addAll(extractOperators(child));
        }

        return operators;
    }
    private static List<SqlNode> getOperands(SqlNode node) {
        if (node instanceof SqlCall) {
            SqlCall sqlCall = (SqlCall) node;
            return sqlCall.getOperandList();
        }
        return List.of();
    }
    private static float jaccardSimilarity(Set<String> set1, Set<String> set2) {
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 1;
        }

        float similarity = (float) intersection.size() / union.size();
        return 1 - similarity;  // d2(vi, v0) = 1 - Jaccard 相似度
    }
    // d3
    public float calculateDataCoverage(String QPrime) throws SqlParseException {
        SqlNode astQPrime = this.rewriter.planner.parse(new SourceStringReader(QPrime));
        Set<String> newSegments = extractNewSegments(astQPrime);
        float totalCoverage = 0;
        for (String segment : newSegments) {
            float variability = calculateVariability(segment);
            float coverageWeight = calculateCoverageWeight(segment);
            totalCoverage += variability * coverageWeight;
        }
        return totalCoverage;
    }
    private static Set<String> extractNewSegments(SqlNode node) {
        Set<String> newSegments = new HashSet<>();
        if (node instanceof SqlSelect) {
            SqlSelect select = (SqlSelect) node;
            if (select.getFrom() != null) {
                newSegments.add("FROM");
            }
            if (select.getWhere() != null) {
                newSegments.add("WHERE");
            }
        }
        if (node instanceof SqlIdentifier) {
            SqlIdentifier identifier = (SqlIdentifier) node;
            newSegments.add(identifier.toString());
        }
        List<SqlNode> operands = getOperands(node);
        for (SqlNode child : operands) {
            newSegments.addAll(extractNewSegments(child));
        }

        return newSegments;
    }
    private static float calculateVariability(String segment) {
        return 1.0f;
    }
    private static float calculateCoverageWeight(String segment) {
        return 1.0f;
    }
    // d4
    public static float calculateHistoricalRuleUsage(String rule) {
        int ruleUsageCount = UsageCount(rule);
        int maxUsageCount = getMaxUsageCount();
        return 1 - ((float) ruleUsageCount / maxUsageCount);
    }
    private static int UsageCount(String rule) {
        return usageCountMap.getOrDefault(rule, 0);
    }
    private static int getMaxUsageCount() {
        return usageCountMap.values().stream()
                .max(Integer::compare)
                .orElse(0);
    }

    private Node TREEPOLICY(Node node) throws SqlParseException {
        while (!node.is_terminal()) {
            node = BESTCHILD(node);
        }

        return node;
    }

}
