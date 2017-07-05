/*
 * Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.security.*;
import java.security.interfaces.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import javax.net.ssl.*;

import sun.security.internal.spec.TlsRsaPremasterSecretParameterSpec;

/**
 * This is the client key exchange message (CLIENT --> SERVER) used with
 * all RSA key exchanges; it holds the RSA-encrypted pre-master secret.
 *
 * The message is encrypted using PKCS #1 block type 02 encryption with the
 * server's public key.  The padding and resulting message size is a function
 * of this server's public key modulus size, but the pre-master secret is
 * always exactly 48 bytes.
 *
 */
final class RSAClientKeyExchange extends HandshakeMessage {

    /**
     * The TLS spec says that the version in the RSA premaster secret must
     * be the maximum version supported by the client (i.e. the version it
     * requested in its client hello version). However, we (and other
     * implementations) used to send the active negotiated version. The
     * system property below allows to toggle the behavior.
     */
    private final static String PROP_NAME =
                                "com.sun.net.ssl.rsaPreMasterSecretFix";

    /*
     * Default is "false" (old behavior) for compatibility reasons in
     * SSLv3/TLSv1.  Later protocols (TLSv1.1+) do not use this property.
     */
    private final static boolean rsaPreMasterSecretFix =
                                Debug.getBooleanProperty(PROP_NAME, false);

    /*
     * The following field values were encrypted with the server's public
     * key (or temp key from server key exchange msg) and are presented
     * here in DECRYPTED form.
     */
    private ProtocolVersion protocolVersion; // preMaster [0,1]
    SecretKey preMaster;
    private byte[] encrypted;           // same size as public modulus

    /*
     * Client randomly creates a pre-master secret and encrypts it
     * using the server's RSA public key; only the server can decrypt
     * it, using its RSA private key.  Result is the same size as the
     * server's public key, and uses PKCS #1 block format 02.
     */
    RSAClientKeyExchange(ProtocolVersion protocolVersion,
            ProtocolVersion maxVersion,
            SecureRandom generator, PublicKey publicKey) throws IOException {
        if (publicKey.getAlgorithm().equals("RSA") == false) {
            throw new SSLKeyException("Public key not of type RSA");
        }
        this.protocolVersion = protocolVersion;

        int major, minor;

        if (rsaPreMasterSecretFix || maxVersion.v >= ProtocolVersion.TLS11.v) {
            major = maxVersion.major;
            minor = maxVersion.minor;
        } else {
            major = protocolVersion.major;
            minor = protocolVersion.minor;
        }

        try {
            String s = ((protocolVersion.v >= ProtocolVersion.TLS12.v) ?
                "SunTls12RsaPremasterSecret" : "SunTlsRsaPremasterSecret");
            KeyGenerator kg = JsseJce.getKeyGenerator(s);
            kg.init(new TlsRsaPremasterSecretParameterSpec(major, minor));
            preMaster = kg.generateKey();

            Cipher cipher = JsseJce.getCipher(JsseJce.CIPHER_RSA_PKCS1);
            cipher.init(Cipher.WRAP_MODE, publicKey, generator);
            encrypted = cipher.wrap(preMaster);
        } catch (GeneralSecurityException e) {
            throw (SSLKeyException)new SSLKeyException
                                ("RSA premaster secret error").initCause(e);
        }
    }

    /*
     * Server gets the PKCS #1 (block format 02) data, decrypts
     * it with its private key.
     */
    RSAClientKeyExchange(ProtocolVersion currentVersion,
            ProtocolVersion maxVersion,
            SecureRandom generator, HandshakeInStream input,
            int messageSize, PrivateKey privateKey) throws IOException {

        if (privateKey.getAlgorithm().equals("RSA") == false) {
            throw new SSLKeyException("Private key not of type RSA");
        }

        if (currentVersion.v >= ProtocolVersion.TLS10.v) {
            encrypted = input.getBytes16();
        } else {
            encrypted = new byte [messageSize];
            if (input.read(encrypted) != messageSize) {
                throw new SSLProtocolException
                        ("SSL: read PreMasterSecret: short read");
            }
        }

        try {
            Cipher cipher = JsseJce.getCipher(JsseJce.CIPHER_RSA_PKCS1);
            cipher.init(Cipher.UNWRAP_MODE, privateKey);
            preMaster = (SecretKey)cipher.unwrap(encrypted,
                                "TlsRsaPremasterSecret", Cipher.SECRET_KEY);

            // polish the premaster secret
            preMaster = polishPreMasterSecretKey(currentVersion, maxVersion,
                                                generator, preMaster, null);
        } catch (Exception e) {
            // polish the premaster secret
            preMaster =
                    polishPreMasterSecretKey(currentVersion, maxVersion,
                                                generator, null, e);
        }
    }

