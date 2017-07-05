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


package sun.security.ssl;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.math.BigInteger;
import java.security.*;
import java.io.IOException;
import javax.net.ssl.SSLHandshakeException;
import javax.crypto.SecretKey;
import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.*;
import java.util.EnumSet;

import sun.security.util.KeyUtil;

/**
 * This class implements the Diffie-Hellman key exchange algorithm.
 * D-H means combining your private key with your partners public key to
 * generate a number. The peer does the same with its private key and our
 * public key. Through the magic of Diffie-Hellman we both come up with the
 * same number. This number is secret (discounting MITM attacks) and hence
 * called the shared secret. It has the same length as the modulus, e.g. 512
 * or 1024 bit. Man-in-the-middle attacks are typically countered by an
 * independent authentication step using certificates (RSA, DSA, etc.).
 *
 * The thing to note is that the shared secret is constant for two partners
 * with constant private keys. This is often not what we want, which is why
 * it is generally a good idea to create a new private key for each session.
 * Generating a private key involves one modular exponentiation assuming
 * suitable D-H parameters are available.
 *
 * General usage of this class (TLS DHE case):
 *  . if we are server, call DHCrypt(keyLength,random). This generates
 *    an ephemeral keypair of the request length.
 *  . if we are client, call DHCrypt(modulus, base, random). This
 *    generates an ephemeral keypair using the parameters specified by
 *    the server.
 *  . send parameters and public value to remote peer
 *  . receive peers ephemeral public key
 *  . call getAgreedSecret() to calculate the shared secret
 *
 * In TLS the server chooses the parameter values itself, the client must use
 * those sent to it by the server.
 *
 * The use of ephemeral keys as described above also achieves what is called
 * "forward secrecy". This means that even if the authentication keys are
 * broken at a later date, the shared secret remains secure. The session is
 * compromised only if the authentication keys are already broken at the
 * time the key exchange takes place and an active MITM attack is used.
 * This is in contrast to straightforward encrypting RSA key exchanges.
 *
 * @author David Brownell
 */
final class DHCrypt {

    // group parameters (prime modulus and generator)
    private BigInteger modulus;                 // P (aka N)
    private BigInteger base;                    // G (aka alpha)

    // our private key (including private component x)
    private PrivateKey privateKey;

    // public component of our key, X = (g ^ x) mod p
    private BigInteger publicValue;             // X (aka y)

    // the times to recove from failure if public key validation
    private static int MAX_FAILOVER_TIMES = 2;

    /**
     * Generate a Diffie-Hellman keypair of the specified size.
     */
    DHCrypt(int keyLength, SecureRandom random) {
        this(keyLength,
                ParametersHolder.definedParams.get(keyLength), random);
    }

    /**
     * Generate a Diffie-Hellman keypair using the specified parameters.
     *
     * @param modulus the Diffie-Hellman modulus P
     * @param base the Diffie-Hellman base G
     */
    DHCrypt(BigInteger modulus, BigInteger base, SecureRandom random) {
        this(modulus.bitLength(),
                new DHParameterSpec(modulus, base), random);
    }

    /**
     * Generate a Diffie-Hellman keypair using the specified size and
     * parameters.
     */
    private DHCrypt(int keyLength,
            DHParameterSpec params, SecureRandom random) {

        try {
            KeyPairGenerator kpg = JsseJce.getKeyPairGenerator("DiffieHellman");
            if (params != null) {
                kpg.initialize(params, random);
            } else {
                kpg.initialize(keyLength, random);
            }

            DHPublicKeySpec spec = generateDHPublicKeySpec(kpg);
            if (spec == null) {
                throw new RuntimeException("Could not generate DH keypair");
            }

            publicValue = spec.getY();
            modulus = spec.getP();
            base = spec.getG();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Could not generate DH keypair", e);
        }
    }

