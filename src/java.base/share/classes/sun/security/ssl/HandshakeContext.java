/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import javax.crypto.SecretKey;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.x500.X500Principal;
import sun.security.ssl.SupportedGroupsExtension.NamedGroup;
import sun.security.ssl.SupportedGroupsExtension.NamedGroupType;
import static sun.security.ssl.SupportedGroupsExtension.NamedGroupType.*;
import sun.security.ssl.SupportedGroupsExtension.SupportedGroups;

abstract class HandshakeContext implements ConnectionContext {
    // System properties

    // By default, disable the unsafe legacy session renegotiation.
    static final boolean allowUnsafeRenegotiation =
            Utilities.getBooleanProperty(
                    "sun.security.ssl.allowUnsafeRenegotiation", false);

    // For maximum interoperability and backward compatibility, RFC 5746
    // allows server (or client) to accept ClientHello (or ServerHello)
    // message without the secure renegotiation_info extension or SCSV.
    //
    // For maximum security, RFC 5746 also allows server (or client) to
    // reject such message with a fatal "handshake_failure" alert.
    //
    // By default, allow such legacy hello messages.
    static final boolean allowLegacyHelloMessages =
            Utilities.getBooleanProperty(
                    "sun.security.ssl.allowLegacyHelloMessages", true);

    // registered handshake message actors
    LinkedHashMap<Byte, SSLConsumer>  handshakeConsumers;
    final HashMap<Byte, HandshakeProducer>  handshakeProducers;

    // context
    final SSLContextImpl                    sslContext;
    final TransportContext                  conContext;
    final SSLConfiguration                  sslConfig;

    // consolidated parameters
    final List<ProtocolVersion>             activeProtocols;
    final List<CipherSuite>                 activeCipherSuites;
    final AlgorithmConstraints              algorithmConstraints;
    final ProtocolVersion                   maximumActiveProtocol;

    // output stream
    final HandshakeOutStream                handshakeOutput;

    // handshake transcript hash
    final HandshakeHash                     handshakeHash;

    // negotiated security parameters
    SSLSessionImpl                          handshakeSession;
    boolean                                 handshakeFinished;
    // boolean                                 isInvalidated;

    boolean                                 kickstartMessageDelivered;

    // Resumption
    boolean                                 isResumption;
    SSLSessionImpl                          resumingSession;

    final Queue<Map.Entry<Byte, ByteBuffer>> delegatedActions;
    volatile boolean                        taskDelegated = false;
    volatile Exception                      delegatedThrown = null;

    ProtocolVersion                         negotiatedProtocol;
    CipherSuite                             negotiatedCipherSuite;
    final List<SSLPossession>               handshakePossessions;
    final List<SSLCredentials>              handshakeCredentials;
    SSLKeyDerivation                        handshakeKeyDerivation;
    SSLKeyExchange                          handshakeKeyExchange;
    SecretKey                               baseReadSecret;
    SecretKey                               baseWriteSecret;

    // protocol version being established
    int                                     clientHelloVersion;
    String                                  applicationProtocol;

    RandomCookie                            clientHelloRandom;
    RandomCookie                            serverHelloRandom;
    byte[]                                  certRequestContext;

    ////////////////////
    // Extensions

    // the extensions used in the handshake
    final Map<SSLExtension, SSLExtension.SSLExtensionSpec>
                                            handshakeExtensions;

    // MaxFragmentLength
    int                                     maxFragmentLength;

    // SignatureScheme
    List<SignatureScheme>                   localSupportedSignAlgs;
    List<SignatureScheme>                   peerRequestedSignatureSchemes;
    List<SignatureScheme>                   peerRequestedCertSignSchemes;

    // Known authorities
    X500Principal[]                         peerSupportedAuthorities = null;

    // SupportedGroups
    List<NamedGroup>                        clientRequestedNamedGroups;

    // HelloRetryRequest
    NamedGroup                              serverSelectedNamedGroup;

    // if server name indicator is negotiated
    //
    // May need a public API for the indication in the future.
    List<SNIServerName>                     requestedServerNames;
    SNIServerName                           negotiatedServerName;

    // OCSP Stapling info
    boolean                                 staplingActive = false;

