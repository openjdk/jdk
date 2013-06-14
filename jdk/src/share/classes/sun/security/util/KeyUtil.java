/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.security.Key;
import java.security.PrivilegedAction;
import java.security.AccessController;
import java.security.InvalidKeyException;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.DSAKey;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.interfaces.DHKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;
import java.math.BigInteger;

/**
 * A utility class to get key length, valiate keys, etc.
 */
public final class KeyUtil {

    /**
     * Returns the key size of the given key object in bits.
     *
     * @param key the key object, cannot be null
     * @return the key size of the given key object in bits, or -1 if the
     *       key size is not accessible
     */
    public static final int getKeySize(Key key) {
        int size = -1;

        if (key instanceof Length) {
            try {
                Length ruler = (Length)key;
                size = ruler.length();
            } catch (UnsupportedOperationException usoe) {
                // ignore the exception
            }

            if (size >= 0) {
                return size;
            }
        }

        // try to parse the length from key specification
        if (key instanceof SecretKey) {
            SecretKey sk = (SecretKey)key;
            String format = sk.getFormat();
            if ("RAW".equals(format) && sk.getEncoded() != null) {
                size = (sk.getEncoded().length * 8);
            }   // Otherwise, it may be a unextractable key of PKCS#11, or
                // a key we are not able to handle.
        } else if (key instanceof RSAKey) {
            RSAKey pubk = (RSAKey)key;
            size = pubk.getModulus().bitLength();
        } else if (key instanceof ECKey) {
            ECKey pubk = (ECKey)key;
            size = pubk.getParams().getOrder().bitLength();
        } else if (key instanceof DSAKey) {
            DSAKey pubk = (DSAKey)key;
            size = pubk.getParams().getP().bitLength();
        } else if (key instanceof DHKey) {
            DHKey pubk = (DHKey)key;
            size = pubk.getParams().getP().bitLength();
        }   // Otherwise, it may be a unextractable key of PKCS#11, or
            // a key we are not able to handle.

        return size;
    }

    /**
     * Returns whether the key is valid or not.
     * <P>
     * Note that this method is only apply to DHPublicKey at present.
     *
     * @param  publicKey
     *         the key object, cannot be null
     *
     * @throws NullPointerException if {@code publicKey} is null
     * @throws InvalidKeyException if {@code publicKey} is invalid
     */
    public static final void validate(Key key)
            throws InvalidKeyException {
        if (key == null) {
            throw new NullPointerException(
                "The key to be validated cannot be null");
        }

        if (key instanceof DHPublicKey) {
            validateDHPublicKey((DHPublicKey)key);
        }
    }


    /**
     * Returns whether the key spec is valid or not.
     * <P>
     * Note that this method is only apply to DHPublicKeySpec at present.
     *
     * @param  keySpec
     *         the key spec object, cannot be null
     *
     * @throws NullPointerException if {@code keySpec} is null
     * @throws InvalidKeyException if {@code keySpec} is invalid
     */
    public static final void validate(KeySpec keySpec)
            throws InvalidKeyException {
        if (keySpec == null) {
            throw new NullPointerException(
                "The key spec to be validated cannot be null");
        }

        if (keySpec instanceof DHPublicKeySpec) {
            validateDHPublicKey((DHPublicKeySpec)keySpec);
        }
    }

    /**
     * Returns whether the specified provider is Oracle provider or not.
     * <P>
     * Note that this method is only apply to SunJCE and SunPKCS11 at present.
     *
     * @param  providerName
     *         the provider name
     * @return true if, and only if, the provider of the specified
     *         {@code providerName} is Oracle provider
     */
    public static final boolean isOracleJCEProvider(String providerName) {
        return providerName != null && (providerName.equals("SunJCE") ||
                                        providerName.startsWith("SunPKCS11"));
    }

    /**
     * Returns whether the Diffie-Hellman public key is valid or not.
     *
     * Per RFC 2631 and NIST SP800-56A, the following algorithm is used to
     * validate Diffie-Hellman public keys:
     * 1. Verify that y lies within the interval [2,p-1]. If it does not,
     *    the key is invalid.
     * 2. Compute y^q mod p. If the result == 1, the key is valid.
     *    Otherwise the key is invalid.
     */
    private static void validateDHPublicKey(DHPublicKey publicKey)
            throws InvalidKeyException {
        DHParameterSpec paramSpec = publicKey.getParams();

        BigInteger p = paramSpec.getP();
        BigInteger g = paramSpec.getG();
        BigInteger y = publicKey.getY();

        validateDHPublicKey(p, g, y);
    }

    private static void validateDHPublicKey(DHPublicKeySpec publicKeySpec)
            throws InvalidKeyException {
        validateDHPublicKey(publicKeySpec.getP(),
            publicKeySpec.getG(), publicKeySpec.getY());
    }

    private static void validateDHPublicKey(BigInteger p,
            BigInteger g, BigInteger y) throws InvalidKeyException {

        // For better interoperability, the interval is limited to [2, p-2].
        BigInteger leftOpen = BigInteger.ONE;
        BigInteger rightOpen = p.subtract(BigInteger.ONE);
        if (y.compareTo(leftOpen) <= 0) {
            throw new InvalidKeyException(
                    "Diffie-Hellman public key is too small");
        }
        if (y.compareTo(rightOpen) >= 0) {
            throw new InvalidKeyException(
                    "Diffie-Hellman public key is too large");
        }

        // Don't bother to check against the y^q mod p if safe primes are used.
    }

    /**
     * Trim leading (most significant) zeroes from the result.
     *
     * @throws NullPointerException if {@code b} is null
     */
    public static byte[] trimZeroes(byte[] b) {
        int i = 0;
        while ((i < b.length - 1) && (b[i] == 0)) {
            i++;
        }
        if (i == 0) {
            return b;
        }
        byte[] t = new byte[b.length - i];
        System.arraycopy(b, i, t, 0, t.length);
        return t;
    }

}

