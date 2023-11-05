/*
 * @test /nodynamiccopyright/
 * @bug 8172880
 * @summary  Wrong LineNumberTable for synthetic null checks
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 */

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;

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
                new SimpleEntry<>(29, 0),
                new SimpleEntry<>(30, 4),
                new SimpleEntry<>(32, 9),
                new SimpleEntry<>(33, 16),
                new SimpleEntry<>(34, 32),
                new SimpleEntry<>(35, 46),
                new SimpleEntry<>(36, 52)
        );
        if (!Objects.equals(actualEntries, expectedEntries)) {
            error(String.format("Unexpected LineNumberTable: %s", actualEntries.toString()));
        }

        try {
            new Test();
        } catch (NullPointerException npe) {
            if (Arrays.stream(npe.getStackTrace())
                      .noneMatch(se -> se.getFileName().contains("NullCheckLineNumberTest") &&
                                       se.getLineNumber() == 34)) {
                throw new AssertionError("Should go through line 34!");
            }
        }
    }

    static List<Entry> findEntries() throws IOException {
        ClassModel self = Classfile.of().parse(Objects.requireNonNull(Test.class.getResourceAsStream("NullCheckLineNumberTest$Test.class")).readAllBytes());
        for (MethodModel m : self.methods()) {
            if ("<init>".equals(m.methodName().stringValue())) {
                CodeAttribute code_attribute = m.findAttribute(Attributes.CODE).orElseThrow();
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
