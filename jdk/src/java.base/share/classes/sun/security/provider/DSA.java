/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.io.*;
import java.util.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import java.security.*;
import java.security.SecureRandom;
import java.security.interfaces.*;

import sun.security.util.Debug;
import sun.security.util.DerValue;
import sun.security.util.DerInputStream;
import sun.security.util.DerOutputStream;
import sun.security.jca.JCAUtil;

/**
 * The Digital Signature Standard (using the Digital Signature
 * Algorithm), as described in fips186-3 of the National Instute of
 * Standards and Technology (NIST), using SHA digest algorithms
 * from FIPS180-3.
 *
 * This file contains both the signature implementation for the
 * commonly used SHA1withDSA (DSS), SHA224withDSA, SHA256withDSA,
 * as well as RawDSA, used by TLS among others. RawDSA expects
 * the 20 byte SHA-1 digest as input via update rather than the
 * original data like other signature implementations.
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

    /* The RNG used to output a seed for generating k */
    private SecureRandom signingRandom;

    /* The message digest object used */
    private final MessageDigest md;

    /* The format. true for the IEEE P1363 format. false (default) for ASN.1 */
    private final boolean p1363Format;

    /**
     * Construct a blank DSA object. It must be
     * initialized before being usable for signing or verifying.
     */
    DSA(MessageDigest md) {
        this(md, false);
    }

    /**
     * Construct a blank DSA object that will use the specified
     * signature format. {@code p1363Format} should be {@code true} to
     * use the IEEE P1363 format. If {@code p1363Format} is {@code false},
     * the DER-encoded ASN.1 format will used. The DSA object must be
     * initialized before being usable for signing or verifying.
     */
    DSA(MessageDigest md, boolean p1363Format) {
        super();
        this.md = md;
        this.p1363Format = p1363Format;
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

        // check for algorithm specific constraints before doing initialization
        DSAParams params = priv.getParams();
        if (params == null) {
            throw new InvalidKeyException("DSA private key lacks parameters");
        }

        this.params = params;
        this.presetX = priv.getX();
        this.presetY = null;
        this.presetP = params.getP();
        this.presetQ = params.getQ();
        this.presetG = params.getG();
        this.md.reset();
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

        // check for algorithm specific constraints before doing initialization
        DSAParams params = pub.getParams();
        if (params == null) {
            throw new InvalidKeyException("DSA public key lacks parameters");
        }

        this.params = params;
        this.presetY = pub.getY();
        this.presetX = null;
        this.presetP = params.getP();
        this.presetQ = params.getQ();
        this.presetG = params.getG();
        this.md.reset();
    }

    /**
     * Update a byte to be signed or verified.
     */
    protected void engineUpdate(byte b) {
        md.update(b);
    }

    /**
     * Update an array of bytes to be signed or verified.
     */
    protected void engineUpdate(byte[] data, int off, int len) {
        md.update(data, off, len);
    }

    protected void engineUpdate(ByteBuffer b) {
        md.update(b);
    }


    /**
     * Sign all the data thus far updated. The signature format is
     * determined by {@code p1363Format}. If {@code p1363Format} is
     * {@code false} (the default), then the signature is formatted
     * according to the Canonical Encoding Rules, returned as a DER
     * sequence of Integers, r and s. If {@code p1363Format} is
     * {@code false}, the signature is returned in the IEEE P1363
     * format, which is the concatenation or r and s.
     *
     * @return a signature block formatted according to the format
     * indicated by {@code p1363Format}
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

        if (p1363Format) {
            // Return the concatenation of r and s
            byte[] rBytes = r.toByteArray();
            byte[] sBytes = s.toByteArray();

            int size = presetQ.bitLength() / 8;
            byte[] outseq = new byte[size * 2];

            int rLength = rBytes.length;
            int sLength = sBytes.length;
            int i;
            for (i = rLength; i > 0 && rBytes[rLength - i] == 0; i--);

            int j;
            for (j = sLength;
                    j > 0 && sBytes[sLength - j] == 0; j--);

            System.arraycopy(rBytes, rLength - i, outseq, size - i, i);
            System.arraycopy(sBytes, sLength - j, outseq, size * 2 - j, j);

            return outseq;
        } else {
            // Return the DER-encoded ASN.1 form
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
    }

    /**
     * Verify all the data thus far updated.
     *
     * @param signature the alleged signature, encoded using the
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
     * @param signature the alleged signature, encoded using the
     * format indicated by {@code p1363Format}. If {@code p1363Format}
     * is {@code false} (the default), then the signature is formatted
     * according to the Canonical Encoding Rules, as a DER sequence of
     * Integers, r and s. If {@code p1363Format} is {@code false},
     * the signature is in the IEEE P1363 format, which is the
     * concatenation or r and s.
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

        if (p1363Format) {
            if ((length & 1) == 1) {
                // length of signature byte array should be even
                throw new SignatureException("invalid signature format");
            }
            int mid = length/2;
            r = new BigInteger(Arrays.copyOfRange(signature, 0, mid));
            s = new BigInteger(Arrays.copyOfRange(signature, mid, length));
        } else {
            // first decode the signature.
            try {
                DerInputStream in = new DerInputStream(signature, offset,
                                                       length);
                DerValue[] values = in.getSequence(2);

                r = values[0].getBigInteger();
                s = values[1].getBigInteger();

            } catch (IOException e) {
                throw new SignatureException("invalid encoding for signature");
            }
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

    @Deprecated
    protected void engineSetParameter(String key, Object param) {
        throw new InvalidParameterException("No parameter accepted");
    }

    @Deprecated
    protected Object engineGetParameter(String key) {
        return null;
    }

    private BigInteger generateR(BigInteger p, BigInteger q, BigInteger g,
                         BigInteger k) {
        BigInteger temp = g.modPow(k, p);
        return temp.mod(q);
    }

    private BigInteger generateS(BigInteger x, BigInteger q,
            BigInteger r, BigInteger k) throws SignatureException {

        byte[] s2;
        try {
            s2 = md.digest();
        } catch (RuntimeException re) {
            // Only for RawDSA due to its 20-byte length restriction
            throw new SignatureException(re.getMessage());
        }
        // get the leftmost min(N, outLen) bits of the digest value
        int nBytes = q.bitLength()/8;
        if (nBytes < s2.length) {
            s2 = Arrays.copyOfRange(s2, 0, nBytes);
        }
        BigInteger z = new BigInteger(1, s2);
        BigInteger k1 = k.modInverse(q);

        return x.multiply(r).add(z).multiply(k1).mod(q);
    }

    private BigInteger generateW(BigInteger p, BigInteger q,
                         BigInteger g, BigInteger s) {
        return s.modInverse(q);
    }

    private BigInteger generateV(BigInteger y, BigInteger p,
             BigInteger q, BigInteger g, BigInteger w, BigInteger r)
             throws SignatureException {

        byte[] s2;
        try {
            s2 = md.digest();
        } catch (RuntimeException re) {
            // Only for RawDSA due to its 20-byte length restriction
            throw new SignatureException(re.getMessage());
        }
        // get the leftmost min(N, outLen) bits of the digest value
        int nBytes = q.bitLength()/8;
        if (nBytes < s2.length) {
            s2 = Arrays.copyOfRange(s2, 0, nBytes);
        }
        BigInteger z = new BigInteger(1, s2);

        BigInteger u1 = z.multiply(w).mod(q);
        BigInteger u2 = (r.multiply(w)).mod(q);

        BigInteger t1 = g.modPow(u1,p);
        BigInteger t2 = y.modPow(u2,p);
        BigInteger t3 = t1.multiply(t2);
        BigInteger t5 = t3.mod(p);
        return t5.mod(q);
    }

    // NOTE: This following impl is defined in FIPS 186-3 AppendixB.2.2.
    // Original DSS algos such as SHA1withDSA and RawDSA uses a different
    // algorithm defined in FIPS 186-1 Sec3.2, and thus need to override this.
    protected BigInteger generateK(BigInteger q) {
        SecureRandom random = getSigningRandom();
        byte[] kValue = new byte[q.bitLength()/8];

        while (true) {
            random.nextBytes(kValue);
            BigInteger k = new BigInteger(1, kValue).mod(q);
            if (k.signum() > 0 && k.compareTo(q) < 0) {
                return k;
            }
        }
    }

    // Use the application-specified SecureRandom Object if provided.
    // Otherwise, use our default SecureRandom Object.
    protected SecureRandom getSigningRandom() {
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

    /**
     * Standard SHA224withDSA implementation as defined in FIPS186-3.
     */
    public static final class SHA224withDSA extends DSA {
        public SHA224withDSA() throws NoSuchAlgorithmException {
            super(MessageDigest.getInstance("SHA-224"));
        }
    }

    /**
     * SHA224withDSA implementation that uses the IEEE P1363 format.
     */
    public static final class SHA224withDSAinP1363Format extends DSA {
        public SHA224withDSAinP1363Format() throws NoSuchAlgorithmException {
            super(MessageDigest.getInstance("SHA-224"), true);
        }
    }

    /**
     * Standard SHA256withDSA implementation as defined in FIPS186-3.
     */
    public static final class SHA256withDSA extends DSA {
        public SHA256withDSA() throws NoSuchAlgorithmException {
            super(MessageDigest.getInstance("SHA-256"));
        }
    }

    /**
     * SHA256withDSA implementation that uses the IEEE P1363 format.
     */
    public static final class SHA256withDSAinP1363Format extends DSA {
        public SHA256withDSAinP1363Format() throws NoSuchAlgorithmException {
            super(MessageDigest.getInstance("SHA-256"), true);
        }
    }

    static class LegacyDSA extends DSA {
        /* The random seed used to generate k */
        private int[] kSeed;
        /* The random seed used to generate k (specified by application) */
        private byte[] kSeedAsByteArray;
        /*
         * The random seed used to generate k
         * (prevent the same Kseed from being used twice in a row
         */
        private int[] kSeedLast;

        public LegacyDSA(MessageDigest md) throws NoSuchAlgorithmException {
            this(md, false);
        }

        private LegacyDSA(MessageDigest md, boolean p1363Format)
                throws NoSuchAlgorithmException {
            super(md, p1363Format);
        }

        @Deprecated
        protected void engineSetParameter(String key, Object param) {
            if (key.equals("KSEED")) {
                if (param instanceof byte[]) {
                    kSeed = byteArray2IntArray((byte[])param);
                    kSeedAsByteArray = (byte[])param;
                } else {
                    debug("unrecognized param: " + key);
                    throw new InvalidParameterException("kSeed not a byte array");
                }
            } else {
                throw new InvalidParameterException("Unsupported parameter");
            }
        }

        @Deprecated
        protected Object engineGetParameter(String key) {
           if (key.equals("KSEED")) {
               return kSeedAsByteArray;
           } else {
               return null;
           }
        }

        /*
         * Please read bug report 4044247 for an alternative, faster,
         * NON-FIPS approved method to generate K
         */
        @Override
        protected BigInteger generateK(BigInteger q) {
            BigInteger k = null;

            // The application specified a kSeed for us to use.
            // Note: we dis-allow usage of the same Kseed twice in a row
            if (kSeed != null && !Arrays.equals(kSeed, kSeedLast)) {
                k = generateKUsingKSeed(kSeed, q);
                if (k.signum() > 0 && k.compareTo(q) < 0) {
                    kSeedLast = kSeed.clone();
                    return k;
                }
            }

            // The application did not specify a Kseed for us to use.
            // We'll generate a new Kseed by getting random bytes from
            // a SecureRandom object.
            SecureRandom random = getSigningRandom();

            while (true) {
                int[] seed = new int[5];

                for (int i = 0; i < 5; i++) seed[i] = random.nextInt();

                k = generateKUsingKSeed(seed, q);
                if (k.signum() > 0 && k.compareTo(q) < 0) {
                    kSeedLast = seed;
                    return k;
                }
            }
        }

        /**
         * Compute k for the DSA signature as defined in the original DSS,
         * i.e. FIPS186.
         *
         * @param seed the seed for generating k. This seed should be
         * secure. This is what is referred to as the KSEED in the DSA
         * specification.
         *
         * @param g the g parameter from the DSA key pair.
         */
        private BigInteger generateKUsingKSeed(int[] seed, BigInteger q) {

            // check out t in the spec.
            int[] t = { 0xEFCDAB89, 0x98BADCFE, 0x10325476,
                        0xC3D2E1F0, 0x67452301 };
            //
            int[] tmp = SHA_7(seed, t);
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
    }

    /**
     * Standard SHA1withDSA implementation.
     */
    public static final class SHA1withDSA extends LegacyDSA {
        public SHA1withDSA() throws NoSuchAlgorithmException {
            super(MessageDigest.getInstance("SHA-1"));
        }
    }

    /**
     * SHA1withDSA implementation that uses the IEEE P1363 format.
     */
    public static final class SHA1withDSAinP1363Format extends LegacyDSA {
        public SHA1withDSAinP1363Format() throws NoSuchAlgorithmException {
            super(MessageDigest.getInstance("SHA-1"), true);
        }
    }

    /**
     * Raw DSA.
     *
     * Raw DSA requires the data to be exactly 20 bytes long. If it is
     * not, a SignatureException is thrown when sign()/verify() is called
     * per JCA spec.
     */
    static class Raw extends LegacyDSA {
        // Internal special-purpose MessageDigest impl for RawDSA
        // Only override whatever methods used
        // NOTE: no clone support
        public static final class NullDigest20 extends MessageDigest {
            // 20 byte digest buffer
            private final byte[] digestBuffer = new byte[20];

            // offset into the buffer; use Integer.MAX_VALUE to indicate
            // out-of-bound condition
            private int ofs = 0;

            protected NullDigest20() {
                super("NullDigest20");
            }
            protected void engineUpdate(byte input) {
                if (ofs == digestBuffer.length) {
                    ofs = Integer.MAX_VALUE;
                } else {
                    digestBuffer[ofs++] = input;
                }
            }
            protected void engineUpdate(byte[] input, int offset, int len) {
                if (ofs + len > digestBuffer.length) {
                    ofs = Integer.MAX_VALUE;
                } else {
                    System.arraycopy(input, offset, digestBuffer, ofs, len);
                    ofs += len;
                }
            }
            protected final void engineUpdate(ByteBuffer input) {
                int inputLen = input.remaining();
                if (ofs + inputLen > digestBuffer.length) {
                    ofs = Integer.MAX_VALUE;
                } else {
                    input.get(digestBuffer, ofs, inputLen);
                    ofs += inputLen;
                }
            }
            protected byte[] engineDigest() throws RuntimeException {
                if (ofs != digestBuffer.length) {
                    throw new RuntimeException
                        ("Data for RawDSA must be exactly 20 bytes long");
                }
                reset();
                return digestBuffer;
            }
            protected int engineDigest(byte[] buf, int offset, int len)
                throws DigestException {
                if (ofs != digestBuffer.length) {
                    throw new DigestException
                        ("Data for RawDSA must be exactly 20 bytes long");
                }
                if (len < digestBuffer.length) {
                    throw new DigestException
                        ("Output buffer too small; must be at least 20 bytes");
                }
                System.arraycopy(digestBuffer, 0, buf, offset, digestBuffer.length);
                reset();
                return digestBuffer.length;
            }

            protected void engineReset() {
                ofs = 0;
            }
            protected final int engineGetDigestLength() {
                return digestBuffer.length;
            }
        }

        private Raw(boolean p1363Format) throws NoSuchAlgorithmException {
            super(new NullDigest20(), p1363Format);
        }

    }

    /**
     * Standard Raw DSA implementation.
     */
    public static final class RawDSA extends Raw {
        public RawDSA() throws NoSuchAlgorithmException {
            super(false);
        }
    }

    /**
     * Raw DSA implementation that uses the IEEE P1363 format.
     */
    public static final class RawDSAinP1363Format extends Raw {
        public RawDSAinP1363Format() throws NoSuchAlgorithmException {
            super(true);
        }
    }
}
