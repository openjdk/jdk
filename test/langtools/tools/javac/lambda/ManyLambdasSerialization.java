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

/**
 * @test
 * @bug 8381812
 * @summary Check that serializable lambda desugaring can handle many serializable lambdas
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @compile ManyLambdasSerialization.java
 * @build toolbox.ToolBox
 * @run junit ManyLambdasSerialization
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.Task;
import toolbox.ToolBox;

public class ManyLambdasSerialization {
    private static final int LAMBDA_COUNT = 1000;
    private final ToolBox tb = new ToolBox();

    @Test
    public void testManySerializableLambdasDifferentImplMethodName() throws IOException {
        List<String> lambdaMethods = new ArrayList<>();
        List<String> tests = new ArrayList<>();

        for (int i = 0; i < LAMBDA_COUNT; i++) {
            String index = Integer.toString(i);

            lambdaMethods.add("""
                                  private static Supplier<Integer> create${INDEX}() {
                                      return (Supplier<Integer> & Serializable) () -> ${INDEX};
                                  }
                              """.replace("${INDEX}", index));
            tests.add("        runTest(${INDEX}, create${INDEX}());\n".replace("${INDEX}", index));
        }

        StringBuilder code = new StringBuilder();
        code.append("""
                    import java.io.*;
                    import java.util.function.Supplier;
                    class Test {
                    """);

        lambdaMethods.forEach(code::append);

        code.append("""
                        private static void runTest(int expectedResult, Supplier<Integer> instance) throws Exception {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                                oos.writeObject(instance);
                                oos.close();
                            }
                            try (ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
                                 ObjectInputStream ois = new ObjectInputStream(in)) {
                                int actual = ((Supplier<Integer>) ois.readObject()).get();
                                if (expectedResult != actual) {
                                    throw new AssertionError("Expected: " + expectedResult + ", actual: " + actual);
                                }
                            }
                        }

                        public static void main() throws Exception {
                    """);

        tests.forEach(code::append);

        code.append("""
                            System.err.println("OK");
                        }
                    }
                    """);

        new JavacTask(tb)
                .sources(code.toString())
                .outdir(".")
                .run()
                .writeAll();

        List<String> output = new JavaTask(tb)
                .classpath(".")
                .className("Test")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDERR);

        tb.checkEqual(List.of("OK"), output);
    }

//    @Test The current deserialization does not support too many serialized lambdas with the same implementation method name
    public void testManySerializableLambdasSameImplMethodName() {
        List<String> lambdaMethods = new ArrayList<>();
        List<String> tests = new ArrayList<>();

        for (int i = 0; i < LAMBDA_COUNT; i++) {
            String index = Integer.toString(i);

            lambdaMethods.add("""
                                  private static Function<Box${INDEX}, Box${INDEX}> create${INDEX}() {
                                      return (Function<Box${INDEX}, Box${INDEX}> & Serializable) Test::id;
                                  }
                                  record Box${INDEX}(int i) {}
                              """.replace("${INDEX}", index));
            tests.add("""
                              runTest(create${INDEX}(), t -> {
                                  int expectedResult = ${INDEX};
                                  //in case of a bad deserialization, the implicit cast here would fail:
                                  int actual = t.apply(new Box${INDEX}(expectedResult)).i();
                                  if (expectedResult != actual) {
                                      throw new AssertionError("Expected: " + expectedResult + ", actual: " + actual);
                                  }
                              });
                      """.replace("${INDEX}", index));
        }

        StringBuilder code = new StringBuilder();
        code.append("""
                    import java.io.*;
                    import java.util.function.*;
                    class Test {
                    """);

        lambdaMethods.forEach(code::append);

        code.append("""
                        private static <T> T id(T t) { return t; }
                        private static <T> void runTest(Function<T, T> testInstance, Consumer<Function<T, T>> checker) throws Exception {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                                oos.writeObject(testInstance);
                                oos.close();
                            }
                            try (ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
                                 ObjectInputStream ois = new ObjectInputStream(in)) {
                                checker.accept((Function<T, T>) ois.readObject());
                            }
                        }

                        public static void main() throws Exception {
                    """);

        tests.forEach(code::append);

        code.append("""
                            System.err.println("OK");
                        }
                    }
                    """);

        new JavacTask(tb)
                .sources(code.toString())
                .outdir(".")
                .run()
                .writeAll();

        List<String> output = new JavaTask(tb)
                .classpath(".")
                .className("Test")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDERR);

        tb.checkEqual(List.of("OK"), output);
    }

    @Test
    public void testManySerializableLambdasCapturing() {
        List<String> lambdaMethods = new ArrayList<>();
        List<String> tests = new ArrayList<>();

        for (int i = 0; i < LAMBDA_COUNT; i++) {
            String index = Integer.toString(i);
            int capturedValues = 10;

            lambdaMethods.add("""
                                  private static Supplier<Integer> create${INDEX}() {
                                      ${DECLARATIONS}
                                      return (Supplier<Integer> & Serializable) () -> ${CAPTURED};
                                  }
                              """.replace("${INDEX}", index)
                                 .replace("${DECLARATIONS}", Stream.iterate(0, v -> v + 1).limit(capturedValues).map(v -> "int v" + v + " = " + index + ";\n").collect(Collectors.joining()))
                                 .replace("${CAPTURED}", Stream.iterate(0, v -> v + 1).limit(capturedValues).map(v -> "v" + v).collect(Collectors.joining(" + "))));
            tests.add("        runTest(${EXPECTED}, create${INDEX}());\n".replace("${INDEX}", index).replace("${EXPECTED}", String.valueOf(capturedValues * i)));
        }

        StringBuilder code = new StringBuilder();
        code.append("""
                    import java.io.*;
                    import java.util.function.Supplier;
                    class Test {
                    """);

        lambdaMethods.forEach(code::append);

        code.append("""
                        private static void runTest(int expectedResult, Supplier<Integer> instance) throws Exception {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                                oos.writeObject(instance);
                                oos.close();
                            }
                            try (ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
                                 ObjectInputStream ois = new ObjectInputStream(in)) {
                                int actual = ((Supplier<Integer>) ois.readObject()).get();
                                if (expectedResult != actual) {
                                    throw new AssertionError("Expected: " + expectedResult + ", actual: " + actual);
                                }
                            }
                        }

                        public static void main() throws Exception {
                    """);

        tests.forEach(code::append);

        code.append("""
                            System.err.println("OK");
                        }
                    }
                    """);

        new JavacTask(tb)
                .sources(code.toString())
                .outdir(".")
                .run()
                .writeAll();

        List<String> output = new JavaTask(tb)
                .classpath(".")
                .className("Test")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDERR);

        tb.checkEqual(List.of("OK"), output);
    }

    @Test
    public void testVeryManySerializableLambdasCapturing() {
        List<String> lambdaMethods = new ArrayList<>();
        List<String> tests = new ArrayList<>();

        for (int i = 0; i < LAMBDA_COUNT; i++) {
            String index = Integer.toString(i);
            int capturedValues = 200;

            lambdaMethods.add("""
                                  private static Supplier<Integer> create${INDEX}() {
                                      ${DECLARATIONS}
                                      return (Supplier<Integer> & Serializable) () -> ${CAPTURED};
                                  }
                              """.replace("${INDEX}", index)
                                 .replace("${DECLARATIONS}", Stream.iterate(0, v -> v + 1).limit(capturedValues).map(v -> "int v" + v + " = " + index + ";\n").collect(Collectors.joining()))
                                 .replace("${CAPTURED}", Stream.iterate(0, v -> v + 1).limit(capturedValues).map(v -> "v" + v).collect(Collectors.joining(" + "))));
            tests.add("        runTest(${EXPECTED}, create${INDEX}());\n".replace("${INDEX}", index).replace("${EXPECTED}", String.valueOf(capturedValues * i)));
        }

        StringBuilder code = new StringBuilder();
        code.append("""
                    import java.io.*;
                    import java.util.function.Supplier;
                    class Test {
                    """);

        lambdaMethods.forEach(code::append);

        code.append("""
                        private static void runTest(int expectedResult, Supplier<Integer> instance) throws Exception {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                                oos.writeObject(instance);
                                oos.close();
                            }
                            try (ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
                                 ObjectInputStream ois = new ObjectInputStream(in)) {
                                int actual = ((Supplier<Integer>) ois.readObject()).get();
                                if (expectedResult != actual) {
                                    throw new AssertionError("Expected: " + expectedResult + ", actual: " + actual);
                                }
                            }
                        }

                        public static void main() throws Exception {
                    """);

        tests.forEach(code::append);

        code.append("""
                            System.err.println("OK");
                        }
                    }
                    """);

        new JavacTask(tb)
                .sources(code.toString())
                .options("-XDdeserializableLambdaCaseCountLimit=15")
                .outdir(".")
                .run()
                .writeAll();

        List<String> output = new JavaTask(tb)
                .classpath(".")
                .className("Test")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDERR);

        tb.checkEqual(List.of("OK"), output);
    }
}
