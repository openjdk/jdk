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
 * @bug 8373480
 * @summary Optimize multiplication by constant multiplier using LEA instructions
 * @library /test/lib /
 * @compile ../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../compiler/lib/generators/Generators.java
 * @compile ../../compiler/lib/verify/Verify.java
 * @run driver compiler.c2.TestConstantMultiplier
 */

package compiler.c2;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import compiler.lib.ir_framework.Test;

import compiler.lib.compile_framework.*;
import compiler.lib.generators.Generators;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;

import compiler.lib.template_framework.library.TestFrameworkClass;

public class TestConstantMultiplier {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("c2.compilerr.ConstantMultiplierTest", generate(comp));

        // Compile the source file.
        comp.compile();

        comp.invoke("c2.compiler.ConstantMultiplierTest", "main", new Object[] {new String[] {}});

        // We can also pass VM flags for the Test VM.
        comp.invoke("c2.compiler.ConstantMultiplierTest", "main", new Object[] {new String[] {"-Xbatch"}});
    }


    // Generate a source Java file as String
    public static String generate(CompileFramework comp) {
        var testHeader = Template.make(() -> scope(
            """
                public static Random RANDOM = new Random(1023);

            """
        ));
        var testTemplate = Template.make(() -> scope(
            IntStream.of(81, 73, 45, 41, 37, 27, 25, 21, 19, 13, 11).mapToObj(
                multiplier -> scope(
                    let("multiplier", multiplier),
                    """
                        @Test
                        @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
                        private static int testMultBy#{multiplier}I(int num) {
                            return num * #{multiplier};
                        }

                        @Run(test = "testMultBy#{multiplier}I")
                        private static void runMultBy#{multiplier}II() {
                            int multiplicand = RANDOM.nextInt();
                            Verify.checkEQ(#{multiplier} * multiplicand, testMultBy#{multiplier}I(multiplicand));
                        }

                        @Test
                        @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
                        private static long testMultBy#{multiplier}L(long num) {
                            return num * #{multiplier};
                        }

                        @Run(test = "testMultBy#{multiplier}L")
                        private static void runMultBy#{multiplier}L() {
                            long multiplicand = RANDOM.nextInt();
                            Verify.checkEQ(#{multiplier} * multiplicand, testMultBy#{multiplier}L(multiplicand));
                        }
                    """
            )).toList()
        ));

        var testClass = Template.make(() -> scope(
            testHeader.asToken(),
            testTemplate.asToken()
        ));

        List<TemplateToken> testTemplateTokens = List.of(testClass.asToken());

        return TestFrameworkClass.render(
            // package and class name.
            "c2.compiler", "ConstantMultiplierTest",
            // Set of imports.
            Set.of("java.util.Random","compiler.lib.verify.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }
}
