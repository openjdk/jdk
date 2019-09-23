/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.test.lib.security;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/*
 * Utilities for creating key store.
 */
public class KeyStoreUtils {

    private static final String DEFAULT_TYPE = KeyStore.getDefaultType();

    /**
     * Create key store with a given input stream.
     *
     * @param type the key store type
     * @param input the input stream containing a key store
     * @param password the password used to check the integrity of the key store
     * @return the key store
     * @throws Exception on errors
     */
    public static KeyStore loadKeyStore(String type, InputStream input,
            String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        try {
            keyStore.load(input,
                    password == null ? null : password.toCharArray());
            return keyStore;
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    /**
     * Create key store with a given input stream.
     *
     * @param input the input stream containing a key store
     * @param password the password used to check the integrity of the key store
     * @return the key store
     * @throws Exception on errors
     */
    public static KeyStore loadKeyStore(InputStream input, String password)
            throws Exception {
        return loadKeyStore(DEFAULT_TYPE, input, password);
    }

    /**
     * Create key store with given Base64-encoded string.
     *
     * @param keyStoreBase64 the Base64-encoded string containing a key store
     * @param password the password used to check the integrity of the key store
     * @return the key store
     * @throws Exception on errors
     */
    public static KeyStore loadKeyStoreBase64(String keyStoreBase64,
            String password) throws Exception {
        return loadKeyStore(DEFAULT_TYPE, new ByteArrayInputStream(
                Base64.getDecoder().decode(keyStoreBase64)), password);
    }

    /**
     * Create key store with a given file.
     *
     * @param type the key store type
     * @param path the path to file containing a key store
     * @param password the password used to check the integrity of the key store
     * @return the key store
     * @throws Exception on errors
     */
    public static KeyStore loadKeyStore(String type, String path,
            String password) throws Exception {
        return loadKeyStore(type, new FileInputStream(path), password);
    }

    /**
     * Create key store with a given file.
     *
     * @param path the path to file containing a key store
     * @param password the password used to check the integrity of the key store
     * @return the key store
     * @throws Exception on errors
     */
    public static KeyStore loadKeyStore(String path, String password)
            throws Exception {
        return loadKeyStore(DEFAULT_TYPE, path, password);
    }

    /**
     * Create trust store with given certificates.
     *
     * @param type the key store type
     * @param certStrs the certificates added to the trust store
     * @return the trust store
     * @throws Exception on errors
     */
    public static KeyStore createTrustStore(String type, String[] certStrs)
            throws Exception {
        KeyStore trustStore = initKeyStore(type);

        for (int i = 0; i < certStrs.length; i++) {
            trustStore.setCertificateEntry("trust-" + i,
                    CertUtils.getCertFromString(certStrs[i]));
        }

        return trustStore;
    }

    /**
     * Create trust store with given certificates.
     *
     * @param certStrs the certificates added to the trust store
     * @return the trust store
     * @throws Exception on errors
     */
    public static KeyStore createTrustStore(String[] certStrs)
            throws Exception {
        return createTrustStore(DEFAULT_TYPE, certStrs);
    }

    /**
     * Create key store with given entries.
     *
     * @param type the key store type
     * @param entries the key entries added to the key store
     * @return the key store
     * @throws Exception on errors
     */
    public static KeyStore createKeyStore(String type, KeyEntry[] entries)
            throws Exception {
        KeyStore keyStore = initKeyStore(type);

        for (int i = 0; i < entries.length; i++) {
            KeyEntry entry = entries[i];
            PrivateKey key = CertUtils.getKeyFromString(
                    entry.keyAlgo, entry.keyStr);
            char[] password = entry.password == null
                    ? null
                    : entry.password.toCharArray();
            Certificate[] chain = new Certificate[entry.certStrs.length];
            for (int j = 0; j < chain.length; j++) {
                chain[j] = CertUtils.getCertFromString(entry.certStrs[j]);
            }

            keyStore.setKeyEntry("cert-" + i, key, password, chain);
        }

        return keyStore;
    }

    /**
     * Create key store with given entries.
     *
     * @param entries the key entries added to the key store
     * @return the key store
     * @throws Exception on errors
     */
    public static KeyStore createKeyStore(KeyEntry[] entries)
            throws Exception {
        return createKeyStore(DEFAULT_TYPE, entries);
    }

    /**
     * Create key store with given private keys and associated certificate chains.
     * Note that here one chain contains only one certificate. If a chain needs
     * to contain multiple certificates, please use the following methods:
     * createKeyStore(String type, KeyEntry[] entries);
     * createKeyStore(KeyEntry[] entries)
     *
     * @param type the key store type
     * @param keyAlgos the key algorithm array
     * @param keyStrs the PEM-encoded PKCS8 key string array
     * @param passwords the key-associated password array
     * @param certStrs the key-associated certificate array
     * @return  the key store
     * @throws Exception on errors
     */
    public static KeyStore createKeyStore(String type, String[] keyAlgos,
            String[] keyStrs, String[] passwords, String[] certStrs)
            throws Exception {
        KeyEntry[] entries = new KeyEntry[keyStrs.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new KeyEntry(
                    keyAlgos[i],
                    keyStrs[i],
                    passwords == null ? null : passwords[i],
                    new String[] { certStrs[i] });
        }
        return createKeyStore(type, entries);
    }

    /**
     * Create key store with given private keys and associated certificate chains.
     * Note that here one chain contains only one certificate. If a chain needs
     * to contain multiple certificates, please use the following methods:
     * createKeyStore(String type, KeyEntry[] entries);
     * createKeyStore(KeyEntry[] entries)
     *
     * @param keyAlgos the key algorithm array
     * @param keyStrs the PEM-encoded PKCS8 key string array
     * @param passwords the key-associated password array
     * @param certStrs the key-associated certificate array
     * @return  the key store
     * @throws Exception on errors
     */
    public static KeyStore createKeyStore(String[] keyAlgos, String[] keyStrs,
            String[] passwords, String[] certStrs) throws Exception {
        return createKeyStore(DEFAULT_TYPE, keyAlgos, keyStrs, passwords,
                certStrs);
    }

    private static KeyStore initKeyStore(String type) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(null, null);
        return keyStore;
    }

    /**
     * The default trust store that contains RSA, ECDSA, RSASSA-PSS and DSA
     * certificates.
     */
    public static KeyStore defaultTrustStore() throws Exception {
        return createTrustStore(
                new String[] { CertUtils.RSA_CERT, CertUtils.ECDSA_CERT,
                        CertUtils.RSASSAPSS_CERT, CertUtils.DSA_CERT });
    }

    /**
     * The default key store that contains RSA, ECDSA, RSASSA-PSS and DSA
     * certificates.
     */
    public static KeyStore defaultKeyStore() throws Exception {
        List<KeyEntry> entries = new ArrayList<>();
        entries.add(new KeyEntry("RSA", CertUtils.RSA_KEY,
                new String[] { CertUtils.RSA_CERT }));
        entries.add(new KeyEntry("EC", CertUtils.ECDSA_KEY,
                new String[] { CertUtils.ECDSA_CERT }));
        entries.add(new KeyEntry("RSASSA-PSS", CertUtils.RSASSAPSS_KEY,
                new String[] { CertUtils.RSASSAPSS_CERT }));
        entries.add(new KeyEntry("DSA", CertUtils.DSA_KEY,
                new String[] { CertUtils.DSA_CERT }));
        return createKeyStore(entries.toArray(new KeyEntry[entries.size()]));
    }
}
