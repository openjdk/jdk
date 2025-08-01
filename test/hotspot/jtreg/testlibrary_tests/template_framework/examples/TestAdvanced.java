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
 * @bug 8344942
 * @summary Test simple use of Templates with the Compile Framework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run main template_framework.examples.TestAdvanced
 */

package template_framework.examples;

import java.util.List;
import jdk.test.lib.Utils;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

/**
 * This is a basic example for Templates, using them to cover a list of test variants.
 * <p>
 * The "@compile" command for JTREG is required so that the frameworks used in the Template code
 * are compiled and available for the Test-VM.
 * <p>
 * Additionally, we must set the classpath for the Test-VM, so that it has access to all compiled
 * classes (see {@link CompileFramework#getEscapedClassPathOfCompiledClasses}).
 */
public class TestAdvanced {
    public static final RestrictableGenerator<Integer> GEN_BYTE = Generators.G.safeRestrict(Generators.G.ints(), Byte.MIN_VALUE, Byte.MAX_VALUE);
    public static final RestrictableGenerator<Integer> GEN_CHAR = Generators.G.safeRestrict(Generators.G.ints(), Character.MIN_VALUE, Character.MAX_VALUE);
    public static final RestrictableGenerator<Integer> GEN_SHORT = Generators.G.safeRestrict(Generators.G.ints(), Short.MIN_VALUE, Short.MAX_VALUE);
    public static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    public static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    public static final Generator<Float> GEN_FLOAT = Generators.G.floats();
    public static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile();

        // Object ret = p.xyz.InnerTest.main();
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {});
    }

    interface MyGenerator {
        Object next();
    }

    record Type(String name, MyGenerator generator, List<String> operators) {}

    // Generate a source Java file as String
    public static String generate(CompileFramework comp) {

        // The test template:
        // - For a chosen type, operator, and generator.
        // - The variable name "GOLD" and the test name "test" would get conflicts
        //   if we instantiate the template multiple times. Thus, we use the $ prefix
        //   so that the Template Framework can replace the names and make them unique
        //   for each Template instantiation.
        // - The GOLD value is computed at the beginning, hopefully by the interpreter.
        // - The test method is eventually compiled, and the values are verified by the
        //   check method.
        var testTemplate = Template.make("typeName", "operator", "generator", (String typeName, String operator, MyGenerator generator) -> body(
            let("con1", generator.next()),
            let("con2", generator.next()),
            """
            // #typeName #operator #con1 #con2
            public static #typeName $GOLD = $test();

            @Test
            public static #typeName $test() {
                return (#typeName)(#con1 #operator #con2);
            }

            @Check(test = "$test")
            public static void $check(#typeName result) {
                Verify.checkEQ(result, $GOLD);
            }
            """
        ));

        // Template for the Class.
        var classTemplate = Template.make("types", (List<Type> types) -> body(
            let("classpath", comp.getEscapedClassPathOfCompiledClasses()),
            """
            package p.xyz;

            import compiler.lib.ir_framework.*;
            import compiler.lib.verify.*;

            public class InnerTest {
                public static void main() {
                    TestFramework framework = new TestFramework(InnerTest.class);
                    // Set the classpath, so that the TestFramework test VM knows where
                    // the CompileFramework put the class files of the compiled source code.
                    framework.addFlags("-classpath", "#classpath");
                    framework.start();
                }

            """,
            // Call the testTemplate for each type and operator, generating a
            // list of lists of TemplateToken:
            types.stream().map((Type type) ->
                type.operators().stream().map((String operator) ->
                    testTemplate.asToken(type.name(), operator, type.generator())).toList()
            ).toList(),
            """
            }
            """
        ));

        // For each type, we choose a list of operators that do not throw exceptions.
        List<Type> types = List.of(
            new Type("byte",   GEN_BYTE::next,   List.of("+", "-", "*", "&", "|", "^")),
            new Type("char",   GEN_CHAR::next,   List.of("+", "-", "*", "&", "|", "^")),
            new Type("short",  GEN_SHORT::next,  List.of("+", "-", "*", "&", "|", "^")),
            new Type("int",    GEN_INT::next,    List.of("+", "-", "*", "&", "|", "^")),
            new Type("long",   GEN_LONG::next,   List.of("+", "-", "*", "&", "|", "^")),
            new Type("float",  GEN_FLOAT::next,  List.of("+", "-", "*", "/")),
            new Type("double", GEN_DOUBLE::next, List.of("+", "-", "*", "/"))
        );

        // Use the template with one argument and render it to a String.
        return classTemplate.render(types);
    }
}
