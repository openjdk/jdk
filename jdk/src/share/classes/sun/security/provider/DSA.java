/*
 * Copyright 1996-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.provider;

import java.io.*;
import java.util.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import java.security.*;
import java.security.SecureRandom;
import java.security.interfaces.*;
import java.security.spec.DSAParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import sun.security.util.Debug;
import sun.security.util.DerValue;
import sun.security.util.DerInputStream;
import sun.security.util.DerOutputStream;
import sun.security.x509.AlgIdDSA;
import sun.security.jca.JCAUtil;

/**
 * The Digital Signature Standard (using the Digital Signature
 * Algorithm), as described in fips186 of the National Instute of
 * Standards and Technology (NIST), using fips180-1 (SHA-1).
 *
 * This file contains both the signature implementation for the
 * commonly used SHA1withDSA (DSS) as well as RawDSA, used by TLS
 * among others. RawDSA expects the 20 byte SHA-1 digest as input
 * via update rather than the original data like other signature
 * implementations.
 *
 * @author Benjamin Renaud
 *
 * @since   1.1
 *
 * @see DSAPublicKey
 * @see DSAPrivateKey
 */
abstract class DSA extends SignatureSpi {

    /* Are we debugging? */
    private static final boolean debug = false;

    /* The parameter object */
    private DSAParams params;

    /* algorithm parameters */
    private BigInteger presetP, presetQ, presetG;

    /* The public key, if any */
    private BigInteger presetY;

    /* The private key, if any */
    private BigInteger presetX;

    /* The random seed used to generate k */
    private int[] Kseed;

    /* The random seed used to generate k (specified by application) */
    private byte[] KseedAsByteArray;

    /*
     * The random seed used to generate k
     * (prevent the same Kseed from being used twice in a row
     */
    private int[] previousKseed;

    /* The RNG used to output a seed for generating k */
    private SecureRandom signingRandom;

    /**
     * Construct a blank DSA object. It must be
     * initialized before being usable for signing or verifying.
     */
    DSA() {
        super();
    }

    /**
     * Return the 20 byte hash value and reset the digest.
     */
    abstract byte[] getDigest() throws SignatureException;

    /**
     * Reset the digest.
     */
    abstract void resetDigest();

    /**
     * Standard SHA1withDSA implementation.
     */
    public static final class SHA1withDSA extends DSA {

        /* The SHA hash for the data */
        private final MessageDigest dataSHA;

        public SHA1withDSA() throws NoSuchAlgorithmException {
            dataSHA = MessageDigest.getInstance("SHA-1");
        }

        /**
         * Update a byte to be signed or verified.
         */
        protected void engineUpdate(byte b) {
            dataSHA.update(b);
        }

        /**
         * Update an array of bytes to be signed or verified.
         */
        protected void engineUpdate(byte[] data, int off, int len) {
            dataSHA.update(data, off, len);
        }

        protected void engineUpdate(ByteBuffer b) {
            dataSHA.update(b);
        }

        byte[] getDigest() {
            return dataSHA.digest();
        }

        void resetDigest() {
            dataSHA.reset();
        }
    }

    /**
     * RawDSA implementation.
     *
     * RawDSA requires the data to be exactly 20 bytes long. If it is
     * not, a SignatureException is thrown when sign()/verify() is called
     * per JCA spec.
     */
    public static final class RawDSA extends DSA {

        // length of the SHA-1 digest (20 bytes)
        private final static int SHA1_LEN = 20;

        // 20 byte digest buffer
        private final byte[] digestBuffer;

        // offset into the buffer
        private int ofs;

        public RawDSA() {
            digestBuffer = new byte[SHA1_LEN];
        }

        protected void engineUpdate(byte b) {
            if (ofs == SHA1_LEN) {
                ofs = SHA1_LEN + 1;
                return;
            }
            digestBuffer[ofs++] = b;
        }

        protected void engineUpdate(byte[] data, int off, int len) {
            if (ofs + len > SHA1_LEN) {
                ofs = SHA1_LEN + 1;
                return;
            }
            System.arraycopy(data, off, digestBuffer, ofs, len);
            ofs += len;
        }

        byte[] getDigest() throws SignatureException {
            if (ofs != SHA1_LEN) {
                throw new SignatureException
                        ("Data for RawDSA must be exactly 20 bytes long");
            }
            ofs = 0;
            return digestBuffer;
        }

        void resetDigest() {
            ofs = 0;
        }
    }

