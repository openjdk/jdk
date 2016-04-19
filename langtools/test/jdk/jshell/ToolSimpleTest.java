/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8153716 8143955
 * @summary Simple jshell tool tests
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @build KullaTesting TestingInputStream
 * @run testng ToolSimpleTest
 */
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class ToolSimpleTest extends ReplToolTesting {

    public void testRemaining() {
        test(
                (a) -> assertCommand(a, "int z; z =", "z ==> 0"),
                (a) -> assertCommand(a, "5", "z ==> 5"),
                (a) -> assertCommand(a, "/*nada*/; int q =", ""),
                (a) -> assertCommand(a, "77", "q ==> 77"),
                (a) -> assertCommand(a, "//comment;", ""),
                (a) -> assertCommand(a, "int v;", "v ==> 0"),
                (a) -> assertCommand(a, "int v; int c",
                        "v ==> 0\n" +
                        "c ==> 0")
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

    public void defineVar() {
        test(
                (a) -> assertCommand(a, "int x = 72", "x ==> 72"),
                (a) -> assertCommand(a, "x", "x ==> 72"),
                (a) -> assertCommand(a, "/vars", "|    int x = 72")
        );
    }

    @Test(enabled = false) // TODO 8153897
    public void defineUnresolvedVar() {
        test(
                (a) -> assertCommand(a, "undefined x",
                        "|  created variable x, however, it cannot be referenced until class undefined is declared"),
                (a) -> assertCommand(a, "/vars", "|    undefined x = (not-active)")
        );
    }

    public void testUnresolved() {
        test(
                (a) -> assertCommand(a, "int f() { return g() + x + new A().a; }",
                        "|  created method f(), however, it cannot be invoked until method g(), variable x, and class A are declared"),
                (a) -> assertCommand(a, "f()",
                        "|  attempted to call method f() which cannot be invoked until method g(), variable x, and class A are declared"),
                (a) -> assertCommandOutputStartsWith(a, "int g() { return x; }",
                        "|  created method g(), however, it cannot be invoked until variable x is declared"),
                (a) -> assertCommand(a, "g()", "|  attempted to call method g() which cannot be invoked until variable x is declared")
        );
    }

    public void testUnknownCommand() {
        test((a) -> assertCommand(a, "/unknown",
                "|  No such command or snippet id: /unknown\n" +
                "|  Type /help for help."));
    }

    public void testEmptyClassPath() {
        test(after -> assertCommand(after, "/classpath", "|  The /classpath command requires a path argument."));
    }

    public void testNoArgument() {
        String[] commands = {"/save", "/open", "/set start"};
        test(Stream.of(commands)
                .map(cmd -> {
                    String c = cmd;
                    final String finalC = c;
                    return (ReplTest) after -> assertCommand(after, cmd,
                            "|  '" + finalC + "' requires a filename argument.");
                })
                .toArray(ReplTest[]::new));
    }

    public void testDebug() {
        test(
                (a) -> assertCommand(a, "/deb", "|  Debugging on"),
                (a) -> assertCommand(a, "/debug", "|  Debugging off"),
                (a) -> assertCommand(a, "/debug", "|  Debugging on"),
                (a) -> assertCommand(a, "/deb", "|  Debugging off")
        );
    }

    public void testDrop() {
        test(false, new String[]{"-nostartup"},
                a -> assertVariable(a, "int", "a"),
                a -> dropVariable(a, "/drop 1", "int a = 0", "|  dropped variable a"),
                a -> assertMethod(a, "int b() { return 0; }", "()I", "b"),
                a -> dropMethod(a, "/drop 2", "b ()I", "|  dropped method b()"),
                a -> assertClass(a, "class A {}", "class", "A"),
                a -> dropClass(a, "/drop 3", "class A", "|  dropped class A"),
                a -> assertImport(a, "import java.util.stream.*;", "", "java.util.stream.*"),
                a -> dropImport(a, "/drop 4", "import java.util.stream.*", ""),
                a -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                a -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                a -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                a -> assertCommandCheckOutput(a, "/imports", assertImports())
        );
        test(false, new String[]{"-nostartup"},
                a -> assertVariable(a, "int", "a"),
                a -> dropVariable(a, "/drop a", "int a = 0", "|  dropped variable a"),
                a -> assertMethod(a, "int b() { return 0; }", "()I", "b"),
                a -> dropMethod(a, "/drop b", "b ()I", "|  dropped method b()"),
                a -> assertClass(a, "class A {}", "class", "A"),
                a -> dropClass(a, "/drop A", "class A", "|  dropped class A"),
                a -> assertCommandCheckOutput(a, "/vars", assertVariables()),
                a -> assertCommandCheckOutput(a, "/methods", assertMethods()),
                a -> assertCommandCheckOutput(a, "/classes", assertClasses()),
                a -> assertCommandCheckOutput(a, "/imports", assertImports())
        );
    }

    public void testDropNegative() {
        test(false, new String[]{"-nostartup"},
                a -> assertCommandOutputStartsWith(a, "/drop 0", "|  No definition or id found named: 0"),
                a -> assertCommandOutputStartsWith(a, "/drop a", "|  No definition or id found named: a"),
                a -> assertCommandCheckOutput(a, "/drop",
                        assertStartsWith("|  In the /drop argument, please specify an import, variable, method, or class to drop.")),
                a -> assertVariable(a, "int", "a"),
                a -> assertCommand(a, "a", "a ==> 0"),
                a -> assertCommand(a, "/drop 2", "|  The argument did not specify an active import, variable, method, or class to drop.")
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

    public void testHelpLength() {
        Consumer<String> testOutput = (s) -> {
            List<String> ss = Stream.of(s.split("\n"))
                    .filter(l -> !l.isEmpty())
                    .collect(Collectors.toList());
            assertTrue(ss.size() >= 10, "Help does not print enough lines:" + s);
        };
        test(
                (a) -> assertCommandCheckOutput(a, "/?", testOutput),
                (a) -> assertCommandCheckOutput(a, "/help", testOutput),
                (a) -> assertCommandCheckOutput(a, "/help /list", testOutput)
        );
    }

    public void testHelp() {
        test(
                (a) -> assertHelp(a, "/?", "/list", "/help", "/exit", "intro"),
                (a) -> assertHelp(a, "/help", "/list", "/help", "/exit", "intro"),
                (a) -> assertHelp(a, "/help short", "shortcuts", "<tab>"),
                (a) -> assertHelp(a, "/? /li", "/list all", "snippets"),
                (a) -> assertHelp(a, "/help /help", "/help <command>")
        );
    }

    private void assertHelp(boolean a, String command, String... find) {
        assertCommandCheckOutput(a, command, s -> {
            for (String f : find) {
                assertTrue(s.contains(f), "Expected output of " + command + " to contain: " + f);
            }
        });
    }

    // Check that each line of output contains the corresponding string from the list
    private void checkLineToList(String in, List<String> match) {
        String[] res = in.trim().split("\n");
        assertEquals(res.length, match.size(), "Got: " + Arrays.asList(res));
        for (int i = 0; i < match.size(); ++i) {
            assertTrue(res[i].contains(match.get(i)));
        }
    }

    public void testListArgs() {
        String arg = "qqqq";
        List<String> startVarList = new ArrayList<>(START_UP);
        startVarList.add("int aardvark");
        test(
                a -> assertCommandCheckOutput(a, "/list all",
                        s -> checkLineToList(s, START_UP)),
                a -> assertCommandOutputStartsWith(a, "/list " + arg,
                        "|  No definition or id found named: " + arg),
                a -> assertVariable(a, "int", "aardvark"),
                a -> assertCommandOutputContains(a, "/list aardvark", "aardvark"),
                a -> assertCommandCheckOutput(a, "/list start",
                        s -> checkLineToList(s, START_UP)),
                a -> assertCommandCheckOutput(a, "/list all",
                        s -> checkLineToList(s, startVarList)),
                a -> assertCommandCheckOutput(a, "/list printf",
                        s -> assertTrue(s.contains("void printf"))),
                a -> assertCommandOutputStartsWith(a, "/list " + arg,
                        "|  No definition or id found named: " + arg)
        );
    }

    public void testCommandPrefix() {
        test(a -> assertCommandCheckOutput(a, "/s",
                      assertStartsWith("|  Command: '/s' is ambiguous: /save, /set")),
             a -> assertCommand(a, "int var", "var ==> 0"),
             a -> assertCommandCheckOutput(a, "/va",
                      assertStartsWith("|    int var = 0")),
             a -> assertCommandCheckOutput(a, "/save",
                      assertStartsWith("|  '/save' requires a filename argument.")));
    }

    public void testHeadlessEditPad() {
        String prevHeadless = System.getProperty("java.awt.headless");
        try {
            System.setProperty("java.awt.headless", "true");
            test(
                (a) -> assertCommandOutputStartsWith(a, "/edit printf", "|  Cannot launch editor -- unexpected exception:")
            );
        } finally {
            System.setProperty("java.awt.headless", prevHeadless==null? "false" : prevHeadless);
        }
    }
}
