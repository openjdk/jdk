/*
 * Copyright (c) 1996, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.security.*;
import java.io.IOException;
import javax.net.ssl.SSLHandshakeException;
import javax.crypto.SecretKey;
import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.*;

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
        try {
            KeyPairGenerator kpg = JsseJce.getKeyPairGenerator("DiffieHellman");
            kpg.initialize(keyLength, random);

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


    /**
     * Generate a Diffie-Hellman keypair using the specified parameters.
     *
     * @param modulus the Diffie-Hellman modulus P
     * @param base the Diffie-Hellman base G
     */
    DHCrypt(BigInteger modulus, BigInteger base, SecureRandom random) {
        this.modulus = modulus;
        this.base = base;
        try {
            KeyPairGenerator kpg = JsseJce.getKeyPairGenerator("DiffieHellman");
            DHParameterSpec params = new DHParameterSpec(modulus, base);
            kpg.initialize(params, random);

            DHPublicKeySpec spec = generateDHPublicKeySpec(kpg);
            if (spec == null) {
                throw new RuntimeException("Could not generate DH keypair");
            }

            publicValue = spec.getY();
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
            boolean keyIsValidated) throws IOException {
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
            throw new RuntimeException("Could not generate secret", e);
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
}
