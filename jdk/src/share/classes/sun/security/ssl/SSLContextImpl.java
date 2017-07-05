/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import java.security.cert.*;

import javax.net.ssl.*;

public class SSLContextImpl extends SSLContextSpi {

    private static final Debug debug = Debug.getInstance("ssl");

    private final EphemeralKeyManager ephemeralKeyManager;
    private final SSLSessionContextImpl clientCache;
    private final SSLSessionContextImpl serverCache;

    private boolean isInitialized;

    private X509ExtendedKeyManager keyManager;
    private X509TrustManager trustManager;
    private SecureRandom secureRandom;

    public SSLContextImpl() {
        this(null);
    }

    SSLContextImpl(SSLContextImpl other) {
        if (other == null) {
            ephemeralKeyManager = new EphemeralKeyManager();
            clientCache = new SSLSessionContextImpl();
            serverCache = new SSLSessionContextImpl();
        } else {
            ephemeralKeyManager = other.ephemeralKeyManager;
            clientCache = other.clientCache;
            serverCache = other.serverCache;
        }
    }

    protected void engineInit(KeyManager[] km, TrustManager[] tm,
                                SecureRandom sr) throws KeyManagementException {
        isInitialized = false;
        keyManager = chooseKeyManager(km);

        if (tm == null) {
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore)null);
                tm = tmf.getTrustManagers();
            } catch (Exception e) {
                // eat
            }
        }
        trustManager = chooseTrustManager(tm);

        if (sr == null) {
            secureRandom = JsseJce.getSecureRandom();
        } else {
            if (SunJSSE.isFIPS() && (sr.getProvider() != SunJSSE.cryptoProvider)) {
                throw new KeyManagementException
                    ("FIPS mode: SecureRandom must be from provider "
                    + SunJSSE.cryptoProvider.getName());
            }
            secureRandom = sr;
        }

        /*
         * The initial delay of seeding the random number generator
         * could be long enough to cause the initial handshake on our
         * first connection to timeout and fail. Make sure it is
         * primed and ready by getting some initial output from it.
         */
        if (debug != null && Debug.isOn("sslctx")) {
            System.out.println("trigger seeding of SecureRandom");
        }
        secureRandom.nextInt();
        if (debug != null && Debug.isOn("sslctx")) {
            System.out.println("done seeding SecureRandom");
        }
        isInitialized = true;
    }

    private X509TrustManager chooseTrustManager(TrustManager[] tm)
            throws KeyManagementException {
        // We only use the first instance of X509TrustManager passed to us.
        for (int i = 0; tm != null && i < tm.length; i++) {
            if (tm[i] instanceof X509TrustManager) {
                if (SunJSSE.isFIPS() && !(tm[i] instanceof X509TrustManagerImpl)) {
                    throw new KeyManagementException
                        ("FIPS mode: only SunJSSE TrustManagers may be used");
                }
                return (X509TrustManager)tm[i];
            }
        }

        // nothing found, return a dummy X509TrustManager.
        return DummyX509TrustManager.INSTANCE;
    }

    private X509ExtendedKeyManager chooseKeyManager(KeyManager[] kms)
            throws KeyManagementException {
        for (int i = 0; kms != null && i < kms.length; i++) {
            KeyManager km = kms[i];
            if (km instanceof X509KeyManager == false) {
                continue;
            }
            if (SunJSSE.isFIPS()) {
                // In FIPS mode, require that one of SunJSSE's own keymanagers
                // is used. Otherwise, we cannot be sure that only keys from
                // the FIPS token are used.
                if ((km instanceof X509KeyManagerImpl)
                            || (km instanceof SunX509KeyManagerImpl)) {
                    return (X509ExtendedKeyManager)km;
                } else {
                    // throw exception, we don't want to silently use the
                    // dummy keymanager without telling the user.
                    throw new KeyManagementException
                        ("FIPS mode: only SunJSSE KeyManagers may be used");
                }
            }
            if (km instanceof X509ExtendedKeyManager) {
                return (X509ExtendedKeyManager)km;
            }
            if (debug != null && Debug.isOn("sslctx")) {
                System.out.println(
                    "X509KeyManager passed to " +
                    "SSLContext.init():  need an " +
                    "X509ExtendedKeyManager for SSLEngine use");
            }
            return new AbstractWrapper((X509KeyManager)km);
        }

        // nothing found, return a dummy X509ExtendedKeyManager
        return DummyX509KeyManager.INSTANCE;
    }

    protected SSLSocketFactory engineGetSocketFactory() {
        if (!isInitialized) {
            throw new IllegalStateException(
                "SSLContextImpl is not initialized");
        }
        return new SSLSocketFactoryImpl(this);
    }

    protected SSLServerSocketFactory engineGetServerSocketFactory() {
        if (!isInitialized) {
            throw new IllegalStateException("SSLContext is not initialized");
        }
        return new SSLServerSocketFactoryImpl(this);
    }

    protected SSLEngine engineCreateSSLEngine() {
        if (!isInitialized) {
            throw new IllegalStateException(
                "SSLContextImpl is not initialized");
        }
        return new SSLEngineImpl(this);
    }

    protected SSLEngine engineCreateSSLEngine(String host, int port) {
        if (!isInitialized) {
            throw new IllegalStateException(
                "SSLContextImpl is not initialized");
        }
        return new SSLEngineImpl(this, host, port);
    }

    protected SSLSessionContext engineGetClientSessionContext() {
        return clientCache;
    }

    protected SSLSessionContext engineGetServerSessionContext() {
        return serverCache;
    }

    SecureRandom getSecureRandom() {
        return secureRandom;
    }

    X509ExtendedKeyManager getX509KeyManager() {
        return keyManager;
    }

    X509TrustManager getX509TrustManager() {
        return trustManager;
    }

    EphemeralKeyManager getEphemeralKeyManager() {
        return ephemeralKeyManager;
    }

}

