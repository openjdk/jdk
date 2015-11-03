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
 * @summary Testing IllegalStateException.
 * @build KullaTesting TestingInputStream JShellStateClosedTest
 * @run testng JShellStateClosedTest
 */

import java.util.function.Consumer;

import jdk.jshell.DeclarationSnippet;
import jdk.jshell.PersistentSnippet;
import jdk.jshell.Snippet;
import jdk.jshell.VarSnippet;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

@Test
public class JShellStateClosedTest extends KullaTesting {

    private void testStateClosedException(Runnable action) {
        getState().close();
        try {
            action.run();
            fail("Exception expected");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    public void testClasses() {
        testStateClosedException(() -> getState().types());
    }

    public void testVariables() {
        testStateClosedException(() -> getState().variables());
    }

    public void testMethods() {
        testStateClosedException(() -> getState().methods());
    }

    public void testKeys() {
        testStateClosedException(() -> getState().snippets());
    }

    public void testEval() {
        testStateClosedException(() -> getState().eval("int a;"));
    }

    private void testStateClosedException(Consumer<Snippet> action) {
        Snippet k = varKey(assertEval("int a;"));
        getState().close();
        try {
            action.accept(k);
            fail("IllegalStateException expected since closed");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    private void testStateClosedWithoutException(Consumer<Snippet> action) {
        Snippet k = varKey(assertEval("int a;"));
        getState().close();
        try {
            action.accept(k);
        } catch (IllegalStateException e) {
            fail("Expected no IllegalStateException even though closed");
        }
    }

    public void testStatus() {
        testStateClosedWithoutException((key) -> getState().status(key));
    }

    public void testVarValue() {
        testStateClosedException((key) -> getState().varValue((VarSnippet) key));
    }

    public void testDrop() {
        testStateClosedException((key) -> getState().drop((PersistentSnippet) key));
    }

    public void testUnresolved() {
        testStateClosedWithoutException((key) -> getState().unresolvedDependencies((DeclarationSnippet) key));
    }

    public void testDiagnostics() {
        testStateClosedWithoutException((key) -> getState().diagnostics(key));
    }
}
