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
import java.security.InvalidKeyException;
import java.security.PublicKey;
import javax.crypto.spec.DHParameterSpec;
import sun.security.util.*;


/**
 * A public key in X.509 format for the Diffie-Hellman key agreement algorithm.
 *
 * @author Jan Luehe
 * @see DHPrivateKey
 * @see javax.crypto.KeyAgreement
 */
final class DHPublicKey implements PublicKey,
javax.crypto.interfaces.DHPublicKey, Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 7647557958927458271L;

    // the public key
    private final BigInteger y;

    // the key bytes, without the algorithm information
    private byte[] key;

    // the encoded key
    private byte[] encodedKey;

    // the prime modulus
    private final BigInteger p;

    // the base generator
    private final BigInteger g;

    // the private-value length (optional)
    private final int l;

    // Note: this OID is used by DHPrivateKey as well.
    static final ObjectIdentifier DH_OID =
            ObjectIdentifier.of(KnownOIDs.DiffieHellman);

    private static class DHComponents {
        final BigInteger y;
        final BigInteger p;
        final BigInteger g;
        final int l;
        final byte[] key;

        DHComponents(BigInteger y, BigInteger p, BigInteger g, int l,
                byte[] key) {
            this.y = y;
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
                throw new IOException("Invalid key format");
            }

            // algorithm identifier
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
            int l = (params.data.available() != 0 ? params.data.getInteger() :
                    0);
            if (params.data.available() != 0) {
                throw new IOException("Extra parameter data");
            }

            // publickey
            byte[] key = val.data.getBitString();
            DerInputStream in = new DerInputStream(key);
            BigInteger y = in.getBigInteger();

            if (val.data.available() != 0) {
                throw new IOException("Excess key data");
            }
            return new DHComponents(y, p, g, l, key);
        } catch (NumberFormatException e) {
            throw new IOException("Error parsing key encoding", e);
        }
    }

    // generates the ASN.1 encoding
    private static byte[] encode(BigInteger p, BigInteger g, int l,
            byte[] key) {
        DerOutputStream algid = new DerOutputStream();

        // store oid in algid
        algid.putOID(DH_OID);

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

        // wrap algid into SEQUENCE, and store it in key encoding
        DerOutputStream tmpDerKey = new DerOutputStream();
        tmpDerKey.write(DerValue.tag_Sequence, algid);

        // store key data
        tmpDerKey.putBitString(key);

        // wrap algid and key into SEQUENCE
        DerOutputStream derKey = new DerOutputStream();
        derKey.write(DerValue.tag_Sequence, tmpDerKey);
        return derKey.toByteArray();
    }

    /**
     * Make a DH public key out of a public value <code>y</code>, a prime
     * modulus <code>p</code>, and a base generator <code>g</code>.
     *
     * @param y the public value
     * @param p the prime modulus
     * @param g the base generator
     *
     * @exception InvalidKeyException if the key cannot be encoded
     */
    DHPublicKey(BigInteger y, BigInteger p, BigInteger g)
        throws InvalidKeyException {
        this(y, p, g, 0);
    }

    /**
     * Make a DH public key out of a public value <code>y</code>, a prime
     * modulus <code>p</code>, a base generator <code>g</code>, and a
     * private-value length <code>l</code>.
     *
     * @param y the public value
     * @param p the prime modulus
     * @param g the base generator
     * @param l the private-value length
     */
    DHPublicKey(BigInteger y, BigInteger p, BigInteger g, int l) {
        this.y = y;
        this.p = p;
        this.g = g;
        this.l = l;
        this.key = new DerValue(DerValue.tag_Integer,
                this.y.toByteArray()).toByteArray();
        this.encodedKey = encode(p, g, l, key);
    }

    /**
     * Make a DH public key from its DER encoding (X.509).
     *
     * @param encodedKey the encoded key
     *
     * @exception InvalidKeyException if the encoded key does not represent
     * a Diffie-Hellman public key
     */
    DHPublicKey(byte[] encodedKey) throws InvalidKeyException {
        this.encodedKey = encodedKey.clone();

        DHComponents dc;
        try {
            dc = decode(this.encodedKey);
        } catch (IOException e) {
            throw new InvalidKeyException("Invalid encoding", e);
        }
        this.y = dc.y;
        this.p = dc.p;
        this.g = dc.g;
        this.l = dc.l;
        this.key = dc.key;
    }

    /**
     * Returns the encoding format of this key: "X.509"
     */
    public String getFormat() {
        return "X.509";
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
        return this.encodedKey.clone();
    }

    /**
     * Returns the public value, <code>y</code>.
     *
     * @return the public value, <code>y</code>
     */
    public BigInteger getY() {
        return this.y;
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

    public String toString() {
        String LINE_SEP = System.lineSeparator();

        StringBuilder sb
            = new StringBuilder("SunJCE Diffie-Hellman Public Key:"
                               + LINE_SEP + "y:" + LINE_SEP
                               + Debug.toHexString(this.y)
                               + LINE_SEP + "p:" + LINE_SEP
                               + Debug.toHexString(this.p)
                               + LINE_SEP + "g:" + LINE_SEP
                               + Debug.toHexString(this.g));
        if (this.l != 0) {
            sb.append(LINE_SEP + "l:" + LINE_SEP + "    " + this.l);
        }
        return sb.toString();
    }

    /**
     * Calculates a hash code value for the object.
     * Objects that are equal will also have the same hashcode.
     */
    @Override
    public int hashCode() {
        return Objects.hash(y, p, g);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (!(obj instanceof javax.crypto.interfaces.DHPublicKey other)) {
            return false;
        }

        DHParameterSpec otherParams = other.getParams();
        return ((this.y.compareTo(other.getY()) == 0) &&
                (this.p.compareTo(otherParams.getP()) == 0) &&
                (this.g.compareTo(otherParams.getG()) == 0));
    }

    /**
     * Replace the DH public key to be serialized.
     *
     * @return the standard KeyRep object to be serialized
     *
     * @throws java.io.ObjectStreamException if a new object representing
     * this DH public key could not be created
     */
    @java.io.Serial
    private Object writeReplace() throws java.io.ObjectStreamException {
        return new KeyRep(KeyRep.Type.PUBLIC,
                        getAlgorithm(),
                        getFormat(),
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
        if (!Arrays.equals(c.key, key) || !c.y.equals(y) || !c.p.equals(p)
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
