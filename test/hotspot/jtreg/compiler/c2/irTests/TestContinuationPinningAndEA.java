/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.continuations
 * @bug 8347997
 * @library /test/lib /
 * @summary Test that Continuation.pin() and unpin() intrinsics work with EA.
 * @modules java.base/jdk.internal.vm
 * @run driver compiler.c2.irTests.TestContinuationPinningAndEA
 */

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.internal.vm.Continuation;

public class TestContinuationPinningAndEA {
    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED");
    }

    @Run(test = {
            "test_AllocPinUnpin", "test_PinUnpinAlloc",
            "test_AllocPinUnpinNoInline", "test_PinUnpinAllocNoInline",
    })
    public void runMethod() {
        test_AllocPinUnpin();
        test_PinUnpinAlloc();
        test_AllocPinUnpinNoInline();
        test_PinUnpinAllocNoInline();
    }

    // ===Cases where allocations are removed===
    static class AllocPinUnpin {
        final Object o;

        @ForceInline
        public AllocPinUnpin() {
            o = new Object();
            Continuation.pin();
            Continuation.unpin();
        }
    }

    static class PinUnpinAlloc {
        final Object o;

        @ForceInline
        public PinUnpinAlloc() {
            Continuation.pin();
            Continuation.unpin();
            o = new Object();
        }
    }

    @Test
    @IR(failOn = {IRNode.ALLOC})
    void test_AllocPinUnpin() {
        new AllocPinUnpin();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC})
    void test_PinUnpinAlloc() {
        new PinUnpinAlloc();
    }

    // ===Sanity check that allocations would happen===
    static class AllocPinUnpinNoInline {
        final Object o;

        @DontInline
        public AllocPinUnpinNoInline() {
            o = new Object();
            Continuation.pin();
            Continuation.unpin();
        }
    }

    static class PinUnpinAllocNoInline {
        final Object o;

        @DontInline
        public PinUnpinAllocNoInline() {
            Continuation.pin();
            Continuation.unpin();
            o = new Object();
        }
    }

    @Test
    @IR(counts = {IRNode.ALLOC, ">0"})
    void test_AllocPinUnpinNoInline() {
        new AllocPinUnpinNoInline();
    }

    @Test
    @IR(counts = {IRNode.ALLOC, ">0"})
    void test_PinUnpinAllocNoInline() {
        new PinUnpinAllocNoInline();
    }
}