    protected HandshakeContext(SSLContextImpl sslContext,
            TransportContext conContext) throws IOException {
        this.sslContext = sslContext;
        this.conContext = conContext;
        this.sslConfig = (SSLConfiguration)conContext.sslConfig.clone();

        this.activeProtocols = getActiveProtocols(sslConfig.enabledProtocols,
                sslConfig.enabledCipherSuites, sslConfig.algorithmConstraints);
        if (activeProtocols.isEmpty()) {
            throw new SSLHandshakeException(
                "No appropriate protocol (protocol is disabled or " +
                "cipher suites are inappropriate)");
        }

        ProtocolVersion maximumVersion = ProtocolVersion.NONE;
        for (ProtocolVersion pv : this.activeProtocols) {
            if (maximumVersion == ProtocolVersion.NONE ||
                    pv.compare(maximumVersion) > 0) {
                maximumVersion = pv;
            }
        }
        this.maximumActiveProtocol = maximumVersion;
        this.activeCipherSuites = getActiveCipherSuites(this.activeProtocols,
                sslConfig.enabledCipherSuites, sslConfig.algorithmConstraints);
        if (activeCipherSuites.isEmpty()) {
            throw new SSLHandshakeException("No appropriate cipher suite");
        }
        this.algorithmConstraints =
                new SSLAlgorithmConstraints(sslConfig.algorithmConstraints);

        this.handshakeConsumers = new LinkedHashMap<>();
        this.handshakeProducers = new HashMap<>();
        this.handshakeHash = conContext.inputRecord.handshakeHash;
        this.handshakeOutput = new HandshakeOutStream(conContext.outputRecord);

        this.handshakeFinished = false;
        this.kickstartMessageDelivered = false;

        this.delegatedActions = new LinkedList<>();
        this.handshakeExtensions = new HashMap<>();
        this.handshakePossessions = new LinkedList<>();
        this.handshakeCredentials = new LinkedList<>();
        this.requestedServerNames = null;
        this.negotiatedServerName = null;
        this.negotiatedCipherSuite = conContext.cipherSuite;
        initialize();
    }

    /**
     * Constructor for PostHandshakeContext
     */
    HandshakeContext(TransportContext conContext) {
        this.sslContext = conContext.sslContext;
        this.conContext = conContext;
        this.sslConfig = conContext.sslConfig;

        this.negotiatedProtocol = conContext.protocolVersion;
        this.negotiatedCipherSuite = conContext.cipherSuite;
        this.handshakeOutput = new HandshakeOutStream(conContext.outputRecord);
        this.delegatedActions = new LinkedList<>();

        this.handshakeProducers = null;
        this.handshakeHash = null;
        this.activeProtocols = null;
        this.activeCipherSuites = null;
        this.algorithmConstraints = null;
        this.maximumActiveProtocol = null;
        this.handshakeExtensions = Collections.emptyMap();  // Not in TLS13
        this.handshakePossessions = null;
        this.handshakeCredentials = null;
    }

    // Initialize the non-final class variables.
    private void initialize() {
        ProtocolVersion inputHelloVersion;
        ProtocolVersion outputHelloVersion;
        if (conContext.isNegotiated) {
            inputHelloVersion = conContext.protocolVersion;
            outputHelloVersion = conContext.protocolVersion;
        } else {
            if (activeProtocols.contains(ProtocolVersion.SSL20Hello)) {
                inputHelloVersion = ProtocolVersion.SSL20Hello;

                // Per TLS 1.3 protocol, implementation MUST NOT send an SSL
                // version 2.0 compatible CLIENT-HELLO.
                if (maximumActiveProtocol.useTLS13PlusSpec()) {
                    outputHelloVersion = maximumActiveProtocol;
                } else {
                    outputHelloVersion = ProtocolVersion.SSL20Hello;
                }
            } else {
                inputHelloVersion = maximumActiveProtocol;
                outputHelloVersion = maximumActiveProtocol;
            }
        }

        conContext.inputRecord.setHelloVersion(inputHelloVersion);
        conContext.outputRecord.setHelloVersion(outputHelloVersion);

        if (!conContext.isNegotiated) {
            conContext.protocolVersion = maximumActiveProtocol;
        }
        conContext.outputRecord.setVersion(conContext.protocolVersion);
    }

