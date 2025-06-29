/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.Adler32;
import javax.crypto.KDF;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;

import sun.security.ssl.CipherSuite.HashAlg;
import sun.security.internal.spec.TlsPrfParameterSpec;
import static sun.security.ssl.CipherSuite.HashAlg.H_NONE;
import static sun.security.ssl.ProtocolVersion.*;
import sun.security.util.KeyUtil;
import sun.security.provider.X509Factory;
import sun.security.ssl.X509Authentication.X509Possession;

/**
 * Implements the SSL session interface, and exposes the session context
 * which is maintained by SSL servers.
 *
 * <P> Servers have the ability to manage the sessions associated with
 * their authentication context(s).  They can do this by enumerating the
 * IDs of the sessions which are cached, examining those sessions, and then
 * perhaps invalidating a given session so that it can't be used again.
 * If servers do not explicitly manage the cache, sessions will linger
 * until memory is low enough that the runtime environment purges cache
 * entries automatically to reclaim space.
 *
 * <P><em> The only reason this class is not package-private is that
 * there's no other public way to get at the server session context which
 * is associated with any given authentication context. </em>
 *
 * @author David Brownell
 */
final class SSLSessionImpl extends ExtendedSSLSession {

    /*
     * The state of a single session, as described in section 7.1
     * of the SSLv3 spec.
     */
    private final ProtocolVersion       protocolVersion;
    private final SessionId             sessionId;
    private X509Certificate[]   peerCerts;
    private CipherSuite         cipherSuite;
    private SecretKey           masterSecret;
    final boolean               useExtendedMasterSecret;

    /*
     * Information not part of the SSLv3 protocol spec, but used
     * to support session management policies.
     */
    private final long          creationTime;
    private long                lastUsedTime = 0;
    private final String        host;
    private final int           port;
    private SSLSessionContextImpl       context;
    private boolean             invalidated;
    private X509Certificate[]   localCerts;
    private PrivateKey          localPrivateKey;
    private final Collection<SignatureScheme>     localSupportedSignAlgs;
    private Collection<SignatureScheme> peerSupportedSignAlgs; //for certificate
    private boolean             useDefaultPeerSignAlgs = false;
    private List<byte[]>        statusResponses;
    private SecretKey           exporterMasterSecret;  // TLSv1.3+ exporter info
    private RandomCookie        clientRandom;          // TLSv1.2- exporter info
    private RandomCookie        serverRandom;
    private SecretKey           resumptionMasterSecret;
    private SecretKey           preSharedKey;
    private byte[]              pskIdentity;
    private final long          ticketCreationTime = System.currentTimeMillis();
    private int                 ticketAgeAdd;

    private int                 negotiatedMaxFragLen = -1;
    private int                 maximumPacketSize;

    private final Queue<SSLSessionImpl> childSessions =
                                        new ConcurrentLinkedQueue<>();

    /*
     * Is the session currently re-established with a session-resumption
     * abbreviated initial handshake?
     *
     * Note that currently we only set this variable in client side.
     */
    private boolean isSessionResumption = false;

    /*
     * Use of session caches is globally enabled/disabled.
     */
    private static final boolean defaultRejoinable = true;

    // server name indication
    final SNIServerName         serverNameIndication;
    private final List<SNIServerName>    requestedServerNames;

    // Counter used to create unique nonces in NewSessionTicket
    private byte ticketNonceCounter = 1;

    // This boolean is true when a new set of NewSessionTickets are needed after
    // the initial ones sent after the handshake.
    boolean updateNST = false;

    // The endpoint identification algorithm used to check certificates
    // in this session.
    private final String        identificationProtocol;

    private final ReentrantLock sessionLock = new ReentrantLock();

    /*
     * Create a new non-rejoinable session, using the default (null)
     * cipher spec.  This constructor returns a session which could
     * be used either by a client or by a server, as a connection is
     * first opened and before handshaking begins.
     */
    SSLSessionImpl() {
        this.protocolVersion = ProtocolVersion.NONE;
        this.cipherSuite = CipherSuite.C_NULL;
        this.sessionId = new SessionId(false, null);
        this.host = null;
        this.port = -1;
        this.localSupportedSignAlgs = Collections.emptySet();
        this.serverNameIndication = null;
        this.requestedServerNames = Collections.emptyList();
        this.useExtendedMasterSecret = false;
        this.creationTime = System.currentTimeMillis();
        this.identificationProtocol = null;
        this.boundValues = new ConcurrentHashMap<>();
    }

    /*
     * Create a new session, using a given cipher spec.  This will
     * be rejoinable if session caching is enabled; the constructor
     * is intended mostly for use by serves.
     */
    SSLSessionImpl(HandshakeContext hc, CipherSuite cipherSuite) {
        this(hc, cipherSuite,
            new SessionId(defaultRejoinable, hc.sslContext.getSecureRandom()));
    }

    /*
     * Record a new session, using a given cipher spec and session ID.
     */
    SSLSessionImpl(HandshakeContext hc, CipherSuite cipherSuite, SessionId id) {
        this(hc, cipherSuite, id, System.currentTimeMillis());
    }

    /*
     * Record a new session, using a given cipher spec, session ID,
     * and creation time.
     * Note: For the unmodifiable collections and lists we are creating new
     * collections as inputs to avoid potential deep nesting of
     * unmodifiable collections that can cause StackOverflowErrors
     * (see JDK-6323374).
     */
    SSLSessionImpl(HandshakeContext hc,
            CipherSuite cipherSuite, SessionId id, long creationTime) {
        this.protocolVersion = hc.negotiatedProtocol;
        this.cipherSuite = cipherSuite;
        this.sessionId = id;
        this.host = hc.conContext.transport.getPeerHost();
        this.port = hc.conContext.transport.getPeerPort();
        this.localSupportedSignAlgs = hc.localSupportedCertSignAlgs == null ?
                Collections.emptySet() :
                Collections.unmodifiableCollection(
                        new ArrayList<>(hc.localSupportedCertSignAlgs));
        this.serverNameIndication = hc.negotiatedServerName;
        this.requestedServerNames = List.copyOf(hc.getRequestedServerNames());
        if (hc.sslConfig.isClientMode) {
            this.useExtendedMasterSecret =
                (hc.handshakeExtensions.get(
                        SSLExtension.CH_EXTENDED_MASTER_SECRET) != null) &&
                (hc.handshakeExtensions.get(
                        SSLExtension.SH_EXTENDED_MASTER_SECRET) != null);
        } else {
            this.useExtendedMasterSecret =
                (hc.handshakeExtensions.get(
                        SSLExtension.CH_EXTENDED_MASTER_SECRET) != null) &&
                (!hc.negotiatedProtocol.useTLS13PlusSpec());
        }
        this.creationTime = creationTime;
        this.identificationProtocol = hc.sslConfig.identificationProtocol;
        this.boundValues = new ConcurrentHashMap<>();

        if (SSLLogger.isOn && SSLLogger.isOn("session")) {
             SSLLogger.finest("Session initialized:  " + this);
        }
    }

