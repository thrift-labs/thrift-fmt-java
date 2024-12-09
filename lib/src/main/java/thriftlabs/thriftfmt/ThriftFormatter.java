package thriftlabs.thriftfmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
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

    public ThriftFormatter(Thrift.ParserResult data, Option opt) {
        this(data);
        if (opt == null) {
            throw new IllegalArgumentException("Option cannot be null.");
        }
        this.setOption(opt);
    }

    public String format() {
        patch();
        return formatNode(document);
    }

    private void patch() {
        if (this.option.isPatchRequired()) {
            Util.walkNode(this.document, node -> this.patchFieldRequired(node));
        }
        if (this.option.isPatchSeparator()) {
            Util.walkNode(this.document, node -> this.patchFieldListSeparator(node));
            Util.walkNode(this.document, node -> this.patchRemoveLastListSeparator(node));
        }
    }

    private void patchFieldRequired(ParseTree node) {
        if (!(node instanceof ThriftParser.FieldContext)) {
            return;
        }
        ThriftParser.FieldContext field = (ThriftParser.FieldContext) node;
        if (field.getParent() == null || Util.isFunctionOrThrowsListNode(field.getParent())) {
            return;
        }

        int i;
        for (i = 0; i < field.getChildCount(); i++) {
            ParseTree child = field.getChild(i);
            if (child instanceof ThriftParser.Field_reqContext) {
                return;
            }
            if (child instanceof ThriftParser.Field_typeContext) {
                break;
            }
        }

        TerminalNode fakeNode = Util.createFakeNode(ThriftParser.T__20, "required");
        ThriftParser.Field_reqContext fakeReq = new ThriftParser.Field_reqContext(field, 0);

        fakeNode.setParent(fakeReq);
        fakeReq.addChild(fakeNode);
        fakeReq.setParent(field);

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
            token.setText(Option.DEFAULT_SEPARATOR);
            return;
        }

        ParserRuleContext currentNode = (ParserRuleContext) node;
        TerminalNode fakeNode = Util.createFakeNode(ThriftParser.COMMA, Option.DEFAULT_SEPARATOR);
        ThriftParser.List_separatorContext fakeCtx = new ThriftParser.List_separatorContext(currentNode, 0);

        fakeNode.setParent(fakeCtx);
        fakeCtx.addChild(fakeNode);

        fakeCtx.setParent(currentNode);
        currentNode.addChild(fakeCtx);
    }

    private void patchRemoveLastListSeparator(ParseTree node) {
        boolean isInlineField = node instanceof ThriftParser.FieldContext &&
                node.getParent() != null &&
                Util.isFunctionOrThrowsListNode(node.getParent());
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
                if (i == brotherCount - 1 || !Util.notSameClass(node, brothers.get(i + 1))) {
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
            padding += this.option.getIndent();
        }
        return padding;
    }

    protected void beforeSubblocks(List<ParseTree> subblocks) {
        if (this.option.isAlignByField()) {
            Pair<Map<String, Integer>, Integer> result = Util.calcFieldAlignByFieldPaddingMap(subblocks);
            Map<String, Integer> paddingMap = result.a;
            Integer commentPadding = result.b;

            paddingMap.forEach((key, value) -> paddingMap.put(key, this.calcAddIndentPadding(value)));
            this.fieldAlignByFieldPaddingMap = paddingMap;
            this.fieldCommentPadding = this.calcAddIndentPadding(commentPadding);
        } else if (this.option.isAlignByAssign()) {
            Pair<Integer, Integer> result = Util.calcFieldAlignByAssignPadding(subblocks);
            int alignPadding = result.a;
            int commentPadding = result.b;
            this.fieldAlignByAssignPadding = this.calcAddIndentPadding(alignPadding);
            this.fieldCommentPadding = this.calcAddIndentPadding(commentPadding);
        }

        if (this.option.isKeepComment() && this.fieldCommentPadding == 0) {
            int commentPadding = Util.calcSubBlocksCommentPadding(subblocks);
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
        if (!Util.isFieldOrEnumField(n.getParent())) {
            return;
        }

        if (this.option.isAlignByField() && !this.fieldAlignByFieldPaddingMap.isEmpty()) {
            String name = Util.getFieldChildName(n);
            Integer padding = this.fieldAlignByFieldPaddingMap.get(name);
            if (padding != null && padding > 0) {
                this.padding(padding);
            }
            return;
        }

        if (this.option.isAlignByAssign() && Util.isToken(n, "=")) {
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

        int index = this.out.lastIndexOf("\n");
        if (index == -1) {
            return this.out;
        }
        return this.out.substring(index + 1);
    }

    private void addTailComment() {
        if (!this.option.isKeepComment()) {
            return;
        }
        if (this.lastTokenIndex == -1) {
            return;
        }

        List<Token> tokens = this.data.tokens.getTokens();
        Token lastToken = tokens.get(this.lastTokenIndex);
        List<Token> comments = tokens.stream()
                .skip(this.lastTokenIndex + 1)
                .takeWhile(token -> token.getLine() == lastToken.getLine())
                .filter(token -> token.getChannel() == Thrift.CommentChannel)
                .collect(Collectors.toList());

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
        if (!this.option.isKeepComment()) {
            return;
        }

        if (Util.isFakeNode(node)) {
            return;
        }

        int tokenIndex = node.getSymbol().getTokenIndex();
        List<Token> tokens = this.data.tokens.getTokens();
        List<Token> comments = tokens.stream()
                .skip(this.lastTokenIndex + 1)
                .filter(token -> token.getChannel() == Thrift.CommentChannel)
                .filter(token -> token.getTokenIndex() < tokenIndex)
                .collect(Collectors.toList());

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
                    Util.isEOF(node) ||
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
