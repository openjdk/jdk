/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.transparentScope;
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
        var boxingTemplate = Template.make("name", "type", (String name, PrimitiveType type) -> scope(
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
        var integralFloatTemplate = Template.make("name", "type", (String name, PrimitiveType type) -> scope(
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
        // IMPORTANT: since we are adding the DataName via an inserted Template, we
        //            must chose a "transparentScope", so that the DataName escapes. If we
        //            instead chose "scope", the test would fail, because it later
        //            finds no DataNames when we sample.
        var variableTemplate = Template.make("type", (PrimitiveType type) -> transparentScope(
            let("CON", type.con()),
            addDataName($("var"), type, MUTABLE), // escapes the Template
            """
            #type $var = #CON;
            """
        ));

        var sampleTemplate = Template.make("type", (PrimitiveType type) -> scope(
            let("CON", type.con()),
            dataNames(MUTABLE).exactOf(type).sampleAndLetAs("var"),
            """
            #var = #CON;
            """
        ));

        var namesTemplate = Template.make(() -> scope(
            """
            public static void test_names() {
            """,
            Hooks.METHOD_HOOK.anchor(scope(
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
            )),
            """
            }
            """
        ));

        tests.put("test_names", namesTemplate.asToken());

        var abbrevDefTemplate = Template.make("type", (PrimitiveType type) -> scope(
            let("CON1", type.con()),
            let("CON2", type.con()),
            let("abbrev", type.abbrev()),
            let("fieldDesc", type.fieldDesc()),
            """
            static #type varAbbrev#abbrev = #CON1;
            static #type varFieldDesc#fieldDesc = #CON2;
            """
        ));
        var swapTemplate = Template.make("type", (PrimitiveType type) -> scope(
            let("abbrev", type.abbrev()),
            let("fieldDesc", type.fieldDesc()),
            """
            #type tmp#abbrev = varAbbrev#abbrev;
            varAbbrev#abbrev = varFieldDesc#fieldDesc;
            varFieldDesc#fieldDesc = tmp#abbrev;
            """
        ));
        var abbrevTemplate = Template.make(() -> scope(
            """
            public static void test_abbrev() {
            """,
            Hooks.CLASS_HOOK.insert(scope(
                // Create fields that would collide if the abbrev() or fieldDesc() methods produced colliding
                // strings for different types
                CodeGenerationDataNameType.PRIMITIVE_TYPES.stream().map(type ->
                    abbrevDefTemplate.asToken(type)
                ).toList()
            )),
                CodeGenerationDataNameType.PRIMITIVE_TYPES.stream().map(type ->
                    swapTemplate.asToken(type)
                ).toList(),
            """
            }
            """
        ));

        tests.put("test_abbrev", abbrevTemplate.asToken());

        // Test runtime random value generation with LibraryRNG
        // Runtime random number generation of a given primitive type can be very helpful
        // when writing tests that require random inputs.
        var libraryRNGWithTypeTemplate = Template.make("type", (PrimitiveType type) -> scope(
            """
            {
                // Fill an array with 1_000 random values. Every type has at least 2 values,
                // so the chance that all values are the same is 2^-1_000 < 10^-300. This should
                // never happen, even with a relatively weak PRNG.
                #type[] a = new #type[1_000];
                for (int i = 0; i < a.length; i++) {
            """,
            "       a[i] = ", type.callLibraryRNG(), ";\n",
            """
                }
                boolean allSame = true;
                for (int i = 0; i < a.length; i++) {
                    if (a[i] != a[0]) {
                        allSame = false;
                        break;
                    }
                }
                if (allSame) { throw new RuntimeException("all values were the same for #type"); }
            }
            """
        ));

        var libraryRNGTemplate = Template.make(() -> scope(
            // Make sure we instantiate the LibraryRNG class.
            PrimitiveType.generateLibraryRNG(),
            // Now we can use it inside the test.
            """
            public static void test_LibraryRNG() {
            """,
            CodeGenerationDataNameType.PRIMITIVE_TYPES.stream().map(libraryRNGWithTypeTemplate::asToken).toList(),
            """
            }
            """
        ));

        tests.put("test_LibraryRNG", libraryRNGTemplate.asToken());

        // Finally, put all the tests together in a class, and invoke all
        // tests from the main method.
        var template = Template.make(() -> scope(
            """
            package p.xyz;

            import compiler.lib.verify.*;
            import java.lang.foreign.MemorySegment;

            // Imports for LibraryRNG
            import java.util.Random;
            import jdk.test.lib.Utils;
            import compiler.lib.generators.*;

            public class InnerTest {
            """,
            Hooks.CLASS_HOOK.anchor(scope(
            """
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
            tests.values().stream().toList()
            )),
            """
            }
            """
        ));

        // Render the template to a String.
        return template.render();
    }
}