// Dummy X509TrustManager implementation, rejects all peer certificates.
// Used if the application did not specify a proper X509TrustManager.
final class DummyX509TrustManager implements X509TrustManager {

    static final X509TrustManager INSTANCE = new DummyX509TrustManager();

    private DummyX509TrustManager() {
        // empty
    }

    /*
     * Given the partial or complete certificate chain
     * provided by the peer, build a certificate path
     * to a trusted root and return if it can be
     * validated and is trusted for client SSL authentication.
     * If not, it throws an exception.
     */
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
        throw new CertificateException(
            "No X509TrustManager implementation avaiable");
    }

    /*
     * Given the partial or complete certificate chain
     * provided by the peer, build a certificate path
     * to a trusted root and return if it can be
     * validated and is trusted for server SSL authentication.
     * If not, it throws an exception.
     */
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
        throw new CertificateException(
            "No X509TrustManager implementation available");
    }

    /*
     * Return an array of issuer certificates which are trusted
     * for authenticating peers.
     */
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}

/*
 * A wrapper class to turn a X509KeyManager into an X509ExtendedKeyManager
 */
final class AbstractWrapper extends X509ExtendedKeyManager {

    private final X509KeyManager km;

    AbstractWrapper(X509KeyManager km) {
        this.km = km;
    }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return km.getClientAliases(keyType, issuers);
    }

    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket) {
        return km.chooseClientAlias(keyType, issuers, socket);
    }

    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return km.getServerAliases(keyType, issuers);
    }

    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        return km.chooseServerAlias(keyType, issuers, socket);
    }

    public X509Certificate[] getCertificateChain(String alias) {
        return km.getCertificateChain(alias);
    }

    public PrivateKey getPrivateKey(String alias) {
        return km.getPrivateKey(alias);
    }

    // Inherit chooseEngineClientAlias() and chooseEngineServerAlias() from
    // X509ExtendedKeymanager. It defines them to return null;
}


// Dummy X509KeyManager implementation, never returns any certificates/keys.
// Used if the application did not specify a proper X509TrustManager.
final class DummyX509KeyManager extends X509ExtendedKeyManager {

    static final X509ExtendedKeyManager INSTANCE = new DummyX509KeyManager();

    private DummyX509KeyManager() {
        // empty
    }

    /*
     * Get the matching aliases for authenticating the client side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return null;
    }

    /*
     * Choose an alias to authenticate the client side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {
        return null;
    }

    /*
     * Choose an alias to authenticate the client side of an
     * engine given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    public String chooseEngineClientAlias(
            String[] keyTypes, Principal[] issuers, SSLEngine engine) {
        return null;
    }

    /*
     * Get the matching aliases for authenticating the server side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return null;
    }

    /*
     * Choose an alias to authenticate the server side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        return null;
    }

    /*
     * Choose an alias to authenticate the server side of an engine
     * given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    public String chooseEngineServerAlias(
            String keyType, Principal[] issuers, SSLEngine engine) {
        return null;
    }

    /**
     * Returns the certificate chain associated with the given alias.
     *
     * @param alias the alias name
     *
     * @return the certificate chain (ordered with the user's certificate first
     * and the root certificate authority last)
     */
    public X509Certificate[] getCertificateChain(String alias) {
        return null;
    }

    /*
     * Returns the key associated with the given alias, using the given
     * password to recover it.
     *
     * @param alias the alias name
     *
     * @return the requested key
     */
    public PrivateKey getPrivateKey(String alias) {
        return null;
    }
}
