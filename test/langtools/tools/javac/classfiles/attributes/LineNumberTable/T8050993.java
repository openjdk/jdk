/*
 * @test /nodynamiccopyright/
 * @bug 8050993
 * @summary Verify that the condition in the conditional lexpression gets a LineNumberTable entry
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 * @compile -g T8050993.java
 * @run main T8050993
 */

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

public class T8050993 {
    public static void main(String[] args) throws IOException {
        ClassModel someTestIn = ClassFile.of().parse(T8050993.class.getResourceAsStream("T8050993.class").readAllBytes());
        Set<Integer> expectedLineNumbers = new HashSet<>(Arrays.asList(49, 50, 47, 48));
        for (MethodModel m : someTestIn.methods()) {
            if (m.methodName().equalsString("method")) {
                CodeAttribute code_attribute = m.findAttribute(Attributes.code()).orElse(null);
                assert code_attribute != null;
                for (Attribute<?> at : code_attribute.attributes()) {
                    if (Attributes.lineNumberTable().equals(at)) {
                        assert at instanceof LineNumberTableAttribute;
                        LineNumberTableAttribute att = (LineNumberTableAttribute) at;
                        Set<Integer> actualLinesNumbers = Arrays.stream(att.lineNumbers().toArray(new LineNumberInfo[0]))
                                .map(LineNumberInfo::lineNumber)
                                .collect(Collectors.toSet());
                        if (!Objects.equals(expectedLineNumbers, actualLinesNumbers)) {
                            throw new AssertionError("Expected LineNumber entries not found;" +
                                                     "actual=" + actualLinesNumbers + ";" +
                                                     "expected=" + expectedLineNumbers);
                        }
                    }
                }
            }
        }
    }

    public static int field;

    public static String method() {
        String s =
                field % 2 == 0 ?
                "true" + field :
                "false" + field;
        return s;
    }

}
