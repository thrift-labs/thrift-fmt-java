package thriftlabs.thriftfmt.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
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
import thriftlabs.thriftfmt.ThriftFormatter;
import thriftlabs.thriftfmt.Option;

public class ThriftFormatterTest {

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
        assertTrue(result.isSuccess());

        var formatter = new ThriftFormatter(result);
        formatter.setOption(new Option(4, true, true, true, false, false));
        formatter.format();

        var option = new Option(4, true, true, true, true, false);
        var formatter2 = new ThriftFormatter(result, option);
        formatter2.format();
    }

    @Test
    public void testWithComplexThrift() {
        String origin = "struct A {\n" +
                "   1: i64 n,\n" +
                "   2: string text\n" +
                "   3: boolean flag = true\n" +
                "}";
        var result = Thrift.parse(origin);
        assertTrue(result.isSuccess());
        var opt = new Option();
        var formatter = new ThriftFormatter(result, opt);
        var newContent = formatter.format();
        var expect = "struct A {\n" +
                "    1: required i64 n,\n" +
                "    2: required string text,\n" +
                "    3: required boolean flag = true,\n" +
                "}";
        assertEquals(newContent, expect);
    }

    @Test
    public void testWithAlignThrift() {
        String origin = "struct A {\n" +
                "   1: i64 n,\n" +
                "   2: string text = \"hello\"\n" +
                "   3: boolean flag_value = true\n" +
                "}";
        var result = Thrift.parse(origin);
        assertTrue(result.isSuccess());
        var opt = new Option(4, true, true, true, true, false);
        var formatter = new ThriftFormatter(result, opt);
        var newContent = formatter.format();
        var expect = "struct A {\n" +
                "    1: required i64 n,\n" +
                "    2: required string text        = \"hello\",\n" +
                "    3: required boolean flag_value = true,\n" +
                "}";
        assertEquals(expect, newContent);
    }

    @Test
    public void testWithAlignFieldThrift() {
        String origin = "struct A {\n" +
                "   1: i64 n,\n" +
                "   2: string text = \"hello\"\n" +
                "   3: boolean flag_value = true\n" +
                "}";
        var result = Thrift.parse(origin);
        assertTrue(result.isSuccess());
        var opt = new Option(4, true, true, true, false, true);
        var formatter = new ThriftFormatter(result, opt);
        var newContent = formatter.format();
        var expect = "struct A {\n" +
                "    1: required i64     n                   ,\n" +
                "    2: required string  text       = \"hello\",\n" +
                "    3: required boolean flag_value = true   ,\n" +
                "}";
        assertEquals(expect, newContent);
    }
}
