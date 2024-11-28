package thriftlabs.thriftfmt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import thriftlabs.thriftparser.ThriftParser;
import thriftlabs.thriftparser.ThriftParser.FieldContext;

public class PureThriftFormatter {

    protected static class Option {
        int indent;
        boolean patchRequired;
        boolean patchSeparator;
        boolean keepComment;
        boolean alignByAssign;
        boolean alignByField;

        public Option(int indent, boolean patchRequired, boolean patchSeparator, boolean keepComment,
                boolean alignByAssign, boolean alignByField) {
            this.indent = indent;
            this.patchRequired = patchRequired;
            this.patchSeparator = patchSeparator;
            this.keepComment = keepComment;
            this.alignByAssign = alignByAssign;
            this.alignByField = alignByField;
        }
    }

    protected Option option = new Option(4, true, true, true, false, false);
    private String out;
    private int newlineCounter;
    private String currentIndent;

    public void option(Option opt) {
        this.option = opt;
    }

    public String getOut() {
        return out;
    }

    protected static class Utils {
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

            ParseTree[][] splitChildren = Utils.splitFieldChildrenByAssign(node);
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
                ParseTree[] split = Utils.splitFieldByAssign(node); // 需要实现 splitFieldByAssign 方法
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

        // 定义 NodeProcessFunc 接口
        public interface NodeProcessFunc {
            void process(PureThriftFormatter formatter, ParseTree node);
        }
    }

    public String formatNode(ParseTree node) {
        out = "";
        newlineCounter = 0;
        currentIndent = "";
        processNode(node);
        return out;
    }

    private void push(String text) {
        out += text;
    }

    protected void append(String text) {
        if (newlineCounter > 0) {
            push("\n".repeat(newlineCounter));
        }
        newlineCounter = 0;
        push(text);
    }

    // appendCurrentLine append to current line, and ignore this.newlineCounter.
    protected void appendCurrentLine(String text) {
        push(text);
    }

    protected void newline(int repeat) {
        int diff = repeat - newlineCounter;
        if (diff > 0) {
            newlineCounter += diff;
        }
    }

    protected void newline() {
        newline(1);
    }

    protected void setCurrentIndent(String indent) {
        this.currentIndent = indent;
    }

    protected void pushCurrentIndent() {
        if (currentIndent.length() > 0) {
            append(currentIndent);
        }
    }

    protected void beforeBlockNode(ParseTree node) {
    }

    protected void afterBlockNode(ParseTree node) {
    }

    protected void beforeSubblocks(List<ParseTree> subblocks) {
    }

    protected void afterSubblocks(List<ParseTree> subblocks) {
    }

    protected void beforeProcessNode(ParseTree node) {
    }

    protected void afterProcessNode(ParseTree node) {
    }

    protected void processBlockNodes(List<ParseTree> nodes, String indent) {
        ParseTree lastNode = null;
        for (int index = 0; index < nodes.size(); index++) {
            ParseTree node = nodes.get(index);
            if (node instanceof ThriftParser.HeaderContext || node instanceof ThriftParser.DefinitionContext) {
                node = node.getChild(0);
            }
            beforeBlockNode(node);
            if (index > 0 && lastNode != null) {
                if (!lastNode.getClass().equals(node.getClass()) || Utils.isNeedNewLineNode(node)) {
                    newline(2);
                } else {
                    newline();
                }
            }
            setCurrentIndent(indent);
            processNode(node);
            afterBlockNode(node);
            lastNode = node;
        }
    }

    protected void processInlineNodes(List<ParseTree> nodes, String join) {
        for (int index = 0; index < nodes.size(); index++) {
            ParseTree node = nodes.get(index);
            if (index > 0) {
                append(join);
            }
            processNode(node);
        }
    }

    protected void processNode(ParseTree node) {
        beforeProcessNode(node);
        _processNode(node);
        afterProcessNode(node);
    }

