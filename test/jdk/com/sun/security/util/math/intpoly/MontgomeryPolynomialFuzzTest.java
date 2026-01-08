/*
 * Copyright (c) 2024, 2025, Intel Corporation. All rights reserved.
 *
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

import java.util.Random;
import java.math.BigInteger;
import sun.security.util.math.*;
import sun.security.util.math.intpoly.*;

/*
 * @test
 * @key randomness
 * @modules java.base/sun.security.util java.base/sun.security.util.math
 *          java.base/sun.security.util.math.intpoly
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-UseIntPolyIntrinsics
 *      MontgomeryPolynomialFuzzTest
 * @summary Unit test MontgomeryPolynomialFuzzTest without intrinsic, plain java
 */

/*
 * @test
 * @key randomness
 * @modules java.base/sun.security.util java.base/sun.security.util.math
 *          java.base/sun.security.util.math.intpoly
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseIntPolyIntrinsics
 *      MontgomeryPolynomialFuzzTest
 * @summary Unit test MontgomeryPolynomialFuzzTest with intrinsic enabled
 */

// This test case is NOT entirely deterministic, it uses a random seed for
// pseudo-random number generator
// If a failure occurs, hardcode the seed to make the test case deterministic
public class MontgomeryPolynomialFuzzTest {
    public static void main(String[] args) throws Exception {
        // Note: it might be useful to increase this number during development
        final int repeat = 1000000;
        for (int i = 0; i < repeat; i++) {
            run();
        }
        System.out.println("Fuzz Success");
    }

    private static void checkOverflow(String opMsg,
            ImmutableIntegerModuloP testValue, long seed) {
        long limbs[] = testValue.getLimbs();
        BigInteger mod = MontgomeryIntegerPolynomialP256.ONE.MODULUS;
        BigInteger ref = BigInteger.ZERO;
        for (int i = 0; i<limbs.length; i++) {
            ref.add(BigInteger.valueOf(limbs[i]).shiftLeft(i*52));
        }
        if (ref.compareTo(mod)!=-1) {
            String msg = "Error while " + opMsg + System.lineSeparator()
                + ref.toString(16) + " != " + mod.toString(16) + System.lineSeparator()
                + "To reproduce, set SEED to [" + seed + "L]: ";
            throw new RuntimeException(msg);
        }
    }

    private static void check(String opMsg, BigInteger reference,
            ImmutableIntegerModuloP testValue, long seed) {
        BigInteger test = testValue.asBigInteger();
        if (!reference.equals(test)) {
            String msg = "Error while " + opMsg + System.lineSeparator()
                + reference.toString(16) + " != " + test.toString(16)
                + System.lineSeparator()+ "To reproduce, set SEED to ["
                + seed + "L]: ";
            throw new RuntimeException(msg);
        }
    }

    public static void run() throws Exception {
        Random rnd = new Random();
        // To reproduce an error, fix the value of the seed to the value from
        // the failure
        long seed = rnd.nextLong();
        rnd.setSeed(seed);

        IntegerMontgomeryFieldModuloP montField = MontgomeryIntegerPolynomialP256.ONE;
        BigInteger P = MontgomeryIntegerPolynomialP256.ONE.MODULUS;
        BigInteger r = BigInteger.ONE.shiftLeft(260).mod(P);
        BigInteger rInv = r.modInverse(P);
        BigInteger aRef = (new BigInteger(P.bitLength(), rnd)).mod(P);
        BigInteger bRef = (new BigInteger(P.bitLength(), rnd)).mod(P);
        SmallValue two = montField.getSmallValue(2);
        SmallValue three = montField.getSmallValue(3);
        SmallValue four = montField.getSmallValue(4);

        // Test conversion to montgomery domain
        ImmutableIntegerModuloP a = montField.getElement(aRef);
        String msg = "converting "+aRef.toString(16) + " to montgomery domain";
        aRef = aRef.multiply(r).mod(P);
        check(msg, aRef, a, seed);
        checkOverflow(msg, a, seed);

        ImmutableIntegerModuloP b = montField.getElement(bRef);
        msg = "converting "+aRef.toString(16) + " to montgomery domain";
        bRef = bRef.multiply(r).mod(P);
        check(msg, bRef, b, seed);
        checkOverflow(msg, b, seed);

        if (rnd.nextBoolean()) {
            msg = "squaring "+aRef.toString(16);
            aRef = aRef.multiply(aRef).multiply(rInv).mod(P);
            a = a.multiply(a);
            check(msg, aRef, a, seed);
            checkOverflow(msg, a, seed);
        }

        if (rnd.nextBoolean()) {
            msg = "doubling "+aRef.toString(16);
            aRef = aRef.add(aRef).mod(P);
            a = a.add(a);
            check(msg, aRef, a, seed);
        }

        if (rnd.nextBoolean()) {
            msg = "subtracting "+bRef.toString(16)+" from "+aRef.toString(16);
            aRef = aRef.subtract(bRef).mod(P);
            a = a.mutable().setDifference(b).fixed();
            check(msg, aRef, a, seed);
        }

        if (rnd.nextBoolean()) {
            msg = "multiplying "+bRef.toString(16)+" with "+aRef.toString(16);
            aRef = aRef.multiply(bRef).multiply(rInv).mod(P);
            a = a.multiply(b);
            check(msg, aRef, a, seed);
            checkOverflow(msg, a, seed);
        }

        if (rnd.nextBoolean()) {
            msg = "multiplying "+aRef.toString(16)+" with constant 2";
            aRef = aRef.multiply(BigInteger.valueOf(2)).mod(P);
            a = a.mutable().setProduct(two).fixed();
            check(msg, aRef, a, seed);
        }

        if (rnd.nextBoolean()) {
            msg = "multiplying "+aRef.toString(16)+" with constant 3";
            aRef = aRef.multiply(BigInteger.valueOf(3)).mod(P);
            a = a.mutable().setProduct(three).fixed();
            check(msg, aRef, a, seed);
            checkOverflow(msg, a, seed);
        }

        if (rnd.nextBoolean()) {
            msg = "multiplying "+aRef.toString(16)+" with constant 4";
            aRef = aRef.multiply(BigInteger.valueOf(4)).mod(P);
            a = a.mutable().setProduct(four).fixed();
            check(msg, aRef, a, seed);
            checkOverflow(msg, a, seed);
        }
    }
}

//make test TEST="test/jdk/com/sun/security/util/math/intpoly/MontgomeryPolynomialFuzzTest.java"
