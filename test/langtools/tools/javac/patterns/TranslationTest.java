/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8291769 8326129
 * @summary Check expected translation of various pattern related constructs
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.JavaTask
 * @run main TranslationTest
*/

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.TransPatterns;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCSwitchExpression;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import toolbox.TestRunner;
import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class TranslationTest extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new TranslationTest().runTests();
    }

    TranslationTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testSimple(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public record Box(Object o) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(Object obj) {
                       return switch (obj) {
                           case Box(String s) -> 0;
                           case Box(Integer i) -> 0;
                           case Box(Number n) -> 0;
                           case Box(CharSequence cs) -> 0;

                           default -> -1;
                       };
                   }
               }
               """,
               toplevel -> printSwitchStructure(toplevel),
               """
               switch
                   case
                       switch
                           case
                           case
                           case
                           case
                           case ..., default
                   default
               """);
    }

    @Test
    public void testMultiComponent(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public record Pair(Object o1, Object o2) {}
                            """,
                            """
                            package lib;
                            public record Triplet(Object o1, Object o2, Object o3) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(Object obj) {
                       return switch (obj) {
                           case Pair(String c1, Pair(String o2, String s2)) -> 0;
                           case Pair(String c1, Pair(String o2, Integer s2)) -> 0;
                           case Pair(String c1, Pair(Integer o2, String s2)) -> 0;
                           case Pair(String c1, Pair(Integer o2, Integer s2)) -> 0;

                           case Pair(Integer c1, Pair(String o2, String s2)) -> 0;
                           case Pair(Integer c1, Pair(String o2, Integer s2)) -> 0;
                           case Pair(Integer c1, Pair(Integer o2, String s2)) -> 0;
                           case Pair(Integer c1, Pair(Integer o2, Integer s2)) -> 0;

                           default -> -1;
                       };
                   }
               }
               """,
               toplevel -> printSwitchStructure(toplevel),
               """
               switch
                   case
                       switch
                           case
                               switch
                                   case
                                       switch
                                           case
                                               switch
                                                   case
                                                   case
                                                   case ..., default
                                           case
                                               switch
                                                   case
                                                   case
                                                   case ..., default
                                           case ..., default
                                   case ..., default
                           case
                               switch
                                   case
                                       switch
                                           case
                                               switch
                                                   case
                                                   case
                                                   case ..., default
                                           case
                                               switch
                                                   case
                                                   case
                                                   case ..., default
                                           case ..., default
                                   case ..., default
                           case ..., default
                   default
               """);
    }

    @Test //JDK-8326129
    public void testRunWithNull(Path base) throws Exception {
        doRunTest(base,
                  new String[]{"""
                               package lib;
                               public record Box(Object o) {}
                               """},
                  """
                  import lib.*;
                  public class Test {
                      public static void main(String... args) {
                          System.err.println(new Test().test(new Box(null)));
                      }
                      private int test(Box b) {
                          return switch (b) {
                              case Box(Integer i) -> 0;
                              case Box(Object o) when check(o) -> 1;
                              case Box(Object o) -> 2;
                          };
                      }
                      private static int c;
                      private boolean check(Object o) {
                          System.err.println("check: " + o);
                          if (c++ > 10) throw new IllegalStateException();
                          return o != null;
                      }
                  }
                  """,
                  "check: null",
                  "2");
    }

    private void doTest(Path base, String[] libraryCode, String testCode,
                        Callback callback, String expectedOutput) throws IOException {
        Path current = base.resolve(".");
        Path libClasses = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        if (libraryCode.length != 0) {
            Path libSrc = current.resolve("lib-src");

            for (String code : libraryCode) {
                tb.writeJavaFiles(libSrc, code);
            }

            new JavacTask(tb)
                    .outdir(libClasses)
                    .files(tb.findJavaFiles(libSrc))
                    .run();
        }

        Path src = current.resolve("src");
        tb.writeJavaFiles(src, testCode);

        Path classes = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        List<String> output = new ArrayList<>();

        new JavacTask(tb)
            .options("-Xlint:-preview",
                     "--class-path", libClasses.toString(),
                     "-XDshould-stop.at=FLOW")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .callback(task -> {
                 Context ctx = ((JavacTaskImpl) task).getContext();

                 TestTransPatterns.preRegister(ctx, callback, output);
             })
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        if (output.size() != 1 || !expectedOutput.equals(output.get(0))) {
            throw new AssertionError("Unexpected output:\n" + output);
        }
    }

    private String printSwitchStructure(JCTree topLevel) {
        StringBuilder structure = new StringBuilder();

        new TreeScanner() {
            private static final int INDENT = 4;
            private int indent = 0;
            @Override
            public void visitSwitch(JCSwitch node) {
                int prevIndent = indent;
                appendLine("switch");
                try {
                    indent += INDENT;
                    super.visitSwitch(node);
                } finally {
                    indent = prevIndent;
                }
            }

            @Override
            public void visitSwitchExpression(JCSwitchExpression node) {
                int prevIndent = indent;
                appendLine("switch");
                try {
                    indent += INDENT;
                    super.visitSwitchExpression(node);
                } finally {
                    indent = prevIndent;
                }
            }
            @Override
            public void visitCase(JCCase node) {
                int prevIndent = indent;
                if (node.labels.size() == 1 && node.labels.head.hasTag(Tag.DEFAULTCASELABEL)) {
                    appendLine("default");
                } else if (node.labels.stream().anyMatch(l -> l.hasTag(Tag.DEFAULTCASELABEL))) {
                    appendLine("case ..., default");
                } else {
                    appendLine("case");
                }
                try {
                    indent += INDENT;
                    super.visitCase(node);
                } finally {
                    indent = prevIndent;
                }
            }
            private void appendLine(String what) {
                for (int i = 0; i < indent; i++) {
                    structure.append(' ');
                }
                structure.append(what);
                structure.append('\n');
            }
        }.scan(topLevel);

        return structure.toString();
    }

    public interface Callback {
        public String patternsTranslated(JCTree topLevel);
    }

    private static final class TestTransPatterns extends TransPatterns {

        public static void preRegister(Context ctx, Callback validator, List<String> output) {
            ctx.put(transPatternsKey, (Factory<TransPatterns>) c -> new TestTransPatterns(c, validator, output));
        }

        private final Callback callback;
        private final List<String> output;

        public TestTransPatterns(Context context, Callback callback, List<String> output) {
            super(context);
            this.callback = callback;
            this.output = output;
        }

        @Override
        public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
            JCTree result = super.translateTopLevelClass(env, cdef, make);
            output.add(callback.patternsTranslated(cdef));
            return result;
        }

    }

    private void doRunTest(Path base, String[] libraryCode, String testCode,
                           String... expectedOutput) throws IOException {
        Path current = base.resolve(".");
        Path libClasses = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        if (libraryCode.length != 0) {
            Path libSrc = current.resolve("lib-src");

            for (String code : libraryCode) {
                tb.writeJavaFiles(libSrc, code);
            }

            new JavacTask(tb)
                    .outdir(libClasses)
                    .files(tb.findJavaFiles(libSrc))
                    .run();
        }

        Path src = current.resolve("src");
        tb.writeJavaFiles(src, testCode);

        Path classes = current.resolve("classes");

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("-Xlint:-preview",
                     "--class-path", libClasses.toString())
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll();

        List<String> log = new JavaTask(tb)
            .classpath(libClasses.toString() + File.pathSeparatorChar + classes.toString())
            .classArgs("Test")
            .run()
            .getOutputLines(Task.OutputKind.STDERR);

        if (!List.of(expectedOutput).equals(log)) {
            throw new AssertionError("Expected: " + expectedOutput +
                                     "but got: " + log);
        }
    }
}