    private void _processNode(ParseTree node) {
        if (node instanceof TerminalNode) {
            TerminalNode((TerminalNode) node);
        } else if (node instanceof ThriftParser.DocumentContext) {
            DocumentContext((ThriftParser.DocumentContext) node);
        } else if (node instanceof ThriftParser.HeaderContext) {
            HeaderContext((ThriftParser.HeaderContext) node);
        } else if (node instanceof ThriftParser.DefinitionContext) {
            DefinitionContext((ThriftParser.DefinitionContext) node);
        } else if (node instanceof ThriftParser.Include_Context) {
            Include_Context((ThriftParser.Include_Context) node);
        } else if (node instanceof ThriftParser.Namespace_Context) {
            Namespace_Context((ThriftParser.Namespace_Context) node);
        } else if (node instanceof ThriftParser.Typedef_Context) {
            Typedef_Context((ThriftParser.Typedef_Context) node);
        } else if (node instanceof ThriftParser.Base_typeContext) {
            Base_typeContext((ThriftParser.Base_typeContext) node);
        } else if (node instanceof ThriftParser.Real_base_typeContext) {
            Real_base_typeContext((ThriftParser.Real_base_typeContext) node);
        } else if (node instanceof ThriftParser.Const_ruleContext) {
            Const_ruleContext((ThriftParser.Const_ruleContext) node);
        } else if (node instanceof ThriftParser.Const_valueContext) {
            Const_valueContext((ThriftParser.Const_valueContext) node);
        } else if (node instanceof ThriftParser.IntegerContext) {
            IntegerContext((ThriftParser.IntegerContext) node);
        } else if (node instanceof ThriftParser.Container_typeContext) {
            Container_typeContext((ThriftParser.Container_typeContext) node);
        } else if (node instanceof ThriftParser.Set_typeContext) {
            Set_typeContext((ThriftParser.Set_typeContext) node);
        } else if (node instanceof ThriftParser.List_typeContext) {
            List_typeContext((ThriftParser.List_typeContext) node);
        } else if (node instanceof ThriftParser.Cpp_typeContext) {
            Cpp_typeContext((ThriftParser.Cpp_typeContext) node);
        } else if (node instanceof ThriftParser.Const_mapContext) {
            Const_mapContext((ThriftParser.Const_mapContext) node);
        } else if (node instanceof ThriftParser.Const_map_entryContext) {
            Const_map_entryContext((ThriftParser.Const_map_entryContext) node);
        } else if (node instanceof ThriftParser.List_separatorContext) {
            List_separatorContext((ThriftParser.List_separatorContext) node);
        } else if (node instanceof ThriftParser.Field_idContext) {
            Field_idContext((ThriftParser.Field_idContext) node);
        } else if (node instanceof ThriftParser.Field_reqContext) {
            Field_reqContext((ThriftParser.Field_reqContext) node);
        } else if (node instanceof ThriftParser.Field_typeContext) {
            Field_typeContext((ThriftParser.Field_typeContext) node);
        } else if (node instanceof ThriftParser.Map_typeContext) {
            Map_typeContext((ThriftParser.Map_typeContext) node);
        } else if (node instanceof ThriftParser.Const_listContext) {
            Const_listContext((ThriftParser.Const_listContext) node);
        } else if (node instanceof ThriftParser.Enum_ruleContext) {
            Enum_ruleContext((ThriftParser.Enum_ruleContext) node);
        } else if (node instanceof ThriftParser.Struct_Context) {
            Struct_Context((ThriftParser.Struct_Context) node);
        } else if (node instanceof ThriftParser.Union_Context) {
            Union_Context((ThriftParser.Union_Context) node);
        } else if (node instanceof ThriftParser.Exception_Context) {
            Exception_Context((ThriftParser.Exception_Context) node);
        } else if (node instanceof ThriftParser.Enum_fieldContext) {
            Enum_fieldContext((ThriftParser.Enum_fieldContext) node);
        } else if (node instanceof ThriftParser.FieldContext) {
            FieldContext((ThriftParser.FieldContext) node);
        } else if (node instanceof ThriftParser.Function_Context) {
            Function_Context((ThriftParser.Function_Context) node);
        } else if (node instanceof ThriftParser.OnewayContext) {
            OnewayContext((ThriftParser.OnewayContext) node);
        } else if (node instanceof ThriftParser.Function_typeContext) {
            Function_typeContext((ThriftParser.Function_typeContext) node);
        } else if (node instanceof ThriftParser.Throws_listContext) {
            Throws_listContext((ThriftParser.Throws_listContext) node);
        } else if (node instanceof ThriftParser.Type_annotationsContext) {
            Type_annotationsContext((ThriftParser.Type_annotationsContext) node);
        } else if (node instanceof ThriftParser.Type_annotationContext) {
            Type_annotationContext((ThriftParser.Type_annotationContext) node);
        } else if (node instanceof ThriftParser.Annotation_valueContext) {
            Annotation_valueContext((ThriftParser.Annotation_valueContext) node);
        } else if (node instanceof ThriftParser.ServiceContext) {
            ServiceContext((ThriftParser.ServiceContext) node);
        } else if (node instanceof ThriftParser.SenumContext) {
            SenumContext((ThriftParser.SenumContext) node);
        }
        // unsupport types
    }

