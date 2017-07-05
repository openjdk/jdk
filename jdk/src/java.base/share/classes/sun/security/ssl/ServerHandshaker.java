/*
 * Copyright (c) 1996, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.TimeUnit;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.*;
import java.security.spec.ECParameterSpec;
import java.math.BigInteger;

import javax.crypto.SecretKey;
import javax.net.ssl.*;

import sun.security.action.GetLongAction;
import sun.security.util.KeyUtil;
import sun.security.util.LegacyAlgorithmConstraints;
import sun.security.action.GetPropertyAction;
import sun.security.ssl.HandshakeMessage.*;
import sun.security.ssl.CipherSuite.*;
import sun.security.ssl.SignatureAndHashAlgorithm.*;
import static sun.security.ssl.CipherSuite.KeyExchange.*;

/**
 * ServerHandshaker does the protocol handshaking from the point
 * of view of a server.  It is driven asychronously by handshake messages
 * as delivered by the parent Handshaker class, and also uses
 * common functionality (e.g. key generation) that is provided there.
 *
 * @author David Brownell
 */
final class ServerHandshaker extends Handshaker {

    // The default number of milliseconds the handshaker will wait for
    // revocation status responses.
    private static final long DEFAULT_STATUS_RESP_DELAY = 5000;

    // is the server going to require the client to authenticate?
    private ClientAuthType      doClientAuth;

    // our authentication info
    private X509Certificate[]   certs;
    private PrivateKey          privateKey;

    private Object              serviceCreds;

    // flag to check for clientCertificateVerify message
    private boolean             needClientVerify = false;

    /*
     * For exportable ciphersuites using non-exportable key sizes, we use
     * ephemeral RSA keys. We could also do anonymous RSA in the same way
     * but there are no such ciphersuites currently defined.
     */
    private PrivateKey          tempPrivateKey;
    private PublicKey           tempPublicKey;

    /*
     * For anonymous and ephemeral Diffie-Hellman key exchange, we use
     * ephemeral Diffie-Hellman keys.
     */
    private DHCrypt dh;

    // Helper for ECDH based key exchanges
    private ECDHCrypt ecdh;

    // version request by the client in its ClientHello
    // we remember it for the RSA premaster secret version check
    private ProtocolVersion clientRequestedVersion;

    private SupportedEllipticCurvesExtension supportedCurves;

    // the preferable signature algorithm used by ServerKeyExchange message
    SignatureAndHashAlgorithm preferableSignatureAlgorithm;

    // Flag to use smart ephemeral DH key which size matches the corresponding
    // authentication key
    private static final boolean useSmartEphemeralDHKeys;

    // Flag to use legacy ephemeral DH key which size is 512 bits for
    // exportable cipher suites, and 768 bits for others
    private static final boolean useLegacyEphemeralDHKeys;

    // The customized ephemeral DH key size for non-exportable cipher suites.
    private static final int customizedDHKeySize;

    // legacy algorithm constraints
    private static final AlgorithmConstraints legacyAlgorithmConstraints =
            new LegacyAlgorithmConstraints(
                    LegacyAlgorithmConstraints.PROPERTY_TLS_LEGACY_ALGS,
                    new SSLAlgorithmDecomposer());

    private long statusRespTimeout;

    static {
        String property =
                GetPropertyAction.getProperty("jdk.tls.ephemeralDHKeySize");
        if (property == null || property.length() == 0) {
            useLegacyEphemeralDHKeys = false;
            useSmartEphemeralDHKeys = false;
            customizedDHKeySize = -1;
        } else if ("matched".equals(property)) {
            useLegacyEphemeralDHKeys = false;
            useSmartEphemeralDHKeys = true;
            customizedDHKeySize = -1;
        } else if ("legacy".equals(property)) {
            useLegacyEphemeralDHKeys = true;
            useSmartEphemeralDHKeys = false;
            customizedDHKeySize = -1;
        } else {
            useLegacyEphemeralDHKeys = false;
            useSmartEphemeralDHKeys = false;

            try {
                // DH parameter generation can be extremely slow, best to
                // use one of the supported pre-computed DH parameters
                // (see DHCrypt class).
                customizedDHKeySize = Integer.parseUnsignedInt(property);
                if (customizedDHKeySize < 1024 || customizedDHKeySize > 8192 ||
                        (customizedDHKeySize & 0x3f) != 0) {
                    throw new IllegalArgumentException(
                        "Unsupported customized DH key size: " +
                        customizedDHKeySize + ". " +
                        "The key size must be multiple of 64, " +
                        "and can only range from 1024 to 8192 (inclusive)");
                }
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(
                        "Invalid system property jdk.tls.ephemeralDHKeySize");
            }
        }
    }

    /*
     * Constructor ... use the keys found in the auth context.
     */
    ServerHandshaker(SSLSocketImpl socket, SSLContextImpl context,
            ProtocolList enabledProtocols, ClientAuthType clientAuth,
            ProtocolVersion activeProtocolVersion, boolean isInitialHandshake,
            boolean secureRenegotiation,
            byte[] clientVerifyData, byte[] serverVerifyData) {

        super(socket, context, enabledProtocols,
                (clientAuth != ClientAuthType.CLIENT_AUTH_NONE), false,
                activeProtocolVersion, isInitialHandshake, secureRenegotiation,
                clientVerifyData, serverVerifyData);
        doClientAuth = clientAuth;
        statusRespTimeout = AccessController.doPrivileged(
                    new GetLongAction("jdk.tls.stapling.responseTimeout",
                        DEFAULT_STATUS_RESP_DELAY));
        statusRespTimeout = statusRespTimeout >= 0 ? statusRespTimeout :
                DEFAULT_STATUS_RESP_DELAY;
    }

    /*
     * Constructor ... use the keys found in the auth context.
     */
    ServerHandshaker(SSLEngineImpl engine, SSLContextImpl context,
            ProtocolList enabledProtocols, ClientAuthType clientAuth,
            ProtocolVersion activeProtocolVersion,
            boolean isInitialHandshake, boolean secureRenegotiation,
            byte[] clientVerifyData, byte[] serverVerifyData,
            boolean isDTLS) {

        super(engine, context, enabledProtocols,
                (clientAuth != ClientAuthType.CLIENT_AUTH_NONE), false,
                activeProtocolVersion, isInitialHandshake, secureRenegotiation,
                clientVerifyData, serverVerifyData, isDTLS);
        doClientAuth = clientAuth;
        statusRespTimeout = AccessController.doPrivileged(
                    new GetLongAction("jdk.tls.stapling.responseTimeout",
                        DEFAULT_STATUS_RESP_DELAY));
        statusRespTimeout = statusRespTimeout >= 0 ? statusRespTimeout :
                DEFAULT_STATUS_RESP_DELAY;
    }

    /*
     * As long as handshaking has not started, we can change
     * whether client authentication is required.  Otherwise,
     * we will need to wait for the next handshake.
     */
    void setClientAuth(ClientAuthType clientAuth) {
        doClientAuth = clientAuth;
    }

