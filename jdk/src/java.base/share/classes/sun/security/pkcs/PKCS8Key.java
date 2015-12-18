/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.Properties;
import java.math.*;
import java.security.Key;
import java.security.KeyRep;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.Security;
import java.security.Provider;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import sun.security.util.HexDumpEncoder;
import sun.security.x509.*;
import sun.security.util.*;

/**
 * Holds a PKCS#8 key, for example a private key
 *
 * @author Dave Brownell
 * @author Benjamin Renaud
 */
public class PKCS8Key implements PrivateKey {

    /** use serialVersionUID from JDK 1.1. for interoperability */
    private static final long serialVersionUID = -3836890099307167124L;

    /* The algorithm information (name, parameters, etc). */
    protected AlgorithmId algid;

    /* The key bytes, without the algorithm information */
    protected byte[] key;

    /* The encoded for the key. */
    protected byte[] encodedKey;

    /* The version for this key */
    public static final BigInteger version = BigInteger.ZERO;

    /**
     * Default constructor.  The key constructed must have its key
     * and algorithm initialized before it may be used, for example
     * by using <code>decode</code>.
     */
    public PKCS8Key() { }

    /*
     * Build and initialize as a "default" key.  All PKCS#8 key
     * data is stored and transmitted losslessly, but no knowledge
     * about this particular algorithm is available.
     */
    private PKCS8Key (AlgorithmId algid, byte[] key)
    throws InvalidKeyException {
        this.algid = algid;
        this.key = key;
        encode();
    }

    /*
     * Binary backwards compatibility. New uses should call parseKey().
     */
    public static PKCS8Key parse (DerValue in) throws IOException {
        PrivateKey key;

        key = parseKey(in);
        if (key instanceof PKCS8Key)
            return (PKCS8Key)key;

        throw new IOException("Provider did not return PKCS8Key");
    }

    /**
     * Construct PKCS#8 subject public key from a DER value.  If
     * the runtime environment is configured with a specific class for
     * this kind of key, a subclass is returned.  Otherwise, a generic
     * PKCS8Key object is returned.
     *
     * <P>This mechanism gurantees that keys (and algorithms) may be
     * freely manipulated and transferred, without risk of losing
     * information.  Also, when a key (or algorithm) needs some special
     * handling, that specific need can be accomodated.
     *
     * @param in the DER-encoded SubjectPublicKeyInfo value
     * @exception IOException on data format errors
     */
    public static PrivateKey parseKey (DerValue in) throws IOException
    {
        AlgorithmId algorithm;
        PrivateKey privKey;

        if (in.tag != DerValue.tag_Sequence)
            throw new IOException ("corrupt private key");

        BigInteger parsedVersion = in.data.getBigInteger();
        if (!version.equals(parsedVersion)) {
            throw new IOException("version mismatch: (supported: " +
                                  Debug.toHexString(version) +
                                  ", parsed: " +
                                  Debug.toHexString(parsedVersion));
        }

        algorithm = AlgorithmId.parse (in.data.getDerValue ());

        try {
            privKey = buildPKCS8Key (algorithm, in.data.getOctetString ());

        } catch (InvalidKeyException e) {
            throw new IOException("corrupt private key");
        }

        if (in.data.available () != 0)
            throw new IOException ("excess private key");
        return privKey;
    }

    /**
     * Parse the key bits.  This may be redefined by subclasses to take
     * advantage of structure within the key.  For example, RSA public
     * keys encapsulate two unsigned integers (modulus and exponent) as
     * DER values within the <code>key</code> bits; Diffie-Hellman and
     * DSS/DSA keys encapsulate a single unsigned integer.
     *
     * <P>This function is called when creating PKCS#8 SubjectPublicKeyInfo
     * values using the PKCS8Key member functions, such as <code>parse</code>
     * and <code>decode</code>.
     *
     * @exception IOException if a parsing error occurs.
     * @exception InvalidKeyException if the key encoding is invalid.
     */
    protected void parseKeyBits () throws IOException, InvalidKeyException {
        encode();
    }

    /*
     * Factory interface, building the kind of key associated with this
     * specific algorithm ID or else returning this generic base class.
     * See the description above.
     */
    static PrivateKey buildPKCS8Key (AlgorithmId algid, byte[] key)
    throws IOException, InvalidKeyException
    {
        /*
         * Use the algid and key parameters to produce the ASN.1 encoding
         * of the key, which will then be used as the input to the
         * key factory.
         */
        DerOutputStream pkcs8EncodedKeyStream = new DerOutputStream();
        encode(pkcs8EncodedKeyStream, algid, key);
        PKCS8EncodedKeySpec pkcs8KeySpec
            = new PKCS8EncodedKeySpec(pkcs8EncodedKeyStream.toByteArray());

        try {
            // Instantiate the key factory of the appropriate algorithm
            KeyFactory keyFac = KeyFactory.getInstance(algid.getName());

            // Generate the private key
            return keyFac.generatePrivate(pkcs8KeySpec);
        } catch (NoSuchAlgorithmException e) {
            // Return generic PKCS8Key with opaque key data (see below)
        } catch (InvalidKeySpecException e) {
            // Return generic PKCS8Key with opaque key data (see below)
        }

        /*
         * Try again using JDK1.1-style for backwards compatibility.
         */
        String classname = "";
        try {
            Properties props;
            String keytype;
            Provider sunProvider;

            sunProvider = Security.getProvider("SUN");
            if (sunProvider == null)
                throw new InstantiationException();
            classname = sunProvider.getProperty("PrivateKey.PKCS#8." +
              algid.getName());
            if (classname == null) {
                throw new InstantiationException();
            }

            Class<?> keyClass = null;
            try {
                keyClass = Class.forName(classname);
            } catch (ClassNotFoundException e) {
                ClassLoader cl = ClassLoader.getSystemClassLoader();
                if (cl != null) {
                    keyClass = cl.loadClass(classname);
                }
            }

            Object      inst = null;
            PKCS8Key    result;

            if (keyClass != null)
                inst = keyClass.newInstance();
            if (inst instanceof PKCS8Key) {
                result = (PKCS8Key) inst;
                result.algid = algid;
                result.key = key;
                result.parseKeyBits();
                return result;
            }
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
            // this should not happen.
            throw new IOException (classname + " [internal error]");
        }

        PKCS8Key result = new PKCS8Key();
        result.algid = algid;
        result.key = key;
        return result;
    }

