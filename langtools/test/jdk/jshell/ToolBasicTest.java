/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8143037
 * @summary Tests for Basic tests for REPL tool
 * @ignore 8139873
 * @library /tools/lib
 * @build KullaTesting TestingInputStream ToolBox Compiler
 * @run testng ToolBasicTest
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    public void defineVar() {
        test(
                (a) -> assertCommand(a, "int x = 72", "|  Added variable x of type int with initial value 72\n"),
                (a) -> assertCommand(a, "x", "|  Variable x of type int has value 72\n"),
                (a) -> assertCommand(a, "/vars", "|    int x = 72\n")
        );
    }

    public void defineUnresolvedVar() {
        test(
                (a) -> assertCommand(a, "undefined x",
                        "|  Added variable x, however, it cannot be referenced until class undefined is declared\n"),
                (a) -> assertCommand(a, "/vars", "|    undefined x = (not-active)\n")
        );
    }

    public void testUnresolved() {
        test(
                (a) -> assertCommand(a, "int f() { return g() + x + new A().a; }",
                        "|  Added method f(), however, it cannot be invoked until method g(), variable x, and class A are declared\n"),
                (a) -> assertCommand(a, "f()",
                        "|  Attempted to call f which cannot be invoked until method g(), variable x, and class A are declared\n"),
                (a) -> assertCommand(a, "int g() { return x; }",
                        "|  Added method g(), however, it cannot be invoked until variable x is declared\n"),
                (a) -> assertCommand(a, "g()", "|  Attempted to call g which cannot be invoked until variable x is declared\n")
        );
    }

    public void elideStartUpFromList() {
        test(
                (a) -> assertCommandCheckOutput(a, "123", (s) ->
                        assertTrue(s.contains("type int"), s)),
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
                (a) -> assertCommandCheckOutput(a, "123",
                        (s) -> assertTrue(s.contains("type int"), s)),
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
            assertOutput(getUserOutput(), output, "user");
            assertOutput(getUserErrorOutput(), "", "user error");
        }
    }

    public void testStop() {
        test(
                (a) -> assertStop(a, "while (true) {}", "Killed.\n"),
                (a) -> assertStop(a, "while (true) { try { Thread.sleep(100); } catch (InterruptedException ex) { } }", "Killed.\n")
        );
    }

    @Test(enabled = false) // TODO 8130450
    public void testRerun() {
        test(false, new String[] {"-nostartup"},
                (a) -> assertCommand(a, "/0", "|  No such command or snippet id: /0\n|  Type /help for help.\n"),
                (a) -> assertCommand(a, "/5", "|  No such command or snippet id: /5\n|  Type /help for help.\n")
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
                (a) -> assertCommand(a, "/s1", "|  No such command or snippet id: /s1\n|  Type /help for help.\n"),
                (a) -> assertCommand(a, "/1", "|  No such command or snippet id: /1\n|  Type /help for help.\n"),
                (a) -> assertCommand(a, "/e1", "|  No such command or snippet id: /e1\n|  Type /help for help.\n")
        );
    }

    public void testRemaining() {
        test(
                (a) -> assertCommand(a, "int z; z =", "|  Added variable z of type int\n"),
                (a) -> assertCommand(a, "5", "|  Variable z has been assigned the value 5\n"),
                (a) -> assertCommand(a, "/*nada*/; int q =", ""),
                (a) -> assertCommand(a, "77", "|  Added variable q of type int with initial value 77\n"),
                (a) -> assertCommand(a, "//comment;", ""),
                (a) -> assertCommand(a, "int v;", "|  Added variable v of type int\n"),
                (a) -> assertCommand(a, "int v; int c", "|  Added variable c of type int\n")
        );
    }

    public void testDebug() {
        test(
                (a) -> assertCommand(a, "/deb", "|  Debugging on\n"),
                (a) -> assertCommand(a, "/debug", "|  Debugging off\n"),
                (a) -> assertCommand(a, "/debug", "|  Debugging on\n"),
                (a) -> assertCommand(a, "/deb", "|  Debugging off\n")
        );
    }

    public void testHelp() {
        Consumer<String> testOutput = (s) -> {
            List<String> ss = Stream.of(s.split("\n"))
                    .filter(l -> !l.isEmpty())
                    .collect(Collectors.toList());
            assertTrue(ss.size() >= 5, "Help does not print enough lines:\n" + s);
        };
        test(
                (a) -> assertCommandCheckOutput(a, "/?", testOutput),
                (a) -> assertCommandCheckOutput(a, "/help", testOutput)
        );
    }

    public void oneLineOfError() {
        test(
                (a) -> assertCommand(a, "12+", null),
                (a) -> assertCommandCheckOutput(a, "  true", (s) ->
                        assertTrue(s.contains("12+") && !s.contains("true"), "Output: '" + s + "'"))
        );
    }

    public void defineVariables() {
        test(
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                (a) -> assertVariable(a, "int", "a"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                (a) -> assertVariable(a, "double", "a", "1", "1.0"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                (a) -> evaluateExpression(a, "double", "2 * a", "2.0"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/vars", assertVariables())
        );
    }

    public void defineMethods() {
        test(
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                (a) -> assertMethod(a, "int f() { return 0; }", "()int", "f"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                (a) -> assertMethod(a, "void f(int a) { g(); }", "(int)void", "f"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                (a) -> assertMethod(a, "void g() {}", "()void", "g"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/methods", assertMethods())
        );
    }

    public void defineClasses() {
        test(
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                (a) -> assertClass(a, "class A { }", "class", "A"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                (a) -> assertClass(a, "interface A { }", "interface", "A"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                (a) -> assertClass(a, "enum A { }", "enum", "A"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                (a) -> assertClass(a, "@interface A { }", "@interface", "A"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/classes", assertClasses())
        );
    }

    public void defineImports() {
        test(
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/imports", assertImports()),
                (a) -> assertImport(a, "import java.util.stream.Stream;", "", "java.util.stream.Stream"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/imports", assertImports()),
                (a) -> assertImport(a, "import java.util.stream.*;", "", "java.util.stream.*"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/imports", assertImports()),
                (a) -> assertImport(a, "import static java.lang.Math.PI;", "static", "java.lang.Math.PI"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/imports", assertImports()),
                (a) -> assertImport(a, "import static java.lang.Math.*;", "static", "java.lang.Math.*"),
                (a) -> assertCommandCheckOutput(a, "/list", assertList()),
                (a) -> assertCommandCheckOutput(a, "/imports", assertImports())
        );
    }

    public void testClasspathDirectory() {
        Compiler compiler = new Compiler();
        Path outDir = Paths.get("testClasspathDirectory");
        compiler.compile(outDir, "package pkg; public class A { public String toString() { return \"A\"; } }");
        Path classpath = compiler.getPath(outDir);
        test(
                (a) -> assertCommand(a, "/classpath " + classpath, String.format("|  Path %s added to classpath\n", classpath)),
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "\"A\"")
        );
        test(new String[] { "-cp", classpath.toString() },
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "\"A\"")
        );
        test(new String[] { "-classpath", classpath.toString() },
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "\"A\"")
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
                (a) -> assertCommand(a, "/classpath " + jarPath, String.format("|  Path %s added to classpath\n", jarPath)),
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "\"A\"")
        );
        test(new String[] { "-cp", jarPath.toString() },
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "\"A\"")
        );
        test(new String[] { "-classpath", jarPath.toString() },
                (a) -> evaluateExpression(a, "pkg.A", "new pkg.A();", "\"A\"")
        );
    }

    public void testStartupFileOption() {
        try {
            Compiler compiler = new Compiler();
            Path startup = compiler.getPath("StartupFileOption/startup.txt");
            compiler.writeToFile(startup, "class A { public String toString() { return \"A\"; } }");
            test(new String[]{"-startup", startup.toString()},
                    (a) -> evaluateExpression(a, "A", "new A()", "\"A\"\n")
            );
            test(new String[]{"-nostartup"},
                    (a) -> assertCommandCheckOutput(a, "printf(\"\")", assertStartsWith("|  Error:\n|  cannot find symbol"))
            );
            test((a) -> assertCommand(a, "printf(\"A\")", "", "", null, "A", ""));
            test(false, new String[]{"-startup", "UNKNOWN"}, "|  File 'UNKNOWN' for start-up is not found.");
        } finally {
            removeStartup();
        }
    }

    public void testLoadingFromArgs() {
        Compiler compiler = new Compiler();
        Path path = compiler.getPath("loading.repl");
        compiler.writeToFile(path, "int a = 10; double x = 20; double a = 10;");
        test(new String[] { path.toString() },
                (a) -> assertCommand(a, "x", "|  Variable x of type double has value 20.0\n"),
                (a) -> assertCommand(a, "a", "|  Variable a of type double has value 10.0\n")
        );
        Path unknown = compiler.getPath("UNKNOWN.jar");
        test(true, new String[]{unknown.toString()},
                "|  File '" + unknown
                + "' is not found: " + unknown
                + " (No such file or directory)\n");
    }

    public void testReset() {
        test(
                (a) -> assertReset(a, "/r"),
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
                    (a) -> assertCommand(a, "a", "|  Variable a of type double has value 10.0\n"),
                    (a) -> evaluateExpression(a, "A", "new A();", "\"A\""),
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
                            "|  File '" + unknown
                                    + "' is not found: " + unknown
                                    + " (No such file or directory)\n")
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
                (a) -> assertClass(a, "class A { public String toString() { return \"A\"; } }", "class", "A"),
                (a) -> assertCommand(a, "/save " + path.toString(), "")
        );
        assertEquals(Files.readAllLines(path), list);
        {
            List<String> output = new ArrayList<>();
            test(
                    (a) -> assertCommand(a, "int a;", null),
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
                    (a) -> assertCommand(a, "/setstart " + startUpFile.toString(), null)
            );
            Path unknown = compiler.getPath("UNKNOWN");
            test(
                    (a) -> assertCommand(a, "/setstart " + unknown.toString(),
                            "|  File '" + unknown + "' for /setstart is not found.\n")
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
        Preferences preferences = Preferences.userRoot().node("tool/REPL");
        if (preferences != null) {
            preferences.remove("STARTUP");
        }
    }

    public void testUnknownCommand() {
        test((a) -> assertCommand(a, "/unknown",
                "|  No such command or snippet id: /unknown\n" +
                "|  Type /help for help.\n"));
    }

    public void testEmptyClassPath() {
        test(after -> assertCommand(after, "/classpath", "|  /classpath requires a path argument\n"));
    }

    public void testNoArgument() {
        String[] commands = {"/save", "/open", "/setstart"};
        test(Stream.of(commands)
                .map(cmd -> {
                    String c = cmd;
                    final String finalC = c;
                    return (ReplTest) after -> assertCommand(after, cmd,
                            "|  The " + finalC + " command requires a filename argument.\n");
                })
                .toArray(ReplTest[]::new));
    }

    public void testStartSave() throws IOException {
        Compiler compiler = new Compiler();
        Path startSave = compiler.getPath("startSave.txt");
        test(a -> assertCommand(a, "/save start " + startSave.toString(), null));
        List<String> lines = Files.lines(startSave)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        assertEquals(lines, Arrays.asList(
                "import java.util.*;",
                "import java.io.*;",
                "import java.math.*;",
                "import java.net.*;",
                "import java.util.concurrent.*;",
                "import java.util.prefs.*;",
                "import java.util.regex.*;",
                "void printf(String format, Object... args) { System.out.printf(format, args); }"));
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
                a -> assertCommandCheckOutput(a, "System.exit(5);",  s ->
                        assertTrue(s.contains("terminated"), s)),
                a -> assertCommandCheckOutput(a, "/vars", s ->
                        assertTrue(s.trim().isEmpty(), s)),
                a -> assertMethod(a, "void f() { }", "()void", "f"),
                a -> assertCommandCheckOutput(a, "/methods", assertMethods())
        );
    }

    public void testListArgs() {
        Consumer<String> assertList = s -> assertTrue(s.split("\n").length >= 4, s);
        String arg = "qqqq";
        Consumer<String> assertError = s -> assertEquals(s, "|  Invalid /list argument: " + arg + "\n");
        test(
                a -> assertCommandCheckOutput(a, "/list all", assertList),
                a -> assertCommandCheckOutput(a, "/list " + arg, assertError),
                a -> assertVariable(a, "int", "a"),
                a -> assertCommandCheckOutput(a, "/list history", assertList)
        );
    }

    public void testFeedbackNegative() {
        test(a -> assertCommandCheckOutput(a, "/feedback aaaa",
                assertStartsWith("|  Follow /feedback with of the following")));
    }

    public void testFeedbackOff() {
        for (String off : new String[]{"o", "off"}) {
            test(
                    a -> assertCommand(a, "/feedback " + off, ""),
                    a -> assertCommand(a, "int a", ""),
                    a -> assertCommand(a, "void f() {}", ""),
                    a -> assertCommandCheckOutput(a, "aaaa", assertStartsWith("|  Error:")),
                    a -> assertCommandCheckOutput(a, "public void f() {}", assertStartsWith("|  Warning:"))
            );
        }
    }

    public void testFeedbackConcise() {
        Compiler compiler = new Compiler();
        Path testConciseFile = compiler.getPath("testConciseFeedback");
        String[] sources = new String[] {"int a", "void f() {}", "class A {}", "a = 10"};
        compiler.writeToFile(testConciseFile, sources);
        for (String concise : new String[]{"c", "concise"}) {
            test(
                    a -> assertCommand(a, "/feedback " + concise, ""),
                    a -> assertCommand(a, sources[0], ""),
                    a -> assertCommand(a, sources[1], ""),
                    a -> assertCommand(a, sources[2], ""),
                    a -> assertCommand(a, sources[3], "|  a : 10\n"),
                    a -> assertCommand(a, "/o " + testConciseFile.toString(), "|  a : 10\n")
            );
        }
    }

    public void testFeedbackNormal() {
        Compiler compiler = new Compiler();
        Path testNormalFile = compiler.getPath("testConciseNormal");
        String[] sources = new String[] {"int a", "void f() {}", "class A {}", "a = 10"};
        String[] sources2 = new String[] {"int a //again", "void f() {int y = 4;}", "class A {} //again", "a = 10"};
        String[] output = new String[] {
                "|  Added variable a of type int\n",
                "|  Added method f()\n",
                "|  Added class A\n",
                "|  Variable a has been assigned the value 10\n"
        };
        compiler.writeToFile(testNormalFile, sources2);
        for (String feedback : new String[]{"/f", "/feedback"}) {
            for (String feedbackState : new String[]{"n", "normal", "v", "verbose"}) {
                String f = null;
                if (feedbackState.startsWith("n")) {
                    f = "normal";
                } else if (feedbackState.startsWith("v")) {
                    f = "verbose";
                }
                final String finalF = f;
                test(
                        a -> assertCommand(a, feedback + " " + feedbackState, "|  Feedback mode: " + finalF +"\n"),
                        a -> assertCommand(a, sources[0], output[0]),
                        a -> assertCommand(a, sources[1], output[1]),
                        a -> assertCommand(a, sources[2], output[2]),
                        a -> assertCommand(a, sources[3], output[3]),
                        a -> assertCommand(a, "/o " + testNormalFile.toString(),
                                "|  Modified variable a of type int\n" +
                                "|  Modified method f()\n" +
                                "|    Update overwrote method f()\n" +
                                "|  Modified class A\n" +
                                "|    Update overwrote class A\n" +
                                "|  Variable a has been assigned the value 10\n")
                );
            }
        }
    }

    public void testFeedbackDefault() {
        Compiler compiler = new Compiler();
        Path testDefaultFile = compiler.getPath("testDefaultFeedback");
        String[] sources = new String[] {"int a", "void f() {}", "class A {}", "a = 10"};
        String[] output = new String[] {
                "|  Added variable a of type int\n",
                "|  Added method f()\n",
                "|  Added class A\n",
                "|  Variable a has been assigned the value 10\n"
        };
        compiler.writeToFile(testDefaultFile, sources);
        for (String defaultFeedback : new String[]{"", "d", "default"}) {
            test(
                    a -> assertCommand(a, "/feedback o", ""),
                    a -> assertCommand(a, "int x", ""),
                    a -> assertCommand(a, "/feedback " + defaultFeedback, "|  Feedback mode: default\n"),
                    a -> assertCommand(a, sources[0], output[0]),
                    a -> assertCommand(a, sources[1], output[1]),
                    a -> assertCommand(a, sources[2], output[2]),
                    a -> assertCommand(a, sources[3], output[3]),
                    a -> assertCommand(a, "/o " + testDefaultFile.toString(), "")
            );
        }
    }

    public void testDrop() {
        test(false, new String[]{"-nostartup"},
                a -> assertVariable(a, "int", "a"),
                a -> dropVariable(a, "/drop 1", "int a = 0"),
                a -> assertMethod(a, "int b() { return 0; }", "()I", "b"),
                a -> dropMethod(a, "/drop 2", "b ()I"),
                a -> assertClass(a, "class A {}", "class", "A"),
                a -> dropClass(a, "/drop 3", "class A"),
                a -> assertImport(a, "import java.util.stream.*;", "", "java.util.stream.*"),
                a -> dropImport(a, "/drop 4", "import java.util.stream.*"),
                a -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                a -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                a -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                a -> assertCommandCheckOutput(a, "/imports", assertImports())
        );
        test(false, new String[]{"-nostartup"},
                a -> assertVariable(a, "int", "a"),
                a -> dropVariable(a, "/drop a", "int a = 0"),
                a -> assertMethod(a, "int b() { return 0; }", "()I", "b"),
                a -> dropMethod(a, "/drop b", "b ()I"),
                a -> assertClass(a, "class A {}", "class", "A"),
                a -> dropClass(a, "/drop A", "class A"),
                a -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                a -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                a -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                a -> assertCommandCheckOutput(a, "/imports", assertImports())
        );
    }

    public void testDropNegative() {
        test(false, new String[]{"-nostartup"},
                a -> assertCommand(a, "/drop 0", "|  No definition or id named 0 found.  See /classes /methods /vars or /list\n"),
                a -> assertCommand(a, "/drop a", "|  No definition or id named a found.  See /classes /methods /vars or /list\n"),
                a -> assertCommandCheckOutput(a, "/drop",
                        assertStartsWith("|  In the /drop argument, please specify an import, variable, method, or class to drop.")),
                a -> assertVariable(a, "int", "a"),
                a -> assertCommand(a, "a", "|  Variable a of type int has value 0\n"),
                a -> assertCommand(a, "/drop 2", "|  The argument did not specify an import, variable, method, or class to drop.\n")
        );
    }

    public void testAmbiguousDrop() {
        Consumer<String> check = s -> {
            assertTrue(s.startsWith("|  The argument references more than one import, variable, method, or class"), s);
            int lines = s.split("\n").length;
            assertEquals(lines, 5, "Expected 3 ambiguous keys, but found: " + (lines - 2) + "\n" + s);
        };
        test(
                a -> assertVariable(a, "int", "a"),
                a -> assertMethod(a, "int a() { return 0; }", "()int", "a"),
                a -> assertClass(a, "class a {}", "class", "a"),
                a -> assertCommandCheckOutput(a, "/drop a", check),
                a -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                a -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                a -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                a -> assertCommandCheckOutput(a, "/imports", assertImports())
        );
        test(
                a -> assertMethod(a, "int a() { return 0; }", "()int", "a"),
                a -> assertMethod(a, "double a(int a) { return 0; }", "(int)double", "a"),
                a -> assertMethod(a, "double a(double a) { return 0; }", "(double)double", "a"),
                a -> assertCommandCheckOutput(a, "/drop a", check),
                a -> assertCommandCheckOutput(a, "/methods", assertMethods())
        );
    }

    public void testHistoryReference() {
        test(false, new String[]{"-nostartup"},
                a -> assertCommand(a, "System.err.println(1)", "", "", null, "", "1\n"),
                a -> assertCommand(a, "System.err.println(2)", "", "", null, "", "2\n"),
                a -> assertCommand(a, "/-2", "System.err.println(1)\n", "", null, "", "1\n"),
                a -> assertCommand(a, "/history", "\n" +
                                                    "/debug 0\n" +
                                                    "System.err.println(1)\n" +
                                                    "System.err.println(2)\n" +
                                                    "System.err.println(1)\n" +
                                                    "/history\n"),
                a -> assertCommand(a, "/-2", "System.err.println(2)\n", "", null, "", "2\n"),
                a -> assertCommand(a, "/!", "System.err.println(2)\n", "", null, "", "2\n"),
                a -> assertCommand(a, "/2", "System.err.println(2)\n", "", null, "", "2\n"),
                a -> assertCommand(a, "/1", "System.err.println(1)\n", "", null, "", "1\n")
        );
    }

    public void testCommandPrefix() {
        test(a -> assertCommandCheckOutput(a, "/s",
                      assertStartsWith("|  Command: /s is ambiguous: /seteditor, /save, /setstart")),
             a -> assertCommand(a, "int var", "|  Added variable var of type int\n"),
             a -> assertCommandCheckOutput(a, "/va",
                      assertStartsWith("|    int var = 0")),
             a -> assertCommandCheckOutput(a, "/save",
                      assertStartsWith("|  The /save command requires a filename argument.")));
    }
}
