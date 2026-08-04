/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that oop fields of value classes are preserved over safepoints at returns.
 * @enablePreview
 * @run main/othervm -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestSafepointAtPollReturn::test* -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+SafepointALot -XX:-TieredCompilation -XX:+UseTLAB compiler.valhalla.inlinetypes.TestSafepointAtPollReturn
 */

package compiler.valhalla.inlinetypes;

public class TestSafepointAtPollReturn {
    static Integer INT_VAL = 0;

    static value class MyValue {
        Integer val = INT_VAL;
    }

    static public MyValue testValueCallee(boolean b) {
        return b ? null : new MyValue();
    }

    static public MyValue testValue(boolean b) {
        return testValueCallee(b);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1_000_000_000; ++i) {
            INT_VAL = i;
            boolean b = (i % 2) == 0;
            MyValue val = testValue(b);
            if (b) {
                if (val != null) {
                    throw new RuntimeException("testValue failed: result should be null");
                }
            } else {
                int res = val.val;
                if (res != i) {
                    throw new RuntimeException("testValue failed: " + res + " != " + i);
                }
            }
        }
    }
}
