package sqlancer.mysql.LR;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.apache.calcite.util.SourceStringReader;
import org.apache.commons.logging.impl.AvalonLogger;
import javax.swing.LayoutStyle;
import org.checkerframework.checker.units.qual.A;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
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
        List rel_list = relNode.getInputs(); // 获取relNode的直接父节点,还不止一个???
        for (int i = 0; i < rel_list.size(); i++) {
            if (rule_check((RelNode) rel_list.get(i), clazz)) {
                return true;
            }
        }
        return false;
    }

    public void node_children() throws Exception {
        // todo rule selection
        for (String rule : this.rewriter.rule2class.keySet()) {
            // 遍历rewriter中rule集中的key值集
            if (rule_check(this.state_rel, rewriter.rule2class.get(rule))) {
                List res = this.rewriter.singleRewrite(this.state_rel, rule);
                // 使用rewriter中方法 单次重写
                String csql = (String) res.get(1); // 获取改写后的结果
                Map activatedRules = new HashMap(); // 用来记录query-rule键值对
                double new_cost = this.rewriter.getCostRecordFromRelNode((RelNode) res.get(0));
                if (new_cost == -1) {
                    return;
                }
                if (new_cost <= this.origin_cost) {
                    // todo rule selection
                    // System.out.println("new node added..");
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
        root.selected = 1;// 这个节点被选中了
        root.node_children();// 生成第一波子节点
        // 循环所有子节点,成为这些节点的父节点
        for (int i = 0; i < root.children.size(); i++) {
            Node child = (Node) root.children.get(i);
            child.parent = root;
            RewriteList.add(child.state.replace("\"", ""));
        }
        List front_list = new ArrayList();
        for (int i = 0; i < buget; i++) {
            if (parallel_num == 1 || parallel_num > root.node_num) {
                Node front = TREEPOLICY(root); // 返回"太子"
                front_list.clear();
                front_list.add(front); // 这是为了并行,对于多个树而言
            }
            for (int j = 0; j < front_list.size(); j++) {
                Node front = (Node) front_list.get(j); // 选择一个front
                front.selected = 1; // 该节点被搜索过了
                front.node_children(); // 生成该节点的孩子
                // 下面进行rule选择
                for (int k = 0; k < front.children.size(); k++) { // 遍历front的孩子
                    Node c = (Node) front.children.get(k);
                    c.parent = front; // 设置其父节点
                    RewriteList.add(c.state.replace("\"", ""));
                }
                root.node_num += front.children.size(); // 更新root节点信息中的子节点数量
                float reward = FINDBESTREWARD(front); // 不管了
                BACKUP(front, reward); // 不管了
            }
        }
        Node best_node = FINDBESTNODE(root); // 获取root的子节点中收益最大的
        // return best_node;
        return RewriteList;
    }

    private float FINDBESTREWARD(Node node) {
        float reward = -1;
        for (int i = 0; i < node.children.size(); i++) {
            Node c = (Node) node.children.get(i);
            if (c.reward > reward) {
                reward = c.reward;
            }
        }
        return reward;
    }

    private Node BESTCHILD(Node node) throws SqlParseException {
        // todo 选取最佳子节点 : node是root节点
        float bestscore = Float.NEGATIVE_INFINITY;
        List<Node> bestchildren = new ArrayList<>();

        // 预设的权重
        float w1 = 0.25f;
        float w2 = 0.25f;
        float w3 = 0.25f;
        float w4 = -0.25f;  // w4 为负值，用于惩罚

        // 获取 Q 和 Q'（原始查询和重写查询）
        String Q = node.state;
        String QPrime; // 需要为每个子节点生成 Q'

        // 遍历所有子节点并计算新的评分
        for (int i = 0; i < node.children.size(); i++) {
            Node c = (Node) node.children.get(i);

            // 假设 c.query 是重写后的查询 Q′
            QPrime = c.state;

            // 计算每个 d1 到 d4
            float d1 = calculateTreeEditDistance(Q, QPrime);  // 计算树编辑距离
            float d2 = calculateOperatorMutation(Q, QPrime);  // 计算操作符变异
            float d3 = calculateDataCoverage(QPrime);  // 计算数据覆盖多样性
            float d4 = calculateHistoricalRuleUsage(c.name);  // 计算历史规则使用惩罚
            float Dvi = w1 * d1 + w2 * d2 + w3 * d3 + w4 * d4;

            // 如果当前得分更好，则更新最佳子节点
            if (Dvi > bestscore) {
                bestscore = Dvi;
                bestchildren.clear();
                bestchildren.add(c);
            } else if (Dvi == bestscore) {
                bestchildren.add(c);  // 如果得分相同，加入多个子节点
            }
        }

        // 随机选择一个最佳子节点
        Random random = new Random();
        int i = random.nextInt(bestchildren.size());
        return bestchildren.get(i);
    }
    // d1
    private float calculateTreeEditDistance(String Q, String QPrime) throws SqlParseException {
        // 解析 SQL 字符串为 SqlNode (AST)
        SqlNode ast1 = this.rewriter.planner.parse(new SourceStringReader(Q));
        SqlNode ast2 = this.rewriter.planner.parse(new SourceStringReader(QPrime));

        // 使用动态规划计算树编辑距离
        return calculateTreeEditDistance(ast1, ast2);
    }
    private static float calculateTreeEditDistance(SqlNode root1, SqlNode root2) {
        // 使用递归方式计算树的编辑距离
        return calculateEditDistance(root1, root2);
    }
    private static float calculateEditDistance(SqlNode node1, SqlNode node2) {
        if (node1 == null && node2 == null) {
            return 0;
        }
        if (node1 == null || node2 == null) {
            return 1;  // 如果一个节点为空，另一节点非空，表示插入或删除操作
        }

        // 如果节点类型和内容相同，则没有编辑操作
        if (node1.getClass() == node2.getClass() && node1.toString().equals(node2.toString())) {
            return 0;
        }

        // 计算替换操作的代价
        float replaceCost = 1;

        // 递归计算子节点之间的编辑距离
        List<SqlNode> children1 = getChildren(node1);
        List<SqlNode> children2 = getChildren(node2);

        // 使用动态规划计算编辑距离
        float[][] dp = new float[children1.size() + 1][children2.size() + 1];

        // 初始化边界条件：空树的编辑距离
        for (int i = 0; i <= children1.size(); i++) dp[i][0] = i;
        for (int j = 0; j <= children2.size(); j++) dp[0][j] = j;

        // 填充 dp 表，递归计算子树之间的编辑距离
        for (int i = 1; i <= children1.size(); i++) {
            for (int j = 1; j <= children2.size(); j++) {
                float cost = (children1.get(i - 1).toString().equals(children2.get(j - 1).toString())) ? 0 : replaceCost;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),  // 插入或删除操作
                        dp[i - 1][j - 1] + cost  // 替换操作
                );
            }
        }

        // 返回最终的树编辑距离
        return dp[children1.size()][children2.size()];
    }
    private static List<SqlNode> getChildren(SqlNode node) {
        List<SqlNode> children = new ArrayList<>();

        // 判断节点类型并获取相应的子节点
        if (node instanceof SqlCall) {
            // SqlCall 节点有操作数（子节点）
            SqlCall sqlCall = (SqlCall) node;
            children.addAll(sqlCall.getOperandList());
        } else if (node instanceof SqlIdentifier) {
            // SqlIdentifier 没有子节点（标识符节点）
            // 可以选择返回空的子节点列表
        } else if (node instanceof SqlLiteral) {
            // SqlLiteral 没有子节点（字面量节点）
            // 同样返回空的子节点列表
        }
        // 对于其他类型的节点（如语法糖节点），可以根据需要添加处理逻辑

        return children;
    }
    // d2
    private float calculateOperatorMutation(String Q, String QPrime) throws SqlParseException {
        SqlNode ast1 = this.rewriter.planner.parse(new SourceStringReader(Q));
        SqlNode ast2 = this.rewriter.planner.parse(new SourceStringReader(QPrime));
        // 提取操作符
        Set<String> operators1 = extractOperators(ast1);
        Set<String> operators2 = extractOperators(ast2);
        // 计算 Jaccard 相似度
        return jaccardSimilarity(operators1, operators2);
    }
    private static Set<String> extractOperators(SqlNode node) {
        Set<String> operators = new HashSet<>();

        if (node instanceof SqlSelect) {
            // 提取 SELECT 查询中的操作符（如 FROM, WHERE, GROUP BY 等）
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
            // SqlCall 节点有操作数（子节点）
            SqlCall sqlCall = (SqlCall) node;
            return sqlCall.getOperandList();  // 获取操作符的操作数
        }
        return List.of();  // 对于没有子节点的节点，返回空列表
    }
    private static float jaccardSimilarity(Set<String> set1, Set<String> set2) {
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);  // 计算交集

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);  // 计算并集

        // 计算 Jaccard 相似度
        if (union.isEmpty()) {
            return 1;  // 防止除以零的情况
        }

        float similarity = (float) intersection.size() / union.size();
        return 1 - similarity;  // d2(vi, v0) = 1 - Jaccard 相似度
    }
    // d3
    public float calculateDataCoverage(String QPrime) throws SqlParseException {
        // 解析 SQL 字符串为 SqlNode (AST)
        SqlNode astQPrime = this.rewriter.planner.parse(new SourceStringReader(QPrime));

        // 提取新查询中的数据片段
        Set<String> newSegments = extractNewSegments(astQPrime);

        // 计算数据片段的变异性和覆盖权重
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

        // 如果是 SELECT 查询，提取涉及的表、列等
        if (node instanceof SqlSelect) {
            SqlSelect select = (SqlSelect) node;

            // 提取查询中涉及的表
            if (select.getFrom() != null) {
                newSegments.add("FROM");
            }

            // 提取查询中的过滤条件
            if (select.getWhere() != null) {
                newSegments.add("WHERE");
            }

            // 这里可以继续添加更多的判断逻辑，根据需要提取更多的数据片段
        }

        // 对于其他节点类型（如 SqlCall, SqlLiteral, SqlIdentifier 等），可以进行相应处理
        if (node instanceof SqlIdentifier) {
            SqlIdentifier identifier = (SqlIdentifier) node;
            newSegments.add(identifier.toString());  // 表或列名
        }

        // 递归提取子节点中的数据片段
        List<SqlNode> operands = getOperands(node);
        for (SqlNode child : operands) {
            newSegments.addAll(extractNewSegments(child));
        }

        return newSegments;
    }
    private static float calculateVariability(String segment) {
        // todo : calculate the Variability
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

    private void BACKUP(Node node, float reward) {
        while (node != null) {
            node.visits += 1;
            if (reward > node.reward) {
                node.reward = reward;
            }
            node = node.parent;
        }
    }

    private Node FINDBESTNODE(Node best_node) {
        while (!best_node.is_terminal()) {
            float bestscore = ((Node) best_node.children.get(0)).reward;
            List bestchildren = new ArrayList();
            bestchildren.add(best_node.children.get(0));
            for (int i = 0; i < best_node.children.size(); i++) {
                Node c = (Node) best_node.children.get(i);
                float score = c.reward;
                if (score > bestscore) {
                    bestchildren.clear();
                    bestchildren.add(c);
                    bestscore = score;
                } else if (score == bestscore) {
                    bestchildren.add(c);
                }

            }
            Random random = new Random();
            int i = random.nextInt(bestchildren.size());
            best_node = (Node) bestchildren.get(i);
        }
        return best_node;
    }
}
