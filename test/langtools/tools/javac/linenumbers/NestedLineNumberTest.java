/*
 * @test /nodynamiccopyright/
 * @bug 8061778
 * @summary  Wrong LineNumberTable for default constructors
 * @enablePreview
 */

import java.util.List;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

import java.io.IOException;

public class NestedLineNumberTest {

    public static void main(String[] args) throws Exception {
        List<LineNumberInfo> lines = findEntries();
        if (lines == null || lines.size() != 1) {
            int found = lines == null ? 0 : lines.size();
            error(String.format("LineNumberTable contains wrong number of entries - expected %d, found %d", 1, found));
        }

        int line = lines.get(0).lineNumber();
        if (line != 50) {
            error(String.format("LineNumberTable contains wrong line number - expected %d, found %d", 50, line));
        }
    }

    static List<LineNumberInfo> findEntries() throws IOException {
        ClassModel self = ClassFile.of().parse(NestedLineNumberTest.Test.class.getResourceAsStream("NestedLineNumberTest$Test.class").readAllBytes());
        for (MethodModel m : self.methods()) {
            if ("<init>".equals(m.methodName().stringValue())) {
                CodeAttribute code_attribute = m.findAttribute(Attributes.CODE).orElseThrow();
                for (Attribute<?> at : code_attribute.attributes()) {
                    if (at instanceof LineNumberTableAttribute lineAt) {
                        return lineAt.lineNumbers();
                    }
                }
            }
        }
        return null;
    }

    static void error(String msg) {
        throw new AssertionError(msg);
    }

    // The default constructor in this class should get only one LineNumberTable entry,
    // pointing to the first line of the declaration of class Test.
    static class Test {
        static class Empty { }
    }
}
