/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import jdk.internal.classfile.impl.BoundAttribute;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

/**
 * Base class for line number table attribute tests.
 * To add new tests cases(e.g. for new language constructions) you should modify TestData in LineNumberTest.
 * If you plan to add new tests you should extends LineNumberTestBase and invoke one of two "test(...)" methods.
 *
 * @see #test(Container) test methods for more info.
 */
public class LineNumberTestBase extends TestBase {
    /**
     * Generates test cases and passes to {@link #test(java.util.List)}
     * Generation: Replaces placeholder in template by value of enum {@link Constructions}.
     */
    protected void test(Container container) throws Exception {
        test(container.generate(Constructions.values()));
    }

    /**
     * Takes list of test cases. Compiles source of test case.
     * Checks what expected lines are covered by line number table.
     * Does general check of line number table for consistency.
     *
     * @param testCases list of test cases.
     */
    protected void test(List<TestCase> testCases) throws Exception {
        boolean failed = false;
        for (TestCase testCase : testCases) {
            try {
                writeToFileIfEnabled(Paths.get(testCase.getName() + ".java"), testCase.src);
                Set<Integer> coveredLines = new HashSet<>();
                for (JavaFileObject file : compile(testCase.extraCompilerOptions, testCase.src).getClasses().values()) {
                    ClassModel classFile;
                    try (InputStream input = file.openInputStream()) {
                        classFile = ClassFile.of().parse(input.readAllBytes());
                    }
                    for (MethodModel m : classFile.methods()) {
                        CodeAttribute code_attribute = m.findAttribute(Attributes.code()).orElse(null);

                        assert code_attribute != null;
                        assertEquals(
                                countAttributes(Attributes.lineNumberTable(), code_attribute),
                                1,
                                "Can be more than one LNT attribute, but javac should generate only one.");

                        LineNumberTableAttribute tableAttribute = code_attribute.findAttribute(Attributes.lineNumberTable()).orElse(null);
                        assert tableAttribute != null;
                        checkAttribute(testCase, tableAttribute, code_attribute.codeLength());
                        Set<Integer> methodCoveredLines =
                                tableAttribute.lineNumbers().stream()
                                        .map(LineNumberInfo::lineNumber)
                                        .collect(toSet());

                        TestCase.MethodData expected = testCase.findData(m.methodName().stringValue());

                        if (expected != null) {
                            verifyCoveredLines(methodCoveredLines, expected);
                        }

                        coveredLines.addAll(methodCoveredLines);
                    }
                }

                TestCase.MethodData expected = testCase.findData(null);

                if (expected != null) {
                    verifyCoveredLines(coveredLines, expected);
                }
            } catch (AssertionFailedException | CompilationException ex) {
                System.err.printf("#       %-20s#%n", testCase.getName());
                int l = 0;
                for (String line : testCase.src.split("\n")) {
                    System.err.println(++l + line);
                }
                System.err.println(ex);
                failed = true;
                continue;
            }
            System.err.printf("#       %-20s#%n", testCase.getName());
            System.err.println("Passed");
        }
        if (failed) {
            throw new RuntimeException("Test failed");
        }
    }

    private void verifyCoveredLines(Set<Integer> actualCoveredLines, TestCase.MethodData expected) {
        if (expected.exactLines()) {
            assertTrue(actualCoveredLines.equals(expected.expectedLines()),
                    format("Incorrect covered lines.%n" +
                            "Covered: %s%n" +
                            "Expected: %s%n", actualCoveredLines, expected.expectedLines()));
        } else {
            assertTrue(actualCoveredLines.containsAll(expected.expectedLines()),
                    format("All significant lines are not covered.%n" +
                            "Covered: %s%n" +
                            "Expected: %s%n", actualCoveredLines, expected.expectedLines()));
        }
    }

    private <T extends Attribute<T>> int countAttributes(AttributeMapper<T> attr, AttributedElement attributedElement) {
        int i = 0;
        for (Attribute<?> attribute : attributedElement.attributes()) {
            if (attribute.attributeName().equals(attr.name())) {
                i++;
            }
        }
        return i;
    }

    private void checkAttribute(TestCase testCase, LineNumberTableAttribute tableAttribute, int code_length) {
        // This test is unnecessary
//        assertEquals(tableAttribute.line_number_table_length, tableAttribute.line_number_table.length,
//                "Incorrect line number table length.");
        //attribute length is offset(line_number_table_length) + element_size*element_count
        assertEquals(((BoundAttribute<?>)tableAttribute).payloadLen(), 2 + 4 * tableAttribute.lineNumbers().size(),
                "Incorrect attribute length");
        testNonEmptyLine(testCase.src.split("\n"), tableAttribute);
        assertEquals(
                tableAttribute.lineNumbers().stream()
                        .filter(e -> e.startPc() >= code_length)
                        .count()
                , 0L, "StartPC is out of bounds.");
    }

    /**
     * Expects line number table point to non empty lines.
     * The method can't recognize commented lines as empty(insensible) in case of multiline comment.
     */
    private void testNonEmptyLine(String[] source, LineNumberTableAttribute attribute) {
        for (LineNumberInfo e : attribute.lineNumbers()) {
            String line = source[e.lineNumber() - 1].trim();
            assertTrue(!("".equals(line) || line.startsWith("//") || line.startsWith("/*")),
                    format("Expect that line #%d is not empty.%n", e.lineNumber()));
        }
    }

