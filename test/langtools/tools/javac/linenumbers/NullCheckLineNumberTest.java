/*
 * @test /nodynamiccopyright/
 * @bug 8172880
 * @summary  Wrong LineNumberTable for synthetic null checks
 * @enablePreview
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NullCheckLineNumberTest {

    //test data:
    static class Test {

        public Test() {
            String a = "", b = null;

            Stream.of("x")
                  .filter(a::equals)
                  .filter(b::equals)
                  .count();
        }

    }

    public static void main(String[] args) throws Exception {
        List<Entry> actualEntries = findEntries();
        List<Entry> expectedEntries = List.of(
                new SimpleEntry<>(25, 0),
                new SimpleEntry<>(26, 4),
                new SimpleEntry<>(28, 9),
                new SimpleEntry<>(29, 16),
                new SimpleEntry<>(30, 32),
                new SimpleEntry<>(31, 46),
                new SimpleEntry<>(32, 52)
        );
        if (!Objects.equals(actualEntries, expectedEntries)) {
            error(String.format("Unexpected LineNumberTable: %s", actualEntries.toString()));
        }

        try {
            new Test();
        } catch (NullPointerException npe) {
            if (Arrays.stream(npe.getStackTrace())
                      .noneMatch(se -> se.getFileName().contains("NullCheckLineNumberTest") &&
                                       se.getLineNumber() == 30)) {
                throw new AssertionError("Should go through line 30!");
            }
        }
    }

    static List<Entry> findEntries() throws IOException {
        ClassModel self = ClassFile.of().parse(Objects.requireNonNull(Test.class.getResourceAsStream("NullCheckLineNumberTest$Test.class")).readAllBytes());
        for (MethodModel m : self.methods()) {
            if ("<init>".equals(m.methodName().stringValue())) {
                CodeAttribute code_attribute = m.findAttribute(Attributes.code()).orElseThrow();
                for (Attribute<?> at : code_attribute.attributes()) {
                    if (at instanceof LineNumberTableAttribute lineAt) {
                        return lineAt.lineNumbers().stream()
                                     .map(e -> new SimpleEntry<> (e.lineNumber(), e.startPc()))
                                     .collect(Collectors.toList());
                    }
                }
            }
        }
        return null;
    }

    static void error(String msg) {
        throw new AssertionError(msg);
    }

}
