/*
 * Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.action.GetPropertyAction;

public abstract class SSLContextImpl extends SSLContextSpi {

    private static final Debug debug = Debug.getInstance("ssl");

    private final EphemeralKeyManager ephemeralKeyManager;
    private final SSLSessionContextImpl clientCache;
    private final SSLSessionContextImpl serverCache;

    private boolean isInitialized;

    private X509ExtendedKeyManager keyManager;
    private X509TrustManager trustManager;
    private SecureRandom secureRandom;

    // DTLS cookie exchange manager
    private volatile HelloCookieManager helloCookieManager;

    private final boolean clientEnableStapling = Debug.getBooleanProperty(
            "jdk.tls.client.enableStatusRequestExtension", true);
    private final boolean serverEnableStapling = Debug.getBooleanProperty(
            "jdk.tls.server.enableStatusRequestExtension", false);
    private volatile StatusResponseManager statusResponseManager;

    SSLContextImpl() {
        ephemeralKeyManager = new EphemeralKeyManager();
        clientCache = new SSLSessionContextImpl();
        serverCache = new SSLSessionContextImpl();
    }

    @Override
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
            if (!(km instanceof X509KeyManager)) {
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

    abstract SSLEngine createSSLEngineImpl();
    abstract SSLEngine createSSLEngineImpl(String host, int port);

    @Override
    protected SSLEngine engineCreateSSLEngine() {
        if (!isInitialized) {
            throw new IllegalStateException("SSLContext is not initialized");
        }
        return createSSLEngineImpl();
    }

    @Override
    protected SSLEngine engineCreateSSLEngine(String host, int port) {
        if (!isInitialized) {
            throw new IllegalStateException("SSLContext is not initialized");
        }
        return createSSLEngineImpl(host, port);
    }

    @Override
    protected SSLSocketFactory engineGetSocketFactory() {
        if (!isInitialized) {
            throw new IllegalStateException("SSLContext is not initialized");
        }
       return new SSLSocketFactoryImpl(this);
    }

    @Override
    protected SSLServerSocketFactory engineGetServerSocketFactory() {
        if (!isInitialized) {
            throw new IllegalStateException("SSLContext is not initialized");
        }
        return new SSLServerSocketFactoryImpl(this);
    }

    @Override
    protected SSLSessionContext engineGetClientSessionContext() {
        return clientCache;
    }

    @Override
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

    // Used for DTLS in server mode only, see ServerHandshaker.
    HelloCookieManager getHelloCookieManager() {
        if (!isInitialized) {
            throw new IllegalStateException("SSLContext is not initialized");
        }

        if (helloCookieManager != null) {
            return helloCookieManager;
        }

        synchronized (this) {
            if (helloCookieManager == null) {
                helloCookieManager = getHelloCookieManager(secureRandom);
            }
        }

        return helloCookieManager;
    }

    HelloCookieManager getHelloCookieManager(SecureRandom secureRandom) {
        throw new UnsupportedOperationException(
                "Cookie exchange applies to DTLS only");
    }

    StatusResponseManager getStatusResponseManager() {
        if (serverEnableStapling && statusResponseManager == null) {
            synchronized (this) {
                if (statusResponseManager == null) {
                    if (debug != null && Debug.isOn("sslctx")) {
                        System.out.println(
                                "Initializing StatusResponseManager");
                    }
                    statusResponseManager = new StatusResponseManager();
                }
            }
        }

        return statusResponseManager;
    }

    // Get supported ProtocolList.
    abstract ProtocolList getSuportedProtocolList();

    // Get default ProtocolList for server mode.
    abstract ProtocolList getServerDefaultProtocolList();

    // Get default ProtocolList for client mode.
    abstract ProtocolList getClientDefaultProtocolList();

    // Get supported CipherSuiteList.
    abstract CipherSuiteList getSupportedCipherSuiteList();

    // Get default CipherSuiteList for server mode.
    abstract CipherSuiteList getServerDefaultCipherSuiteList();

    // Get default CipherSuiteList for client mode.
    abstract CipherSuiteList getClientDefaultCipherSuiteList();

    // Get default ProtocolList.
    ProtocolList getDefaultProtocolList(boolean roleIsServer) {
        return roleIsServer ? getServerDefaultProtocolList()
                            : getClientDefaultProtocolList();
    }

    // Get default CipherSuiteList.
    CipherSuiteList getDefaultCipherSuiteList(boolean roleIsServer) {
        return roleIsServer ? getServerDefaultCipherSuiteList()
                            : getClientDefaultCipherSuiteList();
    }

    /**
     * Return whether a protocol list is the original default enabled
     * protocols.  See: SSLSocket/SSLEngine.setEnabledProtocols()
     */
    boolean isDefaultProtocolList(ProtocolList protocols) {
        return (protocols == getServerDefaultProtocolList()) ||
               (protocols == getClientDefaultProtocolList());
    }

    /**
     * Return whether a protocol list is the original default enabled
     * protocols.  See: SSLSocket/SSLEngine.setEnabledProtocols()
     */
    boolean isDefaultCipherSuiteList(CipherSuiteList cipherSuites) {
        return (cipherSuites == getServerDefaultCipherSuiteList()) ||
               (cipherSuites == getClientDefaultCipherSuiteList());
    }

    /**
     * Return whether client or server side stapling has been enabled
     * for this SSLContextImpl
     * @param isClient true if the caller is operating in a client side role,
     * false if acting as a server.
     * @return true if stapling has been enabled for the specified role, false
     * otherwise.
     */
    boolean isStaplingEnabled(boolean isClient) {
        return isClient ? clientEnableStapling : serverEnableStapling;
    }

    /*
     * Return the list of all available CipherSuites with a priority of
     * minPriority or above.
     */
    private static CipherSuiteList getApplicableCipherSuiteList(
            ProtocolList protocols, boolean onlyEnabled) {

        int minPriority = CipherSuite.SUPPORTED_SUITES_PRIORITY;
        if (onlyEnabled) {
            minPriority = CipherSuite.DEFAULT_SUITES_PRIORITY;
        }

        Collection<CipherSuite> allowedCipherSuites =
                                    CipherSuite.allowedCipherSuites();

        TreeSet<CipherSuite> suites = new TreeSet<>();
        if (!(protocols.collection().isEmpty()) &&
                protocols.min.v != ProtocolVersion.NONE.v) {
            for (CipherSuite suite : allowedCipherSuites) {
                if (!suite.allowed || suite.priority < minPriority) {
                    continue;
                }

                if (suite.isAvailable() &&
                        !protocols.min.obsoletes(suite) &&
                        protocols.max.supports(suite)) {
                    if (SSLAlgorithmConstraints.DEFAULT.permits(
                            EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                            suite.name, null)) {
                        suites.add(suite);
                    }
                } else if (debug != null &&
                        Debug.isOn("sslctx") && Debug.isOn("verbose")) {
                    if (protocols.min.obsoletes(suite)) {
                        System.out.println(
                            "Ignoring obsoleted cipher suite: " + suite);
                    } else if (!protocols.max.supports(suite)) {
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

    private static String[] getAvailableProtocols(
            ProtocolVersion[] protocolCandidates) {

        List<String> availableProtocols = Collections.<String>emptyList();
        if (protocolCandidates !=  null && protocolCandidates.length != 0) {
            availableProtocols = new ArrayList<>(protocolCandidates.length);
            for (ProtocolVersion p : protocolCandidates) {
                if (ProtocolVersion.availableProtocols.contains(p)) {
                    availableProtocols.add(p.name);
                }
            }
        }

        return availableProtocols.toArray(new String[0]);
    }


    /*
     * The SSLContext implementation for SSL/(D)TLS algorithm
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
     * The base abstract SSLContext implementation for the Transport Layer
     * Security (TLS) protocols.
     *
     * This abstract class encapsulates supported and the default server
     * SSL/TLS parameters.
     *
     * @see SSLContext
     */
    private abstract static class AbstractTLSContext extends SSLContextImpl {
        private static final ProtocolList supportedProtocolList;
        private static final ProtocolList serverDefaultProtocolList;

        private static final CipherSuiteList supportedCipherSuiteList;
        private static final CipherSuiteList serverDefaultCipherSuiteList;

        static {
            if (SunJSSE.isFIPS()) {
                supportedProtocolList = new ProtocolList(new String[] {
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

                serverDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(new ProtocolVersion[] {
                    ProtocolVersion.TLS10,
                    ProtocolVersion.TLS11,
                    ProtocolVersion.TLS12
                }));
            } else {
                supportedProtocolList = new ProtocolList(new String[] {
                    ProtocolVersion.SSL20Hello.name,
                    ProtocolVersion.SSL30.name,
                    ProtocolVersion.TLS10.name,
                    ProtocolVersion.TLS11.name,
                    ProtocolVersion.TLS12.name
                });

                serverDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(new ProtocolVersion[] {
                    ProtocolVersion.SSL20Hello,
                    ProtocolVersion.SSL30,
                    ProtocolVersion.TLS10,
                    ProtocolVersion.TLS11,
                    ProtocolVersion.TLS12
                }));
            }

            supportedCipherSuiteList = getApplicableCipherSuiteList(
                    supportedProtocolList, false);          // all supported
            serverDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    serverDefaultProtocolList, true);       // enabled only
        }

        @Override
        ProtocolList getSuportedProtocolList() {
            return supportedProtocolList;
        }

        @Override
        CipherSuiteList getSupportedCipherSuiteList() {
            return supportedCipherSuiteList;
        }

        @Override
        ProtocolList getServerDefaultProtocolList() {
            return serverDefaultProtocolList;
        }

        @Override
        CipherSuiteList getServerDefaultCipherSuiteList() {
            return serverDefaultCipherSuiteList;
        }

        @Override
        SSLEngine createSSLEngineImpl() {
            return new SSLEngineImpl(this, false);
        }

        @Override
        SSLEngine createSSLEngineImpl(String host, int port) {
            return new SSLEngineImpl(this, host, port, false);
        }
    }

    /*
     * The SSLContext implementation for SSLv3 and TLS10 algorithm
     *
     * @see SSLContext
     */
    public static final class TLS10Context extends AbstractTLSContext {
        private static final ProtocolList clientDefaultProtocolList;
        private static final CipherSuiteList clientDefaultCipherSuiteList;

        static {
            if (SunJSSE.isFIPS()) {
                clientDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(new ProtocolVersion[] {
                    ProtocolVersion.TLS10
                }));
            } else {
                clientDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(new ProtocolVersion[] {
                    ProtocolVersion.SSL30,
                    ProtocolVersion.TLS10
                }));
            }

            clientDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    clientDefaultProtocolList, true);       // enabled only
        }

        @Override
        ProtocolList getClientDefaultProtocolList() {
            return clientDefaultProtocolList;
        }

        @Override
        CipherSuiteList getClientDefaultCipherSuiteList() {
            return clientDefaultCipherSuiteList;
        }
    }

    /*
     * The SSLContext implementation for TLS11 algorithm
     *
     * @see SSLContext
     */
    public static final class TLS11Context extends AbstractTLSContext {
        private static final ProtocolList clientDefaultProtocolList;
        private static final CipherSuiteList clientDefaultCipherSuiteList;

        static {
            if (SunJSSE.isFIPS()) {
                clientDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(new ProtocolVersion[] {
                    ProtocolVersion.TLS10,
                    ProtocolVersion.TLS11
                }));
            } else {
                clientDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(new ProtocolVersion[] {
                    ProtocolVersion.SSL30,
                    ProtocolVersion.TLS10,
                    ProtocolVersion.TLS11
                }));
            }

            clientDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    clientDefaultProtocolList, true);       // enabled only
        }

        @Override
        ProtocolList getClientDefaultProtocolList() {
            return clientDefaultProtocolList;
        }

        @Override
        CipherSuiteList getClientDefaultCipherSuiteList() {
            return clientDefaultCipherSuiteList;
        }
    }

    /*
     * The SSLContext implementation for TLS12 algorithm
     *
     * @see SSLContext
     */
    public static final class TLS12Context extends AbstractTLSContext {
        private static final ProtocolList clientDefaultProtocolList;
        private static final CipherSuiteList clientDefaultCipherSuiteList;

        static {
            if (SunJSSE.isFIPS()) {
                clientDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(new ProtocolVersion[] {
                    ProtocolVersion.TLS10,
                    ProtocolVersion.TLS11,
                    ProtocolVersion.TLS12
                }));
            } else {
                clientDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(new ProtocolVersion[] {
                    ProtocolVersion.SSL30,
                    ProtocolVersion.TLS10,
                    ProtocolVersion.TLS11,
                    ProtocolVersion.TLS12
                }));
            }

            clientDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    clientDefaultProtocolList, true);       // enabled only
        }

        @Override
        ProtocolList getClientDefaultProtocolList() {
            return clientDefaultProtocolList;
        }

        @Override
        CipherSuiteList getClientDefaultCipherSuiteList() {
            return clientDefaultCipherSuiteList;
        }
    }

    /*
     * The interface for the customized SSL/(D)TLS SSLContext.
     *
     * @see SSLContext
     */
    private static class CustomizedSSLProtocols {
        private static final String PROPERTY_NAME = "jdk.tls.client.protocols";
        static IllegalArgumentException reservedException = null;
        static ArrayList<ProtocolVersion>
                                customizedProtocols = new ArrayList<>();

        // Don't want a java.lang.LinkageError for illegal system property.
        //
        // Please don't throw exception in this static block.  Otherwise,
        // java.lang.LinkageError may be thrown during the instantiation of
        // the provider service. Instead, please handle the initialization
        // exception in the caller's constructor.
        static {
            String property = GetPropertyAction.getProperty(PROPERTY_NAME);
            if (property != null && property.length() != 0) {
                // remove double quote marks from beginning/end of the property
                if (property.length() > 1 && property.charAt(0) == '"' &&
                        property.charAt(property.length() - 1) == '"') {
                    property = property.substring(1, property.length() - 1);
                }
            }

            if (property != null && property.length() != 0) {
                String[] protocols = property.split(",");
                for (int i = 0; i < protocols.length; i++) {
                    protocols[i] = protocols[i].trim();
                    // Is it a supported protocol name?
                    try {
                        ProtocolVersion pro =
                                ProtocolVersion.valueOf(protocols[i]);

                        if (SunJSSE.isFIPS() &&
                                ((pro.v == ProtocolVersion.SSL30.v) ||
                                 (pro.v == ProtocolVersion.SSL20Hello.v))) {
                            reservedException = new IllegalArgumentException(
                                    PROPERTY_NAME + ": " + pro +
                                    " is not FIPS compliant");

                            break;
                        }

                        // ignore duplicated protocols
                        if (!customizedProtocols.contains(pro)) {
                            customizedProtocols.add(pro);
                        }
                    } catch (IllegalArgumentException iae) {
                        reservedException = new IllegalArgumentException(
                                PROPERTY_NAME + ": " + protocols[i] +
                                " is not a standard SSL protocol name", iae);
                    }
                }
            }
        }
    }

    /*
     * The SSLContext implementation for customized TLS protocols
     *
     * @see SSLContext
     */
    private static class CustomizedTLSContext extends AbstractTLSContext {

        private static final ProtocolList clientDefaultProtocolList;
        private static final CipherSuiteList clientDefaultCipherSuiteList;

        private static IllegalArgumentException reservedException = null;

        // Don't want a java.lang.LinkageError for illegal system property.
        //
        // Please don't throw exception in this static block.  Otherwise,
        // java.lang.LinkageError may be thrown during the instantiation of
        // the provider service. Instead, let's handle the initialization
        // exception in constructor.
        static {
            reservedException = CustomizedSSLProtocols.reservedException;
            if (reservedException == null) {
                ArrayList<ProtocolVersion>
                        customizedTLSProtocols = new ArrayList<>();
                for (ProtocolVersion protocol :
                        CustomizedSSLProtocols.customizedProtocols) {
                    if (!protocol.isDTLSProtocol()) {
                        customizedTLSProtocols.add(protocol);
                    }
                }

                // candidates for available protocols
                ProtocolVersion[] candidates;
                if (customizedTLSProtocols.isEmpty()) {
                    // Use the default enabled client protocols if no
                    // customized TLS protocols.
                    if (SunJSSE.isFIPS()) {
                        candidates = new ProtocolVersion[] {
                            ProtocolVersion.TLS10,
                            ProtocolVersion.TLS11,
                            ProtocolVersion.TLS12
                        };
                    } else {
                        candidates = new ProtocolVersion[] {
                            ProtocolVersion.SSL30,
                            ProtocolVersion.TLS10,
                            ProtocolVersion.TLS11,
                            ProtocolVersion.TLS12
                        };
                    }
                } else {
                    // Use the customized TLS protocols.
                    candidates =
                            new ProtocolVersion[customizedTLSProtocols.size()];
                    candidates = customizedTLSProtocols.toArray(candidates);
                }

                clientDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(candidates));
                clientDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    clientDefaultProtocolList, true);   // enabled only
            } else {
                clientDefaultProtocolList = null;       // unlikely to be used
                clientDefaultCipherSuiteList = null;    // unlikely to be used
            }
        }

        protected CustomizedTLSContext() {
            if (reservedException != null) {
                throw reservedException;
            }
        }

        @Override
        ProtocolList getClientDefaultProtocolList() {
            return clientDefaultProtocolList;
        }

        @Override
        CipherSuiteList getClientDefaultCipherSuiteList() {
            return clientDefaultCipherSuiteList;
        }
    }

    /*
     * The SSLContext implementation for default "TLS" algorithm
     *
     * @see SSLContext
     */
    public static final class TLSContext extends CustomizedTLSContext {
        // use the default constructor and methods
    }

    // lazy initialization holder class idiom for static default parameters
    //
    // See Effective Java Second Edition: Item 71.
    private static final class DefaultManagersHolder {
        private static final String NONE = "NONE";
        private static final String P11KEYSTORE = "PKCS11";

        private static final TrustManager[] trustManagers;
        private static final KeyManager[] keyManagers;

        static Exception reservedException = null;

        static {
            TrustManager[] tmMediator;
            try {
                tmMediator = getTrustManagers();
            } catch (Exception e) {
                reservedException = e;
                tmMediator = new TrustManager[0];
            }
            trustManagers = tmMediator;

            if (reservedException == null) {
                KeyManager[] kmMediator;
                try {
                    kmMediator = getKeyManagers();
                } catch (Exception e) {
                    reservedException = e;
                    kmMediator = new KeyManager[0];
                }
                keyManagers = kmMediator;
            } else {
                keyManagers = new KeyManager[0];
            }
        }

        private static TrustManager[] getTrustManagers() throws Exception {
            KeyStore ks =
                TrustManagerFactoryImpl.getCacertsKeyStore("defaultctx");

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            return tmf.getTrustManagers();
        }

        private static KeyManager[] getKeyManagers() throws Exception {

            final Map<String,String> props = new HashMap<>();
            AccessController.doPrivileged(
                        new PrivilegedExceptionAction<Object>() {
                @Override
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
            KeyStore ks = null;
            char[] passwd = null;
            try {
                if (defaultKeyStore.length() != 0 &&
                        !NONE.equals(defaultKeyStore)) {
                    fs = AccessController.doPrivileged(
                            new PrivilegedExceptionAction<FileInputStream>() {
                        @Override
                        public FileInputStream run() throws Exception {
                            return new FileInputStream(defaultKeyStore);
                        }
                    });
                }

                String defaultKeyStorePassword = props.get("keyStorePasswd");
                if (defaultKeyStorePassword.length() != 0) {
                    passwd = defaultKeyStorePassword.toCharArray();
                }

                /**
                 * Try to initialize key store.
                 */
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
            } finally {
                if (fs != null) {
                    fs.close();
                    fs = null;
                }
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

            return kmf.getKeyManagers();
        }
    }

    // lazy initialization holder class idiom for static default parameters
    //
    // See Effective Java Second Edition: Item 71.
    private static final class DefaultSSLContextHolder {

        private static final SSLContextImpl sslContext;
        static Exception reservedException = null;

        static {
            SSLContextImpl mediator = null;
            if (DefaultManagersHolder.reservedException != null) {
                reservedException = DefaultManagersHolder.reservedException;
            } else {
                try {
                    mediator = new DefaultSSLContext();
                } catch (Exception e) {
                    reservedException = e;
                }
            }

            sslContext = mediator;
        }
    }

    /*
     * The SSLContext implementation for default "Default" algorithm
     *
     * @see SSLContext
     */
    public static final class DefaultSSLContext extends CustomizedTLSContext {

        // public constructor for SSLContext.getInstance("Default")
        public DefaultSSLContext() throws Exception {
            if (DefaultManagersHolder.reservedException != null) {
                throw DefaultManagersHolder.reservedException;
            }

            try {
                super.engineInit(DefaultManagersHolder.keyManagers,
                        DefaultManagersHolder.trustManagers, null);
            } catch (Exception e) {
                if (debug != null && Debug.isOn("defaultctx")) {
                    System.out.println("default context init failed: " + e);
                }
                throw e;
            }
        }

        @Override
        protected void engineInit(KeyManager[] km, TrustManager[] tm,
            SecureRandom sr) throws KeyManagementException {
            throw new KeyManagementException
                ("Default SSLContext is initialized automatically");
        }

        static SSLContextImpl getDefaultImpl() throws Exception {
            if (DefaultSSLContextHolder.reservedException != null) {
                throw DefaultSSLContextHolder.reservedException;
            }

            return DefaultSSLContextHolder.sslContext;
        }
    }

    /*
     * The base abstract SSLContext implementation for the Datagram Transport
     * Layer Security (DTLS) protocols.
     *
     * This abstract class encapsulates supported and the default server DTLS
     * parameters.
     *
     * @see SSLContext
     */
    private abstract static class AbstractDTLSContext extends SSLContextImpl {
        private static final ProtocolList supportedProtocolList;
        private static final ProtocolList serverDefaultProtocolList;

        private static final CipherSuiteList supportedCipherSuiteList;
        private static final CipherSuiteList serverDefaultCipherSuiteList;

        static {
            // Both DTLSv1.0 and DTLSv1.2 can be used in FIPS mode.
            supportedProtocolList = new ProtocolList(new String[] {
                ProtocolVersion.DTLS10.name,
                ProtocolVersion.DTLS12.name
            });

            // available protocols for server mode
            serverDefaultProtocolList = new ProtocolList(
                    getAvailableProtocols(new ProtocolVersion[] {
                ProtocolVersion.DTLS10,
                ProtocolVersion.DTLS12
            }));

            supportedCipherSuiteList = getApplicableCipherSuiteList(
                    supportedProtocolList, false);          // all supported
            serverDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    serverDefaultProtocolList, true);       // enabled only
        }

        @Override
        ProtocolList getSuportedProtocolList() {
            return supportedProtocolList;
        }

        @Override
        CipherSuiteList getSupportedCipherSuiteList() {
            return supportedCipherSuiteList;
        }

        @Override
        ProtocolList getServerDefaultProtocolList() {
            return serverDefaultProtocolList;
        }

        @Override
        CipherSuiteList getServerDefaultCipherSuiteList() {
            return serverDefaultCipherSuiteList;
        }

        @Override
        SSLEngine createSSLEngineImpl() {
            return new SSLEngineImpl(this, true);
        }

        @Override
        SSLEngine createSSLEngineImpl(String host, int port) {
            return new SSLEngineImpl(this, host, port, true);
        }

        @Override
        HelloCookieManager getHelloCookieManager(SecureRandom secureRandom) {
            return new HelloCookieManager(secureRandom);
        }
    }

    /*
     * The SSLContext implementation for DTLSv1.0 algorithm.
     *
     * @see SSLContext
     */
    public static final class DTLS10Context extends AbstractDTLSContext {
        private static final ProtocolList clientDefaultProtocolList;
        private static final CipherSuiteList clientDefaultCipherSuiteList;

        static {
            // available protocols for client mode
            clientDefaultProtocolList = new ProtocolList(
                    getAvailableProtocols(new ProtocolVersion[] {
                ProtocolVersion.DTLS10
            }));

            clientDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    clientDefaultProtocolList, true);       // enabled only
        }

        @Override
        ProtocolList getClientDefaultProtocolList() {
            return clientDefaultProtocolList;
        }

        @Override
        CipherSuiteList getClientDefaultCipherSuiteList() {
            return clientDefaultCipherSuiteList;
        }
    }

    /*
     * The SSLContext implementation for DTLSv1.2 algorithm.
     *
     * @see SSLContext
     */
    public static final class DTLS12Context extends AbstractDTLSContext {
        private static final ProtocolList clientDefaultProtocolList;
        private static final CipherSuiteList clientDefaultCipherSuiteList;

        static {
            // available protocols for client mode
            clientDefaultProtocolList = new ProtocolList(
                    getAvailableProtocols(new ProtocolVersion[] {
                ProtocolVersion.DTLS10,
                ProtocolVersion.DTLS12
            }));

            clientDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    clientDefaultProtocolList, true);       // enabled only
        }

        @Override
        ProtocolList getClientDefaultProtocolList() {
            return clientDefaultProtocolList;
        }

        @Override
        CipherSuiteList getClientDefaultCipherSuiteList() {
            return clientDefaultCipherSuiteList;
        }
    }

    /*
     * The SSLContext implementation for customized TLS protocols
     *
     * @see SSLContext
     */
    private static class CustomizedDTLSContext extends AbstractDTLSContext {
        private static final ProtocolList clientDefaultProtocolList;
        private static final CipherSuiteList clientDefaultCipherSuiteList;

        private static IllegalArgumentException reservedException = null;

        // Don't want a java.lang.LinkageError for illegal system property.
        //
        // Please don't throw exception in this static block.  Otherwise,
        // java.lang.LinkageError may be thrown during the instantiation of
        // the provider service. Instead, let's handle the initialization
        // exception in constructor.
        static {
            reservedException = CustomizedSSLProtocols.reservedException;
            if (reservedException == null) {
                ArrayList<ProtocolVersion>
                        customizedDTLSProtocols = new ArrayList<>();
                for (ProtocolVersion protocol :
                        CustomizedSSLProtocols.customizedProtocols) {
                    if (protocol.isDTLSProtocol()) {
                        customizedDTLSProtocols.add(protocol);
                    }
                }

                // candidates for available protocols
                ProtocolVersion[] candidates;
                if (customizedDTLSProtocols.isEmpty()) {
                    // Use the default enabled client protocols if no
                    // customized TLS protocols.
                    //
                    // Both DTLSv1.0 and DTLSv1.2 can be used in FIPS mode.
                    candidates = new ProtocolVersion[] {
                        ProtocolVersion.DTLS10,
                        ProtocolVersion.DTLS12
                    };

                } else {
                    // Use the customized TLS protocols.
                    candidates =
                            new ProtocolVersion[customizedDTLSProtocols.size()];
                    candidates = customizedDTLSProtocols.toArray(candidates);
                }

                clientDefaultProtocolList = new ProtocolList(
                        getAvailableProtocols(candidates));
                clientDefaultCipherSuiteList = getApplicableCipherSuiteList(
                    clientDefaultProtocolList, true);   // enabled only
            } else {
                clientDefaultProtocolList = null;       // unlikely to be used
                clientDefaultCipherSuiteList = null;    // unlikely to be used
            }
        }

        protected CustomizedDTLSContext() {
            if (reservedException != null) {
                throw reservedException;
            }
        }

        @Override
        ProtocolList getClientDefaultProtocolList() {
            return clientDefaultProtocolList;
        }

        @Override
        CipherSuiteList getClientDefaultCipherSuiteList() {
            return clientDefaultCipherSuiteList;
        }
    }

    /*
     * The SSLContext implementation for default "DTLS" algorithm
     *
     * @see SSLContext
     */
    public static final class DTLSContext extends CustomizedDTLSContext {
        // use the default constructor and methods
    }

}


