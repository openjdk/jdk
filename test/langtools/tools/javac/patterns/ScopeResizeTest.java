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
 * @bug 8292756
 * @summary Verify the Scope can be safely and correctly resized to accommodate pattern binding variables
 *          when the Scope for a guard is constructed.
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @build combo.ComboTestHelper
 * @compile ScopeResizeTest.java
 * @run main ScopeResizeTest
 */

import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask;
import combo.ComboTestHelper;
import java.util.stream.Stream;
import toolbox.ToolBox;

public class ScopeResizeTest extends ComboInstance<ScopeResizeTest> {
    protected ToolBox tb;

    ScopeResizeTest() {
        super();
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        int variantsSize = 17;
        PredefinedVariables[] variants = Stream.iterate(0, i -> i + 1)
                                               .limit(variantsSize)
                                               .map(s -> new PredefinedVariables(s))
                                               .toArray(s -> new PredefinedVariables[s]);
        new ComboTestHelper<ScopeResizeTest>()
                .withDimension("PREDEFINED_VARIABLES", (x, predefinedVariables) -> x.predefinedVariables = predefinedVariables, variants)
                .run(ScopeResizeTest::new);
    }

    private PredefinedVariables predefinedVariables;

    private static final String MAIN_TEMPLATE =
            """
            public class Test {
                public static void test(Object o) {
                    #{PREDEFINED_VARIABLES}
                    switch (o) {
                        case String s when s.isEmpty() -> {}
                        default -> {}
                    }
                }
            }
            """;

    @Override
    protected void doWork() throws Throwable {
        ComboTask task = newCompilationTask()
                .withSourceFromTemplate(MAIN_TEMPLATE, pname -> switch (pname) {
                        case "PREDEFINED_VARIABLES" -> predefinedVariables;
                        default -> throw new UnsupportedOperationException(pname);
                    });

        task.analyze(result -> {});
    }

    public record PredefinedVariables(int size) implements ComboParameter {
        @Override
        public String expand(String optParameter) {
            StringBuilder variables = new StringBuilder();
            for (int i = 0; i < size(); i++) {
                variables.append("int i" + i + ";\n");
            }
            return variables.toString();
        }
    }

}
