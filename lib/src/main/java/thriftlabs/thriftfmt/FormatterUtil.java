package thriftlabs.thriftfmt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import thriftlabs.thriftparser.ThriftParser;
import thriftlabs.thriftparser.ThriftParser.FieldContext;

public final class FormatterUtil {
    private static final int FAKE_NODE_LINE_NO = -1;

    public static boolean isToken(ParseTree node, String text) {
        return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getText().equals(text);
    }

    public static boolean isEOF(ParseTree node) {
        return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == ThriftParser.EOF;
    }

    public static boolean isFakeNode(TerminalNode node) {
        return node.getSymbol().getLine() == FAKE_NODE_LINE_NO;
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

    public static ParseTree[] splitFieldByAssign(ThriftParser.FieldContext node) {
        /*
         * 将字段的子节点分割为 [左, 右]
         * 字段: '1: required i32 number_a = 0,'
         * 左: '1: required i32 number_a'
         * 右: '= 0,'
         */
        ParserRuleContext left;
        ParserRuleContext right;

        if (node instanceof ThriftParser.FieldContext) {
            left = new ThriftParser.FieldContext(node.getParent(), 0);
            right = new ThriftParser.FieldContext(node.getParent(), 0);
        } else {
            left = new ThriftParser.Enum_fieldContext(node.getParent(), 0);
            right = new ThriftParser.Enum_fieldContext(node.getParent(), 0);
        }

        ParseTree[][] splitChildren = splitFieldChildrenByAssign(node);
        ParseTree[] leftChildren = splitChildren[0];
        ParseTree[] rightChildren = splitChildren[1];

        for (ParseTree child : leftChildren) {
            left.addAnyChild(child); // 假设 addAnyChild 方法已定义
        }
        for (ParseTree child : rightChildren) {
            right.addAnyChild(child); // 假设 addAnyChild 方法已定义
        }

        return new ParseTree[] { left, right };
    }

    public static ParseTree[][] splitFieldChildrenByAssign(FieldContext node) {
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

        return new ParseTree[][] { left.toArray(new ParseTree[0]), right.toArray(new ParseTree[0]) };
    }

    // 获取字段的左右分割大小
    public static int[] getSplitFieldsLeftRightSize(ParseTree[] fields) {
        int leftMaxSize = 0;
        int rightMaxSize = 0;

        for (ParseTree field : fields) {
            FieldContext node = (FieldContext) field; // 假设 FieldContext 是已定义的
            ParseTree[] split = splitFieldByAssign(node); // 需要实现 splitFieldByAssign 方法
            ParseTree left = split[0];
            ParseTree right = split[1];

            int leftSize = new PureThriftFormatter().formatNode(left).length();
            int rightSize = new PureThriftFormatter().formatNode(right).length();

            leftMaxSize = Math.max(leftMaxSize, leftSize);
            rightMaxSize = Math.max(rightMaxSize, rightSize);
        }

        return new int[] { leftMaxSize, rightMaxSize };
    }

    // 获取节点的所有子节点
    public static List<ParseTree> getNodeChildren(ParseTree node) {
        int childCount = node.getChildCount();
        ParseTree[] children = new ParseTree[childCount];

        for (int i = 0; i < childCount; i++) {
            children[i] = node.getChild(i);
        }

        return List.of(children);
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
                ParseTree[][] result = splitRepeatNodes(leftChildren.toArray(new ParseTree[0]), kindClass);
                ParseTree[] subblocks = result[0]; // 符合条件的子块
                ParseTree[] leftNodes = result[1]; // 剩余的节点

                formatter.beforeSubblocks(List.of(subblocks));
                formatter.processBlockNodes(List.of(subblocks), " ".repeat(formatter.option.indent));
                formatter.afterSubblocks(List.of(subblocks));

                formatter.newline();
                formatter.processInlineNodes(List.of(leftNodes), " ");
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

    public static ParseTree[][] splitRepeatNodes(ParseTree[] nodes, Class<?> targetClass) {
        List<ParseTree> children = new ArrayList<>();

        for (int index = 0; index < nodes.length; index++) {
            ParseTree node = nodes[index];
            if (!isSameClass(node, targetClass)) {
                return new ParseTree[][] { children.toArray(new ParseTree[0]), getSubArray(nodes, index) };
            }
            children.add(node);
        }
        return new ParseTree[][] { children.toArray(new ParseTree[0]), new ParseTree[0] };
    }

    private static boolean isSameClass(ParseTree node, Class<?> targetClass) {
        return targetClass.isInstance(node);
    }

    private static ParseTree[] getSubArray(ParseTree[] nodes, int startIndex) {
        ParseTree[] subArray = new ParseTree[nodes.length - startIndex];
        System.arraycopy(nodes, startIndex, subArray, 0, nodes.length - startIndex);
        return subArray;
    }
}