    /**
     * Initialize the DSA object with a DSA private key.
     *
     * @param privateKey the DSA private key
     *
     * @exception InvalidKeyException if the key is not a valid DSA private
     * key.
     */
    protected void engineInitSign(PrivateKey privateKey)
            throws InvalidKeyException {
        if (!(privateKey instanceof java.security.interfaces.DSAPrivateKey)) {
            throw new InvalidKeyException("not a DSA private key: " +
                                          privateKey);
        }
        java.security.interfaces.DSAPrivateKey priv =
            (java.security.interfaces.DSAPrivateKey)privateKey;
        this.presetX = priv.getX();
        this.presetY = null;
        initialize(priv.getParams());
    }

    /**
     * Initialize the DSA object with a DSA public key.
     *
     * @param publicKey the DSA public key.
     *
     * @exception InvalidKeyException if the key is not a valid DSA public
     * key.
     */
    protected void engineInitVerify(PublicKey publicKey)
            throws InvalidKeyException {
        if (!(publicKey instanceof java.security.interfaces.DSAPublicKey)) {
            throw new InvalidKeyException("not a DSA public key: " +
                                          publicKey);
        }
        java.security.interfaces.DSAPublicKey pub =
            (java.security.interfaces.DSAPublicKey)publicKey;
        this.presetY = pub.getY();
        this.presetX = null;
        initialize(pub.getParams());
    }

    private void initialize(DSAParams params) throws InvalidKeyException {
        resetDigest();
        setParams(params);
    }

    /**
     * Sign all the data thus far updated. The signature is formatted
     * according to the Canonical Encoding Rules, returned as a DER
     * sequence of Integer, r and s.
     *
     * @return a signature block formatted according to the Canonical
     * Encoding Rules.
     *
     * @exception SignatureException if the signature object was not
     * properly initialized, or if another exception occurs.
     *
     * @see sun.security.DSA#engineUpdate
     * @see sun.security.DSA#engineVerify
     */
    protected byte[] engineSign() throws SignatureException {
        BigInteger k = generateK(presetQ);
        BigInteger r = generateR(presetP, presetQ, presetG, k);
        BigInteger s = generateS(presetX, presetQ, r, k);

        try {
            DerOutputStream outseq = new DerOutputStream(100);
            outseq.putInteger(r);
            outseq.putInteger(s);
            DerValue result = new DerValue(DerValue.tag_Sequence,
                                           outseq.toByteArray());

            return result.toByteArray();

        } catch (IOException e) {
            throw new SignatureException("error encoding signature");
        }
    }

    /**
     * Verify all the data thus far updated.
     *
     * @param signature the alledged signature, encoded using the
     * Canonical Encoding Rules, as a sequence of integers, r and s.
     *
     * @exception SignatureException if the signature object was not
     * properly initialized, or if another exception occurs.
     *
     * @see sun.security.DSA#engineUpdate
     * @see sun.security.DSA#engineSign
     */
    protected boolean engineVerify(byte[] signature)
            throws SignatureException {
        return engineVerify(signature, 0, signature.length);
    }

    /**
     * Verify all the data thus far updated.
     *
     * @param signature the alledged signature, encoded using the
     * Canonical Encoding Rules, as a sequence of integers, r and s.
     *
     * @param offset the offset to start from in the array of bytes.
     *
     * @param length the number of bytes to use, starting at offset.
     *
     * @exception SignatureException if the signature object was not
     * properly initialized, or if another exception occurs.
     *
     * @see sun.security.DSA#engineUpdate
     * @see sun.security.DSA#engineSign
     */
    protected boolean engineVerify(byte[] signature, int offset, int length)
            throws SignatureException {

        BigInteger r = null;
        BigInteger s = null;
        // first decode the signature.
        try {
            DerInputStream in = new DerInputStream(signature, offset, length);
            DerValue[] values = in.getSequence(2);

            r = values[0].getBigInteger();
            s = values[1].getBigInteger();

        } catch (IOException e) {
            throw new SignatureException("invalid encoding for signature");
        }

        // some implementations do not correctly encode values in the ASN.1
        // 2's complement format. force r and s to be positive in order to
        // to validate those signatures
        if (r.signum() < 0) {
            r = new BigInteger(1, r.toByteArray());
        }
        if (s.signum() < 0) {
            s = new BigInteger(1, s.toByteArray());
        }

        if ((r.compareTo(presetQ) == -1) && (s.compareTo(presetQ) == -1)) {
            BigInteger w = generateW(presetP, presetQ, presetG, s);
            BigInteger v = generateV(presetY, presetP, presetQ, presetG, w, r);
            return v.equals(r);
        } else {
            throw new SignatureException("invalid signature: out of range values");
        }
    }

    private BigInteger generateR(BigInteger p, BigInteger q, BigInteger g,
                         BigInteger k) {
        BigInteger temp = g.modPow(k, p);
        return temp.remainder(q);
   }

