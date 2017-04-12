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
 * @bug 8166744
 * @summary Test Completion
 * @modules jdk.internal.le/jdk.internal.jline.extra
 *          jdk.jshell/jdk.internal.jshell.tool:+open
 * @build HistoryTest
 * @run testng HistoryTest
 */

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.internal.jline.extra.EditingHistory;
import org.testng.annotations.Test;
import jdk.internal.jshell.tool.JShellTool;
import jdk.internal.jshell.tool.JShellToolBuilder;
import static org.testng.Assert.*;

public class HistoryTest extends ReplToolTesting {

    private JShellTool repl;

    @Override
    protected void testRawRun(Locale locale, String[] args) {
        // turn on logging of launch failures
        Logger.getLogger("jdk.jshell.execution").setLevel(Level.ALL);
        repl = ((JShellToolBuilder) builder(locale))
                .rawTool();
        try {
            repl.start(args);
        } catch (Exception ex) {
            fail("Repl tool died with exception", ex);
        }
    }

    @Test
    public void testHistory() {
        test(
             a -> {if (!a) setCommandInput("void test() {\n");},
             a -> {if (!a) setCommandInput("    System.err.println(1);\n");},
             a -> {if (!a) setCommandInput("    System.err.println(1);\n");},
             a -> {assertCommand(a, "} //test", "|  created method test()");},
             a -> {
                 if (!a) {
                     try {
                         previousAndAssert(getHistory(), "} //test");
                         previousSnippetAndAssert(getHistory(), "void test() {");
                     } catch (Exception ex) {
                         throw new IllegalStateException(ex);
                     }
                 }
                 assertCommand(a, "int dummy;", "dummy ==> 0");
             });
        test(
             a -> {if (!a) setCommandInput("void test2() {\n");},
             a -> {assertCommand(a, "} //test2", "|  created method test2()");},
             a -> {
                 if (!a) {
                     try {
                         previousAndAssert(getHistory(), "} //test2");
                         previousSnippetAndAssert(getHistory(), "void test2() {");
                         previousSnippetAndAssert(getHistory(), "/debug 0"); //added by test framework
                         previousSnippetAndAssert(getHistory(), "/exit");
                         previousSnippetAndAssert(getHistory(), "int dummy;");
                         previousSnippetAndAssert(getHistory(), "void test() {");
                     } catch (Exception ex) {
                         throw new IllegalStateException(ex);
                     }
                 }
                 assertCommand(a, "int dummy;", "dummy ==> 0");
             });
    }

    @Test
    public void test8166744() {
        test(
             a -> {if (!a) setCommandInput("class C {\n");},
             a -> {if (!a) setCommandInput("void f() {\n");},
             a -> {if (!a) setCommandInput("}\n");},
             a -> {assertCommand(a, "}", "|  created class C");},
             a -> {
                 if (!a) {
                     try {
                         previousAndAssert(getHistory(), "}");
                         previousAndAssert(getHistory(), "}");
                         previousAndAssert(getHistory(), "void f() {");
                         previousAndAssert(getHistory(), "class C {");
                         getHistory().add("class C{");
                     } catch (Exception ex) {
                         throw new IllegalStateException(ex);
                     }
                 }
                 assertCommand(a, "int dummy;", "dummy ==> 0");
             });
        test(
             a -> {if (!a) setCommandInput("class C {\n");},
             a -> {if (!a) setCommandInput("void f() {\n");},
             a -> {if (!a) setCommandInput("}\n");},
             a -> {assertCommand(a, "}", "|  created class C");},
             a -> {
                 if (!a) {
                     try {
                         previousSnippetAndAssert(getHistory(), "class C {");
                         getHistory().add("class C{");
                     } catch (Exception ex) {
                         throw new IllegalStateException(ex);
                     }
                 }
                 assertCommand(a, "int dummy;", "dummy ==> 0");
             });
    }

    private EditingHistory getHistory() throws Exception {
        Field input = repl.getClass().getDeclaredField("input");
        input.setAccessible(true);
        Object console = input.get(repl);
        Field history = console.getClass().getDeclaredField("history");
        history.setAccessible(true);
        return (EditingHistory) history.get(console);
    }

    private void previousAndAssert(EditingHistory history, String expected) {
        assertTrue(history.previous());
        assertEquals(history.current().toString(), expected);
    }

    private void previousSnippetAndAssert(EditingHistory history, String expected) {
        assertTrue(history.previousSnippet());
        assertEquals(history.current().toString(), expected);
    }

}
