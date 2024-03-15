/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.interfaces.DHKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

import sun.security.jca.JCAUtil;

/**
 * A utility class to get key length, validate keys, etc.
 */
public final class KeyUtil {

    /**
     * Returns the key size of the given key object in bits.
     *
     * @param key the key object, cannot be null
     * @return the key size of the given key object in bits, or -1 if the
     *       key size is not accessible
     */
    public static int getKeySize(Key key) {
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
        if (key instanceof SecretKey sk) {
            String format = sk.getFormat();
            if ("RAW".equals(format)) {
                byte[] encoded = sk.getEncoded();
                if (encoded != null) {
                    size = (encoded.length * 8);
                    Arrays.fill(encoded, (byte)0);
                }
            }   // Otherwise, it may be an unextractable key of PKCS#11, or
                // a key we are not able to handle.
        } else if (key instanceof RSAKey pubk) {
            size = pubk.getModulus().bitLength();
        } else if (key instanceof ECKey pubk) {
            size = pubk.getParams().getOrder().bitLength();
        } else if (key instanceof DSAKey pubk) {
            DSAParams params = pubk.getParams();    // params can be null
            size = (params != null) ? params.getP().bitLength() : -1;
        } else if (key instanceof DHKey pubk) {
            size = pubk.getParams().getP().bitLength();
        } else if (key instanceof XECKey pubk) {
            AlgorithmParameterSpec params = pubk.getParams();
            if (params instanceof NamedParameterSpec) {
                String name = ((NamedParameterSpec) params).getName();
                if (name.equalsIgnoreCase(NamedParameterSpec.X25519.getName())) {
                    size = 255;
                } else if (name.equalsIgnoreCase(NamedParameterSpec.X448.getName())) {
                    size = 448;
                } else {
                    size = -1;
                }
            } else {
                size = -1;
            }
        } else if (key instanceof EdECKey) {
            String nc = ((EdECKey) key).getParams().getName();
            if (nc.equalsIgnoreCase(NamedParameterSpec.ED25519.getName())) {
                size = 255;
            } else if (nc.equalsIgnoreCase(
                    NamedParameterSpec.ED448.getName())) {
                size = 448;
            } else {
                size = -1;
            }
        }   // Otherwise, it may be an unextractable key of PKCS#11, or
            // a key we are not able to handle.

        return size;
    }

    /**
     * Returns the key size of the given cryptographic parameters in bits.
     *
     * @param parameters the cryptographic parameters, cannot be null
     * @return the key size of the given cryptographic parameters in bits,
     *       or -1 if the key size is not accessible
     */
    public static final int getKeySize(AlgorithmParameters parameters) {

        switch (parameters.getAlgorithm()) {
            case "EC":
                // ECKeySizeParameterSpec is SunEC internal only
                if (parameters.getProvider().getName().equals("SunEC")) {
                    try {
                        ECKeySizeParameterSpec ps = parameters.getParameterSpec(
                            ECKeySizeParameterSpec.class);
                        if (ps != null) {
                            return ps.getKeySize();
                        }
                    } catch (InvalidParameterSpecException ipse) {
                        // ignore
                    }
                }

                try {
                    ECParameterSpec ps = parameters.getParameterSpec(
                            ECParameterSpec.class);
                    if (ps != null) {
                        return ps.getOrder().bitLength();
                    }
                } catch (InvalidParameterSpecException ipse) {
                    // ignore
                }

                // Note: the ECGenParameterSpec case should be covered by the
                // ECParameterSpec case above.
                // See ECUtil.getECParameterSpec(Provider, String).

                break;
            case "DiffieHellman":
                try {
                    DHParameterSpec ps = parameters.getParameterSpec(
                            DHParameterSpec.class);
                    if (ps != null) {
                        return ps.getP().bitLength();
                    }
                } catch (InvalidParameterSpecException ipse) {
                    // ignore
                }
                break;

            // May support more AlgorithmParameters algorithms in the future.
        }

        return -1;
    }

    /**
     * Returns the algorithm name of the given key object. If an EC key is
     * specified, returns the algorithm name and its named curve.
     *
     * @param key the key object, cannot be null
     * @return the algorithm name of the given key object, or return in the
     *       form of "EC (named curve)" if the given key object is an EC key
     */
    public static final String fullDisplayAlgName(Key key) {
        String result = key.getAlgorithm();
        if (key instanceof ECKey) {
            ECParameterSpec paramSpec = ((ECKey) key).getParams();
            if (paramSpec instanceof NamedCurve nc) {
                result += " (" + nc.getNameAndAliases()[0] + ")";
            }
        } else if (key instanceof EdECKey) {
            result = ((EdECKey) key).getParams().getName();
        }
        return result;
    }

    /**
     * Returns whether the key is valid or not.
     * <P>
     * Note that this method is only apply to DHPublicKey at present.
     *
     * @param  key the key object, cannot be null
     *
     * @throws NullPointerException if {@code key} is null
     * @throws InvalidKeyException if {@code key} is invalid
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
     *
     * @param  providerName
     *         the provider name
     * @return true if, and only if, the provider of the specified
     *         {@code providerName} is Oracle provider
     */
    public static final boolean isOracleJCEProvider(String providerName) {
        return providerName != null &&
                (providerName.equals("SunJCE") ||
                    providerName.equals("SunMSCAPI") ||
                    providerName.startsWith("SunPKCS11"));
    }

