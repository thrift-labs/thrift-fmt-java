package thriftlabs.thriftfmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import thriftlabs.thriftparser.ThriftParser;

class Util {
    public static final int FAKE_NODE_LINE_NO = -1;
    public static final int FAKE_TOKEN_INDEX = -1; // 用于 token

    public static boolean isToken(ParseTree node, String text) {
        return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getText().equals(text);
    }

    public static boolean isEOF(ParseTree node) {
        return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == ThriftParser.EOF;
    }

    public static boolean isFakeNode(TerminalNode node) {
        return node.getSymbol().getLine() == FAKE_NODE_LINE_NO;
    }

    public static TerminalNode createFakeNode(int type, String text) {
        CommonToken fakeToken = new CommonToken(type, text);
        fakeToken.setLine(FAKE_NODE_LINE_NO);
        fakeToken.setCharPositionInLine(FAKE_NODE_LINE_NO);
        fakeToken.setTokenIndex(FAKE_TOKEN_INDEX);
        return new TerminalNodeImpl(fakeToken);
    }

    public static boolean notSameClass(ParseTree a, ParseTree b) {
        return !a.getClass().equals(b.getClass());
    }

    public static boolean isNeedNewLineNode(ParseTree node) {
        return node instanceof ThriftParser.Enum_fieldContext ||
                node instanceof ThriftParser.Struct_Context ||
                node instanceof ThriftParser.Union_Context ||
                node instanceof ThriftParser.Exception_Context ||
                node instanceof ThriftParser.ServiceContext;
    }

    public static Pair<ParseTree, ParseTree> splitFieldByAssign(ParseTree node) {
        /*
         * 将字段的子节点以等号分割为 [左, 右]
         * 字段: '1: required i32 number_a = 0,'
         * 左: '1: required i32 number_a'
         * 右: '= 0,'
         */
        ParserRuleContext left;
        ParserRuleContext right;
        Pair<List<ParseTree>, List<ParseTree>> splitChildren;

        if (node instanceof ThriftParser.FieldContext) {
            ThriftParser.FieldContext field = (ThriftParser.FieldContext) node;
            left = new ThriftParser.FieldContext(field.getParent(), 0);
            right = new ThriftParser.FieldContext(field.getParent(), 0);
            splitChildren = splitFieldChildrenByAssign(field);
        } else if (node instanceof ThriftParser.Enum_fieldContext) {
            ThriftParser.Enum_fieldContext field = (ThriftParser.Enum_fieldContext) node;
            left = new ThriftParser.Enum_fieldContext(field.getParent(), 0);
            right = new ThriftParser.Enum_fieldContext(field.getParent(), 0);
            splitChildren = splitFieldChildrenByAssign(field);
        } else {
            return null;
        }

        for (ParseTree child : splitChildren.a) {
            left.addAnyChild(child);
        }
        for (ParseTree child : splitChildren.b) {
            right.addAnyChild(child);
        }
        return new Pair<ParseTree, ParseTree>(left, right);
    }

    public static Pair<List<ParseTree>, List<ParseTree>> splitFieldChildrenByAssign(ParserRuleContext node) {
        List<ParseTree> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            children.add(node.getChild(i));
        }

        int i = 0;
        boolean curLeft = true;

        for (; i < node.getChildCount(); i++) {
            ParseTree child = node.getChild(i);
            if (isToken(child, "=") || child instanceof ThriftParser.List_separatorContext) {
                curLeft = false;
                break;
            }
        }

        // 当前子节点属于左侧
        if (curLeft) {
            i++;
        }

