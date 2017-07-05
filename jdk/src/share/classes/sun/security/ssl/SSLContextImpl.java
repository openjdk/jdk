/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;

import javax.net.ssl.*;

import sun.security.provider.certpath.AlgorithmChecker;

public abstract class SSLContextImpl extends SSLContextSpi {

    private static final Debug debug = Debug.getInstance("ssl");

    private final EphemeralKeyManager ephemeralKeyManager;
    private final SSLSessionContextImpl clientCache;
    private final SSLSessionContextImpl serverCache;

    private boolean isInitialized;

    private X509ExtendedKeyManager keyManager;
    private X509TrustManager trustManager;
    private SecureRandom secureRandom;

    // The default algrithm constraints
    private AlgorithmConstraints defaultAlgorithmConstraints =
                                 new SSLAlgorithmConstraints(null);

    // supported and default protocols
    private ProtocolList defaultServerProtocolList;
    private ProtocolList defaultClientProtocolList;
    private ProtocolList supportedProtocolList;

    // supported and default cipher suites
    private CipherSuiteList defaultServerCipherSuiteList;
    private CipherSuiteList defaultClientCipherSuiteList;
    private CipherSuiteList supportedCipherSuiteList;

    SSLContextImpl() {
        ephemeralKeyManager = new EphemeralKeyManager();
        clientCache = new SSLSessionContextImpl();
        serverCache = new SSLSessionContextImpl();
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
            if (SunJSSE.isFIPS() &&
                        (sr.getProvider() != SunJSSE.cryptoProvider)) {
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
                if (SunJSSE.isFIPS() &&
                        !(tm[i] instanceof X509TrustManagerImpl)) {
                    throw new KeyManagementException
                        ("FIPS mode: only SunJSSE TrustManagers may be used");
                }

                if (tm[i] instanceof X509ExtendedTrustManager) {
                    return (X509TrustManager)tm[i];
                } else {
                    return new AbstractTrustManagerWrapper(
                                        (X509TrustManager)tm[i]);
                }
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
            return new AbstractKeyManagerWrapper((X509KeyManager)km);
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

    abstract SSLParameters getDefaultServerSSLParams();
    abstract SSLParameters getDefaultClientSSLParams();
    abstract SSLParameters getSupportedSSLParams();

    // Get suported ProtoclList.
    ProtocolList getSuportedProtocolList() {
        if (supportedProtocolList == null) {
            supportedProtocolList =
                new ProtocolList(getSupportedSSLParams().getProtocols());
        }

        return supportedProtocolList;
    }

    // Get default ProtoclList.
    ProtocolList getDefaultProtocolList(boolean roleIsServer) {
        if (roleIsServer) {
            if (defaultServerProtocolList == null) {
                defaultServerProtocolList = new ProtocolList(
                        getDefaultServerSSLParams().getProtocols());
            }

            return defaultServerProtocolList;
        } else {
            if (defaultClientProtocolList == null) {
                defaultClientProtocolList = new ProtocolList(
                        getDefaultClientSSLParams().getProtocols());
            }

            return defaultClientProtocolList;
        }
    }

    // Get suported CipherSuiteList.
    CipherSuiteList getSuportedCipherSuiteList() {
        // Clear cache of available ciphersuites.
        clearAvailableCache();

        if (supportedCipherSuiteList == null) {
            supportedCipherSuiteList =
                getApplicableCipherSuiteList(getSuportedProtocolList(), false);
        }

        return supportedCipherSuiteList;
    }

    // Get default CipherSuiteList.
    CipherSuiteList getDefaultCipherSuiteList(boolean roleIsServer) {
        // Clear cache of available ciphersuites.
        clearAvailableCache();

        if (roleIsServer) {
            if (defaultServerCipherSuiteList == null) {
                defaultServerCipherSuiteList = getApplicableCipherSuiteList(
                        getDefaultProtocolList(true), true);
            }

            return defaultServerCipherSuiteList;
        } else {
            if (defaultClientCipherSuiteList == null) {
                defaultClientCipherSuiteList = getApplicableCipherSuiteList(
                        getDefaultProtocolList(false), true);
            }

            return defaultClientCipherSuiteList;
        }
    }

    /**
     * Return whether a protocol list is the original default enabled
     * protocols.  See: SSLSocket/SSLEngine.setEnabledProtocols()
     */
    boolean isDefaultProtocolList(ProtocolList protocols) {
        return (protocols == defaultServerProtocolList) ||
               (protocols == defaultClientProtocolList);
    }


    /*
     * Return the list of all available CipherSuites with a priority of
     * minPriority or above.
     */
    private CipherSuiteList getApplicableCipherSuiteList(
            ProtocolList protocols, boolean onlyEnabled) {

        int minPriority = CipherSuite.SUPPORTED_SUITES_PRIORITY;
        if (onlyEnabled) {
            minPriority = CipherSuite.DEFAULT_SUITES_PRIORITY;
        }

        Collection<CipherSuite> allowedCipherSuites =
                                    CipherSuite.allowedCipherSuites();

        ArrayList<CipherSuite> suites = new ArrayList<>();
        if (!(protocols.collection().isEmpty()) &&
                protocols.min.v != ProtocolVersion.NONE.v) {
            for (CipherSuite suite : allowedCipherSuites) {
                if (suite.allowed == false || suite.priority < minPriority) {
                    continue;
                }

                if (suite.isAvailable() &&
                        suite.obsoleted > protocols.min.v &&
                        suite.supported <= protocols.max.v) {
                    if (defaultAlgorithmConstraints.permits(
                            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                            suite.name, null)) {
                        suites.add(suite);
                    }
                } else if (debug != null &&
                        Debug.isOn("sslctx") && Debug.isOn("verbose")) {
                    if (suite.obsoleted <= protocols.min.v) {
                        System.out.println(
                            "Ignoring obsoleted cipher suite: " + suite);
                    } else if (suite.supported > protocols.max.v) {
                        System.out.println(
                            "Ignoring unsupported cipher suite: " + suite);
                    } else {
                        System.out.println(
                            "Ignoring unavailable cipher suite: " + suite);
                    }
                }
            }
        }

        return new CipherSuiteList(suites);
    }

    /**
     * Clear cache of available ciphersuites. If we support all ciphers
     * internally, there is no need to clear the cache and calling this
     * method has no effect.
     */
    synchronized void clearAvailableCache() {
        if (CipherSuite.DYNAMIC_AVAILABILITY) {
            supportedCipherSuiteList = null;
            defaultServerCipherSuiteList = null;
            defaultClientCipherSuiteList = null;
            CipherSuite.BulkCipher.clearAvailableCache();
            JsseJce.clearEcAvailable();
        }
    }

    /*
     * The SSLContext implementation for TLS/SSL algorithm
     *
     * SSL/TLS protocols specify the forward compatibility and version
     * roll-back attack protections, however, a number of SSL/TLS server
     * vendors did not implement these aspects properly, and some current
     * SSL/TLS servers may refuse to talk to a TLS 1.1 or later client.
     *
     * Considering above interoperability issues, SunJSSE will not set
     * TLS 1.1 and TLS 1.2 as the enabled protocols for client by default.
     *
     * For SSL/TLS servers, there is no such interoperability issues as
     * SSL/TLS clients. In SunJSSE, TLS 1.1 or later version will be the
     * enabled protocols for server by default.
     *
     * We may change the behavior when popular TLS/SSL vendors support TLS
     * forward compatibility properly.
     *
     * SSLv2Hello is no longer necessary.  This interoperability option was
     * put in place in the late 90's when SSLv3/TLS1.0 were relatively new
     * and there were a fair number of SSLv2-only servers deployed.  Because
     * of the security issues in SSLv2, it is rarely (if ever) used, as
     * deployments should now be using SSLv3 and TLSv1.
     *
     * Considering the issues of SSLv2Hello, we should not enable SSLv2Hello
     * by default. Applications still can use it by enabling SSLv2Hello with
     * the series of setEnabledProtocols APIs.
     */

    /*
     * The conservative SSLContext implementation for TLS, SSL, SSLv3 and
     * TLS10 algorithm.
     *
     * This is a super class of DefaultSSLContext and TLS10Context.
     *
     * @see SSLContext
     */
    private static class ConservativeSSLContext extends SSLContextImpl {
        // parameters
        private static SSLParameters defaultServerSSLParams;
        private static SSLParameters defaultClientSSLParams;
        private static SSLParameters supportedSSLParams;

        static {
            if (SunJSSE.isFIPS()) {
                supportedSSLParams = new SSLParameters();
                supportedSSLParams.setProtocols(new String[] {
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

                defaultServerSSLParams = supportedSSLParams;

                defaultClientSSLParams = new SSLParameters();
                defaultClientSSLParams.setProtocols(new String[] {
                    ProtocolVersion.TLS10.name
                });

            } else {
                supportedSSLParams = new SSLParameters();
                supportedSSLParams.setProtocols(new String[] {
                    ProtocolVersion.SSL20Hello.name,
                    ProtocolVersion.SSL30.name,
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

                defaultServerSSLParams = supportedSSLParams;

                defaultClientSSLParams = new SSLParameters();
                defaultClientSSLParams.setProtocols(new String[] {
                    ProtocolVersion.SSL30.name,
                    ProtocolVersion.TLS10.name
                });
            }
        }

        SSLParameters getDefaultServerSSLParams() {
            return defaultServerSSLParams;
        }

        SSLParameters getDefaultClientSSLParams() {
            return defaultClientSSLParams;
        }

        SSLParameters getSupportedSSLParams() {
            return supportedSSLParams;
        }
    }

    /*
     * The SSLContext implementation for default algorithm
     *
     * @see SSLContext
     */
    public static final class DefaultSSLContext extends ConservativeSSLContext {
        private static final String NONE = "NONE";
        private static final String P11KEYSTORE = "PKCS11";

        private static volatile SSLContextImpl defaultImpl;

        private static TrustManager[] defaultTrustManagers;
        private static KeyManager[] defaultKeyManagers;

        public DefaultSSLContext() throws Exception {
            try {
                super.engineInit(getDefaultKeyManager(),
                        getDefaultTrustManager(), null);
            } catch (Exception e) {
                if (debug != null && Debug.isOn("defaultctx")) {
                    System.out.println("default context init failed: " + e);
                }
                throw e;
            }

            if (defaultImpl == null) {
                defaultImpl = this;
            }
        }

        protected void engineInit(KeyManager[] km, TrustManager[] tm,
            SecureRandom sr) throws KeyManagementException {
            throw new KeyManagementException
                ("Default SSLContext is initialized automatically");
        }

        static synchronized SSLContextImpl getDefaultImpl() throws Exception {
            if (defaultImpl == null) {
                new DefaultSSLContext();
            }
            return defaultImpl;
        }

        private static synchronized TrustManager[] getDefaultTrustManager()
                throws Exception {
            if (defaultTrustManagers != null) {
                return defaultTrustManagers;
            }

            KeyStore ks =
                TrustManagerFactoryImpl.getCacertsKeyStore("defaultctx");

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            defaultTrustManagers = tmf.getTrustManagers();
            return defaultTrustManagers;
        }

        private static synchronized KeyManager[] getDefaultKeyManager()
                throws Exception {
            if (defaultKeyManagers != null) {
                return defaultKeyManagers;
            }

            final Map<String,String> props = new HashMap<>();
            AccessController.doPrivileged(
                        new PrivilegedExceptionAction<Object>() {
                public Object run() throws Exception {
                    props.put("keyStore",  System.getProperty(
                                "javax.net.ssl.keyStore", ""));
                    props.put("keyStoreType", System.getProperty(
                                "javax.net.ssl.keyStoreType",
                                KeyStore.getDefaultType()));
                    props.put("keyStoreProvider", System.getProperty(
                                "javax.net.ssl.keyStoreProvider", ""));
                    props.put("keyStorePasswd", System.getProperty(
                                "javax.net.ssl.keyStorePassword", ""));
                    return null;
                }
            });

            final String defaultKeyStore = props.get("keyStore");
            String defaultKeyStoreType = props.get("keyStoreType");
            String defaultKeyStoreProvider = props.get("keyStoreProvider");
            if (debug != null && Debug.isOn("defaultctx")) {
                System.out.println("keyStore is : " + defaultKeyStore);
                System.out.println("keyStore type is : " +
                                        defaultKeyStoreType);
                System.out.println("keyStore provider is : " +
                                        defaultKeyStoreProvider);
            }

            if (P11KEYSTORE.equals(defaultKeyStoreType) &&
                    !NONE.equals(defaultKeyStore)) {
                throw new IllegalArgumentException("if keyStoreType is "
                    + P11KEYSTORE + ", then keyStore must be " + NONE);
            }

            FileInputStream fs = null;
            if (defaultKeyStore.length() != 0 && !NONE.equals(defaultKeyStore)) {
                fs = AccessController.doPrivileged(
                        new PrivilegedExceptionAction<FileInputStream>() {
                    public FileInputStream run() throws Exception {
                        return new FileInputStream(defaultKeyStore);
                    }
                });
            }

            String defaultKeyStorePassword = props.get("keyStorePasswd");
            char[] passwd = null;
            if (defaultKeyStorePassword.length() != 0) {
                passwd = defaultKeyStorePassword.toCharArray();
            }

            /**
             * Try to initialize key store.
             */
            KeyStore ks = null;
            if ((defaultKeyStoreType.length()) != 0) {
                if (debug != null && Debug.isOn("defaultctx")) {
                    System.out.println("init keystore");
                }
                if (defaultKeyStoreProvider.length() == 0) {
                    ks = KeyStore.getInstance(defaultKeyStoreType);
                } else {
                    ks = KeyStore.getInstance(defaultKeyStoreType,
                                        defaultKeyStoreProvider);
                }

                // if defaultKeyStore is NONE, fs will be null
                ks.load(fs, passwd);
            }
            if (fs != null) {
                fs.close();
                fs = null;
            }

            /*
             * Try to initialize key manager.
             */
            if (debug != null && Debug.isOn("defaultctx")) {
                System.out.println("init keymanager of type " +
                    KeyManagerFactory.getDefaultAlgorithm());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());

            if (P11KEYSTORE.equals(defaultKeyStoreType)) {
                kmf.init(ks, null); // do not pass key passwd if using token
            } else {
                kmf.init(ks, passwd);
            }

            defaultKeyManagers = kmf.getKeyManagers();
            return defaultKeyManagers;
        }
    }

    /*
     * The SSLContext implementation for TLS, SSL, SSLv3 and TLS10 algorithm
     *
     * @see SSLContext
     */
    public static final class TLS10Context extends ConservativeSSLContext {
        // use the default constructor and methods
    }

    /*
     * The SSLContext implementation for TLS11 algorithm
     *
     * @see SSLContext
     */
    public static final class TLS11Context extends SSLContextImpl {
        // parameters
        private static SSLParameters defaultServerSSLParams;
        private static SSLParameters defaultClientSSLParams;
        private static SSLParameters supportedSSLParams;

        static {
            if (SunJSSE.isFIPS()) {
                supportedSSLParams = new SSLParameters();
                supportedSSLParams.setProtocols(new String[] {
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

                defaultServerSSLParams = supportedSSLParams;

                defaultClientSSLParams = new SSLParameters();
                defaultClientSSLParams.setProtocols(new String[] {
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name
                });

            } else {
                supportedSSLParams = new SSLParameters();
                supportedSSLParams.setProtocols(new String[] {
                    ProtocolVersion.SSL20Hello.name,
                    ProtocolVersion.SSL30.name,
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

                defaultServerSSLParams = supportedSSLParams;

                defaultClientSSLParams = new SSLParameters();
                defaultClientSSLParams.setProtocols(new String[] {
                    ProtocolVersion.SSL30.name,
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name
                });
            }
        }

        SSLParameters getDefaultServerSSLParams() {
            return defaultServerSSLParams;
        }

        SSLParameters getDefaultClientSSLParams() {
            return defaultClientSSLParams;
        }

        SSLParameters getSupportedSSLParams() {
            return supportedSSLParams;
        }
    }

    /*
     * The SSLContext implementation for TLS12 algorithm
     *
     * @see SSLContext
     */
    public static final class TLS12Context extends SSLContextImpl {
        // parameters
        private static SSLParameters defaultServerSSLParams;
        private static SSLParameters defaultClientSSLParams;
        private static SSLParameters supportedSSLParams;

        static {
            if (SunJSSE.isFIPS()) {
                supportedSSLParams = new SSLParameters();
                supportedSSLParams.setProtocols(new String[] {
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

                defaultServerSSLParams = supportedSSLParams;

                defaultClientSSLParams = new SSLParameters();
                defaultClientSSLParams.setProtocols(new String[] {
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

            } else {
                supportedSSLParams = new SSLParameters();
                supportedSSLParams.setProtocols(new String[] {
                    ProtocolVersion.SSL20Hello.name,
                    ProtocolVersion.SSL30.name,
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

                defaultServerSSLParams = supportedSSLParams;

                defaultClientSSLParams = new SSLParameters();
                defaultClientSSLParams.setProtocols(new String[] {
                    ProtocolVersion.SSL30.name,
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });
            }
        }

        SSLParameters getDefaultServerSSLParams() {
            return defaultServerSSLParams;
        }

        SSLParameters getDefaultClientSSLParams() {
            return defaultClientSSLParams;
        }

        SSLParameters getSupportedSSLParams() {
            return supportedSSLParams;
        }
    }

}


final class AbstractTrustManagerWrapper extends X509ExtendedTrustManager
            implements X509TrustManager {

    private final X509TrustManager tm;

    AbstractTrustManagerWrapper(X509TrustManager tm) {
        this.tm = tm;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
        tm.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
        tm.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return tm.getAcceptedIssuers();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType,
                Socket socket) throws CertificateException {
        tm.checkClientTrusted(chain, authType);
        checkAdditionalTrust(chain, authType, socket, true);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType,
            Socket socket) throws CertificateException {
        tm.checkServerTrusted(chain, authType);
        checkAdditionalTrust(chain, authType, socket, false);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType,
            SSLEngine engine) throws CertificateException {
        tm.checkClientTrusted(chain, authType);
        checkAdditionalTrust(chain, authType, engine, true);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType,
            SSLEngine engine) throws CertificateException {
        tm.checkServerTrusted(chain, authType);
        checkAdditionalTrust(chain, authType, engine, false);
    }

    private void checkAdditionalTrust(X509Certificate[] chain, String authType,
                Socket socket, boolean isClient) throws CertificateException {
        if (socket != null && socket.isConnected() &&
                                    socket instanceof SSLSocket) {

            SSLSocket sslSocket = (SSLSocket)socket;
            SSLSession session = sslSocket.getHandshakeSession();
            if (session == null) {
                throw new CertificateException("No handshake session");
            }

            // check endpoint identity
            String identityAlg = sslSocket.getSSLParameters().
                                        getEndpointIdentificationAlgorithm();
            if (identityAlg != null && identityAlg.length() != 0) {
                String hostname = session.getPeerHost();
                X509TrustManagerImpl.checkIdentity(
                                    hostname, chain[0], identityAlg);
            }

            // try the best to check the algorithm constraints
            ProtocolVersion protocolVersion =
                ProtocolVersion.valueOf(session.getProtocol());
            AlgorithmConstraints constraints = null;
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                if (session instanceof ExtendedSSLSession) {
                    ExtendedSSLSession extSession =
                                    (ExtendedSSLSession)session;
                    String[] peerSupportedSignAlgs =
                            extSession.getLocalSupportedSignatureAlgorithms();

                    constraints = new SSLAlgorithmConstraints(
                                    sslSocket, peerSupportedSignAlgs, true);
                } else {
                    constraints =
                            new SSLAlgorithmConstraints(sslSocket, true);
                }
            } else {
                constraints = new SSLAlgorithmConstraints(sslSocket, true);
            }

            AlgorithmChecker checker = new AlgorithmChecker(constraints);
            try {
                checker.init(false);

                // a forward checker, need to check from trust to target
                for (int i = chain.length - 1; i >= 0; i--) {
                    Certificate cert = chain[i];
                    // We don't care about the unresolved critical extensions.
                    checker.check(cert, Collections.<String>emptySet());
                }
            } catch (CertPathValidatorException cpve) {
                throw new CertificateException(
                    "Certificates does not conform to algorithm constraints");
            }
        }
    }

    private void checkAdditionalTrust(X509Certificate[] chain, String authType,
            SSLEngine engine, boolean isClient) throws CertificateException {
        if (engine != null) {
            SSLSession session = engine.getHandshakeSession();
            if (session == null) {
                throw new CertificateException("No handshake session");
            }

            // check endpoint identity
            String identityAlg = engine.getSSLParameters().
                                        getEndpointIdentificationAlgorithm();
            if (identityAlg != null && identityAlg.length() != 0) {
                String hostname = session.getPeerHost();
                X509TrustManagerImpl.checkIdentity(
                                    hostname, chain[0], identityAlg);
            }

            // try the best to check the algorithm constraints
            ProtocolVersion protocolVersion =
                ProtocolVersion.valueOf(session.getProtocol());
            AlgorithmConstraints constraints = null;
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                if (session instanceof ExtendedSSLSession) {
                    ExtendedSSLSession extSession =
                                    (ExtendedSSLSession)session;
                    String[] peerSupportedSignAlgs =
                            extSession.getLocalSupportedSignatureAlgorithms();

                    constraints = new SSLAlgorithmConstraints(
                                    engine, peerSupportedSignAlgs, true);
                } else {
                    constraints =
                            new SSLAlgorithmConstraints(engine, true);
                }
            } else {
                constraints = new SSLAlgorithmConstraints(engine, true);
            }

            AlgorithmChecker checker = new AlgorithmChecker(constraints);
            try {
                checker.init(false);

                // A forward checker, need to check from trust to target
                for (int i = chain.length - 1; i >= 0; i--) {
                    Certificate cert = chain[i];
                    // We don't care about the unresolved critical extensions.
                    checker.check(cert, Collections.<String>emptySet());
                }
            } catch (CertPathValidatorException cpve) {
                throw new CertificateException(
                    "Certificates does not conform to algorithm constraints");
            }
        }
    }
}

// Dummy X509TrustManager implementation, rejects all peer certificates.
// Used if the application did not specify a proper X509TrustManager.
final class DummyX509TrustManager extends X509ExtendedTrustManager
            implements X509TrustManager {

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
    @Override
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
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
        throw new CertificateException(
            "No X509TrustManager implementation available");
    }

    /*
     * Return an array of issuer certificates which are trusted
     * for authenticating peers.
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType,
                Socket socket) throws CertificateException {
        throw new CertificateException(
            "No X509TrustManager implementation available");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType,
            Socket socket) throws CertificateException {
        throw new CertificateException(
            "No X509TrustManager implementation available");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType,
            SSLEngine engine) throws CertificateException {
        throw new CertificateException(
            "No X509TrustManager implementation available");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType,
            SSLEngine engine) throws CertificateException {
        throw new CertificateException(
            "No X509TrustManager implementation available");
    }
}

/*
 * A wrapper class to turn a X509KeyManager into an X509ExtendedKeyManager
 */
final class AbstractKeyManagerWrapper extends X509ExtendedKeyManager {

    private final X509KeyManager km;

    AbstractKeyManagerWrapper(X509KeyManager km) {
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
