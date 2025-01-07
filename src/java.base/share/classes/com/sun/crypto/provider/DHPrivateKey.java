/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;
import java.math.BigInteger;
import java.security.KeyRep;
import java.security.PrivateKey;
import java.security.InvalidKeyException;
import javax.crypto.spec.DHParameterSpec;
import sun.security.util.*;

/**
 * A private key in PKCS#8 format for the Diffie-Hellman key agreement
 * algorithm.
 *
 * @author Jan Luehe
 * @see DHPublicKey
 * @see javax.crypto.KeyAgreement
 */
final class DHPrivateKey implements PrivateKey,
        javax.crypto.interfaces.DHPrivateKey, Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 7565477590005668886L;

    // only supported version of PKCS#8 PrivateKeyInfo
    private static final BigInteger PKCS8_VERSION = BigInteger.ZERO;

    // the private key
    private final BigInteger x;

    // the key bytes, without the algorithm information
    // cannot be final as it's re-assigned for deserialization
    private byte[] key;

    // the encoded key
    private byte[] encodedKey;

    // the prime modulus
    private final BigInteger p;

    // the base generator
    private final BigInteger g;

    // the private-value length (optional)
    private final int l;

    private static class DHComponents {
        final BigInteger x;
        final BigInteger p;
        final BigInteger g;
        final int l;
        final byte[] key;

        DHComponents(BigInteger x, BigInteger p, BigInteger g, int l,
                byte[] key) {
            this.x = x;
            this.p = p;
            this.g = g;
            this.l = l;
            this.key = key;
        }
    }

    // parses the specified encoding into a DHComponents object
    private static DHComponents decode(byte[] encodedKey)
            throws IOException {
        DerValue val = null;

        try {
            val = new DerValue(encodedKey);
            if (val.tag != DerValue.tag_Sequence) {
                throw new IOException("Key not a SEQUENCE");
            }

            // version
            BigInteger parsedVersion = val.data.getBigInteger();
            if (!parsedVersion.equals(PKCS8_VERSION)) {
                throw new IOException("version mismatch: (supported: " +
                        PKCS8_VERSION + ", parsed: " + parsedVersion);
            }

            // privateKeyAlgorithm
            DerValue algid = val.data.getDerValue();
            if (algid.tag != DerValue.tag_Sequence) {
                throw new IOException("AlgId is not a SEQUENCE");
            }
            DerInputStream derInStream = algid.toDerInputStream();
            ObjectIdentifier oid = derInStream.getOID();
            if (oid == null) {
                throw new IOException("Null OID");
            }
            if (derInStream.available() == 0) {
                throw new IOException("Parameters missing");
            }
            // parse the parameters
            DerValue params = derInStream.getDerValue();
            if (params.tag == DerValue.tag_Null) {
                throw new IOException("Null parameters");
            }
            if (params.tag != DerValue.tag_Sequence) {
                throw new IOException("Parameters not a SEQUENCE");
            }
            params.data.reset();
            BigInteger p = params.data.getBigInteger();
            BigInteger g = params.data.getBigInteger();
            // Private-value length is OPTIONAL
            int l = (params.data.available() != 0 ?
                    params.data.getInteger() : 0);
            // should have no trailing data
            if (params.data.available() != 0) {
                throw new IOException("Extra parameter data");
            }

            // privateKey
            byte[] key = val.data.getOctetString();
            DerInputStream in = new DerInputStream(key);
            BigInteger x = in.getBigInteger();

            // should have no trailing data
            if (val.data.available() != 0) {
                throw new IOException("Excess trailing data");
            }
            return new DHComponents(x, p, g, l, key);
        } catch (NumberFormatException e) {
            throw new IOException("Error parsing key encoding", e);
        } finally {
            if (val != null) {
                val.clear();
            }
        }
    }

    // Generates the ASN.1 encoding
    private static byte[] encode(BigInteger p, BigInteger g, int l,
            byte[] key) {
        DerOutputStream tmp = new DerOutputStream();

        // version
        tmp.putInteger(PKCS8_VERSION);

        // privateKeyAlgorithm
        DerOutputStream algid = new DerOutputStream();

        // store OID
        algid.putOID(DHPublicKey.DH_OID);
        // encode parameters
        DerOutputStream params = new DerOutputStream();
        params.putInteger(p);
        params.putInteger(g);
        if (l != 0) {
            params.putInteger(l);
        }
        // wrap parameters into SEQUENCE
        DerValue paramSequence = new DerValue(DerValue.tag_Sequence,
                params.toByteArray());
        // store parameter SEQUENCE in algid
        algid.putDerValue(paramSequence);
        // wrap algid into SEQUENCE
        tmp.write(DerValue.tag_Sequence, algid);

        // privateKey
        tmp.putOctetString(key);

        // make it a SEQUENCE
        DerValue val = DerValue.wrap(DerValue.tag_Sequence, tmp);
        byte[] encoded = val.toByteArray();
        val.clear();

        return encoded;
    }

    /**
     * Make a DH private key out of a private value <code>x</code>, a prime
     * modulus <code>p</code>, and a base generator <code>g</code>.
     *
     * @param x the private value
     * @param p the prime modulus
     * @param g the base generator
     */
    DHPrivateKey(BigInteger x, BigInteger p, BigInteger g)
            throws InvalidKeyException {
        this(x, p, g, 0);
    }

    /**
     * Make a DH private key out of a private value <code>x</code>, a prime
     * modulus <code>p</code>, a base generator <code>g</code>, and a
     * private-value length <code>l</code>.
     *
     * @param x the private value
     * @param p the prime modulus
     * @param g the base generator
     * @param l the private-value length
     */
    DHPrivateKey(BigInteger x, BigInteger p, BigInteger g, int l) {
        this.x = x;
        this.p = p;
        this.g = g;
        this.l = l;

        byte[] xbytes = x.toByteArray();
        DerValue val = new DerValue(DerValue.tag_Integer, xbytes);
        try {
            this.key = val.toByteArray();
        } finally {
            val.clear();
            Arrays.fill(xbytes, (byte) 0);
        }
        this.encodedKey = encode(p, g, l, key);
    }

    /**
     * Make a DH private key from its DER encoding (PKCS #8).
     *
     * @param encodedKey the encoded key
     *
     * @throws InvalidKeyException if the encoded key does not represent
     * a Diffie-Hellman private key
     */
    DHPrivateKey(byte[] encodedKey) throws InvalidKeyException {
        this.encodedKey = encodedKey.clone();
        DHComponents dc;
        try {
            dc = decode(this.encodedKey);
        } catch (IOException e) {
            throw new InvalidKeyException("Invalid encoding", e);
        }
        this.x = dc.x;
        this.p = dc.p;
        this.g = dc.g;
        this.l = dc.l;
        this.key = dc.key;
    }

    /**
     * Returns the encoding format of this key: "PKCS#8"
     */
    public String getFormat() {
        return "PKCS#8";
    }

    /**
     * Returns the name of the algorithm associated with this key: "DH"
     */
    public String getAlgorithm() {
        return "DH";
    }

    /**
     * Get the encoding of the key.
     */
    public synchronized byte[] getEncoded() {
        return encodedKey.clone();
    }

    /**
     * Returns the private value, <code>x</code>.
     *
     * @return the private value, <code>x</code>
     */
    public BigInteger getX() {
        return this.x;
    }

    /**
     * Returns the key parameters.
     *
     * @return the key parameters
     */
    public DHParameterSpec getParams() {
        if (this.l != 0) {
            return new DHParameterSpec(this.p, this.g, this.l);
        } else {
            return new DHParameterSpec(this.p, this.g);
        }
    }

    /**
     * Calculates a hash code value for the object.
     * Objects that are equal will also have the same hashcode.
     */
    @Override
    public int hashCode() {
        return Objects.hash(x, p, g);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (!(obj instanceof javax.crypto.interfaces.DHPrivateKey other)) {
            return false;
        }
        DHParameterSpec otherParams = other.getParams();
        return ((this.x.compareTo(other.getX()) == 0) &&
                (this.p.compareTo(otherParams.getP()) == 0) &&
                (this.g.compareTo(otherParams.getG()) == 0));
    }

    /**
     * Replace the DH private key to be serialized.
     *
     * @return the standard KeyRep object to be serialized
     *
     * @throws java.io.ObjectStreamException if a new object representing
     * this DH private key could not be created
     */
    @java.io.Serial
    private Object writeReplace() throws java.io.ObjectStreamException {
        return new KeyRep(KeyRep.Type.PRIVATE, getAlgorithm(), getFormat(),
                encodedKey);
    }

    /**
     * Restores the state of this object from the stream.
     * <p>
     * JDK 1.5+ objects use <code>KeyRep</code>s instead.
     *
     * @param  stream the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        if ((key == null) || (key.length == 0)) {
            throw new InvalidObjectException("key not deserializable");
        }
        if ((encodedKey == null) || (encodedKey.length == 0)) {
            throw new InvalidObjectException(
                    "encoded key not deserializable");
        }
        // check if the "encodedKey" value matches the deserialized fields
        DHComponents c;
        byte[] encodedKeyIntern = encodedKey.clone();
        try {
            c = decode(encodedKeyIntern);
        } catch (IOException e) {
            throw new InvalidObjectException("Invalid encoding", e);
        }
        if (!Arrays.equals(c.key, key) || !c.x.equals(x) || !c.p.equals(p)
                || !c.g.equals(g) || c.l != l) {
            throw new InvalidObjectException(
                    "encoded key not matching internal fields");
        }
        // zero out external arrays
        Arrays.fill(key, (byte)0x00);
        Arrays.fill(encodedKey, (byte)0x00);
        // use self-created internal copies
        this.key = c.key;
        this.encodedKey = encodedKeyIntern;
    }
}
