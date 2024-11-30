package thriftlabs.thriftfmt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import thriftlabs.thriftparser.Thrift;

public class PureThriftFormatterTest {
    @Test
    public void TestSimpleFormatter() {
        var thrift = "include        \"shared.thrift\"";
        Thrift.ParserResult result = Thrift.parse(thrift);
        var formatter = new PureThriftFormatter();
        var content = formatter.formatNode(result.document);
        assertEquals("include \"shared.thrift\"", content);
    }
}
