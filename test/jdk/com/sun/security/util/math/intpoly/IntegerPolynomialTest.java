/*
 * Copyright (c) 2024, Intel Corporation. All rights reserved.
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
import java.util.Arrays;
import sun.security.util.math.*;
import sun.security.util.math.intpoly.*;

/*
 * @test
 * @key randomness
 * @modules java.base/sun.security.util java.base/sun.security.util.math
 * java.base/sun.security.util.math.intpoly
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-UseIntPolyIntrinsics
 * IntegerPolynomialTest
 * @summary Unit test
 * IntegerPolynomial.MutableIntegerModuloP.conditionalAssign().
 */

/*
 * @test
 * @key randomness
 * @modules java.base/sun.security.util java.base/sun.security.util.math
 * java.base/sun.security.util.math.intpoly
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xcomp
 * -XX:-TieredCompilation -XX:+UseIntPolyIntrinsics IntegerPolynomialTest
 * @summary Unit test
 * IntegerPolynomial.MutableIntegerModuloP.conditionalAssign().
 */

// This test case is NOT entirely deterministic, it uses a random seed for
// pseudo-random number generator. If a failure occurs, hardcode the seed to
// make the test case deterministic
public class IntegerPolynomialTest {
    public static void main(String[] args) throws Exception {
        Random rnd = new Random();
        long seed = rnd.nextLong();
        rnd.setSeed(seed);

        IntegerPolynomial testFields[] = new IntegerPolynomial[] {
                IntegerPolynomial1305.ONE, IntegerPolynomial25519.ONE,
                IntegerPolynomial448.ONE, IntegerPolynomialP256.ONE,
                MontgomeryIntegerPolynomialP256.ONE, IntegerPolynomialP384.ONE,
                IntegerPolynomialP521.ONE,
                new IntegerPolynomialModBinP.Curve25519OrderField(),
                new IntegerPolynomialModBinP.Curve448OrderField(),
                P256OrderField.ONE, P384OrderField.ONE, P521OrderField.ONE,
                Curve25519OrderField.ONE, Curve448OrderField.ONE };

        for (IntegerPolynomial field : testFields) {
            ImmutableIntegerModuloP aRef = field
                    .getElement(new BigInteger(32 * 64, rnd));
            MutableIntegerModuloP a = aRef.mutable();
            ImmutableIntegerModuloP bRef = field
                    .getElement(new BigInteger(32 * 64, rnd));
            MutableIntegerModuloP b = bRef.mutable();

            a.conditionalSet(b, 0); // Don't assign
            if (Arrays.equals(a.getLimbs(), b.getLimbs())) {
                throw new RuntimeException(
                        "[SEED " + seed + "]: Incorrect assign for " + field);
            }
            a.conditionalSet(b, 1); // Assign
            if (!Arrays.equals(a.getLimbs(), b.getLimbs())) {
                throw new RuntimeException(
                        "[SEED " + seed + "]: Incorrect assign for " + field);
            }
        }
        System.out.println("Test Success");
    }
}

//make test TEST="test/jdk/com/sun/security/util/math/intpoly/IntegerPolynomialTest.java"
