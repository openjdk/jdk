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
 * @bug 8153716
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

    public void defineVar() {
        test(
                (a) -> assertCommand(a, "int x = 72", "|  Added variable x of type int with initial value 72"),
                (a) -> assertCommand(a, "x", "|  Variable x of type int has value 72"),
                (a) -> assertCommand(a, "/vars", "|    int x = 72")
        );
    }

    @Test(enabled = false) // TODO 8153897
    public void defineUnresolvedVar() {
        test(
                (a) -> assertCommand(a, "undefined x",
                        "|  Added variable x, however, it cannot be referenced until class undefined is declared"),
                (a) -> assertCommand(a, "/vars", "|    undefined x = (not-active)")
        );
    }

    public void testUnresolved() {
        test(
                (a) -> assertCommand(a, "int f() { return g() + x + new A().a; }",
                        "|  Added method f(), however, it cannot be invoked until method g(), variable x, and class A are declared"),
                (a) -> assertCommand(a, "f()",
                        "|  Attempted to call method f() which cannot be invoked until method g(), variable x, and class A are declared"),
                (a) -> assertCommandOutputStartsWith(a, "int g() { return x; }",
                        "|  Added method g(), however, it cannot be invoked until variable x is declared"),
                (a) -> assertCommand(a, "g()", "|  Attempted to call method g() which cannot be invoked until variable x is declared")
        );
    }

    public void testDebug() {
        test(
                (a) -> assertCommand(a, "/deb", "|  Debugging on"),
                (a) -> assertCommand(a, "/debug", "|  Debugging off"),
                (a) -> assertCommand(a, "/debug", "|  Debugging on"),
                (a) -> assertCommand(a, "/deb", "|  Debugging off")
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
