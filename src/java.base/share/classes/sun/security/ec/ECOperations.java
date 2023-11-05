/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.ec;

import sun.security.ec.point.*;
import sun.security.util.CurveDB;
import sun.security.util.KnownOIDs;
import sun.security.util.ArrayUtil;
import sun.security.util.math.*;
import sun.security.util.math.intpoly.*;

import java.math.BigInteger;
import java.security.ProviderException;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.util.Map;
import java.util.Optional;

/*
 * Elliptic curve point arithmetic for prime-order curves where a=-3.
 * Formulas are derived from "Complete addition formulas for prime order
 * elliptic curves" by Renes, Costello, and Batina.
 */

public class ECOperations {
    private static final ECOperations secp256r1Ops =
        new ECOperations(IntegerPolynomialP256.ONE.getElement(
                CurveDB.lookup(KnownOIDs.secp256r1.value()).getCurve().getB()),
                P256OrderField.ONE);

    /*
     * An exception indicating a problem with an intermediate value produced
     * by some part of the computation. For example, the signing operation
     * will throw this exception to indicate that the r or s value is 0, and
     * that the signing operation should be tried again with a different nonce.
     */
    static class IntermediateValueException extends Exception {
        private static final long serialVersionUID = 1;
    }

    static final Map<BigInteger, IntegerFieldModuloP> fields = Map.of(
        IntegerPolynomialP256.MODULUS, IntegerPolynomialP256.ONE,
        IntegerPolynomialP384.MODULUS, IntegerPolynomialP384.ONE,
        IntegerPolynomialP521.MODULUS, IntegerPolynomialP521.ONE
    );

    static final Map<BigInteger, IntegerFieldModuloP> orderFields = Map.of(
        P256OrderField.MODULUS, P256OrderField.ONE,
        P384OrderField.MODULUS, P384OrderField.ONE,
        P521OrderField.MODULUS, P521OrderField.ONE
    );

    public static Optional<ECOperations> forParameters(ECParameterSpec params) {

        EllipticCurve curve = params.getCurve();
        if (!(curve.getField() instanceof ECFieldFp primeField)) {
            return Optional.empty();
        }

        BigInteger three = BigInteger.valueOf(3);
        if (!primeField.getP().subtract(curve.getA()).equals(three)) {
            return Optional.empty();
        }
        IntegerFieldModuloP field = fields.get(primeField.getP());
        if (field == null) {
            return Optional.empty();
        }

        IntegerFieldModuloP orderField = orderFields.get(params.getOrder());
        if (orderField == null) {
            return Optional.empty();
        }

        ImmutableIntegerModuloP b = field.getElement(curve.getB());
        ECOperations ecOps = new ECOperations(b, orderField);
        return Optional.of(ecOps);
    }

    final ImmutableIntegerModuloP b;
    final SmallValue one;
    final SmallValue two;
    final SmallValue three;
    final SmallValue four;
    final ProjectivePoint.Immutable neutral;
    private final IntegerFieldModuloP orderField;

    public ECOperations(IntegerModuloP b, IntegerFieldModuloP orderField) {
        this.b = b.fixed();
        this.orderField = orderField;

        this.one = b.getField().getSmallValue(1);
        this.two = b.getField().getSmallValue(2);
        this.three = b.getField().getSmallValue(3);
        this.four = b.getField().getSmallValue(4);

        IntegerFieldModuloP field = b.getField();
        this.neutral = new ProjectivePoint.Immutable(field.get0(),
            field.get1(), field.get0());
    }

    public IntegerFieldModuloP getField() {
        return b.getField();
    }
    public IntegerFieldModuloP getOrderField() {
        return orderField;
    }

    protected ProjectivePoint.Immutable getNeutral() {
        return neutral;
    }

    public boolean isNeutral(Point p) {
        ProjectivePoint<?> pp = (ProjectivePoint<?>) p;

        IntegerModuloP z = pp.getZ();

        IntegerFieldModuloP field = z.getField();
        int byteLength = (field.getSize().bitLength() + 7) / 8;
        byte[] zBytes = z.asByteArray(byteLength);
        return allZero(zBytes);
    }

