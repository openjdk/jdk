/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8178821 8198670
 * @summary Test Completion
 * @modules jdk.internal.le/jdk.internal.jline
 *          jdk.internal.le/jdk.internal.jline.console
 *          jdk.internal.le/jdk.internal.jline.console.history
 *          jdk.internal.le/jdk.internal.jline.extra
 * @build HistoryTest
 * @run testng HistoryTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.internal.jline.UnsupportedTerminal;
import jdk.internal.jline.console.ConsoleReader;
import jdk.internal.jline.console.history.MemoryHistory;
import jdk.internal.jline.extra.EditingHistory;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Test
public class HistoryTest {

    public void testHistory() throws IOException {
        ConsoleReader in = new ConsoleReader(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), new UnsupportedTerminal());
        AtomicBoolean complete = new AtomicBoolean();
        EditingHistory history = new EditingHistory(in, Collections.emptyList()) {
            @Override
            protected boolean isComplete(CharSequence input) {
                return complete.get();
            }
        };
        complete.set(false); history.add("void test() {");
        complete.set(false); history.add("    System.err.println(1);");
        complete.set(true);  history.add("}");
        complete.set(true);  history.add("/exit");

        previousAndAssert(history, "/exit");

        history.previous(); history.previous(); history.previous();

        complete.set(false); history.add("void test() { /*changed*/");

        complete.set(true);
        previousAndAssert(history, "}");
        previousAndAssert(history, "    System.err.println(1);");
        previousAndAssert(history, "void test() {");

        assertFalse(history.previous());

        nextAndAssert(history, "    System.err.println(1);");
        nextAndAssert(history, "}");
        nextAndAssert(history, "");

        complete.set(false); history.add("    System.err.println(2);");
        complete.set(true);  history.add("} /*changed*/");

        assertEquals(history.size(), 7);

        Collection<? extends String> persistentHistory = history.save();

        history = new EditingHistory(in, persistentHistory) {
            @Override
            protected boolean isComplete(CharSequence input) {
                return complete.get();
            }
        };

        previousSnippetAndAssert(history, "void test() { /*changed*/");
        previousSnippetAndAssert(history, "/exit");
        previousSnippetAndAssert(history, "void test() {");

        assertFalse(history.previousSnippet());

        nextSnippetAndAssert(history, "/exit");
        nextSnippetAndAssert(history, "void test() { /*changed*/");
        nextSnippetAndAssert(history, "");

        assertFalse(history.nextSnippet());

        complete.set(false); history.add("{");
        complete.set(true);  history.add("}");

        persistentHistory = history.save();

        history = new EditingHistory(in, persistentHistory) {
            @Override
            protected boolean isComplete(CharSequence input) {
                return complete.get();
            }
        };

        previousSnippetAndAssert(history, "{");
        previousSnippetAndAssert(history, "void test() { /*changed*/");
        previousSnippetAndAssert(history, "/exit");
        previousSnippetAndAssert(history, "void test() {");

        while (history.next());

        complete.set(true);  history.add("/*current1*/");
        complete.set(true);  history.add("/*current2*/");
        complete.set(true);  history.add("/*current3*/");

        assertEquals(history.entries(true), Arrays.asList("/*current1*/", "/*current2*/", "/*current3*/"));
        assertEquals(history.entries(false), Arrays.asList(
                "void test() {",
                "    System.err.println(1);",
                "}",
                "/exit",
                "void test() { /*changed*/",
                "    System.err.println(2);",
                "} /*changed*/",
                "{",
                "}",
                "/*current1*/", "/*current2*/", "/*current3*/"), history.entries(false).toString());

        history.remove(0);

        assertEquals(history.entries(true), Arrays.asList("/*current1*/", "/*current2*/", "/*current3*/"));

        while (history.size() > 2)
            history.remove(0);

        assertEquals(history.entries(true), Arrays.asList("/*current2*/", "/*current3*/"));

        for (int i = 0; i < MemoryHistory.DEFAULT_MAX_SIZE * 2; i++) {
            complete.set(true);  history.add("/exit");
        }

        complete.set(false); history.add("void test() { /*after full*/");
        complete.set(false); history.add("    System.err.println(1);");
        complete.set(true);  history.add("}");

        previousSnippetAndAssert(history, "void test() { /*after full*/");
        nextSnippetAndAssert(history, "");

        assertFalse(history.nextSnippet());

        while (history.previousSnippet())
            ;

        while (history.nextSnippet())
            ;
    }

    private void previousAndAssert(EditingHistory history, String expected) {
        assertTrue(history.previous());
        assertEquals(history.current().toString(), expected);
    }

    private void nextAndAssert(EditingHistory history, String expected) {
        assertTrue(history.next());
        assertEquals(history.current().toString(), expected);
    }

    private void previousSnippetAndAssert(EditingHistory history, String expected) {
        assertTrue(history.previousSnippet());
        assertEquals(history.current().toString(), expected);
    }

    private void nextSnippetAndAssert(EditingHistory history, String expected) {
        assertTrue(history.nextSnippet());
        assertEquals(history.current().toString(), expected);
    }

}
