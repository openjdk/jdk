/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.ea;

import org.junit.Test;

import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ValuePhiNode;

public class EAMergingTest extends EATestBase {

    @Test
    public void testSimpleMerge() {
        testEscapeAnalysis("simpleMergeSnippet", null, false);
        assertDeepEquals(1, returnNodes.size());
        assertTrue(returnNodes.get(0).result() instanceof ValuePhiNode);
        PhiNode phi = (PhiNode) returnNodes.get(0).result();
        assertTrue(phi.valueAt(0) instanceof ParameterNode);
        assertTrue(phi.valueAt(1) instanceof ParameterNode);
    }

    public static int simpleMergeSnippet(boolean b, int u, int v) {
        TestClassInt obj;
        if (b) {
            obj = new TestClassInt(u, 0);
            notInlineable();
        } else {
            obj = new TestClassInt(v, 0);
            notInlineable();
        }
        return obj.x;
    }
}
