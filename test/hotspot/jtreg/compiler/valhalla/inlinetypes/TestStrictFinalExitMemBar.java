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

/*
 * @test
 * @bug 8369166
 * @summary Verify constructor exit barriers are emitted for non-strict finals but not for strict finals.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @compile TestStrictFinalExitMemBar.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             compiler.valhalla.inlinetypes.TestStrictFinalExitMemBar$StrictFinalHolder
 *             compiler.valhalla.inlinetypes.TestStrictFinalExitMemBar$StrictNonFinalHolder
 * @run driver ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.helpers.StrictInit;

public class TestStrictFinalExitMemBar {
    static Object sink;

    static class StrictFinalHolder {
        @StrictInit
        final int x;

        StrictFinalHolder(int x) {
            this.x = x;
            super();
        }
    }

    static class NonStrictFinalHolder {
        final int x;

        NonStrictFinalHolder(int x) {
            this.x = x;
            super();
        }
    }

    static class StrictNonFinalHolder {
        @StrictInit
        int x;

        StrictNonFinalHolder(int x) {
            this.x = x;
            super();
        }
    }

    static class NonStrictNonFinalHolder {
        int x;

        NonStrictNonFinalHolder(int x) {
            this.x = x;
            super();
        }
    }

    @Test
    @IR(counts = {"MemBar(StoreStore|Release).*Object::<init>\\s+@ bci:-1", "1"},
        failOn = {"MemBar(StoreStore|Release).*StrictFinalHolder::<init>\\s+@ bci:-1"},
        phase = CompilePhase.BEFORE_MATCHING)
    public static int testStrictFinalNoExitMemBar() {
        StrictFinalHolder holder = new StrictFinalHolder(42);
        sink = holder;
        return holder.x;
    }

    @Run(test = "testStrictFinalNoExitMemBar")
    public static void testStrictFinalNoExitMemBarRunner() {
        Asserts.assertEquals(testStrictFinalNoExitMemBar(), 42);
    }

    @Test
    @IR(counts = {"MemBar(StoreStore|Release).*NonStrictFinalHolder::<init>\\s+@ bci:-1", "1"},
        failOn = {"MemBar(StoreStore|Release).*Object::<init>\\s+@ bci:-1"},
        phase = CompilePhase.BEFORE_MATCHING)
    public static int testNonStrictFinalHasExitMemBar() {
        NonStrictFinalHolder holder = new NonStrictFinalHolder(42);
        sink = holder;
        return holder.x;
    }

    @Run(test = "testNonStrictFinalHasExitMemBar")
    public static void testNonStrictFinalHasExitMemBarRunner() {
        Asserts.assertEquals(testNonStrictFinalHasExitMemBar(), 42);
    }

    @Test
    @IR(counts = {"MemBar(StoreStore|Release).*Object::<init>\\s+@ bci:-1", "1"},
        failOn = {"MemBar(StoreStore|Release).*StrictNonFinalHolder::<init>\\s+@ bci:-1"},
        phase = CompilePhase.BEFORE_MATCHING)
    public static int testStrictNonFinalNoExitMemBar() {
        StrictNonFinalHolder holder = new StrictNonFinalHolder(42);
        sink = holder;
        return holder.x;
    }

    @Run(test = "testStrictNonFinalNoExitMemBar")
    public static void testStrictNonFinalNoExitMemBarRunner() {
        Asserts.assertEquals(testStrictNonFinalNoExitMemBar(), 42);
    }

    @Test
    @IR(failOn = {"MemBar(StoreStore|Release).*Object::<init>\\s+@ bci:-1",
                  "MemBar(StoreStore|Release).*NonStrictNonFinalHolder::<init>\\s+@ bci:-1"},
        phase = CompilePhase.BEFORE_MATCHING)
    public static int testNonStrictNonFinalNoMemBar() {
        NonStrictNonFinalHolder holder = new NonStrictNonFinalHolder(42);
        sink = holder;
        return holder.x;
    }

    @Run(test = "testNonStrictNonFinalNoMemBar")
    public static void testNonStrictNonFinalNoMemBarRunner() {
        Asserts.assertEquals(testNonStrictNonFinalNoMemBar(), 42);
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--enable-preview");
    }
}
