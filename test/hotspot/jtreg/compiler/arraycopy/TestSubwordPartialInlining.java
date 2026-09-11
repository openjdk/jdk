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

/**
 * @test
 * @bug 8387073
 * @key randomness
 * @summary Arrays.copyOf(Range) must pad overized elements with 0 with partial inlining as well.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.arraycopy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.*;


import jdk.test.lib.Utils;
import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import compiler.lib.template_framework.library.*;

public class TestSubwordPartialInlining {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final String PACKAGE = "compiler.arraycopy.generated";
    private static final String CLASS_NAME = "TestSubwordPartialInliningGenerated";

    public static void main(String[] args) {
        final CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode(PACKAGE + "." + CLASS_NAME, generate(comp));
        comp.compile();
        comp.invoke(PACKAGE + "." + CLASS_NAME, "main", new Object[] { args });
    }

    private static String generate(CompileFramework comp) {
        final Set<String> imports = Set.of("java.util.Arrays",
                                           "java.util.Random",
                                           "jdk.test.lib.Utils",
                                           "compiler.lib.generators.*");

        final List<TemplateToken> tests = new ArrayList<>();
        tests.addAll(Stream.of(CodeGenerationDataNameType.booleans(), CodeGenerationDataNameType.bytes(), CodeGenerationDataNameType.shorts(), CodeGenerationDataNameType.chars())
                           .map(pty -> new TestPerType(pty).generate())
                           .toList());
        tests.add(PrimitiveType.generateLibraryRNG());

        return TestFrameworkClass.render(PACKAGE, CLASS_NAME, imports, comp.getEscapedClassPathOfCompiledClasses(), tests);
    }

    enum Operation {
        COPY_OF,
        COPY_OF_RANGE
    }

    record TestPerType(PrimitiveType pty) {
        private String getTestName(Operation op) {
            return switch (op) {
                case COPY_OF       -> "CopyOf" + pty.boxedTypeName();
                case COPY_OF_RANGE -> "CopyOfRange" + pty.boxedTypeName();
            };
        }

        TemplateToken generate() {
            final int maxSize = RANDOM.nextInt(0, 4) == 0 ? RANDOM.nextInt(5, 100) : 4;
            final int inputSize = RANDOM.nextInt(1, maxSize);
            final int copySize = RANDOM.nextInt(inputSize + 1, maxSize + 1);


            var runTemplate = Template.make("op", (Operation op) -> scope(
                let("pty", pty),
                let("testName", getTestName(op)),
                """
                    @Run(test = "test#{testName}", mode = RunMode.STANDALONE)
                    static void run#{testName}() {
                        final #pty[] intRes = test#{testName}();
                        for (int i = 0; i < 10_000; i++) {
                            test#{testName}();
                        }
                        final #pty[] compRes = test#{testName}();
                        if (!Arrays.equals(intRes, compRes)) {
                            throw new RuntimeException("wrong result:\\n" +
                                                       "  interpreter result: " + Arrays.toString(intRes) + "\\n" +
                                                       "  compiled result: " + Arrays.toString(compRes));
                        }
                    }
                """
            ));

            var testTemplate = Template.make("op", (Operation op) -> scope(
                let("pty", pty),
                let("testName", getTestName(op)),
                let("len", copySize),
                let("minRange", RANDOM.nextInt(0, 4) == 0 ? RANDOM.nextInt(0, inputSize) : 0),
                """
                    @Test
                    static #pty[] test#{testName}() {
                """,
                "       return ",
                switch (op) {
                    case COPY_OF       -> "Arrays.copyOf(#{pty}Arr, #len)";
                    case COPY_OF_RANGE -> "Arrays.copyOfRange(#{pty}Arr, #minRange, #len)";
                },
                ";\n",
                """
                    }

                """
            ));

            return Template.make(() -> scope(
                Stream.of(Operation.COPY_OF, Operation.COPY_OF_RANGE)
                      .map(op -> scope(runTemplate.asToken(op), testTemplate.asToken(op)))
                      .toList(),
                Hooks.CLASS_HOOK.insert(scope(
                    let("pty", pty),
                    let("arr", String.join(", ", Collections.nCopies(inputSize, (String) pty.callLibraryRNG()))),
                    """
                        private static #pty[] #{pty}Arr = { #arr };
                    """
                ))
            )).asToken();
        }
    }
}
