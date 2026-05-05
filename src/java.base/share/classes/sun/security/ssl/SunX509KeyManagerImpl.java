/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.Socket;
import java.security.AlgorithmConstraints;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.security.auth.x500.X500Principal;


/**
 * An implementation of X509KeyManager backed by a KeyStore.
 * <p>
 * The backing KeyStore is inspected when this object is constructed.
 * All key entries containing a PrivateKey and a non-empty chain of
 * X509Certificate are then copied into an internal store. This means
 * that subsequent modifications of the KeyStore have no effect on the
 * X509KeyManagerImpl object.
 * <p>
 * Note that this class assumes that all keys are protected by the same
 * password.
 * <p>
 * Algorithm constraints and certificate checks can be disabled by setting
 * "jdk.tls.SunX509KeyManager.certChecking" system property to "false"
 * before calling a class constructor.
 *
 */

final class SunX509KeyManagerImpl extends X509KeyManagerCertChecking {

    /*
     * The credentials from the KeyStore as
     * Map: String(alias) -> X509Credentials(credentials)
     */
    private final Map<String, X509Credentials> credentialsMap;

    @Override
    protected boolean isCheckingDisabled() {
        return "false".equalsIgnoreCase(System.getProperty(
                "jdk.tls.SunX509KeyManager.certChecking", "true"));
    }

    /*
     * Basic container for credentials implemented as an inner class.
     */
    private static class X509Credentials {

        final PrivateKey privateKey;
        final X509Certificate[] certificates;

        X509Credentials(PrivateKey privateKey, X509Certificate[] certificates) {
            // assert privateKey and certificates != null
            this.privateKey = privateKey;
            this.certificates = certificates;
        }
    }

    SunX509KeyManagerImpl(KeyStore ks, char[] password)
            throws KeyStoreException,
            NoSuchAlgorithmException, UnrecoverableKeyException {

        credentialsMap = new HashMap<>();

        if (ks == null) {
            return;
        }

        for (Enumeration<String> aliases = ks.aliases();
                aliases.hasMoreElements(); ) {
            String alias = aliases.nextElement();
            if (!ks.isKeyEntry(alias)) {
                continue;
            }
            Key key = ks.getKey(alias, password);
            if (!(key instanceof PrivateKey)) {
                continue;
            }
            Certificate[] certs = ks.getCertificateChain(alias);
            if ((certs == null) || (certs.length == 0) ||
                    !(certs[0] instanceof X509Certificate)) {
                continue;
            }
            if (!(certs instanceof X509Certificate[])) {
                Certificate[] tmp = new X509Certificate[certs.length];
                System.arraycopy(certs, 0, tmp, 0, certs.length);
                certs = tmp;
            }

            X509Credentials cred = new X509Credentials((PrivateKey) key,
                    (X509Certificate[]) certs);
            credentialsMap.put(alias, cred);
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.KEYMANAGER)) {
                SSLLogger.fine("found key for : " + alias, (Object[])certs);
            }
        }
    }

    /*
     * Returns the certificate chain associated with the given alias.
     *
     * @return the certificate chain (ordered with the user's certificate first
     * and the root certificate authority last)
     */
    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        if (alias == null) {
            return null;
        }
        X509Credentials cred = credentialsMap.get(alias);
        if (cred == null) {
            return null;
        } else {
            return cred.certificates.clone();
        }
    }

    /*
     * Returns the key associated with the given alias
     */
    @Override
    public PrivateKey getPrivateKey(String alias) {
        if (alias == null) {
            return null;
        }
        X509Credentials cred = credentialsMap.get(alias);
        if (cred == null) {
            return null;
        } else {
            return cred.privateKey;
        }
    }

    /*
     * Choose an alias to authenticate the client side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                getAlgorithmConstraints(socket), null, null);
    }

    /*
     * Choose an alias to authenticate the client side of an
     * <code>SSLEngine</code> connection given the public key type
     * and the list of certificate issuer authorities recognized by
     * the peer (if any).
     */
    @Override
    public String chooseEngineClientAlias(String[] keyTypes,
            Principal[] issuers, SSLEngine engine) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                getAlgorithmConstraints(engine), null, null);
    }

    @Override
    String chooseQuicClientAlias(String[] keyTypes, Principal[] issuers,
                                 QuicTLSEngineImpl quicTLSEngine) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                getAlgorithmConstraints(quicTLSEngine), null, null);
    }

    /*
     * Choose an alias to authenticate the server side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String chooseServerAlias(String keyType,
            Principal[] issuers, Socket socket) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
                getAlgorithmConstraints(socket),
                X509TrustManagerImpl.getRequestedServerNames(socket), "HTTPS");
    }

    /*
     * Choose an alias to authenticate the server side of an
     * <code>SSLEngine</code> connection given the public key type
     * and the list of certificate issuer authorities recognized by
     * the peer (if any).
     */
    @Override
    public String chooseEngineServerAlias(String keyType,
            Principal[] issuers, SSLEngine engine) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
                getAlgorithmConstraints(engine),
                X509TrustManagerImpl.getRequestedServerNames(engine), "HTTPS");
    }

    @Override
    String chooseQuicServerAlias(String keyType,
                                 X500Principal[] issuers,
                                 QuicTLSEngineImpl quicTLSEngine) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
                getAlgorithmConstraints(quicTLSEngine),
                X509TrustManagerImpl.getRequestedServerNames(quicTLSEngine),
                "HTTPS");
    }

    /*
     * Get the matching aliases for authenticating the client side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return getAliases(getKeyTypes(keyType), issuers, CheckType.CLIENT,
                null, null, null);
    }

    /*
     * Get the matching aliases for authenticating the server side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return getAliases(getKeyTypes(keyType), issuers, CheckType.SERVER,
                null, null, null);
    }

    private String chooseAlias(List<KeyType> keyTypes, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints,
            List<SNIServerName> requestedServerNames, String idAlgorithm) {

        String[] aliases = getAliases(
                keyTypes, issuers, checkType,
                constraints, requestedServerNames, idAlgorithm);

        if (aliases != null && aliases.length > 0) {
            return aliases[0];
        }

        return null;
    }

    /*
     * Get the matching aliases for authenticating the either side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     *
     * Issuers come to us in the form of X500Principal[].
     */
    private String[] getAliases(List<KeyType> keyTypes, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints,
            List<SNIServerName> requestedServerNames,
            String idAlgorithm) {

        if (keyTypes == null || keyTypes.isEmpty()) {
            return null;
        }

        Set<X500Principal> issuerSet = getIssuerSet(issuers);
        List<EntryStatus> results = null;

        for (Map.Entry<String, X509Credentials> entry :
                credentialsMap.entrySet()) {

            EntryStatus status = checkAlias(0, entry.getKey(),
                    entry.getValue().certificates,
                    null, keyTypes, issuerSet, checkType,
                    constraints, requestedServerNames, idAlgorithm);

            if (status == null) {
                continue;
            }

            if (results == null) {
                results = new ArrayList<>();
            }

            results.add(status);
        }

        if (results == null) {
            if (SSLLogger.isOn() &&
                                SSLLogger.isOn(SSLLogger.Opt.KEYMANAGER)) {
                SSLLogger.fine("KeyMgr: no matching key found");
            }
            return null;
        }

        // Sort results in order of alias preference.
        Collections.sort(results);
        return results.stream().map(r -> r.alias).toArray(String[]::new);
    }
}
