/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework.library;

import java.util.List;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

/**
 * This class provides a {@link #TEMPLATE} that can be used to simplify generating
 * source code when using the {@link TestFramework} (also known as IR Framework) to run a list of tests.
 *
 * <p>
 * The idea is that the user only has to generate the code for the individual tests,
 * and can then pass the corresponding list of {@link TemplateToken}s to this
 * provided {@link #TEMPLATE} which generates the surrounding class and the main
 * method that invokes the {@link TestFramework}, so that all the generated tests
 * are run.
 */
public final class TestFrameworkClass {

    // Ensure there can be no instance, and we do not have to document the constructor.
    private TestFrameworkClass() {}

    /**
     * To use the {@link TestFrameworkClass#TEMPLATE}, the user must specify the
     * {@link #packageName} and {@link #className}, as well as a list of {@link #imports}
     * and the {@link #classpath} from {@link CompileFramework#getEscapedClassPathOfCompiledClasses},
     * so that the Test VM has access to the class files that are compiled from the generated
     * source code.
     *
     * @param packageName The package name of the test class.
     * @param className The name of the test class.
     * @param imports A list of imports.
     * @param classpath The classpath from {@link CompileFramework#getEscapedClassPathOfCompiledClasses},
     *                  so that the Test VM has access to the class files that are compiled from the
     *                  generated source code.
     */
    public record Info(String packageName, String className, List<String> imports, String classpath) {};

    /**
     * This {@link Template} simplifies generating source code when using the {@link TestFramework}
     * (also known as IR Framework) to run a list of tests.
     *
     * <p>
     * The {@code info} argument encapsulates the context information for the
     * generated class. The {@code testTemplateTokens} is a list of {@link TemplateToken}s,
     * which represent the tests that are to be generated inside this test class.
     *
     * <p>
     * The {@code main} method is to be invoked with a {@code vmFlags} argument, where
     * the Test VM flags can be specified with which the tests are to be run.
     */
    public static final Template.TwoArgs<Info, List<TemplateToken>> TEMPLATE =
        Template.make("info", "testTemplateTokens", (Info info, List<TemplateToken> testTemplateTokens) -> body(
            let("classpath", info.classpath),
            let("packageName", info.packageName),
            let("className", info.className),
            """
            package #packageName;
            // --- IMPORTS start ---
            import compiler.lib.ir_framework.*;
            """,
            info.imports.stream().map(i -> "import " + i + ";\n").toList(),
            """
            // --- IMPORTS end   ---
            public class #className {
            // --- CLASS_HOOK insertions start ---
            """,
            Hooks.CLASS_HOOK.anchor(
            """
            // --- CLASS_HOOK insertions end   ---
                public static void main(String[] vmFlags) {
                    TestFramework framework = new TestFramework(#className.class);
                    framework.addFlags("-classpath", "#classpath");
                    framework.addFlags(vmFlags);
                    framework.start();
                }
            // --- LIST OF TESTS start ---
            """,
            testTemplateTokens
            ),
            """
            // --- LIST OF TESTS end   ---
            }
            """
        ));
}
