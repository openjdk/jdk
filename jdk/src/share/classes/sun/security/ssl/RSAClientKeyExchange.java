/*
 * Copyright 1996-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
     *
     * Default is "false" (old behavior) for compatibility reasons. This
     * will be changed in the future.
     */
    private final static String PROP_NAME =
                                "com.sun.net.ssl.rsaPreMasterSecretFix";

    private final static boolean rsaPreMasterSecretFix =
                                Debug.getBooleanProperty(PROP_NAME, false);

    int messageType() {
        return ht_client_key_exchange;
    }

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
    RSAClientKeyExchange(ProtocolVersion protocolVersion, ProtocolVersion maxVersion,
            SecureRandom generator, PublicKey publicKey) throws IOException {
        if (publicKey.getAlgorithm().equals("RSA") == false) {
            throw new SSLKeyException("Public key not of type RSA");
        }
        this.protocolVersion = protocolVersion;

        int major, minor;

        if (rsaPreMasterSecretFix) {
            major = maxVersion.major;
            minor = maxVersion.minor;
        } else {
            major = protocolVersion.major;
            minor = protocolVersion.minor;
        }

        try {
            KeyGenerator kg = JsseJce.getKeyGenerator("SunTlsRsaPremasterSecret");
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
    RSAClientKeyExchange(ProtocolVersion currentVersion, HandshakeInStream input,
            int messageSize, PrivateKey privateKey) throws IOException {

        if (privateKey.getAlgorithm().equals("RSA") == false) {
            throw new SSLKeyException("Private key not of type RSA");
        }

        this.protocolVersion = currentVersion;
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
        } catch (Exception e) {
            /*
             * Bogus decrypted ClientKeyExchange? If so, conjure a
             * a random preMaster secret that will fail later during
             * Finished message processing. This is a countermeasure against
             * the "interactive RSA PKCS#1 encryption envelop attack" reported
             * in June 1998. Preserving the executation path will
             * mitigate timing attacks and force consistent error handling
             * that will prevent an attacking client from differentiating
             * different kinds of decrypted ClientKeyExchange bogosities.
             */
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("Error decrypting premaster secret:");
                e.printStackTrace(System.out);
                System.out.println("Generating random secret");
            }
            preMaster = generateDummySecret(currentVersion);
        }
    }

    // generate a premaster secret with the specified version number
    static SecretKey generateDummySecret(ProtocolVersion version) {
        try {
            KeyGenerator kg =
                    JsseJce.getKeyGenerator("SunTlsRsaPremasterSecret");
            kg.init(new TlsRsaPremasterSecretParameterSpec
                    (version.major, version.minor));
            return kg.generateKey();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Could not generate dummy secret", e);
        }
    }

    int messageLength() {
        if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
            return encrypted.length + 2;
        } else {
            return encrypted.length;
        }
    }

    void send(HandshakeOutStream s) throws IOException {
        if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
            s.putBytes16(encrypted);
        } else {
            s.write(encrypted);
        }
    }

    void print(PrintStream s) throws IOException {
        s.println("*** ClientKeyExchange, RSA PreMasterSecret, " + protocolVersion);
    }
}
