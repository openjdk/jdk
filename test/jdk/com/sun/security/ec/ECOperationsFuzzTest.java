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
import java.lang.reflect.Field;
import java.security.spec.ECParameterSpec;
import sun.security.ec.ECOperations;
import sun.security.util.ECUtil;
import sun.security.util.NamedCurve;
import sun.security.util.CurveDB;
import sun.security.ec.point.*;
import java.security.spec.ECPoint;
import sun.security.util.KnownOIDs;
import sun.security.util.math.IntegerMontgomeryFieldModuloP;
import sun.security.util.math.intpoly.*;

/*
 * @test
 * @key randomness
 * @modules java.base/sun.security.ec java.base/sun.security.ec.point
 *          java.base/sun.security.util java.base/sun.security.util.math
 *          java.base/sun.security.util.math.intpoly
 * @run main/othervm/timeout=1200 --add-opens
 *      java.base/sun.security.ec=ALL-UNNAMED -XX:+UnlockDiagnosticVMOptions
 *      -XX:-UseIntPolyIntrinsics ECOperationsFuzzTest
 * @summary Unit test ECOperationsFuzzTest.
 */

/*
 * @test
 * @key randomness
 * @modules java.base/sun.security.ec java.base/sun.security.ec.point
 *          java.base/sun.security.util java.base/sun.security.util.math
 *          java.base/sun.security.util.math.intpoly
 * @run main/othervm/timeout=1200 --add-opens
 *      java.base/sun.security.ec=ALL-UNNAMED -XX:+UnlockDiagnosticVMOptions
 *      -XX:+UseIntPolyIntrinsics ECOperationsFuzzTest
 * @summary Unit test ECOperationsFuzzTest.
 */

// This test case is NOT entirely deterministic, it uses a random seed for
// pseudo-random number generator. If a failure occurs, hardcode the seed to
// make the test case deterministic
public class ECOperationsFuzzTest {
    public static void main(String[] args) throws Exception {
        // Note: it might be useful to increase this number during development
        final int repeat = 10000;
        test(repeat);
        System.out.println("Fuzz Success");
    }

    private static void check(MutablePoint reference, MutablePoint testValue,
            long seed, int iter) {
        AffinePoint affineRef = reference.asAffine();
        AffinePoint affine = testValue.asAffine();
        if (!affineRef.equals(affine)) {
            throw new RuntimeException(
                    "Found error with seed " + seed + "at iteration " + iter);
        }
    }

    public static void test(int repeat) throws Exception {
        Random rnd = new Random();
        long seed = rnd.nextLong();
        rnd.setSeed(seed);

        int keySize = 256;
        ECParameterSpec params = ECUtil.getECParameterSpec(keySize);
        NamedCurve curve = CurveDB.lookup(KnownOIDs.secp256r1.value());
        ECPoint generator = curve.getGenerator();
        BigInteger b = curve.getCurve().getB();
        if (params == null || generator == null) {
            throw new RuntimeException(
                    "No EC parameters available for key size " + keySize + " bits");
        }

        ECOperations ops = ECOperations.forParameters(params).get();
        ECOperations opsReference = new ECOperations(
                IntegerPolynomialP256.ONE.getElement(b), P256OrderField.ONE);

        boolean instanceTest1 = ops
                .getField() instanceof IntegerMontgomeryFieldModuloP;
        boolean instanceTest2 = opsReference
                .getField() instanceof IntegerMontgomeryFieldModuloP;
        if (instanceTest1 == false || instanceTest2 == true) {
            throw new RuntimeException("Bad Initialization: ["
                + instanceTest1 + "," + instanceTest2 + "]");
        }

        byte[] multiple = new byte[keySize / 8];
        rnd.nextBytes(multiple);
        multiple[keySize/8 - 1] &= 0x7f; // from opsReference.seedToScalar(multiple);

        MutablePoint referencePoint = opsReference.multiply(generator, multiple);
        MutablePoint point = ops.multiply(generator, multiple);
        check(referencePoint, point, seed, -1);

        AffinePoint refAffineGenerator = AffinePoint.fromECPoint(generator,
                referencePoint.getField());
        AffinePoint montAffineGenerator = AffinePoint.fromECPoint(generator,
                point.getField());

        MutablePoint refProjGenerator = new ProjectivePoint.Mutable(
                refAffineGenerator.getX(false).mutable(),
                refAffineGenerator.getY(false).mutable(),
                referencePoint.getField().get1().mutable());

        MutablePoint projGenerator = new ProjectivePoint.Mutable(
                montAffineGenerator.getX(false).mutable(),
                montAffineGenerator.getY(false).mutable(),
                point.getField().get1().mutable());

        for (int i = 0; i < repeat; i++) {
            rnd.nextBytes(multiple);
            multiple[keySize/8 - 1] &= 0x7f; // opsReference.seedToScalar(multiple);

            MutablePoint nextReferencePoint = opsReference
                    .multiply(referencePoint.asAffine(), multiple);
            MutablePoint nextPoint = ops.multiply(point.asAffine().toECPoint(),
                    multiple);
            check(nextReferencePoint, nextPoint, seed, i);

            if (rnd.nextBoolean()) {
                opsReference.setSum(nextReferencePoint, referencePoint);
                ops.setSum(nextPoint, point);
                check(nextReferencePoint, nextPoint, seed, i);
            }

            if (rnd.nextBoolean()) {
                opsReference.setSum(nextReferencePoint, refProjGenerator);
                ops.setSum(nextPoint, projGenerator);
                check(nextReferencePoint, nextPoint, seed, i);
            }

            if (rnd.nextInt(100) < 10) { // 10% Reset point to generator, test
                                         // generator multiplier
                referencePoint = opsReference.multiply(generator, multiple);
                point = ops.multiply(generator, multiple);
                check(referencePoint, point, seed, i);
            } else {
                referencePoint = nextReferencePoint;
                point = nextPoint;
            }
        }
    }

}

// make test TEST="test/jdk/com/sun/security/ec/ECOperationsFuzzTest.java"