    private static List<ProtocolVersion> getActiveProtocols(
            List<ProtocolVersion> enabledProtocols,
            List<CipherSuite> enabledCipherSuites,
            AlgorithmConstraints algorithmConstraints) {
        boolean enabledSSL20Hello = false;
        ArrayList<ProtocolVersion> protocols = new ArrayList<>(4);
        for (ProtocolVersion protocol : enabledProtocols) {
            if (!enabledSSL20Hello && protocol == ProtocolVersion.SSL20Hello) {
                enabledSSL20Hello = true;
                continue;
            }

            if (!algorithmConstraints.permits(
                    EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                    protocol.name, null)) {
                // Ignore disabled protocol.
                continue;
            }

            boolean found = false;
            Map<NamedGroupType, Boolean> cachedStatus =
                    new EnumMap<>(NamedGroupType.class);
            for (CipherSuite suite : enabledCipherSuites) {
                if (suite.isAvailable() && suite.supports(protocol)) {
                    if (isActivatable(suite,
                            algorithmConstraints, cachedStatus)) {
                        protocols.add(protocol);
                        found = true;
                        break;
                    }
                } else if (SSLLogger.isOn && SSLLogger.isOn("verbose")) {
                    SSLLogger.fine(
                        "Ignore unsupported cipher suite: " + suite +
                             " for " + protocol);
                }
            }

            if (!found && (SSLLogger.isOn) && SSLLogger.isOn("handshake")) {
                SSLLogger.fine(
                    "No available cipher suite for " + protocol);
            }
        }

        if (!protocols.isEmpty()) {
            if (enabledSSL20Hello) {
                protocols.add(ProtocolVersion.SSL20Hello);
            }
            Collections.sort(protocols);
        }

        return Collections.unmodifiableList(protocols);
    }

    private static List<CipherSuite> getActiveCipherSuites(
            List<ProtocolVersion> enabledProtocols,
            List<CipherSuite> enabledCipherSuites,
            AlgorithmConstraints algorithmConstraints) {

        List<CipherSuite> suites = new LinkedList<>();
        if (enabledProtocols != null && !enabledProtocols.isEmpty()) {
            Map<NamedGroupType, Boolean> cachedStatus =
                    new EnumMap<>(NamedGroupType.class);
            for (CipherSuite suite : enabledCipherSuites) {
                if (!suite.isAvailable()) {
                    continue;
                }

                boolean isSupported = false;
                for (ProtocolVersion protocol : enabledProtocols) {
                    if (!suite.supports(protocol)) {
                        continue;
                    }
                    if (isActivatable(suite,
                            algorithmConstraints, cachedStatus)) {
                        suites.add(suite);
                        isSupported = true;
                        break;
                    }
                }

                if (!isSupported &&
                        SSLLogger.isOn && SSLLogger.isOn("verbose")) {
                    SSLLogger.finest(
                            "Ignore unsupported cipher suite: " + suite);
                }
            }
        }

        return Collections.unmodifiableList(suites);
    }

    /**
     * Parse the handshake record and return the contentType
     */
    static byte getHandshakeType(TransportContext conContext,
            Plaintext plaintext) throws IOException {
        //     struct {
        //         HandshakeType msg_type;    /* handshake type */
        //         uint24 length;             /* bytes in message */
        //         select (HandshakeType) {
        //             ...
        //         } body;
        //     } Handshake;

        if (plaintext.contentType != ContentType.HANDSHAKE.id) {
            conContext.fatal(Alert.INTERNAL_ERROR,
                "Unexpected operation for record: " + plaintext.contentType);

            return 0;
        }

        if (plaintext.fragment == null || plaintext.fragment.remaining() < 4) {
            conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Invalid handshake message: insufficient data");

            return 0;
        }

        byte handshakeType = (byte)Record.getInt8(plaintext.fragment);
        int handshakeLen = Record.getInt24(plaintext.fragment);
        if (handshakeLen != plaintext.fragment.remaining()) {
            conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Invalid handshake message: insufficient handshake body");

            return 0;
        }

        return handshakeType;
    }

    void dispatch(byte handshakeType, Plaintext plaintext) throws IOException {
        if (conContext.transport.useDelegatedTask()) {
            boolean hasDelegated = !delegatedActions.isEmpty();
            if (hasDelegated ||
                   (handshakeType != SSLHandshake.FINISHED.id &&
                    handshakeType != SSLHandshake.KEY_UPDATE.id &&
                    handshakeType != SSLHandshake.NEW_SESSION_TICKET.id)) {
                if (!hasDelegated) {
                    taskDelegated = false;
                    delegatedThrown = null;
                }

                // Clone the fragment for delegated actions.
                //
                // The plaintext may share the application buffers.  It is
                // fine to use shared buffers if no delegated actions.
                // However, for delegated actions, the shared buffers may be
                // polluted in application layer before the delegated actions
                // executed.
                ByteBuffer fragment = ByteBuffer.wrap(
                        new byte[plaintext.fragment.remaining()]);
                fragment.put(plaintext.fragment);
                fragment = fragment.rewind();

                delegatedActions.add(new SimpleImmutableEntry<>(
                        handshakeType,
                        fragment
                    ));
            } else {
                dispatch(handshakeType, plaintext.fragment);
            }
        } else {
            dispatch(handshakeType, plaintext.fragment);
        }
    }

