/*
 * Copyright (c) 2023, Intel Corporation. All rights reserved.
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
import java.security.spec.ECParameterSpec;
import sun.security.ec.ECOperations;
import sun.security.util.ECUtil;
import sun.security.util.NamedCurve;
import sun.security.util.CurveDB;
import sun.security.ec.point.*;
import java.security.spec.ECPoint;
import sun.security.util.KnownOIDs;
import sun.security.util.math.IntegerResidueMontgomeryFieldModuloP;

/*
 * @test
 * @key randomness
 * @modules java.base/sun.security.ec java.base/sun.security.ec.point java.base/sun.security.util java.base/sun.security.util.math java.base/sun.security.util.math.intpoly
 * @run main/othervm/timeout=1200 -XX:+UnlockDiagnosticVMOptions  -XX:-UseIntPolyIntrinsics ECOperationsFuzzTest
 * @summary Unit test ECOperationsFuzzTest.
 */

 /*
 * @test
 * @key randomness
 * @modules java.base/sun.security.ec java.base/sun.security.ec.point java.base/sun.security.util java.base/sun.security.util.math java.base/sun.security.util.math.intpoly
 * @run main/othervm/timeout=1200 -XX:+UnlockDiagnosticVMOptions -XX:+UseIntPolyIntrinsics ECOperationsFuzzTest
 * @summary Unit test ECOperationsFuzzTest.
 */

// This test case is NOT entirely deterministic, it uses a random seed for pseudo-random number generator
// If a failure occurs, hardcode the seed to make the test case deterministic
public class ECOperationsFuzzTest {
        public static void main(String[] args) throws Exception {
                //Note: it might be useful to increase this number during development
                final int repeat = 100000;
                test(repeat);
                System.out.println("Fuzz Success");
        }

        private static void check(MutablePoint reference, MutablePoint testValue, long seed, int iter) {
                AffinePoint affineRef = reference.asAffine();
                AffinePoint affine = testValue.asAffine();
                if (!affineRef.getX().asBigInteger().equals(affine.getX().asBigInteger()) || 
                    !affineRef.getY().asBigInteger().equals(affine.getY().asBigInteger())) {
                        throw new RuntimeException("Found error with seed "+seed +"at iteration "+ iter);
                }
        }

        public static void test(int repeat) throws Exception {
                Random rnd = new Random();
                long seed = 4156581389403250683L; //rnd.nextLong();
                rnd.setSeed(seed);
                int keySize = 256;
                ECParameterSpec params = ECUtil.getECParameterSpec(null, keySize);
                NamedCurve curve = CurveDB.lookup(KnownOIDs.secp256r1.value());
                ECPoint generator = curve.getGenerator();
                BigInteger orderB = curve.getCurve().getB();
                if (params == null || generator == null || orderB == null) {
                        throw new RuntimeException("No EC parameters available for key size " + keySize + " bits");
                }

                ECOperations opsReference = ECOperations.forParameters(params).get();
                AffinePoint refAffineGenerator = AffinePoint.fromECPoint(generator, opsReference.getField());

                params.montgomery2 = true;
                ECOperations ops = ECOperations.forParameters(params).get();
                IntegerResidueMontgomeryFieldModuloP residueField = (IntegerResidueMontgomeryFieldModuloP)ops.montgomeryOps.getField();
                AffinePoint affineGenerator = AffinePoint.fromECPoint(generator, residueField);
                affineGenerator = new AffinePoint(
                    residueField.toMontgomery(affineGenerator.getX()), 
                    residueField.toMontgomery(affineGenerator.getY()));

                if (ops.montgomeryOps == null) {
                        throw new RuntimeException();
                }

                byte[] multiple = new byte[keySize/8];
                rnd.nextBytes(multiple);
                multiple[keySize/8-1] &= 0x7f;

                MutablePoint referencePoint = opsReference.multiply(generator, multiple);
                MutablePoint point = ops.multiply(generator, multiple);
                check(referencePoint, point, seed, -1);

                for (int i = 0; i < repeat; i++) {
                        rnd.nextBytes(multiple);
                        //multiple = opsReference.seedToScalar(multiple); //package private
                        multiple[keySize/8-1] &= 0x7f;

                        MutablePoint nextReferencePoint = opsReference.multiply(referencePoint.asAffine(), multiple);
                        MutablePoint nextPoint = ops.multiply(point.asAffine().toECPoint(), multiple);
                        check(nextReferencePoint, nextPoint, seed, i);

                        if (rnd.nextBoolean()) {
                                opsReference.setSum(nextReferencePoint, referencePoint);
                                ops.setSum(nextPoint, point);
                                check(nextReferencePoint, nextPoint, seed, i);
                        }

                        if (rnd.nextBoolean()) {
                                opsReference.setSum(nextReferencePoint, refAffineGenerator);
                                ops.setSum(nextPoint, affineGenerator);
                                check(nextReferencePoint, nextPoint, seed, i);
                        }

                        if (rnd.nextInt(100)<10) { // 10% Reset point to generator, test generator multiplier
                                referencePoint = opsReference.multiply(generator, multiple);
                                point = ops.multiply(generator, multiple);
                                check(referencePoint, point, seed, i);
                        } else {
                                referencePoint = nextReferencePoint;
                                point = nextPoint;
                        }
                }
        }


        public static void test2(int repeat) throws Exception {
                Random rnd = new Random();
                long seed = rnd.nextLong();
                rnd.setSeed(seed);
                int keySize = 256;
                ECParameterSpec params = ECUtil.getECParameterSpec(null, keySize);
                NamedCurve curve = CurveDB.lookup(KnownOIDs.secp256r1.value());
                ECPoint generator = curve.getGenerator();
                BigInteger orderB = curve.getCurve().getB();
                if (params == null || generator == null || orderB == null) {
                        throw new RuntimeException("No EC parameters available for key size " + keySize + " bits");
                }

                ECOperations opsReference = ECOperations.forParameters(params).get();
                AffinePoint refAffineGenerator = AffinePoint.fromECPoint(generator, opsReference.getField());

                params.montgomery2 = true;
                ECOperations ops = ECOperations.forParameters(params).get();
                IntegerResidueMontgomeryFieldModuloP residueField = (IntegerResidueMontgomeryFieldModuloP)ops.montgomeryOps.getField();
                AffinePoint affineGenerator = AffinePoint.fromECPoint(generator, residueField);
                affineGenerator = new AffinePoint(
                    residueField.toMontgomery(affineGenerator.getX()), 
                    residueField.toMontgomery(affineGenerator.getY()));

                if (ops.montgomeryOps == null) {
                        throw new RuntimeException();
                }

                int i = 0;
                byte[] multiple = new byte[keySize/8];
                rnd.nextBytes(multiple);
                multiple[keySize/8-1] &= 0x7f;
                System.err.println("VP Case ------------START-------------");
                MutablePoint referencePoint = opsReference.multiply(generator, multiple);
                MutablePoint point = ops.multiply(generator, multiple);
                //check(referencePoint, point, seed, -1);

                rnd.nextBytes(multiple);
                //multiple = opsReference.seedToScalar(multiple); //package private
                multiple[keySize/8-1] &= 0x7f;

                System.err.println("VP Case ------------START2-------------");

                MutablePoint nextReferencePoint = opsReference.multiply(referencePoint.asAffine(), multiple);
                MutablePoint nextPoint = ops.multiply(point.asAffine().toECPoint(), multiple);
                //check(nextReferencePoint, nextPoint, seed, i);
                throw new RuntimeException();
        }


}

//make test TEST="test/jdk/com/sun/security/ec/ECOperationsTest.java"