    byte[] seedToScalar(byte[] seedBytes)
        throws IntermediateValueException {

        // Produce a nonce from the seed using FIPS 186-4,section B.5.1:
        // Per-Message Secret Number Generation Using Extra Random Bits
        // or
        // Produce a scalar from the seed using FIPS 186-4, section B.4.1:
        // Key Pair Generation Using Extra Random Bits

        // To keep the implementation simple, sample in the range [0,n)
        // and throw IntermediateValueException in the (unlikely) event
        // that the result is 0.

        // Get 64 extra bits and reduce into the nonce
        int seedBits = orderField.getSize().bitLength() + 64;
        if (seedBytes.length * 8 < seedBits) {
            throw new ProviderException("Incorrect seed length: " +
            seedBytes.length * 8 + " < " + seedBits);
        }

        // input conversion only works on byte boundaries
        // clear high-order bits of last byte so they don't influence nonce
        int lastByteBits = seedBits % 8;
        if (lastByteBits != 0) {
            int lastByteIndex = seedBits / 8;
            byte mask = (byte) (0xFF >>> (8 - lastByteBits));
            seedBytes[lastByteIndex] &= mask;
        }

        int seedLength = (seedBits + 7) / 8;
        IntegerModuloP scalarElem =
            orderField.getElement(seedBytes, 0, seedLength, (byte) 0);
        int scalarLength = (orderField.getSize().bitLength() + 7) / 8;
        byte[] scalarArr = new byte[scalarLength];
        scalarElem.asByteArray(scalarArr);
        if (ECOperations.allZero(scalarArr)) {
            throw new IntermediateValueException();
        }
        return scalarArr;
    }

    /*
     * Compare all values in the array to 0 without branching on any value
     *
     */
    public static boolean allZero(byte[] arr) {
        byte acc = 0;
        for (int i = 0; i < arr.length; i++) {
            acc |= arr[i];
        }
        return acc == 0;
    }

    /**
     * Multiply an affine point by a scalar and return the result as a mutable
     * point.
     *
     * @param affineP the point
     * @param s the scalar as a little-endian array
     * @return the product
     */
    public MutablePoint multiply(AffinePoint affineP, byte[] s) {
        return PointMultiplier.of(this, affineP).pointMultiply(s);
    }

    public MutablePoint multiply(ECPoint ecPoint, byte[] s) {
        return PointMultiplier.of(this, ecPoint).pointMultiply(s);
    }

    /*
     * Point double
     */
    private void setDouble(ProjectivePoint.Mutable p, MutableIntegerModuloP t0,
        MutableIntegerModuloP t1, MutableIntegerModuloP t2,
        MutableIntegerModuloP t3, MutableIntegerModuloP t4) {

        t0.setValue(p.getX()).setSquare();
        t1.setValue(p.getY()).setSquare();
        t2.setValue(p.getZ()).setSquare();
        t3.setValue(p.getX()).setProduct(p.getY());
        t4.setValue(p.getY()).setProduct(p.getZ());

        t3.setSum(t3);
        p.getZ().setProduct(p.getX());

        p.getZ().setProduct(two);

        p.getY().setValue(t2).setProduct(b);
        p.getY().setDifference(p.getZ());

        p.getY().setProduct(three);
        p.getX().setValue(t1).setDifference(p.getY());

        p.getY().setSum(t1);
        p.getY().setProduct(p.getX());
        p.getX().setProduct(t3);

        t2.setProduct(three);
        p.getZ().setProduct(b);

        p.getZ().setDifference(t2);
        p.getZ().setDifference(t0);
        p.getZ().setProduct(three);
        t0.setProduct(three);

        t0.setDifference(t2);
        t0.setProduct(p.getZ());
        p.getY().setSum(t0);

        t4.setSum(t4);
        p.getZ().setProduct(t4);

        p.getX().setDifference(p.getZ());
        p.getZ().setValue(t4).setProduct(t1);

        p.getZ().setProduct(four);

    }

