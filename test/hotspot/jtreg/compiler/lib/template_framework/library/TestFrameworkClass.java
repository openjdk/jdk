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
import java.util.Set;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

/**
 * This class provides a {@link #render} method that can be used to simplify generating
 * source code when using the {@link TestFramework} (also known as IR Framework) to run
 * a list of tests.
 *
 * <p>
 * The idea is that the user only has to generate the code for the individual tests,
 * and can then pass the corresponding list of {@link TemplateToken}s to this
 * provided {@link #render} method which generates the surrounding class and the main
 * method that invokes the {@link TestFramework}, so that all the generated tests
 * are run.
 */
public final class TestFrameworkClass {

    // Ensure there can be no instance, and we do not have to document the constructor.
    private TestFrameworkClass() {}

    /**
     * This method renders a list of {@code testTemplateTokens} into the body of a class
     * and generates a {@code main} method which launches the {@link TestFramework}
     * to run the generated tests.
     *
     * <p>
     * The generated {@code main} method is to be invoked with a {@code vmFlags} argument,
     * which must be a {@link String[]}, specifying the VM flags for the Test VM, in which
     * the tests will be run. Thus, one can generate the test class once, and invoke its
     * {@code main} method multiple times, each time with a different set of VM flags.
     *
     * <p>
     * The internal {@link Template} sets the {@link Hooks#CLASS_HOOK} for the scope of
     * all test methods.
     *
     * @param packageName The package name of the test class.
     * @param className The name of the test class.
     * @param imports A set of imports.
     * @param classpath The classpath from {@link CompileFramework#getEscapedClassPathOfCompiledClasses},
     *                  so that the Test VM has access to the class files that are compiled from the
     *                  generated source code.
     * @param testTemplateTokens The list of tests to be generated into the test class.
     *                           Every test must be annotated with {@code @Test}, so that
     *                           the {@link TestFramework} can later find and run them.
     * @return The generated source code of the test class as a {@link String}.
     */
    public static String render(final String packageName,
                                final String className,
                                final Set<String> imports,
                                final String classpath,
                                final List<TemplateToken> testTemplateTokens) {
        var template = Template.make(() -> body(
            let("packageName", packageName),
            let("className", className),
            let("classpath", classpath),
            """
            package #packageName;
            // --- IMPORTS start ---
            import compiler.lib.ir_framework.*;
            """,
            imports.stream().map(i -> "import " + i + ";\n").toList(),
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
        return template.render();
    }
}
