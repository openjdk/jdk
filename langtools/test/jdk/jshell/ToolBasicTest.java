/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8143037 8142447 8144095 8140265 8144906 8146138 8147887 8147886 8148316 8148317 8143955
 * @summary Tests for Basic tests for REPL tool
 * @requires os.family != "solaris"
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @library /tools/lib
 * @ignore 8139873
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build KullaTesting TestingInputStream Compiler
 * @run testng/timeout=600 ToolBasicTest
 */

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test
public class ToolBasicTest extends ReplToolTesting {

    public void elideStartUpFromList() {
        test(
                (a) -> assertCommandOutputContains(a, "123", "type int"),
                (a) -> assertCommandCheckOutput(a, "/list", (s) -> {
                    int cnt;
                    try (Scanner scanner = new Scanner(s)) {
                        cnt = 0;
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine();
                            if (!line.trim().isEmpty()) {
                                ++cnt;
                            }
                        }
                    }
                    assertEquals(cnt, 1, "Expected only one listed line");
                })
        );
    }

    public void elideStartUpFromSave() throws IOException {
        Compiler compiler = new Compiler();
        Path path = compiler.getPath("myfile");
        test(
                (a) -> assertCommandOutputContains(a, "123", "type int"),
                (a) -> assertCommand(a, "/save " + path.toString(), "")
        );
        try (Stream<String> lines = Files.lines(path)) {
            assertEquals(lines.count(), 1, "Expected only one saved line");
        }
    }

    public void testInterrupt() {
        ReplTest interrupt = (a) -> assertCommand(a, "\u0003", "");
        for (String s : new String[] { "", "\u0003" }) {
            test(false, new String[]{"-nostartup"},
                    (a) -> assertCommand(a, "int a = 2 +" + s, ""),
                    interrupt,
                    (a) -> assertCommand(a, "int a\u0003", ""),
                    (a) -> assertCommand(a, "int a = 2 + 2\u0003", ""),
                    (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                    (a) -> evaluateExpression(a, "int", "2", "2"),
                    (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                    (a) -> assertCommand(a, "void f() {", ""),
                    (a) -> assertCommand(a, "int q = 10;" + s, ""),
                    interrupt,
                    (a) -> assertCommand(a, "void f() {}\u0003", ""),
                    (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                    (a) -> assertMethod(a, "int f() { return 0; }", "()int", "f"),
                    (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                    (a) -> assertCommand(a, "class A {" + s, ""),
                    interrupt,
                    (a) -> assertCommand(a, "class A {}\u0003", ""),
                    (a) -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                    (a) -> assertClass(a, "interface A {}", "interface", "A"),
                    (a) -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                    (a) -> assertCommand(a, "import java.util.stream." + s, ""),
                    interrupt,
                    (a) -> assertCommand(a, "import java.util.stream.\u0003", ""),
                    (a) -> assertCommandCheckOutput(a, "/imports", assertImports()),
                    (a) -> assertImport(a, "import java.util.stream.Stream", "", "java.util.stream.Stream"),
                    (a) -> assertCommandCheckOutput(a, "/imports", assertImports())
            );
        }
    }

    private final Object lock = new Object();
    private PrintWriter out;
    private boolean isStopped;
    private Thread t;
    private void assertStop(boolean after, String cmd, String output) {
        if (!after) {
            isStopped = false;
            StringWriter writer = new StringWriter();
            out = new PrintWriter(writer);
            setCommandInput(cmd + "\n");
            t = new Thread(() -> {
                try {
                    // no chance to know whether cmd is being evaluated
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                }
                int i = 1;
                int n = 30;
                synchronized (lock) {
                    do {
                        setCommandInput("\u0003");
                        if (!isStopped) {
                            out.println("Not stopped. Try again: " + i);
                            try {
                                lock.wait(1000);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    } while (i++ < n && !isStopped);
                    if (!isStopped) {
                        System.err.println(writer.toString());
                        fail("Evaluation was not stopped: '" + cmd + "'");
                    }
                }
            });
            t.start();
        } else {
            synchronized (lock)  {
                out.println("Evaluation was stopped successfully: '" + cmd + "'");
                isStopped = true;
                lock.notify();
            }
            try {
                t.join();
                t = null;
            } catch (InterruptedException ignored) {
            }
            assertOutput(getCommandOutput(), "", "command");
            assertOutput(getCommandErrorOutput(), "", "command error");
            assertOutput(getUserOutput().trim(), output, "user");
            assertOutput(getUserErrorOutput(), "", "user error");
        }
    }

    public void testStop() {
        test(
                (a) -> assertStop(a, "while (true) {}", "Killed."),
                (a) -> assertStop(a, "while (true) { try { Thread.sleep(100); } catch (InterruptedException ex) { } }", "Killed.")
        );
    }

    @Test(enabled = false) // TODO 8130450
    public void testRerun() {
        test(false, new String[] {"-nostartup"},
                (a) -> assertCommand(a, "/0", "|  No such command or snippet id: /0\n|  Type /help for help."),
                (a) -> assertCommand(a, "/5", "|  No such command or snippet id: /5\n|  Type /help for help.")
        );
        String[] codes = new String[] {
                "int a = 0;", // var
                "class A {}", // class
                "void f() {}", // method
                "bool b;", // active failed
                "void g() { h(); }", // active corralled
        };
        List<ReplTest> tests = new ArrayList<>();
        for (String s : codes) {
            tests.add((a) -> assertCommand(a, s, null));
        }
        for (int i = 0; i < codes.length; ++i) {
            final int finalI = i;
            Consumer<String> check = (s) -> {
                String[] ss = s.split("\n");
                assertEquals(ss[0], codes[finalI]);
                assertTrue(ss.length > 1, s);
            };
            tests.add((a) -> assertCommandCheckOutput(a, "/" + (finalI + 1), check));
        }
        for (int i = 0; i < codes.length; ++i) {
            final int finalI = i;
            Consumer<String> check = (s) -> {
                String[] ss = s.split("\n");
                assertEquals(ss[0], codes[codes.length - finalI - 1]);
                assertTrue(ss.length > 1, s);
            };
            tests.add((a) -> assertCommandCheckOutput(a, "/-" + (finalI + 1), check));
        }
        tests.add((a) -> assertCommandCheckOutput(a, "/!", assertStartsWith("void g() { h(); }")));
        test(false, new String[]{"-nostartup"},
                tests.toArray(new ReplTest[tests.size()]));
    }

    public void test8142447() {
        Function<String, BiFunction<String, Integer, ReplTest>> assertRerun = cmd -> (code, assertionCount) ->
                (a) -> assertCommandCheckOutput(a, cmd, s -> {
                            String[] ss = s.split("\n");
                            assertEquals(ss[0], code);
                            loadVariable(a, "int", "assertionCount", Integer.toString(assertionCount), Integer.toString(assertionCount));
                        });
        ReplTest assertVariables = (a) -> assertCommandCheckOutput(a, "/v", assertVariables());

        Compiler compiler = new Compiler();
        Path startup = compiler.getPath("StartupFileOption/startup.txt");
        compiler.writeToFile(startup, "int assertionCount = 0;\n" + // id: s1
                "void add(int n) { assertionCount += n; }");
        test(new String[]{"-startup", startup.toString()},
                (a) -> assertCommand(a, "add(1)", ""), // id: 1
                (a) -> assertCommandCheckOutput(a, "add(ONE)", s -> assertEquals(s.split("\n")[0], "|  Error:")), // id: e1
                (a) -> assertVariable(a, "int", "ONE", "1", "1"),
                assertRerun.apply("/1").apply("add(1)", 2), assertVariables,
                assertRerun.apply("/e1").apply("add(ONE)", 3), assertVariables,
                assertRerun.apply("/s1").apply("int assertionCount = 0;", 0), assertVariables
        );

        test(false, new String[] {"-nostartup"},
                (a) -> assertCommand(a, "/s1", "|  No such command or snippet id: /s1\n|  Type /help for help."),
                (a) -> assertCommand(a, "/1", "|  No such command or snippet id: /1\n|  Type /help for help."),
                (a) -> assertCommand(a, "/e1", "|  No such command or snippet id: /e1\n|  Type /help for help.")
        );
    }

    public void testClasspathDirectory() {
        Compiler compiler = new Compiler();
        Path outDir = Paths.get("testClasspathDirectory");
        compiler.compile(outDir, "package pkg; public class A { public String toString() { return \"A\"; } }");
        Path classpath = compiler.getPath(outDir);
        test(
                (a) -> assertCommand(a, "/classpath " + classpath, String.format("|  Path '%s' added to classpath", classpath)),
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "A")
        );
        test(new String[] { "-cp", classpath.toString() },
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "A")
        );
        test(new String[] { "-classpath", classpath.toString() },
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "A")
        );
    }

    public void testClasspathJar() {
        Compiler compiler = new Compiler();
        Path outDir = Paths.get("testClasspathJar");
        compiler.compile(outDir, "package pkg; public class A { public String toString() { return \"A\"; } }");
        String jarName = "test.jar";
        compiler.jar(outDir, jarName, "pkg/A.class");
        Path jarPath = compiler.getPath(outDir).resolve(jarName);
        test(
                (a) -> assertCommand(a, "/classpath " + jarPath, String.format("|  Path '%s' added to classpath", jarPath)),
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "A")
        );
        test(new String[] { "-cp", jarPath.toString() },
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "A")
        );
        test(new String[] { "-classpath", jarPath.toString() },
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "A")
        );
    }

    public void testStartupFileOption() {
        try {
            Compiler compiler = new Compiler();
            Path startup = compiler.getPath("StartupFileOption/startup.txt");
            compiler.writeToFile(startup, "class A { public String toString() { return \"A\"; } }");
            test(new String[]{"-startup", startup.toString()},
                    (a) -> evaluateExpression(a, "A", "new A()", "A")
            );
            test(new String[]{"-nostartup"},
                    (a) -> assertCommandCheckOutput(a, "printf(\"\")", assertStartsWith("|  Error:\n|  cannot find symbol"))
            );
            test((a) -> assertCommand(a, "printf(\"A\")", "", "", null, "A", ""));
            test(Locale.ROOT, false, new String[]{"-startup", "UNKNOWN"}, "|  File 'UNKNOWN' for start-up is not found.");
        } finally {
            removeStartup();
        }
    }

    public void testLoadingFromArgs() {
        Compiler compiler = new Compiler();
        Path path = compiler.getPath("loading.repl");
        compiler.writeToFile(path, "int a = 10; double x = 20; double a = 10;");
        test(new String[] { path.toString() },
                (a) -> assertCommand(a, "x", "x ==> 20.0"),
                (a) -> assertCommand(a, "a", "a ==> 10.0")
        );
        Path unknown = compiler.getPath("UNKNOWN.jar");
        test(Locale.ROOT, true, new String[]{unknown.toString()},
                "|  File " + unknown
                + " is not found: " + unresolvableMessage(unknown));
    }

    public void testReset() {
        test(
                (a) -> assertReset(a, "/res"),
                (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                (a) -> assertVariable(a, "int", "x"),
                (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                (a) -> assertMethod(a, "void f() { }", "()void", "f"),
                (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                (a) -> assertClass(a, "class A { }", "class", "A"),
                (a) -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                (a) -> assertImport(a, "import java.util.stream.*;", "", "java.util.stream.*"),
                (a) -> assertCommandCheckOutput(a, "/imports", assertImports()),
                (a) -> assertReset(a, "/reset"),
                (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                (a) -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                (a) -> assertCommandCheckOutput(a, "/imports", assertImports())
        );
    }

    public void testOpen() {
        Compiler compiler = new Compiler();
        Path path = compiler.getPath("testOpen.repl");
        compiler.writeToFile(path,
                "int a = 10;\ndouble x = 20;\ndouble a = 10;\n" +
                        "class A { public String toString() { return \"A\"; } }\nimport java.util.stream.*;");
        for (String s : new String[]{"/o", "/open"}) {
            test(
                    (a) -> assertCommand(a, s + " " + path.toString(), ""),
                    (a) -> assertCommand(a, "a", "a ==> 10.0"),
                    (a) -> evaluateExpression(a, "A", "new A();", "A"),
                    (a) -> evaluateExpression(a, "long", "Stream.of(\"A\").count();", "1"),
                    (a) -> {
                        loadVariable(a, "double", "x", "20.0", "20.0");
                        loadVariable(a, "double", "a", "10.0", "10.0");
                        loadVariable(a, "A", "$7", "new A();", "A");
                        loadVariable(a, "long", "$8", "Stream.of(\"A\").count();", "1");
                        loadClass(a, "class A { public String toString() { return \"A\"; } }",
                                "class", "A");
                        loadImport(a, "import java.util.stream.*;", "", "java.util.stream.*");
                        assertCommandCheckOutput(a, "/classes", assertClasses());
                    },
                    (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                    (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                    (a) -> assertCommandCheckOutput(a, "/imports", assertImports())
            );
            Path unknown = compiler.getPath("UNKNOWN.repl");
            test(
                    (a) -> assertCommand(a, s + " " + unknown,
                            "|  File '" + unknown + "' for '/open' is not found.")
            );
        }
    }

    public void testSave() throws IOException {
        Compiler compiler = new Compiler();
        Path path = compiler.getPath("testSave.repl");
        List<String> list = Arrays.asList(
                "int a;",
                "class A { public String toString() { return \"A\"; } }"
        );
        test(
                (a) -> assertVariable(a, "int", "a"),
                (a) -> assertCommand(a, "()", null, null, null, "", ""),
                (a) -> assertClass(a, "class A { public String toString() { return \"A\"; } }", "class", "A"),
                (a) -> assertCommand(a, "/save " + path.toString(), "")
        );
        assertEquals(Files.readAllLines(path), list);
        {
            List<String> output = new ArrayList<>();
            test(
                    (a) -> assertCommand(a, "int a;", null),
                    (a) -> assertCommand(a, "()", null, null, null, "", ""),
                    (a) -> assertClass(a, "class A { public String toString() { return \"A\"; } }", "class", "A"),
                    (a) -> assertCommandCheckOutput(a, "/list all", (out) ->
                            output.addAll(Stream.of(out.split("\n"))
                                    .filter(str -> !str.isEmpty())
                                    .map(str -> str.substring(str.indexOf(':') + 2))
                                    .filter(str -> !str.startsWith("/"))
                                    .collect(Collectors.toList()))),
                    (a) -> assertCommand(a, "/save all " + path.toString(), "")
            );
            assertEquals(Files.readAllLines(path), output);
        }
        List<String> output = new ArrayList<>();
        test(
                (a) -> assertVariable(a, "int", "a"),
                (a) -> assertCommand(a, "()", null, null, null, "", ""),
                (a) -> assertClass(a, "class A { public String toString() { return \"A\"; } }", "class", "A"),
                (a) -> assertCommandCheckOutput(a, "/history", (out) ->
                        output.addAll(Stream.of(out.split("\n"))
                                .filter(str -> !str.isEmpty())
                                .collect(Collectors.toList()))),
                (a) -> assertCommand(a, "/save history " + path.toString(), "")
        );
        output.add("/save history " + path.toString());
        assertEquals(Files.readAllLines(path), output);
    }

    public void testStartSet() throws BackingStoreException {
        try {
            Compiler compiler = new Compiler();
            Path startUpFile = compiler.getPath("startUp.txt");
            test(
                    (a) -> assertVariable(a, "int", "a"),
                    (a) -> assertVariable(a, "double", "b", "10", "10.0"),
                    (a) -> assertMethod(a, "void f() {}", "()V", "f"),
                    (a) -> assertImport(a, "import java.util.stream.*;", "", "java.util.stream.*"),
                    (a) -> assertCommand(a, "/save " + startUpFile.toString(), null),
                    (a) -> assertCommand(a, "/set start " + startUpFile.toString(), null)
            );
            Path unknown = compiler.getPath("UNKNOWN");
            test(
                    (a) -> assertCommandOutputStartsWith(a, "/set start " + unknown.toString(),
                            "|  File '" + unknown + "' for '/set start' is not found.")
            );
            test(false, new String[0],
                    (a) -> {
                        loadVariable(a, "int", "a");
                        loadVariable(a, "double", "b", "10.0", "10.0");
                        loadMethod(a, "void f() {}", "()void", "f");
                        loadImport(a, "import java.util.stream.*;", "", "java.util.stream.*");
                        assertCommandCheckOutput(a, "/classes", assertClasses());
                    },
                    (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                    (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                    (a) -> assertCommandCheckOutput(a, "/imports", assertImports())
            );
        } finally {
            removeStartup();
        }
    }

    private void removeStartup() {
        Preferences preferences = Preferences.userRoot().node("tool/JShell");
        if (preferences != null) {
            preferences.remove("STARTUP");
        }
    }

    public void testStartSave() throws IOException {
        Compiler compiler = new Compiler();
        Path startSave = compiler.getPath("startSave.txt");
        test(a -> assertCommand(a, "/save start " + startSave.toString(), null));
        List<String> lines = Files.lines(startSave)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        assertEquals(lines, START_UP);
    }

    public void testConstrainedUpdates() {
        test(
                a -> assertClass(a, "class XYZZY { }", "class", "XYZZY"),
                a -> assertVariable(a, "XYZZY", "xyzzy"),
                a -> assertCommandCheckOutput(a, "import java.util.stream.*",
                        (out) -> assertTrue(out.trim().isEmpty(), "Expected no output, got: " + out))
        );
    }

    public void testRemoteExit() {
        test(
                a -> assertVariable(a, "int", "x"),
                a -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                a -> assertCommandOutputContains(a, "System.exit(5);", "terminated"),
                a -> assertCommandCheckOutput(a, "/vars", s ->
                        assertTrue(s.trim().isEmpty(), s)),
                a -> assertMethod(a, "void f() { }", "()void", "f"),
                a -> assertCommandCheckOutput(a, "/methods", assertMethods())
        );
    }

    public void testFeedbackNegative() {
        test(a -> assertCommandCheckOutput(a, "/set feedback aaaa",
                assertStartsWith("|  Does not match any current feedback mode")));
    }

    public void testFeedbackSilent() {
        for (String off : new String[]{"s", "silent"}) {
            test(
                    a -> assertCommand(a, "/set feedback " + off, ""),
                    a -> assertCommand(a, "int a", ""),
                    a -> assertCommand(a, "void f() {}", ""),
                    a -> assertCommandCheckOutput(a, "aaaa", assertStartsWith("|  Error:")),
                    a -> assertCommandCheckOutput(a, "public void f() {}", assertStartsWith("|  Warning:"))
            );
        }
    }

    public void testFeedbackNormal() {
        Compiler compiler = new Compiler();
        Path testNormalFile = compiler.getPath("testConciseNormal");
        String[] sources = new String[] {"int a", "void f() {}", "class A {}", "a = 10"};
        String[] sources2 = new String[] {"int a //again", "void f() {int y = 4;}", "class A {} //again", "a = 10"};
        String[] output = new String[] {
                "a ==> 0",
                "|  created method f()",
                "|  created class A",
                "a ==> 10"
        };
        compiler.writeToFile(testNormalFile, sources2);
        for (String feedback : new String[]{"/set fe", "/set feedback"}) {
            for (String feedbackState : new String[]{"n", "normal"}) {
                test(
                        a -> assertCommand(a, feedback + " " + feedbackState, "|  Feedback mode: normal"),
                        a -> assertCommand(a, sources[0], output[0]),
                        a -> assertCommand(a, sources[1], output[1]),
                        a -> assertCommand(a, sources[2], output[2]),
                        a -> assertCommand(a, sources[3], output[3]),
                        a -> assertCommand(a, "/o " + testNormalFile.toString(), "")
                );
            }
        }
    }

    public void testHistoryReference() {
        test(false, new String[]{"-nostartup"},
                a -> assertCommand(a, "System.err.println(1)", "", "", null, "", "1\n"),
                a -> assertCommand(a, "System.err.println(2)", "", "", null, "", "2\n"),
                a -> assertCommand(a, "/-2", "System.err.println(1)", "", null, "", "1\n"),
                a -> assertCommand(a, "/history",
                                                    "/debug 0\n" +
                                                    "System.err.println(1)\n" +
                                                    "System.err.println(2)\n" +
                                                    "System.err.println(1)\n" +
                                                    "/history\n"),
                a -> assertCommand(a, "/-2", "System.err.println(2)", "", null, "", "2\n"),
                a -> assertCommand(a, "/!", "System.err.println(2)", "", null, "", "2\n"),
                a -> assertCommand(a, "/2", "System.err.println(2)", "", null, "", "2\n"),
                a -> assertCommand(a, "/1", "System.err.println(1)", "", null, "", "1\n")
        );
    }

    private String unresolvableMessage(Path p) {
        try {
            new FileInputStream(p.toFile());
            throw new AssertionError("Expected exception did not occur.");
        } catch (IOException ex) {
            return ex.getMessage();
        }
    }
}