    protected static enum Constructions implements Container.Construction {
        STORE("testField = 10;"),
        LOAD("int p;\n" +
                "p = testField;", 2),
        ASSERT("assert false: \"Assert error\";"),
        ARRAY("double arr[] = new double[10];"),
        ARRAY2("int arr2[][] = {{1,2},{}};"),
        LAMBDA("Runnable runnable = () -> \n" +
                "   System.out.println();"),
        LAMBDA_BODY("Runnable runnable = () -> {\n" +
                "   testField++;\n" +
                "};"),
        METHOD_REFERENCE("Runnable run = System.out::println;\nrun.run();"),
        INVOKE_STATIC_METHOD("System.out.println(\"\");"),
        INVOKE_INTERFACE("Runnable runnable = new Runnable() {\n" +
                "    @Override\n" +
                "    public void run() {\n" +
                "        System.out.println(\"runnable\");\n" +
                "    }\n" +
                "};\n" +
                "runnable.run();", 1, 7),
        INVOKE_VIRTUAL_METHOD("testMethod();"),
        INVOKE_CONSTRUCTOR("new Integer(2);"),
        INVOKE_LAMBDA(LAMBDA.getSource() + "\n" +
                "runnable.run();"),
        DO_WHILE("do{\n" +
                "    testField++;\n" +
                "}while(testField == 1);", 2, 3),
        WHILE("while(testField == 1);"),
        FOR("for(int i = 0; i < 3 ; i++);"),
        FOR_ENHANCEMENT("int[] ints = {1,2,3};\n" +
                "for(int i:  ints);"),
        LABEL("int i=0;\n" +
                "label:{\n" +
                "    label2:\n" +
                "    for(;i<5;i++){\n" +
                "        if(i==3)\n" +
                "            break label;\n" +
                "        if(i==0){\n" +
                "            continue label2;\n" +
                "        }\n" +
                "        return;\n" +
                "    }\n" +
                "    i++;\n" +
                "}\n"
                , 1, 4, 5, 6, 7, 8, 10, 12),
        CONDITION("int res = \n" +
                "testField == 2 ?\n" +
                "10\n" +
                ":9;", 2, 3, 4),
        TRY("try{\n" +
                "    --testField;\n" +
                "}\n" +
                "catch(Exception e){\n" +
                "    --testField;\n" +
                "}\n" +
                "catch(Error e){\n" +
                "    System.out.print(e);\n" +
                "    throw e;\n " +
                "}\n" +
                "finally{\n" +
                "    ++testField;\n" +
                "}", 2, 4, 5, 7, 8, 9, 12),
        TRY_WITH_RESOURCES("try (\n" +
                "    Writer writer = new StringWriter();\n" +
                "    Reader reader = new StringReader(\"\")) {\n" +
                "        writer.write(1);\n" +
                "        reader.read();\n" +
                "} catch (IOException e) {}\n"
                , 2, 3, 4, 5),
        SYNCHRONIZE("" +
                "synchronized(this){\n" +
                "    testField++;\n" +
                "}"),
        SWITCH("switch (testField){\n" +
                "case 1:\n" +
                "    break;\n" +
                "case 2:\n" +
                "    testField++;\n" +
                "default: \n" +
                "    testField+=2; \n" +
                "}", 1, 3, 5, 7),
        SWITCH_STRING(
                "String str = String.valueOf(testField);\n" +
                        "switch (str){\n" +
                        "case \"1\":\n" +
                        "    break;\n" +
                        "case \"2\":\n" +
                        "    testField++;\n" +
                        "default: \n" +
                        "    testField+=2; \n" +
                        "}", 1, 2, 4, 6, 8);

        private final String source;
        private int[] expectedLines;

        Constructions(String source) {
            this.source = source;
            expectedLines = IntStream.rangeClosed(1, source.split("\n").length).toArray();
        }

        Constructions(String source, int... expectedLines) {
            this.source = source;
            this.expectedLines = expectedLines;
        }

        @Override
        public String getSource() {
            return source;
        }

        @Override
        public int[] getExpectedLines() {
            return expectedLines;
        }
    }

    protected static class MainContainer extends Container {

        public MainContainer() {
            super("import java.io.*;\n" +
                    "public class Main{\n" +
                    "    public int testField;\n" +
                    "\n" +
                    "    public void testMethod() {\n" +
                    "        #SUB_TEMPLATE\n" +
                    "    }\n" +
                    "}");
        }
    }

    protected static class LocalClassContainer extends Container {

        public LocalClassContainer() {

            super("class Local#LEVEL{\n" +
                    "    public void m(){\n" +
                    "        #SUB_TEMPLATE\n" +
                    "        return;\n" +
                    "    }" +
                    "}");
        }
    }

    protected static class LambdaContainer extends Container {

        public LambdaContainer() {
            super("Runnable lambda#LEVEL = () -> {\n" +
                    "    #SUB_TEMPLATE\n" +
                    "};\n" +
                    "lambda#LEVEL.run();\n");
        }
    }
}
