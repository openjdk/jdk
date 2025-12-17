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
 * @bug 8358772
 * @summary Demonstrate the use of VectorTypes (Vector API) form the Template Library.
 * @modules java.base/jdk.internal.misc
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run main ${test.main.class}
 */

package template_framework.examples;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;

import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.VectorType;
import compiler.lib.template_framework.library.TestFrameworkClass;

/**
 * This test shows the use of {@link VectorType}.
 */
public class TestVectorTypes {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile("--add-modules=jdk.incubator.vector");

        // p.xyz.InnerTest.main();
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[] {
            "--add-modules=jdk.incubator.vector",
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        }});
    }

    // Generate a Java source file as String
    public static String generate(CompileFramework comp) {
        // Generate a list of test methods.
        List<TemplateToken> tests = new ArrayList<>();

        var vectorTemplate = Template.make("type", (VectorType.Vector type) -> {
            return scope(
                let("elementType", type.elementType),
                let("length", type.length),
                let("SPECIES", type.speciesName),
                let("maskType", type.maskType),
                let("shuffleType", type.shuffleType),
                """
                // #type #elementType #length #SPECIES
                @Test
                public static void $test() {
                """,
                "    #elementType scalar = ", type.elementType.con(), ";\n",
                "    #maskType mask = ", type.maskType.con(), ";\n",
                "    #shuffleType shuffle = ", type.shuffleType.con(), ";\n",
                """
                    #type zeros = #{type}.zero(#SPECIES);
                    #type zeros2 = #{type}.broadcast(#SPECIES, 0);
                    #type vector = #{type}.broadcast(#SPECIES, scalar);
                    Verify.checkEQ(zeros, zeros2);
                    Verify.checkEQ(vector, vector.add(zeros));
                    Verify.checkEQ(vector.length(), #length);
                    Verify.checkEQ(vector.lane(#length-1), scalar);

                    #type v1 = zeros.add(vector, mask);
                    #type v2 = zeros.blend(vector, mask);
                    Verify.checkEQ(v1, v2);

                    #type iota = zeros.addIndex(1);
                    #type v3 = iota.rearrange(iota.toShuffle());
                    Verify.checkEQ(iota, v3);
                    #type v4 = iota.rearrange(shuffle);
                    Verify.checkEQ(shuffle, v4.toShuffle());
                    Verify.checkEQ(shuffle.toVector(), v4);
                }
                """
            );
        });

        for (var type : CodeGenerationDataNameType.VECTOR_ALL_VECTOR_TYPES) {
            tests.add(vectorTemplate.asToken(type));
        }

        // Create the test class, which runs all tests.
        return TestFrameworkClass.render(
            // package and class name.
            "p.xyz", "InnerTest",
            // Set of imports.
            Set.of("compiler.lib.verify.*", "jdk.incubator.vector.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            tests);
    }
}