    /*
     * This routine handles all the server side handshake messages, one at
     * a time.  Given the message type (and in some cases the pending cipher
     * spec) it parses the type-specific message.  Then it calls a function
     * that handles that specific message.
     *
     * It updates the state machine as each message is processed, and writes
     * responses as needed using the connection in the constructor.
     */
    @Override
    void processMessage(byte type, int message_len)
            throws IOException {

        // check the handshake state
        handshakeState.check(type);

        switch (type) {
            case HandshakeMessage.ht_client_hello:
                ClientHello ch = new ClientHello(input, message_len, isDTLS);
                handshakeState.update(ch, resumingSession);

                /*
                 * send it off for processing.
                 */
                this.clientHello(ch);
                break;

            case HandshakeMessage.ht_certificate:
                if (doClientAuth == ClientAuthType.CLIENT_AUTH_NONE) {
                    fatalSE(Alerts.alert_unexpected_message,
                                "client sent unsolicited cert chain");
                    // NOTREACHED
                }
                CertificateMsg certificateMsg = new CertificateMsg(input);
                handshakeState.update(certificateMsg, resumingSession);
                this.clientCertificate(certificateMsg);
                break;

            case HandshakeMessage.ht_client_key_exchange:
                SecretKey preMasterSecret;
                switch (keyExchange) {
                case K_RSA:
                case K_RSA_EXPORT:
                    /*
                     * The client's pre-master secret is decrypted using
                     * either the server's normal private RSA key, or the
                     * temporary one used for non-export or signing-only
                     * certificates/keys.
                     */
                    RSAClientKeyExchange pms = new RSAClientKeyExchange(
                            protocolVersion, clientRequestedVersion,
                            sslContext.getSecureRandom(), input,
                            message_len, privateKey);
                    handshakeState.update(pms, resumingSession);
                    preMasterSecret = this.clientKeyExchange(pms);
                    break;
                case K_DHE_RSA:
                case K_DHE_DSS:
                case K_DH_ANON:
                    /*
                     * The pre-master secret is derived using the normal
                     * Diffie-Hellman calculation.   Note that the main
                     * protocol difference in these five flavors is in how
                     * the ServerKeyExchange message was constructed!
                     */
                    DHClientKeyExchange dhcke = new DHClientKeyExchange(input);
                    handshakeState.update(dhcke, resumingSession);
                    preMasterSecret = this.clientKeyExchange(dhcke);
                    break;
                case K_ECDH_RSA:
                case K_ECDH_ECDSA:
                case K_ECDHE_RSA:
                case K_ECDHE_ECDSA:
                case K_ECDH_ANON:
                    ECDHClientKeyExchange ecdhcke =
                                    new ECDHClientKeyExchange(input);
                    handshakeState.update(ecdhcke, resumingSession);
                    preMasterSecret = this.clientKeyExchange(ecdhcke);
                    break;
                default:
                    ClientKeyExchangeService p =
                            ClientKeyExchangeService.find(keyExchange.name);
                    if (p == null) {
                        throw new SSLProtocolException
                                ("Unrecognized key exchange: " + keyExchange);
                    }
                    byte[] encodedTicket = input.getBytes16();
                    input.getBytes16();
                    byte[] secret = input.getBytes16();
                    ClientKeyExchange cke = p.createServerExchange(protocolVersion,
                            clientRequestedVersion,
                            sslContext.getSecureRandom(),
                            encodedTicket,
                            secret,
                            this.getAccSE(), serviceCreds);
                    handshakeState.update(cke, resumingSession);
                    preMasterSecret = this.clientKeyExchange(cke);
                    break;
                }

                //
                // All keys are calculated from the premaster secret
                // and the exchanged nonces in the same way.
                //
                calculateKeys(preMasterSecret, clientRequestedVersion);
                break;

            case HandshakeMessage.ht_certificate_verify:
                CertificateVerify cvm =
                        new CertificateVerify(input,
                            getLocalSupportedSignAlgs(), protocolVersion);
                handshakeState.update(cvm, resumingSession);
                this.clientCertificateVerify(cvm);

                break;

            case HandshakeMessage.ht_finished:
                Finished cfm =
                    new Finished(protocolVersion, input, cipherSuite);
                handshakeState.update(cfm, resumingSession);
                this.clientFinished(cfm);

                break;

            default:
                throw new SSLProtocolException(
                        "Illegal server handshake msg, " + type);
        }

    }


    /*
     * ClientHello presents the server with a bunch of options, to which the
     * server replies with a ServerHello listing the ones which this session
     * will use.  If needed, it also writes its Certificate plus in some cases
     * a ServerKeyExchange message.  It may also write a CertificateRequest,
     * to elicit a client certificate.
     *
     * All these messages are terminated by a ServerHelloDone message.  In
     * most cases, all this can be sent in a single Record.
     */
    private void clientHello(ClientHello mesg) throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        // Reject client initiated renegotiation?
        //
        // If server side should reject client-initiated renegotiation,
        // send an alert_handshake_failure fatal alert, not a no_renegotiation
        // warning alert (no_renegotiation must be a warning: RFC 2246).
        // no_renegotiation might seem more natural at first, but warnings
        // are not appropriate because the sending party does not know how
        // the receiving party will behave.  This state must be treated as
        // a fatal server condition.
        //
        // This will not have any impact on server initiated renegotiation.
        if (rejectClientInitiatedRenego && !isInitialHandshake &&
                !serverHelloRequested) {
            fatalSE(Alerts.alert_handshake_failure,
                "Client initiated renegotiation is not allowed");
        }

        // check the server name indication if required
        ServerNameExtension clientHelloSNIExt = (ServerNameExtension)
                    mesg.extensions.get(ExtensionType.EXT_SERVER_NAME);
        if (!sniMatchers.isEmpty()) {
            // we do not reject client without SNI extension
            if (clientHelloSNIExt != null &&
                        !clientHelloSNIExt.isMatched(sniMatchers)) {
                fatalSE(Alerts.alert_unrecognized_name,
                    "Unrecognized server name indication");
            }
        }

        // Does the message include security renegotiation indication?
        boolean renegotiationIndicated = false;

        // check the TLS_EMPTY_RENEGOTIATION_INFO_SCSV
        CipherSuiteList cipherSuites = mesg.getCipherSuites();
        if (cipherSuites.contains(CipherSuite.C_SCSV)) {
            renegotiationIndicated = true;
            if (isInitialHandshake) {
                secureRenegotiation = true;
            } else {
                // abort the handshake with a fatal handshake_failure alert
                if (secureRenegotiation) {
                    fatalSE(Alerts.alert_handshake_failure,
                        "The SCSV is present in a secure renegotiation");
                } else {
                    fatalSE(Alerts.alert_handshake_failure,
                        "The SCSV is present in a insecure renegotiation");
                }
            }
        }

        // check the "renegotiation_info" extension
        RenegotiationInfoExtension clientHelloRI = (RenegotiationInfoExtension)
                    mesg.extensions.get(ExtensionType.EXT_RENEGOTIATION_INFO);
        if (clientHelloRI != null) {
            renegotiationIndicated = true;
            if (isInitialHandshake) {
                // verify the length of the "renegotiated_connection" field
                if (!clientHelloRI.isEmpty()) {
                    // abort the handshake with a fatal handshake_failure alert
                    fatalSE(Alerts.alert_handshake_failure,
                        "The renegotiation_info field is not empty");
                }

                secureRenegotiation = true;
            } else {
                if (!secureRenegotiation) {
                    // unexpected RI extension for insecure renegotiation,
                    // abort the handshake with a fatal handshake_failure alert
                    fatalSE(Alerts.alert_handshake_failure,
                        "The renegotiation_info is present in a insecure " +
                        "renegotiation");
                }

                // verify the client_verify_data value
                if (!MessageDigest.isEqual(clientVerifyData,
                                clientHelloRI.getRenegotiatedConnection())) {
                    fatalSE(Alerts.alert_handshake_failure,
                        "Incorrect verify data in ClientHello " +
                        "renegotiation_info message");
                }
            }
        } else if (!isInitialHandshake && secureRenegotiation) {
           // if the connection's "secure_renegotiation" flag is set to TRUE
           // and the "renegotiation_info" extension is not present, abort
           // the handshake.
            fatalSE(Alerts.alert_handshake_failure,
                        "Inconsistent secure renegotiation indication");
        }

