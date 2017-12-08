/*
 * Copyright (c) 1996, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import java.security.*;
import java.nio.ByteBuffer;
import java.util.function.BiFunction;

import javax.crypto.*;
import javax.crypto.spec.*;

import javax.net.ssl.*;
import sun.security.util.HexDumpEncoder;

import sun.security.internal.spec.*;
import sun.security.internal.interfaces.TlsMasterSecret;

import sun.security.ssl.HandshakeMessage.*;
import sun.security.ssl.CipherSuite.*;

import static sun.security.ssl.CipherSuite.PRF.*;
import static sun.security.ssl.CipherSuite.CipherType.*;
import static sun.security.ssl.NamedGroupType.*;

/**
 * Handshaker ... processes handshake records from an SSL V3.0
 * data stream, handling all the details of the handshake protocol.
 *
 * Note that the real protocol work is done in two subclasses, the  base
 * class just provides the control flow and key generation framework.
 *
 * @author David Brownell
 */
abstract class Handshaker {

    // protocol version being established using this Handshaker
    ProtocolVersion protocolVersion;

    // the currently active protocol version during a renegotiation
    ProtocolVersion     activeProtocolVersion;

    // security parameters for secure renegotiation.
    boolean             secureRenegotiation;
    byte[]              clientVerifyData;
    byte[]              serverVerifyData;

    // Is it an initial negotiation  or a renegotiation?
    boolean                     isInitialHandshake;

    // List of enabled protocols
    private ProtocolList        enabledProtocols;

    // List of enabled CipherSuites
    private CipherSuiteList     enabledCipherSuites;

    // The endpoint identification protocol
    String                      identificationProtocol;

    // The cryptographic algorithm constraints
    AlgorithmConstraints        algorithmConstraints = null;

    // Local supported signature and algorithms
    private Collection<SignatureAndHashAlgorithm> localSupportedSignAlgs;

    // Peer supported signature and algorithms
    Collection<SignatureAndHashAlgorithm> peerSupportedSignAlgs;

    /*
     * List of active protocols
     *
     * Active protocols is a subset of enabled protocols, and will
     * contain only those protocols that have vaild cipher suites
     * enabled.
     */
    private ProtocolList       activeProtocols;

    /*
     * List of active cipher suites
     *
     * Active cipher suites is a subset of enabled cipher suites, and will
     * contain only those cipher suites available for the active protocols.
     */
    private CipherSuiteList     activeCipherSuites;

    // The server name indication and matchers
    List<SNIServerName> serverNames = Collections.<SNIServerName>emptyList();
    Collection<SNIMatcher> sniMatchers = Collections.<SNIMatcher>emptyList();

    // List of local ApplicationProtocols
    String[] localApl = null;

    // Negotiated ALPN value
    String applicationProtocol = null;

    // Application protocol callback function (for SSLEngine)
    BiFunction<SSLEngine,List<String>,String>
        appProtocolSelectorSSLEngine = null;

    // Application protocol callback function (for SSLSocket)
    BiFunction<SSLSocket,List<String>,String>
        appProtocolSelectorSSLSocket = null;

    // The maximum expected network packet size for SSL/TLS/DTLS records.
    int                         maximumPacketSize = 0;

    private boolean             isClient;
    private boolean             needCertVerify;

    SSLSocketImpl               conn = null;
    SSLEngineImpl               engine = null;

    HandshakeHash               handshakeHash;
    HandshakeInStream           input;
    HandshakeOutStream          output;
    SSLContextImpl              sslContext;
    RandomCookie                clnt_random, svr_random;
    SSLSessionImpl              session;

    HandshakeStateManager       handshakeState;
    boolean                     clientHelloDelivered;
    boolean                     serverHelloRequested;
    boolean                     handshakeActivated;
    boolean                     handshakeFinished;

    // current CipherSuite. Never null, initially SSL_NULL_WITH_NULL_NULL
    CipherSuite         cipherSuite;

    // current key exchange. Never null, initially K_NULL
    KeyExchange         keyExchange;

    // True if this session is being resumed (fast handshake)
    boolean             resumingSession;

    // True if it's OK to start a new SSL session
    boolean             enableNewSession;

    // Whether local cipher suites preference should be honored during
    // handshaking?
    //
    // Note that in this provider, this option only applies to server side.
    // Local cipher suites preference is always honored in client side in
    // this provider.
    boolean preferLocalCipherSuites = false;

    // Temporary storage for the individual keys. Set by
    // calculateConnectionKeys() and cleared once the ciphers are
    // activated.
    private SecretKey clntWriteKey, svrWriteKey;
    private IvParameterSpec clntWriteIV, svrWriteIV;
    private SecretKey clntMacSecret, svrMacSecret;

    /*
     * Delegated task subsystem data structures.
     *
     * If thrown is set, we need to propagate this back immediately
     * on entry into processMessage().
     *
     * Data is protected by the SSLEngine.this lock.
     */
    private volatile boolean taskDelegated = false;
    private volatile DelegatedTask<?> delegatedTask = null;
    private volatile Exception thrown = null;

    // Could probably use a java.util.concurrent.atomic.AtomicReference
    // here instead of using this lock.  Consider changing.
    private Object thrownLock = new Object();

    /* Class and subclass dynamic debugging support */
    static final Debug debug = Debug.getInstance("ssl");

    // By default, disable the unsafe legacy session renegotiation
    static final boolean allowUnsafeRenegotiation = Debug.getBooleanProperty(
                    "sun.security.ssl.allowUnsafeRenegotiation", false);

    // For maximum interoperability and backward compatibility, RFC 5746
    // allows server (or client) to accept ClientHello (or ServerHello)
    // message without the secure renegotiation_info extension or SCSV.
    //
    // For maximum security, RFC 5746 also allows server (or client) to
    // reject such message with a fatal "handshake_failure" alert.
    //
    // By default, allow such legacy hello messages.
    static final boolean allowLegacyHelloMessages = Debug.getBooleanProperty(
                    "sun.security.ssl.allowLegacyHelloMessages", true);

    // To prevent the TLS renegotiation issues, by setting system property
    // "jdk.tls.rejectClientInitiatedRenegotiation" to true, applications in
    // server side can disable all client initiated SSL renegotiations
    // regardless of the support of TLS protocols.
    //
    // By default, allow client initiated renegotiations.
    static final boolean rejectClientInitiatedRenego =
            Debug.getBooleanProperty(
                "jdk.tls.rejectClientInitiatedRenegotiation", false);