    /*
     * Mixed point addition. This method constructs new temporaries each time
     * it is called. For better efficiency, the method that reuses temporaries
     * should be used if more than one sum will be computed.
     */
    public void setSum(MutablePoint p, AffinePoint p2) {

        IntegerModuloP zero = p.getField().get0();
        MutableIntegerModuloP t0 = zero.mutable();
        MutableIntegerModuloP t1 = zero.mutable();
        MutableIntegerModuloP t2 = zero.mutable();
        MutableIntegerModuloP t3 = zero.mutable();
        MutableIntegerModuloP t4 = zero.mutable();
        setSum((ProjectivePoint.Mutable) p, p2, t0, t1, t2, t3, t4);

    }

    /*
     * Mixed point addition
     */
    private void setSum(ProjectivePoint.Mutable p, AffinePoint p2,
        MutableIntegerModuloP t0, MutableIntegerModuloP t1,
        MutableIntegerModuloP t2, MutableIntegerModuloP t3,
        MutableIntegerModuloP t4) {

        t0.setValue(p.getX()).setProduct(p2.getX());
        t1.setValue(p.getY()).setProduct(p2.getY());
        t3.setValue(p2.getX()).setSum(p2.getY());
        t4.setValue(p.getX()).setSum(p.getY());
        t3.setProduct(t4);
        t4.setValue(t0).setSum(t1);

        t3.setDifference(t4);
        t4.setValue(p2.getY()).setProduct(p.getZ());
        t4.setSum(p.getY());

        p.getY().setValue(p2.getX()).setProduct(p.getZ());
        p.getY().setSum(p.getX());
        t2.setValue(p.getZ());
        p.getZ().setProduct(b);

        p.getX().setValue(p.getY()).setDifference(p.getZ());
        p.getX().setProduct(three);

        p.getZ().setValue(t1).setDifference(p.getX());
        p.getX().setSum(t1);
        p.getY().setProduct(b);

        t2.setProduct(three);
        p.getY().setDifference(t2);

        p.getY().setDifference(t0);
        p.getY().setProduct(three);

        t0.setProduct(three);
        t0.setDifference(t2);

        t1.setValue(t4).setProduct(p.getY());
        t2.setValue(t0).setProduct(p.getY());
        p.getY().setValue(p.getX()).setProduct(p.getZ());

        p.getY().setSum(t2);
        p.getX().setProduct(t3);
        p.getX().setDifference(t1);

        p.getZ().setProduct(t4);
        t3.setProduct(t0);
        p.getZ().setSum(t3);
    }

    /*
     * Projective point addition
     */
    private void setSum(ProjectivePoint.Mutable p, ProjectivePoint.Mutable p2,
        MutableIntegerModuloP t0, MutableIntegerModuloP t1,
        MutableIntegerModuloP t2, MutableIntegerModuloP t3,
        MutableIntegerModuloP t4) {

        t0.setValue(p.getX()).setProduct(p2.getX());
        t1.setValue(p.getY()).setProduct(p2.getY());
        t2.setValue(p.getZ()).setProduct(p2.getZ());

        t3.setValue(p.getX()).setSum(p.getY());
        t4.setValue(p2.getX()).setSum(p2.getY());
        t3.setProduct(t4);

        t4.setValue(t0).setSum(t1);
        t3.setDifference(t4);
        t4.setValue(p.getY()).setSum(p.getZ());

        p.getY().setValue(p2.getY()).setSum(p2.getZ());
        t4.setProduct(p.getY());
        p.getY().setValue(t1).setSum(t2);

        t4.setDifference(p.getY());
        p.getX().setSum(p.getZ());
        p.getY().setValue(p2.getX()).setSum(p2.getZ());

        p.getX().setProduct(p.getY());
        p.getY().setValue(t0).setSum(t2);
        p.getY().setAdditiveInverse().setSum(p.getX());

        p.getZ().setValue(t2).setProduct(b);
        p.getX().setValue(p.getY()).setDifference(p.getZ());

        p.getX().setProduct(three);

        p.getZ().setValue(t1).setDifference(p.getX());
        p.getX().setSum(t1);

        p.getY().setProduct(b);
        t2.setProduct(three);

        p.getY().setDifference(t2);
        p.getY().setDifference(t0);
        p.getY().setProduct(three);

        t0.setProduct(three);

        t0.setDifference(t2);
        t1.setValue(t4).setProduct(p.getY());
        t2.setValue(t0).setProduct(p.getY());

        p.getY().setValue(p.getX()).setProduct(p.getZ());
        p.getY().setSum(t2);
        p.getX().setProduct(t3);

        p.getX().setDifference(t1);
        p.getZ().setProduct(t4);

        t3.setProduct(t0);
        p.getZ().setSum(t3);
    }

