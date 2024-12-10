package thriftlabs.thriftfmt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

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

    @SuppressWarnings("resource")
    public String readResourceFile(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("File not found: " + fileName);
            }
            return new Scanner(inputStream, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Test
    public void testFindThriftFiles() {
        String directory = "src/test/resources/thrifts"; // 资源目录
        try {
            List<String> thriftFiles = findThriftFiles(directory);
            thriftFiles.forEach(path -> testFormatterFile(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> findThriftFiles(String directory) throws IOException {
        Path dirPath = Paths.get(directory);
        return Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".thrift"))
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toList());
    }

    public void testFormatterFile(String fileName) {
        String content = readResourceFile("thrifts/" + fileName);
        assertNotNull("Fixture file should be found", content);

        Thrift.ParserResult result = Thrift.parse(content);
        var formatter = new PureThriftFormatter();
        formatter.formatNode(result.document);
    }

    @Test
    public void TestConstList() {
        var thrift = "struct OptionalSetDefaultTest {\n" + //
                "    1: optional set<string> with_default = [ \"test\", \"hello\", ],\n" + //
                "}";
        Thrift.ParserResult result = Thrift.parse(thrift);
        var formatter = new PureThriftFormatter();
        var content = formatter.formatNode(result.document);
        var expect = "struct OptionalSetDefaultTest {\n" + //
                "    1: optional set<string> with_default = [ \"test\", \"hello\", ],\n" + //
                "}";
        assertEquals(expect, content);
    }
}