    // To switch off the extended_master_secret extension.
    static final boolean useExtendedMasterSecret;

    // Allow session resumption without Extended Master Secret extension.
    static final boolean allowLegacyResumption =
            Debug.getBooleanProperty("jdk.tls.allowLegacyResumption", true);

    // Allow full handshake without Extended Master Secret extension.
    static final boolean allowLegacyMasterSecret =
            Debug.getBooleanProperty("jdk.tls.allowLegacyMasterSecret", true);

    // Is it requested to use extended master secret extension?
    boolean requestedToUseEMS = false;

    // need to dispose the object when it is invalidated
    boolean invalidated;

    /*
     * Is this an instance for Datagram Transport Layer Security (DTLS)?
     */
    final boolean isDTLS;

    // Is the extended_master_secret extension supported?
    static {
        boolean supportExtendedMasterSecret = true;
        try {
            KeyGenerator kg =
                JsseJce.getKeyGenerator("SunTlsExtendedMasterSecret");
        } catch (NoSuchAlgorithmException nae) {
            supportExtendedMasterSecret = false;
        }

        if (supportExtendedMasterSecret) {
            useExtendedMasterSecret = Debug.getBooleanProperty(
                    "jdk.tls.useExtendedMasterSecret", true);
        } else {
            useExtendedMasterSecret = false;
        }
    }

    Handshaker(SSLSocketImpl c, SSLContextImpl context,
            ProtocolList enabledProtocols, boolean needCertVerify,
            boolean isClient, ProtocolVersion activeProtocolVersion,
            boolean isInitialHandshake, boolean secureRenegotiation,
            byte[] clientVerifyData, byte[] serverVerifyData) {
        this.conn = c;
        this.isDTLS = false;
        init(context, enabledProtocols, needCertVerify, isClient,
            activeProtocolVersion, isInitialHandshake, secureRenegotiation,
            clientVerifyData, serverVerifyData);
   }

    Handshaker(SSLEngineImpl engine, SSLContextImpl context,
            ProtocolList enabledProtocols, boolean needCertVerify,
            boolean isClient, ProtocolVersion activeProtocolVersion,
            boolean isInitialHandshake, boolean secureRenegotiation,
            byte[] clientVerifyData, byte[] serverVerifyData,
            boolean isDTLS) {
        this.engine = engine;
        this.isDTLS = isDTLS;
        init(context, enabledProtocols, needCertVerify, isClient,
            activeProtocolVersion, isInitialHandshake, secureRenegotiation,
            clientVerifyData, serverVerifyData);
    }

    private void init(SSLContextImpl context, ProtocolList enabledProtocols,
            boolean needCertVerify, boolean isClient,
            ProtocolVersion activeProtocolVersion,
            boolean isInitialHandshake, boolean secureRenegotiation,
            byte[] clientVerifyData, byte[] serverVerifyData) {

        if (debug != null && Debug.isOn("handshake")) {
            System.out.println(
                "Allow unsafe renegotiation: " + allowUnsafeRenegotiation +
                "\nAllow legacy hello messages: " + allowLegacyHelloMessages +
                "\nIs initial handshake: " + isInitialHandshake +
                "\nIs secure renegotiation: " + secureRenegotiation);
        }

        this.sslContext = context;
        this.isClient = isClient;
        this.needCertVerify = needCertVerify;
        this.activeProtocolVersion = activeProtocolVersion;
        this.isInitialHandshake = isInitialHandshake;
        this.secureRenegotiation = secureRenegotiation;
        this.clientVerifyData = clientVerifyData;
        this.serverVerifyData = serverVerifyData;
        this.enableNewSession = true;
        this.invalidated = false;
        this.handshakeState = new HandshakeStateManager(isDTLS);
        this.clientHelloDelivered = false;
        this.serverHelloRequested = false;
        this.handshakeActivated = false;
        this.handshakeFinished = false;

        setCipherSuite(CipherSuite.C_NULL);
        setEnabledProtocols(enabledProtocols);

        if (conn != null) {
            algorithmConstraints = new SSLAlgorithmConstraints(conn, true);
        } else {        // engine != null
            algorithmConstraints = new SSLAlgorithmConstraints(engine, true);
        }
    }

    /*
     * Reroutes calls to the SSLSocket or SSLEngine (*SE).
     *
     * We could have also done it by extra classes
     * and letting them override, but this seemed much
     * less involved.
     */
    void fatalSE(byte b, String diagnostic) throws IOException {
        fatalSE(b, diagnostic, null);
    }

    void fatalSE(byte b, Throwable cause) throws IOException {
        fatalSE(b, null, cause);
    }

    void fatalSE(byte b, String diagnostic, Throwable cause)
            throws IOException {
        if (conn != null) {
            conn.fatal(b, diagnostic, cause);
        } else {
            engine.fatal(b, diagnostic, cause);
        }
    }

    void warningSE(byte b) {
        if (conn != null) {
            conn.warning(b);
        } else {
            engine.warning(b);
        }
    }

    // ONLY used by ClientHandshaker to setup the peer host in SSLSession.
    String getHostSE() {
        if (conn != null) {
            return conn.getHost();
        } else {
            return engine.getPeerHost();
        }
    }

    // ONLY used by ServerHandshaker to setup the peer host in SSLSession.
    String getHostAddressSE() {
        if (conn != null) {
            return conn.getInetAddress().getHostAddress();
        } else {
            /*
             * This is for caching only, doesn't matter that's is really
             * a hostname.  The main thing is that it doesn't do
             * a reverse DNS lookup, potentially slowing things down.
             */
            return engine.getPeerHost();
        }
    }

    int getPortSE() {
        if (conn != null) {
            return conn.getPort();
        } else {
            return engine.getPeerPort();
        }
    }

    int getLocalPortSE() {
        if (conn != null) {
            return conn.getLocalPort();
        } else {
            return -1;
        }
    }

    AccessControlContext getAccSE() {
        if (conn != null) {
            return conn.getAcc();
        } else {
            return engine.getAcc();
        }
    }