final class AbstractTrustManagerWrapper extends X509ExtendedTrustManager
            implements X509TrustManager {

    // the delegated trust manager
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
            if (protocolVersion.useTLS12PlusSpec()) {
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

            checkAlgorithmConstraints(chain, constraints);
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
            if (protocolVersion.useTLS12PlusSpec()) {
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

            checkAlgorithmConstraints(chain, constraints);
        }
    }

    private void checkAlgorithmConstraints(X509Certificate[] chain,
            AlgorithmConstraints constraints) throws CertificateException {

        try {
            // Does the certificate chain end with a trusted certificate?
            int checkedLength = chain.length - 1;

            Collection<X509Certificate> trustedCerts = new HashSet<>();
            X509Certificate[] certs = tm.getAcceptedIssuers();
            if ((certs != null) && (certs.length > 0)){
                Collections.addAll(trustedCerts, certs);
            }

            if (trustedCerts.contains(chain[checkedLength])) {
                    checkedLength--;
            }

            // A forward checker, need to check from trust to target
            if (checkedLength >= 0) {
                AlgorithmChecker checker = new AlgorithmChecker(constraints);
                checker.init(false);
                for (int i = checkedLength; i >= 0; i--) {
                    Certificate cert = chain[i];
                    // We don't care about the unresolved critical extensions.
                    checker.check(cert, Collections.<String>emptySet());
                }
            }
        } catch (CertPathValidatorException cpve) {
            throw new CertificateException(
                "Certificates does not conform to algorithm constraints");
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

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return km.getClientAliases(keyType, issuers);
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket) {
        return km.chooseClientAlias(keyType, issuers, socket);
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return km.getServerAliases(keyType, issuers);
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        return km.chooseServerAlias(keyType, issuers, socket);
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        return km.getCertificateChain(alias);
    }

    @Override
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
    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return null;
    }

    /*
     * Choose an alias to authenticate the client side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {
        return null;
    }

    /*
     * Choose an alias to authenticate the client side of an
     * engine given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String chooseEngineClientAlias(
            String[] keyTypes, Principal[] issuers, SSLEngine engine) {
        return null;
    }

    /*
     * Get the matching aliases for authenticating the server side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return null;
    }

    /*
     * Choose an alias to authenticate the server side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        return null;
    }

    /*
     * Choose an alias to authenticate the server side of an engine
     * given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
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
    @Override
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
    @Override
    public PrivateKey getPrivateKey(String alias) {
        return null;
    }
}