    void dispatch(byte handshakeType,
            ByteBuffer fragment) throws IOException {
        SSLConsumer consumer;
        if (handshakeType == SSLHandshake.HELLO_REQUEST.id) {
            // For TLS 1.2 and prior versions, the HelloRequest message MAY
            // be sent by the server at any time.
            consumer = SSLHandshake.HELLO_REQUEST;
        } else {
            consumer = handshakeConsumers.get(handshakeType);
        }

        if (consumer == null) {
            conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Unexpected handshake message: " +
                    SSLHandshake.nameOf(handshakeType));
            return;
        }

        try {
            consumer.consume(this, fragment);
        } catch (UnsupportedOperationException unsoe) {
            conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Unsupported handshake message: " +
                    SSLHandshake.nameOf(handshakeType), unsoe);
        }

        // update handshake hash after handshake message consumption.
        handshakeHash.consume();
    }

    abstract void kickstart() throws IOException;

    /**
     * Check if the given cipher suite is enabled and available within
     * the current active cipher suites.
     *
     * Does not check if the required server certificates are available.
     */
    boolean isNegotiable(CipherSuite cs) {
        return isNegotiable(activeCipherSuites, cs);
    }

    /**
     * Check if the given cipher suite is enabled and available within
     * the proposed cipher suite list.
     *
     * Does not check if the required server certificates are available.
     */
    static final boolean isNegotiable(
            List<CipherSuite> proposed, CipherSuite cs) {
        return proposed.contains(cs) && cs.isNegotiable();
    }

    /**
     * Check if the given cipher suite is enabled and available within
     * the proposed cipher suite list and specific protocol version.
     *
     * Does not check if the required server certificates are available.
     */
    static final boolean isNegotiable(List<CipherSuite> proposed,
            ProtocolVersion protocolVersion, CipherSuite cs) {
        return proposed.contains(cs) &&
                cs.isNegotiable() && cs.supports(protocolVersion);
    }

    /**
     * Check if the given protocol version is enabled and available.
     */
    boolean isNegotiable(ProtocolVersion protocolVersion) {
        return activeProtocols.contains(protocolVersion);
    }

    /**
     * Set the active protocol version and propagate it to the SSLSocket
     * and our handshake streams. Called from ClientHandshaker
     * and ServerHandshaker with the negotiated protocol version.
     */
    void setVersion(ProtocolVersion protocolVersion) {
        this.conContext.protocolVersion = protocolVersion;
    }

    private static boolean isActivatable(CipherSuite suite,
            AlgorithmConstraints algorithmConstraints,
            Map<NamedGroupType, Boolean> cachedStatus) {

        if (algorithmConstraints.permits(
                EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), suite.name, null)) {
            if (suite.keyExchange == null) {
                // TLS 1.3, no definition of key exchange in cipher suite.
                return true;
            }

            boolean available;
            NamedGroupType groupType = suite.keyExchange.groupType;
            if (groupType != NAMED_GROUP_NONE) {
                Boolean checkedStatus = cachedStatus.get(groupType);
                if (checkedStatus == null) {
                    available = SupportedGroups.isActivatable(
                            algorithmConstraints, groupType);
                    cachedStatus.put(groupType, available);

                    if (!available &&
                            SSLLogger.isOn && SSLLogger.isOn("verbose")) {
                        SSLLogger.fine("No activated named group");
                    }
                } else {
                    available = checkedStatus;
                }

                if (!available && SSLLogger.isOn && SSLLogger.isOn("verbose")) {
                    SSLLogger.fine(
                        "No active named group, ignore " + suite);
                }
                return available;
            } else {
                return true;
            }
        } else if (SSLLogger.isOn && SSLLogger.isOn("verbose")) {
            SSLLogger.fine("Ignore disabled cipher suite: " + suite);
        }

        return false;
    }

    List<SNIServerName> getRequestedServerNames() {
        if (requestedServerNames == null) {
            return Collections.<SNIServerName>emptyList();
        }
        return requestedServerNames;
    }
}

