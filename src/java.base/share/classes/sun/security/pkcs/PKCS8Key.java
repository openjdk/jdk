/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs;

import jdk.internal.access.SharedSecrets;
import sun.security.util.*;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X509Key;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

/**
 * Holds a PKCS#8 key, for example a private key
 *
 * According to https://tools.ietf.org/html/rfc5958:
 *
 *     OneAsymmetricKey ::= SEQUENCE {
 *        version                   Version,
 *        privateKeyAlgorithm       PrivateKeyAlgorithmIdentifier,
 *        privateKey                PrivateKey,
 *        attributes            [0] Attributes OPTIONAL,
 *        ...,
 *        [[2: publicKey        [1] PublicKey OPTIONAL ]],
 *        ...
 *      }
 *
 * We support this format but do not parse attributes.
 */
public class PKCS8Key implements PrivateKey, InternalPrivateKey {

    /** use serialVersionUID from JDK 1.1. for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = -3836890099307167124L;

    /* The algorithm information (name, parameters, etc). */
    protected AlgorithmId algid;

    /* The private key OctetString for the algorithm subclasses to decode */
    protected byte[] privKeyMaterial;

    /* The pkcs8 encoding of this key(s). Created on demand. */
    protected byte[] encodedKey;

    /* The encoded x509 public key for v2 */
    protected byte[] pubKeyEncoded = null;

    /* ASN.1 Attributes */
    private byte[] attributes;

    /* PKCS8 version of the PEM */
    private int version;

    /* The version for this key */
    public static final int V1 = 0;
    public static final int V2 = 1;

    /**
     * Default constructor. Constructors in subclasses that create a new key
     * from its components require this. These constructors must initialize
     * {@link #algid} and {@link #privKeyMaterial}.
     */
    protected PKCS8Key() { }

    /**
     * Another constructor. Constructors in subclasses that create a new key
     * from an encoded byte array require this. We do not assign this
     * encoding to {@link #encodedKey} directly.
     *
     * This method is also used by {@link #parseKey} to create a raw key.
     */
    public PKCS8Key(byte[] input) throws InvalidKeyException {
        try {
            decode(new DerValue(input));
        } catch (IOException e) {
            throw new InvalidKeyException("Unable to decode key", e);
        }
    }

    private PKCS8Key(byte[] privEncoding, byte[] pubEncoding)
        throws InvalidKeyException {
        this(privEncoding);
        pubKeyEncoded = pubEncoding;
        version = V2;
    }

    public int getVersion() {
        return version;
    }

    /**
     * Method for decoding PKCS8 v1 and v2 formats. Decoded values are stored
     * in this class, key material remains in DER format for algorithm
     * subclasses to decode.
     */
    private void decode(DerValue val) throws InvalidKeyException {
        try {
            if (val.tag != DerValue.tag_Sequence) {
                throw new InvalidKeyException("invalid key format");
            }

            // Support check for V1, aka 0, and V2, aka 1.
            version = val.data.getInteger();
            if (version != V1 && version != V2) {
                throw new InvalidKeyException("unknown version: " + version);
            }
            // Parse and store AlgorithmID
            algid = AlgorithmId.parse(val.data.getDerValue());

            // Store key material for subclasses to parse
            privKeyMaterial = val.data.getOctetString();

            // PKCS8 v1 typically ends here
            if (val.data.available() == 0) {
                return;
            }

            // OPTIONAL Context tag 0 for Attributes for PKCS8 v1 & v2
            // Uses 0xA0 context-specific/constructed or 0x80
            // context-specific/primitive.
            DerValue v = val.data.getDerValue();
            if (v.isContextSpecific((byte)0)) {
                attributes = v.getDataBytes();  // Save DER sequence
                if (val.data.available() == 0) {
                    return;
                }
                v = val.data.getDerValue();
            }

            // OPTIONAL context tag 1 for Public Key for PKCS8 v2 only
            if (version == V2) {
                if (v.isContextSpecific((byte)1)) {
                    DerValue bits = v.withTag(DerValue.tag_BitString);
                    pubKeyEncoded = new X509Key(algid,
                        bits.getUnalignedBitString()).getEncoded();
                } else {
                    throw new InvalidKeyException("Invalid context tag");
                }
                if (val.data.available() == 0) {
                    return;
                }
            }

            throw new InvalidKeyException("Extra bytes");
        } catch (IOException e) {
            throw new InvalidKeyException("Unable to decode key", e);
        } finally {
            if (val != null) {
                val.clear();
            }
        }
    }

    /**
     * Construct PKCS#8 subject public key from a DER encoding.  If a
     * security provider supports the key algorithm with a specific class,
     * a PrivateKey from the provider is returned.  Otherwise, a raw
     * PKCS8Key object is returned.
     *
     * <P>This mechanism guarantees that keys (and algorithms) may be
     * freely manipulated and transferred, without risk of losing
     * information.  Also, when a key (or algorithm) needs some special
     * handling, that specific need can be accommodated.
     *
     * @param encoded the DER-encoded SubjectPublicKeyInfo value
     * @exception InvalidKeyException on data format errors
     */
    public static PrivateKey parseKey(byte[] encoded)
        throws InvalidKeyException {
        return parseKey(encoded, null);
    }