    /**
     * Check the format of TLS PreMasterSecret.
     * <P>
     * To avoid vulnerabilities described by section 7.4.7.1, RFC 5246,
     * treating incorrectly formatted message blocks and/or mismatched
     * version numbers in a manner indistinguishable from correctly
     * formatted RSA blocks.
     *
     * RFC 5246 describes the approach as:
     * <pre>{@literal
     *
     *  1. Generate a string R of 48 random bytes
     *
     *  2. Decrypt the message to recover the plaintext M
     *
     *  3. If the PKCS#1 padding is not correct, or the length of message
     *     M is not exactly 48 bytes:
     *        pre_master_secret = R
     *     else If ClientHello.client_version <= TLS 1.0, and version
     *     number check is explicitly disabled:
     *        premaster secret = M
     *     else If M[0..1] != ClientHello.client_version:
     *        premaster secret = R
     *     else:
     *        premaster secret = M
     *
     * Note that #2 should have completed before the call to this method.
     * }</pre>
     *
     * @param  clientVersion the version of the TLS protocol by which the
     *         client wishes to communicate during this session
     * @param  serverVersion the negotiated version of the TLS protocol which
     *         contains the lower of that suggested by the client in the client
     *         hello and the highest supported by the server.
     * @param  encoded the encoded key in its "RAW" encoding format
     * @param  failure true if encoded is incorrect according to previous checks
     * @return the polished PreMasterSecret key in its "RAW" encoding format
     */
    public static byte[] checkTlsPreMasterSecretKey(
            int clientVersion, int serverVersion, SecureRandom random,
            byte[] encoded, boolean failure) {

        byte[] tmp;

        if (random == null) {
            random = JCAUtil.getSecureRandom();
        }
        byte[] replacer = new byte[48];
        random.nextBytes(replacer);

        if (failure) {
            tmp = replacer;
        } else {
            tmp = encoded;
        }

        if (tmp == null) {
            encoded = replacer;
        } else {
            encoded = tmp;
        }
        // check the length
        if (encoded.length != 48) {
            // private, don't need to clone the byte array.
            tmp = replacer;
        } else {
            tmp = encoded;
        }

        int encodedVersion =
                ((tmp[0] & 0xFF) << 8) | (tmp[1] & 0xFF);
        int check1 = 0;
        int check2 = 0;
        int check3 = 0;
        if (clientVersion != encodedVersion) check1 = 1;
        if (clientVersion > 0x0301) check2 = 1;
        if (serverVersion != encodedVersion) check3 = 1;
        if ((check1 & (check2 | check3)) == 1) {
            return replacer;
        } else {
            return tmp;
        }
    }

    /**
     * Returns whether the Diffie-Hellman public key is valid or not.
     *
     * Per RFC 2631 and NIST SP800-56A, the following algorithm is used to
     * validate Diffie-Hellman public keys:
     * 1. Verify that y lies within the interval [2,p-1]. If it does not,
     *    the key is invalid.
     * 2. Compute y^q mod p. If the result == 1, the key is valid.
     *    Otherwise, the key is invalid.
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

        // y^q mod p == 1?
        // Unable to perform this check as q is unknown in this circumstance.

        // p is expected to be prime.  However, it is too expensive to check
        // that p is prime.  Instead, in order to mitigate the impact of
        // non-prime values, we check that y is not a factor of p.
        BigInteger r = p.remainder(y);
        if (r.equals(BigInteger.ZERO)) {
            throw new InvalidKeyException("Invalid Diffie-Hellman parameters");
        }
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

    /**
     * Finds the hash algorithm from an HSS/LMS public key.
     *
     * @param publicKey the HSS/LMS public key
     * @return the hash algorithm
     * @throws NoSuchAlgorithmException if key is from an unknown configuration
     */
    public static String hashAlgFromHSS(PublicKey publicKey)
            throws NoSuchAlgorithmException {
        try {
            DerValue val = new DerValue(publicKey.getEncoded());
            val.data.getDerValue();
            byte[] rawKey = new DerValue(val.data.getBitString()).getOctetString();
            // According to https://www.rfc-editor.org/rfc/rfc8554.html:
            // Section 6.1: HSS public key is u32str(L) || pub[0], where pub[0]
            // is the LMS public key for the top-level tree.
            // Section 5.3: LMS public key is u32str(type) || u32str(otstype) || I || T[1]
            // Section 8: type is the numeric identifier for an LMS specification.
            // This RFC defines 5 SHA-256 based types, value from 5 to 9.
            if (rawKey.length < 8) {
                throw new NoSuchAlgorithmException("Cannot decode public key");
            }
            int num = ((rawKey[4] & 0xff) << 24) + ((rawKey[5] & 0xff) << 16)
                    + ((rawKey[6] & 0xff) << 8) + (rawKey[7] & 0xff);
            return switch (num) {
                // RFC 8554 only supports SHA_256 hash algorithm
                case 5, 6, 7, 8, 9 -> "SHA-256";
                default -> throw new NoSuchAlgorithmException("Unknown LMS type: " + num);
            };
        } catch (IOException e) {
            throw new NoSuchAlgorithmException("Cannot decode public key", e);
        }
    }
}

