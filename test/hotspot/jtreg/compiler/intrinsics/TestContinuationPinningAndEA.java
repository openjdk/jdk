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

/**
 * @test
 * @requires vm.continuations
 * @bug 8347997
 * @summary Test that Continuation.pin() and unpin() intrinsics work with EA.
 * @modules java.base/jdk.internal.vm
 * @run main TestContinuationPinningAndEA
 */

import jdk.internal.vm.Continuation;

public class TestContinuationPinningAndEA {

    static class FailsEA {
        final Object o;

        public FailsEA() throws Throwable {
            o = new Object();
            Continuation.pin();
            Continuation.unpin();
        }
    }

    static class Crashes {
        final Object o;

        public Crashes() throws Throwable {
            Continuation.pin();
            Continuation.unpin();
            o = new Object();
        }
    }

    static void test_FailsEA() throws Throwable {
        for (int i = 0; i < 10_000; ++i) {
            new FailsEA();
        }
    }

    static void test_Crashes() throws Throwable {
        for (int i = 0; i < 10_000; ++i) {
            new Crashes();
        }
    }

    public static void main(String[] args) throws Throwable {
        int iterations = 100;
        for (int i = 0; i < iterations; ++i) {
            test_FailsEA();
        }
        for (int i = 0; i < iterations; ++i) {
            test_Crashes();
        }
    }
}
