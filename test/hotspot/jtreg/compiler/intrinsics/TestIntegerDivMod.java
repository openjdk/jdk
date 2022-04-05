/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
* @summary Test x86_64 intrinsic for divideUnsigned() and remainderUnsigned() methods for Integer
* @requires os.arch=="amd64" | os.arch=="x86_64"
* @library /test/lib /
* @run driver compiler.intrinsics.TestIntegerDivMod
*/

package compiler.intrinsics;
import compiler.lib.ir_framework.*;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;


public class TestIntegerDivMod {
    private final int BUFFER_SIZE = 1024;
    private int[] dividends = new int[BUFFER_SIZE];
    private int[] divisors = new int[BUFFER_SIZE];
    private int[] quotients =  new int[BUFFER_SIZE];
    private int[] remainders =  new int[BUFFER_SIZE];
    private RandomGenerator rng;

    public static void main(String args[]) {
        TestFramework.run(TestIntegerDivMod.class);
    }

    public TestIntegerDivMod() {
        rng = RandomGeneratorFactory.getDefault().create(0);
        for (int i = 0; i < BUFFER_SIZE; i++) {
            dividends[i] = rng.nextInt();
            divisors[i] = rng.nextInt();
        }
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {"UDivI", ">= 1"}) // Atleast one UDivI node is generated if intrinsic is used
    public void testDivideUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            quotients[i] = Integer.divideUnsigned(dividends[i], divisors[i]);
        }
        checkResult("divideUnsigned");
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {"UModI", ">= 1"}) // Atleast one UModI node is generated if intrinsic is used
    public void testRemainderUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            remainders[i] = Integer.remainderUnsigned(dividends[i], divisors[i]);
        }
        checkResult("remainderUnsigned");
    }


    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {"UDivModI", ">= 1"}) // Atleast one UDivModI node is generated if intrinsic is used
    public void testDivModUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++)  divmod(dividends[i], divisors[i], i);
        checkResult("divmodUnsigned");
    }

    private void divmod(int dividend, int divisor, int i) {
        quotients[i] = Integer.divideUnsigned(dividend, divisor);
        remainders[i] = Integer.remainderUnsigned(dividend, divisor);
    }

    public void checkResult(String mode) {
        for (int i=0; i < BUFFER_SIZE; i++) {
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
                throw new RuntimeException("Test failed");
            }
        }
    }
}
