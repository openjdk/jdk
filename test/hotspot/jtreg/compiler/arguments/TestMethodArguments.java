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

/**
 * @test
 * @bug 8325467
 * @summary Ensure that C2 can compile methods up to the maximum
 *          number of parameters (according to the JVM spec).
 * @library /test/lib /
 * @requires vm.compMode != "Xcomp"
 * @run driver/timeout=1000 compiler.arguments.TestMethodArguments
 */

package compiler.arguments;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.PrimitiveType;

public class TestMethodArguments {

    static final int MIN = 0;
    static final int MAX = 255;
    static final int INPUT_SIZE = 100;

    public static Template.ZeroArgs generateTest(PrimitiveType type, int numberOfArguments) {
        String arguments = IntStream.range(0, numberOfArguments)
                .mapToObj(i -> "input[" + i + "]")
                .collect(Collectors.joining(", "));
        String parameters = IntStream.range(0, numberOfArguments)
                .mapToObj(i -> type.name() + " x" + i)
                .collect(Collectors.joining(", "));
        String sum = numberOfArguments == 0 ? "0"
                : IntStream.range(0, numberOfArguments)
                        .mapToObj(i -> "x" + i)
                        .collect(Collectors.joining(" + "));
        return Template.make(() -> Template.scope(
                Template.let("type", type.name()),
                Template.let("boxedType", type.boxedTypeName()),
                Template.let("arguments", arguments),
                Template.let("parameters", parameters),
                Template.let("sum", sum),
                Template.let("inputSize", INPUT_SIZE),
                Template.let("numberOfArguments", numberOfArguments),
                """
                static #boxedType[][] $inputs = generateInput(generator#boxedType, new #boxedType[#inputSize][#numberOfArguments]);
                static int $nextInput = 0;
                static #type[] $golden = $init();

                public static #type[] $init() {
                    #type[] golden = new #type[$inputs.length];
                    for (int i = 0; i < golden.length; ++i) {
                        #boxedType[] input = $inputs[i];
                        golden[i] = $test(#arguments);
                    }
                    return golden;
                }

                @Setup
                public static Object[] $setup() {
                    #boxedType[] input = $inputs[$nextInput];
                    return new Object[]{#arguments};
                }

                @Test
                @Arguments(setup = "$setup")
                public static #type $test(#parameters) {
                    return #sum;
                }

                @Check(test = "$test")
                public static void $check(#type res) {
                    if (res != $golden[$nextInput]) {
                        throw new RuntimeException("wrong result " + res + "!=" + $golden[$nextInput]);
                    }
                    $nextInput = ($nextInput + 1) % $inputs.length;
                }
                """));
    }

    public static String generate(CompileFramework comp) {
        List<Object> tests = new LinkedList<>();
        for (int i = MIN; i <= MAX; ++i) {
            tests.add(generateTest(CodeGenerationDataNameType.ints(), i).asToken());
            tests.add(generateTest(CodeGenerationDataNameType.floats(), i).asToken());
            // Longs and doubles take up double as much space in the parameter list as other
            // primitive types (e.g., int). We therefore have to divide by two to fill up
            // the same amount of space as for ints and floats.
            tests.add(generateTest(CodeGenerationDataNameType.longs(), i / 2).asToken());
            tests.add(generateTest(CodeGenerationDataNameType.doubles(), i / 2).asToken());
        }
        return Template.make(() -> Template.scope(
                Template.let("classpath", comp.getEscapedClassPathOfCompiledClasses()),
                """
                import java.util.Arrays;
                import java.util.stream.*;
                import compiler.lib.generators.*;
                import compiler.lib.ir_framework.*;
                import compiler.lib.template_framework.library.*;

                public class InnerTest {

                    static RestrictableGenerator<Integer> generatorInteger = Generators.G.uniformInts();
                    static RestrictableGenerator<Long> generatorLong = Generators.G.uniformLongs();
                    static RestrictableGenerator<Float> generatorFloat = Generators.G.uniformFloats();
                    static RestrictableGenerator<Double> generatorDouble = Generators.G.uniformDoubles();

                    public static void main() {
                        TestFramework framework = new TestFramework(InnerTest.class);
                        framework.addFlags("-classpath", "#classpath");
                        framework.start();
                    }

                    public static <T> T[][] generateInput(Generator<T> t, T[][] array) {
                        for (int i = 0; i < array.length; i++) {
                            for (int j = 0; j < array[i].length; j++) {
                                array[i][j] = t.next();
                            }
                        }
                        return array;
                    }
                """,
                tests,
                """
                }
                """)).render();
    }

    public static void main(String[] args) {
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("InnerTest", generate(comp));
        comp.compile();
        comp.invoke("InnerTest", "main", new Object[] {});
    }
}
