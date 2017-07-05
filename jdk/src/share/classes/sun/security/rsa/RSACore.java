/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.rsa;

import java.math.BigInteger;
import java.util.*;

import java.security.SecureRandom;
import java.security.interfaces.*;

import javax.crypto.BadPaddingException;

import sun.security.jca.JCAUtil;

/**
 * Core of the RSA implementation. Has code to perform public and private key
 * RSA operations (with and without CRT for private key ops). Private CRT ops
 * also support blinding to twart timing attacks.
 *
 * The code in this class only does the core RSA operation. Padding and
 * unpadding must be done externally.
 *
 * Note: RSA keys should be at least 512 bits long
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class RSACore {

    private RSACore() {
        // empty
    }

    /**
     * Return the number of bytes required to store the magnitude byte[] of
     * this BigInteger. Do not count a 0x00 byte toByteArray() would
     * prefix for 2's complement form.
     */
    public static int getByteLength(BigInteger b) {
        int n = b.bitLength();
        return (n + 7) >> 3;
    }

    /**
     * Return the number of bytes required to store the modulus of this
     * RSA key.
     */
    public static int getByteLength(RSAKey key) {
        return getByteLength(key.getModulus());
    }

    // temporary, used by RSACipher and RSAPadding. Move this somewhere else
    public static byte[] convert(byte[] b, int ofs, int len) {
        if ((ofs == 0) && (len == b.length)) {
            return b;
        } else {
            byte[] t = new byte[len];
            System.arraycopy(b, ofs, t, 0, len);
            return t;
        }
    }

    /**
     * Perform an RSA public key operation.
     */
    public static byte[] rsa(byte[] msg, RSAPublicKey key)
            throws BadPaddingException {
        return crypt(msg, key.getModulus(), key.getPublicExponent());
    }

    /**
     * Perform an RSA private key operation. Uses CRT if the key is a
     * CRT key.
     */
    public static byte[] rsa(byte[] msg, RSAPrivateKey key)
            throws BadPaddingException {
        if (key instanceof RSAPrivateCrtKey) {
            return crtCrypt(msg, (RSAPrivateCrtKey)key);
        } else {
            return crypt(msg, key.getModulus(), key.getPrivateExponent());
        }
    }

    /**
     * RSA public key ops and non-CRT private key ops. Simple modPow().
     */
    private static byte[] crypt(byte[] msg, BigInteger n, BigInteger exp)
            throws BadPaddingException {
        BigInteger m = parseMsg(msg, n);
        BigInteger c = m.modPow(exp, n);
        return toByteArray(c, getByteLength(n));
    }

    /**
     * RSA private key operations with CRT. Algorithm and variable naming
     * are taken from PKCS#1 v2.1, section 5.1.2.
     *
     * The only difference is the addition of blinding to twart timing attacks.
     * This is described in the RSA Bulletin#2 (Jan 96) among other places.
     * This means instead of implementing RSA as
     *   m = c ^ d mod n (or RSA in CRT variant)
     * we do
     *   r  = random(0, n-1)
     *   c' = c  * r^e  mod n
     *   m' = c' ^ d    mod n (or RSA in CRT variant)
     *   m  = m' * r^-1 mod n (where r^-1 is the modular inverse of r mod n)
     * This works because r^(e*d) * r^-1 = r * r^-1 = 1 (all mod n)
     *
     * We do not generate new blinding parameters for each operation but reuse
     * them BLINDING_MAX_REUSE times (see definition below).
     */
    private static byte[] crtCrypt(byte[] msg, RSAPrivateCrtKey key)
            throws BadPaddingException {
        BigInteger n = key.getModulus();
        BigInteger c = parseMsg(msg, n);
        BigInteger p = key.getPrimeP();
        BigInteger q = key.getPrimeQ();
        BigInteger dP = key.getPrimeExponentP();
        BigInteger dQ = key.getPrimeExponentQ();
        BigInteger qInv = key.getCrtCoefficient();

        BlindingParameters params;
        if (ENABLE_BLINDING) {
            params = getBlindingParameters(key);
            c = c.multiply(params.re).mod(n);
        } else {
            params = null;
        }

        // m1 = c ^ dP mod p
        BigInteger m1 = c.modPow(dP, p);
        // m2 = c ^ dQ mod q
        BigInteger m2 = c.modPow(dQ, q);

        // h = (m1 - m2) * qInv mod p
        BigInteger mtmp = m1.subtract(m2);
        if (mtmp.signum() < 0) {
            mtmp = mtmp.add(p);
        }
        BigInteger h = mtmp.multiply(qInv).mod(p);

        // m = m2 + q * h
        BigInteger m = h.multiply(q).add(m2);

        if (params != null) {
            m = m.multiply(params.rInv).mod(n);
        }

        return toByteArray(m, getByteLength(n));
    }

    /**
     * Parse the msg into a BigInteger and check against the modulus n.
     */
    private static BigInteger parseMsg(byte[] msg, BigInteger n)
            throws BadPaddingException {
        BigInteger m = new BigInteger(1, msg);
        if (m.compareTo(n) >= 0) {
            throw new BadPaddingException("Message is larger than modulus");
        }
        return m;
    }

    /**
     * Return the encoding of this BigInteger that is exactly len bytes long.
     * Prefix/strip off leading 0x00 bytes if necessary.
     * Precondition: bi must fit into len bytes
     */
    private static byte[] toByteArray(BigInteger bi, int len) {
        byte[] b = bi.toByteArray();
        int n = b.length;
        if (n == len) {
            return b;
        }
        // BigInteger prefixed a 0x00 byte for 2's complement form, remove it
        if ((n == len + 1) && (b[0] == 0)) {
            byte[] t = new byte[len];
            System.arraycopy(b, 1, t, 0, len);
            return t;
        }
        // must be smaller
        assert (n < len);
        byte[] t = new byte[len];
        System.arraycopy(b, 0, t, (len - n), n);
        return t;
    }

    // globally enable/disable use of blinding
    private final static boolean ENABLE_BLINDING = true;

    // maximum number of times that we will use a set of blinding parameters
    // value suggested by Paul Kocher (quoted by NSS)
    private final static int BLINDING_MAX_REUSE = 50;

    // cache for blinding parameters. Map<BigInteger,BlindingParameters>
    // use a weak hashmap so that cached values are automatically cleared
    // when the modulus is GC'ed
    private final static Map<BigInteger,BlindingParameters> blindingCache =
                new WeakHashMap<BigInteger,BlindingParameters>();

    /**
     * Set of blinding parameters for a given RSA key.
     *
     * The RSA modulus is usually unique, so we index by modulus in
     * blindingCache. However, to protect against the unlikely case of two
     * keys sharing the same modulus, we also store the public exponent.
     * This means we cannot cache blinding parameters for multiple keys that
     * share the same modulus, but since sharing moduli is fundamentally broken
     * an insecure, this does not matter.
     */
    private static final class BlindingParameters {
        // e (RSA public exponent)
        final BigInteger e;
        // r ^ e mod n
        final BigInteger re;
        // inverse of r mod n
        final BigInteger rInv;
        // how many more times this parameter object can be used
        private volatile int remainingUses;
        BlindingParameters(BigInteger e, BigInteger re, BigInteger rInv) {
            this.e = e;
            this.re = re;
            this.rInv = rInv;
            // initialize remaining uses, subtract current use now
            remainingUses = BLINDING_MAX_REUSE - 1;
        }
        boolean valid(BigInteger e) {
            int k = remainingUses--;
            return (k > 0) && this.e.equals(e);
        }
    }

    /**
     * Return valid RSA blinding parameters for the given private key.
     * Use cached parameters if available. If not, generate new parameters
     * and cache.
     */
    private static BlindingParameters getBlindingParameters
            (RSAPrivateCrtKey key) {
        BigInteger modulus = key.getModulus();
        BigInteger e = key.getPublicExponent();
        BlindingParameters params;
        // we release the lock between get() and put()
        // that means threads might concurrently generate new blinding
        // parameters for the same modulus. this is only a slight waste
        // of cycles and seems preferable in terms of scalability
        // to locking out all threads while generating new parameters
        synchronized (blindingCache) {
            params = blindingCache.get(modulus);
        }
        if ((params != null) && params.valid(e)) {
            return params;
        }
        int len = modulus.bitLength();
        SecureRandom random = JCAUtil.getSecureRandom();
        BigInteger r = new BigInteger(len, random).mod(modulus);
        BigInteger re = r.modPow(e, modulus);
        BigInteger rInv = r.modInverse(modulus);
        params = new BlindingParameters(e, re, rInv);
        synchronized (blindingCache) {
            blindingCache.put(modulus, params);
        }
        return params;
    }

}