    static DHPublicKeySpec getDHPublicKeySpec(PublicKey key) {
        if (key instanceof DHPublicKey) {
            DHPublicKey dhKey = (DHPublicKey)key;
            DHParameterSpec params = dhKey.getParams();
            return new DHPublicKeySpec(dhKey.getY(),
                                    params.getP(), params.getG());
        }
        try {
            KeyFactory factory = JsseJce.getKeyFactory("DH");
            return factory.getKeySpec(key, DHPublicKeySpec.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /** Returns the Diffie-Hellman modulus. */
    BigInteger getModulus() {
        return modulus;
    }

    /** Returns the Diffie-Hellman base (generator).  */
    BigInteger getBase() {
        return base;
    }

    /**
     * Gets the public key of this end of the key exchange.
     */
    BigInteger getPublicKey() {
        return publicValue;
    }

    /**
     * Get the secret data that has been agreed on through Diffie-Hellman
     * key agreement protocol.  Note that in the two party protocol, if
     * the peer keys are already known, no other data needs to be sent in
     * order to agree on a secret.  That is, a secured message may be
     * sent without any mandatory round-trip overheads.
     *
     * <P>It is illegal to call this member function if the private key
     * has not been set (or generated).
     *
     * @param  peerPublicKey the peer's public key.
     * @param  keyIsValidated whether the {@code peerPublicKey} has beed
     *         validated
     * @return the secret, which is an unsigned big-endian integer
     *         the same size as the Diffie-Hellman modulus.
     */
    SecretKey getAgreedSecret(BigInteger peerPublicValue,
            boolean keyIsValidated) throws SSLHandshakeException {
        try {
            KeyFactory kf = JsseJce.getKeyFactory("DiffieHellman");
            DHPublicKeySpec spec =
                        new DHPublicKeySpec(peerPublicValue, modulus, base);
            PublicKey publicKey = kf.generatePublic(spec);
            KeyAgreement ka = JsseJce.getKeyAgreement("DiffieHellman");

            // validate the Diffie-Hellman public key
            if (!keyIsValidated &&
                    !KeyUtil.isOracleJCEProvider(ka.getProvider().getName())) {
                try {
                    KeyUtil.validate(spec);
                } catch (InvalidKeyException ike) {
                    // prefer handshake_failure alert to internal_error alert
                    throw new SSLHandshakeException(ike.getMessage());
                }
            }

            ka.init(privateKey);
            ka.doPhase(publicKey, true);
            return ka.generateSecret("TlsPremasterSecret");
        } catch (GeneralSecurityException e) {
            throw (SSLHandshakeException) new SSLHandshakeException(
                "Could not generate secret").initCause(e);
        }
    }

    // Check constraints of the specified DH public key.
    void checkConstraints(AlgorithmConstraints constraints,
            BigInteger peerPublicValue) throws SSLHandshakeException {

        try {
            KeyFactory kf = JsseJce.getKeyFactory("DiffieHellman");
            DHPublicKeySpec spec =
                        new DHPublicKeySpec(peerPublicValue, modulus, base);
            DHPublicKey publicKey = (DHPublicKey)kf.generatePublic(spec);

            // check constraints of DHPublicKey
            if (!constraints.permits(
                    EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), publicKey)) {
                throw new SSLHandshakeException(
                    "DHPublicKey does not comply to algorithm constraints");
            }
        } catch (GeneralSecurityException gse) {
            throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate DHPublicKey").initCause(gse);
        }
    }

    // Generate and validate DHPublicKeySpec
    private DHPublicKeySpec generateDHPublicKeySpec(KeyPairGenerator kpg)
            throws GeneralSecurityException {

        boolean doExtraValiadtion =
                    (!KeyUtil.isOracleJCEProvider(kpg.getProvider().getName()));
        for (int i = 0; i <= MAX_FAILOVER_TIMES; i++) {
            KeyPair kp = kpg.generateKeyPair();
            privateKey = kp.getPrivate();
            DHPublicKeySpec spec = getDHPublicKeySpec(kp.getPublic());

            // validate the Diffie-Hellman public key
            if (doExtraValiadtion) {
                try {
                    KeyUtil.validate(spec);
                } catch (InvalidKeyException ivke) {
                    if (i == MAX_FAILOVER_TIMES) {
                        throw ivke;
                    }
                    // otherwise, ignore the exception and try the next one
                    continue;
                }
            }

            return spec;
        }

        return null;
    }

    // lazy initialization holder class idiom for static default parameters
    //
    // See Effective Java Second Edition: Item 71.
    private static class ParametersHolder {
        private final static boolean debugIsOn =
                (Debug.getInstance("ssl") != null) && Debug.isOn("sslctx");

        //
        // Default DH ephemeral parameters
        //
        private static final BigInteger g2 = BigInteger.valueOf(2);

        private static final BigInteger p512 = new BigInteger(   // generated
                "D87780E15FF50B4ABBE89870188B049406B5BEA98AB23A02" +
                "41D88EA75B7755E669C08093D3F0CA7FC3A5A25CF067DCB9" +
                "A43DD89D1D90921C6328884461E0B6D3", 16);
        private static final BigInteger p768 = new BigInteger(   // RFC 2409
                "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                "E485B576625E7EC6F44C42E9A63A3620FFFFFFFFFFFFFFFF", 16);

        private static final BigInteger p1024 = new BigInteger(  // RFC 2409
                "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381" +
                "FFFFFFFFFFFFFFFF", 16);
        private static final BigInteger p2048 = new BigInteger(  // TLS FEDHE
                "FFFFFFFFFFFFFFFFADF85458A2BB4A9AAFDC5620273D3CF1" +
                "D8B9C583CE2D3695A9E13641146433FBCC939DCE249B3EF9" +
                "7D2FE363630C75D8F681B202AEC4617AD3DF1ED5D5FD6561" +
                "2433F51F5F066ED0856365553DED1AF3B557135E7F57C935" +
                "984F0C70E0E68B77E2A689DAF3EFE8721DF158A136ADE735" +
                "30ACCA4F483A797ABC0AB182B324FB61D108A94BB2C8E3FB" +
                "B96ADAB760D7F4681D4F42A3DE394DF4AE56EDE76372BB19" +
                "0B07A7C8EE0A6D709E02FCE1CDF7E2ECC03404CD28342F61" +
                "9172FE9CE98583FF8E4F1232EEF28183C3FE3B1B4C6FAD73" +
                "3BB5FCBC2EC22005C58EF1837D1683B2C6F34A26C1B2EFFA" +
                "886B423861285C97FFFFFFFFFFFFFFFF", 16);

        private static final BigInteger[] supportedPrimes = {
                p512, p768, p1024, p2048};

        // a measure of the uncertainty that prime modulus p is not a prime
        //
        // see BigInteger.isProbablePrime(int certainty)
        private final static int PRIME_CERTAINTY = 120;

        // the known security property, jdk.tls.server.defaultDHEParameters
        private final static String PROPERTY_NAME =
                "jdk.tls.server.defaultDHEParameters";

        private static final Pattern spacesPattern = Pattern.compile("\\s+");

        private final static Pattern syntaxPattern = Pattern.compile(
                "(\\{[0-9A-Fa-f]+,[0-9A-Fa-f]+\\})" +
                "(,\\{[0-9A-Fa-f]+,[0-9A-Fa-f]+\\})*");

        private static final Pattern paramsPattern = Pattern.compile(
                "\\{([0-9A-Fa-f]+),([0-9A-Fa-f]+)\\}");

        // cache of predefined default DH ephemeral parameters
        private final static Map<Integer,DHParameterSpec> definedParams;

        static {
            String property = AccessController.doPrivileged(
                new PrivilegedAction<String>() {
                    public String run() {
                        return Security.getProperty(PROPERTY_NAME);
                    }
                });

            if (property != null && !property.isEmpty()) {
                // remove double quote marks from beginning/end of the property
                if (property.length() >= 2 && property.charAt(0) == '"' &&
                        property.charAt(property.length() - 1) == '"') {
                    property = property.substring(1, property.length() - 1);
                }

                property = property.trim();
            }

            if (property != null && !property.isEmpty()) {
                Matcher spacesMatcher = spacesPattern.matcher(property);
                property = spacesMatcher.replaceAll("");

                if (debugIsOn) {
                    System.out.println("The Security Property " +
                            PROPERTY_NAME + ": " + property);
                }
            }

            Map<Integer,DHParameterSpec> defaultParams = new HashMap<>();
            if (property != null && !property.isEmpty()) {
                Matcher syntaxMatcher = syntaxPattern.matcher(property);
                if (syntaxMatcher.matches()) {
                    Matcher paramsFinder = paramsPattern.matcher(property);
                    while(paramsFinder.find()) {
                        String primeModulus = paramsFinder.group(1);
                        BigInteger p = new BigInteger(primeModulus, 16);
                        if (!p.isProbablePrime(PRIME_CERTAINTY)) {
                            if (debugIsOn) {
                                System.out.println(
                                    "Prime modulus p in Security Property, " +
                                    PROPERTY_NAME + ", is not a prime: " +
                                    primeModulus);
                            }

                            continue;
                        }

                        String baseGenerator = paramsFinder.group(2);
                        BigInteger g = new BigInteger(baseGenerator, 16);

                        DHParameterSpec spec = new DHParameterSpec(p, g);
                        int primeLen = p.bitLength();
                        defaultParams.put(primeLen, spec);
                    }
                } else if (debugIsOn) {
                    System.out.println("Invalid Security Property, " +
                            PROPERTY_NAME + ", definition");
                }
            }

            for (BigInteger p : supportedPrimes) {
                int primeLen = p.bitLength();
                defaultParams.putIfAbsent(primeLen, new DHParameterSpec(p, g2));
            }

            definedParams =
                    Collections.<Integer,DHParameterSpec>unmodifiableMap(
                                                                defaultParams);
        }
    }
}
