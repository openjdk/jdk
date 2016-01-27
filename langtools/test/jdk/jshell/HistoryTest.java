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
 * @summary Test Completion
 * @modules jdk.jshell/jdk.internal.jshell.tool
 *          jdk.internal.le/jdk.internal.jline.console.history
 * @build HistoryTest
 * @run testng HistoryTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import jdk.internal.jline.console.history.MemoryHistory;

import jdk.jshell.JShell;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import org.testng.annotations.Test;
import jdk.internal.jshell.tool.EditingHistory;

import static org.testng.Assert.*;

@Test
public class HistoryTest {

    public void testHistory() {
        JShell eval = JShell.builder()
                .in(new ByteArrayInputStream(new byte[0]))
                .out(new PrintStream(new ByteArrayOutputStream()))
                .err(new PrintStream(new ByteArrayOutputStream()))
                .build();
        SourceCodeAnalysis analysis = eval.sourceCodeAnalysis();
        MemoryPreferences prefs = new MemoryPreferences(null, "");
        EditingHistory history = new EditingHistory(prefs) {
            @Override protected CompletionInfo analyzeCompletion(String input) {
                return analysis.analyzeCompletion(input);
            }
        };
        history.add("void test() {");
        history.add("    System.err.println(1);");
        history.add("}");
        history.add("/exit");

        previousAndAssert(history, "/exit");

        history.previous(); history.previous(); history.previous();

        history.add("void test() { /*changed*/");

        previousAndAssert(history, "}");
        previousAndAssert(history, "    System.err.println(1);");
        previousAndAssert(history, "void test() {");

        assertFalse(history.previous());

        nextAndAssert(history, "    System.err.println(1);");
        nextAndAssert(history, "}");
        nextAndAssert(history, "");

        history.add("    System.err.println(2);");
        history.add("} /*changed*/");

        assertEquals(history.size(), 7);

        history.save();

        history = new EditingHistory(prefs) {
            @Override protected CompletionInfo analyzeCompletion(String input) {
                return analysis.analyzeCompletion(input);
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

        history.add("{");
        history.add("}");

        history.save();

        history = new EditingHistory(prefs) {
            @Override protected CompletionInfo analyzeCompletion(String input) {
                return analysis.analyzeCompletion(input);
            }
        };

        previousSnippetAndAssert(history, "{");
        previousSnippetAndAssert(history, "void test() { /*changed*/");
        previousSnippetAndAssert(history, "/exit");
        previousSnippetAndAssert(history, "void test() {");

        while (history.next());

        history.add("/*current1*/");
        history.add("/*current2*/");
        history.add("/*current3*/");

        assertEquals(history.currentSessionEntries(), Arrays.asList("/*current1*/", "/*current2*/", "/*current3*/"));

        history.remove(0);

        assertEquals(history.currentSessionEntries(), Arrays.asList("/*current1*/", "/*current2*/", "/*current3*/"));

        while (history.size() > 2)
            history.remove(0);

        assertEquals(history.currentSessionEntries(), Arrays.asList("/*current2*/", "/*current3*/"));

        for (int i = 0; i < MemoryHistory.DEFAULT_MAX_SIZE * 2; i++) {
            history.add("/exit");
        }

        history.add("void test() { /*after full*/");
        history.add("    System.err.println(1);");
        history.add("}");

        previousSnippetAndAssert(history, "void test() { /*after full*/");
    }

    public void testSaveOneHistory() {
        JShell eval = JShell.builder()
                .in(new ByteArrayInputStream(new byte[0]))
                .out(new PrintStream(new ByteArrayOutputStream()))
                .err(new PrintStream(new ByteArrayOutputStream()))
                .build();
        SourceCodeAnalysis analysis = eval.sourceCodeAnalysis();
        MemoryPreferences prefs = new MemoryPreferences(null, "");
        EditingHistory history = new EditingHistory(prefs) {
            @Override protected CompletionInfo analyzeCompletion(String input) {
                return analysis.analyzeCompletion(input);
            }
        };

        history.add("first");
        history.save();
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

    private static final class MemoryPreferences extends AbstractPreferences {

        private final Map<String, String> key2Value = new HashMap<>();
        private final Map<String, MemoryPreferences> key2SubNode = new HashMap<>();

        public MemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        @Override
        protected void putSpi(String key, String value) {
            key2Value.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return key2Value.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            key2Value.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            ((MemoryPreferences) parent()).key2SubNode.remove(name());
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return key2Value.keySet().toArray(new String[key2Value.size()]);
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return key2SubNode.keySet().toArray(new String[key2SubNode.size()]);
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return key2SubNode.computeIfAbsent(name, n -> new MemoryPreferences(this, n));
        }

        @Override
        protected void syncSpi() throws BackingStoreException {}

        @Override
        protected void flushSpi() throws BackingStoreException {}

    }

}
