package thriftlabs.thriftfmt;

import java.util.List;
import java.util.function.BiPredicate;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import thriftlabs.thriftparser.ThriftParser;

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
    protected String out;
    protected int newlineCounter;
    protected String currentIndent;

    public void option(Option opt) {
        this.option = opt;
    }

    public String getOut() {
        return out;
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
                if (!lastNode.getClass().equals(node.getClass()) || FormatterUtil.isNeedNewLineNode(node)) {
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

    protected void TerminalNode(TerminalNode node) {
        if (FormatterUtil.isEOF(node)) {
            return;
        }

        this.pushCurrentIndent();
        this.setCurrentIndent("");
        this.append(node.getSymbol().getText());
    }

    protected void DocumentContext(ThriftParser.DocumentContext node) {
        this.processBlockNodes(FormatterUtil.getNodeChildren(node), "");
    }

    protected void HeaderContext(ThriftParser.HeaderContext node) {
        this.processNode(node.getChild(0));
    }

    protected void DefinitionContext(ThriftParser.DefinitionContext node) {
        this.processNode(node.getChild(0));
    }

    private void Include_Context(ThriftParser.Include_Context node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Namespace_Context(ThriftParser.Namespace_Context node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Typedef_Context(ThriftParser.Typedef_Context node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Base_typeContext(ThriftParser.Base_typeContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Real_base_typeContext(ThriftParser.Real_base_typeContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Const_ruleContext(ThriftParser.Const_ruleContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Const_valueContext(ThriftParser.Const_valueContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void IntegerContext(ThriftParser.IntegerContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Container_typeContext(ThriftParser.Container_typeContext node) {
        FormatterUtil.tightInline.process(this, node);
    }

    protected void Set_typeContext(ThriftParser.Set_typeContext node) {
        FormatterUtil.tightInline.process(this, node);
    }

    protected void List_typeContext(ThriftParser.List_typeContext node) {
        FormatterUtil.tightInline.process(this, node);
    }

    protected void Cpp_typeContext(ThriftParser.Cpp_typeContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Const_mapContext(ThriftParser.Const_mapContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Const_map_entryContext(ThriftParser.Const_map_entryContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void List_separatorContext(ThriftParser.List_separatorContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Field_idContext(ThriftParser.Field_idContext node) {
        FormatterUtil.tightInline.process(this, node);
    }

    protected void Field_reqContext(ThriftParser.Field_reqContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Field_typeContext(ThriftParser.Field_typeContext node) {
        FormatterUtil.defaultInline.process(this, node);
    }

    protected void Map_typeContext(ThriftParser.Map_typeContext node) {
        // (i, n) => !isToken(n.parent?.getChild(i - 1), ',')
        BiPredicate<Integer, ParseTree> tightFn = (index, child) -> {
            if (child.getParent() == null) {
                return false;
            }
            if (!FormatterUtil.isToken(child.getParent().getChild(index - 1), ",")) {
                return true;
            }
            return false;
        };
        FormatterUtil.genInlineContext(" ", tightFn).process(this, node);
    }

    protected void Const_listContext(ThriftParser.Const_listContext node) {
        FormatterUtil.listSeparatorInline.process(this, node);
    }

    protected void Enum_ruleContext(ThriftParser.Enum_ruleContext node) {
        FormatterUtil.genSubblocksContext(3, ThriftParser.Enum_fieldContext.class).process(this, node);
    }

    protected void Struct_Context(ThriftParser.Struct_Context node) {
        FormatterUtil.fieldSubblocks.process(this, node);
    }

    protected void Union_Context(ThriftParser.Union_Context node) {
        FormatterUtil.fieldSubblocks.process(this, node);
    }

    protected void Exception_Context(ThriftParser.Exception_Context node) {
        FormatterUtil.fieldSubblocks.process(this, node);
    }

    protected void Enum_fieldContext(ThriftParser.Enum_fieldContext node) {
        FormatterUtil.listSeparatorInline.process(this, node);
    }

    protected void FieldContext(ThriftParser.FieldContext node) {
        FormatterUtil.listSeparatorInline.process(this, node);
    }

    protected void Function_Context(ThriftParser.Function_Context node) {
        FormatterUtil.tupleTightInline.process(this, node);
    }

    protected void OnewayContext(ThriftParser.OnewayContext node) {
        FormatterUtil.genInlineContext(" ", null).process(this, node);
    }

    protected void Function_typeContext(ThriftParser.Function_typeContext node) {
        FormatterUtil.genInlineContext(" ", null).process(this, node);
    }

    protected void Throws_listContext(ThriftParser.Throws_listContext node) {
        FormatterUtil.tupleTightInline.process(this, node);
    }

    protected void Type_annotationsContext(ThriftParser.Type_annotationsContext node) {
        FormatterUtil.tupleTightInline.process(this, node);
    }

    protected void Type_annotationContext(ThriftParser.Type_annotationContext node) {
        FormatterUtil.tupleTightInline.process(this, node);
    }

    protected void Annotation_valueContext(ThriftParser.Annotation_valueContext node) {
        FormatterUtil.genInlineContext(" ", null).process(this, node);
    }

    protected void ServiceContext(ThriftParser.ServiceContext node) {
        if (FormatterUtil.isToken(node.getChild(2), "extends")) {
            FormatterUtil.genSubblocksContext(5, ThriftParser.Function_Context.class).process(this, node);
        } else {
            FormatterUtil.genSubblocksContext(3, ThriftParser.Function_Context.class).process(this, node);
        }
    }

    protected void SenumContext(ThriftParser.SenumContext node) {
    }
}
