package thriftlabs.thriftfmt;

import org.junit.Test;

import thriftlabs.thriftparser.Thrift;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

public class ThriftFormatterTest extends PureThriftFormatterTest {
    @Test
    public void someLibraryMethodReturnsTrue() {
        String directory = "src/test/resources/thrifts"; // 资源目录
        try {
            List<String> thriftFiles = findThriftFiles(directory);
            thriftFiles.forEach(path -> testFormatterFileWithFormatter(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void testFormatterFileWithFormatter(String fileName) {
        String content = readResourceFile("thrifts/" + fileName);
        assertNotNull("Fixture file should be found", content);

        Thrift.ParserResult result = Thrift.parse(content);
        var formatter = new ThriftFormatter(result);
        formatter.format();
    }
}
