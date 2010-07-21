/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl.krb5;

import java.io.*;
import java.security.*;
import java.util.Arrays;

import javax.net.ssl.*;

import sun.security.krb5.EncryptionKey;
import sun.security.krb5.EncryptedData;
import sun.security.krb5.KrbException;
import sun.security.krb5.internal.crypto.KeyUsage;

import sun.security.ssl.Debug;
import sun.security.ssl.HandshakeInStream;
import sun.security.ssl.HandshakeMessage;
import sun.security.ssl.ProtocolVersion;

/**
 * This is the Kerberos premaster secret in the Kerberos client key
 * exchange message (CLIENT --> SERVER); it holds the
 * Kerberos-encrypted pre-master secret. The secret is encrypted using the
 * Kerberos session key.  The padding and size of the resulting message
 * depends on the session key type, but the pre-master secret is
 * always exactly 48 bytes.
 *
 */
final class KerberosPreMasterSecret {

    private ProtocolVersion protocolVersion; // preMaster [0,1]
    private byte preMaster[];           // 48 bytes
    private byte encrypted[];

    /**
     * Constructor used by client to generate premaster secret.
     *
     * Client randomly creates a pre-master secret and encrypts it
     * using the Kerberos session key; only the server can decrypt
     * it, using the session key available in the service ticket.
     *
     * @param protocolVersion used to set preMaster[0,1]
     * @param generator random number generator for generating premaster secret
     * @param sessionKey Kerberos session key for encrypting premaster secret
     */
    KerberosPreMasterSecret(ProtocolVersion protocolVersion,
        SecureRandom generator, EncryptionKey sessionKey) throws IOException {

        if (sessionKey.getEType() ==
            EncryptedData.ETYPE_DES3_CBC_HMAC_SHA1_KD) {
            throw new IOException(
               "session keys with des3-cbc-hmac-sha1-kd encryption type " +
               "are not supported for TLS Kerberos cipher suites");
        }

        this.protocolVersion = protocolVersion;
        preMaster = generatePreMaster(generator, protocolVersion);

        // Encrypt premaster secret
        try {
            EncryptedData eData = new EncryptedData(sessionKey, preMaster,
                KeyUsage.KU_UNKNOWN);
            encrypted = eData.getBytes();  // not ASN.1 encoded.

        } catch (KrbException e) {
            throw (SSLKeyException)new SSLKeyException
                ("Kerberos premaster secret error").initCause(e);
        }
    }

    /*
     * Constructor used by server to decrypt encrypted premaster secret.
     * The protocol version in preMaster[0,1] must match either currentVersion
     * or clientVersion, otherwise, the premaster secret is set to
     * a random one to foil possible attack.
     *
     * @param currentVersion version of protocol being used
     * @param clientVersion version requested by client
     * @param generator random number generator used to generate
     *        bogus premaster secret if premaster secret verification fails
     * @param input input stream from which to read the encrypted
     *        premaster secret
     * @param sessionKey Kerberos session key to be used for decryption
     */
    KerberosPreMasterSecret(ProtocolVersion currentVersion,
        ProtocolVersion clientVersion,
        SecureRandom generator, HandshakeInStream input,
        EncryptionKey sessionKey) throws IOException {

         // Extract encrypted premaster secret from message
         encrypted = input.getBytes16();

         if (HandshakeMessage.debug != null && Debug.isOn("handshake")) {
            if (encrypted != null) {
                Debug.println(System.out,
                     "encrypted premaster secret", encrypted);
            }
         }

        if (sessionKey.getEType() ==
            EncryptedData.ETYPE_DES3_CBC_HMAC_SHA1_KD) {
            throw new IOException(
               "session keys with des3-cbc-hmac-sha1-kd encryption type " +
               "are not supported for TLS Kerberos cipher suites");
        }

        // Decrypt premaster secret
        try {
            EncryptedData data = new EncryptedData(sessionKey.getEType(),
                        null /* optional kvno */, encrypted);

            byte[] temp = data.decrypt(sessionKey, KeyUsage.KU_UNKNOWN);
            if (HandshakeMessage.debug != null && Debug.isOn("handshake")) {
                 if (encrypted != null) {
                     Debug.println(System.out,
                         "decrypted premaster secret", temp);
                 }
            }

            // Remove padding bytes after decryption. Only DES and DES3 have
            // paddings and we don't support DES3 in TLS (see above)

            if (temp.length == 52 &&
                    data.getEType() == EncryptedData.ETYPE_DES_CBC_CRC) {
                // For des-cbc-crc, 4 paddings. Value can be 0x04 or 0x00.
                if (paddingByteIs(temp, 52, (byte)4) ||
                        paddingByteIs(temp, 52, (byte)0)) {
                    temp = Arrays.copyOf(temp, 48);
                }
            } else if (temp.length == 56 &&
                    data.getEType() == EncryptedData.ETYPE_DES_CBC_MD5) {
                // For des-cbc-md5, 8 paddings with 0x08, or no padding
                if (paddingByteIs(temp, 56, (byte)8)) {
                    temp = Arrays.copyOf(temp, 48);
                }
            }

            preMaster = temp;

            protocolVersion = ProtocolVersion.valueOf(preMaster[0],
                 preMaster[1]);
            if (HandshakeMessage.debug != null && Debug.isOn("handshake")) {
                 System.out.println("Kerberos PreMasterSecret version: "
                        + protocolVersion);
            }
        } catch (Exception e) {
            // catch exception & process below
            preMaster = null;
            protocolVersion = currentVersion;
        }

        // check if the premaster secret version is ok
        // the specification says that it must be the maximum version supported
        // by the client from its ClientHello message. However, many
        // implementations send the negotiated version, so accept both
        // NOTE that we may be comparing two unsupported version numbers in
        // the second case, which is why we cannot use object references
        // equality in this special case
        boolean versionMismatch = (protocolVersion != currentVersion) &&
                                  (protocolVersion.v != clientVersion.v);


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
         if ((preMaster == null) || (preMaster.length != 48)
                || versionMismatch) {
            if (HandshakeMessage.debug != null && Debug.isOn("handshake")) {
                System.out.println("Kerberos PreMasterSecret error, "
                                   + "generating random secret");
                if (preMaster != null) {
                    Debug.println(System.out, "Invalid secret", preMaster);
                }
            }
            preMaster = generatePreMaster(generator, currentVersion);
            protocolVersion = currentVersion;
        }
    }

    /**
     * Checks if all paddings of data are b
     * @param data the block with padding
     * @param len length of data, >= 48
     * @param b expected padding byte
     */
    private static boolean paddingByteIs(byte[] data, int len, byte b) {
        for (int i=48; i<len; i++) {
            if (data[i] != b) return false;
        }
        return true;
    }

    /*
     * Used by server to generate premaster secret in case of
     * problem decoding ticket.
     *
     * @param protocolVersion used for preMaster[0,1]
     * @param generator random number generator to use for generating secret.
     */
    KerberosPreMasterSecret(ProtocolVersion protocolVersion,
        SecureRandom generator) {

        this.protocolVersion = protocolVersion;
        preMaster = generatePreMaster(generator, protocolVersion);
    }

    private static byte[] generatePreMaster(SecureRandom rand,
        ProtocolVersion ver) {

        byte[] pm = new byte[48];
        rand.nextBytes(pm);
        pm[0] = ver.major;
        pm[1] = ver.minor;

        return pm;
    }

    // Clone not needed; internal use only
    byte[] getUnencrypted() {
        return preMaster;
    }

    // Clone not needed; internal use only
    byte[] getEncrypted() {
        return encrypted;
    }
}
