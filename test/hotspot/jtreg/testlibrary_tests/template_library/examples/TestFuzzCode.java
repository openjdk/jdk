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
 * @summary Test the Template Library's generation of classes.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/generators/Generators.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run driver template_library.examples.TestFuzzCode
 */

package template_library.examples;

import java.util.List;
import java.util.ArrayList;

import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.compile_framework.CompileFramework;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateWithArgs;
import compiler.lib.template_framework.Name;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.addName;

import compiler.lib.template_library.Library;
import compiler.lib.template_library.Dispatcher;
import compiler.lib.template_library.IRTestClass;
import compiler.lib.template_library.Type;

/**
 * TODO
 * See {@link TestFuzzExpression} for more explanation about the structure of the test.
 */
public class TestFuzzCode {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile();

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
                                                             "compiler.lib.verify.*"),
                                                     List.of());

        var template1Body = Template.make("type", (Type type)-> body(
            // The "ret" variable captures the return value, which can be read / modified
            // by the random code.
            addName(new Name($("ret"), type, true, 10)),
            "#type $ret = ", type.con(), ";\n",
            new Dispatcher().templateWithArgs(),
            "return $ret;\n"
        ));
        var template1 = Template.make("type", (Type type) -> body(
                """
                // --- $test start ---
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
        ));

        // Now use the templates and add them into the IRTestClass.
        List<TemplateWithArgs> templates = new ArrayList<>();
        for (Type type : Type.PRIMITIVE_TYPES) {
            templates.add(template1.withArgs(type));
        }
        return IRTestClass.TEMPLATE.withArgs(info, templates).render();
    }
}
