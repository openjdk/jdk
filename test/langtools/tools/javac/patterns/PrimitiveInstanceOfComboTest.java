/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304487
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @build combo.ComboTestHelper
 * @compile PrimitiveInstanceOfComboTest.java
 * @run main PrimitiveInstanceOfComboTest
 */

import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask;
import combo.ComboTestHelper;
import toolbox.ToolBox;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.List;

public class PrimitiveInstanceOfComboTest extends ComboInstance<PrimitiveInstanceOfComboTest> {
    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    protected ToolBox tb;

    PrimitiveInstanceOfComboTest() {
        super();
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new ComboTestHelper<PrimitiveInstanceOfComboTest>()
                .withDimension("TYPE1", (x, type1) -> x.type1 = type1, Type.values())
                .withDimension("TYPE2", (x, type2) -> x.type2 = type2, Type.values())
                .withFailMode(ComboTestHelper.FailMode.FAIL_FAST)
                .run(PrimitiveInstanceOfComboTest::new);
    }

    private Type type1;
    private Type type2;

    private static final String test1 =
            """
            public class Test {
                public static void doTest(#{TYPE1} in) {
                    var r = (#{TYPE2}) in;
                }
            }
            """;

    private static final String test2 =
            """
            public class Test {
                public static void doTest(#{TYPE1} in) {
                    var r = in instanceof #{TYPE2};
                }
            }
            """;

    // potential not-exhaustive errors are expected and filtered out in `doWork`
    private static final String test3 =
            """
            public class Test {
                public static void doTest(#{TYPE1} in) {
                    switch(in) {
                       case #{TYPE2} x -> {}
                    }
                }
            }
            """;

    @Override
    protected void doWork() throws Throwable {
        ComboTask task1 = newCompilationTask()
                .withSourceFromTemplate(test1.replace("#{TYPE1}", type1.code).replace("#{TYPE2}", type2.code))
                .withOption("--enable-preview")
                .withOption("-source").withOption(JAVA_VERSION);

        ComboTask task2 = newCompilationTask()
                .withSourceFromTemplate(test2.replace("#{TYPE1}", type1.code).replace("#{TYPE2}", type2.code))
                .withOption("--enable-preview")
                .withOption("-source").withOption(JAVA_VERSION);

        ComboTask task3 = newCompilationTask()
                .withSourceFromTemplate(test3.replace("#{TYPE1}", type1.code).replace("#{TYPE2}", type2.code))
                .withOption("--enable-preview")
                .withOption("-source").withOption(JAVA_VERSION);

        task1.generate(result1 -> {
            task2.generate(result2 -> {
                task3.generate(result3 -> {
                    List<Diagnostic<? extends JavaFileObject>> list1 = result1.diagnosticsForKind(Diagnostic.Kind.ERROR);
                    List<Diagnostic<? extends JavaFileObject>> list2 = result2.diagnosticsForKind(Diagnostic.Kind.ERROR);
                    List<Diagnostic<? extends JavaFileObject>> list3 = result3.diagnosticsForKind(Diagnostic.Kind.ERROR).stream().filter(e -> !e.getCode().equals("compiler.err.not.exhaustive.statement")).toList();
                    if (!(list1.size() == list2.size() && list3.size() == list2.size())) {
                        throw new AssertionError("Unexpected result: " +
                                "\n task1: " + result1.hasErrors() + ", info: " + result1.compilationInfo() +
                                "\n task2: " + result2.hasErrors() + ", info: " + result2.compilationInfo() +
                                "\n task3: " + result3.hasErrors() + ", info: " + result3.compilationInfo()
                        );
                    }
                });
            });
        });
    }

    public enum Type implements ComboParameter {
        BYTE("byte"),
        SHORT("short"),
        CHAR("char"),
        INT("int"),
        LONG("long"),
        FLOAT("float"),
        DOUBLE("double"),
        BOOLEAN("boolean"),

        BYTE_r("Byte"),
        SHORT_r("Short"),
        CHAR_r("Character"),
        INTEGER_r("Integer"),
        LONG_r("Long"),
        FLOAT_r("Float"),
        DOUBLE_r("Double"),
        BOOLEAN_r("Boolean");

        private final String code;

        Type(String code) {
            this.code = code;
        }

        @Override
        public String expand(String optParameter) {
            throw new UnsupportedOperationException("Not supported.");
        }
    }
}
