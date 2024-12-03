package thriftlabs.thriftfmt;

import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import thriftlabs.thriftparser.Thrift;
import thriftlabs.thriftparser.ThriftParser;

public class ThriftFormatter extends PureThriftFormatter {

    private Thrift.ParserResult data;
    private ThriftParser.DocumentContext document;
    private int lastTokenIndex = -1;
    private int fieldCommentPadding = 0;
    private int fieldAlignByAssignPadding = 0;
    private Map<String, Integer> fieldAlignByFieldPaddingMap;

    public ThriftFormatter(Thrift.ParserResult data) {
        this.data = data;
        this.document = data.document;
        this.fieldAlignByFieldPaddingMap = new HashMap<>();
    }

    public String format() {
        patch();
        return formatNode(document);
    }

    private void patch() {
        if (this.option.patchRequired) {
            FormatterUtil.walkNode(this.document, node -> this.patchFieldRequired(node));
        }
        if (this.option.patchSeparator) {
            FormatterUtil.walkNode(this.document, node -> this.patchFieldListSeparator(node));
            FormatterUtil.walkNode(this.document, node -> this.patchRemoveLastListSeparator(node));
        }
    }

    private void patchFieldRequired(ParseTree node) {
        // 检查是否是 FieldContext 的实例
        if (!(node instanceof ThriftParser.FieldContext)) {
            return;
        }
        ThriftParser.FieldContext field = (ThriftParser.FieldContext) node;

        // 检查父节点是否为 undefined 或者是 FunctionOrThrowsListNode 的实例
        if (field.getParent() == null || FormatterUtil.isFunctionOrThrowsListNode(field.getParent())) {
            return;
        }

        int i;
        // 遍历子节点
        for (i = 0; i < field.getChildCount(); i++) {
            ParseTree child = field.getChild(i);
            if (child instanceof ThriftParser.Field_reqContext) {
                return; // 如果已经有 Field_reqContext，返回
            }
            if (child instanceof ThriftParser.Field_typeContext) {
                break; // 找到 Field_typeContext，停止循环
            }
        }

        // 创建伪节点
        CommonToken fakeToken = new CommonToken(ThriftParser.T__20, "required");
        fakeToken.setLine(FormatterUtil.FAKE_NODE_LINE_NO);
        fakeToken.setCharPositionInLine(FormatterUtil.FAKE_NODE_LINE_NO);
        fakeToken.setTokenIndex(-1);
        TerminalNode fakeNode = new TerminalNodeImpl(fakeToken);
        ThriftParser.Field_reqContext fakeReq = new ThriftParser.Field_reqContext(field, 0);

        fakeNode.setParent(fakeReq);
        fakeReq.addChild(fakeNode);
        fakeReq.setParent(field);

        // 在子节点的指定位置插入 fakeReq
        field.children.add(i, fakeReq);
    }

    private void patchFieldListSeparator(ParseTree node) {
        if (!(node instanceof ThriftParser.Enum_fieldContext ||
                node instanceof ThriftParser.FieldContext ||
                node instanceof ThriftParser.Function_Context)) {
            return;
        }

        ParseTree child = node.getChild(node.getChildCount() - 1);
        if (child instanceof ThriftParser.List_separatorContext) {
            TerminalNodeImpl comma = (TerminalNodeImpl) child.getChild(0);
            CommonToken token = (CommonToken) comma.getSymbol();
            token.setText(",");
            return;
        }

        CommonToken fakeToken = new CommonToken(ThriftParser.COMMA, ",");
        fakeToken.setLine(FormatterUtil.FAKE_NODE_LINE_NO);
        fakeToken.setCharPositionInLine(FormatterUtil.FAKE_NODE_LINE_NO);
        fakeToken.setTokenIndex(-1);

        TerminalNode fakeNode = new TerminalNodeImpl(fakeToken);
        ParserRuleContext currentNode = (ParserRuleContext) node;
        ThriftParser.List_separatorContext fakeCtx = new ThriftParser.List_separatorContext(currentNode, 0);

        fakeNode.setParent(fakeCtx);
        fakeCtx.addChild(fakeNode);

        fakeCtx.setParent(currentNode);
        currentNode.addChild(fakeCtx);
    }

    private void patchRemoveLastListSeparator(ParseTree node) {
        boolean isInlineField = node instanceof ThriftParser.FieldContext &&
                node.getParent() != null &&
                FormatterUtil.isFunctionOrThrowsListNode(node.getParent());
        boolean isInlineNode = node instanceof ThriftParser.Type_annotationContext;
        if (!(isInlineField || isInlineNode)) {
            return;
        }

        if (node.getParent() == null) {
            return;
        }
        ParserRuleContext currentNode = (ParserRuleContext) node;

        boolean last = false;
        List<ParseTree> brothers = currentNode.getParent().children != null ? currentNode.getParent().children
                : new ArrayList<>();
        int brotherCount = node.getParent().getChildCount();

        for (int i = 0; i < brotherCount; i++) {
            if (brothers.get(i) == node) {
                if (i == brotherCount - 1 || !FormatterUtil.notSameClass(node, brothers.get(i + 1))) {
                    last = true;
                    break;
                }
            }
        }

        if (last) {
            ParseTree child = node.getChild(node.getChildCount() - 1);
            if (child instanceof ThriftParser.List_separatorContext) {
                currentNode.removeLastChild();
            }
        }
    }

    private int calcAddIndentPadding(int padding) {
        if (padding > 0) {
            padding += this.option.indent;
        }
        return padding;
    }

