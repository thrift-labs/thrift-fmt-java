package thriftlabs.thriftfmt;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
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
    private int fieldAlignAssignPadding = 0;
    private Map<String, Integer> fieldAlignPaddingMap;

    public ThriftFormatter(Thrift.ParserResult data) {
        this.data = data;
        this.document = data.document;
        this.fieldAlignPaddingMap = new HashMap<>();
    }

    public String format() {
        patch();
        return formatNode(document);
    }

    private void patch() {
        FormatterUtil.walkNode(this.document, node -> this.patchFieldRequired(node));
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
}
