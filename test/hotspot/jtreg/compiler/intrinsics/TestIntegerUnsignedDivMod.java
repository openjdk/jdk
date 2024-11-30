/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
* @summary Test intrinsic for divideUnsigned() and remainderUnsigned() methods for Integer
* @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="riscv64" | os.arch=="aarch64"
* @library /test/lib /
* @run driver compiler.intrinsics.TestIntegerUnsignedDivMod
*/

package compiler.intrinsics;
import compiler.lib.ir_framework.*;

public class TestIntegerUnsignedDivMod {
    private int BUFFER_SIZE;
    private int[] dividends;
    private int[] divisors;
    private int[] quotients;
    private int[] remainders;
    final long MAX_UNSIGNED_INT = Integer.toUnsignedLong(0xffff_ffff);
    long[] inRange = {
        0L,
        1L,
        2L,
        2147483646L,   // MAX_VALUE - 1
        2147483647L,   // MAX_VALUE
        2147483648L,   // MAX_VALUE + 1
        MAX_UNSIGNED_INT - 1L,
        MAX_UNSIGNED_INT,
    };

    public static void main(String args[]) {
        TestFramework.run(TestIntegerUnsignedDivMod.class);
    }

    public TestIntegerUnsignedDivMod() {
        BUFFER_SIZE = inRange.length * inRange.length;
        dividends = new int[BUFFER_SIZE];
        divisors = new int[BUFFER_SIZE];
        quotients = new int[BUFFER_SIZE];
        remainders = new int[BUFFER_SIZE];

        int idx = 0;
        for (int i = 0; i < inRange.length; i++) {
            for (int j = 0; j < inRange.length; j++){
                dividends[idx] = (int) inRange[i];
                divisors[idx] = (int) inRange[j];
                idx++;
            }
        }
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {IRNode.UDIV_I, ">= 1"}) // At least one UDivI node is generated if intrinsic is used
    public void testDivideUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            try {
                quotients[i] = Integer.divideUnsigned(dividends[i], divisors[i]);
            } catch(ArithmeticException ea) {
                ; // expected
            }
        }
        checkResult("divideUnsigned");
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {IRNode.UMOD_I, ">= 1"}) // At least one UModI node is generated if intrinsic is used
    public void testRemainderUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            try {
                remainders[i] = Integer.remainderUnsigned(dividends[i], divisors[i]);
            } catch(ArithmeticException ea) {
                ; // expected
            }
        }
        checkResult("remainderUnsigned");
    }


    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(applyIfPlatform = {"x64", "true"},
        counts = {IRNode.UDIV_MOD_I, ">= 1"}) // At least one UDivModI node is generated if intrinsic is used
    public void testDivModUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            try {
                divmod(dividends[i], divisors[i], i);
            } catch(ArithmeticException ea) {
                ; // expected
            }
        }
        checkResult("divmodUnsigned");
    }

    private void divmod(int dividend, int divisor, int i) {
        quotients[i] = Integer.divideUnsigned(dividend, divisor);
        remainders[i] = Integer.remainderUnsigned(dividend, divisor);
    }

    public void checkResult(String mode) {
        for (int i=0; i < BUFFER_SIZE; i++) {
            if (divisors[i] == 0) continue;
            long dividend = Integer.toUnsignedLong(dividends[i]);
            long divisor = Integer.toUnsignedLong(divisors[i]);
            int quo =  (int) (dividend / divisor);
            int rem = (int) (dividend % divisor);
            boolean mismatch;
            switch (mode) {
                case "divideUnsigned": mismatch = (quotients[i] != quo); break;
                case "remainderUnsigned": mismatch = (remainders[i] != rem); break;
                case "divmodUnsigned": mismatch = (quotients[i] != quo || remainders[i] != rem); break;
                default: throw new IllegalArgumentException("incorrect mode");
            }
            if (mismatch) {
                throw new RuntimeException(errorMessage(mode, i, quo, rem));
            }
        }
    }

    private String errorMessage(String mode, int i, int quo, int rem) {
        StringBuilder sb = new StringBuilder(mode);
        sb = sb.append(" test error at index=").append(i);
        sb = sb.append(": dividend=").append(dividends[i]);
        sb = sb.append("; divisor= ").append(divisors[i]);
        if (!mode.equals("remainderUnsigned")) {
            sb = sb.append("; quotient (expected)= ").append(quo);
            sb = sb.append("; quotient (actual)= ").append(quotients[i]);
        }
        if (!mode.equals("divideUnsigned")) {
            sb = sb.append("; remainder (expected)= ").append(rem);
            sb = sb.append("; remainder (actual)= ").append(remainders[i]);
        }
        return sb.toString();
    }

}