    /**
     * To avoid vulnerabilities described by section 7.4.7.1, RFC 5246,
     * treating incorrectly formatted message blocks and/or mismatched
     * version numbers in a manner indistinguishable from correctly
     * formatted RSA blocks.
     *
     * RFC 5246 describes the approach as :
     *
     *  1. Generate a string R of 46 random bytes
     *
     *  2. Decrypt the message to recover the plaintext M
     *
     *  3. If the PKCS#1 padding is not correct, or the length of message
     *     M is not exactly 48 bytes:
     *        pre_master_secret = ClientHello.client_version || R
     *     else If ClientHello.client_version <= TLS 1.0, and version
     *     number check is explicitly disabled:
     *        pre_master_secret = M
     *     else:
     *        pre_master_secret = ClientHello.client_version || M[2..47]
     */
    private SecretKey polishPreMasterSecretKey(ProtocolVersion currentVersion,
            ProtocolVersion clientHelloVersion, SecureRandom generator,
            SecretKey secretKey, Exception failoverException) {

        this.protocolVersion = clientHelloVersion;

        if (failoverException == null && secretKey != null) {
            // check the length
            byte[] encoded = secretKey.getEncoded();
            if (encoded == null) {      // unable to get the encoded key
                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println(
                        "unable to get the plaintext of the premaster secret");
                }

                // We are not always able to get the encoded key of the
                // premaster secret. Pass the cheking to master secret
                // calculation.
                return secretKey;
            } else if (encoded.length == 48) {
                // check the version
                if (clientHelloVersion.major == encoded[0] &&
                    clientHelloVersion.minor == encoded[1]) {
                    return secretKey;
                } else if (clientHelloVersion.v <= ProtocolVersion.TLS10.v) {
                    /*
                     * we never checked the client_version in server side
                     * for TLS v1.0 and SSL v3.0. For compatibility, we
                     * maintain this behavior.
                     */
                    if (currentVersion.major == encoded[0] &&
                        currentVersion.minor == encoded[1]) {
                        this.protocolVersion = currentVersion;
                        return secretKey;
                    }
                }

                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println("Mismatching Protocol Versions, " +
                        "ClientHello.client_version is " + clientHelloVersion +
                        ", while PreMasterSecret.client_version is " +
                        ProtocolVersion.valueOf(encoded[0], encoded[1]));
                }
            } else {
                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println(
                        "incorrect length of premaster secret: " +
                        encoded.length);
                }
            }
        }

        if (debug != null && Debug.isOn("handshake")) {
            if (failoverException != null) {
                System.out.println("Error decrypting premaster secret:");
                failoverException.printStackTrace(System.out);
            }

            System.out.println("Generating random secret");
        }

        return generateDummySecret(clientHelloVersion);
    }

    // generate a premaster secret with the specified version number
    static SecretKey generateDummySecret(ProtocolVersion version) {
        try {
            String s = ((version.v >= ProtocolVersion.TLS12.v) ?
                "SunTls12RsaPremasterSecret" : "SunTlsRsaPremasterSecret");
            KeyGenerator kg = JsseJce.getKeyGenerator(s);
            kg.init(new TlsRsaPremasterSecretParameterSpec
                    (version.major, version.minor));
            return kg.generateKey();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Could not generate dummy secret", e);
        }
    }

    @Override
    int messageType() {
        return ht_client_key_exchange;
    }

    @Override
    int messageLength() {
        if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
            return encrypted.length + 2;
        } else {
            return encrypted.length;
        }
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
            s.putBytes16(encrypted);
        } else {
            s.write(encrypted);
        }
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** ClientKeyExchange, RSA PreMasterSecret, " +
                                                        protocolVersion);
    }
}