    private void TerminalNode(TerminalNode node) {
        if (Utils.isEOF(node)) {
            return;
        }

        this.pushCurrentIndent();
        this.setCurrentIndent("");
        this.append(node.getSymbol().getText());
    }

    protected void DocumentContext(ThriftParser.DocumentContext node) {
        this.processBlockNodes(Utils.getNodeChildren(node), "");
    }

    protected void HeaderContext(ThriftParser.HeaderContext node) {
        this.processNode(node.getChild(0));
    }

    protected void DefinitionContext(ThriftParser.DefinitionContext node) {
        this.processNode(node.getChild(0));
    }

    private void Include_Context(ThriftParser.Include_Context node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Namespace_Context(ThriftParser.Namespace_Context node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Typedef_Context(ThriftParser.Typedef_Context node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Base_typeContext(ThriftParser.Base_typeContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Real_base_typeContext(ThriftParser.Real_base_typeContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Const_ruleContext(ThriftParser.Const_ruleContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Const_valueContext(ThriftParser.Const_valueContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void IntegerContext(ThriftParser.IntegerContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Container_typeContext(ThriftParser.Container_typeContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Set_typeContext(ThriftParser.Set_typeContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void List_typeContext(ThriftParser.List_typeContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Cpp_typeContext(ThriftParser.Cpp_typeContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Const_mapContext(ThriftParser.Const_mapContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Const_map_entryContext(ThriftParser.Const_map_entryContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void List_separatorContext(ThriftParser.List_separatorContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Field_idContext(ThriftParser.Field_idContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Field_reqContext(ThriftParser.Field_reqContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Field_typeContext(ThriftParser.Field_typeContext node) {
        Utils.genInlineContext("", null).process(this, node);
    }

    protected void Map_typeContext(ThriftParser.Map_typeContext node) {
        // (i, n) => !isToken(n.parent?.getChild(i - 1), ',')
        BiPredicate<Integer, ParseTree> tightFn = (index, child) -> {
            if (child.getParent() == null) {
                return false;
            }
            if (!Utils.isToken(child.getParent().getChild(index - 1), ",")) {
                return true;
            }
            return false;
        };
        Utils.genInlineContext(" ", tightFn).process(this, node);
    }

    protected void Const_listContext(ThriftParser.Const_listContext node) {
    }

    protected void Enum_ruleContext(ThriftParser.Enum_ruleContext node) {
    }

    protected void Struct_Context(ThriftParser.Struct_Context node) {
    }

    protected void Union_Context(ThriftParser.Union_Context node) {
    }

    protected void Exception_Context(ThriftParser.Exception_Context node) {
    }

    protected void Enum_fieldContext(ThriftParser.Enum_fieldContext node) {
    }

    protected void FieldContext(ThriftParser.FieldContext node) {
    }

    protected void Function_Context(ThriftParser.Function_Context node) {
    }

    protected void OnewayContext(ThriftParser.OnewayContext node) {
    }

    protected void Function_typeContext(ThriftParser.Function_typeContext node) {
    }

    protected void Throws_listContext(ThriftParser.Throws_listContext node) {
    }

    protected void Type_annotationsContext(ThriftParser.Type_annotationsContext node) {
    }

    protected void Type_annotationContext(ThriftParser.Type_annotationContext node) {
    }

    protected void Annotation_valueContext(ThriftParser.Annotation_valueContext node) {
    }

    protected void ServiceContext(ThriftParser.ServiceContext node) {
    }

    protected void SenumContext(ThriftParser.SenumContext node) {
    }
}