    // The extra step in the Full Public key validation as described in
    // NIST SP 800-186 Appendix D.1.1.2
    public boolean checkOrder(ECPoint point) {
        BigInteger x = point.getAffineX();
        BigInteger y = point.getAffineY();

        // Verify that n Q = INFINITY. Output REJECT if verification fails.
        IntegerFieldModuloP field = this.getField();
        AffinePoint ap = new AffinePoint(field.getElement(x), field.getElement(y));
        byte[] scalar = this.orderField.getSize().toByteArray();
        ArrayUtil.reverse(scalar);
        return isNeutral(this.multiply(ap, scalar));
    }

    sealed interface PointMultiplier {
        Map<ECPoint, PointMultiplier> multipliers = Map.of(
                Secp256R1GeneratorMultiplier.generator,
                Secp256R1GeneratorMultiplier.multiplier);

        // Multiply the point by a scalar and return the result as a mutable
        // point.  The multiplier point is specified by the implementation of
        // this interface, which could be a general EC point or EC generator
        // point.
        //
        // Multiply the ECPoint (that is specified in the implementation) by
        // a scalar and return the result as a ProjectivePoint.Mutable point.
        // The point to be multiplied can be a general EC point or the
        // generator of a named EC group.  The scalar multiplier is an integer
        // in little endian byte array representation.
        ProjectivePoint.Mutable pointMultiply(byte[] scalar);

        static PointMultiplier of(ECOperations ecOps, AffinePoint affPoint) {
            PointMultiplier multiplier = multipliers.get(affPoint.toECPoint());
            if (multiplier == null) {
                multiplier = new Default(ecOps, affPoint);
            }

            return multiplier;
        }

        static PointMultiplier of(ECOperations ecOps, ECPoint ecPoint) {
            PointMultiplier multiplier = multipliers.get(ecPoint);
            if (multiplier == null) {
                AffinePoint affPoint =
                        AffinePoint.fromECPoint(ecPoint, ecOps.getField());
                multiplier = new Default(ecOps, affPoint);
            }

            return multiplier;
        }

        private static void lookup(
                ProjectivePoint.Immutable[] ips, int index,
                ProjectivePoint.Mutable result) {
            for (int i = 0; i < 16; i++) {
                int xor = index ^ i;
                int bit3 = (xor & 0x8) >>> 3;
                int bit2 = (xor & 0x4) >>> 2;
                int bit1 = (xor & 0x2) >>> 1;
                int bit0 = (xor & 0x1);
                int inverse = bit0 | bit1 | bit2 | bit3;
                int set = 1 - inverse;

                ProjectivePoint.Immutable pi = ips[i];
                result.conditionalSet(pi, set);
            }
        }

        final class Default implements PointMultiplier {
            private final AffinePoint affineP;
            private final ECOperations ecOps;

            private Default(ECOperations ecOps, AffinePoint affineP) {
                this.ecOps = ecOps;
                this.affineP = affineP;
            }