        List<ParseTree> left = children.subList(0, i);
        List<ParseTree> right = children.subList(i, children.size());
        return new Pair<List<ParseTree>, List<ParseTree>>(left, right);
    }

    // 获取字段的左右分割大小
    public static Pair<Integer, Integer> getSplitFieldsLeftRightSize(List<ParseTree> fields) {
        int leftMaxSize = 0;
        int rightMaxSize = 0;

        for (ParseTree field : fields) {
            Pair<ParseTree, ParseTree> split = splitFieldByAssign(field); // 需要实现 splitFieldByAssign 方法
            if (split == null) {
                break;
            }
            int leftSize = new PureThriftFormatter().formatNode(split.a).length();
            int rightSize = new PureThriftFormatter().formatNode(split.b).length();

            leftMaxSize = Math.max(leftMaxSize, leftSize);
            rightMaxSize = Math.max(rightMaxSize, rightSize);
        }
        return new Pair<>(leftMaxSize, rightMaxSize);
    }

    // 获取节点的所有子节点
    public static List<ParseTree> getNodeChildren(ParseTree node) {
        int childCount = node.getChildCount();
        List<ParseTree> children = new ArrayList<>(childCount);

        for (int i = 0; i < childCount; i++) {
            children.add(node.getChild(i));
        }
        return children;
    }

    public static NodeProcessFunc genInlineContext(String join, BiPredicate<Integer, ParseTree> tightFn) {
        return new NodeProcessFunc() {
            @Override
            public void process(PureThriftFormatter formatter, ParseTree node) {
                for (int i = 0; i < node.getChildCount(); i++) {
                    ParseTree child = node.getChild(i);
                    if (i > 0 && !join.isEmpty()) {
                        if (tightFn == null || !tightFn.test(i, child)) {
                            formatter.append(join);
                        }
                    }
                    formatter.processNode(child);
                }
            }
        };
    }

    public static NodeProcessFunc genSubblocksContext(int start, Class<?> kindClass) {
        return new NodeProcessFunc() {
            @Override
            public void process(PureThriftFormatter formatter, ParseTree node) {
                List<ParseTree> children = getNodeChildren(node);
                formatter.processInlineNodes(children.subList(0, start), " ");
                formatter.newline();

                List<ParseTree> leftChildren = children.subList(start, children.size());
                Pair<List<ParseTree>, List<ParseTree>> result = splitRepeatNodes(leftChildren, kindClass);
                List<ParseTree> subblocks = result.a; // 符合条件的子块
                List<ParseTree> leftNodes = result.b; // 剩余的节点

                formatter.beforeSubblocks(subblocks);
                formatter.processBlockNodes(subblocks, " ".repeat(formatter.option.getIndent()));
                formatter.afterSubblocks(subblocks);

                formatter.newline();
                formatter.processInlineNodes(leftNodes, " ");
            }
        };
    }

    public static final NodeProcessFunc defaultInline = genInlineContext(" ", null);

    public static final NodeProcessFunc tightInline = genInlineContext("", null);

    public static final NodeProcessFunc listSeparatorInline = genInlineContext(
            " ",
            (index, node) -> node instanceof ThriftParser.List_separatorContext);

    public static final NodeProcessFunc fieldSubblocks = genSubblocksContext(
            3,
            ThriftParser.FieldContext.class);

    public static final NodeProcessFunc tupleTightInline = genInlineContext(
            " ",
            (i, n) -> isToken(n, "(") ||
                    isToken(n, ")") ||
                    (n.getParent() != null && isToken(n.getParent().getChild(i - 1), "(")) ||
                    n instanceof ThriftParser.List_separatorContext);

    // 定义 NodeProcessFunc 接口
    public interface NodeProcessFunc {
        void process(PureThriftFormatter formatter, ParseTree node);
    }

    public static Pair<List<ParseTree>, List<ParseTree>> splitRepeatNodes(List<ParseTree> nodes, Class<?> targetClass) {
        List<ParseTree> children = new ArrayList<>();
        List<ParseTree> left = new ArrayList<>();

        for (int index = 0; index < nodes.size(); index++) {
            ParseTree node = nodes.get(index);
            if (!isSameClass(node, targetClass)) {
                left.addAll(nodes.subList(index, nodes.size()));
                return new Pair<>(children, left);
            }
            children.add(node);
        }
        return new Pair<>(children, left);
    }

    private static boolean isSameClass(ParseTree node, Class<?> targetClass) {
        return targetClass.isInstance(node);
    }

    // 遍历节点
    public static void walkNode(ParseTree root, Consumer<ParseTree> callback) {
        LinkedList<ParseTree> stack = new LinkedList<>();
        stack.add(root);

        while (!stack.isEmpty()) {
            ParseTree node = stack.removeFirst();
            if (node == null) {
                break;
            }

            callback.accept(node);

            List<ParseTree> children = getNodeChildren(node);
            for (ParseTree child : children) {
                stack.add(child);
            }
        }
    }

    public static boolean isFunctionOrThrowsListNode(ParseTree node) {
        return node instanceof ThriftParser.Function_Context ||
                node instanceof ThriftParser.Throws_listContext;
    }

    public static Pair<Map<String, Integer>, Integer> calcFieldAlignByFieldPaddingMap(List<ParseTree> fields) {
        Map<String, Integer> paddingMap = new HashMap<>();
        if (fields.isEmpty() || !isFieldOrEnumField(fields.get(0))) {
            return new Pair<>(paddingMap, 0);
        }

        Map<String, Integer> nameLevels = new HashMap<>();
        for (ParseTree field : fields) {
            for (int i = 0; i < field.getChildCount() - 1; i++) {
                String nameA = getFieldChildName(field.getChild(i));
                String nameB = getFieldChildName(field.getChild(i + 1));

                nameLevels.putIfAbsent(nameA, 0);
                nameLevels.putIfAbsent(nameB, 0);

                int levelB = Math.max(nameLevels.get(nameB), nameLevels.get(nameA) + 1);
                nameLevels.put(nameB, levelB);
            }
        }

        // Check if levels are continuous
        if (Collections.max(nameLevels.values()) != (nameLevels.size() - 1)) {
            return new Pair<>(paddingMap, 0);
        }

        Map<Integer, Integer> levelLength = new HashMap<>();
        for (ParseTree field : fields) {
            for (int i = 0; i < field.getChildCount(); i++) {
                ParseTree child = field.getChild(i);
                int level = nameLevels.get(getFieldChildName(child));
                int length = new PureThriftFormatter().formatNode(child).length();

                levelLength.put(level, Math.max(levelLength.getOrDefault(level, 0), length));
            }
        }

        ThriftParser.List_separatorContext sep = new ThriftParser.List_separatorContext(null, 0);
        Map<Integer, Integer> levelPadding = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : levelLength.entrySet()) {
            int level = entry.getKey();
            int padding = level;
            if (level == nameLevels.get(getFieldChildName(sep))) {
                padding -= 1;
            }

            for (int i = 0; i < level; i++) {
                padding += levelLength.getOrDefault(i, 0);
            }

            levelPadding.put(level, padding);
        }

        for (Map.Entry<String, Integer> entry : nameLevels.entrySet()) {
            String name = entry.getKey();
            int level = entry.getValue();
            paddingMap.put(name, levelPadding.get(level));
        }

        int commentPadding = levelLength.size();
        for (int length : levelLength.values()) {
            commentPadding += length;
        }
        if (paddingMap.containsKey(getFieldChildName(sep))) {
            commentPadding -= 1;
        }

        return new Pair<>(paddingMap, commentPadding);
    }

    public static Pair<Integer, Integer> calcFieldAlignByAssignPadding(List<ParseTree> fields) {
        if (fields.isEmpty() || !isFieldOrEnumField(fields.get(0))) {
            return new Pair<>(0, 0);
        }

        Pair<Integer, Integer> sizes = getSplitFieldsLeftRightSize(fields);
        int leftMaxSize = sizes.a;
        int rightMaxSize = sizes.b;

        // Add extra space "xxx = yyy" -> "xxx" + " " + "= yyy"
        int assignPadding = leftMaxSize + 1;
        int commentPadding = assignPadding + rightMaxSize + 1; // add an extra space for next comment

        /*
         * If it is not list separator, need to add extra space
         * Case 1 --> "1: bool a = true," ---> "1: bool a" + " " + "= true,"
         * Case 2 --> "2: bool b," ---> "2: bool b" + "" + ","
         */
        if (rightMaxSize <= 1) { // Case 1
            commentPadding = commentPadding - 1;
        }

        return new Pair<>(assignPadding, commentPadding);
    }

    public static boolean isFieldOrEnumField(ParseTree field) {
        return field instanceof ThriftParser.FieldContext ||
                field instanceof ThriftParser.Enum_fieldContext;
    }

    public static String getFieldChildName(ParseTree n) {
        if (isToken(n, "=")) {
            return "=";
        }
        return n.getClass().getSimpleName();
    }

    public static int calcSubBlocksCommentPadding(List<ParseTree> subblocks) {
        int padding = 0;
        for (ParseTree subblock : subblocks) {
            int nodeLength = new PureThriftFormatter().formatNode(subblock).length();
            padding = Math.max(padding, nodeLength);
        }

        if (padding > 0) {
            padding += 1;
        }
        return padding;
    }
}