        // if there is no security renegotiation indication or the previous
        // handshake is insecure.
        if (!renegotiationIndicated || !secureRenegotiation) {
            if (isInitialHandshake) {
                if (!allowLegacyHelloMessages) {
                    // abort the handshake with a fatal handshake_failure alert
                    fatalSE(Alerts.alert_handshake_failure,
                        "Failed to negotiate the use of secure renegotiation");
                }

                // continue with legacy ClientHello
                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println("Warning: No renegotiation " +
                        "indication in ClientHello, allow legacy ClientHello");
                }
            } else if (!allowUnsafeRenegotiation) {
                // abort the handshake
                if (activeProtocolVersion.useTLS10PlusSpec()) {
                    // respond with a no_renegotiation warning
                    warningSE(Alerts.alert_no_renegotiation);

                    // invalidate the handshake so that the caller can
                    // dispose this object.
                    invalidated = true;

                    // If there is still unread block in the handshake
                    // input stream, it would be truncated with the disposal
                    // and the next handshake message will become incomplete.
                    //
                    // However, according to SSL/TLS specifications, no more
                    // handshake message could immediately follow ClientHello
                    // or HelloRequest. But in case of any improper messages,
                    // we'd better check to ensure there is no remaining bytes
                    // in the handshake input stream.
                    if (input.available() > 0) {
                        fatalSE(Alerts.alert_unexpected_message,
                            "ClientHello followed by an unexpected  " +
                            "handshake message");
                    }

                    return;
                } else {
                    // For SSLv3, send the handshake_failure fatal error.
                    // Note that SSLv3 does not define a no_renegotiation
                    // alert like TLSv1. However we cannot ignore the message
                    // simply, otherwise the other side was waiting for a
                    // response that would never come.
                    fatalSE(Alerts.alert_handshake_failure,
                        "Renegotiation is not allowed");
                }
            } else {   // !isInitialHandshake && allowUnsafeRenegotiation
                // continue with unsafe renegotiation.
                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println(
                            "Warning: continue with insecure renegotiation");
                }
            }
        }

        // check the "max_fragment_length" extension
        MaxFragmentLengthExtension maxFragLenExt = (MaxFragmentLengthExtension)
                    mesg.extensions.get(ExtensionType.EXT_MAX_FRAGMENT_LENGTH);
        if ((maxFragLenExt != null) && (maximumPacketSize != 0)) {
            // Not yet consider the impact of IV/MAC/padding.
            int estimatedMaxFragSize = maximumPacketSize;
            if (isDTLS) {
                estimatedMaxFragSize -= DTLSRecord.headerSize;
            } else {
                estimatedMaxFragSize -= SSLRecord.headerSize;
            }

            if (maxFragLenExt.getMaxFragLen() > estimatedMaxFragSize) {
                // For better interoperability, abort the maximum fragment
                // length negotiation, rather than terminate the connection
                // with a fatal alert.
                maxFragLenExt = null;

                // fatalSE(Alerts.alert_illegal_parameter,
                //         "Not an allowed max_fragment_length value");
            }
        }

        // check the ALPN extension
        ALPNExtension clientHelloALPN = (ALPNExtension)
            mesg.extensions.get(ExtensionType.EXT_ALPN);

        if ((clientHelloALPN != null) && (localApl.length > 0)) {

            // Intersect the requested and the locally supported,
            // and save for later.
            String negotiatedValue = null;
            List<String> protocols = clientHelloALPN.getPeerAPs();

            // Use server preference order
            for (String ap : localApl) {
                if (protocols.contains(ap)) {
                    negotiatedValue = ap;
                    break;
                }
            }

            if (negotiatedValue == null) {
                fatalSE(Alerts.alert_no_application_protocol,
                    new SSLHandshakeException(
                        "No matching ALPN values"));
            }
            applicationProtocol = negotiatedValue;

        } else {
            applicationProtocol = "";
        }

        // cookie exchange
        if (isDTLS) {
             HelloCookieManager hcMgr = sslContext.getHelloCookieManager();
             if ((mesg.cookie == null) || (mesg.cookie.length == 0) ||
                    (!hcMgr.isValid(mesg))) {

                //
                // Perform cookie exchange for DTLS handshaking if no cookie
                // or the cookie is invalid in the ClientHello message.
                //
                HelloVerifyRequest m0 = new HelloVerifyRequest(hcMgr, mesg);

                if (debug != null && Debug.isOn("handshake")) {
                    m0.print(System.out);
                }

                m0.write(output);
                handshakeState.update(m0, resumingSession);
                output.flush();

                return;
            }
        }

        /*
         * FIRST, construct the ServerHello using the options and priorities
         * from the ClientHello.  Update the (pending) cipher spec as we do
         * so, and save the client's version to protect against rollback
         * attacks.
         *
         * There are a bunch of minor tasks here, and one major one: deciding
         * if the short or the full handshake sequence will be used.
         */
        ServerHello m1 = new ServerHello();

        clientRequestedVersion = mesg.protocolVersion;

        // select a proper protocol version.
        ProtocolVersion selectedVersion =
               selectProtocolVersion(clientRequestedVersion);
        if (selectedVersion == null ||
                selectedVersion.v == ProtocolVersion.SSL20Hello.v) {
            fatalSE(Alerts.alert_handshake_failure,
                "Client requested protocol " + clientRequestedVersion +
                " not enabled or not supported");
        }

        handshakeHash.protocolDetermined(selectedVersion);
        setVersion(selectedVersion);

        m1.protocolVersion = protocolVersion;

        //
        // random ... save client and server values for later use
        // in computing the master secret (from pre-master secret)
        // and thence the other crypto keys.
        //
        // NOTE:  this use of three inputs to generating _each_ set
        // of ciphers slows things down, but it does increase the
        // security since each connection in the session can hold
        // its own authenticated (and strong) keys.  One could make
        // creation of a session a rare thing...
        //
        clnt_random = mesg.clnt_random;
        svr_random = new RandomCookie(sslContext.getSecureRandom());
        m1.svr_random = svr_random;

        session = null; // forget about the current session
        //
        // Here we go down either of two paths:  (a) the fast one, where
        // the client's asked to rejoin an existing session, and the server
        // permits this; (b) the other one, where a new session is created.
        //
        if (mesg.sessionId.length() != 0) {
            // client is trying to resume a session, let's see...

            SSLSessionImpl previous = ((SSLSessionContextImpl)sslContext
                        .engineGetServerSessionContext())
                        .get(mesg.sessionId.getId());
            //
            // Check if we can use the fast path, resuming a session.  We
            // can do so iff we have a valid record for that session, and
            // the cipher suite for that session was on the list which the
            // client requested, and if we're not forgetting any needed
            // authentication on the part of the client.
            //
            if (previous != null) {
                resumingSession = previous.isRejoinable();

                if (resumingSession) {
                    ProtocolVersion oldVersion = previous.getProtocolVersion();
                    // cannot resume session with different version
                    if (oldVersion != protocolVersion) {
                        resumingSession = false;
                    }
                }

                // cannot resume session with different server name indication
                if (resumingSession) {
                    List<SNIServerName> oldServerNames =
                            previous.getRequestedServerNames();
                    if (clientHelloSNIExt != null) {
                        if (!clientHelloSNIExt.isIdentical(oldServerNames)) {
                            resumingSession = false;
                        }
                    } else if (!oldServerNames.isEmpty()) {
                        resumingSession = false;
                    }

                    if (!resumingSession &&
                            debug != null && Debug.isOn("handshake")) {
                        System.out.println(
                            "The requested server name indication " +
                            "is not identical to the previous one");
                    }
                }

                if (resumingSession &&
                        (doClientAuth == ClientAuthType.CLIENT_AUTH_REQUIRED)) {
                    try {
                        previous.getPeerPrincipal();
                    } catch (SSLPeerUnverifiedException e) {
                        resumingSession = false;
                    }
                }

                // validate subject identity
                if (resumingSession) {
                    CipherSuite suite = previous.getSuite();
                    ClientKeyExchangeService p =
                            ClientKeyExchangeService.find(suite.keyExchange.name);
                    if (p != null) {
                        Principal localPrincipal = previous.getLocalPrincipal();

                        if (p.isRelated(
                                false, getAccSE(), localPrincipal)) {
                            if (debug != null && Debug.isOn("session"))
                                System.out.println("Subject can" +
                                        " provide creds for princ");
                        } else {
                            resumingSession = false;
                            if (debug != null && Debug.isOn("session"))
                                System.out.println("Subject cannot" +
                                        " provide creds for princ");
                        }
                    }
                }

                if (resumingSession) {
                    CipherSuite suite = previous.getSuite();
                    // verify that the ciphersuite from the cached session
                    // is in the list of client requested ciphersuites and
                    // we have it enabled
                    if ((isNegotiable(suite) == false) ||
                            (mesg.getCipherSuites().contains(suite) == false)) {
                        resumingSession = false;
                    } else {
                        // everything looks ok, set the ciphersuite
                        // this should be done last when we are sure we
                        // will resume
                        setCipherSuite(suite);
                    }
                }

                if (resumingSession) {
                    session = previous;
                    if (debug != null &&
                        (Debug.isOn("handshake") || Debug.isOn("session"))) {
                        System.out.println("%% Resuming " + session);
                    }
                }
            }
        }   // else client did not try to resume

        //
        // If client hasn't specified a session we can resume, start a
        // new one and choose its cipher suite and compression options.
        // Unless new session creation is disabled for this connection!
        //
        if (session == null) {
            if (!enableNewSession) {
                throw new SSLException("Client did not resume a session");
            }

            supportedCurves = (SupportedEllipticCurvesExtension)
                        mesg.extensions.get(ExtensionType.EXT_ELLIPTIC_CURVES);

            // We only need to handle the "signature_algorithm" extension
            // for full handshakes and TLS 1.2 or later.
            if (protocolVersion.useTLS12PlusSpec()) {
                SignatureAlgorithmsExtension signAlgs =
                    (SignatureAlgorithmsExtension)mesg.extensions.get(
                                    ExtensionType.EXT_SIGNATURE_ALGORITHMS);
                if (signAlgs != null) {
                    Collection<SignatureAndHashAlgorithm> peerSignAlgs =
                                            signAlgs.getSignAlgorithms();
                    if (peerSignAlgs == null || peerSignAlgs.isEmpty()) {
                        throw new SSLHandshakeException(
                            "No peer supported signature algorithms");
                    }

                    Collection<SignatureAndHashAlgorithm>
                        supportedPeerSignAlgs =
                            SignatureAndHashAlgorithm.getSupportedAlgorithms(
                                algorithmConstraints, peerSignAlgs);
                    if (supportedPeerSignAlgs.isEmpty()) {
                        throw new SSLHandshakeException(
                            "No signature and hash algorithm in common");
                    }

                    setPeerSupportedSignAlgs(supportedPeerSignAlgs);
                } // else, need to use peer implicit supported signature algs
            }

            session = new SSLSessionImpl(protocolVersion, CipherSuite.C_NULL,
                        getLocalSupportedSignAlgs(),
                        sslContext.getSecureRandom(),
                        getHostAddressSE(), getPortSE());

            if (protocolVersion.useTLS12PlusSpec()) {
                if (peerSupportedSignAlgs != null) {
                    session.setPeerSupportedSignatureAlgorithms(
                            peerSupportedSignAlgs);
                }   // else, we will set the implicit peer supported signature
                    // algorithms in chooseCipherSuite()
            }

            // set the server name indication in the session
            List<SNIServerName> clientHelloSNI =
                    Collections.<SNIServerName>emptyList();
            if (clientHelloSNIExt != null) {
                clientHelloSNI = clientHelloSNIExt.getServerNames();
            }
            session.setRequestedServerNames(clientHelloSNI);

            // set the handshake session
            setHandshakeSessionSE(session);

            // choose cipher suite and corresponding private key
            chooseCipherSuite(mesg);

            session.setSuite(cipherSuite);
            session.setLocalPrivateKey(privateKey);

            // chooseCompression(mesg);

            // set the negotiated maximum fragment in the session
            //
            // The protocol version and cipher suite have been negotiated
            // in previous processes.
            if (maxFragLenExt != null) {
                int maxFragLen = maxFragLenExt.getMaxFragLen();

                // More check of the requested "max_fragment_length" extension.
                if (maximumPacketSize != 0) {
                    int estimatedMaxFragSize = cipherSuite.calculatePacketSize(
                            maxFragLen, protocolVersion, isDTLS);
                    if (estimatedMaxFragSize > maximumPacketSize) {
                        // For better interoperability, abort the maximum
                        // fragment length negotiation, rather than terminate
                        // the connection with a fatal alert.
                        maxFragLenExt = null;

                        // fatalSE(Alerts.alert_illegal_parameter,
                        //         "Not an allowed max_fragment_length value");
                    }
                }

                if (maxFragLenExt != null) {
                    session.setNegotiatedMaxFragSize(maxFragLen);
                }
            }

            session.setMaximumPacketSize(maximumPacketSize);
        } else {
            // set the handshake session
            setHandshakeSessionSE(session);
        }

        if (protocolVersion.useTLS12PlusSpec()) {
            handshakeHash.setFinishedAlg(cipherSuite.prfAlg.getPRFHashAlg());
        }

        m1.cipherSuite = cipherSuite;
        m1.sessionId = session.getSessionId();
        m1.compression_method = session.getCompression();

        if (secureRenegotiation) {
            // For ServerHellos that are initial handshakes, then the
            // "renegotiated_connection" field in "renegotiation_info"
            // extension is of zero length.
            //
            // For ServerHellos that are renegotiating, this field contains
            // the concatenation of client_verify_data and server_verify_data.
            //
            // Note that for initial handshakes, both the clientVerifyData
            // variable and serverVerifyData variable are of zero length.
            HelloExtension serverHelloRI = new RenegotiationInfoExtension(
                                        clientVerifyData, serverVerifyData);
            m1.extensions.add(serverHelloRI);
        }

        if (!sniMatchers.isEmpty() && clientHelloSNIExt != null) {
            // When resuming a session, the server MUST NOT include a
            // server_name extension in the server hello.
            if (!resumingSession) {
                ServerNameExtension serverHelloSNI = new ServerNameExtension();
                m1.extensions.add(serverHelloSNI);
            }
        }

        if ((maxFragLenExt != null) && !resumingSession) {
            // When resuming a session, the server MUST NOT include a
            // max_fragment_length extension in the server hello.
            //
            // Otherwise, use the same value as the requested extension.
            m1.extensions.add(maxFragLenExt);
        }

        StaplingParameters staplingParams = processStapling(mesg);
        if (staplingParams != null) {
            // We now can safely assert status_request[_v2] in our
            // ServerHello, and know for certain that we can provide
            // responses back to this client for this connection.
            if (staplingParams.statusRespExt ==
                    ExtensionType.EXT_STATUS_REQUEST) {
                m1.extensions.add(new CertStatusReqExtension());
            } else if (staplingParams.statusRespExt ==
                    ExtensionType.EXT_STATUS_REQUEST_V2) {
                m1.extensions.add(new CertStatusReqListV2Extension());
            }
        }

        // Prepare the ALPN response
        if (applicationProtocol != null && !applicationProtocol.isEmpty()) {
            m1.extensions.add(new ALPNExtension(applicationProtocol));
        }

        if (debug != null && Debug.isOn("handshake")) {
            m1.print(System.out);
            System.out.println("Cipher suite:  " + session.getSuite());
        }
        m1.write(output);
        handshakeState.update(m1, resumingSession);

        //
        // If we are resuming a session, we finish writing handshake
        // messages right now and then finish.
        //
        if (resumingSession) {
            calculateConnectionKeys(session.getMasterSecret());
            sendChangeCipherAndFinish(false);

            // expecting the final ChangeCipherSpec and Finished messages
            expectingFinishFlightSE();

            return;
        }


        /*
         * SECOND, write the server Certificate(s) if we need to.
         *
         * NOTE:  while an "anonymous RSA" mode is explicitly allowed by
         * the protocol, we can't support it since all of the SSL flavors
         * defined in the protocol spec are explicitly stated to require
         * using RSA certificates.
         */
        if (ClientKeyExchangeService.find(cipherSuite.keyExchange.name) != null) {
            // No external key exchange provider needs a cert now.
        } else if ((keyExchange != K_DH_ANON) && (keyExchange != K_ECDH_ANON)) {
            if (certs == null) {
                throw new RuntimeException("no certificates");
            }

            CertificateMsg m2 = new CertificateMsg(certs);

            /*
             * Set local certs in the SSLSession, output
             * debug info, and then actually write to the client.
             */
            session.setLocalCertificates(certs);
            if (debug != null && Debug.isOn("handshake")) {
                m2.print(System.out);
            }
            m2.write(output);
            handshakeState.update(m2, resumingSession);

            // XXX has some side effects with OS TCP buffering,
            // leave it out for now

            // let client verify chain in the meantime...
            // output.flush();
        } else {
            if (certs != null) {
                throw new RuntimeException("anonymous keyexchange with certs");
            }
        }

        /**
         * The CertificateStatus message ... only if it is needed.
         * This would only be needed if we've established that this handshake
         * supports status stapling and there is at least one response to
         * return to the client.
         */
        if (staplingParams != null) {
            CertificateStatus csMsg = new CertificateStatus(
                    staplingParams.statReqType, certs,
                    staplingParams.responseMap);
            if (debug != null && Debug.isOn("handshake")) {
                csMsg.print(System.out);
            }
            csMsg.write(output);
            handshakeState.update(csMsg, resumingSession);
        }

        /*
         * THIRD, the ServerKeyExchange message ... iff it's needed.
         *
         * It's usually needed unless there's an encryption-capable
         * RSA cert, or a D-H cert.  The notable exception is that
         * exportable ciphers used with big RSA keys need to downgrade
         * to use short RSA keys, even when the key/cert encrypts OK.
         */

        ServerKeyExchange m3;
        switch (keyExchange) {
        case K_RSA:
            // no server key exchange for RSA ciphersuites
            m3 = null;
            break;
        case K_RSA_EXPORT:
            if (JsseJce.getRSAKeyLength(certs[0].getPublicKey()) > 512) {
                try {
                    m3 = new RSA_ServerKeyExchange(
                        tempPublicKey, privateKey,
                        clnt_random, svr_random,
                        sslContext.getSecureRandom());
                    privateKey = tempPrivateKey;
                } catch (GeneralSecurityException e) {
                    m3 = null; // make compiler happy
                    throw new SSLException(
                            "Error generating RSA server key exchange", e);
                }
            } else {
                // RSA_EXPORT with short key, don't need ServerKeyExchange
                m3 = null;
            }
            break;
        case K_DHE_RSA:
        case K_DHE_DSS:
            try {
                m3 = new DH_ServerKeyExchange(dh,
                    privateKey,
                    clnt_random.random_bytes,
                    svr_random.random_bytes,
                    sslContext.getSecureRandom(),
                    preferableSignatureAlgorithm,
                    protocolVersion);
            } catch (GeneralSecurityException e) {
                m3 = null; // make compiler happy
                throw new SSLException(
                        "Error generating DH server key exchange", e);
            }
            break;
        case K_DH_ANON:
            m3 = new DH_ServerKeyExchange(dh, protocolVersion);
            break;
        case K_ECDHE_RSA:
        case K_ECDHE_ECDSA:
        case K_ECDH_ANON:
            try {
                m3 = new ECDH_ServerKeyExchange(ecdh,
                    privateKey,
                    clnt_random.random_bytes,
                    svr_random.random_bytes,
                    sslContext.getSecureRandom(),
                    preferableSignatureAlgorithm,
                    protocolVersion);
            } catch (GeneralSecurityException e) {
                m3 = null; // make compiler happy
                throw new SSLException(
                        "Error generating ECDH server key exchange", e);
            }
            break;
        case K_ECDH_RSA:
        case K_ECDH_ECDSA:
            // ServerKeyExchange not used for fixed ECDH
            m3 = null;
            break;
        default:
            ClientKeyExchangeService p =
                    ClientKeyExchangeService.find(keyExchange.name);
            if (p != null) {
                // No external key exchange provider needs a cert now.
                m3 = null;
                break;
            }
            throw new RuntimeException("internal error: " + keyExchange);
        }
        if (m3 != null) {
            if (debug != null && Debug.isOn("handshake")) {
                m3.print(System.out);
            }
            m3.write(output);
            handshakeState.update(m3, resumingSession);
        }

        //
        // FOURTH, the CertificateRequest message.  The details of
        // the message can be affected by the key exchange algorithm
        // in use.  For example, certs with fixed Diffie-Hellman keys
        // are only useful with the DH_DSS and DH_RSA key exchange
        // algorithms.
        //
        // Needed only if server requires client to authenticate self.
        // Illegal for anonymous flavors, so we need to check that.
        //
        // No external key exchange provider needs a cert now.
        if (doClientAuth != ClientAuthType.CLIENT_AUTH_NONE &&
                keyExchange != K_DH_ANON && keyExchange != K_ECDH_ANON &&
                ClientKeyExchangeService.find(keyExchange.name) == null) {

            CertificateRequest m4;
            X509Certificate[] caCerts;

            Collection<SignatureAndHashAlgorithm> localSignAlgs = null;
            if (protocolVersion.useTLS12PlusSpec()) {
                // We currently use all local upported signature and hash
                // algorithms. However, to minimize the computation cost
                // of requested hash algorithms, we may use a restricted
                // set of signature algorithms in the future.
                localSignAlgs = getLocalSupportedSignAlgs();
                if (localSignAlgs.isEmpty()) {
                    throw new SSLHandshakeException(
                            "No supported signature algorithm");
                }

                Set<String> localHashAlgs =
                    SignatureAndHashAlgorithm.getHashAlgorithmNames(
                        localSignAlgs);
                if (localHashAlgs.isEmpty()) {
                    throw new SSLHandshakeException(
                            "No supported signature algorithm");
                }
            }

            caCerts = sslContext.getX509TrustManager().getAcceptedIssuers();
            m4 = new CertificateRequest(caCerts, keyExchange,
                                            localSignAlgs, protocolVersion);

            if (debug != null && Debug.isOn("handshake")) {
                m4.print(System.out);
            }
            m4.write(output);
            handshakeState.update(m4, resumingSession);
        }

        /*
         * FIFTH, say ServerHelloDone.
         */
        ServerHelloDone m5 = new ServerHelloDone();

        if (debug != null && Debug.isOn("handshake")) {
            m5.print(System.out);
        }
        m5.write(output);
        handshakeState.update(m5, resumingSession);

        /*
         * Flush any buffered messages so the client will see them.
         * Ideally, all the messages above go in a single network level
         * message to the client.  Without big Certificate chains, it's
         * going to be the common case.
         */
        output.flush();
    }

    /*
     * Choose cipher suite from among those supported by client. Sets
     * the cipherSuite and keyExchange variables.
     */
    private void chooseCipherSuite(ClientHello mesg) throws IOException {
        CipherSuiteList prefered;
        CipherSuiteList proposed;
        if (preferLocalCipherSuites) {
            prefered = getActiveCipherSuites();
            proposed = mesg.getCipherSuites();
        } else {
            prefered = mesg.getCipherSuites();
            proposed = getActiveCipherSuites();
        }

        List<CipherSuite> legacySuites = new ArrayList<>();
        for (CipherSuite suite : prefered.collection()) {
            if (isNegotiable(proposed, suite) == false) {
                continue;
            }

            if (doClientAuth == ClientAuthType.CLIENT_AUTH_REQUIRED) {
                if ((suite.keyExchange == K_DH_ANON) ||
                    (suite.keyExchange == K_ECDH_ANON)) {
                    continue;
                }
            }

            if (!legacyAlgorithmConstraints.permits(null, suite.name, null)) {
                legacySuites.add(suite);
                continue;
            }

            if (trySetCipherSuite(suite) == false) {
                continue;
            }
            return;
        }

        for (CipherSuite suite : legacySuites) {
            if (trySetCipherSuite(suite)) {
                return;
            }
        }

        fatalSE(Alerts.alert_handshake_failure, "no cipher suites in common");
    }

    /**
     * Set the given CipherSuite, if possible. Return the result.
     * The call succeeds if the CipherSuite is available and we have
     * the necessary certificates to complete the handshake. We don't
     * check if the CipherSuite is actually enabled.
     *
     * If successful, this method also generates ephemeral keys if
     * required for this ciphersuite. This may take some time, so this
     * method should only be called if you really want to use the
     * CipherSuite.
     *
     * This method is called from chooseCipherSuite() in this class.
     */
    boolean trySetCipherSuite(CipherSuite suite) {
        /*
         * If we're resuming a session we know we can
         * support this key exchange algorithm and in fact
         * have already cached the result of it in
         * the session state.
         */
        if (resumingSession) {
            return true;
        }

        if (suite.isNegotiable() == false) {
            return false;
        }

        // must not negotiate the obsoleted weak cipher suites.
        if (protocolVersion.obsoletes(suite)) {
            return false;
        }

        // must not negotiate unsupported cipher suites.
        if (!protocolVersion.supports(suite)) {
            return false;
        }

        KeyExchange keyExchange = suite.keyExchange;

        // null out any existing references
        privateKey = null;
        certs = null;
        dh = null;
        tempPrivateKey = null;
        tempPublicKey = null;

        Collection<SignatureAndHashAlgorithm> supportedSignAlgs = null;
        if (protocolVersion.useTLS12PlusSpec()) {
            if (peerSupportedSignAlgs != null) {
                supportedSignAlgs = peerSupportedSignAlgs;
            } else {
                SignatureAndHashAlgorithm algorithm = null;

                // we may optimize the performance
                switch (keyExchange) {
                    // If the negotiated key exchange algorithm is one of
                    // (RSA, DHE_RSA, DH_RSA, RSA_PSK, ECDH_RSA, ECDHE_RSA),
                    // behave as if client had sent the value {sha1,rsa}.
                    case K_RSA:
                    case K_DHE_RSA:
                    case K_DH_RSA:
                    // case K_RSA_PSK:
                    case K_ECDH_RSA:
                    case K_ECDHE_RSA:
                        algorithm = SignatureAndHashAlgorithm.valueOf(
                                HashAlgorithm.SHA1.value,
                                SignatureAlgorithm.RSA.value, 0);
                        break;
                    // If the negotiated key exchange algorithm is one of
                    // (DHE_DSS, DH_DSS), behave as if the client had
                    // sent the value {sha1,dsa}.
                    case K_DHE_DSS:
                    case K_DH_DSS:
                        algorithm = SignatureAndHashAlgorithm.valueOf(
                                HashAlgorithm.SHA1.value,
                                SignatureAlgorithm.DSA.value, 0);
                        break;
                    // If the negotiated key exchange algorithm is one of
                    // (ECDH_ECDSA, ECDHE_ECDSA), behave as if the client
                    // had sent value {sha1,ecdsa}.
                    case K_ECDH_ECDSA:
                    case K_ECDHE_ECDSA:
                        algorithm = SignatureAndHashAlgorithm.valueOf(
                                HashAlgorithm.SHA1.value,
                                SignatureAlgorithm.ECDSA.value, 0);
                        break;
                    default:
                        // no peer supported signature algorithms
                }

                if (algorithm == null) {
                    supportedSignAlgs =
                        Collections.<SignatureAndHashAlgorithm>emptySet();
                } else {
                    supportedSignAlgs =
                        new ArrayList<SignatureAndHashAlgorithm>(1);
                    supportedSignAlgs.add(algorithm);

                    supportedSignAlgs =
                            SignatureAndHashAlgorithm.getSupportedAlgorithms(
                                algorithmConstraints, supportedSignAlgs);

                    // May be no default activated signature algorithm, but
                    // let the following process make the final decision.
                }

                // Sets the peer supported signature algorithm to use in KM
                // temporarily.
                session.setPeerSupportedSignatureAlgorithms(supportedSignAlgs);
            }
        }

        switch (keyExchange) {
        case K_RSA:
            // need RSA certs for authentication
            if (setupPrivateKeyAndChain("RSA") == false) {
                return false;
            }
            break;
        case K_RSA_EXPORT:
            // need RSA certs for authentication
            if (setupPrivateKeyAndChain("RSA") == false) {
                return false;
            }

            try {
               if (JsseJce.getRSAKeyLength(certs[0].getPublicKey()) > 512) {
                    if (!setupEphemeralRSAKeys(suite.exportable)) {
                        return false;
                    }
               }
            } catch (RuntimeException e) {
                // could not determine keylength, ignore key
                return false;
            }
            break;
        case K_DHE_RSA:
            // need RSA certs for authentication
            if (setupPrivateKeyAndChain("RSA") == false) {
                return false;
            }

            // get preferable peer signature algorithm for server key exchange
            if (protocolVersion.useTLS12PlusSpec()) {
                preferableSignatureAlgorithm =
                    SignatureAndHashAlgorithm.getPreferableAlgorithm(
                                        supportedSignAlgs, "RSA", privateKey);
                if (preferableSignatureAlgorithm == null) {
                    if ((debug != null) && Debug.isOn("handshake")) {
                        System.out.println(
                                "No signature and hash algorithm for cipher " +
                                suite);
                    }
                    return false;
                }
            }

            setupEphemeralDHKeys(suite.exportable, privateKey);
            break;
        case K_ECDHE_RSA:
            // need RSA certs for authentication
            if (setupPrivateKeyAndChain("RSA") == false) {
                return false;
            }

            // get preferable peer signature algorithm for server key exchange
            if (protocolVersion.useTLS12PlusSpec()) {
                preferableSignatureAlgorithm =
                    SignatureAndHashAlgorithm.getPreferableAlgorithm(
                                        supportedSignAlgs, "RSA", privateKey);
                if (preferableSignatureAlgorithm == null) {
                    if ((debug != null) && Debug.isOn("handshake")) {
                        System.out.println(
                                "No signature and hash algorithm for cipher " +
                                suite);
                    }
                    return false;
                }
            }

            if (setupEphemeralECDHKeys() == false) {
                return false;
            }
            break;
        case K_DHE_DSS:
            // get preferable peer signature algorithm for server key exchange
            if (protocolVersion.useTLS12PlusSpec()) {
                preferableSignatureAlgorithm =
                    SignatureAndHashAlgorithm.getPreferableAlgorithm(
                                                supportedSignAlgs, "DSA");
                if (preferableSignatureAlgorithm == null) {
                    if ((debug != null) && Debug.isOn("handshake")) {
                        System.out.println(
                                "No signature and hash algorithm for cipher " +
                                suite);
                    }
                    return false;
                }
            }

            // need DSS certs for authentication
            if (setupPrivateKeyAndChain("DSA") == false) {
                return false;
            }

            setupEphemeralDHKeys(suite.exportable, privateKey);
            break;
        case K_ECDHE_ECDSA:
            // get preferable peer signature algorithm for server key exchange
            if (protocolVersion.useTLS12PlusSpec()) {
                preferableSignatureAlgorithm =
                    SignatureAndHashAlgorithm.getPreferableAlgorithm(
                                            supportedSignAlgs, "ECDSA");
                if (preferableSignatureAlgorithm == null) {
                    if ((debug != null) && Debug.isOn("handshake")) {
                        System.out.println(
                                "No signature and hash algorithm for cipher " +
                                suite);
                    }
                    return false;
                }
            }

            // need EC cert
            if (setupPrivateKeyAndChain("EC") == false) {
                return false;
            }
            if (setupEphemeralECDHKeys() == false) {
                return false;
            }
            break;
        case K_ECDH_RSA:
            // need EC cert
            if (setupPrivateKeyAndChain("EC") == false) {
                return false;
            }
            setupStaticECDHKeys();
            break;
        case K_ECDH_ECDSA:
            // need EC cert
            if (setupPrivateKeyAndChain("EC") == false) {
                return false;
            }
            setupStaticECDHKeys();
            break;
        case K_DH_ANON:
            // no certs needed for anonymous
            setupEphemeralDHKeys(suite.exportable, null);
            break;
        case K_ECDH_ANON:
            // no certs needed for anonymous
            if (setupEphemeralECDHKeys() == false) {
                return false;
            }
            break;
        default:
            ClientKeyExchangeService p =
                    ClientKeyExchangeService.find(keyExchange.name);
            if (p == null) {
                // internal error, unknown key exchange
                throw new RuntimeException(
                        "Unrecognized cipherSuite: " + suite);
            }
            // need service creds
            if (serviceCreds == null) {
                AccessControlContext acc = getAccSE();
                serviceCreds = p.getServiceCreds(acc);
                if (serviceCreds != null) {
                    if (debug != null && Debug.isOn("handshake")) {
                        System.out.println("Using serviceCreds");
                    }
                }
                if (serviceCreds == null) {
                    return false;
                }
            }
            break;
        }
        setCipherSuite(suite);

        // set the peer implicit supported signature algorithms
        if (protocolVersion.useTLS12PlusSpec()) {
            if (peerSupportedSignAlgs == null) {
                setPeerSupportedSignAlgs(supportedSignAlgs);
                // we had alreay update the session
            }
        }
        return true;
    }

    /*
     * Get some "ephemeral" RSA keys for this context. This means
     * generating them if it's not already been done.
     *
     * Note that we currently do not implement any ciphersuites that use
     * strong ephemeral RSA. (We do not support the EXPORT1024 ciphersuites
     * and standard RSA ciphersuites prohibit ephemeral mode for some reason)
     * This means that export is always true and 512 bit keys are generated.
     */
    private boolean setupEphemeralRSAKeys(boolean export) {
        KeyPair kp = sslContext.getEphemeralKeyManager().
                        getRSAKeyPair(export, sslContext.getSecureRandom());
        if (kp == null) {
            return false;
        } else {
            tempPublicKey = kp.getPublic();
            tempPrivateKey = kp.getPrivate();
            return true;
        }
    }

    /*
     * Acquire some "ephemeral" Diffie-Hellman  keys for this handshake.
     * We don't reuse these, for improved forward secrecy.
     */
    private void setupEphemeralDHKeys(boolean export, Key key) {
        /*
         * 768 bits ephemeral DH private keys were used to be used in
         * ServerKeyExchange except that exportable ciphers max out at 512
         * bits modulus values. We still adhere to this behavior in legacy
         * mode (system property "jdk.tls.ephemeralDHKeySize" is defined
         * as "legacy").
         *
         * Old JDK (JDK 7 and previous) releases don't support DH keys bigger
         * than 1024 bits. We have to consider the compatibility requirement.
         * 1024 bits DH key is always used for non-exportable cipher suites
         * in default mode (system property "jdk.tls.ephemeralDHKeySize"
         * is not defined).
         *
         * However, if applications want more stronger strength, setting
         * system property "jdk.tls.ephemeralDHKeySize" to "matched"
         * is a workaround to use ephemeral DH key which size matches the
         * corresponding authentication key. For example, if the public key
         * size of an authentication certificate is 2048 bits, then the
         * ephemeral DH key size should be 2048 bits accordingly unless
         * the cipher suite is exportable.  This key sizing scheme keeps
         * the cryptographic strength consistent between authentication
         * keys and key-exchange keys.
         *
         * Applications may also want to customize the ephemeral DH key size
         * to a fixed length for non-exportable cipher suites. This can be
         * approached by setting system property "jdk.tls.ephemeralDHKeySize"
         * to a valid positive integer between 1024 and 8192 bits, inclusive.
         *
         * Note that the minimum acceptable key size is 1024 bits except
         * exportable cipher suites or legacy mode.
         *
         * Note that per RFC 2246, the key size limit of DH is 512 bits for
         * exportable cipher suites.  Because of the weakness, exportable
         * cipher suites are deprecated since TLS v1.1 and they are not
         * enabled by default in Oracle provider. The legacy behavior is
         * reserved and 512 bits DH key is always used for exportable
         * cipher suites.
         */
        int keySize = export ? 512 : 1024;           // default mode
        if (!export) {
            if (useLegacyEphemeralDHKeys) {          // legacy mode
                keySize = 768;
            } else if (useSmartEphemeralDHKeys) {    // matched mode
                if (key != null) {
                    int ks = KeyUtil.getKeySize(key);

                    // DH parameter generation can be extremely slow, make
                    // sure to use one of the supported pre-computed DH
                    // parameters (see DHCrypt class).
                    //
                    // Old deployed applications may not be ready to support
                    // DH key sizes bigger than 2048 bits.  Please DON'T use
                    // value other than 1024 and 2048 at present.  May improve
                    // the underlying providers and key size limit in the
                    // future when the compatibility and interoperability
                    // impact is limited.
                    //
                    // keySize = ks <= 1024 ? 1024 : (ks >= 2048 ? 2048 : ks);
                    keySize = ks <= 1024 ? 1024 : 2048;
                } // Otherwise, anonymous cipher suites, 1024-bit is used.
            } else if (customizedDHKeySize > 0) {    // customized mode
                keySize = customizedDHKeySize;
            }
        }

        dh = new DHCrypt(keySize, sslContext.getSecureRandom());
    }

    // Setup the ephemeral ECDH parameters.
    // If we cannot continue because we do not support any of the curves that
    // the client requested, return false. Otherwise (all is well), return true.
    private boolean setupEphemeralECDHKeys() {
        int index = -1;
        if (supportedCurves != null) {
            // if the client sent the supported curves extension, pick the
            // first one that we support;
            for (int curveId : supportedCurves.curveIds()) {
                if (SupportedEllipticCurvesExtension.isSupported(curveId)) {
                    index = curveId;
                    break;
                }
            }
            if (index < 0) {
                // no match found, cannot use this ciphersuite
                return false;
            }
        } else {
            // pick our preference
            index = SupportedEllipticCurvesExtension.DEFAULT.curveIds()[0];
        }
        String oid = SupportedEllipticCurvesExtension.getCurveOid(index);
        ecdh = new ECDHCrypt(oid, sslContext.getSecureRandom());
        return true;
    }

    private void setupStaticECDHKeys() {
        // don't need to check whether the curve is supported, already done
        // in setupPrivateKeyAndChain().
        ecdh = new ECDHCrypt(privateKey, certs[0].getPublicKey());
    }

    /**
     * Retrieve the server key and certificate for the specified algorithm
     * from the KeyManager and set the instance variables.
     *
     * @return true if successful, false if not available or invalid
     */
    private boolean setupPrivateKeyAndChain(String algorithm) {
        X509ExtendedKeyManager km = sslContext.getX509KeyManager();
        String alias;
        if (conn != null) {
            alias = km.chooseServerAlias(algorithm, null, conn);
        } else {
            alias = km.chooseEngineServerAlias(algorithm, null, engine);
        }
        if (alias == null) {
            return false;
        }
        PrivateKey tempPrivateKey = km.getPrivateKey(alias);
        if (tempPrivateKey == null) {
            return false;
        }
        X509Certificate[] tempCerts = km.getCertificateChain(alias);
        if ((tempCerts == null) || (tempCerts.length == 0)) {
            return false;
        }
        String keyAlgorithm = algorithm.split("_")[0];
        PublicKey publicKey = tempCerts[0].getPublicKey();
        if ((tempPrivateKey.getAlgorithm().equals(keyAlgorithm) == false)
                || (publicKey.getAlgorithm().equals(keyAlgorithm) == false)) {
            return false;
        }
        // For ECC certs, check whether we support the EC domain parameters.
        // If the client sent a SupportedEllipticCurves ClientHello extension,
        // check against that too.
        if (keyAlgorithm.equals("EC")) {
            if (publicKey instanceof ECPublicKey == false) {
                return false;
            }
            ECParameterSpec params = ((ECPublicKey)publicKey).getParams();
            int index = SupportedEllipticCurvesExtension.getCurveIndex(params);
            if (SupportedEllipticCurvesExtension.isSupported(index) == false) {
                return false;
            }
            if ((supportedCurves != null) && !supportedCurves.contains(index)) {
                return false;
            }
        }
        this.privateKey = tempPrivateKey;
        this.certs = tempCerts;
        return true;
    }

    /*
     * Returns premaster secret for external key exchange services.
     */
    private SecretKey clientKeyExchange(ClientKeyExchange mesg)
        throws IOException {

        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        // Record the principals involved in exchange
        session.setPeerPrincipal(mesg.getPeerPrincipal());
        session.setLocalPrincipal(mesg.getLocalPrincipal());

        return mesg.clientKeyExchange();
    }

    /*
     * Diffie Hellman key exchange is used when the server presented
     * D-H parameters in its certificate (signed using RSA or DSS/DSA),
     * or else the server presented no certificate but sent D-H params
     * in a ServerKeyExchange message.  Use of D-H is specified by the
     * cipher suite chosen.
     *
     * The message optionally contains the client's D-H public key (if
     * it wasn't not sent in a client certificate).  As always with D-H,
     * if a client and a server have each other's D-H public keys and
     * they use common algorithm parameters, they have a shared key
     * that's derived via the D-H calculation.  That key becomes the
     * pre-master secret.
     */
    private SecretKey clientKeyExchange(DHClientKeyExchange mesg)
            throws IOException {

        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        BigInteger publicKeyValue = mesg.getClientPublicKey();

        // check algorithm constraints
        dh.checkConstraints(algorithmConstraints, publicKeyValue);

        return dh.getAgreedSecret(publicKeyValue, false);
    }

    private SecretKey clientKeyExchange(ECDHClientKeyExchange mesg)
            throws IOException {

        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        byte[] publicPoint = mesg.getEncodedPoint();

        // check algorithm constraints
        ecdh.checkConstraints(algorithmConstraints, publicPoint);

        return ecdh.getAgreedSecret(publicPoint);
    }

    /*
     * Client wrote a message to verify the certificate it sent earlier.
     *
     * Note that this certificate isn't involved in key exchange.  Client
     * authentication messages are included in the checksums used to
     * validate the handshake (e.g. Finished messages).  Other than that,
     * the _exact_ identity of the client is less fundamental to protocol
     * security than its role in selecting keys via the pre-master secret.
     */
    private void clientCertificateVerify(CertificateVerify mesg)
            throws IOException {

        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        if (protocolVersion.useTLS12PlusSpec()) {
            SignatureAndHashAlgorithm signAlg =
                mesg.getPreferableSignatureAlgorithm();
            if (signAlg == null) {
                throw new SSLHandshakeException(
                        "Illegal CertificateVerify message");
            }

            String hashAlg =
                SignatureAndHashAlgorithm.getHashAlgorithmName(signAlg);
            if (hashAlg == null || hashAlg.length() == 0) {
                throw new SSLHandshakeException(
                        "No supported hash algorithm");
            }
        }

        try {
            PublicKey publicKey =
                session.getPeerCertificates()[0].getPublicKey();

            boolean valid = mesg.verify(protocolVersion, handshakeHash,
                                        publicKey, session.getMasterSecret());
            if (valid == false) {
                fatalSE(Alerts.alert_bad_certificate,
                            "certificate verify message signature error");
            }
        } catch (GeneralSecurityException e) {
            fatalSE(Alerts.alert_bad_certificate,
                "certificate verify format error", e);
        }

        // reset the flag for clientCertificateVerify message
        needClientVerify = false;
    }


    /*
     * Client writes "finished" at the end of its handshake, after cipher
     * spec is changed.   We verify it and then send ours.
     *
     * When we're resuming a session, we'll have already sent our own
     * Finished message so just the verification is needed.
     */
    private void clientFinished(Finished mesg) throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        /*
         * Verify if client did send the certificate when client
         * authentication was required, otherwise server should not proceed
         */
        if (doClientAuth == ClientAuthType.CLIENT_AUTH_REQUIRED) {
           // get X500Principal of the end-entity certificate for X509-based
           // ciphersuites, or Kerberos principal for Kerberos ciphersuites, etc
           session.getPeerPrincipal();
        }

        /*
         * Verify if client did send clientCertificateVerify message following
         * the client Certificate, otherwise server should not proceed
         */
        if (needClientVerify) {
                fatalSE(Alerts.alert_handshake_failure,
                        "client did not send certificate verify message");
        }

        /*
         * Verify the client's message with the "before" digest of messages,
         * and forget about continuing to use that digest.
         */
        boolean verified = mesg.verify(handshakeHash, Finished.CLIENT,
            session.getMasterSecret());

        if (!verified) {
            fatalSE(Alerts.alert_handshake_failure,
                        "client 'finished' message doesn't verify");
            // NOTREACHED
        }

        /*
         * save client verify data for secure renegotiation
         */
        if (secureRenegotiation) {
            clientVerifyData = mesg.getVerifyData();
        }

        /*
         * OK, it verified.  If we're doing the full handshake, add that
         * "Finished" message to the hash of handshake messages, then send
         * the change_cipher_spec and Finished message.
         */
        if (!resumingSession) {
            sendChangeCipherAndFinish(true);
        } else {
            handshakeFinished = true;
        }

        /*
         * Update the session cache only after the handshake completed, else
         * we're open to an attack against a partially completed handshake.
         */
        session.setLastAccessedTime(System.currentTimeMillis());
        if (!resumingSession && session.isRejoinable()) {
            ((SSLSessionContextImpl)sslContext.engineGetServerSessionContext())
                .put(session);
            if (debug != null && Debug.isOn("session")) {
                System.out.println(
                    "%% Cached server session: " + session);
            }
        } else if (!resumingSession &&
                debug != null && Debug.isOn("session")) {
            System.out.println(
                "%% Didn't cache non-resumable server session: "
                + session);
        }
    }

    /*
     * Compute finished message with the "server" digest (and then forget
     * about that digest, it can't be used again).
     */
    private void sendChangeCipherAndFinish(boolean finishedTag)
            throws IOException {

        // Reload if this message has been reserved.
        handshakeHash.reload();

        Finished mesg = new Finished(protocolVersion, handshakeHash,
            Finished.SERVER, session.getMasterSecret(), cipherSuite);

        /*
         * Send the change_cipher_spec record; then our Finished handshake
         * message will be the last handshake message.  Flush, and now we
         * are ready for application data!!
         */
        sendChangeCipherSpec(mesg, finishedTag);

        /*
         * save server verify data for secure renegotiation
         */
        if (secureRenegotiation) {
            serverVerifyData = mesg.getVerifyData();
        }
    }


    /*
     * Returns a HelloRequest message to kickstart renegotiations
     */
    @Override
    HandshakeMessage getKickstartMessage() {
        return new HelloRequest();
    }


    /*
     * Fault detected during handshake.
     */
    @Override
    void handshakeAlert(byte description) throws SSLProtocolException {

        String message = Alerts.alertDescription(description);

        if (debug != null && Debug.isOn("handshake")) {
            System.out.println("SSL -- handshake alert:  "
                + message);
        }

        /*
         * It's ok to get a no_certificate alert from a client of which
         * we *requested* authentication information.
         * However, if we *required* it, then this is not acceptable.
         *
         * Anyone calling getPeerCertificates() on the
         * session will get an SSLPeerUnverifiedException.
         */
        if ((description == Alerts.alert_no_certificate) &&
                (doClientAuth == ClientAuthType.CLIENT_AUTH_REQUESTED)) {
            return;
        }

        throw new SSLProtocolException("handshake alert: " + message);
    }

    /*
     * RSA key exchange is normally used.  The client encrypts a "pre-master
     * secret" with the server's public key, from the Certificate (or else
     * ServerKeyExchange) message that was sent to it by the server.  That's
     * decrypted using the private key before we get here.
     */
    private SecretKey clientKeyExchange(RSAClientKeyExchange mesg)
            throws IOException {

        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }
        return mesg.preMaster;
    }

    /*
     * Verify the certificate sent by the client. We'll only get one if we
     * sent a CertificateRequest to request client authentication. If we
     * are in TLS mode, the client may send a message with no certificates
     * to indicate it does not have an appropriate chain. (In SSLv3 mode,
     * it would send a no certificate alert).
     */
    private void clientCertificate(CertificateMsg mesg) throws IOException {
        if (debug != null && Debug.isOn("handshake")) {
            mesg.print(System.out);
        }

        X509Certificate[] peerCerts = mesg.getCertificateChain();

        if (peerCerts.length == 0) {
            /*
             * If the client authentication is only *REQUESTED* (e.g.
             * not *REQUIRED*, this is an acceptable condition.)
             */
            if (doClientAuth == ClientAuthType.CLIENT_AUTH_REQUESTED) {
                return;
            } else {
                fatalSE(Alerts.alert_bad_certificate,
                    "null cert chain");
            }
        }

        // ask the trust manager to verify the chain
        X509TrustManager tm = sslContext.getX509TrustManager();

        try {
            // find out the types of client authentication used
            PublicKey key = peerCerts[0].getPublicKey();
            String keyAlgorithm = key.getAlgorithm();
            String authType;
            if (keyAlgorithm.equals("RSA")) {
                authType = "RSA";
            } else if (keyAlgorithm.equals("DSA")) {
                authType = "DSA";
            } else if (keyAlgorithm.equals("EC")) {
                authType = "EC";
            } else {
                // unknown public key type
                authType = "UNKNOWN";
            }

            if (tm instanceof X509ExtendedTrustManager) {
                if (conn != null) {
                    ((X509ExtendedTrustManager)tm).checkClientTrusted(
                        peerCerts.clone(),
                        authType,
                        conn);
                } else {
                    ((X509ExtendedTrustManager)tm).checkClientTrusted(
                        peerCerts.clone(),
                        authType,
                        engine);
                }
            } else {
                // Unlikely to happen, because we have wrapped the old
                // X509TrustManager with the new X509ExtendedTrustManager.
                throw new CertificateException(
                    "Improper X509TrustManager implementation");
            }
        } catch (CertificateException e) {
            // This will throw an exception, so include the original error.
            fatalSE(Alerts.alert_certificate_unknown, e);
        }
        // set the flag for clientCertificateVerify message
        needClientVerify = true;

        session.setPeerCertificates(peerCerts);
    }

    private StaplingParameters processStapling(ClientHello mesg) {
        StaplingParameters params = null;
        ExtensionType ext;
        StatusRequestType type = null;
        StatusRequest req = null;
        Map<X509Certificate, byte[]> responses;

        // If this feature has not been enabled, then no more processing
        // is necessary.  Also we will only staple if we're doing a full
        // handshake.
        if (!sslContext.isStaplingEnabled(false) || resumingSession) {
            return null;
        }

        // Check if the client has asserted the status_request[_v2] extension(s)
        CertStatusReqExtension statReqExt = (CertStatusReqExtension)
                    mesg.extensions.get(ExtensionType.EXT_STATUS_REQUEST);
        CertStatusReqListV2Extension statReqExtV2 =
                (CertStatusReqListV2Extension)mesg.extensions.get(
                        ExtensionType.EXT_STATUS_REQUEST_V2);
        // Keep processing only if either status_request or status_request_v2
        // has been sent in the ClientHello.
        if (statReqExt == null && statReqExtV2 == null) {
            return null;
        }

        // Determine which type of stapling we are doing and assert the
        // proper extension in the server hello.
        // Favor status_request_v2 over status_request and ocsp_multi
        // over ocsp.
        // If multiple ocsp or ocsp_multi types exist, select the first
        // instance of a given type
        ext = ExtensionType.EXT_STATUS_REQUEST;
        if (statReqExtV2 != null) {             // RFC 6961 stapling
            ext = ExtensionType.EXT_STATUS_REQUEST_V2;
            List<CertStatusReqItemV2> reqItems =
                    statReqExtV2.getRequestItems();
            int ocspIdx = -1;
            int ocspMultiIdx = -1;
            for (int pos = 0; pos < reqItems.size(); pos++) {
                CertStatusReqItemV2 item = reqItems.get(pos);
                if (ocspIdx < 0 && item.getType() ==
                        StatusRequestType.OCSP) {
                    ocspIdx = pos;
                } else if (ocspMultiIdx < 0 && item.getType() ==
                        StatusRequestType.OCSP_MULTI) {
                    ocspMultiIdx = pos;
                }
            }
            if (ocspMultiIdx >= 0) {
                type = reqItems.get(ocspMultiIdx).getType();
                req = reqItems.get(ocspMultiIdx).getRequest();
            } else if (ocspIdx >= 0) {
                type = reqItems.get(ocspIdx).getType();
                req = reqItems.get(ocspIdx).getRequest();
            }
        } else {                                // RFC 6066 stapling
            type = StatusRequestType.OCSP;
            req = statReqExt.getRequest();
        }

        // If, after walking through the extensions we were unable to
        // find a suitable StatusRequest, then stapling is disabled.
        // Both statReqType and statReqData must have been set to continue.
        if (type == null || req == null) {
            return null;
        }

        // Get the OCSP responses from the StatusResponseManager
        StatusResponseManager statRespMgr =
                sslContext.getStatusResponseManager();
        if (statRespMgr != null) {
            responses = statRespMgr.get(type, req, certs, statusRespTimeout,
                    TimeUnit.MILLISECONDS);
            if (!responses.isEmpty()) {
                // If this RFC 6066-style stapling (SSL cert only) then the
                // response cannot be zero length
                if (type == StatusRequestType.OCSP) {
                    byte[] respDER = responses.get(certs[0]);
                    if (respDER == null || respDER.length <= 0) {
                        return null;
                    }
                }
                params = new StaplingParameters(ext, type, req, responses);
            }
        } else {
            // This should not happen, but if lazy initialization of the
            // StatusResponseManager doesn't occur we should turn off stapling.
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("Warning: lazy initialization " +
                        "of the StatusResponseManager failed.  " +
                        "Stapling has been disabled.");
            }
        }

        return params;
    }

    /**
     * Inner class used to hold stapling parameters needed by the handshaker
     * when stapling is active.
     */
    private class StaplingParameters {
        private final ExtensionType statusRespExt;
        private final StatusRequestType statReqType;
        private final StatusRequest statReqData;
        private final Map<X509Certificate, byte[]> responseMap;

        StaplingParameters(ExtensionType ext, StatusRequestType type,
                StatusRequest req, Map<X509Certificate, byte[]> responses) {
            statusRespExt = ext;
            statReqType = type;
            statReqData = req;
            responseMap = responses;
        }
    }
}