    SSLSessionImpl(SSLSessionImpl baseSession, SessionId newId) {
        this.protocolVersion = baseSession.getProtocolVersion();
        this.cipherSuite = baseSession.cipherSuite;
        this.sessionId = newId;
        this.host = baseSession.getPeerHost();
        this.port = baseSession.getPeerPort();
        this.localSupportedSignAlgs =
                baseSession.localSupportedSignAlgs == null ?
                Collections.emptySet() : baseSession.localSupportedSignAlgs;
        this.peerSupportedSignAlgs =
                baseSession.peerSupportedSignAlgs == null ?
                Collections.emptySet() : baseSession.peerSupportedSignAlgs;
        this.serverNameIndication = baseSession.serverNameIndication;
        this.requestedServerNames = baseSession.getRequestedServerNames();
        this.masterSecret = baseSession.getMasterSecret();
        this.useExtendedMasterSecret = baseSession.useExtendedMasterSecret;
        this.creationTime = baseSession.getCreationTime();
        this.lastUsedTime = System.currentTimeMillis();
        this.identificationProtocol = baseSession.getIdentificationProtocol();
        this.localCerts = baseSession.localCerts;
        this.peerCerts = baseSession.peerCerts;
        this.statusResponses = baseSession.statusResponses;
        this.resumptionMasterSecret = baseSession.resumptionMasterSecret;
        this.context = baseSession.context;
        this.negotiatedMaxFragLen = baseSession.negotiatedMaxFragLen;
        this.maximumPacketSize = baseSession.maximumPacketSize;
        this.boundValues = baseSession.boundValues;

        if (SSLLogger.isOn && SSLLogger.isOn("session")) {
             SSLLogger.finest("Session initialized:  " + this);
        }
    }

    /**
     * Reassemble new session ticket.
     * <p>
     * < 2 bytes > protocolVersion
     * < 2 bytes > cipherSuite
     * < 1 byte > localSupportedSignAlgs entries
     *   < 2 bytes per entries > localSupportedSignAlgs
     * select (protocolVersion)
     *   case TLS13Plus:
     *     < 2 bytes > preSharedKey length
     *     < length in bytes > preSharedKey
     *   case non-TLS13Plus:
     *     < 2 bytes > masterSecretKey length
     *     < length in bytes> masterSecretKey
     *     < 1 byte > useExtendedMasterSecret
     * < 1 byte > identificationProtocol length
     *   < length in bytes > identificationProtocol
     * < 1 byte > serverNameIndication length
     *   < length in bytes > serverNameIndication
     * < 1 byte > Number of requestedServerNames entries
     *   For each entry {
     *     < 1 byte > ServerName length
     *     < length in bytes > ServerName
     *   }
     * < 4 bytes > maximumPacketSize
     * < 4 bytes > negotiatedMaxFragSize
     * < 4 bytes > creationTime
     * < 1 byte > Length of peer host
     *   < length in bytes > peer host
     * < 2 bytes> peer port
     * < 1 byte > Number of Peer Certificate entries
     *   For each entry {
     *     < 4 bytes > Peer certificate length
     *     < length in bytes> Peer certificate
     *   }
     * < 1 byte > Number of Local Certificate entries
     *   For each entry {
     *     < 1 byte > Local Certificate algorithm length
     *     < length in bytes> Local Certificate algorithm
     *     < 4 bytes > Certificate checksum
     *   }
     */