    /**
     * Returns the algorithm to be used with this key.
     */
    public String getAlgorithm() {
        return algid.getName();
    }

    /**
     * Returns the algorithm ID to be used with this key.
     */
    public AlgorithmId  getAlgorithmId () { return algid; }

    /**
     * PKCS#8 sequence on the DER output stream.
     */
    public final void encode(DerOutputStream out) throws IOException
    {
        encode(out, this.algid, this.key);
    }

    /**
     * Returns the DER-encoded form of the key as a byte array.
     */
    public synchronized byte[] getEncoded() {
        byte[] result = null;
        try {
            result = encode();
        } catch (InvalidKeyException e) {
        }
        return result;
    }

    /**
     * Returns the format for this key: "PKCS#8"
     */
    public String getFormat() {
        return "PKCS#8";
    }

    /**
     * Returns the DER-encoded form of the key as a byte array.
     *
     * @exception InvalidKeyException if an encoding error occurs.
     */
    public byte[] encode() throws InvalidKeyException {
        if (encodedKey == null) {
            try {
                DerOutputStream out;

                out = new DerOutputStream ();
                encode (out);
                encodedKey = out.toByteArray();

            } catch (IOException e) {
                throw new InvalidKeyException ("IOException : " +
                                               e.getMessage());
            }
        }
        return encodedKey.clone();
    }

    /**
     * Initialize an PKCS8Key object from an input stream.  The data
     * on that input stream must be encoded using DER, obeying the
     * PKCS#8 format: a sequence consisting of a version, an algorithm
     * ID and a bit string which holds the key.  (That bit string is
     * often used to encapsulate another DER encoded sequence.)
     *
     * <P>Subclasses should not normally redefine this method; they should
     * instead provide a <code>parseKeyBits</code> method to parse any
     * fields inside the <code>key</code> member.
     *
     * @param in an input stream with a DER-encoded PKCS#8
     * SubjectPublicKeyInfo value
     *
     * @exception InvalidKeyException if a parsing error occurs.
     */
    public void decode(InputStream in) throws InvalidKeyException
    {
        DerValue        val;

        try {
            val = new DerValue (in);
            if (val.tag != DerValue.tag_Sequence)
                throw new InvalidKeyException ("invalid key format");


            BigInteger version = val.data.getBigInteger();
            if (!version.equals(PKCS8Key.version)) {
                throw new IOException("version mismatch: (supported: " +
                                      Debug.toHexString(PKCS8Key.version) +
                                      ", parsed: " +
                                      Debug.toHexString(version));
            }
            algid = AlgorithmId.parse (val.data.getDerValue ());
            key = val.data.getOctetString ();
            parseKeyBits ();

            if (val.data.available () != 0)  {
                // OPTIONAL attributes not supported yet
            }

        } catch (IOException e) {
            // e.printStackTrace ();
            throw new InvalidKeyException("IOException : " +
                                          e.getMessage());
        }
    }

    public void decode(byte[] encodedKey) throws InvalidKeyException {
        decode(new ByteArrayInputStream(encodedKey));
    }

    protected Object writeReplace() throws java.io.ObjectStreamException {
        return new KeyRep(KeyRep.Type.PRIVATE,
                        getAlgorithm(),
                        getFormat(),
                        getEncoded());
    }

    /**
     * Serialization read ... PKCS#8 keys serialize as
     * themselves, and they're parsed when they get read back.
     */
    private void readObject (ObjectInputStream stream)
    throws IOException {

        try {
            decode(stream);

        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new IOException("deserialized key is invalid: " +
                                  e.getMessage());
        }
    }

    /*
     * Produce PKCS#8 encoding from algorithm id and key material.
     */
    static void encode(DerOutputStream out, AlgorithmId algid, byte[] key)
        throws IOException {
            DerOutputStream tmp = new DerOutputStream();
            tmp.putInteger(version);
            algid.encode(tmp);
            tmp.putOctetString(key);
            out.write(DerValue.tag_Sequence, tmp);
    }

    /**
     * Compares two private keys. This returns false if the object with which
     * to compare is not of type <code>Key</code>.
     * Otherwise, the encoding of this key object is compared with the
     * encoding of the given key object.
     *
     * @param object the object with which to compare
     * @return <code>true</code> if this key has the same encoding as the
     * object argument; <code>false</code> otherwise.
     */
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Key) {

            // this encoding
            byte[] b1;
            if (encodedKey != null) {
                b1 = encodedKey;
            } else {
                b1 = getEncoded();
            }

            // that encoding
            byte[] b2 = ((Key)object).getEncoded();

            // do the comparison
            int i;
            if (b1.length != b2.length)
                return false;
            for (i = 0; i < b1.length; i++) {
                if (b1[i] != b2[i]) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Calculates a hash code value for this object. Objects
     * which are equal will also have the same hashcode.
     */
    public int hashCode() {
        int retval = 0;
        byte[] b1 = getEncoded();

        for (int i = 1; i < b1.length; i++) {
            retval += b1[i] * i;
        }
        return(retval);
    }
}
