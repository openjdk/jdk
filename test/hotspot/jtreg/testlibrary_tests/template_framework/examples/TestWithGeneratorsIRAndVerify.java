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
 * @summary Example of a "best practice" test, using Generators, the IR Framework and Verify.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/generators/Generators.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run driver template_framework.examples.TestWithGeneratorsIRAndVerify
 */

package template_framework.examples;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;

import java.util.Arrays;
import java.util.HashSet;

/**
 * This is a basic IR verification test, in combination with Generators for random input generation
 * and Verify for output verification.
 * <p>
 * The "@compile" command for JTREG is required so that the frameworks used in the Template code
 * are compiled and available for the Test-VM.
 * <p>
 * Additionally, we must set the classpath for the Test-VM, so that it has access to all compiled
 * classes (see {@link CompileFramework#getEscapedClassPathOfCompiledClasses}).
 */
public class TestWithGeneratorsIRAndVerify {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile();

        // Object ret = p.xyz.InnerTest.main();
        Object ret = comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[0]});
        System.out.println("res: " + ret);
    }

    // Generate a source Java file as String
    public static String generate(CompileFramework comp) {
        // We need to import all the used classes.
        HashSet<String> imports = new HashSet<String>();
        imports.add("compiler.lib.ir_framework.*");
        imports.add("compiler.lib.generators.*");
        imports.add("compiler.lib.verify.*");
        CodeGeneratorLibrary library = CodeGeneratorLibrary.standard();
        TestClassInstantiator instantiator = new TestClassInstantiator("p.xyz", "InnerTest", library, imports);

        // Definie the main method body, where we laungh the IR tests from. It is imporant
        // that we pass the classpath to the Test-VM, so that it has access to all compiled
        // classes.
        Template mainTemplate = new Template("my_example_main",
            """
            TestFramework framework = new TestFramework(InnerTest.class);
            framework.addFlags("-classpath", "#{classpath}");
            framework.start();
            """
        );
        instantiator.where("classpath", comp.getEscapedClassPathOfCompiledClasses())
                    .add(null, mainTemplate, null);

        // We define a Test-Template:
        // - static fields for inputs: INPUT_A and INPUT_B
        //   - Data generated with Generators and template holes (int_con).
        // - GOLD value precomputed with dedicated call to test.
        //   - This ensures that the GOLD value is computed in the interpreter
        //     most likely, since the test method is not yet compiled.
        //     This allows us later to compare to the results of the compiled
        //     code.
        //     The input data is cloned, so that the original INPUT_A is never
        //     modified and can serve as identical input in later calls to test.
        // - In the Setup method, we clone the input data, since the input data
        //   could be modified inside the test method.
        // - The test method can further make use of template holes, e.g.
        //   recursive calls to other generators (e.g. int_con) or holes that
        //   are filled with parameter values (e.g. OP).
        // - The Check method verifies the results of the test method with the
        //   GOLD value.
        Template testTemplate = new Template("my_example_test",
            """
            // $test with length #{size:int_con(lo=10000,hi=20000)}
            private static int[] $INPUT_A = new int[#{size}];
            static {
                Generators.G.fill(Generators.G.ints(), $INPUT_A);
            }

            private static int $INPUT_B = #{:int_con};
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
                    int con = #{:int_con(lo=1,hi=max_int)};
                    a[i] = (a[i] * con) #{OP} b;
                }
                return a;
            }

            @Check(test = "$test")
            public static void $check(Object result) {
                Verify.checkEQ(result, $GOLD);
            }
            """
        );

        // Instantiate the test twice (repeat 2) for each operator OP.
        instantiator.where("OP", Arrays.asList("+", "-", "*", "&", "|"))
                    .repeat(2)
                    .add(null, null, testTemplate);

        // Collect everything into a String.
        return instantiator.instantiate();
    }
}