    String getEndpointIdentificationAlgorithmSE() {
        SSLParameters paras;
        if (conn != null) {
            paras = conn.getSSLParameters();
        } else {
            paras = engine.getSSLParameters();
        }

        return paras.getEndpointIdentificationAlgorithm();
    }

    private void setVersionSE(ProtocolVersion protocolVersion) {
        if (conn != null) {
            conn.setVersion(protocolVersion);
        } else {
            engine.setVersion(protocolVersion);
        }
    }

    /**
     * Set the active protocol version and propagate it to the SSLSocket
     * and our handshake streams. Called from ClientHandshaker
     * and ServerHandshaker with the negotiated protocol version.
     */
    void setVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
        setVersionSE(protocolVersion);
    }

    /**
     * Set the enabled protocols. Called from the constructor or
     * SSLSocketImpl/SSLEngineImpl.setEnabledProtocols() (if the
     * handshake is not yet in progress).
     */
    void setEnabledProtocols(ProtocolList enabledProtocols) {
        activeCipherSuites = null;
        activeProtocols = null;

        this.enabledProtocols = enabledProtocols;
    }

    /**
     * Set the enabled cipher suites. Called from
     * SSLSocketImpl/SSLEngineImpl.setEnabledCipherSuites() (if the
     * handshake is not yet in progress).
     */
    void setEnabledCipherSuites(CipherSuiteList enabledCipherSuites) {
        activeCipherSuites = null;
        activeProtocols = null;
        this.enabledCipherSuites = enabledCipherSuites;
    }

    /**
     * Set the algorithm constraints. Called from the constructor or
     * SSLSocketImpl/SSLEngineImpl.setAlgorithmConstraints() (if the
     * handshake is not yet in progress).
     */
    void setAlgorithmConstraints(AlgorithmConstraints algorithmConstraints) {
        activeCipherSuites = null;
        activeProtocols = null;

        this.algorithmConstraints =
            new SSLAlgorithmConstraints(algorithmConstraints);
        this.localSupportedSignAlgs = null;
    }

    Collection<SignatureAndHashAlgorithm> getLocalSupportedSignAlgs() {
        if (localSupportedSignAlgs == null) {
            localSupportedSignAlgs =
                SignatureAndHashAlgorithm.getSupportedAlgorithms(
                                                    algorithmConstraints);
        }

        return localSupportedSignAlgs;
    }

    void setPeerSupportedSignAlgs(
            Collection<SignatureAndHashAlgorithm> algorithms) {
        peerSupportedSignAlgs =
            new ArrayList<SignatureAndHashAlgorithm>(algorithms);
    }

    Collection<SignatureAndHashAlgorithm> getPeerSupportedSignAlgs() {
        return peerSupportedSignAlgs;
    }


    /**
     * Set the identification protocol. Called from the constructor or
     * SSLSocketImpl/SSLEngineImpl.setIdentificationProtocol() (if the
     * handshake is not yet in progress).
     */
    void setIdentificationProtocol(String protocol) {
        this.identificationProtocol = protocol;
    }

    /**
     * Sets the server name indication of the handshake.
     */
    void setSNIServerNames(List<SNIServerName> serverNames) {
        // The serverNames parameter is unmodifiable.
        this.serverNames = serverNames;
    }

    /**
     * Sets the server name matchers of the handshaking.
     */
    void setSNIMatchers(Collection<SNIMatcher> sniMatchers) {
        // The sniMatchers parameter is unmodifiable.
        this.sniMatchers = sniMatchers;
    }

    /**
     * Sets the maximum packet size of the handshaking.
     */
    void setMaximumPacketSize(int maximumPacketSize) {
        this.maximumPacketSize = maximumPacketSize;
    }

    /**
     * Sets the Application Protocol list.
     */
    void setApplicationProtocols(String[] apl) {
        this.localApl = apl;
    }

    /**
     * Gets the "negotiated" ALPN value.
     */
    String getHandshakeApplicationProtocol() {
        return applicationProtocol;
    }

    /**
     * Sets the Application Protocol selector function for SSLEngine.
     */
    void setApplicationProtocolSelectorSSLEngine(
        BiFunction<SSLEngine,List<String>,String> selector) {
        this.appProtocolSelectorSSLEngine = selector;
    }

    /**
     * Sets the Application Protocol selector function for SSLSocket.
     */
    void setApplicationProtocolSelectorSSLSocket(
        BiFunction<SSLSocket,List<String>,String> selector) {
        this.appProtocolSelectorSSLSocket = selector;
    }

    /**
     * Sets the cipher suites preference.
     */
    void setUseCipherSuitesOrder(boolean on) {
        this.preferLocalCipherSuites = on;
    }

    /**
     * Prior to handshaking, activate the handshake and initialize the version,
     * input stream and output stream.
     */
    void activate(ProtocolVersion helloVersion) throws IOException {
        if (activeProtocols == null) {
            activeProtocols = getActiveProtocols();
        }

        if (activeProtocols.collection().isEmpty() ||
                activeProtocols.max.v == ProtocolVersion.NONE.v) {
            throw new SSLHandshakeException(
                    "No appropriate protocol (protocol is disabled or " +
                    "cipher suites are inappropriate)");
        }

        if (activeCipherSuites == null) {
            activeCipherSuites = getActiveCipherSuites();
        }

        if (activeCipherSuites.collection().isEmpty()) {
            throw new SSLHandshakeException("No appropriate cipher suite");
        }

        // temporary protocol version until the actual protocol version
        // is negotiated in the Hello exchange. This affects the record
        // version we sent with the ClientHello.
        if (!isInitialHandshake) {
            protocolVersion = activeProtocolVersion;
        } else {
            protocolVersion = activeProtocols.max;
        }

        if (helloVersion == null || helloVersion.v == ProtocolVersion.NONE.v) {
            helloVersion = activeProtocols.helloVersion;
        }

        // We accumulate digests of the handshake messages so that
        // we can read/write CertificateVerify and Finished messages,
        // getting assurance against some particular active attacks.
        handshakeHash = new HandshakeHash(needCertVerify);

        // Generate handshake input/output stream.
        if (conn != null) {
            input = new HandshakeInStream();
            output = new HandshakeOutStream(conn.outputRecord);

            conn.inputRecord.setHandshakeHash(handshakeHash);
            conn.inputRecord.setHelloVersion(helloVersion);

            conn.outputRecord.setHandshakeHash(handshakeHash);
            conn.outputRecord.setHelloVersion(helloVersion);
            conn.outputRecord.setVersion(protocolVersion);
        } else if (engine != null) {
            input = new HandshakeInStream();
            output = new HandshakeOutStream(engine.outputRecord);

            engine.inputRecord.setHandshakeHash(handshakeHash);
            engine.inputRecord.setHelloVersion(helloVersion);

            engine.outputRecord.setHandshakeHash(handshakeHash);
            engine.outputRecord.setHelloVersion(helloVersion);
            engine.outputRecord.setVersion(protocolVersion);
        }

        handshakeActivated = true;
    }

    /**
     * Set cipherSuite and keyExchange to the given CipherSuite.
     * Does not perform any verification that this is a valid selection,
     * this must be done before calling this method.
     */
    void setCipherSuite(CipherSuite s) {
        this.cipherSuite = s;
        this.keyExchange = s.keyExchange;
    }

    /**
     * Check if the given ciphersuite is enabled and available within the
     * current active cipher suites.
     *
     * Does not check if the required server certificates are available.
     */
    boolean isNegotiable(CipherSuite s) {
        if (activeCipherSuites == null) {
            activeCipherSuites = getActiveCipherSuites();
        }

        return isNegotiable(activeCipherSuites, s);
    }

    /**
     * Check if the given ciphersuite is enabled and available within the
     * proposed cipher suite list.
     *
     * Does not check if the required server certificates are available.
     */
    static final boolean isNegotiable(CipherSuiteList proposed, CipherSuite s) {
        return proposed.contains(s) && s.isNegotiable();
    }

    /**
     * Check if the given protocol version is enabled and available.
     */
    boolean isNegotiable(ProtocolVersion protocolVersion) {
        if (activeProtocols == null) {
            activeProtocols = getActiveProtocols();
        }

        return activeProtocols.contains(protocolVersion);
    }

    /**
     * Select a protocol version from the list. Called from
     * ServerHandshaker to negotiate protocol version.
     *
     * Return the lower of the protocol version suggested in the
     * clien hello and the highest supported by the server.
     */
    ProtocolVersion selectProtocolVersion(ProtocolVersion protocolVersion) {
        if (activeProtocols == null) {
            activeProtocols = getActiveProtocols();
        }

        return activeProtocols.selectProtocolVersion(protocolVersion);
    }

    /**
     * Get the active cipher suites.
     *
     * In TLS 1.1, many weak or vulnerable cipher suites were obsoleted,
     * such as TLS_RSA_EXPORT_WITH_RC4_40_MD5. The implementation MUST NOT
     * negotiate these cipher suites in TLS 1.1 or later mode.
     *
     * Therefore, when the active protocols only include TLS 1.1 or later,
     * the client cannot request to negotiate those obsoleted cipher
     * suites.  That is, the obsoleted suites should not be included in the
     * client hello. So we need to create a subset of the enabled cipher
     * suites, the active cipher suites, which does not contain obsoleted
     * cipher suites of the minimum active protocol.
     *
     * Return empty list instead of null if no active cipher suites.
     */
    CipherSuiteList getActiveCipherSuites() {
        if (activeCipherSuites == null) {
            if (activeProtocols == null) {
                activeProtocols = getActiveProtocols();
            }

            ArrayList<CipherSuite> suites = new ArrayList<>();
            if (!(activeProtocols.collection().isEmpty()) &&
                    activeProtocols.min.v != ProtocolVersion.NONE.v) {
                Map<NamedGroupType, Boolean> cachedStatus =
                        new EnumMap<>(NamedGroupType.class);
                for (CipherSuite suite : enabledCipherSuites.collection()) {
                    if (suite.isAvailable() &&
                            (!activeProtocols.min.obsoletes(suite)) &&
                            activeProtocols.max.supports(suite)) {
                        if (isActivatable(suite, cachedStatus)) {
                            suites.add(suite);
                        }
                    } else if (debug != null && Debug.isOn("verbose")) {
                        if (activeProtocols.min.obsoletes(suite)) {
                            System.out.println(
                                "Ignoring obsoleted cipher suite: " + suite);
                        } else {
                            System.out.println(
                                "Ignoring unsupported cipher suite: " + suite);
                        }
                    }
                }
            }
            activeCipherSuites = new CipherSuiteList(suites);
        }

        return activeCipherSuites;
    }

    /*
     * Get the active protocol versions.
     *
     * In TLS 1.1, many weak or vulnerable cipher suites were obsoleted,
     * such as TLS_RSA_EXPORT_WITH_RC4_40_MD5. The implementation MUST NOT
     * negotiate these cipher suites in TLS 1.1 or later mode.
     *
     * For example, if "TLS_RSA_EXPORT_WITH_RC4_40_MD5" is the
     * only enabled cipher suite, the client cannot request TLS 1.1 or
     * later, even though TLS 1.1 or later is enabled.  We need to create a
     * subset of the enabled protocols, called the active protocols, which
     * contains protocols appropriate to the list of enabled Ciphersuites.
     *
     * Return empty list instead of null if no active protocol versions.
     */
    ProtocolList getActiveProtocols() {
        if (activeProtocols == null) {
            boolean enabledSSL20Hello = false;
            boolean checkedCurves = false;
            boolean hasCurves = false;
            ArrayList<ProtocolVersion> protocols = new ArrayList<>(4);
            for (ProtocolVersion protocol : enabledProtocols.collection()) {
                // Need not to check the SSL20Hello protocol.
                if (protocol.v == ProtocolVersion.SSL20Hello.v) {
                    enabledSSL20Hello = true;
                    continue;
                }

                if (!algorithmConstraints.permits(
                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                        protocol.name, null)) {
                    if (debug != null && Debug.isOn("verbose")) {
                        System.out.println(
                            "Ignoring disabled protocol: " + protocol);
                    }

                    continue;
                }

                boolean found = false;
                Map<NamedGroupType, Boolean> cachedStatus =
                        new EnumMap<>(NamedGroupType.class);
                for (CipherSuite suite : enabledCipherSuites.collection()) {
                    if (suite.isAvailable() && (!protocol.obsoletes(suite)) &&
                                               protocol.supports(suite)) {
                        if (isActivatable(suite, cachedStatus)) {
                            protocols.add(protocol);
                            found = true;
                            break;
                        }
                    } else if (debug != null && Debug.isOn("verbose")) {
                        System.out.println(
                            "Ignoring unsupported cipher suite: " + suite +
                                 " for " + protocol);
                    }
                }

                if (!found && (debug != null) && Debug.isOn("handshake")) {
                    System.out.println(
                        "No available cipher suite for " + protocol);
                }
            }

            if (!protocols.isEmpty() && enabledSSL20Hello) {
                protocols.add(ProtocolVersion.SSL20Hello);
            }

            activeProtocols = new ProtocolList(protocols);
        }

        return activeProtocols;
    }

    private boolean isActivatable(CipherSuite suite,
            Map<NamedGroupType, Boolean> cachedStatus) {

        if (algorithmConstraints.permits(
                EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), suite.name, null)) {
            boolean available = true;
            NamedGroupType groupType = suite.keyExchange.groupType;
            if (groupType != NAMED_GROUP_NONE) {
                Boolean checkedStatus = cachedStatus.get(groupType);
                if (checkedStatus == null) {
                    available = SupportedGroupsExtension.isActivatable(
                            algorithmConstraints, groupType);
                    cachedStatus.put(groupType, available);

                    if (!available && debug != null && Debug.isOn("verbose")) {
                        System.out.println("No activated named group");
                    }
                } else {
                    available = checkedStatus.booleanValue();
                }

                if (!available && debug != null && Debug.isOn("verbose")) {
                    System.out.println(
                        "No active named group, ignore " + suite);
                }

                return available;
            } else {
                return true;
            }
        } else if (debug != null && Debug.isOn("verbose")) {
            System.out.println("Ignoring disabled cipher suite: " + suite);
        }

        return false;
    }

    /**
     * As long as handshaking has not activated, we can
     * change whether session creations are allowed.
     *
     * Callers should do their own checking if handshaking
     * has activated.
     */
    void setEnableSessionCreation(boolean newSessions) {
        enableNewSession = newSessions;
    }

    /**
     * Create a new read cipher and return it to caller.
     */
    CipherBox newReadCipher() throws NoSuchAlgorithmException {
        BulkCipher cipher = cipherSuite.cipher;
        CipherBox box;
        if (isClient) {
            box = cipher.newCipher(protocolVersion, svrWriteKey, svrWriteIV,
                                   sslContext.getSecureRandom(), false);
            svrWriteKey = null;
            svrWriteIV = null;
        } else {
            box = cipher.newCipher(protocolVersion, clntWriteKey, clntWriteIV,
                                   sslContext.getSecureRandom(), false);
            clntWriteKey = null;
            clntWriteIV = null;
        }
        return box;
    }

    /**
     * Create a new write cipher and return it to caller.
     */
    CipherBox newWriteCipher() throws NoSuchAlgorithmException {
        BulkCipher cipher = cipherSuite.cipher;
        CipherBox box;
        if (isClient) {
            box = cipher.newCipher(protocolVersion, clntWriteKey, clntWriteIV,
                                   sslContext.getSecureRandom(), true);
            clntWriteKey = null;
            clntWriteIV = null;
        } else {
            box = cipher.newCipher(protocolVersion, svrWriteKey, svrWriteIV,
                                   sslContext.getSecureRandom(), true);
            svrWriteKey = null;
            svrWriteIV = null;
        }
        return box;
    }

    /**
     * Create a new read MAC and return it to caller.
     */
    Authenticator newReadAuthenticator()
            throws NoSuchAlgorithmException, InvalidKeyException {

        Authenticator authenticator = null;
        if (cipherSuite.cipher.cipherType == AEAD_CIPHER) {
            authenticator = new Authenticator(protocolVersion);
        } else {
            MacAlg macAlg = cipherSuite.macAlg;
            if (isClient) {
                authenticator = macAlg.newMac(protocolVersion, svrMacSecret);
                svrMacSecret = null;
            } else {
                authenticator = macAlg.newMac(protocolVersion, clntMacSecret);
                clntMacSecret = null;
            }
        }

        return authenticator;
    }

    /**
     * Create a new write MAC and return it to caller.
     */
    Authenticator newWriteAuthenticator()
            throws NoSuchAlgorithmException, InvalidKeyException {

        Authenticator authenticator = null;
        if (cipherSuite.cipher.cipherType == AEAD_CIPHER) {
            authenticator = new Authenticator(protocolVersion);
        } else {
            MacAlg macAlg = cipherSuite.macAlg;
            if (isClient) {
                authenticator = macAlg.newMac(protocolVersion, clntMacSecret);
                clntMacSecret = null;
            } else {
                authenticator = macAlg.newMac(protocolVersion, svrMacSecret);
                svrMacSecret = null;
            }
        }

        return authenticator;
    }

    /*
     * Returns true iff the handshake sequence is done, so that
     * this freshly created session can become the current one.
     */
    boolean isDone() {
        return started() && handshakeState.isEmpty() && handshakeFinished;
    }


    /*
     * Returns the session which was created through this
     * handshake sequence ... should be called after isDone()
     * returns true.
     */
    SSLSessionImpl getSession() {
        return session;
    }

    /*
     * Set the handshake session
     */
    void setHandshakeSessionSE(SSLSessionImpl handshakeSession) {
        if (conn != null) {
            conn.setHandshakeSession(handshakeSession);
        } else {
            engine.setHandshakeSession(handshakeSession);
        }
    }

    void expectingFinishFlightSE() {
        if (conn != null) {
            conn.expectingFinishFlight();
        } else {
            engine.expectingFinishFlight();
        }
    }

    /*
     * Returns true if renegotiation is in use for this connection.
     */
    boolean isSecureRenegotiation() {
        return secureRenegotiation;
    }

    /*
     * Returns the verify_data from the Finished message sent by the client.
     */
    byte[] getClientVerifyData() {
        return clientVerifyData;
    }

    /*
     * Returns the verify_data from the Finished message sent by the server.
     */
    byte[] getServerVerifyData() {
        return serverVerifyData;
    }

    /*
     * This routine is fed SSL handshake records when they become available,
     * and processes messages found therein.
     */
    void processRecord(ByteBuffer record,
            boolean expectingFinished) throws IOException {

        checkThrown();

        /*
         * Store the incoming handshake data, then see if we can
         * now process any completed handshake messages
         */
        input.incomingRecord(record);

        /*
         * We don't need to create a separate delegatable task
         * for finished messages.
         */
        if ((conn != null) || expectingFinished) {
            processLoop();
        } else {
            delegateTask(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws Exception {
                    processLoop();
                    return null;
                }
            });
        }
    }

    /*
     * On input, we hash messages one at a time since servers may need
     * to access an intermediate hash to validate a CertificateVerify
     * message.
     *
     * Note that many handshake messages can come in one record (and often
     * do, to reduce network resource utilization), and one message can also
     * require multiple records (e.g. very large Certificate messages).
     */
    void processLoop() throws IOException {

        // need to read off 4 bytes at least to get the handshake
        // message type and length.
        while (input.available() >= 4) {
            byte messageType;
            int messageLen;

            /*
             * See if we can read the handshake message header, and
             * then the entire handshake message.  If not, wait till
             * we can read and process an entire message.
             */
            input.mark(4);

            messageType = (byte)input.getInt8();
            if (HandshakeMessage.isUnsupported(messageType)) {
                throw new SSLProtocolException(
                    "Received unsupported or unknown handshake message: " +
                    messageType);
            }

            messageLen = input.getInt24();

            if (input.available() < messageLen) {
                input.reset();
                return;
            }

            // Set the flags in the message receiving side.
            if (messageType == HandshakeMessage.ht_client_hello) {
                clientHelloDelivered = true;
            } else if (messageType == HandshakeMessage.ht_hello_request) {
                serverHelloRequested = true;
            }

            /*
             * Process the message.  We require
             * that processMessage() consumes the entire message.  In
             * lieu of explicit error checks (how?!) we assume that the
             * data will look like garbage on encoding/processing errors,
             * and that other protocol code will detect such errors.
             *
             * Note that digesting is normally deferred till after the
             * message has been processed, though to process at least the
             * client's Finished message (i.e. send the server's) we need
             * to acccelerate that digesting.
             *
             * Also, note that hello request messages are never hashed;
             * that includes the hello request header, too.
             */
            processMessage(messageType, messageLen);

            // Reload if this message has been reserved.
            //
            // Note: in the implementation, only certificate_verify and
            // finished messages are reserved.
            if ((messageType == HandshakeMessage.ht_finished) ||
                (messageType == HandshakeMessage.ht_certificate_verify)) {

                handshakeHash.reload();
            }
        }
    }


    /**
     * Returns true iff the handshaker has been activated.
     *
     * In activated state, the handshaker may not send any messages out.
     */
    boolean activated() {
        return handshakeActivated;
    }

    /**
     * Returns true iff the handshaker has sent any messages.
     */
    boolean started() {
        return (serverHelloRequested || clientHelloDelivered);
    }

    /*
     * Used to kickstart the negotiation ... either writing a
     * ClientHello or a HelloRequest as appropriate, whichever
     * the subclass returns.  NOP if handshaking's already started.
     */
    void kickstart() throws IOException {
        if ((isClient && clientHelloDelivered) ||
                (!isClient && serverHelloRequested)) {
            return;
        }

        HandshakeMessage m = getKickstartMessage();
        handshakeState.update(m, resumingSession);

        if (debug != null && Debug.isOn("handshake")) {
            m.print(System.out);
        }
        m.write(output);
        output.flush();

        // Set the flags in the message delivering side.
        int handshakeType = m.messageType();
        if (handshakeType == HandshakeMessage.ht_hello_request) {
            serverHelloRequested = true;
        } else {        // HandshakeMessage.ht_client_hello
            clientHelloDelivered = true;
        }
    }

    /**
     * Both client and server modes can start handshaking; but the
     * message they send to do so is different.
     */
    abstract HandshakeMessage getKickstartMessage() throws SSLException;

    /*
     * Client and Server side protocols are each driven though this
     * call, which processes a single message and drives the appropriate
     * side of the protocol state machine (depending on the subclass).
     */
    abstract void processMessage(byte messageType, int messageLen)
        throws IOException;

    /*
     * Most alerts in the protocol relate to handshaking problems.
     * Alerts are detected as the connection reads data.
     */
    abstract void handshakeAlert(byte description) throws SSLProtocolException;

    /*
     * Sends a change cipher spec message and updates the write side
     * cipher state so that future messages use the just-negotiated spec.
     */
    void sendChangeCipherSpec(Finished mesg, boolean lastMessage)
            throws IOException {

        output.flush(); // i.e. handshake data

        /*
         * The write cipher state is protected by the connection write lock
         * so we must grab it while making the change. We also
         * make sure no writes occur between sending the ChangeCipherSpec
         * message, installing the new cipher state, and sending the
         * Finished message.
         *
         * We already hold SSLEngine/SSLSocket "this" by virtue
         * of this being called from the readRecord code.
         */
        if (conn != null) {
            conn.writeLock.lock();
            try {
                handshakeState.changeCipherSpec(false, isClient);
                conn.changeWriteCiphers();
                if (debug != null && Debug.isOn("handshake")) {
                    mesg.print(System.out);
                }

                handshakeState.update(mesg, resumingSession);
                mesg.write(output);
                output.flush();
            } finally {
                conn.writeLock.unlock();
            }
        } else {
            synchronized (engine.writeLock) {
                handshakeState.changeCipherSpec(false, isClient);
                engine.changeWriteCiphers();
                if (debug != null && Debug.isOn("handshake")) {
                    mesg.print(System.out);
                }

                handshakeState.update(mesg, resumingSession);
                mesg.write(output);
                output.flush();
            }
        }

        if (lastMessage) {
            handshakeFinished = true;
        }
    }

    void receiveChangeCipherSpec() throws IOException {
        handshakeState.changeCipherSpec(true, isClient);
    }

    /*
     * Single access point to key calculation logic.  Given the
     * pre-master secret and the nonces from client and server,
     * produce all the keying material to be used.
     */
    void calculateKeys(SecretKey preMasterSecret, ProtocolVersion version) {
        SecretKey master = calculateMasterSecret(preMasterSecret, version);
        session.setMasterSecret(master);
        calculateConnectionKeys(master);
    }

    /*
     * Calculate the master secret from its various components.  This is
     * used for key exchange by all cipher suites.
     *
     * The master secret is the catenation of three MD5 hashes, each
     * consisting of the pre-master secret and a SHA1 hash.  Those three
     * SHA1 hashes are of (different) constant strings, the pre-master
     * secret, and the nonces provided by the client and the server.
     */
    @SuppressWarnings("deprecation")
    private SecretKey calculateMasterSecret(SecretKey preMasterSecret,
            ProtocolVersion requestedVersion) {

        if (debug != null && Debug.isOn("keygen")) {
            HexDumpEncoder      dump = new HexDumpEncoder();

            System.out.println("SESSION KEYGEN:");

            System.out.println("PreMaster Secret:");
            printHex(dump, preMasterSecret.getEncoded());

            // Nonces are dumped with connection keygen, no
            // benefit to doing it twice
        }

        // What algs/params do we need to use?
        String masterAlg;
        PRF prf;

        byte majorVersion = protocolVersion.major;
        byte minorVersion = protocolVersion.minor;
        if (protocolVersion.isDTLSProtocol()) {
            // Use TLS version number for DTLS key calculation
            if (protocolVersion.v == ProtocolVersion.DTLS10.v) {
                majorVersion = ProtocolVersion.TLS11.major;
                minorVersion = ProtocolVersion.TLS11.minor;

                masterAlg = "SunTlsMasterSecret";
                prf = P_NONE;
            } else {    // DTLS 1.2
                majorVersion = ProtocolVersion.TLS12.major;
                minorVersion = ProtocolVersion.TLS12.minor;

                masterAlg = "SunTls12MasterSecret";
                prf = cipherSuite.prfAlg;
            }
        } else {
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                masterAlg = "SunTls12MasterSecret";
                prf = cipherSuite.prfAlg;
            } else {
                masterAlg = "SunTlsMasterSecret";
                prf = P_NONE;
            }
        }

        String prfHashAlg = prf.getPRFHashAlg();
        int prfHashLength = prf.getPRFHashLength();
        int prfBlockSize = prf.getPRFBlockSize();

        TlsMasterSecretParameterSpec spec;
        if (session.getUseExtendedMasterSecret()) {
            // reset to use the extended master secret algorithm
            masterAlg = "SunTlsExtendedMasterSecret";

            byte[] sessionHash = null;
            if (protocolVersion.useTLS12PlusSpec()) {
                sessionHash = handshakeHash.getFinishedHash();
            } else {
                // TLS 1.0/1.1, DTLS 1.0
                sessionHash = new byte[36];
                try {
                    handshakeHash.getMD5Clone().digest(sessionHash, 0, 16);
                    handshakeHash.getSHAClone().digest(sessionHash, 16, 20);
                } catch (DigestException de) {
                    throw new ProviderException(de);
                }
            }

            spec = new TlsMasterSecretParameterSpec(
                    preMasterSecret,
                    (majorVersion & 0xFF), (minorVersion & 0xFF),
                    sessionHash,
                    prfHashAlg, prfHashLength, prfBlockSize);
        } else {
            spec = new TlsMasterSecretParameterSpec(
                    preMasterSecret,
                    (majorVersion & 0xFF), (minorVersion & 0xFF),
                    clnt_random.random_bytes, svr_random.random_bytes,
                    prfHashAlg, prfHashLength, prfBlockSize);
        }

        try {
            KeyGenerator kg = JsseJce.getKeyGenerator(masterAlg);
            kg.init(spec);
            return kg.generateKey();
        } catch (InvalidAlgorithmParameterException |
                NoSuchAlgorithmException iae) {
            // unlikely to happen, otherwise, must be a provider exception
            //
            // For RSA premaster secrets, do not signal a protocol error
            // due to the Bleichenbacher attack. See comments further down.
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("RSA master secret generation error:");
                iae.printStackTrace(System.out);
            }
            throw new ProviderException(iae);

        }
    }

    /*
     * Calculate the keys needed for this connection, once the session's
     * master secret has been calculated.  Uses the master key and nonces;
     * the amount of keying material generated is a function of the cipher
     * suite that's been negotiated.
     *
     * This gets called both on the "full handshake" (where we exchanged
     * a premaster secret and started a new session) as well as on the
     * "fast handshake" (where we just resumed a pre-existing session).
     */
    @SuppressWarnings("deprecation")
    void calculateConnectionKeys(SecretKey masterKey) {
        /*
         * For both the read and write sides of the protocol, we use the
         * master to generate MAC secrets and cipher keying material.  Block
         * ciphers need initialization vectors, which we also generate.
         *
         * First we figure out how much keying material is needed.
         */
        int hashSize = cipherSuite.macAlg.size;
        boolean is_exportable = cipherSuite.exportable;
        BulkCipher cipher = cipherSuite.cipher;
        int expandedKeySize = is_exportable ? cipher.expandedKeySize : 0;

        // Which algs/params do we need to use?
        String keyMaterialAlg;
        PRF prf;

        byte majorVersion = protocolVersion.major;
        byte minorVersion = protocolVersion.minor;
        if (protocolVersion.isDTLSProtocol()) {
            // Use TLS version number for DTLS key calculation
            if (protocolVersion.v == ProtocolVersion.DTLS10.v) {
                majorVersion = ProtocolVersion.TLS11.major;
                minorVersion = ProtocolVersion.TLS11.minor;

                keyMaterialAlg = "SunTlsKeyMaterial";
                prf = P_NONE;
            } else {    // DTLS 1.2+
                majorVersion = ProtocolVersion.TLS12.major;
                minorVersion = ProtocolVersion.TLS12.minor;

                keyMaterialAlg = "SunTls12KeyMaterial";
                prf = cipherSuite.prfAlg;
            }
        } else {
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                keyMaterialAlg = "SunTls12KeyMaterial";
                prf = cipherSuite.prfAlg;
            } else {
                keyMaterialAlg = "SunTlsKeyMaterial";
                prf = P_NONE;
            }
        }

        String prfHashAlg = prf.getPRFHashAlg();
        int prfHashLength = prf.getPRFHashLength();
        int prfBlockSize = prf.getPRFBlockSize();

        // TLS v1.1+ and DTLS use an explicit IV in CBC cipher suites to
        // protect against the CBC attacks.  AEAD/GCM cipher suites in TLS
        // v1.2 or later use a fixed IV as the implicit part of the partially
        // implicit nonce technique described in RFC 5116.
        int ivSize = cipher.ivSize;
        if (cipher.cipherType == AEAD_CIPHER) {
            ivSize = cipher.fixedIvSize;
        } else if ((cipher.cipherType == BLOCK_CIPHER) &&
                protocolVersion.useTLS11PlusSpec()) {
            ivSize = 0;
        }

        TlsKeyMaterialParameterSpec spec = new TlsKeyMaterialParameterSpec(
                masterKey, (majorVersion & 0xFF), (minorVersion & 0xFF),
                clnt_random.random_bytes, svr_random.random_bytes,
                cipher.algorithm, cipher.keySize, expandedKeySize,
                ivSize, hashSize,
                prfHashAlg, prfHashLength, prfBlockSize);

        try {
            KeyGenerator kg = JsseJce.getKeyGenerator(keyMaterialAlg);
            kg.init(spec);
            TlsKeyMaterialSpec keySpec = (TlsKeyMaterialSpec)kg.generateKey();

            // Return null if cipher keys are not supposed to be generated.
            clntWriteKey = keySpec.getClientCipherKey();
            svrWriteKey = keySpec.getServerCipherKey();

            // Return null if IVs are not supposed to be generated.
            clntWriteIV = keySpec.getClientIv();
            svrWriteIV = keySpec.getServerIv();

            // Return null if MAC keys are not supposed to be generated.
            clntMacSecret = keySpec.getClientMacKey();
            svrMacSecret = keySpec.getServerMacKey();
        } catch (GeneralSecurityException e) {
            throw new ProviderException(e);
        }

        //
        // Dump the connection keys as they're generated.
        //
        if (debug != null && Debug.isOn("keygen")) {
            synchronized (System.out) {
                HexDumpEncoder  dump = new HexDumpEncoder();

                System.out.println("CONNECTION KEYGEN:");

                // Inputs:
                System.out.println("Client Nonce:");
                printHex(dump, clnt_random.random_bytes);
                System.out.println("Server Nonce:");
                printHex(dump, svr_random.random_bytes);
                System.out.println("Master Secret:");
                printHex(dump, masterKey.getEncoded());

                // Outputs:
                if (clntMacSecret != null) {
                    System.out.println("Client MAC write Secret:");
                    printHex(dump, clntMacSecret.getEncoded());
                    System.out.println("Server MAC write Secret:");
                    printHex(dump, svrMacSecret.getEncoded());
                } else {
                    System.out.println("... no MAC keys used for this cipher");
                }

                if (clntWriteKey != null) {
                    System.out.println("Client write key:");
                    printHex(dump, clntWriteKey.getEncoded());
                    System.out.println("Server write key:");
                    printHex(dump, svrWriteKey.getEncoded());
                } else {
                    System.out.println("... no encryption keys used");
                }

                if (clntWriteIV != null) {
                    System.out.println("Client write IV:");
                    printHex(dump, clntWriteIV.getIV());
                    System.out.println("Server write IV:");
                    printHex(dump, svrWriteIV.getIV());
                } else {
                    if (protocolVersion.useTLS11PlusSpec()) {
                        System.out.println(
                                "... no IV derived for this protocol");
                    } else {
                        System.out.println("... no IV used for this cipher");
                    }
                }
                System.out.flush();
            }
        }
    }

    private static void printHex(HexDumpEncoder dump, byte[] bytes) {
        if (bytes == null) {
            System.out.println("(key bytes not available)");
        } else {
            try {
                dump.encodeBuffer(bytes, System.out);
            } catch (IOException e) {
                // just for debugging, ignore this
            }
        }
    }

    /*
     * Implement a simple task delegator.
     *
     * We are currently implementing this as a single delegator, may
     * try for parallel tasks later.  Client Authentication could
     * benefit from this, where ClientKeyExchange/CertificateVerify
     * could be carried out in parallel.
     */
    class DelegatedTask<E> implements Runnable {

        private PrivilegedExceptionAction<E> pea;

        DelegatedTask(PrivilegedExceptionAction<E> pea) {
            this.pea = pea;
        }

        public void run() {
            synchronized (engine) {
                try {
                    AccessController.doPrivileged(pea, engine.getAcc());
                } catch (PrivilegedActionException pae) {
                    thrown = pae.getException();
                } catch (RuntimeException rte) {
                    thrown = rte;
                }
                delegatedTask = null;
                taskDelegated = false;
            }
        }
    }

    private <T> void delegateTask(PrivilegedExceptionAction<T> pea) {
        delegatedTask = new DelegatedTask<T>(pea);
        taskDelegated = false;
        thrown = null;
    }

    DelegatedTask<?> getTask() {
        if (!taskDelegated) {
            taskDelegated = true;
            return delegatedTask;
        } else {
            return null;
        }
    }

    /*
     * See if there are any tasks which need to be delegated
     *
     * Locked by SSLEngine.this.
     */
    boolean taskOutstanding() {
        return (delegatedTask != null);
    }

    /*
     * The previous caller failed for some reason, report back the
     * Exception.  We won't worry about Error's.
     *
     * Locked by SSLEngine.this.
     */
    void checkThrown() throws SSLException {
        synchronized (thrownLock) {
            if (thrown != null) {

                String msg = thrown.getMessage();

                if (msg == null) {
                    msg = "Delegated task threw Exception/Error";
                }

                /*
                 * See what the underlying type of exception is.  We should
                 * throw the same thing.  Chain thrown to the new exception.
                 */
                Exception e = thrown;
                thrown = null;

                if (e instanceof RuntimeException) {
                    throw new RuntimeException(msg, e);
                } else if (e instanceof SSLHandshakeException) {
                    throw (SSLHandshakeException)
                        new SSLHandshakeException(msg).initCause(e);
                } else if (e instanceof SSLKeyException) {
                    throw (SSLKeyException)
                        new SSLKeyException(msg).initCause(e);
                } else if (e instanceof SSLPeerUnverifiedException) {
                    throw (SSLPeerUnverifiedException)
                        new SSLPeerUnverifiedException(msg).initCause(e);
                } else if (e instanceof SSLProtocolException) {
                    throw (SSLProtocolException)
                        new SSLProtocolException(msg).initCause(e);
                } else {
                    /*
                     * If it's SSLException or any other Exception,
                     * we'll wrap it in an SSLException.
                     */
                    throw new SSLException(msg, e);
                }
            }
        }
    }
}
