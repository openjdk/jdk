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
 * @summary Test the Template Library's expression generation for the Vector API.
 * @modules jdk.incubator.vector
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/generators/Generators.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run driver template_library.examples.TestFuzzVectorAPI
 */

package template_library.examples;

import java.util.List;
import java.util.ArrayList;

import compiler.lib.compile_framework.CompileFramework;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateWithArgs;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;

import compiler.lib.template_library.Library;
import compiler.lib.template_library.IRTestClass;
import compiler.lib.template_library.Type;
import compiler.lib.template_library.PrimitiveType;
import compiler.lib.template_library.VectorAPIType;
import compiler.lib.template_library.Expression;
import compiler.lib.template_library.Value;

/**
 * TODO
 * See {@link TestFuzzExpression} for more explanation about the structure of the test.
 */
public class TestFuzzVectorAPI {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile("--add-modules=jdk.incubator.vector");

        // Object ret = p.xyz.InnterTest.main();
        Object ret = comp.invoke("p.xyz.InnerTest", "main", new Object[] {});
    }

    // Generate a source Java file as String
    public static String generate(CompileFramework comp) {
        // Create the info required for the test class.
        // It is imporant that we pass the classpath to the Test-VM, so that it has access
        // to all compiled classes.
        IRTestClass.Info info = new IRTestClass.Info(comp.getEscapedClassPathOfCompiledClasses(),
                                                     "p.xyz", "InnerTest",
                                                     List.of("compiler.lib.generators.*",
                                                             "compiler.lib.verify.*",
                                                             "jdk.incubator.vector.*"),
                                                     List.of("--add-modules=jdk.incubator.vector"));

        // Example 1:
        // We only use the "expression" once, and so we can conveniently just run it with
        // random arguments. Those may also generate their own fields under the hood.
        //
        // Note: we put the expression in a separate Template so that the method and class
        // hook are both already set before we call "expression.withRandomArgs", and so that
        // we know we can generate fields and local variables.
        var template1Body = Template.make("type", (Type type)-> {
            Expression expression = Expression.make(type, Type.ALL_BUILTIN_TYPES, 4);
            return body(
                """
                    try {
                """,
                "        return ", expression.withRandomArgs(), ";\n",
                expression.exceptions().stream().map(exception ->
                    "} catch (" + exception + " e) { return e;\n"
                ).toList(),
                """
                    } finally {
                        // Just javac is happy if there are no exceptions to catch.
                    }
                """
            );
        });
        var template1 = Template.make("type", (Type type)-> {
            return body(
                """
                // --- $test start ---
                // Using $GOLD
                // type: #type
                """,
                Library.CLASS_HOOK.set(
                    """

                    static final Object $GOLD = $test();

                    @Test
                    public static Object $test() {
                    """,
                    Library.METHOD_HOOK.set(
                        template1Body.withArgs(type)
                    ),
                    """
                    }

                    @Check(test = "$test")
                    public static void $check(Object result) {
                        Verify.checkEQ(result, $GOLD);
                    }

                    // --- $test end   ---
                    """
                )
            );
        });

        var defineArray = Template.make("type", "name", "size", (Type type, String name, Integer size) -> body(
            """
            public static #type[] #name = fill(new #type[#size]);
            """
        ));

        // Example 2:
        // We use the expression to iterate over arrays, loading from a set of input arrays,
        // and storing to an output array.
        var template2 = Template.make("type", (VectorAPIType type)-> {
            int size = 1000;
            Expression expression = Expression.make(type, Type.ALL_BUILTIN_TYPES, 4);
            List<Type> types = expression.argTypes();
            List<TemplateWithArgs> arrayDefinitions = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            for (int i = 0; i < types.size(); i++) {
                String name = $("array") + "_" + i;
                Type argType = types.get(i);
                PrimitiveType elementType = null;
                if (argType instanceof PrimitiveType t) {
                    elementType = t;
                    args.add(name + "[0]");
                } else if (argType instanceof VectorAPIType vt) {
                    elementType = vt.elementType;
                    args.add(vt.vectorType + ".fromArray(" + vt.species + ", " + name + ", 0)");
                } else {
                    throw new RuntimeException("Not handled: " + argType);
                }
                arrayDefinitions.add(defineArray.withArgs(elementType, name, size));
            }
            return body(
                let("size", size),
                let("elementType", type.elementType),
                """
                // --- $test start ---
                // Using $GOLD
                // type: #type
                // elementType: #elementType
                """,
                Library.CLASS_HOOK.set(
                    """
                    // Input arrays:
                    """,
                    arrayDefinitions,
                    """

                    static final Object $GOLD = $test();

                    @Test
                    public static Object $test() {
                        try {
                            #elementType[] out = new #elementType[#size];
                    """,
                    "        ", expression.withArgs(args), ".intoArray(out, 0);\n",
                    """
                            return out;
                    """,
                    expression.exceptions().stream().map(exception ->
                        "} catch (" + exception + " e) { return e;\n"
                    ).toList(),
                    """
                        } finally {
                            // Just javac is happy if there are no exceptions to catch.
                        }
                    }

                    @Check(test = "$test")
                    public static void $check(Object result) {
                        Verify.checkEQ(result, $GOLD);
                    }

                    // --- $test end   ---
                    """
                )
            );
        });

        // TODO: add scalar output case for reductions
        // TODO: add register stress test
        // TODO: add load/store stress test

        // Now use the templates and add them into the IRTestClass.
        List<TemplateWithArgs> templates = new ArrayList<>();
        templates.add(Library.arrayFillMethods());
        for (VectorAPIType type : Type.VECTOR_API_TYPES) {
            for (int i = 0; i < 10; i++) { templates.add(template1.withArgs(type)); }
            for (int i = 0; i < 10; i++) { templates.add(template2.withArgs(type)); }
        }
        return IRTestClass.TEMPLATE.withArgs(info, templates).render();
    }
}
