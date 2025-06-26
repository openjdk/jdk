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
 * @summary Demonstrate the use of PrimitiveTypes form the Template Library.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run main template_framework.examples.TestPrimitiveTypes
 */

package template_framework.examples;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.dataNames;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.addDataName;
import static compiler.lib.template_framework.DataName.Mutability.MUTABLE;

import compiler.lib.template_framework.library.Hooks;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.PrimitiveType;

/**
 * This test shows the use of {@link PrimitiveType}.
 */
public class TestPrimitiveTypes {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate());

        // Compile the source file.
        comp.compile();

        // p.xyz.InnerTest.main();
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {});
    }

    // Generate a Java source file as String
    public static String generate() {
        // Generate a list of test methods.
        Map<String, TemplateToken> tests = new HashMap<>();

        // The boxing tests check if we can autobox with "boxedTypeName".
        var boxingTemplate = Template.make("name", "type", (String name, PrimitiveType type) -> body(
            let("CON1", type.con()),
            let("CON2", type.con()),
            let("Boxed", type.boxedTypeName()),
            """
            public static void #name() {
                #type c1 = #CON1;
                #type c2 = #CON2;
                #Boxed b1 = c1;
                #Boxed b2 = c2;
                Verify.checkEQ(c1, b1);
                Verify.checkEQ(c2, b2);
            }
            """
        ));

        for (PrimitiveType type : CodeGenerationDataNameType.PRIMITIVE_TYPES) {
            String name = "test_boxing_" + type.name();
            tests.put(name, boxingTemplate.asToken(name, type));
        }

        // Integral and Float types have a size. Also test if "isFloating" is correct.
        var integralFloatTemplate = Template.make("name", "type", (String name, PrimitiveType type) -> body(
            let("size", type.byteSize()),
            let("isFloating", type.isFloating()),
            """
            public static void #name() {
                // Test byteSize via creation of array.
                #type[] array = new #type[1];
                MemorySegment ms = MemorySegment.ofArray(array);
                if (#size != ms.byteSize()) {
                    throw new RuntimeException("byteSize mismatch #type");
                }

                // Test isFloating via rounding.
                double value = 1.5;
                #type rounded = (#type)value;
                boolean isFloating = value != rounded;
                if (isFloating == #isFloating) {
                    throw new RuntimeException("isFloating mismatch #type");
                }
            }
            """
        ));

        for (PrimitiveType type : CodeGenerationDataNameType.INTEGRAL_AND_FLOATING_TYPES) {
            String name = "test_integral_floating_" + type.name();
            tests.put(name, integralFloatTemplate.asToken(name, type));
        }

        // Finally, test the type by creating some DataNames (variables), and sampling
        // from them. There should be no cross-over between the types.
        var variableTemplate = Template.make("type", (PrimitiveType type) -> body(
            let("CON", type.con()),
            addDataName($("var"), type, MUTABLE),
            """
            #type $var = #CON;
            """
        ));

        var sampleTemplate = Template.make("type", (PrimitiveType type) -> body(
            let("var", dataNames(MUTABLE).exactOf(type).sample().name()),
            let("CON", type.con()),
            """
            #var = #CON;
            """
        ));

        var namesTemplate = Template.make(() -> body(
            """
            public static void test_names() {
            """,
            Hooks.METHOD_HOOK.anchor(
                Collections.nCopies(10,
                    CodeGenerationDataNameType.PRIMITIVE_TYPES.stream().map(type ->
                        Hooks.METHOD_HOOK.insert(variableTemplate.asToken(type))
                    ).toList()
                ),
                """
                // Now sample:
                """,
                Collections.nCopies(10,
                    CodeGenerationDataNameType.PRIMITIVE_TYPES.stream().map(sampleTemplate::asToken).toList()
                )
            ),
            """
            }
            """
        ));

        tests.put("test_names", namesTemplate.asToken());

        // Finally, put all the tests together in a class, and invoke all
        // tests from the main method.
        var template = Template.make(() -> body(
            """
            package p.xyz;

            import compiler.lib.verify.*;
            import java.lang.foreign.MemorySegment;

            public class InnerTest {
                public static void main() {
            """,
            // Call all test methods from main.
            tests.keySet().stream().map(
                n -> List.of(n, "();\n")
            ).toList(),
            """
                }
            """,
            // Now add all the test methods.
            tests.values().stream().toList(),
            """
            }
            """
        ));

        // Render the template to a String.
        return template.render();
    }
}
