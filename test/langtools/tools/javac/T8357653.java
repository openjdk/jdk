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
 * @bug 8357653
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @summary Inner classes of type parameters emitted as raw types in signatures
 * @build toolbox.ToolBox toolbox.JavapTask
 * @run main T8357653
 */
import toolbox.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

public class T8357653 extends TestRunner {
    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new T8357653().runTests();
    }

    T8357653() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testCompilation(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
            """
            package test;
            public class Test {
                static abstract class Getters<X> {
                  abstract class Getter {
                      abstract X get();
                  }
                }

                static class Usage1<T, G extends Getters<T>> {
                  public T test(G.Getter getter) {
                      return getter.get();
                  }
                }

                static class Usage2<T, U extends Getters<T>, G extends U> {
                  public T test(G.Getter getter) {
                      return getter.get();
                  }
                }

                static class Usage3<T, U extends T, G extends Getters<T>> {
                  public T test(G.Getter getter) {
                      return getter.get();
                  }
                }

                class G2<K> extends Getters<K> {}
                static class Usage4<M, L extends G2<M>> {
                  M test(L.Getter getter) {
                      return getter.get();
                  }
                }
            }
            """);

        Files.createDirectories(classes);

        {
            new JavacTask(tb)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.SUCCESS)
                    .writeAll();
        }
    }

    @Test
    public void testCompilationArray(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                """
                package test;
                public class Test {
                    static abstract class Getters<X> {
                      abstract class Getter {
                          abstract X get();
                      }
                    }

                    static class Usage1<T, G extends Getters<T>> {
                      public T test(G.Getter[] getter) {
                          return getter[0].get();
                      }
                    }

                    static class Usage2<T, U extends Getters<T>, G extends U> {
                      public T test(G.Getter[] getter) {
                          return getter[0].get();
                      }
                    }

                    static class Usage3<T, U extends T, G extends Getters<T>> {
                      public T test(G.Getter[] getter) {
                          return getter[0].get();
                      }
                    }

                    class G2<K> extends Getters<K> {}
                    static class Usage4<M, L extends G2<M>> {
                      M test(L.Getter[] getter) {
                          return getter[0].get();
                      }
                    }
                }
                """);

        Files.createDirectories(classes);

        {
            new JavacTask(tb)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.SUCCESS)
                    .writeAll();
        }
    }

    @Test
    public void testErasureViaJavap(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                """
                package test;
                public class Test {
                    static abstract class Getters<X> {
                      abstract class Getter {
                          abstract X get();
                      }
                    }

                    static class Usage1<T, G extends Getters<T>> {
                      public T test(G.Getter getter) {
                          return getter.get();
                      }
                    }
                }
                """);

        Files.createDirectories(classes);

        {
            new JavacTask(tb)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.SUCCESS)
                    .writeAll();

            String javapOut = new JavapTask(tb)
                    .options("-v")
                    .classpath(classes.toString())
                    .classes("test.Test$Usage1")
                    .run()
                    .getOutput(Task.OutputKind.DIRECT);

            if (!javapOut.contains("Signature: #21                          // <T:Ljava/lang/Object;G:Ltest/Test$Getters<TT;>;>Ljava/lang/Object;"))
                throw new AssertionError("Wrongly erased generated signature:\n" + javapOut);
        }
    }

    @Test
    public void testGenericsViaReflection(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                """
                package test;
                import java.lang.reflect.*;
                import java.util.Arrays;

                public class Test {
                    public static void main(String[] args) throws Throwable {
                        new test.Test().test();
                    }

                    public void test() throws Throwable  {
                        var m = getClass().getDeclaredMethod("getOwner", Test.Base.Handler.class);
                        System.out.println(m);
                        System.out.println(Arrays.toString(m.getGenericParameterTypes()));
                        System.out.println(m.getGenericReturnType());
                    }

                     <S extends Base<S>> S getOwner(S.Handler handler) {
                        return handler.owner();
                     }

                     abstract class Base<S extends Base<S>> {
                        class Handler {
                            @SuppressWarnings("unchecked")
                            S owner() {
                                return (S) Base.this;
                            }
                        }
                     }
                }
                """);

        Files.createDirectories(classes);

        {
            new JavacTask(tb)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.SUCCESS)
                    .writeAll();

            var out = new JavaTask(tb)
                    .classpath(classes.toString())
                    .className("test.Test")
                    .run()
                    .writeAll()
                    .getOutput(Task.OutputKind.STDOUT);

            var expectedOut = """
                test.Test$Base test.Test.getOwner(test.Test$Base$Handler)
                [test.Test$Base<S>$Handler]
                S
                """;

            containsOrdered(out, expectedOut, "Wrongly erased generated signature:\n");
        }
    }

    private static void containsOrdered(String expected, String actual, String message) {
        List<String> expectedLines = expected.lines().map(s -> s.strip()).toList();
        Stream<String> actualLines = actual.lines().map(s -> s.strip());

        if (!actualLines.gather(Gatherers.windowSliding(expectedLines.size())).anyMatch(window -> window.equals(expectedLines)))
            throw new AssertionError(message + actual);
    }
}
