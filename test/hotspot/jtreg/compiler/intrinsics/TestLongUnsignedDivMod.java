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
* @summary Test intrinsic for divideUnsigned() and remainderUnsigned() methods for Long
* @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="riscv64"
* @library /test/lib /
* @run driver compiler.intrinsics.TestLongUnsignedDivMod
*/

package compiler.intrinsics;
import compiler.lib.ir_framework.*;
import java.math.*;

public class TestLongUnsignedDivMod {
    private int BUFFER_SIZE;
    private long[] dividends;
    private long[] divisors;
    private long[] quotients;
    private long[] remainders;
    long TWO_31 = 1L << Integer.SIZE - 1;
        long TWO_32 = 1L << Integer.SIZE;
    long TWO_33 = 1L << Integer.SIZE + 1;
    BigInteger NINETEEN = BigInteger.valueOf(19L);
    BigInteger TWO_63 = BigInteger.ONE.shiftLeft(Long.SIZE - 1);
    BigInteger TWO_64 = BigInteger.ONE.shiftLeft(Long.SIZE);
    BigInteger[] inRange = {
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.TEN,
            NINETEEN,

            BigInteger.valueOf(TWO_31 - 19L),
            BigInteger.valueOf(TWO_31 - 10L),
            BigInteger.valueOf(TWO_31 - 1L),
            BigInteger.valueOf(TWO_31),
            BigInteger.valueOf(TWO_31 + 1L),
            BigInteger.valueOf(TWO_31 + 10L),
            BigInteger.valueOf(TWO_31 + 19L),

            BigInteger.valueOf(TWO_32 - 19L),
            BigInteger.valueOf(TWO_32 - 10L),
            BigInteger.valueOf(TWO_32 - 1L),
            BigInteger.valueOf(TWO_32),
            BigInteger.valueOf(TWO_32 + 1L),
            BigInteger.valueOf(TWO_32 + 10L),
            BigInteger.valueOf(TWO_32 - 19L),

            BigInteger.valueOf(TWO_33 - 19L),
            BigInteger.valueOf(TWO_33 - 10L),
            BigInteger.valueOf(TWO_33 - 1L),
            BigInteger.valueOf(TWO_33),
            BigInteger.valueOf(TWO_33 + 1L),
            BigInteger.valueOf(TWO_33 + 10L),
            BigInteger.valueOf(TWO_33 + 19L),

            TWO_63.subtract(NINETEEN),
            TWO_63.subtract(BigInteger.TEN),
            TWO_63.subtract(BigInteger.ONE),
            TWO_63,
            TWO_63.add(BigInteger.ONE),
            TWO_63.add(BigInteger.TEN),
            TWO_63.add(NINETEEN),

            TWO_64.subtract(NINETEEN),
            TWO_64.subtract(BigInteger.TEN),
            TWO_64.subtract(BigInteger.ONE),
    };

    public static void main(String args[]) {
        TestFramework.run(TestLongUnsignedDivMod.class);
    }

    public TestLongUnsignedDivMod() {
        BUFFER_SIZE = inRange.length * inRange.length;
        dividends = new long[BUFFER_SIZE];
        divisors = new long[BUFFER_SIZE];
        quotients = new long[BUFFER_SIZE];
        remainders = new long[BUFFER_SIZE];

        int idx = 0;
        for (int i = 0; i < inRange.length; i++) {
            for (int j = 0; j < inRange.length; j++){
                dividends[idx] = inRange[i].longValue();
                divisors[idx] = inRange[j].longValue();
                idx++;
            }
        }
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {IRNode.UDIV_L, ">= 1"}) // At least one UDivL node is generated if intrinsic is used
    public void testDivideUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            try {
                quotients[i] = Long.divideUnsigned(dividends[i], divisors[i]);
            } catch(ArithmeticException ea) {
                ; // expected
            }
        }
        checkResult("divideUnsigned");
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {IRNode.UMOD_L, ">= 1"}) // At least one UModL node is generated if intrinsic is used
    public void testRemainderUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            try {
                remainders[i] = Long.remainderUnsigned(dividends[i], divisors[i]);
            } catch(ArithmeticException ea) {
                ; // expected
            }
        }
        checkResult("remainderUnsigned");
    }


    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {IRNode.UDIV_MOD_L, ">= 1"}) // Atleast one UDivModL node is generated if intrinsic is used
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

    private void divmod(long dividend, long divisor, int i) {
        quotients[i] = Long.divideUnsigned(dividend, divisor);
        remainders[i] = Long.remainderUnsigned(dividend, divisor);
    }

    public void checkResult(String mode) {
        for (int i=0; i < BUFFER_SIZE; i++) {
            if (divisors[i] == 0) continue;
            BigInteger dividend = toUnsignedBigInteger(dividends[i]);
            BigInteger divisor = toUnsignedBigInteger(divisors[i]);

            long quo = dividend.divide(divisor).longValue();
            long rem = dividend.remainder(divisor).longValue();
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

    private String errorMessage(String mode, int i, long quo, long rem) {
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

    private BigInteger toUnsignedBigInteger(long i) {
        if (i >= 0L)
            return BigInteger.valueOf(i);
        else {
            int upper = (int) (i >>> 32);
            int lower = (int) i;

            // return (upper << 32) + lower
            return (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).
                add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
        }
    }

}
