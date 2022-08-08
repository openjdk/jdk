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
import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask;
import combo.ComboTestHelper;
import toolbox.ToolBox;

/*
 * @test
 * @bug 8269674
 * @summary Improve testing of parenthesized patterns
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @build combo.ComboTestHelper
 * @compile ParenthesizedCombo.java
 * @run main/othervm ParenthesizedCombo
 */
public class ParenthesizedCombo extends ComboInstance<ParenthesizedCombo> {
    protected ToolBox tb;

    ParenthesizedCombo() {
        super();
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new ComboTestHelper<ParenthesizedCombo>()
                .withDimension("PATTERN_USE", (x, patternUse) -> x.patternUse = patternUse, PATTERN_USE.values())
                .withDimension("CASE_LABEL", (x, caseLabel) -> x.caseLabel = caseLabel, CASE_LABEL.values())
                .withDimension("TYPE_PATTERN", (x, typePattern) -> x.typePattern = typePattern, TYPE_PATTERN.values())
                .run(ParenthesizedCombo::new);
    }

    private PATTERN_USE patternUse;
    private CASE_LABEL caseLabel;
    private TYPE_PATTERN typePattern;

    private static final String MAIN_TEMPLATE =
        """
        public class Test {
            record StringBox(String s1) {}
            record StringBox2(StringBox s) {}
            public static void test(Object o) {
                #{PATTERN_USE}
            }
        }
        """;

    @Override
    protected void doWork() throws Throwable {
        ComboTask task = newCompilationTask()
                .withSourceFromTemplate(MAIN_TEMPLATE, pname -> switch (pname) {
                    case "PATTERN_USE" -> patternUse;
                    case "CASE_LABEL" -> caseLabel;
                    case "TYPE_PATTERN" -> typePattern;
                    default -> throw new UnsupportedOperationException(pname);
                })
                .withOption("--enable-preview")
                .withOption("-source")
                .withOption(String.valueOf(Runtime.version().feature()));
        task.generate(result -> {
            if (result.hasErrors()) {
                throw new AssertionError("Unexpected result: " + result.compilationInfo());
            }
        });
    }

    public enum TYPE_PATTERN implements ComboParameter {
        SIMPLE("String s1"),
        PARENTHESIZED_SIMPLE("(String s1)");

        private final String code;

        private TYPE_PATTERN(String code) {
            this.code = code;
        }

        @Override
        public String expand(String optParameter) {
            return code;
        }
    }

    public enum CASE_LABEL implements ComboParameter {
        TYPE_PATTERN("#{TYPE_PATTERN}"),
        PARENTHESIZED_RECORD_PATTERN("(StringBox(#{TYPE_PATTERN}))"),
        PARENTHESIZED_RECORD_PATTERN_DEEP("(StringBox2(StringBox(#{TYPE_PATTERN})))");

        private final String code;

        private CASE_LABEL(String code) {
            this.code = code;
        }

        @Override
        public String expand(String optParameter) {
            return code;
        }
    }

    public enum PATTERN_USE implements ComboParameter {
        SWITCH_EXPR_VOID(
            """
            switch (o) {
                case #{CASE_LABEL} when s1.isEmpty() -> System.err.println("OK: " + s1);
                    default -> throw new AssertionError();
            }
            """),
        SWITCH_STAT_VOID(
            """
            switch (o) {
                case #{CASE_LABEL} when s1.isEmpty():
                    System.err.println("OK: " + s1);
                    break;
                default:
                    throw new AssertionError();
            }
            """),
        SWITCH_EXPR_STRING(
            """
            System.err.println(switch (o) {
                case #{CASE_LABEL} when s1.isEmpty() -> "OK: " + s1;
                    default -> throw new AssertionError();
            });
            """),
        IF_INSTANCEOF(
            """
            if (o instanceof #{CASE_LABEL} && s1.isEmpty()) {
                System.err.println("OK: " + s1);
            }
            """);
        private final String body;

        private PATTERN_USE(String body) {
            this.body = body;
        }

        @Override
        public String expand(String optParameter) {
            return body;
        }
    }
}
