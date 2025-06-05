/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8324751
 * @summary Test Speculative Aliasing checks in SuperWord
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/generators/Generators.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run driver compiler.loopopts.superword.TestAliasingFuzzer
 */

package compiler.loopopts.superword;

import java.util.List;
import java.util.ArrayList;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

import compiler.lib.template_framework.library.TestFrameworkClass;

/**
 * Simpler test cases can be found in {@link TestAliasing}.
 */
public class TestAliasingFuzzer {


    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile();

        // Run the tests without any additional VM flags.
        // p.xyz.InnterTest.main(new String[] {});
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[] {}});
    }

    public static String generate(CompileFramework comp) {
        // Create a list to collect all tests.
        List<TemplateToken> testTemplateTokens = new ArrayList<>();


        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "p.xyz", "InnerTest",
            // List of imports.
            List.of("compiler.lib.generators.*",
                    "compiler.lib.verify.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }
}