    SSLSessionImpl(HandshakeContext hc, ByteBuffer buf) throws IOException {
        int len;
        byte[] b;
        boundValues = new ConcurrentHashMap<>();
        this.protocolVersion =
                ProtocolVersion.valueOf(Record.getInt16(buf));

        // The CH session id may reset this if it's provided
        this.sessionId = new SessionId(true,
                hc.sslContext.getSecureRandom());

        this.cipherSuite =
                CipherSuite.valueOf(Record.getInt16(buf));

        // Local Supported signature algorithms
        List<SignatureScheme> list = new ArrayList<>();
        len = Record.getInt8(buf);
        while (len-- > 0) {
            list.add(SignatureScheme.valueOf(
                    Record.getInt16(buf)));
        }
        this.localSupportedSignAlgs = Collections.unmodifiableCollection(list);

        if (protocolVersion.useTLS13PlusSpec()) {
            // PSK
            b = Record.getBytes16(buf);
            if (b.length > 0) {
                this.preSharedKey = new SecretKeySpec(b, "TlsMasterSecret");
            } else {
                this.preSharedKey = null;
            }

            this.useExtendedMasterSecret = false;
        } else {
            // Master secret
            b = Record.getBytes16(buf);
            if (b.length > 0) {
                this.masterSecret = new SecretKeySpec(b, "TlsMasterSecret");
            } else {
                this.masterSecret = null;
            }

            // Extended master secret usage.
            this.useExtendedMasterSecret = (Record.getInt8(buf) != 0);
        }

        // Identification Protocol
        b = Record.getBytes8(buf);
        if (b.length == 0) {
            identificationProtocol = null;
        } else {
            identificationProtocol = new String(b);
        }

        // SNI
        b = Record.getBytes8(buf);
        if (b.length == 0) {
            serverNameIndication = null;
        } else {
            serverNameIndication = new SNIHostName(b);
        }

        // List of SNIServerName
        len = Record.getInt16(buf);
        if (len == 0) {
            this.requestedServerNames = Collections.emptyList();
        } else {
            requestedServerNames = new ArrayList<>();
            while (len > 0) {
                b = Record.getBytes8(buf);
                requestedServerNames.add(new SNIHostName(new String(b)));
                len--;
            }
        }
        maximumPacketSize = buf.getInt();
        negotiatedMaxFragLen = buf.getInt();

        // Get creation time
        this.creationTime = buf.getLong();

        // Get Peer host & port
        b = Record.getBytes8(buf);
        if (b.length == 0) {
            this.host = "";
        } else {
            this.host = new String(b);
        }
        this.port = Record.getInt16(buf);

        // Peer certs.
        len = Record.getInt8(buf);
        if (len == 0) {
            this.peerCerts = null;
        } else {
            this.peerCerts = new X509Certificate[len];
            for (int i = 0; len > i; i++) {
                b = new byte[buf.getInt()];
                buf.get(b);
                try {
                    this.peerCerts[i] = X509Factory.cachedGetX509Cert(b);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
        }

        // Restore local certificates if cert algorithm(s) present.
        len = Record.getInt8(buf);
        if (len == 0) {
            this.localCerts = null;
        } else {
            String[] certAlgs = new String[len];
            int[] certCheckSums = new int[len];

            for (int i = 0; len > i; i++) {
                certAlgs[i] = new String(Record.getBytes8(buf));
                certCheckSums[i] = Record.getInt32(buf);
            }

            SSLPossession pos = X509Authentication.createPossession(
                    hc, certAlgs);
            boolean same = false;

            if (pos instanceof X509Possession x509Pos
                    && x509Pos.popCerts != null
                    && x509Pos.popCerts.length == len) {
                // Make sure we got the exact same cert chain.
                for (int i = 0; i < x509Pos.popCerts.length; i++) {
                    try {
                        byte[] encoded = x509Pos.popCerts[i].getEncoded();
                        String popAlg = x509Pos.popCerts[i]
                                .getPublicKey().getAlgorithm();

                        if (certCheckSums[i] == getChecksum(encoded)
                                && certAlgs[i].equals(popAlg)) {
                            // Use certs from cache.
                            x509Pos.popCerts[i] =
                                    X509Factory.cachedGetX509Cert(encoded);
                            same = true;
                        } else {
                            same = false;
                            break;
                        }
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                }
            }

            if (same) {
                this.localCerts = ((X509Possession) pos).popCerts;
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,session")) {
                    SSLLogger.fine("Restored " + len
                            + " local certificates from session ticket"
                            + " for algorithms " + Arrays.toString(certAlgs));
                }
            } else {
                this.localCerts = null;
                this.invalidated = true;
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,session")) {
                    SSLLogger.warning("Local certificates can not be restored "
                            + "from session ticket "
                            + "for algorithms " + Arrays.toString(certAlgs));
                }
            }
        }

        this.context = (SSLSessionContextImpl)
                hc.sslContext.engineGetServerSessionContext();
        this.lastUsedTime = System.currentTimeMillis();
    }

    // Some situations we cannot provide a stateless ticket, but after it
    // has been negotiated
    boolean isStatelessable() {
        // If there is no getMasterSecret with TLS1.2 or under, do not resume.
        if (!protocolVersion.useTLS13PlusSpec() &&
                getMasterSecret().getEncoded() == null) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.finest("No MasterSecret, cannot make stateless" +
                        " ticket");
            }
            return false;
        }