            @Override
            public ProjectivePoint.Mutable pointMultiply(byte[] s) {
                // 4-bit windowed multiply with branchless lookup.
                // The mixed addition is faster, so it is used to construct
                // the array at the beginning of the operation.

                IntegerFieldModuloP field = affineP.getX().getField();
                ImmutableIntegerModuloP zero = field.get0();
                // temporaries
                MutableIntegerModuloP t0 = zero.mutable();
                MutableIntegerModuloP t1 = zero.mutable();
                MutableIntegerModuloP t2 = zero.mutable();
                MutableIntegerModuloP t3 = zero.mutable();
                MutableIntegerModuloP t4 = zero.mutable();

                ProjectivePoint.Mutable result =
                        new ProjectivePoint.Mutable(field);
                result.getY().setValue(field.get1().mutable());

                ProjectivePoint.Immutable[] pointMultiples =
                        new ProjectivePoint.Immutable[16];
                // 0P is neutral---same as initial result value
                pointMultiples[0] = result.fixed();

                ProjectivePoint.Mutable ps = new ProjectivePoint.Mutable(field);
                ps.setValue(affineP);
                // 1P = P
                pointMultiples[1] = ps.fixed();

                // the rest are calculated using mixed point addition
                for (int i = 2; i < 16; i++) {
                    ecOps.setSum(ps, affineP, t0, t1, t2, t3, t4);
                    pointMultiples[i] = ps.fixed();
                }

                ProjectivePoint.Mutable lookupResult = ps.mutable();

                for (int i = s.length - 1; i >= 0; i--) {
                    double4(result, t0, t1, t2, t3, t4);

                    int high = (0xFF & s[i]) >>> 4;
                    lookup(pointMultiples, high, lookupResult);
                    ecOps.setSum(result, lookupResult, t0, t1, t2, t3, t4);

                    double4(result, t0, t1, t2, t3, t4);

                    int low = 0xF & s[i];
                    lookup(pointMultiples, low, lookupResult);
                    ecOps.setSum(result, lookupResult, t0, t1, t2, t3, t4);
                }

                return result;
            }

            private void double4(ProjectivePoint.Mutable p,
                    MutableIntegerModuloP t0, MutableIntegerModuloP t1,
                    MutableIntegerModuloP t2, MutableIntegerModuloP t3,
                    MutableIntegerModuloP t4) {
                for (int i = 0; i < 4; i++) {
                    ecOps.setDouble(p, t0, t1, t2, t3, t4);
                }
            }
        }

        final class Secp256R1GeneratorMultiplier implements PointMultiplier {
            private static final ECPoint generator =
                    CurveDB.P_256.getGenerator();
            private static final PointMultiplier multiplier =
                    new Secp256R1GeneratorMultiplier();

            private static final ImmutableIntegerModuloP zero =
                    IntegerPolynomialP256.ONE.get0();
            private static final ImmutableIntegerModuloP one =
                    IntegerPolynomialP256.ONE.get1();

            @Override
            public ProjectivePoint.Mutable pointMultiply(byte[] s) {
                MutableIntegerModuloP t0 = zero.mutable();
                MutableIntegerModuloP t1 = zero.mutable();
                MutableIntegerModuloP t2 = zero.mutable();
                MutableIntegerModuloP t3 = zero.mutable();
                MutableIntegerModuloP t4 = zero.mutable();

                ProjectivePoint.Mutable d = new ProjectivePoint.Mutable(
                        zero.mutable(),
                        one.mutable(),
                        zero.mutable());
                ProjectivePoint.Mutable r = d.mutable();
                for (int i = 15; i >= 0; i--) {
                    secp256r1Ops.setDouble(d, t0, t1, t2, t3, t4);
                    for (int j = 3; j >= 0; j--) {
                        int pos = i + j * 16;
                        int index = (bit(s, pos + 192) << 3) |
                                    (bit(s, pos + 128) << 2) |
                                    (bit(s, pos +  64) << 1) |
                                     bit(s, pos);

                        lookup(P256.points[j], index, r);
                        secp256r1Ops.setSum(d, r, t0, t1, t2, t3, t4);
                    }
                }

                return d;
            }

            private static int bit(byte[] k, int i) {
                return (k[i >> 3] >> (i & 0x07)) & 0x01;
            }

