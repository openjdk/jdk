/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.util.ArrayUtil;
import sun.security.util.math.*;
import static sun.security.ec.ECOperations.IntermediateValueException;

import java.security.ProviderException;
import java.security.spec.*;
import java.util.Optional;

public class ECDSAOperations {

    public static class Seed {
        private final byte[] seedValue;

        public Seed(byte[] seedValue) {
            this.seedValue = seedValue;
        }

        public byte[] getSeedValue() {
            return seedValue;
        }
    }

    public static class Nonce {
        private final byte[] nonceValue;

        public Nonce(byte[] nonceValue) {
            this.nonceValue = nonceValue;
        }

        public byte[] getNonceValue() {
            return nonceValue;
        }
    }

    private final ECOperations ecOps;
    private final AffinePoint basePoint;

    public ECDSAOperations(ECOperations ecOps, ECPoint basePoint) {
        this.ecOps = ecOps;
        this.basePoint = toAffinePoint(basePoint, ecOps.getField());
    }

    public ECOperations getEcOperations() {
        return ecOps;
    }

    public AffinePoint basePointMultiply(byte[] scalar) {
        return ecOps.multiply(basePoint, scalar).asAffine();
    }

    public static AffinePoint toAffinePoint(ECPoint point,
        IntegerFieldModuloP field) {

        ImmutableIntegerModuloP affineX = field.getElement(point.getAffineX());
        ImmutableIntegerModuloP affineY = field.getElement(point.getAffineY());
        return new AffinePoint(affineX, affineY);
    }

    public static
    Optional<ECDSAOperations> forParameters(ECParameterSpec ecParams) {
        Optional<ECOperations> curveOps =
            ECOperations.forParameters(ecParams);
        return curveOps.map(
            ops -> new ECDSAOperations(ops, ecParams.getGenerator())
        );
    }

    /**
     *
     * Sign a digest using the provided private key and seed.
     * IMPORTANT: The private key is a scalar represented using a
     * little-endian byte array. This is backwards from the conventional
     * representation in ECDSA. The routines that produce and consume this
     * value uses little-endian, so this deviation from convention removes
     * the requirement to swap the byte order. The returned signature is in
     * the conventional byte order.
     *
     * @param privateKey the private key scalar as a little-endian byte array
     * @param digest the digest to be signed
     * @param seed the seed that will be used to produce the nonce. This object
     *             should contain an array that is at least 64 bits longer than
     *             the number of bits required to represent the group order.
     * @return the ECDSA signature value
     * @throws IntermediateValueException if the signature cannot be produced
     *      due to an unacceptable intermediate or final value. If this
     *      exception is thrown, then the caller should discard the nonnce and
     *      try again with an entirely new nonce value.
     */
    public byte[] signDigest(byte[] privateKey, byte[] digest, Seed seed)
        throws IntermediateValueException {

        byte[] nonceArr = ecOps.seedToScalar(seed.getSeedValue());

        Nonce nonce = new Nonce(nonceArr);
        return signDigest(privateKey, digest, nonce);
    }

    /**
     *
     * Sign a digest using the provided private key and nonce.
     * IMPORTANT: The private key and nonce are scalars represented by a
     * little-endian byte array. This is backwards from the conventional
     * representation in ECDSA. The routines that produce and consume these
     * values use little-endian, so this deviation from convention removes
     * the requirement to swap the byte order. The returned signature is in
     * the conventional byte order.
     *
     * @param privateKey the private key scalar as a little-endian byte array
     * @param digest the digest to be signed
     * @param nonce the nonce object containing a little-endian scalar value.
     * @return the ECDSA signature value
     * @throws IntermediateValueException if the signature cannot be produced
     *      due to an unacceptable intermediate or final value. If this
     *      exception is thrown, then the caller should discard the nonnce and
     *      try again with an entirely new nonce value.
     */
    public byte[] signDigest(byte[] privateKey, byte[] digest, Nonce nonce)
        throws IntermediateValueException {

        IntegerFieldModuloP orderField = ecOps.getOrderField();
        int orderBits = orderField.getSize().bitLength();
        if (orderBits % 8 != 0 && orderBits < digest.length * 8) {
            // This implementation does not support truncating digests to
            // a length that is not a multiple of 8.
            throw new ProviderException("Invalid digest length");
        }

        byte[] k = nonce.getNonceValue();
        // check nonce length
        int length = (orderField.getSize().bitLength() + 7) / 8;
        if (k.length != length) {
            throw new ProviderException("Incorrect nonce length");
        }

        MutablePoint R = ecOps.multiply(basePoint, k);
        IntegerModuloP r = R.asAffine().getX();
        // put r into the correct field by fully reducing to an array
        byte[] temp = new byte[length];
        r.asByteArray(temp);
        r = orderField.getElement(temp);
        // store r in result
        r.asByteArray(temp);
        byte[] result = new byte[2 * length];
        ArrayUtil.reverse(temp);
        System.arraycopy(temp, 0, result, 0, length);
        // compare r to 0
        if (ECOperations.allZero(temp)) {
            throw new IntermediateValueException();
        }

        IntegerModuloP dU = orderField.getElement(privateKey);
        int lengthE = Math.min(length, digest.length);
        byte[] E = new byte[lengthE];
        System.arraycopy(digest, 0, E, 0, lengthE);
        ArrayUtil.reverse(E);
        IntegerModuloP e = orderField.getElement(E);
        IntegerModuloP kElem = orderField.getElement(k);
        IntegerModuloP kInv = kElem.multiplicativeInverse();
        MutableIntegerModuloP s = r.mutable();
        s.setProduct(dU).setSum(e).setProduct(kInv);
        // store s in result
        s.asByteArray(temp);
        ArrayUtil.reverse(temp);
        System.arraycopy(temp, 0, result, length, length);
        // compare s to 0
        if (ECOperations.allZero(temp)) {
            throw new IntermediateValueException();
        }

        return result;

    }

}