    protected void beforeSubblocks(List<ParseTree> subblocks) {
        if (this.option.alignByField) {
            Pair<Map<String, Integer>, Integer> result = FormatterUtil.calcFieldAlignByFieldPaddingMap(subblocks);
            Map<String, Integer> paddingMap = result.a;
            Integer commentPadding = result.b;

            paddingMap.forEach((key, value) -> paddingMap.put(key, this.calcAddIndentPadding(value)));
            this.fieldAlignByFieldPaddingMap = paddingMap;
            this.fieldCommentPadding = this.calcAddIndentPadding(commentPadding);
        } else if (this.option.alignByAssign) {
            int[] result = FormatterUtil.calcFieldAlignByAssignPadding(subblocks);
            int alignPadding = result[0];
            int commentPadding = result[1];
            this.fieldAlignByAssignPadding = this.calcAddIndentPadding(alignPadding);
            this.fieldCommentPadding = this.calcAddIndentPadding(commentPadding);
        }

        if (this.option.keepComment && this.fieldCommentPadding == 0) {
            int commentPadding = FormatterUtil.calcSubBlocksCommentPadding(subblocks);
            this.fieldCommentPadding = this.calcAddIndentPadding(commentPadding);
        }
    }

    protected void afterSubblocks(List<ParseTree> subblocks) {
        this.fieldAlignByAssignPadding = 0;
        this.fieldAlignByFieldPaddingMap = new HashMap<>();
        this.fieldCommentPadding = 0;
    }

    protected void afterBlockNode(ParseTree n) {
        this.addTailComment();
    }

    protected void beforeProcessNode(ParseTree n) {
        this.addAlignPadding(n);
    }

    private void addAlignPadding(ParseTree n) {
        if (!FormatterUtil.isFieldOrEnumField(n.getParent())) {
            return;
        }

        if (this.option.alignByField && !this.fieldAlignByFieldPaddingMap.isEmpty()) {
            String name = FormatterUtil.getFieldChildName(n);
            Integer padding = this.fieldAlignByFieldPaddingMap.get(name);
            if (padding != null && padding > 0) {
                this.padding(padding);
            }
            return;
        }

        if (this.option.alignByAssign && FormatterUtil.isToken(n, "=")) {
            this.padding(this.fieldAlignByAssignPadding);
            return;
        }
    }

    private void padding(int padding) {
        padding(padding, " ");
    }

    private void padding(int padding, String pad) {
        if (padding > 0) {
            padding = padding - this.getCurrentLine().length();
            if (padding > 0) {
                this.appendCurrentLine(String.join("", Collections.nCopies(padding, pad)));
            }
        }
    }

    protected String getCurrentLine() {
        if (this.newlineCounter > 0) {
            return "";
        }

        String[] parts = this.out.split("\n");
        String cur = parts[parts.length - 1];
        return cur;
    }

    private void addTailComment() {
        if (!this.option.keepComment) {
            return;
        }
        if (this.lastTokenIndex == -1) {
            return;
        }

        List<Token> tokens = this.data.tokens.getTokens();
        Token lastToken = tokens.get(this.lastTokenIndex);
        List<Token> comments = new ArrayList<>();

        for (int i = this.lastTokenIndex + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.getLine() != lastToken.getLine()) {
                break;
            }
            if (token.getChannel() != Thrift.CommentChannel) {
                continue;
            }
            comments.add(token);
        }

        if (!comments.isEmpty()) {
            Token comment = comments.get(0);
            if (comment.getText() == null) {
                return;
            }

            // Align comment
            if (this.fieldCommentPadding > 0) {
                this.padding(this.fieldCommentPadding, " ");
            } else {
                this.appendCurrentLine(" ");
            }

            this.appendCurrentLine(comment.getText().trim());
            this.append("");
            this.lastTokenIndex = comment.getTokenIndex();
        }
    }

    private void addInlineComments(TerminalNode node) {
        if (!this.option.keepComment) {
            return;
        }

        if (FormatterUtil.isFakeNode(node)) {
            return;
        }

        int tokenIndex = node.getSymbol().getTokenIndex();
        List<Token> comments = new ArrayList<>();
        List<Token> tokens = this.data.tokens.getTokens();

        for (int i = this.lastTokenIndex + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.getChannel() != Thrift.CommentChannel) {
                continue;
            }
            if (token.getTokenIndex() < tokenIndex) {
                comments.add(token);
            }
        }

        for (Token token : comments) {
            if (token.getTokenIndex() > 0 && token.getType() == ThriftParser.ML_COMMENT) {
                this.newline(2);
            }
            if (token.getText() == null) {
                return;
            }

            // TODO: Confirm if clean indent is needed.
            this.pushCurrentIndent();

            String text = token.getText();
            this.append(text.trim());

            int lastLine = token.getLine() + text.split("\n").length - 1;
            int lineDiff = node.getSymbol().getLine() - lastLine;
            boolean isTight = token.getType() == ThriftParser.SL_COMMENT ||
                    FormatterUtil.isEOF(node) ||
                    (0 < lineDiff && lineDiff <= 1);

            if (isTight) {
                this.newline();
            } else {
                this.newline(2);
            }
        }

        this.lastTokenIndex = tokenIndex;
    }

    protected void TerminalNode(TerminalNode n) {
        if (this.newlineCounter > 0) {
            this.addTailComment();
        }

        this.addInlineComments(n);

        super.TerminalNode(n);
    }
}