    public static PrivateKey parseKey(byte[] encoded, Provider provider)
        throws InvalidKeyException {
        try {
            PKCS8Key rawKey = new PKCS8Key(encoded);

            PKCS8EncodedKeySpec pkcs8KeySpec =
                new PKCS8EncodedKeySpec(rawKey.generateEncoding());
            PrivateKey result = null;
            try {
                if (provider == null) {
                    result = KeyFactory.getInstance(rawKey.algid.getName())
                        .generatePrivate(pkcs8KeySpec);
                } else {
                    result = KeyFactory.getInstance(rawKey.algid.getName(),
                        provider).generatePrivate(pkcs8KeySpec);
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                // Ignore and return raw key
                result = rawKey;
            } finally {
                if (result != rawKey) {
                    rawKey.clear();
                }
                SharedSecrets.getJavaSecuritySpecAccess()
                        .clearEncodedKeySpec(pkcs8KeySpec);
            }
            return result;
        } catch (IOException e) {
            throw new InvalidKeyException(e);
        }
    }

    /**
     * Returns the algorithm to be used with this key.
     */
    public String getAlgorithm() {
        return algid.getName();
    }

    public byte[] getPubKeyEncoded() {
        return pubKeyEncoded;
    }

    public boolean hasPublicKey() {
        return (pubKeyEncoded != null);
    }

    /**
     * Returns the algorithm ID to be used with this key.
     */
    public AlgorithmId getAlgorithmId () {
        return algid;
    }

    /**
     * Returns the DER-encoded form of the key as a byte array,
     * or {@code null} if an encoding error occurs.
     */
    public byte[] getEncoded() {
        byte[] b = getEncodedInternal();
        return (b != null) ? b.clone() : null;
    }

    /**
     * Returns the format for this key: "PKCS#8"
     */
    public String getFormat() {
        return "PKCS#8";
    }

    /**
     * With a given encoded Public and Private key, generate and return a
     * PKCS8v2 DER-encoded byte[].
     *
     * @param pubKeyEncoded DER-encoded PublicKey
     * @param privKeyEncoded DER-encoded PrivateKey
     * @return DER-encoded byte array
     * @throws IOException thrown on encoding failure
     */
    public static byte[] getEncoded(byte[] pubKeyEncoded, byte[] privKeyEncoded)
        throws IOException {
        try {
            return new PKCS8Key(privKeyEncoded, pubKeyEncoded).
                generateEncoding();
        } catch (InvalidKeyException e) {
            throw new IOException(e);
        }
    }

    /**
     * DER-encodes this key as a byte array stored inside this object
     * and return it.
     *
     * @return the encoding
     */
    private synchronized byte[] getEncodedInternal() {
        if (encodedKey == null) {
            try {
                encodedKey = generateEncoding();
            } catch (IOException e) {
               return null;
            }
        }
        return encodedKey;
    }

    private byte[] generateEncoding() throws IOException {
        DerOutputStream out = new DerOutputStream();
        out.putInteger(version);
        algid.encode(out);
        out.putOctetString(privKeyMaterial);

        if (version == V2) {
            if (attributes != null) {
                out.writeImplicit(
                    DerValue.createTag((byte) (DerValue.TAG_CONTEXT |
                        DerValue.TAG_CONSTRUCT), false, (byte) 0),
                    new DerOutputStream().putOctetString(attributes));

            }

            if (pubKeyEncoded != null) {
                X509Key x = new X509Key();
                try {
                    x.decode(pubKeyEncoded);
                } catch (InvalidKeyException e) {
                    throw new IOException(e);
                }

                // X509Key x = X509Key.parse(pubKeyEncoded);
                DerOutputStream pubOut = new DerOutputStream();
                pubOut.putUnalignedBitString(x.getKey());
                out.writeImplicit(
                    DerValue.createTag(DerValue.TAG_CONTEXT, false,
                        (byte) 1), pubOut);
            }
        }

        DerValue val = DerValue.wrap(DerValue.tag_Sequence, out);
        encodedKey = val.toByteArray();
        val.clear();
        return encodedKey;
    }

    @java.io.Serial
    protected Object writeReplace() throws java.io.ObjectStreamException {
        return new KeyRep(KeyRep.Type.PRIVATE,
                getAlgorithm(),
                getFormat(),
                getEncodedInternal());
    }

    /**
     * We used to serialize a PKCS8Key as itself (instead of a KeyRep).
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream) throws IOException {
        try {
            decode(new DerValue(stream));
        } catch (InvalidKeyException e) {
            throw new IOException("deserialized key is invalid", e);
        }
    }

    /**
     * Compares two private keys. This returns false if the object with which
     * to compare is not of type <code>Key</code>.
     * Otherwise, the encoding of this key object is compared with the
     * encoding of the given key object.
     *
     * @param object the object with which to compare
     * @return {@code true} if this key has the same encoding as the
     *          object argument; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof PKCS8Key) {
            // time-constant comparison
            return MessageDigest.isEqual(
                    getEncodedInternal(),
                    ((PKCS8Key)object).getEncodedInternal());
        } else if (object instanceof Key) {
            // time-constant comparison
            byte[] otherEncoded = ((Key)object).getEncoded();
            try {
                return MessageDigest.isEqual(
                        getEncodedInternal(),
                        otherEncoded);
            } finally {
                if (otherEncoded != null) {
                    Arrays.fill(otherEncoded, (byte) 0);
                }
            }
        }
        return false;
    }

    /**
     * Calculates a hash code value for this object. Objects
     * which are equal will also have the same hashcode.
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(getEncodedInternal());
    }

    public void clear() {
        if (encodedKey != null) {
            Arrays.fill(encodedKey, (byte)0);
        }
        Arrays.fill(privKeyMaterial, (byte)0);
    }
}
