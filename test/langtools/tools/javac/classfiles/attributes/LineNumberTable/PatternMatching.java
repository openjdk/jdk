/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8387965
 * @summary Tests a line number table attribute for pattern matching
 * @library /tools/lib /tools/javac/lib ../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox InMemoryFileManager TestBase
 * @build LineNumberTestBase TestCase
 * @run main PatternMatching
 */

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.LineNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import toolbox.ToolBox;

public class PatternMatching extends LineNumberTestBase {
    static ToolBox tb = new ToolBox();

    public static void main(String[] args) throws Exception {
        new PatternMatching().test();
    }

    public void test() throws Exception {
        test(List.of(TEST_CASE));
    }

    private static final TestCase[] TEST_CASE = new TestCase[] {
        new TestCase("""
                     public class PatternMatching {                        // 1
                         private void test(String s) {                     // 2
                             Object obj = "abc";                           // 3
                             boolean isLong = obj instanceof String str    // 4
                                &&                                         // 5
                                true;                                      // 6
                         }                                                 // 7
                     }                                                     // 8
                     """,
                     "PatternMatching",
                     new TestCase.MethodData("test", List.of(3, 4, 7), true, new DetailedValidator(
                         "line: 3",
                         "LDC",
                         "ASTORE_2",
                         "line: 4",
                         "ALOAD_2",
                         "INSTANCEOF",
                         "IFEQ",
                         "ALOAD_2",
                         "CHECKCAST",
                         "ASTORE",
                         "ICONST_1",
                         "GOTO",
                         "ICONST_0",
                         "ISTORE_3",
                         "line: 7",
                         "RETURN"))),
        new TestCase("""
                     public class PatternMatching {                        // 1
                         private void test(String s) {                     // 2
                             Object obj = "abc";                           // 3
                             boolean isLong = true                         // 4
                                &&                                         // 5
                                obj instanceof String str2;                // 6
                         }                                                 // 7
                     }                                                     // 8
                     """,
                     "PatternMatching",
                     new TestCase.MethodData("test", List.of(3, 6, 7), true, new DetailedValidator(
                         "line: 3",
                         "LDC",
                         "ASTORE_2",
                         "line: 6",
                         "ALOAD_2",
                         "INSTANCEOF",
                         "IFEQ",
                         "ALOAD_2",
                         "CHECKCAST",
                         "ASTORE",
                         "ICONST_1",
                         "GOTO",
                         "ICONST_0",
                         "ISTORE_3",
                         "line: 7",
                         "RETURN"
                     ))),
        new TestCase("""
                     public class PatternMatching {                        // 1
                         private void test(String s) {                     // 2
                             Object obj = "abc";                           // 3
                             boolean isLong = obj instanceof String str1   // 4
                                &&                                         // 5
                                obj instanceof String str2;                // 6
                         }                                                 // 7
                     }                                                     // 8
                     """,
                     "PatternMatching",
                     new TestCase.MethodData("test", List.of(3, 4, 6, 7), true, new DetailedValidator(
                         "line: 3",
                         "LDC",
                         "ASTORE_2",
                         "line: 4",
                         "ALOAD_2",
                         "INSTANCEOF",
                         "IFEQ",
                         "ALOAD_2",
                         "CHECKCAST",
                         "ASTORE",
                         "line: 6",
                         "ALOAD_2",
                         "INSTANCEOF",
                         "IFEQ",
                         "ALOAD_2",
                         "CHECKCAST",
                         "ASTORE",
                         "ICONST_1",
                         "GOTO",
                         "ICONST_0",
                         "ISTORE_3",
                         "line: 7",
                         "RETURN"
                     ))),
        new TestCase("""
                     public class PatternMatching {                        // 1
                         private void test(String s) {                     // 2
                             Object obj = "abc";                           // 3
                             boolean isLong = obj instanceof String str    // 4
                                &&                                         // 5
                                check();                                   // 6
                         }                                                 // 7
                         private boolean check() { return true; }          // 8
                     }                                                     // 9
                     """,
                     "PatternMatching",
                     new TestCase.MethodData("test", List.of(3, 4, 6, 7), true, new DetailedValidator(
                         "line: 3",
                         "LDC",
                         "ASTORE_2",
                         "line: 4",
                         "ALOAD_2",
                         "INSTANCEOF",
                         "IFEQ",
                         "ALOAD_2",
                         "CHECKCAST",
                         "ASTORE",
                         "ALOAD_0",
                         "line: 6",
                         "INVOKEVIRTUAL",
                         "IFEQ",
                         "ICONST_1",
                         "GOTO",
                         "ICONST_0",
                         "ISTORE_3",
                         "line: 7",
                         "RETURN"))),
        new TestCase("""
                     public class PatternMatching {                        // 1
                         private void test(String s) {                     // 2
                             Object obj = "abc";                           // 3
                             boolean isLong = check()                      // 4
                                &&                                         // 5
                                obj instanceof String str2;                // 6
                         }                                                 // 7
                         private boolean check() { return true; }          // 8
                     }                                                     // 9
                     """,
                     "PatternMatching",
                     new TestCase.MethodData("test", List.of(3, 4, 6, 7), true, new DetailedValidator(
                        "line: 3",
                        "LDC",
                        "ASTORE_2",
                        "line: 4",
                        "ALOAD_0",
                        "INVOKEVIRTUAL",
                        "IFEQ",
                        "line: 6",
                        "ALOAD_2",
                        "INSTANCEOF",
                        "IFEQ",
                        "ALOAD_2",
                        "CHECKCAST",
                        "ASTORE",
                        "ICONST_1",
                        "GOTO",
                        "ICONST_0",
                        "ISTORE_3",
                        "line: 7",
                         "RETURN"
                     ))),
        new TestCase("""
                     public class PatternMatching {                        // 1
                         private void test(String s) {                     // 2
                             boolean isLong = obj() instanceof String str  // 3
                                &&                                         // 4
                                true;                                      // 5
                         }                                                 // 6
                         private Object obj() { return "abc"; }            // 7
                     }                                                     // 8
                     """,
                     "PatternMatching",
                     new TestCase.MethodData("test", List.of(3, 6), true, new DetailedValidator(
                         "line: 3",
                         "ALOAD_0",
                         "INVOKEVIRTUAL",
                         "ASTORE",
                         "ALOAD",
                         "INSTANCEOF",
                         "IFEQ",
                         "ALOAD",
                         "CHECKCAST",
                         "ASTORE_3",
                         "ICONST_1",
                         "GOTO",
                         "ICONST_0",
                         "ISTORE_2",
                         "line: 6",
                         "RETURN"))),
        new TestCase("""
                     public class PatternMatching {                        // 1
                         private void test(String s) {                     // 2
                             boolean isLong = true                         // 3
                                &&                                         // 4
                                obj() instanceof String str;               // 5
                         }                                                 // 6
                         private Object obj() { return "abc"; }            // 7
                     }                                                     // 8
                     """,
                     "PatternMatching",
                     new TestCase.MethodData("test", List.of(5, 6), true, new DetailedValidator(
                         "line: 5",
                         "ALOAD_0",
                         "INVOKEVIRTUAL",
                         "ASTORE",
                         "ALOAD",
                         "INSTANCEOF",
                         "IFEQ",
                         "ALOAD",
                         "CHECKCAST",
                         "ASTORE_3",
                         "ICONST_1",
                         "GOTO",
                         "ICONST_0",
                         "ISTORE_2",
                         "line: 6",
                         "RETURN"))),
        new TestCase("""
                     public class PatternMatching {                        // 1
                         private void test(String s) {                     // 2
                             boolean isLong = obj() instanceof String str1 // 3
                                &&                                         // 4
                                obj() instanceof String str2;              // 5
                         }                                                 // 6
                         private Object obj() { return "abc"; }            // 7
                     }                                                     // 8
                     """,
                     "PatternMatching",
                     new TestCase.MethodData("test", List.of(3, 5, 6), true, new DetailedValidator(
                            "line: 3",
                            "ALOAD_0",
                            "INVOKEVIRTUAL",
                            "ASTORE",
                            "ALOAD",
                            "INSTANCEOF",
                            "IFEQ",
                            "ALOAD",
                            "CHECKCAST",
                            "ASTORE",
                            "line: 5",
                            "ALOAD_0",
                            "INVOKEVIRTUAL",
                            "ASTORE",
                            "ALOAD",
                            "INSTANCEOF",
                            "IFEQ",
                            "ALOAD",
                            "CHECKCAST",
                            "ASTORE_3",
                            "ICONST_1",
                            "GOTO",
                            "ICONST_0",
                            "ISTORE_2",
                            "line: 6",
                            "RETURN"))),
    };

    private static final class DetailedValidator implements BiConsumer<ClassModel, MethodModel> {

        private final List<String> expectedMethodContent;

        public DetailedValidator(String... expectedMethodContent) {
            this.expectedMethodContent = List.of(expectedMethodContent);
        }

        @Override
        public void accept(ClassModel classFile, MethodModel m) {
            CodeModel code = (CodeModel) m.code().get();
            List<String> methodContent = new ArrayList<>();
            for (CodeElement el : code) {
                switch (el) {
                    case Instruction instr -> methodContent.add(instr.opcode().name());
                    case LineNumber ln -> methodContent.add("line: " + ln.line());
                    case CodeElement _ -> {}
                }
            }
            methodContent.forEach(System.err::println);
            tb.checkEqual(expectedMethodContent, methodContent);
        }
    }
}
