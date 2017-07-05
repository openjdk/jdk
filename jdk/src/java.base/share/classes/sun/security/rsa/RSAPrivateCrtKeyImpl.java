/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.x509.AlgorithmId;
import sun.security.pkcs.PKCS8Key;

/**
 * Key implementation for RSA private keys, CRT form. For non-CRT private
 * keys, see RSAPrivateKeyImpl. We need separate classes to ensure
 * correct behavior in instanceof checks, etc.
 *
 * Note: RSA keys must be at least 512 bits long
 *
 * @see RSAPrivateKeyImpl
 * @see RSAKeyFactory
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class RSAPrivateCrtKeyImpl
        extends PKCS8Key implements RSAPrivateCrtKey {

    private static final long serialVersionUID = -1326088454257084918L;

    private BigInteger n;       // modulus
    private BigInteger e;       // public exponent
    private BigInteger d;       // private exponent
    private BigInteger p;       // prime p
    private BigInteger q;       // prime q
    private BigInteger pe;      // prime exponent p
    private BigInteger qe;      // prime exponent q
    private BigInteger coeff;   // CRT coeffcient

    // algorithmId used to identify RSA keys
    static final AlgorithmId rsaId =
        new AlgorithmId(AlgorithmId.RSAEncryption_oid);

    /**
     * Generate a new key from its encoding. Returns a CRT key if possible
     * and a non-CRT key otherwise. Used by RSAKeyFactory.
     */
    public static RSAPrivateKey newKey(byte[] encoded)
            throws InvalidKeyException {
        RSAPrivateCrtKeyImpl key = new RSAPrivateCrtKeyImpl(encoded);
        if (key.getPublicExponent().signum() == 0) {
            // public exponent is missing, return a non-CRT key
            return new RSAPrivateKeyImpl(
                key.getModulus(),
                key.getPrivateExponent()
            );
        } else {
            return key;
        }
    }

    /**
     * Construct a key from its encoding. Called from newKey above.
     */
    RSAPrivateCrtKeyImpl(byte[] encoded) throws InvalidKeyException {
        decode(encoded);
        RSAKeyFactory.checkRSAProviderKeyLengths(n.bitLength(), e);
    }

    /**
     * Construct a key from its components. Used by the
     * RSAKeyFactory and the RSAKeyPairGenerator.
     */
    RSAPrivateCrtKeyImpl(BigInteger n, BigInteger e, BigInteger d,
            BigInteger p, BigInteger q, BigInteger pe, BigInteger qe,
            BigInteger coeff) throws InvalidKeyException {
        this.n = n;
        this.e = e;
        this.d = d;
        this.p = p;
        this.q = q;
        this.pe = pe;
        this.qe = qe;
        this.coeff = coeff;
        RSAKeyFactory.checkRSAProviderKeyLengths(n.bitLength(), e);

        // generate the encoding
        algid = rsaId;
        try {
            DerOutputStream out = new DerOutputStream();
            out.putInteger(0); // version must be 0
            out.putInteger(n);
            out.putInteger(e);
            out.putInteger(d);
            out.putInteger(p);
            out.putInteger(q);
            out.putInteger(pe);
            out.putInteger(qe);
            out.putInteger(coeff);
            DerValue val =
                new DerValue(DerValue.tag_Sequence, out.toByteArray());
            key = val.toByteArray();
        } catch (IOException exc) {
            // should never occur
            throw new InvalidKeyException(exc);
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

    // see JCA doc
    public BigInteger getPrivateExponent() {
        return d;
    }

    // see JCA doc
    public BigInteger getPrimeP() {
        return p;
    }

    // see JCA doc
    public BigInteger getPrimeQ() {
        return q;
    }

    // see JCA doc
    public BigInteger getPrimeExponentP() {
        return pe;
    }

    // see JCA doc
    public BigInteger getPrimeExponentQ() {
        return qe;
    }

    // see JCA doc
    public BigInteger getCrtCoefficient() {
        return coeff;
    }

    /**
     * Parse the key. Called by PKCS8Key.
     */
    protected void parseKeyBits() throws InvalidKeyException {
        try {
            DerInputStream in = new DerInputStream(key);
            DerValue derValue = in.getDerValue();
            if (derValue.tag != DerValue.tag_Sequence) {
                throw new IOException("Not a SEQUENCE");
            }
            DerInputStream data = derValue.data;
            int version = data.getInteger();
            if (version != 0) {
                throw new IOException("Version must be 0");
            }
            n = getBigInteger(data);
            e = getBigInteger(data);
            d = getBigInteger(data);
            p = getBigInteger(data);
            q = getBigInteger(data);
            pe = getBigInteger(data);
            qe = getBigInteger(data);
            coeff = getBigInteger(data);
            if (derValue.data.available() != 0) {
                throw new IOException("Extra data available");
            }
        } catch (IOException e) {
            throw new InvalidKeyException("Invalid RSA private key", e);
        }
    }

    /**
     * Read a BigInteger from the DerInputStream.
     */
    static BigInteger getBigInteger(DerInputStream data) throws IOException {
        BigInteger b = data.getBigInteger();

        /*
         * Some implementations do not correctly encode ASN.1 INTEGER values
         * in 2's complement format, resulting in a negative integer when
         * decoded. Correct the error by converting it to a positive integer.
         *
         * See CR 6255949
         */
        if (b.signum() < 0) {
            b = new BigInteger(1, b.toByteArray());
        }
        return b;
    }
}
