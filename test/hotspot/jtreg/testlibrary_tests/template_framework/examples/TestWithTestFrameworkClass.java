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

/*
 * @test
 * @summary Test TestFrameworkClass.TEMPLATE which allows generating many tests and running them with the IR TestFramework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/generators/Generators.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run driver template_framework.examples.TestWithTestFrameworkClass
 */

package template_framework.examples;

import java.util.List;
import java.util.Set;

import compiler.lib.compile_framework.CompileFramework;

import compiler.lib.generators.Generators;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

import compiler.lib.template_framework.library.Hooks;
import compiler.lib.template_framework.library.TestFrameworkClass;

/**
 * This is a basic IR verification test, in combination with Generators for random input generation
 * and Verify for output verification.
 * <p>
 * The "@compile" command for JTREG is required so that the frameworks used in the Template code
 * are compiled and available for the Test-VM.
 * <p>
 * Additionally, we must set the classpath for the Test VM, so that it has access to all compiled
 * classes (see {@link CompileFramework#getEscapedClassPathOfCompiledClasses}).
 */
public class TestWithTestFrameworkClass {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile();

        // p.xyz.InnterTest.main(new String[] {});
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[] {}});

        // We can also pass VM flags for the Test VM.
        // p.xyz.InnterTest.main(new String[] {"-Xbatch"});
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[] {"-Xbatch"}});
    }

    // Generate a source Java file as String
    public static String generate(CompileFramework comp) {
        // A simple template that adds a comment.
        var commentTemplate = Template.make(() -> body(
            """
            // Comment inserted from test method to class hook.
            """
        ));

        // We define a Test-Template:
        // - static fields for inputs: INPUT_A and INPUT_B
        //   - Data generated with Generators and hashtag replacement #con1.
        // - GOLD value precomputed with dedicated call to test.
        //   - This ensures that the GOLD value is computed in the interpreter
        //     most likely, since the test method is not yet compiled.
        //     This allows us later to compare to the results of the compiled
        //     code.
        //     The input data is cloned, so that the original INPUT_A is never
        //     modified and can serve as identical input in later calls to test.
        // - In the Setup method, we clone the input data, since the input data
        //   could be modified inside the test method.
        // - The test method makes use of hashtag replacements (#con2 and #op).
        // - The Check method verifies the results of the test method with the
        //   GOLD value.
        var testTemplate = Template.make("op", (String op) -> body(
            let("size", Generators.G.safeRestrict(Generators.G.ints(), 10_000, 20_000).next()),
            let("con1", Generators.G.ints().next()),
            let("con2", Generators.G.safeRestrict(Generators.G.ints(), 1, Integer.MAX_VALUE).next()),
            """
            // --- $test start ---
            // $test with size=#size and op=#op
            private static int[] $INPUT_A = new int[#size];
            static {
                Generators.G.fill(Generators.G.ints(), $INPUT_A);
            }
            private static int $INPUT_B = #con1;
            private static Object $GOLD = $test($INPUT_A.clone(), $INPUT_B);

            @Setup
            public static Object[] $setup() {
                // Must make sure to clone input arrays, if it is mutated in the test.
                return new Object[] {$INPUT_A.clone(), $INPUT_B};
            }

            @Test
            @Arguments(setup = "$setup")
            public static Object $test(int[] a, int b) {
                for (int i = 0; i < a.length; i++) {
                    int con = #con2;
                    a[i] = (a[i] * con) #op b;
                }
                return a;
            }

            @Check(test = "$test")
            public static void $check(Object result) {
                Verify.checkEQ(result, $GOLD);
            }
            // --- $test end   ---
            """,
            // Good to know: we can insert to the class hook, which is set for the
            // TestFrameworkClass scope:
            Hooks.CLASS_HOOK.insert(commentTemplate.asToken())
        ));

        // Create a test for each operator.
        List<String> ops = List.of("+", "-", "*", "&", "|");
        List<TemplateToken> testTemplateTokens = ops.stream().map(testTemplate::asToken).toList();

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "p.xyz", "InnerTest",
            // Set of imports.
            Set.of("compiler.lib.generators.*",
                   "compiler.lib.verify.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }
}
