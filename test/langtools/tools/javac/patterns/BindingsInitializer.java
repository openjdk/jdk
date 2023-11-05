/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278834
 * @summary Verify pattern matching nested inside initializers of classes nested in methods
 *          works correctly.
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @build combo.ComboTestHelper
 * @compile BindingsInitializer.java
 * @run main BindingsInitializer
 */

import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask;
import combo.ComboTestHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import toolbox.ToolBox;

public class BindingsInitializer extends ComboInstance<BindingsInitializer> {
    protected ToolBox tb;

    BindingsInitializer() {
        super();
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new ComboTestHelper<BindingsInitializer>()
                .withDimension("OUTER", (x, outer) -> x.outer = outer, Outer.values())
                .withDimension("MIDDLE", (x, middle) -> x.middle = middle, Middle.values())
                .withDimension("INNER", (x, inner) -> x.inner = inner, Inner.values())
                .withDimension("TEST", (x, test) -> x.test = test, Test.values())
                .run(BindingsInitializer::new);
    }

    private Outer outer;
    private Middle middle;
    private Inner inner;
    private Test test;

    private static final String MAIN_TEMPLATE =
            """
            public class Test {
                private static Object obj = "";
                #{OUTER}
            }
            """;

    @Override
    protected void doWork() throws Throwable {
        Path base = Paths.get(".");

        ComboTask task = newCompilationTask()
                .withSourceFromTemplate(MAIN_TEMPLATE, pname -> switch (pname) {
                        case "OUTER" -> outer;
                        case "MIDDLE" -> middle;
                        case "INNER" -> inner;
                        case "TESST" -> test;
                        default -> throw new UnsupportedOperationException(pname);
                    });

        task.generate(result -> {
            if (result.hasErrors()) {
                throw new AssertionError("Unexpected result: " + result.compilationInfo());
            }
        });
    }

    public enum Outer implements ComboParameter {
        NONE("#{MIDDLE}"),
        STATIC_CLASS("static class Nested { #{MIDDLE} }"),
        CLASS("class Inner { #{MIDDLE} }");
        private final String code;

        private Outer(String code) {
            this.code = code;
        }

        @Override
        public String expand(String optParameter) {
            return code;
        }
    }

    public enum Middle implements ComboParameter {
        STATIC_INIT("static { #{INNER} }"),
        INIT("{ #{INNER} }"),
        METHOD("void test() { #{INNER} }");
        private final String code;

        private Middle(String code) {
            this.code = code;
        }

        @Override
        public String expand(String optParameter) {
            return code;
        }
    }

    public enum Inner implements ComboParameter {
        DIRECT("#{TEST}"),
        CLASS_STATIC_INIT("class C { static { #{TEST} } }"),
        CLASS_INIT("class C { { #{TEST} } }"),
        CLASS_METHOD("class C { void t() { #{TEST} } }"),
        ANNONYMOUS_CLASS_STATIC_INIT("new Object() { static { #{TEST} } };"),
        ANNONYMOUS_CLASS_INIT("new Object() { { #{TEST} } };"),
        ANNONYMOUS_CLASS_METHOD("new Object() { void t() { #{TEST} } };");
        private final String code;

        private Inner(String code) {
            this.code = code;
        }

        @Override
        public String expand(String optParameter) {
            return code;
        }
    }

    public enum Test implements ComboParameter {
        TEST("if (obj instanceof String str) System.err.println(str);");
        private final String code;

        private Test(String code) {
            this.code = code;
        }

        @Override
        public String expand(String optParameter) {
            return code;
        }
    }
}
