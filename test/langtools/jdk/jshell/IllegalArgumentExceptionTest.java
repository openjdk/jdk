/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing IllegalArgumentException.
 * @build KullaTesting TestingInputStream IllegalArgumentExceptionTest
 * @run junit IllegalArgumentExceptionTest
 */

import java.util.function.Consumer;

import jdk.jshell.DeclarationSnippet;
import jdk.jshell.Snippet;
import jdk.jshell.VarSnippet;
import static org.junit.jupiter.api.Assertions.fail;
import static jdk.jshell.Snippet.Status.VALID;
import org.junit.jupiter.api.Test;

public class IllegalArgumentExceptionTest extends KullaTesting {

    private void testIllegalArgumentException(Consumer<Snippet> action) {
        Snippet key = varKey(assertEval("int value;", added(VALID)));
        tearDown();
        setUp();
        assertEval("double value;");
        try {
            action.accept(key);
            fail("Exception expected.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testVarValue() {
        testIllegalArgumentException((key) -> getState().varValue((VarSnippet) key));
    }

    @Test
    public void testStatus() {
        testIllegalArgumentException((key) -> getState().status(key));
    }

    @Test
    public void testDrop() {
        testIllegalArgumentException((key) -> getState().drop(key));
    }

    @Test
    public void testUnresolved() {
        testIllegalArgumentException((key) -> getState().unresolvedDependencies((DeclarationSnippet) key));
    }

    @Test
    public void testDiagnostics() {
        testIllegalArgumentException((key) -> getState().diagnostics(key));
    }
}