            // Lazy loading of the tables.
            private static final class P256 {
                // Pre-computed table to speed up the point multiplication.
                //
                // This is a 4x16 array of ProjectivePoint.Immutable elements.
                // The first row contains the following multiples of the
                // generator.
                //
                // index   |    point
                // --------+----------------
                // 0x0000  | 0G
                // 0x0001  | 1G
                // 0x0002  | (2^64)G
                // 0x0003  | (2^64 + 1)G
                // 0x0004  | 2^128G
                // 0x0005  | (2^128 + 1)G
                // 0x0006  | (2^128 + 2^64)G
                // 0x0007  | (2^128 + 2^64 + 1)G
                // 0x0008  | 2^192G
                // 0x0009  | (2^192 + 1)G
                // 0x000A  | (2^192 + 2^64)G
                // 0x000B  | (2^192 + 2^64 + 1)G
                // 0x000C  | (2^192 + 2^128)G
                // 0x000D  | (2^192 + 2^128 + 1)G
                // 0x000E  | (2^192 + 2^128 + 2^64)G
                // 0x000F  | (2^192 + 2^128 + 2^64 + 1)G
                //
                // For the other 3 rows, points[i][j] = 2^16 * (points[i-1][j].
                private static final ProjectivePoint.Immutable[][] points;

                // Generate the pre-computed tables.  This block may be
                // replaced with hard-coded tables in order to speed up
                // the class loading.
                static {
                    points = new ProjectivePoint.Immutable[4][16];
                    BigInteger[] factors = new BigInteger[] {
                            BigInteger.ONE,
                            BigInteger.TWO.pow(64),
                            BigInteger.TWO.pow(128),
                            BigInteger.TWO.pow(192)
                    };

                    BigInteger[] base = new BigInteger[16];
                    base[0] = BigInteger.ZERO;
                    base[1] = BigInteger.ONE;
                    base[2] = factors[1];
                    for (int i = 3; i < 16; i++) {
                        base[i] = BigInteger.ZERO;
                        for (int k = 0; k < 4; k++) {
                            if (((i >>> k) & 0x01) != 0) {
                                base[i] = base[i].add(factors[k]);
                            }
                        }
                    }

                    for (int d = 0; d < 4; d++) {
                        for (int w = 0; w < 16; w++) {
                            BigInteger bi = base[w];
                            if (d != 0) {
                                bi = bi.multiply(BigInteger.TWO.pow(d * 16));
                            }
                            if (w == 0) {
                                points[d][0] = new ProjectivePoint.Immutable(
                                    zero.fixed(), one.fixed(), zero.fixed());
                            } else {
                                PointMultiplier multiplier = new Default(
                                    secp256r1Ops, AffinePoint.fromECPoint(
                                        generator, zero.getField()));
                                byte[] s = bi.toByteArray();
                                ArrayUtil.reverse(s);
                                ProjectivePoint.Mutable m =
                                        multiplier.pointMultiply(s);
                                points[d][w] = m.setValue(m.asAffine()).fixed();
                            }
                        }
                    }

                    // Check that the tables are correctly generated.
                    if (ECOperations.class.desiredAssertionStatus()) {
                        verifyTables(base);
                    }
                }

                private static void verifyTables(BigInteger[] base) {
                    for (int d = 0; d < 4; d++) {
                        for (int w = 0; w < 16; w++) {
                            BigInteger bi = base[w];
                            if (d != 0) {
                                bi = bi.multiply(BigInteger.TWO.pow(d * 16));
                            }
                            if (w != 0) {
                                byte[] s = new byte[32];
                                byte[] b = bi.toByteArray();
                                ArrayUtil.reverse(b);
                                System.arraycopy(b, 0, s, 0, b.length);

                                ProjectivePoint.Mutable m =
                                        multiplier.pointMultiply(s);
                                ProjectivePoint.Immutable v =
                                        m.setValue(m.asAffine()).fixed();
                                if (!v.getX().asBigInteger().equals(
                                        points[d][w].getX().asBigInteger()) ||
                                    !v.getY().asBigInteger().equals(
                                        points[d][w].getY().asBigInteger())) {
                                    throw new RuntimeException();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
