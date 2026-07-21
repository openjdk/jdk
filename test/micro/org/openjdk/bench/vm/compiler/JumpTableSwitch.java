/*
 * Copyright 2026 Arm Limited and/or its affiliates.
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

package org.openjdk.bench.vm.compiler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;
import org.openjdk.bench.util.InMemoryJavaCompiler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 7, time = 3)
@Fork(3)
public class JumpTableSwitch {

    private static final int MASK = 1023;
    private static final boolean PRINT_GENERATED_SOURCE =
        Boolean.getBoolean("JumpTableSwitch.printGeneratedSource");

    private static final class GeneratedClassLoader extends ClassLoader {
        GeneratedClassLoader() {
            super(JumpTableSwitch.class.getClassLoader());
        }

        Class<?> define(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }

    @State(Scope.Thread)
    public static class InputState {
        private final int[] values = new int[MASK + 1];

        @Param({"4", "8", "9", "10", "12", "16", "24", "32", "64", "72",
                "80", "96", "112", "120", "128", "256", "512", "1024"})
        public int cases;

        @Param({"1", "10", "11"})
        public int step;

        @Param({"even", "defaultHeavy", "hotCase"})
        public String distribution;

        private MethodHandle switchMethod;
        private int index;

        @Setup
        public void setup() throws ReflectiveOperationException {
            int seed = 0x1234ABCD;
            if (step == 1) {
                fillDense(values, cases, seed);
            } else {
                fillSparse(values, cases, step, seed);
            }

            String className = "GeneratedSwitch" + cases + "Step" + step;
            String source = switchSource(className, cases, step);
            if (PRINT_GENERATED_SOURCE) {
                System.out.println("=== Generated benchmark source ===");
                System.out.println("cases=" + cases
                    + ", step=" + step
                    + ", distribution=" + distribution
                    + ", class=" + className);
                System.out.print(source);
                System.out.println("=== End generated benchmark source ===");
            }
            byte[] bytecode = InMemoryJavaCompiler.compile(className, source);
            Class<?> generatedClass = new GeneratedClassLoader().define(className, bytecode);
            switchMethod = MethodHandles.publicLookup().findStatic(generatedClass, "switchValue",
                MethodType.methodType(int.class, int.class));
            index = 0;
        }

        private int next() {
            int value = values[index];
            index = (index + 1) & MASK;
            return value;
        }

        private int fillDense(int[] values, int size, int seed) {
            for (int i = 0; i < values.length; i++) {
                seed = nextSeed(seed);
                int value = Integer.remainderUnsigned(seed, size);
                values[i] = switch (distribution) {
                    case "even" -> value;
                    case "defaultHeavy" -> ((seed & 3) == 0) ? value : size + value;
                    case "hotCase" -> ((seed & 3) == 0) ? value : 0;
                    default -> throw new IllegalStateException("unexpected distribution: " + distribution);
                };
            }
            return seed;
        }

        private int fillSparse(int[] values, int cases, int step, int seed) {
            for (int i = 0; i < values.length; i++) {
                seed = nextSeed(seed);
                int value = Integer.remainderUnsigned(seed, cases) * step;
                values[i] = switch (distribution) {
                    case "even" -> value;
                    case "defaultHeavy" -> ((seed & 3) == 0) ? value : (cases + Integer.remainderUnsigned(seed, cases)) * step;
                    case "hotCase" -> ((seed & 3) == 0) ? value : 0;
                    default -> throw new IllegalStateException("unexpected distribution: " + distribution);
                };
            }
            return seed;
        }

        private static int nextSeed(int seed) {
            return seed * 1664525 + 1013904223;
        }
    }

    private static String switchSource(String className, int cases, int step) {
        StringBuilder source = new StringBuilder()
            .append("public class ").append(className).append(" {\n")
            .append("  public static int switchValue(int value) {\n")
            .append("    return switch (value) {\n");
        for (int i = 0; i < cases; i++) {
            source.append("      case ").append(i * step).append(" -> ")
                .append(100000 - i).append(";\n");
        }
        return source.append("      default -> 0;\n")
            .append("    };\n")
            .append("  }\n")
            .append("}\n")
            .toString();
    }

    @Benchmark
    public void switchCase(InputState state, Blackhole blackhole) throws Throwable {
        MethodHandle switchMethod = state.switchMethod;
        for (int i = 0; i < MASK; i++) {
            blackhole.consume((int) switchMethod.invokeExact(state.next()));
        }
    }
}
