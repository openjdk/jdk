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
import compiler.lib.verify.*;
import compiler.lib.ir_framework.*; // TODO maybe

import java.util.HashSet;

/**
 * TODO adjust this comment.
 *
 * This test shows that the IR verification can be done on code compiled by the Compile Framework.
 * The "@compile" command for JTREG is required so that the IRFramework is compiled, other javac
 * might not compile it because it is not present in the class, only in the dynamically compiled
 * code.
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

        Template mainTemplate = new Template("my_example_main",
            """
            TestFramework framework = new TestFramework(InnerTest.class);
            framework.addFlags("-classpath", "#classpath");
            framework.start();
            """
        );
        instantiator.where("classpath", comp.getEscapedClassPathOfCompiledClasses())
                    .add(null, mainTemplate, null);

        Template staticsTemplate = new Template("my_example_statics",
            """
            // $statics with length #{size:int_con(lo=10000,hi=20000)}
            private static int[] $INPUT_A = new int[#{size}];
            static {
                Generators.ints().fill($INPUT_A, Integer.MIN_VALUE, Integer.MAX_VALUE);
            }

            private static int $INPUT_B = #{:int_con};
            private static Object $GOLD = $test($INPUT_A.clone(), $INPUT_B);
            """
        );
        Template testTemplate = new Template("my_example_test",
            """
            @Setup
            public static Object[] $setup() {
                // Must make sure to clone input arrays, if it is mutated in the test.
                return new Object[] {$INPUT_A.clone(), $INPUT_B};
            }

            @Test
            @Arguments(setup = "$setup")
            public static Object $test(int[] a, int b) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = a[i] + b;
                }
                return a;
            }

            @Check(test = "$test")
            public static void $check(Object result) {
                Verify.checkEQ(result, $GOLD);
            }
            """
        );

        // TODO desc, and maybe extend
        instantiator.add(staticsTemplate, null, testTemplate);

        // Collect everything into a String.
        return instantiator.instantiate();
    }
}