        if (boundValues != null && boundValues.size() > 0) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.finest("There are boundValues, cannot make" +
                        " stateless ticket");
            }
            return false;
        }

        return true;
    }

    /**
     * Write out a SSLSessionImpl in a byte array for a stateless session ticket
     */
    byte[] write() throws Exception {
        byte[] b;
        HandshakeOutStream hos = new HandshakeOutStream(null);

        hos.putInt16(protocolVersion.id);
        hos.putInt16(cipherSuite.id);

        // Local Supported signature algorithms
        hos.putInt8(localSupportedSignAlgs.size());
        for (SignatureScheme s : localSupportedSignAlgs) {
            hos.putInt16(s.id);
        }

        // PreSharedKey is only needed by TLSv1.3,
        // masterSecret is only needed by pre-TLSv1.3.
        if (protocolVersion.useTLS13PlusSpec()) {
            // PSK
            if (preSharedKey == null) {
                hos.putInt16(0);
            } else {
                hos.putBytes16(preSharedKey.getEncoded());
            }
        } else {
            // Master Secret
            if (getMasterSecret() == null) {
                hos.putInt16(0);
            } else {
                hos.putBytes16(masterSecret.getEncoded());
            }

            hos.putInt8(useExtendedMasterSecret ? 1 : 0);
        }

        // Identification Protocol
        if (identificationProtocol == null) {
            hos.putInt8(0);
        } else {
            hos.putInt8(identificationProtocol.length());
            hos.write(identificationProtocol.getBytes(), 0,
                    identificationProtocol.length());
        }

        // SNI
        if (serverNameIndication == null) {
            hos.putInt8(0);
        } else {
            b = serverNameIndication.getEncoded();
            hos.putInt8(b.length);
            hos.write(b, 0, b.length);
        }

        // List of SNIServerName
        hos.putInt16(requestedServerNames.size());
        if (requestedServerNames.size() > 0) {
            for (SNIServerName sn : requestedServerNames) {
                b = sn.getEncoded();
                hos.putInt8(b.length);
                hos.write(b, 0, b.length);
            }
        }

        // Buffer sizes
        hos.putInt32(maximumPacketSize);
        hos.putInt32(negotiatedMaxFragLen);

        // Creation time
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        hos.writeBytes(buffer.putLong(creationTime).array());

        // Peer Host & Port
        if (host == null || host.length() == 0) {
            hos.putInt8(0);
        } else {
            hos.putInt8(host.length());
            hos.writeBytes(host.getBytes());
        }
        hos.putInt16(port);

        // Peer certs.
        if (peerCerts == null || peerCerts.length == 0) {
            hos.putInt8(0);
        } else {
            hos.putInt8(peerCerts.length);
            for (X509Certificate c : peerCerts) {
                b = c.getEncoded();
                hos.putInt32(b.length);
                hos.writeBytes(b);
            }
        }

        // Local certificates' algorithms and checksums.
        // We don't include the complete local certificates in a session ticket
        // to decrease the size of ClientHello message.
        if (localCerts == null || localCerts.length == 0) {
            hos.putInt8(0);
        } else {
            hos.putInt8(localCerts.length);
            for (X509Certificate c : localCerts) {
                hos.putBytes8(c.getPublicKey().getAlgorithm().getBytes());
                hos.putInt32(getChecksum(c.getEncoded()));
            }
        }

        return hos.toByteArray();
    }

    private static int getChecksum(byte[] input) {
        Adler32 adler32 = new Adler32();
        adler32.update(input);
        return (int) adler32.getValue();
    }

    void setMasterSecret(SecretKey secret) {
        masterSecret = secret;
    }

    void setExporterMasterSecret(SecretKey secret) {
        exporterMasterSecret = secret;
    }

    void setRandoms(RandomCookie client, RandomCookie server) {
        clientRandom = client;
        serverRandom = server;
    }

    void setResumptionMasterSecret(SecretKey secret) {
        resumptionMasterSecret = secret;
    }

    void setPreSharedKey(SecretKey key) {
        preSharedKey = key;
    }

    void addChild(SSLSessionImpl session) {
        childSessions.add(session);
    }

    void setTicketAgeAdd(int ticketAgeAdd) {
        this.ticketAgeAdd = ticketAgeAdd;
    }

    void setPskIdentity(byte[] pskIdentity) {
        this.pskIdentity = pskIdentity;
    }

    byte[] incrTicketNonceCounter() {
        return new byte[] {ticketNonceCounter++};
    }

    boolean isPSKable() {
        return (ticketNonceCounter > 0);
    }

    /**
     * Returns the master secret ... treat with extreme caution!
     */
    SecretKey getMasterSecret() {
        return masterSecret;
    }

    SecretKey getResumptionMasterSecret() {
        return resumptionMasterSecret;
    }

    SecretKey getPreSharedKey() {
        sessionLock.lock();
        try {
            return preSharedKey;
        } finally {
            sessionLock.unlock();
        }
    }

    SecretKey consumePreSharedKey() {
        sessionLock.lock();
        try {
            return preSharedKey;
        } finally {
            preSharedKey = null;
            sessionLock.unlock();
        }
    }

    int getTicketAgeAdd() {
        return ticketAgeAdd;
    }

    String getIdentificationProtocol() {
        return this.identificationProtocol;
    }

    /* PSK identities created from new_session_ticket messages should only
     * be used once. This method will return the identity and then clear it
     * so it cannot be used again.
     */
    byte[] consumePskIdentity() {
        sessionLock.lock();
        try {
            return pskIdentity;
        } finally {
            pskIdentity = null;
            sessionLock.unlock();
        }
    }

    byte[] getPskIdentity() {
        return pskIdentity;
    }

    public boolean isPSK() {
        return (pskIdentity != null && pskIdentity.length > 0);
    }

    void setPeerCertificates(X509Certificate[] peer) {
        if (peerCerts == null) {
            peerCerts = peer;
        }
    }

    void setLocalCertificates(X509Certificate[] local) {
        localCerts = local;
    }

    void setLocalPrivateKey(PrivateKey privateKey) {
        localPrivateKey = privateKey;
    }

    void setPeerSupportedSignatureAlgorithms(
            Collection<SignatureScheme> signatureSchemes) {
        peerSupportedSignAlgs = signatureSchemes;
    }

    // TLS 1.2 only
    //
    // Per RFC 5246, If the client supports only the default hash
    // and signature algorithms, it MAY omit the
    // signature_algorithms extension.  If the client does not
    // support the default algorithms, or supports other hash
    // and signature algorithms (and it is willing to use them
    // for verifying messages sent by the server, i.e., server
    // certificates and server key exchange), it MUST send the
    // signature_algorithms extension, listing the algorithms it
    // is willing to accept.
    private static final ArrayList<SignatureScheme> defaultPeerSupportedSignAlgs =
            new ArrayList<>(Arrays.asList(SignatureScheme.RSA_PKCS1_SHA1,
                    SignatureScheme.DSA_SHA1,
                    SignatureScheme.ECDSA_SHA1));

    void setUseDefaultPeerSignAlgs() {
        useDefaultPeerSignAlgs = true;
        peerSupportedSignAlgs = defaultPeerSupportedSignAlgs;
    }

    // Returns the connection session.
    SSLSessionImpl finish() {
        if (useDefaultPeerSignAlgs) {
            peerSupportedSignAlgs = Collections.emptySet();
        }

        return this;
    }

    /**
     * Provide status response data obtained during the SSL handshake.
     *
     * @param responses a {@link List} of responses in binary form.
     */
    void setStatusResponses(List<byte[]> responses) {
        if (responses != null && !responses.isEmpty()) {
            statusResponses = responses;
        } else {
            statusResponses = Collections.emptyList();
        }
    }

    /**
     * Returns true iff this session may be resumed ... sessions are
     * usually resumable.  Security policies may suggest otherwise,
     * for example sessions that haven't been used for a while (say,
     * a working day) won't be resumable, and sessions might have a
     * maximum lifetime in any case.
     */
    boolean isRejoinable() {
        // TLS 1.3 can have no session id
        if (protocolVersion.useTLS13PlusSpec()) {
            return (!invalidated && isLocalAuthenticationValid());
        }
        return sessionId != null && sessionId.length() != 0 &&
                !invalidated && isLocalAuthenticationValid();
    }

    @Override
    public boolean isValid() {
        sessionLock.lock();
        try {
            return isRejoinable();
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Check if the authentication used when establishing this session
     * is still valid. Returns true if no authentication was used
     */
    private boolean isLocalAuthenticationValid() {
        if (localPrivateKey != null) {
            try {
                // if the private key is no longer valid, getAlgorithm()
                // should throw an exception
                // (e.g. Smartcard has been removed from the reader)
                localPrivateKey.getAlgorithm();
            } catch (Exception e) {
                invalidate();
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the ID for this session.  The ID is fixed for the
     * duration of the session; neither it, nor its value, changes.
     */
    @Override
    public byte[] getId() {
        return sessionId.getId();
    }

    /**
     * For server sessions, this returns the set of sessions which
     * are currently valid in this process.  For client sessions,
     * this returns null.
     */
    @Override
    public SSLSessionContext getSessionContext() {
        return context;
    }


    SessionId getSessionId() {
        return sessionId;
    }


    /**
     * Returns the cipher spec in use on this session
     */
    CipherSuite getSuite() {
        return cipherSuite;
    }

    /**
     * Resets the cipher spec in use on this session
     */
    void setSuite(CipherSuite suite) {
       cipherSuite = suite;

        if (SSLLogger.isOn && SSLLogger.isOn("session")) {
             SSLLogger.finest("Negotiating session:  " + this);
       }
    }

    /**
     * Return true if the session is currently re-established with a
     * session-resumption abbreviated initial handshake.
     */
    boolean isSessionResumption() {
        return isSessionResumption;
    }

    /**
     * Resets whether the session is re-established with a session-resumption
     * abbreviated initial handshake.
     */
    void setAsSessionResumption(boolean flag) {
        isSessionResumption = flag;
    }

    /**
     * Returns the name of the cipher suite in use on this session
     */
    @Override
    public String getCipherSuite() {
        return getSuite().name;
    }

    ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Returns the standard name of the protocol in use on this session
     */
    @Override
    public String getProtocol() {
        return getProtocolVersion().name;
    }

    /**
     * {@return the hashcode for this session}
     */
    @Override
    public int hashCode() {
        return sessionId.hashCode();
    }

    /**
     * Returns true if sessions have same ids, false otherwise.
     */
    @Override
    public boolean equals(Object obj) {

        if (obj == this) {
            return true;
        }

        return obj instanceof SSLSessionImpl other
                && sessionId != null
                && sessionId.equals(other.getSessionId());
    }


    /**
     * Return the cert chain presented by the peer in the
     * java.security.cert format.
     * Note: This method can be used only when using certificate-based
     * cipher suites; using it with non-certificate-based cipher suites
     * will throw an SSLPeerUnverifiedException.
     *
     * @return array of peer X.509 certs, with the peer's own cert
     *  first in the chain, and with the "root" CA last.
     */
    @Override
    public java.security.cert.Certificate[] getPeerCertificates()
            throws SSLPeerUnverifiedException {
        //
        // clone to preserve integrity of session ... caller can't
        // change record of peer identity even by accident, much
        // less do it intentionally.
        //
        if (peerCerts == null) {
            throw new SSLPeerUnverifiedException("peer not authenticated");
        }
        // Certs are immutable objects, therefore we don't clone them.
        // But do need to clone the array, so that nothing is inserted
        // into peerCerts.
        return peerCerts.clone();
    }

    /**
     * Return the cert chain presented to the peer in the
     * java.security.cert format.
     * Note: This method is useful only when using certificate-based
     * cipher suites.
     *
     * @return array of peer X.509 certs, with the peer's own cert
     *  first in the chain, and with the "root" CA last.
     */
    @Override
    public java.security.cert.Certificate[] getLocalCertificates() {
        //
        // clone to preserve integrity of session ... caller can't
        // change record of peer identity even by accident, much
        // less do it intentionally.
        return (localCerts == null ? null : localCerts.clone());
    }

    /**
     * Return the cert chain presented by the peer.
     * Note: This method can be used only when using certificate-based
     * cipher suites; using it with non-certificate-based cipher suites
     * will throw an SSLPeerUnverifiedException.
     *
     * @return array of peer X.509 certs, with the peer's own cert
     *  first in the chain, and with the "root" CA last.
     */
    public X509Certificate[] getCertificateChain()
            throws SSLPeerUnverifiedException {
        /*
         * clone to preserve integrity of session ... caller can't
         * change record of peer identity even by accident, much
         * less do it intentionally.
         */
        if (peerCerts != null) {
            return peerCerts.clone();
        } else {
            throw new SSLPeerUnverifiedException("peer not authenticated");
        }
    }

    /**
     * Return a List of status responses presented by the peer.
     * Note: This method can be used only when using certificate-based
     * server authentication; otherwise an empty {@code List} will be returned.
     *
     * @return an unmodifiable {@code List} of byte arrays, each consisting
     * of a DER-encoded OCSP response (see RFC 6960).  If no responses have
     * been presented by the server or non-certificate based server
     * authentication is used then an empty {@code List} is returned.
     */
    @Override
    public List<byte[]> getStatusResponses() {
        if (statusResponses == null || statusResponses.isEmpty()) {
            return Collections.emptyList();
        } else {
            // Clone both the list and the contents
            List<byte[]> responses = new ArrayList<>(statusResponses.size());
            for (byte[] respBytes : statusResponses) {
                responses.add(respBytes.clone());
            }
            return Collections.unmodifiableList(responses);
        }
    }

    /**
     * Returns the identity of the peer which was established as part of
     * defining the session.
     *
     * @return the peer's principal. Returns an X500Principal of the
     * end-entity certificate for X509-based cipher suites.
     *
     * @throws SSLPeerUnverifiedException if the peer's identity has not
     *          been verified
     */
    @Override
    public Principal getPeerPrincipal()
                throws SSLPeerUnverifiedException
    {
        if (peerCerts == null) {
            throw new SSLPeerUnverifiedException("peer not authenticated");
        }
        return peerCerts[0].getSubjectX500Principal();
    }

    /**
     * Returns the principal that was sent to the peer during handshaking.
     *
     * @return the principal sent to the peer. Returns an X500Principal
     * of the end-entity certificate for X509-based cipher suites.
     * If no principal was sent, then null is returned.
     */
    @Override
    public Principal getLocalPrincipal() {
        return ((localCerts == null || localCerts.length == 0) ? null :
                localCerts[0].getSubjectX500Principal());
    }

    /*
     * Return the time the ticket for this session was created.
     */
    public long getTicketCreationTime() {
        return ticketCreationTime;
    }

    /**
     * Returns the time this session was created.
     */
    @Override
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Returns the last time this session was used to initialize
     * a connection.
     */
    @Override
    public long getLastAccessedTime() {
        return (lastUsedTime != 0) ? lastUsedTime : creationTime;
    }

    void setLastAccessedTime(long time) {
        lastUsedTime = time;
    }


    /**
     * Returns the network address of the session's peer.  This
     * implementation does not insist that connections between
     * different ports on the same host must necessarily belong
     * to different sessions, though that is of course allowed.
     */
    public InetAddress getPeerAddress() {
        try {
            return InetAddress.getByName(host);
        } catch (java.net.UnknownHostException e) {
            return null;
        }
    }

    @Override
    public String getPeerHost() {
        return host;
    }

    /**
     * Need to provide the port info for caching sessions based on
     * host and port. Accessed by SSLSessionContextImpl
     */
    @Override
    public int getPeerPort() {
        return port;
    }

    void setContext(SSLSessionContextImpl ctx) {
        if (context == null) {
            context = ctx;
        }
    }

    /**
     * Invalidate a session.  Active connections may still exist, but
     * no connections will be able to rejoin this session.
     */
    @Override
    public void invalidate() {
        sessionLock.lock();
        try {
            if (context != null) {
                context.remove(sessionId);
                context = null;
            }

            if (invalidated) {
                return;
            }
            invalidated = true;
            if (SSLLogger.isOn && SSLLogger.isOn("session")) {
                 SSLLogger.finest("Invalidated session:  " + this);
            }
            for (SSLSessionImpl child : childSessions) {
                child.invalidate();
            }
        } finally {
            sessionLock.unlock();
        }
    }

    /*
     * Table of application-specific session data indexed by an application
     * key.
     */
    private final ConcurrentHashMap<String, Object> boundValues;

    /**
     * Assigns a session value.  Session change events are given if
     * appropriate, to any original value as well as the new value.
     */
    @Override
    public void putValue(String key, Object value) {
        if ((key == null) || (value == null)) {
            throw new IllegalArgumentException("arguments can not be null");
        }

        Object oldValue = boundValues.put(key, value);

        if (oldValue instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent e;

            e = new SSLSessionBindingEvent(this, key);
            ((SSLSessionBindingListener)oldValue).valueUnbound(e);
        }
        if (value instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent e;

            e = new SSLSessionBindingEvent(this, key);
            ((SSLSessionBindingListener)value).valueBound(e);
        }
        if (protocolVersion.useTLS13PlusSpec()) {
            updateNST = true;
        }
    }

    /**
     * Returns the specified session value.
     */
    @Override
    public Object getValue(String key) {
        if (key == null) {
            throw new IllegalArgumentException("argument can not be null");
        }

        return boundValues.get(key);
    }


    /**
     * Removes the specified session value, delivering a session changed
     * event as appropriate.
     */
    @Override
    public void removeValue(String key) {
        if (key == null) {
            throw new IllegalArgumentException("argument can not be null");
        }

        Object value = boundValues.remove(key);

        if (value instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent e;

            e = new SSLSessionBindingEvent(this, key);
            ((SSLSessionBindingListener)value).valueUnbound(e);
        }
        if (protocolVersion.useTLS13PlusSpec()) {
            updateNST = true;
        }
    }


    /**
     * Lists the names of the session values.
     */
    @Override
    public String[] getValueNames() {
        return boundValues.keySet().toArray(new String[0]);
    }

    /**
     * Use large packet sizes now or follow RFC 2246 packet sizes (2^14)
     * until changed.
     * <P>
     * In the TLS specification (section 6.2.1, RFC2246), it is not
     * recommended that the plaintext has more than 2^14 bytes.
     * However, some TLS implementations violate the specification.
     * This is a workaround for interoperability with these stacks.
     * <P>
     * Application could accept large fragments up to 2^15 bytes by
     * setting the system property jsse.SSLEngine.acceptLargeFragments
     * to "true".
     */
    private boolean acceptLargeFragments =
            Utilities.getBooleanProperty(
                    "jsse.SSLEngine.acceptLargeFragments", false);

    /**
     * Expand the buffer size of both SSL/TLS network packet and
     * application data.
     */
    void expandBufferSizes() {
        sessionLock.lock();
        try {
            acceptLargeFragments = true;
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Gets the current size of the largest SSL/TLS packet that is expected
     * when using this session.
     */
    @Override
    public int getPacketBufferSize() {
        sessionLock.lock();
        try {
            // Use the bigger packet size calculated from maximumPacketSize
            // and negotiatedMaxFragLen.
            int packetSize = 0;
            if (negotiatedMaxFragLen > 0) {
                packetSize = cipherSuite.calculatePacketSize(
                        negotiatedMaxFragLen, protocolVersion,
                        protocolVersion.isDTLS);
            }

            if (maximumPacketSize > 0) {
                return Math.max(maximumPacketSize, packetSize);
            }

            if (packetSize != 0) {
               return packetSize;
            }

            if (protocolVersion.isDTLS) {
                return DTLSRecord.maxRecordSize;
            } else {
                return acceptLargeFragments ?
                        SSLRecord.maxLargeRecordSize : SSLRecord.maxRecordSize;
            }
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Gets the current size of the largest application data that is
     * expected when using this session.
     */
    @Override
    public int getApplicationBufferSize() {
        sessionLock.lock();
        try {
            // Use the bigger fragment size calculated from maximumPacketSize
            // and negotiatedMaxFragLen.
            int fragmentSize = 0;
            if (maximumPacketSize > 0) {
                fragmentSize = cipherSuite.calculateFragSize(
                        maximumPacketSize, protocolVersion,
                        protocolVersion.isDTLS);
            }

            if (negotiatedMaxFragLen > 0) {
                return Math.max(negotiatedMaxFragLen, fragmentSize);
            }

            if (fragmentSize != 0) {
                return fragmentSize;
            }

            if (protocolVersion.isDTLS) {
                return Record.maxDataSize;
            } else {
                int maxPacketSize = acceptLargeFragments ?
                            SSLRecord.maxLargeRecordSize : SSLRecord.maxRecordSize;
                return (maxPacketSize - SSLRecord.headerSize);
            }
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Sets the negotiated maximum fragment length, as specified by the
     * max_fragment_length ClientHello extension in RFC 6066.
     *
     * @param  negotiatedMaxFragLen
     *         the negotiated maximum fragment length, or {@code -1} if
     *         no such length has been negotiated.
     */
    void setNegotiatedMaxFragSize(
            int negotiatedMaxFragLen) {
        sessionLock.lock();
        try {
            this.negotiatedMaxFragLen = negotiatedMaxFragLen;
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Get the negotiated maximum fragment length, as specified by the
     * max_fragment_length ClientHello extension in RFC 6066.
     *
     * @return the negotiated maximum fragment length, or {@code -1} if
     *         no such length has been negotiated.
     */
    int getNegotiatedMaxFragSize() {
        sessionLock.lock();
        try {
            return negotiatedMaxFragLen;
        } finally {
            sessionLock.unlock();
        }
    }

    void setMaximumPacketSize(int maximumPacketSize) {
        sessionLock.lock();
        try {
            this.maximumPacketSize = maximumPacketSize;
        } finally {
            sessionLock.unlock();
        }
    }

    int getMaximumPacketSize() {
        sessionLock.lock();
        try {
            return maximumPacketSize;
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Gets an array of supported signature algorithm names that the local
     * side is willing to verify.
     */
    @Override
    public String[] getLocalSupportedSignatureAlgorithms() {
        return SignatureScheme.getAlgorithmNames(localSupportedSignAlgs);
    }

    /**
     * Gets an array of supported signature schemes that the local side is
     * willing to verify.
     */
    public Collection<SignatureScheme> getLocalSupportedSignatureSchemes() {
        return localSupportedSignAlgs;
    }

    /**
     * Gets an array of supported signature algorithms that the peer is
     * able to verify.
     */
    @Override
    public String[] getPeerSupportedSignatureAlgorithms() {
        return SignatureScheme.getAlgorithmNames(peerSupportedSignAlgs);
    }

    /**
     * Obtains a <code>List</code> containing all {@link SNIServerName}s
     * of the requested Server Name Indication (SNI) extension.
     */
    @Override
    public List<SNIServerName> getRequestedServerNames() {
        return requestedServerNames;
    }

    /*
     * keyAlg is used for switching between Keys/Data.  If keyAlg is
     * non-null, we are producing a Key, otherwise data.
     */
    public Object exportKeyingMaterial(
            String keyAlg, String label, byte[] context, int length)
            throws SSLKeyException {

        // Global preconditions

        Objects.requireNonNull(label, "label can not be null");
        if (length < 1) {
            throw new IllegalArgumentException(
                    "length must be positive");
        }

        // Calculations are primarily based on protocol version.
        if (protocolVersion.useTLS13PlusSpec()) {

            // Unlikely, but check anyway.
            if (exporterMasterSecret == null) {
                throw new IllegalStateException(
                        "Exporter master secret not captured");
            }

            // TLS 1.3+ using HKDF-based calcs.
            //     TLS 1.3 (RFC 8446)

            // Check the label/context lengths:
            //       struct {
            //           uint16 length = Length;
            //           opaque label<7..255> = "tls13 " + Label;
            //           opaque context<0..255> = Context;
            //       } HkdfLabel;
            // label can have 249 bytes (+6 for "tls13 "), and context 255

            // RFC 8446 allows for length of 2^16-1 (65536), but RFC 5869
            // states:
            //
            //     L    length of output keying material in octets
            //          (<= 255*HashLen)
            if (length > (255 * cipherSuite.hashAlg.hashLength )) {
                throw new IllegalArgumentException(
                        "length is too large");
            }

            byte[] hkdfInfoLabel =
                        ("tls13 " + label).getBytes(StandardCharsets.UTF_8);
            if ((hkdfInfoLabel.length < 7) || hkdfInfoLabel.length > 255) {
                throw new IllegalArgumentException(
                        "label length outside range");
            }

            // If no context (null) is provided, RFC 8446 requires an empty
            // context be used, unlike RFC 5705.
            context = (context != null ? context : new byte[0]);
            if (context.length > 255) {
                throw new IllegalArgumentException(
                        "context length outside range");
            }

            // Do RFC 8446:7.1-7.5 calculations

            /*
             * TLS-Exporter(label, context_value, key_length) =
             *     HKDF-Expand-Label(Derive-Secret(Secret, label, ""),
             *         "exporter", Hash(context_value), key_length)
             *
             * Derive-Secret(Secret, Label, Messages) =
             *     HKDF-Expand-Label(Secret, Label,
             *         Transcript-Hash(Messages), Hash.length)
             */

            try {
                // Use the ciphersuite's hashAlg for these calcs.
                HashAlg hashAlg = cipherSuite.hashAlg;
                KDF hkdf = KDF.getInstance(hashAlg.hkdfAlgorithm);

                // First calculate the inner Derive-Secret(Secret, label, "")
                MessageDigest md;
                byte[] emptyHash;

                // Create the "" digest...
                try {
                    md = MessageDigest.getInstance(hashAlg.toString());
                    emptyHash = md.digest();
                } catch (NoSuchAlgorithmException nsae) {
                    throw new ProviderException(
                            "Hash algorithm " + cipherSuite.hashAlg.name +
                                    " is not available", nsae);
                }

                // ...then the hkdfInfo...
                byte[] hkdfInfo = SSLSecretDerivation.createHkdfInfo(
                        hkdfInfoLabel, emptyHash, hashAlg.hashLength);

                // ...then the "inner" HKDF-Expand-Label() to get the
                // derivedSecret that is used as the Secret in the "outer"
                // HKDF-Expand-Label().
                SecretKey derivedSecret = hkdf.deriveKey("TlsKey",
                        HKDFParameterSpec.expandOnly(exporterMasterSecret,
                        hkdfInfo, hashAlg.hashLength));
                try {
                    // Now do the "outer" HKDF-Expand-Label.
                    //     HKDF-Expand-Label(derivedSecret, "exporter",
                    //         Hash(context_value), key_length)

                    // If a context was supplied, use it, otherwise, use the
                    // previous hashed value of ""...
                    byte[] hash = ((context.length > 0) ?
                            md.digest(context) : emptyHash);

                    // ...now the hkdfInfo...
                    hkdfInfo = SSLSecretDerivation.createHkdfInfo(
                            ("tls13 exporter").getBytes(StandardCharsets.UTF_8),
                            hash, length);

                    // ...now the final expand.
                    return ((keyAlg != null) ?
                            hkdf.deriveKey(keyAlg,
                                    HKDFParameterSpec.expandOnly(derivedSecret,
                                            hkdfInfo, length)) :
                            hkdf.deriveData(
                                    HKDFParameterSpec.expandOnly(derivedSecret,
                                            hkdfInfo, length)));
                } finally {
                    KeyUtil.destroySecretKeys(derivedSecret);
                }
            } catch (Exception e) {
                // For whatever reason, we couldn't generate.  Wrap and return.
                throw new SSLKeyException("Couldn't generate Exporter/HKDF", e);
            }
        } else if (protocolVersion.useTLS10PlusSpec()) {

            // Unlikely, but check if randoms were not captured.
            if (clientRandom == null || serverRandom == null) {
                throw new IllegalStateException("Random nonces not captured");
            }

            // RFC 7505 using PRF-based calcs.
            //     TLS 1/1.1/1.2 (RFCs 2246/4346/5246) or
            //     DTLS 1.0/1.2 (RFCs 4347/6347)

            // Note:  In RFC 7627:
            //
            //   If a client or server chooses to continue with a full handshake
            //   without the extended master secret extension ... they MUST NOT
            //   export any key material based on the new master secret for any
            //   subsequent application-level authentication ... it MUST
            //   disable [RFC5705] ...
            if (!useExtendedMasterSecret) {
                throw new SSLKeyException(
                        "Exporters require extended master secrets");
            }

            // Check for a "disambiguating label string" (i.e. non-empty).
            // Don't see a max length restriction.
            if (label.isEmpty()) {
                throw new IllegalArgumentException(
                        "label length outside range");
            }

            // context length must fit in 2 unsigned bytes.
            if ((context != null) && (context.length > 0xFFFF)) {
                throw new IllegalArgumentException(
                        "Only 16-bit context lengths supported");
            }

            // Perform RFC 5705 calculations using the internal SunJCE PRF.
            String prfAlg;
            HashAlg hashAlg;
            if (protocolVersion == TLS12) {
                prfAlg = "SunTls12Prf";
                hashAlg = cipherSuite.hashAlg;
            } else {  //  all other cases
                prfAlg = "SunTlsPrf";
                hashAlg = H_NONE;
            }

            // Make a seed with randoms and optional context
            // Note that if context is null, it is omitted from the calc
            byte[] clientRandomBytes = clientRandom.randomBytes;
            byte[] serverRandomBytes = serverRandom.randomBytes;
            byte[] seed = new byte[
                    clientRandomBytes.length + serverRandomBytes.length +
                            ((context != null) ? (2 + context.length) : 0)];

            int pos = 0;
            System.arraycopy(
                    clientRandomBytes, 0, seed, pos, clientRandomBytes.length);
            pos += clientRandomBytes.length;
            System.arraycopy(
                    serverRandomBytes, 0, seed, pos, serverRandomBytes.length);
            pos += serverRandomBytes.length;
            if (context != null) {
                // RFC 5705, "If no context is provided, ..."
                seed[pos++] = (byte) ((context.length >> 8) & 0xFF);
                seed[pos++] = (byte) ((context.length) & 0xFF);
                System.arraycopy(
                        context, 0, seed, pos, context.length);
            }

            // Call the PRF function.
            try {
                @SuppressWarnings("deprecation")
                TlsPrfParameterSpec spec = new TlsPrfParameterSpec(
                        masterSecret, (keyAlg == null) ? "TlsKey" : keyAlg,
                        label, seed, length,
                        hashAlg.name, hashAlg.hashLength, hashAlg.blockSize);
                KeyGenerator kg = KeyGenerator.getInstance(prfAlg);
                kg.init(spec);
                SecretKey key = kg.generateKey();
                if (keyAlg != null) {
                    return key;
                } else {
                    byte[] b = key.getEncoded();
                    if (b == null) {
                        throw new UnsupportedOperationException(
                                "Could not extract encoding from SecretKey");
                    }
                    return b;
                }
            } catch (NoSuchAlgorithmException |
                     InvalidAlgorithmParameterException e) {
                throw new SSLKeyException("Could not generate Exporter/PRF", e);
            }
        } else {
            // SSLv3 is vulnerable to a triple handshake attack and can't be
            // mitigated by RFC 7627.  Don't support this or any other
            // unknown protocol.
            throw new SSLKeyException(
                    "Exporters not supported in " + protocolVersion);
        }
    }

    /**
     * Generate Exported Key Material (EKM) calculated according to the
     * algorithms defined in RFCs 5705/8446.
     */
    @Override
    public SecretKey exportKeyingMaterialKey(String keyAlg,
            String label, byte[] context, int length) throws SSLKeyException {

        Objects.requireNonNull(keyAlg, "keyAlg can not be null");
        if (keyAlg.isEmpty()) {
            throw new IllegalArgumentException(
                    "keyAlg is empty");
        }

        return (SecretKey) exportKeyingMaterial(keyAlg, label, context,
                length);
    }

    /**
     * Generate Exported Key Material (EKM) calculated according to the
     * algorithms defined in RFCs 5705/8446.
     */
    @Override
    public byte[] exportKeyingMaterialData(
            String label, byte[] context, int length) throws SSLKeyException {
        return (byte[])exportKeyingMaterial(null, label, context, length);
    }

    /** Returns a string representation of this SSL session */
    @Override
    public String toString() {
        return "Session(" + creationTime + "|" + getCipherSuite() + ")";
    }
}