    private BigInteger generateS(BigInteger x, BigInteger q,
            BigInteger r, BigInteger k) throws SignatureException {

        byte[] s2 = getDigest();
        BigInteger temp = new BigInteger(1, s2);
        BigInteger k1 = k.modInverse(q);

        BigInteger s = x.multiply(r);
        s = temp.add(s);
        s = k1.multiply(s);
        return s.remainder(q);
    }

    private BigInteger generateW(BigInteger p, BigInteger q,
                         BigInteger g, BigInteger s) {
        return s.modInverse(q);
    }

    private BigInteger generateV(BigInteger y, BigInteger p,
             BigInteger q, BigInteger g, BigInteger w, BigInteger r)
             throws SignatureException {

        byte[] s2 = getDigest();
        BigInteger temp = new BigInteger(1, s2);

        temp = temp.multiply(w);
        BigInteger u1 = temp.remainder(q);

        BigInteger u2 = (r.multiply(w)).remainder(q);

        BigInteger t1 = g.modPow(u1,p);
        BigInteger t2 = y.modPow(u2,p);
        BigInteger t3 = t1.multiply(t2);
        BigInteger t5 = t3.remainder(p);
        return t5.remainder(q);
    }

    /*
     * Please read bug report 4044247 for an alternative, faster,
     * NON-FIPS approved method to generate K
     */
    private BigInteger generateK(BigInteger q) {

        BigInteger k = null;

        // The application specified a Kseed for us to use.
        // Note that we do not allow usage of the same Kseed twice in a row
        if (Kseed != null && !Arrays.equals(Kseed, previousKseed)) {
            k = generateK(Kseed, q);
            if (k.signum() > 0 && k.compareTo(q) < 0) {
                previousKseed = new int [Kseed.length];
                System.arraycopy(Kseed, 0, previousKseed, 0, Kseed.length);
                return k;
            }
        }

        // The application did not specify a Kseed for us to use.
        // We'll generate a new Kseed by getting random bytes from
        // a SecureRandom object.
        SecureRandom random = getSigningRandom();

        while (true) {
            int[] seed = new int[5];

            for (int i = 0; i < 5; i++)
                seed[i] = random.nextInt();
            k = generateK(seed, q);
            if (k.signum() > 0 && k.compareTo(q) < 0) {
                previousKseed = new int [seed.length];
                System.arraycopy(seed, 0, previousKseed, 0, seed.length);
                return k;
            }
        }
    }

    // Use the application-specified SecureRandom Object if provided.
    // Otherwise, use our default SecureRandom Object.
    private SecureRandom getSigningRandom() {
        if (signingRandom == null) {
            if (appRandom != null) {
                signingRandom = appRandom;
            } else {
                signingRandom = JCAUtil.getSecureRandom();
            }
        }
        return signingRandom;
    }

    /**
     * Compute k for a DSA signature.
     *
     * @param seed the seed for generating k. This seed should be
     * secure. This is what is refered to as the KSEED in the DSA
     * specification.
     *
     * @param g the g parameter from the DSA key pair.
     */
    private BigInteger generateK(int[] seed, BigInteger q) {

        // check out t in the spec.
        int[] t = { 0xEFCDAB89, 0x98BADCFE, 0x10325476,
                    0xC3D2E1F0, 0x67452301 };
        //
        int[] tmp = DSA.SHA_7(seed, t);
        byte[] tmpBytes = new byte[tmp.length * 4];
        for (int i = 0; i < tmp.length; i++) {
            int k = tmp[i];
            for (int j = 0; j < 4; j++) {
                tmpBytes[(i * 4) + j] = (byte) (k >>> (24 - (j * 8)));
            }
        }
        BigInteger k = new BigInteger(1, tmpBytes).mod(q);
        return k;
    }

   // Constants for each round
    private static final int round1_kt = 0x5a827999;
    private static final int round2_kt = 0x6ed9eba1;
    private static final int round3_kt = 0x8f1bbcdc;
    private static final int round4_kt = 0xca62c1d6;

   /**
    * Computes set 1 thru 7 of SHA-1 on m1. */
   static int[] SHA_7(int[] m1, int[] h) {

       int[] W = new int[80];
       System.arraycopy(m1,0,W,0,m1.length);
       int temp = 0;

        for (int t = 16; t <= 79; t++){
            temp = W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16];
            W[t] = ((temp << 1) | (temp >>>(32 - 1)));
        }

       int a = h[0],b = h[1],c = h[2], d = h[3], e = h[4];
       for (int i = 0; i < 20; i++) {
            temp = ((a<<5) | (a>>>(32-5))) +
                ((b&c)|((~b)&d))+ e + W[i] + round1_kt;
            e = d;
            d = c;
            c = ((b<<30) | (b>>>(32-30)));
            b = a;
            a = temp;
        }

