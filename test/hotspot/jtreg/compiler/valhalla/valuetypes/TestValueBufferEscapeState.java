/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8392155
 * @summary C2 must preserve MemBarStoreStore barrier when buffering a returned value
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @enablePreview
 * @run driver ${test.main.class}
 */

public class TestValueBufferEscapeState {

    public static void main(String[] args) {
        TestFramework.runWithFlags("--enable-preview", "-XX:+DoEscapeAnalysis");
    }

    static value class Value {
        int field = 42;
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "> 0"})
    public static Object test(int index) {
        Value first = new Value();

        // Prevent C2 from trivial scalar replacement
        Object[] array = new Object[2];
        array[index & 1] = first;

        // Previously, C2 deemed the returned buffer redundant and replaced
        // it with the buffer for `first`, which is non-escaping and therefore had
        // its barrier removed by an earlier optimization. The barrier is
        // necessary for the returned buffer (as it escapes, by definition).
        return new Value();
    }
}
