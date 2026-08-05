/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8389379
 * @key stress
 * @summary Test scalarized calls and entry points around calling convention limits.
 * @library /test/lib /
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:-UseBimorphicInlining
 *                   -XX:CompileCommand=dontinline,*GeneratedScalarizedCallingConventionLimits$ValueImpl*::m
 *                   -XX:CompileCommand=inline,*GeneratedScalarizedCallingConventionLimits$IdentityImpl*::m
 *                   ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=dontinline,*GeneratedScalarizedCallingConventionLimits*::m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+AbortVMOnCompilationFailure -XX:+VerifyOops -XX:+StressCodeBuffers -XX:+ForceUnreachable
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;

import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.scope;

public class TestScalarizedCallingConventionLimits {
    private static final String GENERATED_CLASS_NAME = "GeneratedScalarizedCallingConventionLimits";
    private static final int ITERATIONS = 100_000;
    private static final int MAX_ARGUMENT_COUNT = 30;
    private static final int MAX_OOP_RECEIVER_FIELD_COUNT = 70;

    private static String commaSeparated(int count, IntFunction<String> element) {
        return IntStream.range(0, count).mapToObj(element).collect(Collectors.joining(", "));
    }

    private static String lines(int count, IntFunction<String> element) {
        return IntStream.range(0, count).mapToObj(element).collect(Collectors.joining("\n"));
    }

    public static void main(String[] args) {
        CompileFramework compileFramework = new CompileFramework();
        compileFramework.addJavaSourceCode(GENERATED_CLASS_NAME, generateSource());
        compileFramework.compile("--enable-preview", "--source", System.getProperty("java.specification.version"));
        compileFramework.invoke(GENERATED_CLASS_NAME, "run", new Object[] {});
    }

    // Generate interfaces with value and identity-class implementations and a method
    // with a varying number of arguments to stress test the calling convention.
    private static final Template.OneArg<Integer> INTERFACE_CASE =
            Template.make("argumentCount", (Integer argumentCount) -> {
                String parameters = commaSeparated(argumentCount, i -> "Integer a" + i);
                String parameterNames = commaSeparated(argumentCount, i -> "a" + i);
                String arguments = commaSeparated(argumentCount, i -> Integer.toString(i + 1));

                return scope(
                        let("parameters", parameters),
                        let("parameterNames", parameterNames),
                        let("arguments", arguments),
                        """
                            interface I#argumentCount {
                                int m(#parameters);
                            }

                            static value class ValueImpl#argumentCount implements I#argumentCount {
                                int x, y, z;

                                ValueImpl#argumentCount(int x, int y, int z) {
                                    this.x = x;
                                    this.y = y;
                                    this.z = z;
                                }

                                @Override
                                public int m(#parameters) {
                                    return hash(x, y, z, hash(#parameterNames));
                                }
                            }

                            static class IdentityImpl#argumentCount implements I#argumentCount {
                                @Override
                                public int m(#parameters) {
                                    return hash(#parameterNames);
                                }
                            }

                            static void test#argumentCount() {
                                I#argumentCount value = new ValueImpl#argumentCount(42, 43, 44);
                                I#argumentCount identity = new IdentityImpl#argumentCount();
                                int argumentHash = hash(#arguments);
                                int valueHash = hash(42, 43, 44, argumentHash);
                                for (int i = 0; i < ITERATIONS; i++) {
                                    I#argumentCount receiver = (i & 1) == 0 ? value : identity;
                                    int actual = receiver.m(#arguments);
                                    int expected = (i & 1) == 0 ? valueHash : argumentHash;
                                    Asserts.assertEquals(expected, actual);
                                }
                            }

                        """);
            });

    // Generate methods with a value class receiver with a varying number of oop fields to stress
    // test code buffers during nmethod entry point generation (oops need GC barriers etc.)
    private static final Template.OneArg<Integer> OOP_RECEIVER =
            Template.make("fieldCount", (Integer fieldCount) -> {
                String fields = lines(fieldCount, i -> "        Object f" + i + ";");
                String assignments = lines(fieldCount, i -> "            this.f" + i + " = values[" + i + "];");
                String arguments = commaSeparated(fieldCount, i -> "f" + i);

                return scope(
                        let("fields", fields),
                        let("assignments", assignments),
                        let("arguments", arguments),
                        """
                            static value class OopReceiver#fieldCount {
                        #fields

                                OopReceiver#fieldCount(Object[] values) {
                        #assignments
                                }

                                int m() {
                                    return hash(#arguments);
                                }
                            }

                            static void testOopReceiver#fieldCount() {
                                Object[] values = new Object[#fieldCount];
                                for (int i = 0; i < values.length; i++) {
                                    values[i] = i;
                                }
                                int valuesHash = hash(values);
                                OopReceiver#fieldCount receiver = new OopReceiver#fieldCount(values);
                                for (int i = 0; i < ITERATIONS; i++) {
                                    Asserts.assertEquals(valuesHash, receiver.m());
                                }
                            }

                        """);
            });

    private static String generateSource() {
        String interfaceTestCalls = lines(MAX_ARGUMENT_COUNT, i -> "        test" + i + "();");
        String oopReceiverTestCalls = lines(MAX_OOP_RECEIVER_FIELD_COUNT, i -> "        testOopReceiver" + i + "();");

        Template.ZeroArgs classTemplate = Template.make(() -> scope(
                let("generatedClassName", GENERATED_CLASS_NAME),
                let("iterations", ITERATIONS),
                """
                import jdk.test.lib.Asserts;

                public class #generatedClassName {
                    static final int ITERATIONS = #iterations;

                    static int hash(Object... values) {
                        int result = 1;
                        for (Object value : values) {
                            result = 31 * result + (int)value;
                        }
                        return result;
                    }

                """,
                IntStream.range(0, MAX_ARGUMENT_COUNT).mapToObj(INTERFACE_CASE::asToken).toList(),
                IntStream.range(0, MAX_OOP_RECEIVER_FIELD_COUNT).mapToObj(OOP_RECEIVER::asToken).toList(),
                let("interfaceTestCalls", interfaceTestCalls),
                let("oopReceiverTestCalls", oopReceiverTestCalls),
                """
                    public static void run() {
                #interfaceTestCalls
                #oopReceiverTestCalls
                    }
                }
                """
        ));
        return classTemplate.render();
    }
}

