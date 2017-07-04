/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.rsa;

import java.io.IOException;
import java.math.BigInteger;

import java.security.*;
import java.security.interfaces.*;

import sun.security.util.*;
import sun.security.x509.X509Key;

/**
 * Key implementation for RSA public keys.
 *
 * Note: RSA keys must be at least 512 bits long
 *
 * @see RSAPrivateCrtKeyImpl
 * @see RSAKeyFactory
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class RSAPublicKeyImpl extends X509Key implements RSAPublicKey {

    private static final long serialVersionUID = 2644735423591199609L;
    private static final BigInteger THREE = BigInteger.valueOf(3);

    private BigInteger n;       // modulus
    private BigInteger e;       // public exponent

    /**
     * Construct a key from its components. Used by the
     * RSAKeyFactory and the RSAKeyPairGenerator.
     */
    public RSAPublicKeyImpl(BigInteger n, BigInteger e)
            throws InvalidKeyException {
        this.n = n;
        this.e = e;
        RSAKeyFactory.checkRSAProviderKeyLengths(n.bitLength(), e);
        checkExponentRange();
        // generate the encoding
        algid = RSAPrivateCrtKeyImpl.rsaId;
        try {
            DerOutputStream out = new DerOutputStream();
            out.putInteger(n);
            out.putInteger(e);
            byte[] keyArray =
                new DerValue(DerValue.tag_Sequence,
                             out.toByteArray()).toByteArray();
            setKey(new BitArray(keyArray.length*8, keyArray));
        } catch (IOException exc) {
            // should never occur
            throw new InvalidKeyException(exc);
        }
    }

    /**
     * Construct a key from its encoding. Used by RSAKeyFactory.
     */
    public RSAPublicKeyImpl(byte[] encoded) throws InvalidKeyException {
        decode(encoded);
        RSAKeyFactory.checkRSAProviderKeyLengths(n.bitLength(), e);
        checkExponentRange();
    }

    private void checkExponentRange() throws InvalidKeyException {
        // the exponent should be smaller than the modulus
        if (e.compareTo(n) >= 0) {
            throw new InvalidKeyException("exponent is larger than modulus");
        }

        // the exponent should be at least 3
        if (e.compareTo(THREE) < 0) {
            throw new InvalidKeyException("exponent is smaller than 3");
        }
    }

    // see JCA doc
    public String getAlgorithm() {
        return "RSA";
    }

    // see JCA doc
    public BigInteger getModulus() {
        return n;
    }

    // see JCA doc
    public BigInteger getPublicExponent() {
        return e;
    }

    /**
     * Parse the key. Called by X509Key.
     */
    protected void parseKeyBits() throws InvalidKeyException {
        try {
            DerInputStream in = new DerInputStream(getKey().toByteArray());
            DerValue derValue = in.getDerValue();
            if (derValue.tag != DerValue.tag_Sequence) {
                throw new IOException("Not a SEQUENCE");
            }
            DerInputStream data = derValue.data;
            n = data.getPositiveBigInteger();
            e = data.getPositiveBigInteger();
            if (derValue.data.available() != 0) {
                throw new IOException("Extra data available");
            }
        } catch (IOException e) {
            throw new InvalidKeyException("Invalid RSA public key", e);
        }
    }

    // return a string representation of this key for debugging
    public String toString() {
        return "Sun RSA public key, " + n.bitLength() + " bits\n  modulus: "
                + n + "\n  public exponent: " + e;
    }

    protected Object writeReplace() throws java.io.ObjectStreamException {
        return new KeyRep(KeyRep.Type.PUBLIC,
                        getAlgorithm(),
                        getFormat(),
                        getEncoded());
    }
}
