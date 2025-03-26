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
        TestFramework.run();
    }

    @Run(test = {
            "test_FailsEA", "test_Crashes",
            "test_FailsEANoInline", "test_CrashesNoInline",
    })
    public void runMethod() {
        try {
            test_FailsEA();
        } catch (Throwable _) {
        }
        try {
            test_Crashes();
        } catch (Throwable _) {
        }
        try {
            test_FailsEANoInline();
        } catch (Throwable _) {
        }
        try {
            test_CrashesNoInline();
        } catch (Throwable _) {
        }
    }

    // ===Cases where allocations are removed===
    static class FailsEA {
        final Object o;

        @ForceInline
        public FailsEA() throws Throwable {
            o = new Object();
            Continuation.pin();
            Continuation.unpin();
        }
    }

    static class Crashes {
        final Object o;

        @ForceInline
        public Crashes() throws Throwable {
            Continuation.pin();
            Continuation.unpin();
            o = new Object();
        }
    }

    @Test
    @IR(failOn = {IRNode.ALLOC})
    static void test_FailsEA() throws Throwable {
        new FailsEA();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC})
    static void test_Crashes() throws Throwable {
        new Crashes();
    }

    // ===Sanity check that allocations would happen===
    static class FailsEANoInline {
        final Object o;

        @DontInline
        public FailsEANoInline() throws Throwable {
            o = new Object();
            Continuation.pin();
            Continuation.unpin();
        }
    }

    static class CrashesNoInline {
        final Object o;

        @DontInline
        public CrashesNoInline() throws Throwable {
            Continuation.pin();
            Continuation.unpin();
            o = new Object();
        }
    }

    @Test
    @IR(counts = {IRNode.ALLOC, ">0"})
    static void test_FailsEANoInline() throws Throwable {
        new FailsEANoInline();
    }

    @Test
    @IR(counts = {IRNode.ALLOC, ">0"})
    static void test_CrashesNoInline() throws Throwable {
        new CrashesNoInline();
    }
}