        // Round 2
        for (int i = 20; i < 40; i++) {
            temp = ((a<<5) | (a>>>(32-5))) +
                (b ^ c ^ d) + e + W[i] + round2_kt;
            e = d;
            d = c;
            c = ((b<<30) | (b>>>(32-30)));
            b = a;
            a = temp;
        }

        // Round 3
        for (int i = 40; i < 60; i++) {
            temp = ((a<<5) | (a>>>(32-5))) +
                ((b&c)|(b&d)|(c&d)) + e + W[i] + round3_kt;
            e = d;
            d = c;
            c = ((b<<30) | (b>>>(32-30)));
            b = a;
            a = temp;
        }

        // Round 4
        for (int i = 60; i < 80; i++) {
            temp = ((a<<5) | (a>>>(32-5))) +
                (b ^ c ^ d) + e + W[i] + round4_kt;
            e = d;
            d = c;
            c = ((b<<30) | (b>>>(32-30)));
            b = a;
            a = temp;
        }
       int[] md = new int[5];
       md[0] = h[0] + a;
       md[1] = h[1] + b;
       md[2] = h[2] + c;
       md[3] = h[3] + d;
       md[4] = h[4] + e;
       return md;
   }


    /**
     * This implementation recognizes the following parameter:<dl>
     *
     * <dt><tt>Kseed</tt>
     *
     * <dd>a byte array.
     *
     * </dl>
     *
     * @deprecated
     */
    @Deprecated
    protected void engineSetParameter(String key, Object param) {
        if (key.equals("KSEED")) {
            if (param instanceof byte[]) {
                Kseed = byteArray2IntArray((byte[])param);
                KseedAsByteArray = (byte[])param;
            } else {
                debug("unrecognized param: " + key);
                throw new InvalidParameterException("Kseed not a byte array");
            }
        } else {
            throw new InvalidParameterException("invalid parameter");
        }
    }

    /**
     * Return the value of the requested parameter. Recognized
     * parameters are:
     *
     * <dl>
     *
     * <dt><tt>Kseed</tt>
     *
     * <dd>a byte array.
     *
     * </dl>
     *
     * @return the value of the requested parameter.
     *
     * @see java.security.SignatureEngine
     *
     * @deprecated
     */
    @Deprecated
    protected Object engineGetParameter(String key) {
        if (key.equals("KSEED")) {
            return KseedAsByteArray;
        } else {
            return null;
        }
    }

    /**
     * Set the algorithm object.
     */
    private void setParams(DSAParams params) throws InvalidKeyException {
        if (params == null) {
            throw new InvalidKeyException("DSA public key lacks parameters");
        }
        this.params = params;
        this.presetP = params.getP();
        this.presetQ = params.getQ();
        this.presetG = params.getG();
    }

    /**
     * Return a human readable rendition of the engine.
     */
    public String toString() {
        String printable = "DSA Signature";
        if (presetP != null && presetQ != null && presetG != null) {
            printable += "\n\tp: " + Debug.toHexString(presetP);
            printable += "\n\tq: " + Debug.toHexString(presetQ);
            printable += "\n\tg: " + Debug.toHexString(presetG);
        } else {
            printable += "\n\t P, Q or G not initialized.";
        }
        if (presetY != null) {
            printable += "\n\ty: " + Debug.toHexString(presetY);
        }
        if (presetY == null && presetX == null) {
            printable += "\n\tUNINIIALIZED";
        }
        return printable;
    }

    /*
     * Utility routine for converting a byte array into an int array
     */
    private int[] byteArray2IntArray(byte[] byteArray) {

        int j = 0;
        byte[] newBA;
        int mod = byteArray.length % 4;

        // guarantee that the incoming byteArray is a multiple of 4
        // (pad with 0's)
        switch (mod) {
            case 3:     newBA = new byte[byteArray.length + 1]; break;
            case 2:     newBA = new byte[byteArray.length + 2]; break;
            case 1:     newBA = new byte[byteArray.length + 3]; break;
            default:    newBA = new byte[byteArray.length + 0]; break;
        }
        System.arraycopy(byteArray, 0, newBA, 0, byteArray.length);

        // copy each set of 4 bytes in the byte array into an integer
        int[] newSeed = new int[newBA.length / 4];
        for (int i = 0; i < newBA.length; i += 4) {
            newSeed[j] = newBA[i + 3] & 0xFF;
            newSeed[j] |= (newBA[i + 2] << 8) & 0xFF00;
            newSeed[j] |= (newBA[i + 1] << 16) & 0xFF0000;
            newSeed[j] |= (newBA[i + 0] << 24) & 0xFF000000;
            j++;
        }

        return newSeed;
    }

    private static void debug(Exception e) {
        if (debug) {
            e.printStackTrace();
        }
    }

    private static void debug(String s) {
        if (debug) {
            System.err.println(s);
        }
    }
}
