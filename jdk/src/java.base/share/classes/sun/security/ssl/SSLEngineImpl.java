/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.*;
import java.util.*;
import java.security.*;

import javax.crypto.BadPaddingException;

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;

/**
 * Implementation of an non-blocking SSLEngine.
 *
 * *Currently*, the SSLEngine code exists in parallel with the current
 * SSLSocket.  As such, the current implementation is using legacy code
 * with many of the same abstractions.  However, it varies in many
 * areas, most dramatically in the IO handling.
 *
 * There are three main I/O threads that can be existing in parallel:
 * wrap(), unwrap(), and beginHandshake().  We are encouraging users to
 * not call multiple instances of wrap or unwrap, because the data could
 * appear to flow out of the SSLEngine in a non-sequential order.  We
 * take all steps we can to at least make sure the ordering remains
 * consistent, but once the calls returns, anything can happen.  For
 * example, thread1 and thread2 both call wrap, thread1 gets the first
 * packet, thread2 gets the second packet, but thread2 gets control back
 * before thread1, and sends the data.  The receiving side would see an
 * out-of-order error.
 *
 * @author Brad Wetmore
 */
public final class SSLEngineImpl extends SSLEngine {

    //
    // Fields and global comments
    //

    /*
     * There's a state machine associated with each connection, which
     * among other roles serves to negotiate session changes.
     *
     * - START with constructor, until the TCP connection's around.
     * - HANDSHAKE picks session parameters before allowing traffic.
     *          There are many substates due to sequencing requirements
     *          for handshake messages.
     * - DATA may be transmitted.
     * - RENEGOTIATE state allows concurrent data and handshaking
     *          traffic ("same" substates as HANDSHAKE), and terminates
     *          in selection of new session (and connection) parameters
     * - ERROR state immediately precedes abortive disconnect.
     * - CLOSED when one side closes down, used to start the shutdown
     *          process.  SSL connection objects are not reused.
     *
     * State affects what SSL record types may legally be sent:
     *
     * - Handshake ... only in HANDSHAKE and RENEGOTIATE states
     * - App Data ... only in DATA and RENEGOTIATE states
     * - Alert ... in HANDSHAKE, DATA, RENEGOTIATE
     *
     * Re what may be received:  same as what may be sent, except that
     * HandshakeRequest handshaking messages can come from servers even
     * in the application data state, to request entry to RENEGOTIATE.
     *
     * The state machine within HANDSHAKE and RENEGOTIATE states controls
     * the pending session, not the connection state, until the change
     * cipher spec and "Finished" handshake messages are processed and
     * make the "new" session become the current one.
     *
     * NOTE: details of the SMs always need to be nailed down better.
     * The text above illustrates the core ideas.
     *
     *                +---->-------+------>--------->-------+
     *                |            |                        |
     *     <-----<    ^            ^  <-----<               |
     *START>----->HANDSHAKE>----->DATA>----->RENEGOTIATE    |
     *                v            v               v        |
     *                |            |               |        |
     *                +------------+---------------+        |
     *                |                                     |
     *                v                                     |
     *               ERROR>------>----->CLOSED<--------<----+
     *
     * ALSO, note that the purpose of handshaking (renegotiation is
     * included) is to assign a different, and perhaps new, session to
     * the connection.  The SSLv3 spec is a bit confusing on that new
     * protocol feature.
     */
    private int                 connectionState;

    private static final int    cs_START = 0;
    private static final int    cs_HANDSHAKE = 1;
    private static final int    cs_DATA = 2;
    private static final int    cs_RENEGOTIATE = 3;
    private static final int    cs_ERROR = 4;
    private static final int    cs_CLOSED = 6;

    /*
     * Once we're in state cs_CLOSED, we can continue to
     * wrap/unwrap until we finish sending/receiving the messages
     * for close_notify.
     */
    private boolean             inboundDone = false;
    private boolean             outboundDone = false;

    /*
     * The authentication context holds all information used to establish
     * who this end of the connection is (certificate chains, private keys,
     * etc) and who is trusted (e.g. as CAs or websites).
     */
    private SSLContextImpl      sslContext;

    /*
     * This connection is one of (potentially) many associated with
     * any given session.  The output of the handshake protocol is a
     * new session ... although all the protocol description talks
     * about changing the cipher spec (and it does change), in fact
     * that's incidental since it's done by changing everything that
     * is associated with a session at the same time.  (TLS/IETF may
     * change that to add client authentication w/o new key exchg.)
     */
    private Handshaker                  handshaker;
    private SSLSessionImpl              sess;
    private volatile SSLSessionImpl     handshakeSession;

    /*
     * Flag indicating if the next record we receive MUST be a Finished
     * message. Temporarily set during the handshake to ensure that
     * a change cipher spec message is followed by a finished message.
     */
    private boolean             expectingFinished;


    /*
     * If someone tries to closeInbound() (say at End-Of-Stream)
     * our engine having received a close_notify, we need to
     * notify the app that we may have a truncation attack underway.
     */
    private boolean             recvCN;

    /*
     * For improved diagnostics, we detail connection closure
     * If the engine is closed (connectionState >= cs_ERROR),
     * closeReason != null indicates if the engine was closed
     * because of an error or because or normal shutdown.
     */
    private SSLException        closeReason;

    /*
     * Per-connection private state that doesn't change when the
     * session is changed.
     */
    private ClientAuthType          doClientAuth =
                                            ClientAuthType.CLIENT_AUTH_NONE;
    private boolean                 enableSessionCreation = true;
    InputRecord                     inputRecord;
    OutputRecord                    outputRecord;
    private AccessControlContext    acc;

    // The cipher suites enabled for use on this connection.
    private CipherSuiteList             enabledCipherSuites;

    // the endpoint identification protocol
    private String                      identificationProtocol = null;

    // The cryptographic algorithm constraints
    private AlgorithmConstraints        algorithmConstraints = null;

    // The server name indication and matchers
    List<SNIServerName>         serverNames =
                                    Collections.<SNIServerName>emptyList();
    Collection<SNIMatcher>      sniMatchers =
                                    Collections.<SNIMatcher>emptyList();

    // Have we been told whether we're client or server?
    private boolean                     serverModeSet = false;
    private boolean                     roleIsServer;

    /*
     * The protocol versions enabled for use on this connection.
     *
     * Note: we support a pseudo protocol called SSLv2Hello which when
     * set will result in an SSL v2 Hello being sent with SSL (version 3.0)
     * or TLS (version 3.1, 3.2, etc.) version info.
     */
    private ProtocolList        enabledProtocols;

    /*
     * The SSL version associated with this connection.
     */
    private ProtocolVersion     protocolVersion;

    /*
     * security parameters for secure renegotiation.
     */
    private boolean             secureRenegotiation;
    private byte[]              clientVerifyData;
    private byte[]              serverVerifyData;

    /*
     * READ ME * READ ME * READ ME * READ ME * READ ME * READ ME *
     * IMPORTANT STUFF TO UNDERSTANDING THE SYNCHRONIZATION ISSUES.
     * READ ME * READ ME * READ ME * READ ME * READ ME * READ ME *
     *
     * There are several locks here.
     *
     * The primary lock is the per-instance lock used by
     * synchronized(this) and the synchronized methods.  It controls all
     * access to things such as the connection state and variables which
     * affect handshaking.  If we are inside a synchronized method, we
     * can access the state directly, otherwise, we must use the
     * synchronized equivalents.
     *
     * Note that we must never acquire the <code>this</code> lock after
     * <code>writeLock</code> or run the risk of deadlock.
     *
     * Grab some coffee, and be careful with any code changes.
     */
    private Object              wrapLock;
    private Object              unwrapLock;
    Object                      writeLock;

    /*
     * Whether local cipher suites preference in server side should be
     * honored during handshaking?
     */
    private boolean preferLocalCipherSuites = false;

    /*
     * whether DTLS handshake retransmissions should be enabled?
     */
    private boolean enableRetransmissions = false;

    /*
     * The maximum expected network packet size for SSL/TLS/DTLS records.
     */
    private int maximumPacketSize = 0;

    /*
     * Is this an instance for Datagram Transport Layer Security (DTLS)?
     */
    private final boolean isDTLS;

    /*
     * Class and subclass dynamic debugging support
     */
    private static final Debug debug = Debug.getInstance("ssl");

    //
    // Initialization/Constructors
    //

    /**
     * Constructor for an SSLEngine from SSLContext, without
     * host/port hints.  This Engine will not be able to cache
     * sessions, but must renegotiate everything by hand.
     */
    SSLEngineImpl(SSLContextImpl ctx, boolean isDTLS) {
        super();
        this.isDTLS = isDTLS;
        init(ctx, isDTLS);
    }

    /**
     * Constructor for an SSLEngine from SSLContext.
     */
    SSLEngineImpl(SSLContextImpl ctx, String host, int port, boolean isDTLS) {
        super(host, port);
        this.isDTLS = isDTLS;
        init(ctx, isDTLS);
    }

    /**
     * Initializes the Engine
     */
    private void init(SSLContextImpl ctx, boolean isDTLS) {
        if (debug != null && Debug.isOn("ssl")) {
            System.out.println("Using SSLEngineImpl.");
        }

        sslContext = ctx;
        sess = SSLSessionImpl.nullSession;
        handshakeSession = null;
        protocolVersion = isDTLS ?
                ProtocolVersion.DEFAULT_DTLS : ProtocolVersion.DEFAULT_TLS;

        /*
         * State is cs_START until we initialize the handshaker.
         *
         * Apps using SSLEngine are probably going to be server.
         * Somewhat arbitrary choice.
         */
        roleIsServer = true;
        connectionState = cs_START;

        // default server name indication
        serverNames =
            Utilities.addToSNIServerNameList(serverNames, getPeerHost());

        // default security parameters for secure renegotiation
        secureRenegotiation = false;
        clientVerifyData = new byte[0];
        serverVerifyData = new byte[0];

        enabledCipherSuites =
                sslContext.getDefaultCipherSuiteList(roleIsServer);
        enabledProtocols =
                sslContext.getDefaultProtocolList(roleIsServer);

        wrapLock = new Object();
        unwrapLock = new Object();
        writeLock = new Object();

        /*
         * Save the Access Control Context.  This will be used later
         * for a couple of things, including providing a context to
         * run tasks in, and for determining which credentials
         * to use for Subject based (JAAS) decisions
         */
        acc = AccessController.getContext();

        /*
         * All outbound application data goes through this OutputRecord,
         * other data goes through their respective records created
         * elsewhere.  All inbound data goes through this one
         * input record.
         */
        if (isDTLS) {
            enableRetransmissions = true;

            // SSLEngine needs no record local buffer
            outputRecord = new DTLSOutputRecord();
            inputRecord = new DTLSInputRecord();

        } else {
            outputRecord = new SSLEngineOutputRecord();
            inputRecord = new SSLEngineInputRecord();
        }

        maximumPacketSize = outputRecord.getMaxPacketSize();
    }

    /**
     * Initialize the handshaker object. This means:
     *
     *  . if a handshake is already in progress (state is cs_HANDSHAKE
     *    or cs_RENEGOTIATE), do nothing and return
     *
     *  . if the engine is already closed, throw an Exception (internal error)
     *
     *  . otherwise (cs_START or cs_DATA), create the appropriate handshaker
     *    object and advance the connection state (to cs_HANDSHAKE or
     *    cs_RENEGOTIATE, respectively).
     *
     * This method is called right after a new engine is created, when
     * starting renegotiation, or when changing client/server mode of the
     * engine.
     */
    private void initHandshaker() {
        switch (connectionState) {

        //
        // Starting a new handshake.
        //
        case cs_START:
        case cs_DATA:
            break;

        //
        // We're already in the middle of a handshake.
        //
        case cs_HANDSHAKE:
        case cs_RENEGOTIATE:
            return;

        //
        // Anyone allowed to call this routine is required to
        // do so ONLY if the connection state is reasonable...
        //
        default:
            throw new IllegalStateException("Internal error");
        }

        // state is either cs_START or cs_DATA
        if (connectionState == cs_START) {
            connectionState = cs_HANDSHAKE;
        } else { // cs_DATA
            connectionState = cs_RENEGOTIATE;
        }
        if (roleIsServer) {
            handshaker = new ServerHandshaker(this, sslContext,
                    enabledProtocols, doClientAuth,
                    protocolVersion, connectionState == cs_HANDSHAKE,
                    secureRenegotiation, clientVerifyData, serverVerifyData,
                    isDTLS);
            handshaker.setSNIMatchers(sniMatchers);
            handshaker.setUseCipherSuitesOrder(preferLocalCipherSuites);
        } else {
            handshaker = new ClientHandshaker(this, sslContext,
                    enabledProtocols,
                    protocolVersion, connectionState == cs_HANDSHAKE,
                    secureRenegotiation, clientVerifyData, serverVerifyData,
                    isDTLS);
            handshaker.setSNIServerNames(serverNames);
        }
        handshaker.setMaximumPacketSize(maximumPacketSize);
        handshaker.setEnabledCipherSuites(enabledCipherSuites);
        handshaker.setEnableSessionCreation(enableSessionCreation);

        outputRecord.initHandshaker();
    }

    /*
     * Report the current status of the Handshaker
     */
    private HandshakeStatus getHSStatus(HandshakeStatus hss) {

        if (hss != null) {
            return hss;
        }

        synchronized (this) {
            if (!outputRecord.isEmpty()) {
                // If no handshaking, special case to wrap alters.
                return HandshakeStatus.NEED_WRAP;
            } else if (handshaker != null) {
                if (handshaker.taskOutstanding()) {
                    return HandshakeStatus.NEED_TASK;
                } else if (isDTLS && !inputRecord.isEmpty()) {
                    return HandshakeStatus.NEED_UNWRAP_AGAIN;
                } else {
                    return HandshakeStatus.NEED_UNWRAP;
                }
            } else if (connectionState == cs_CLOSED) {
                /*
                 * Special case where we're closing, but
                 * still need the close_notify before we
                 * can officially be closed.
                 *
                 * Note isOutboundDone is taken care of by
                 * hasOutboundData() above.
                 */
                if (!isInboundDone()) {
                    return HandshakeStatus.NEED_UNWRAP;
                } // else not handshaking
            }

            return HandshakeStatus.NOT_HANDSHAKING;
        }
    }

    private synchronized void checkTaskThrown() throws SSLException {
        if (handshaker != null) {
            handshaker.checkThrown();
        }
    }

    //
    // Handshaking and connection state code
    //

    /*
     * Provides "this" synchronization for connection state.
     * Otherwise, you can access it directly.
     */
    private synchronized int getConnectionState() {
        return connectionState;
    }

    private synchronized void setConnectionState(int state) {
        connectionState = state;
    }

    /*
     * Get the Access Control Context.
     *
     * Used for a known context to
     * run tasks in, and for determining which credentials
     * to use for Subject-based (JAAS) decisions.
     */
    AccessControlContext getAcc() {
        return acc;
    }

    /*
     * Is a handshake currently underway?
     */
    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return getHSStatus(null);
    }

    /*
     * used by Handshaker to change the active write cipher, follows
     * the output of the CCS message.
     *
     * Also synchronized on "this" from readRecord/delegatedTask.
     */
    void changeWriteCiphers() throws IOException {

        Authenticator writeAuthenticator;
        CipherBox writeCipher;
        try {
            writeCipher = handshaker.newWriteCipher();
            writeAuthenticator = handshaker.newWriteAuthenticator();
        } catch (GeneralSecurityException e) {
            // "can't happen"
            throw new SSLException("Algorithm missing:  ", e);
        }

        outputRecord.changeWriteCiphers(writeAuthenticator, writeCipher);
    }

    /*
     * Updates the SSL version associated with this connection.
     * Called from Handshaker once it has determined the negotiated version.
     */
    synchronized void setVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
        outputRecord.setVersion(protocolVersion);
    }


    /**
     * Kickstart the handshake if it is not already in progress.
     * This means:
     *
     *  . if handshaking is already underway, do nothing and return
     *
     *  . if the engine is not connected or already closed, throw an
     *    Exception.
     *
     *  . otherwise, call initHandshake() to initialize the handshaker
     *    object and progress the state. Then, send the initial
     *    handshaking message if appropriate (always on clients and
     *    on servers when renegotiating).
     */
    private synchronized void kickstartHandshake() throws IOException {
        switch (connectionState) {

        case cs_START:
            if (!serverModeSet) {
                throw new IllegalStateException(
                    "Client/Server mode not yet set.");
            }
            initHandshaker();
            break;

        case cs_HANDSHAKE:
            // handshaker already setup, proceed
            break;

        case cs_DATA:
            if (!secureRenegotiation && !Handshaker.allowUnsafeRenegotiation) {
                throw new SSLHandshakeException(
                        "Insecure renegotiation is not allowed");
            }

            if (!secureRenegotiation) {
                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println(
                        "Warning: Using insecure renegotiation");
                }
            }

            // initialize the handshaker, move to cs_RENEGOTIATE
            initHandshaker();
            break;

        case cs_RENEGOTIATE:
            // handshaking already in progress, return
            return;

        default:
            // cs_ERROR/cs_CLOSED
            throw new SSLException("SSLEngine is closing/closed");
        }

        //
        // Kickstart handshake state machine if we need to ...
        //
        if (!handshaker.activated()) {
             // prior to handshaking, activate the handshake
            if (connectionState == cs_RENEGOTIATE) {
                // don't use SSLv2Hello when renegotiating
                handshaker.activate(protocolVersion);
            } else {
                handshaker.activate(null);
            }

            if (handshaker instanceof ClientHandshaker) {
                // send client hello
                handshaker.kickstart();
            } else {    // instanceof ServerHandshaker
                if (connectionState == cs_HANDSHAKE) {
                    // initial handshake, no kickstart message to send
                } else {
                    // we want to renegotiate, send hello request
                    handshaker.kickstart();
                }
            }
        }
    }

    /*
     * Start a SSLEngine handshake
     */
    @Override
    public void beginHandshake() throws SSLException {
        try {
            kickstartHandshake();
        } catch (Exception e) {
            fatal(Alerts.alert_handshake_failure,
                "Couldn't kickstart handshaking", e);
        }
    }


    //
    // Read/unwrap side
    //


    /**
     * Unwraps a buffer.  Does a variety of checks before grabbing
     * the unwrapLock, which blocks multiple unwraps from occurring.
     */
    @Override
    public SSLEngineResult unwrap(ByteBuffer netData, ByteBuffer[] appData,
            int offset, int length) throws SSLException {

        // check engine parameters
        checkEngineParas(netData, appData, offset, length, false);

        try {
            synchronized (unwrapLock) {
                return readNetRecord(netData, appData, offset, length);
            }
        } catch (SSLProtocolException spe) {
            // may be an unexpected handshake message
            fatal(Alerts.alert_unexpected_message, spe.getMessage(), spe);
            return null;  // make compiler happy
        } catch (Exception e) {
            /*
             * Don't reset position so it looks like we didn't
             * consume anything.  We did consume something, and it
             * got us into this situation, so report that much back.
             * Our days of consuming are now over anyway.
             */
            fatal(Alerts.alert_internal_error,
                "problem unwrapping net record", e);
            return null;  // make compiler happy
        }
    }

    private static void checkEngineParas(ByteBuffer netData,
            ByteBuffer[] appData, int offset, int len, boolean isForWrap) {

        if ((netData == null) || (appData == null)) {
            throw new IllegalArgumentException("src/dst is null");
        }

        if ((offset < 0) || (len < 0) || (offset > appData.length - len)) {
            throw new IndexOutOfBoundsException();
        }

        /*
         * If wrapping, make sure the destination bufffer is writable.
         */
        if (isForWrap && netData.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        for (int i = offset; i < offset + len; i++) {
            if (appData[i] == null) {
                throw new IllegalArgumentException(
                        "appData[" + i + "] == null");
            }

            /*
             * If unwrapping, make sure the destination bufffers are writable.
             */
            if (!isForWrap && appData[i].isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
        }
    }

    /*
     * Makes additional checks for unwrap, but this time more
     * specific to this packet and the current state of the machine.
     */
    private SSLEngineResult readNetRecord(ByteBuffer netData,
            ByteBuffer[] appData, int offset, int length) throws IOException {

        Status status = null;
        HandshakeStatus hsStatus = null;

        /*
         * See if the handshaker needs to report back some SSLException.
         */
        checkTaskThrown();

        /*
         * Check if we are closing/closed.
         */
        if (isInboundDone()) {
            return new SSLEngineResult(Status.CLOSED, getHSStatus(null), 0, 0);
        }

        /*
         * If we're still in cs_HANDSHAKE, make sure it's been
         * started.
         */
        synchronized (this) {
            if ((connectionState == cs_HANDSHAKE) ||
                    (connectionState == cs_START)) {
                kickstartHandshake();

                /*
                 * If there's still outbound data to flush, we
                 * can return without trying to unwrap anything.
                 */
                hsStatus = getHSStatus(null);

                if (hsStatus == HandshakeStatus.NEED_WRAP) {
                    return new SSLEngineResult(Status.OK, hsStatus, 0, 0);
                }
            }
        }

        /*
         * Grab a copy of this if it doesn't already exist,
         * and we can use it several places before anything major
         * happens on this side.  Races aren't critical
         * here.
         */
        if (hsStatus == null) {
            hsStatus = getHSStatus(null);
        }

        /*
         * If we have a task outstanding, this *MUST* be done before
         * doing any more unwrapping, because we could be in the middle
         * of receiving a handshake message, for example, a finished
         * message which would change the ciphers.
         */
        if (hsStatus == HandshakeStatus.NEED_TASK) {
            return new SSLEngineResult(Status.OK, hsStatus, 0, 0);
        }

        if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN) {
            Plaintext plainText = null;
            try {
                plainText = readRecord(null, null, 0, 0);
            } catch (SSLException e) {
                throw e;
            } catch (IOException e) {
                throw new SSLException("readRecord", e);
            }

            status = (isInboundDone() ? Status.CLOSED : Status.OK);
            hsStatus = getHSStatus(plainText.handshakeStatus);

            return new SSLEngineResult(
                    status, hsStatus, 0, 0, plainText.recordSN);
        }

        /*
         * Check the packet to make sure enough is here.
         * This will also indirectly check for 0 len packets.
         */
        int packetLen = 0;
        try {
            packetLen = inputRecord.bytesInCompletePacket(netData);
        } catch (SSLException ssle) {
            // Need to discard invalid records for DTLS protocols.
            if (isDTLS) {
                if (debug != null && Debug.isOn("ssl")) {
                    System.out.println(
                        Thread.currentThread().getName() +
                        " discard invalid record: " + ssle);
                }

                // invalid, discard the entire data [section 4.1.2.7, RFC 6347]
                int deltaNet = netData.remaining();
                netData.position(netData.limit());

                status = (isInboundDone() ? Status.CLOSED : Status.OK);
                hsStatus = getHSStatus(hsStatus);

                return new SSLEngineResult(status, hsStatus, deltaNet, 0, -1L);
            } else {
                throw ssle;
            }
        }

        // Is this packet bigger than SSL/TLS normally allows?
        if (packetLen > sess.getPacketBufferSize()) {
            int largestRecordSize = isDTLS ?
                    DTLSRecord.maxRecordSize : SSLRecord.maxLargeRecordSize;
            if ((packetLen <= largestRecordSize) && !isDTLS) {
                // Expand the expected maximum packet/application buffer
                // sizes.
                //
                // Only apply to SSL/TLS protocols.

                // Old behavior: shall we honor the System Property
                // "jsse.SSLEngine.acceptLargeFragments" if it is "false"?
                sess.expandBufferSizes();
            }

            // check the packet again
            largestRecordSize = sess.getPacketBufferSize();
            if (packetLen > largestRecordSize) {
                throw new SSLProtocolException(
                        "Input record too big: max = " +
                        largestRecordSize + " len = " + packetLen);
            }
        }

        int netPos = netData.position();
        int appRemains = 0;
        for (int i = offset; i < offset + length; i++) {
            if (appData[i] == null) {
                throw new IllegalArgumentException(
                        "appData[" + i + "] == null");
            }
            appRemains += appData[i].remaining();
        }

        /*
         * Check for OVERFLOW.
         *
         * Delay enforcing the application buffer free space requirement
         * until after the initial handshaking.
         */
        // synchronize connectionState?
        if ((connectionState == cs_DATA) ||
                (connectionState == cs_RENEGOTIATE)) {

            int FragLen = inputRecord.estimateFragmentSize(packetLen);
            if (FragLen > appRemains) {
                return new SSLEngineResult(
                        Status.BUFFER_OVERFLOW, hsStatus, 0, 0);
            }
        }

        // check for UNDERFLOW.
        if ((packetLen == -1) || (netData.remaining() < packetLen)) {
            return new SSLEngineResult(Status.BUFFER_UNDERFLOW, hsStatus, 0, 0);
        }

        /*
         * We're now ready to actually do the read.
         */
        Plaintext plainText = null;
        try {
            plainText = readRecord(netData, appData, offset, length);
        } catch (SSLException e) {
            throw e;
        } catch (IOException e) {
            throw new SSLException("readRecord", e);
        }

        /*
         * Check the various condition that we could be reporting.
         *
         * It's *possible* something might have happened between the
         * above and now, but it was better to minimally lock "this"
         * during the read process.  We'll return the current
         * status, which is more representative of the current state.
         *
         * status above should cover:  FINISHED, NEED_TASK
         */
        status = (isInboundDone() ? Status.CLOSED : Status.OK);
        hsStatus = getHSStatus(plainText.handshakeStatus);

        int deltaNet = netData.position() - netPos;
        int deltaApp = appRemains;
        for (int i = offset; i < offset + length; i++) {
            deltaApp -= appData[i].remaining();
        }

        return new SSLEngineResult(
                status, hsStatus, deltaNet, deltaApp, plainText.recordSN);
    }

    // the caller have synchronized readLock
    void expectingFinishFlight() {
        inputRecord.expectingFinishFlight();
    }

    /*
     * Actually do the read record processing.
     *
     * Returns a Status if it can make specific determinations
     * of the engine state.  In particular, we need to signal
     * that a handshake just completed.
     *
     * It would be nice to be symmetrical with the write side and move
     * the majority of this to SSLInputRecord, but there's too much
     * SSLEngine state to do that cleanly.  It must still live here.
     */
    private Plaintext readRecord(ByteBuffer netData,
            ByteBuffer[] appData, int offset, int length) throws IOException {

        /*
         * The various operations will return new sliced BB's,
         * this will avoid having to worry about positions and
         * limits in the netBB.
         */
        Plaintext plainText = null;

        if (getConnectionState() == cs_ERROR) {
            return Plaintext.PLAINTEXT_NULL;
        }

        /*
         * Read a record ... maybe emitting an alert if we get a
         * comprehensible but unsupported "hello" message during
         * format checking (e.g. V2).
         */
        try {
            if (isDTLS) {
                // Don't process the incoming record until all of the
                // buffered records get handled.
                plainText = inputRecord.acquirePlaintext();
            }

            if ((!isDTLS || plainText == null) && netData != null) {
                plainText = inputRecord.decode(netData);
            }
        } catch (UnsupportedOperationException unsoe) {         // SSLv2Hello
            // Hack code to deliver SSLv2 error message for SSL/TLS connections.
            if (!isDTLS) {
                outputRecord.encodeV2NoCipher();
            }

            fatal(Alerts.alert_unexpected_message, unsoe);
        } catch (BadPaddingException e) {
            /*
             * The basic SSLv3 record protection involves (optional)
             * encryption for privacy, and an integrity check ensuring
             * data origin authentication.  We do them both here, and
             * throw a fatal alert if the integrity check fails.
             */
            byte alertType = (connectionState != cs_DATA) ?
                    Alerts.alert_handshake_failure :
                    Alerts.alert_bad_record_mac;
            fatal(alertType, e.getMessage(), e);
        } catch (SSLHandshakeException she) {
            // may be record sequence number overflow
            fatal(Alerts.alert_handshake_failure, she);
        } catch (IOException ioe) {
            fatal(Alerts.alert_unexpected_message, ioe);
        }

        // plainText should never be null for TLS protocols
        HandshakeStatus hsStatus = null;
        if (!isDTLS || plainText != null) {
            hsStatus = processInputRecord(plainText, appData, offset, length);
        }

        if (hsStatus == null) {
            hsStatus = getHSStatus(null);
        }

        if (plainText == null) {
            plainText = new Plaintext();
        }
        plainText.handshakeStatus = hsStatus;

        return plainText;
    }

    /*
     * Process the record.
     */
    private synchronized HandshakeStatus processInputRecord(
            Plaintext plainText,
            ByteBuffer[] appData, int offset, int length) throws IOException {

        HandshakeStatus hsStatus = null;
        switch (plainText.contentType) {
            case Record.ct_handshake:
                /*
                 * Handshake messages always go to a pending session
                 * handshaker ... if there isn't one, create one.  This
                 * must work asynchronously, for renegotiation.
                 *
                 * NOTE that handshaking will either resume a session
                 * which was in the cache (and which might have other
                 * connections in it already), or else will start a new
                 * session (new keys exchanged) with just this connection
                 * in it.
                 */
                initHandshaker();
                if (!handshaker.activated()) {
                    // prior to handshaking, activate the handshake
                    if (connectionState == cs_RENEGOTIATE) {
                        // don't use SSLv2Hello when renegotiating
                        handshaker.activate(protocolVersion);
                    } else {
                        handshaker.activate(null);
                    }
                }

                /*
                 * process the handshake record ... may contain just
                 * a partial handshake message or multiple messages.
                 *
                 * The handshaker state machine will ensure that it's
                 * a finished message.
                 */
                handshaker.processRecord(plainText.fragment, expectingFinished);
                expectingFinished = false;

                if (handshaker.invalidated) {
                    finishHandshake();

                    // if state is cs_RENEGOTIATE, revert it to cs_DATA
                    if (connectionState == cs_RENEGOTIATE) {
                        connectionState = cs_DATA;
                    }
                } else if (handshaker.isDone()) {
                    // reset the parameters for secure renegotiation.
                    secureRenegotiation =
                                handshaker.isSecureRenegotiation();
                    clientVerifyData = handshaker.getClientVerifyData();
                    serverVerifyData = handshaker.getServerVerifyData();

                    sess = handshaker.getSession();
                    handshakeSession = null;
                    if (outputRecord.isEmpty()) {
                        hsStatus = finishHandshake();
                        connectionState = cs_DATA;
                    }

                    // No handshakeListeners here.  That's a
                    // SSLSocket thing.
                } else if (handshaker.taskOutstanding()) {
                    hsStatus = HandshakeStatus.NEED_TASK;
                }
                break;

            case Record.ct_application_data:
                // Pass this right back up to the application.
                if ((connectionState != cs_DATA)
                        && (connectionState != cs_RENEGOTIATE)
                        && (connectionState != cs_CLOSED)) {
                    throw new SSLProtocolException(
                            "Data received in non-data state: " +
                            connectionState);
                }

                if (expectingFinished) {
                    throw new SSLProtocolException
                            ("Expecting finished message, received data");
                }

                if (!inboundDone) {
                    ByteBuffer fragment = plainText.fragment;
                    int remains = fragment.remaining();

                    // Should have enough room in appData.
                    for (int i = offset;
                            ((i < (offset + length)) && (remains > 0)); i++) {
                        int amount = Math.min(appData[i].remaining(), remains);
                        fragment.limit(fragment.position() + amount);
                        appData[i].put(fragment);
                        remains -= amount;
                    }
                }

                break;

            case Record.ct_alert:
                recvAlert(plainText.fragment);
                break;

            case Record.ct_change_cipher_spec:
                if ((connectionState != cs_HANDSHAKE
                        && connectionState != cs_RENEGOTIATE)) {
                    // For the CCS message arriving in the wrong state
                    fatal(Alerts.alert_unexpected_message,
                            "illegal change cipher spec msg, conn state = "
                            + connectionState);
                } else if (plainText.fragment.remaining() != 1
                        || plainText.fragment.get() != 1) {
                    // For structural/content issues with the CCS
                    fatal(Alerts.alert_unexpected_message,
                            "Malformed change cipher spec msg");
                }

                //
                // The first message after a change_cipher_spec
                // record MUST be a "Finished" handshake record,
                // else it's a protocol violation.  We force this
                // to be checked by a minor tweak to the state
                // machine.
                //
                handshaker.receiveChangeCipherSpec();

                CipherBox readCipher;
                Authenticator readAuthenticator;
                try {
                    readCipher = handshaker.newReadCipher();
                    readAuthenticator = handshaker.newReadAuthenticator();
                } catch (GeneralSecurityException e) {
                    // can't happen
                    throw new SSLException("Algorithm missing:  ", e);
                }
                inputRecord.changeReadCiphers(readAuthenticator, readCipher);

                // next message MUST be a finished message
                expectingFinished = true;
                break;

            default:
                //
                // TLS requires that unrecognized records be ignored.
                //
                if (debug != null && Debug.isOn("ssl")) {
                    System.out.println(Thread.currentThread().getName() +
                            ", Received record type: " + plainText.contentType);
                }
                break;
        } // switch

        /*
         * We only need to check the sequence number state for
         * non-handshaking record.
         *
         * Note that in order to maintain the handshake status
         * properly, we check the sequence number after the last
         * record reading process. As we request renegotiation
         * or close the connection for wrapped sequence number
         * when there is enough sequence number space left to
         * handle a few more records, so the sequence number
         * of the last record cannot be wrapped.
         */
        hsStatus = getHSStatus(hsStatus);
        if (connectionState < cs_ERROR && !isInboundDone() &&
                (hsStatus == HandshakeStatus.NOT_HANDSHAKING) &&
                (inputRecord.seqNumIsHuge())) {
            /*
             * Ask for renegotiation when need to renew sequence number.
             *
             * Don't bother to kickstart the renegotiation when the local is
             * asking for it.
             */
            if (debug != null && Debug.isOn("ssl")) {
                System.out.println(Thread.currentThread().getName() +
                        ", request renegotiation " +
                        "to avoid sequence number overflow");
            }

            beginHandshake();

            hsStatus = getHSStatus(null);
        }

        return hsStatus;
    }


    //
    // write/wrap side
    //


    /**
     * Wraps a buffer.  Does a variety of checks before grabbing
     * the wrapLock, which blocks multiple wraps from occurring.
     */
    @Override
    public SSLEngineResult wrap(ByteBuffer[] appData,
            int offset, int length, ByteBuffer netData) throws SSLException {

        // check engine parameters
        checkEngineParas(netData, appData, offset, length, true);

        /*
         * We can be smarter about using smaller buffer sizes later.
         * For now, force it to be large enough to handle any valid record.
         */
        if (netData.remaining() < sess.getPacketBufferSize()) {
            return new SSLEngineResult(
                Status.BUFFER_OVERFLOW, getHSStatus(null), 0, 0);
        }

        try {
            synchronized (wrapLock) {
                return writeAppRecord(appData, offset, length, netData);
            }
        } catch (SSLProtocolException spe) {
            // may be an unexpected handshake message
            fatal(Alerts.alert_unexpected_message, spe.getMessage(), spe);
            return null;  // make compiler happy
        } catch (Exception e) {
            fatal(Alerts.alert_internal_error,
                "problem wrapping app data", e);
            return null;  // make compiler happy
        }
    }

    /*
     * Makes additional checks for unwrap, but this time more
     * specific to this packet and the current state of the machine.
     */
    private SSLEngineResult writeAppRecord(ByteBuffer[] appData,
            int offset, int length, ByteBuffer netData) throws IOException {

        Status status = null;
        HandshakeStatus hsStatus = null;

        /*
         * See if the handshaker needs to report back some SSLException.
         */
        checkTaskThrown();

        /*
         * short circuit if we're closed/closing.
         */
        if (isOutboundDone()) {
            return new SSLEngineResult(Status.CLOSED, getHSStatus(null), 0, 0);
        }

        /*
         * If we're still in cs_HANDSHAKE, make sure it's been
         * started.
         */
        synchronized (this) {
            if ((connectionState == cs_HANDSHAKE) ||
                (connectionState == cs_START)) {

                kickstartHandshake();

                /*
                 * If there's no HS data available to write, we can return
                 * without trying to wrap anything.
                 */
                hsStatus = getHSStatus(null);
                if (hsStatus == HandshakeStatus.NEED_UNWRAP) {
                    /*
                     * For DTLS, if the handshake state is
                     * HandshakeStatus.NEED_UNWRAP, a call to SSLEngine.wrap()
                     * means that the previous handshake packets (if delivered)
                     * get lost, and need retransmit the handshake messages.
                     */
                    if (!isDTLS || !enableRetransmissions ||
                            (handshaker == null) || outputRecord.firstMessage) {

                        return new SSLEngineResult(Status.OK, hsStatus, 0, 0);
                    }   // otherwise, need retransmission
                }
            }
        }

        /*
         * Grab a copy of this if it doesn't already exist,
         * and we can use it several places before anything major
         * happens on this side.  Races aren't critical
         * here.
         */
        if (hsStatus == null) {
            hsStatus = getHSStatus(null);
        }

        /*
         * If we have a task outstanding, this *MUST* be done before
         * doing any more wrapping, because we could be in the middle
         * of receiving a handshake message, for example, a finished
         * message which would change the ciphers.
         */
        if (hsStatus == HandshakeStatus.NEED_TASK) {
            return new SSLEngineResult(Status.OK, hsStatus, 0, 0);
        }

        /*
         * This will obtain any waiting outbound data, or will
         * process the outbound appData.
         */
        int netPos = netData.position();
        int appRemains = 0;
        for (int i = offset; i < offset + length; i++) {
            if (appData[i] == null) {
                throw new IllegalArgumentException(
                        "appData[" + i + "] == null");
            }
            appRemains += appData[i].remaining();
        }

        Ciphertext ciphertext = null;
        try {
            if (appRemains != 0) {
                synchronized (writeLock) {
                    ciphertext = writeRecord(appData, offset, length, netData);
                }
            } else {
                synchronized (writeLock) {
                    ciphertext = writeRecord(null, 0, 0, netData);
                }
            }
        } catch (SSLException e) {
            throw e;
        } catch (IOException e) {
            throw new SSLException("Write problems", e);
        }

        /*
         * writeRecord might have reported some status.
         * Now check for the remaining cases.
         *
         * status above should cover:  NEED_WRAP/FINISHED
         */
        status = (isOutboundDone() ? Status.CLOSED : Status.OK);
        hsStatus = getHSStatus(ciphertext.handshakeStatus);

        int deltaNet = netData.position() - netPos;
        int deltaApp = appRemains;
        for (int i = offset; i < offset + length; i++) {
            deltaApp -= appData[i].remaining();
        }

        return new SSLEngineResult(
                status, hsStatus, deltaApp, deltaNet, ciphertext.recordSN);
    }

    /*
     * Central point to write/get all of the outgoing data.
     */
    private Ciphertext writeRecord(ByteBuffer[] appData,
            int offset, int length, ByteBuffer netData) throws IOException {

        Ciphertext ciphertext = null;
        try {
            // Acquire the buffered to-be-delivered records or retransmissions.
            //
            // May have buffered records, or need retransmission if handshaking.
            if (!outputRecord.isEmpty() || (handshaker != null)) {
                ciphertext = outputRecord.acquireCiphertext(netData);
            }

            if ((ciphertext == null) && (appData != null)) {
                ciphertext = outputRecord.encode(
                        appData, offset, length, netData);
            }
        } catch (SSLHandshakeException she) {
            // may be record sequence number overflow
            fatal(Alerts.alert_handshake_failure, she);

            return Ciphertext.CIPHERTEXT_NULL;   // make the complier happy
        } catch (IOException e) {
            fatal(Alerts.alert_unexpected_message, e);

            return Ciphertext.CIPHERTEXT_NULL;   // make the complier happy
        }

        if (ciphertext == null) {
            return Ciphertext.CIPHERTEXT_NULL;
        }

        HandshakeStatus hsStatus = null;
        Ciphertext.RecordType recordType = ciphertext.recordType;
        if ((handshaker != null) &&
                (recordType.contentType == Record.ct_handshake) &&
                (recordType.handshakeType == HandshakeMessage.ht_finished) &&
                handshaker.isDone() && outputRecord.isEmpty()) {

            hsStatus = finishHandshake();
            connectionState = cs_DATA;
        }   // Otherwise, the followed call to getHSStatus() will help.

        /*
         * We only need to check the sequence number state for
         * non-handshaking record.
         *
         * Note that in order to maintain the handshake status
         * properly, we check the sequence number after the last
         * record writing process. As we request renegotiation
         * or close the connection for wrapped sequence number
         * when there is enough sequence number space left to
         * handle a few more records, so the sequence number
         * of the last record cannot be wrapped.
         */
        hsStatus = getHSStatus(hsStatus);
        if (connectionState < cs_ERROR && !isOutboundDone() &&
                (hsStatus == HandshakeStatus.NOT_HANDSHAKING) &&
                (outputRecord.seqNumIsHuge())) {
            /*
             * Ask for renegotiation when need to renew sequence number.
             *
             * Don't bother to kickstart the renegotiation when the local is
             * asking for it.
             */
            if (debug != null && Debug.isOn("ssl")) {
                System.out.println(Thread.currentThread().getName() +
                        ", request renegotiation " +
                        "to avoid sequence number overflow");
            }

            beginHandshake();

            hsStatus = getHSStatus(null);
        }
        ciphertext.handshakeStatus = hsStatus;

        return ciphertext;
    }

    private HandshakeStatus finishHandshake() {
        handshaker = null;
        inputRecord.setHandshakeHash(null);
        outputRecord.setHandshakeHash(null);
        connectionState = cs_DATA;

       return HandshakeStatus.FINISHED;
   }

    //
    // Close code
    //

    /**
     * Signals that no more outbound application data will be sent
     * on this <code>SSLEngine</code>.
     */
    private void closeOutboundInternal() {

        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                                    ", closeOutboundInternal()");
        }

        /*
         * Already closed, ignore
         */
        if (outboundDone) {
            return;
        }

        switch (connectionState) {

        /*
         * If we haven't even started yet, don't bother reading inbound.
         */
        case cs_START:
            try {
                outputRecord.close();
            } catch (IOException ioe) {
               // ignore
            }
            outboundDone = true;

            try {
                inputRecord.close();
            } catch (IOException ioe) {
               // ignore
            }
            inboundDone = true;
            break;

        case cs_ERROR:
        case cs_CLOSED:
            break;

        /*
         * Otherwise we indicate clean termination.
         */
        // case cs_HANDSHAKE:
        // case cs_DATA:
        // case cs_RENEGOTIATE:
        default:
            warning(Alerts.alert_close_notify);
            try {
                outputRecord.close();
            } catch (IOException ioe) {
               // ignore
            }
            outboundDone = true;
            break;
        }

        connectionState = cs_CLOSED;
    }

    @Override
    public synchronized void closeOutbound() {
        /*
         * Dump out a close_notify to the remote side
         */
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                                    ", called closeOutbound()");
        }

        closeOutboundInternal();
    }

    /**
     * Returns the outbound application data closure state
     */
    @Override
    public boolean isOutboundDone() {
        return outboundDone && outputRecord.isEmpty();
    }

    /**
     * Signals that no more inbound network data will be sent
     * to this <code>SSLEngine</code>.
     */
    private void closeInboundInternal() {

        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                                    ", closeInboundInternal()");
        }

        /*
         * Already closed, ignore
         */
        if (inboundDone) {
            return;
        }

        closeOutboundInternal();

        try {
            inputRecord.close();
        } catch (IOException ioe) {
           // ignore
        }
        inboundDone = true;

        connectionState = cs_CLOSED;
    }

    /*
     * Close the inbound side of the connection.  We grab the
     * lock here, and do the real work in the internal verison.
     * We do check for truncation attacks.
     */
    @Override
    public synchronized void closeInbound() throws SSLException {
        /*
         * Currently closes the outbound side as well.  The IETF TLS
         * working group has expressed the opinion that 1/2 open
         * connections are not allowed by the spec.  May change
         * someday in the future.
         */
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                                    ", called closeInbound()");
        }

        /*
         * No need to throw an Exception if we haven't even started yet.
         */
        if ((connectionState != cs_START) && !recvCN) {
            recvCN = true;  // Only receive the Exception once
            fatal(Alerts.alert_internal_error,
                "Inbound closed before receiving peer's close_notify: " +
                "possible truncation attack?");
        } else {
            /*
             * Currently, this is a no-op, but in case we change
             * the close inbound code later.
             */
            closeInboundInternal();
        }
    }

    /**
     * Returns the network inbound data closure state
     */
    @Override
    public synchronized boolean isInboundDone() {
        return inboundDone;
    }


    //
    // Misc stuff
    //


    /**
     * Returns the current <code>SSLSession</code> for this
     * <code>SSLEngine</code>
     * <P>
     * These can be long lived, and frequently correspond to an
     * entire login session for some user.
     */
    @Override
    public synchronized SSLSession getSession() {
        return sess;
    }

    @Override
    public synchronized SSLSession getHandshakeSession() {
        return handshakeSession;
    }

    synchronized void setHandshakeSession(SSLSessionImpl session) {
        // update the fragment size, which may be negotiated during handshaking
        inputRecord.changeFragmentSize(session.getNegotiatedMaxFragSize());
        outputRecord.changeFragmentSize(session.getNegotiatedMaxFragSize());

        handshakeSession = session;
    }

    /**
     * Returns a delegated <code>Runnable</code> task for
     * this <code>SSLEngine</code>.
     */
    @Override
    public synchronized Runnable getDelegatedTask() {
        if (handshaker != null) {
            return handshaker.getTask();
        }
        return null;
    }


    //
    // EXCEPTION AND ALERT HANDLING
    //

    /*
     * Send a warning alert.
     */
    void warning(byte description) {
        sendAlert(Alerts.alert_warning, description);
    }

    synchronized void fatal(byte description, String diagnostic)
            throws SSLException {
        fatal(description, diagnostic, null);
    }

    synchronized void fatal(byte description, Throwable cause)
            throws SSLException {
        fatal(description, null, cause);
    }

    /*
     * We've got a fatal error here, so start the shutdown process.
     *
     * Because of the way the code was written, we have some code
     * calling fatal directly when the "description" is known
     * and some throwing Exceptions which are then caught by higher
     * levels which then call here.  This code needs to determine
     * if one of the lower levels has already started the process.
     *
     * We won't worry about Error's, if we have one of those,
     * we're in worse trouble.  Note:  the networking code doesn't
     * deal with Errors either.
     */
    synchronized void fatal(byte description, String diagnostic,
            Throwable cause) throws SSLException {

        /*
         * If we have no further information, make a general-purpose
         * message for folks to see.  We generally have one or the other.
         */
        if (diagnostic == null) {
            diagnostic = "General SSLEngine problem";
        }
        if (cause == null) {
            cause = Alerts.getSSLException(description, cause, diagnostic);
        }

        /*
         * If we've already shutdown because of an error,
         * there is nothing we can do except rethrow the exception.
         *
         * Most exceptions seen here will be SSLExceptions.
         * We may find the occasional Exception which hasn't been
         * converted to a SSLException, so we'll do it here.
         */
        if (closeReason != null) {
            if ((debug != null) && Debug.isOn("ssl")) {
                System.out.println(Thread.currentThread().getName() +
                    ", fatal: engine already closed.  Rethrowing " +
                    cause.toString());
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            } else if (cause instanceof SSLException) {
                throw (SSLException)cause;
            } else if (cause instanceof Exception) {
                throw new SSLException("fatal SSLEngine condition", cause);
            }
        }

        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName()
                        + ", fatal error: " + description +
                        ": " + diagnostic + "\n" + cause.toString());
        }

        /*
         * Ok, this engine's going down.
         */
        int oldState = connectionState;
        connectionState = cs_ERROR;

        try {
            inputRecord.close();
        } catch (IOException ioe) {
           // ignore
        }
        inboundDone = true;

        sess.invalidate();
        if (handshakeSession != null) {
            handshakeSession.invalidate();
        }

        /*
         * If we haven't even started handshaking yet, no need
         * to generate the fatal close alert.
         */
        if (oldState != cs_START) {
            sendAlert(Alerts.alert_fatal, description);
        }

        if (cause instanceof SSLException) { // only true if != null
            closeReason = (SSLException)cause;
        } else {
            /*
             * Including RuntimeExceptions, but we'll throw those
             * down below.  The closeReason isn't used again,
             * except for null checks.
             */
            closeReason =
                Alerts.getSSLException(description, cause, diagnostic);
        }

        try {
            outputRecord.close();
        } catch (IOException ioe) {
           // ignore
        }
        outboundDone = true;

        connectionState = cs_CLOSED;

        if (cause instanceof RuntimeException) {
            throw (RuntimeException)cause;
        } else {
            throw closeReason;
        }
    }

    /*
     * Process an incoming alert ... caller must already have synchronized
     * access to "this".
     */
    private void recvAlert(ByteBuffer fragment) throws IOException {
        byte level = fragment.get();
        byte description = fragment.get();

        if (description == -1) { // check for short message
            fatal(Alerts.alert_illegal_parameter, "Short alert message");
        }

        if (debug != null && (Debug.isOn("record") ||
                Debug.isOn("handshake"))) {
            synchronized (System.out) {
                System.out.print(Thread.currentThread().getName());
                System.out.print(", RECV " + protocolVersion + " ALERT:  ");
                if (level == Alerts.alert_fatal) {
                    System.out.print("fatal, ");
                } else if (level == Alerts.alert_warning) {
                    System.out.print("warning, ");
                } else {
                    System.out.print("<level " + (0x0ff & level) + ">, ");
                }
                System.out.println(Alerts.alertDescription(description));
            }
        }

        if (level == Alerts.alert_warning) {
            if (description == Alerts.alert_close_notify) {
                if (connectionState == cs_HANDSHAKE) {
                    fatal(Alerts.alert_unexpected_message,
                                "Received close_notify during handshake");
                } else {
                    recvCN = true;
                    closeInboundInternal();  // reply to close
                }
            } else {

                //
                // The other legal warnings relate to certificates,
                // e.g. no_certificate, bad_certificate, etc; these
                // are important to the handshaking code, which can
                // also handle illegal protocol alerts if needed.
                //
                if (handshaker != null) {
                    handshaker.handshakeAlert(description);
                }
            }
        } else { // fatal or unknown level
            String reason = "Received fatal alert: "
                + Alerts.alertDescription(description);
            if (closeReason == null) {
                closeReason = Alerts.getSSLException(description, reason);
            }
            fatal(Alerts.alert_unexpected_message, reason);
        }
    }


    /*
     * Emit alerts.  Caller must have synchronized with "this".
     */
    private void sendAlert(byte level, byte description) {
        // the connectionState cannot be cs_START
        if (connectionState >= cs_CLOSED) {
            return;
        }

        // For initial handshaking, don't send alert message to peer if
        // handshaker has not started.
        //
        // Shall we send an fatal alter to terminate the connection gracefully?
        if (connectionState <= cs_HANDSHAKE &&
                (handshaker == null || !handshaker.started() ||
                        !handshaker.activated())) {
            return;
        }

        try {
            outputRecord.encodeAlert(level, description);
        } catch (IOException ioe) {
            // ignore
        }
    }


    //
    // VARIOUS OTHER METHODS (COMMON TO SSLSocket)
    //


    /**
     * Controls whether new connections may cause creation of new SSL
     * sessions.
     *
     * As long as handshaking has not started, we can change
     * whether we enable session creations.  Otherwise,
     * we will need to wait for the next handshake.
     */
    @Override
    public synchronized void setEnableSessionCreation(boolean flag) {
        enableSessionCreation = flag;

        if ((handshaker != null) && !handshaker.activated()) {
            handshaker.setEnableSessionCreation(enableSessionCreation);
        }
    }

    /**
     * Returns true if new connections may cause creation of new SSL
     * sessions.
     */
    @Override
    public synchronized boolean getEnableSessionCreation() {
        return enableSessionCreation;
    }


    /**
     * Sets the flag controlling whether a server mode engine
     * *REQUIRES* SSL client authentication.
     *
     * As long as handshaking has not started, we can change
     * whether client authentication is needed.  Otherwise,
     * we will need to wait for the next handshake.
     */
    @Override
    public synchronized void setNeedClientAuth(boolean flag) {
        doClientAuth = (flag ?
                ClientAuthType.CLIENT_AUTH_REQUIRED :
                ClientAuthType.CLIENT_AUTH_NONE);

        if ((handshaker != null) &&
                (handshaker instanceof ServerHandshaker) &&
                !handshaker.activated()) {
            ((ServerHandshaker) handshaker).setClientAuth(doClientAuth);
        }
    }

    @Override
    public synchronized boolean getNeedClientAuth() {
        return (doClientAuth == ClientAuthType.CLIENT_AUTH_REQUIRED);
    }

    /**
     * Sets the flag controlling whether a server mode engine
     * *REQUESTS* SSL client authentication.
     *
     * As long as handshaking has not started, we can change
     * whether client authentication is requested.  Otherwise,
     * we will need to wait for the next handshake.
     */
    @Override
    public synchronized void setWantClientAuth(boolean flag) {
        doClientAuth = (flag ?
                ClientAuthType.CLIENT_AUTH_REQUESTED :
                ClientAuthType.CLIENT_AUTH_NONE);

        if ((handshaker != null) &&
                (handshaker instanceof ServerHandshaker) &&
                !handshaker.activated()) {
            ((ServerHandshaker) handshaker).setClientAuth(doClientAuth);
        }
    }

    @Override
    public synchronized boolean getWantClientAuth() {
        return (doClientAuth == ClientAuthType.CLIENT_AUTH_REQUESTED);
    }


    /**
     * Sets the flag controlling whether the engine is in SSL
     * client or server mode.  Must be called before any SSL
     * traffic has started.
     */
    @Override
    @SuppressWarnings("fallthrough")
    public synchronized void setUseClientMode(boolean flag) {
        switch (connectionState) {

        case cs_START:
            /*
             * If we need to change the socket mode and the enabled
             * protocols and cipher suites haven't specifically been
             * set by the user, change them to the corresponding
             * default ones.
             */
            if (roleIsServer != (!flag)) {
                if (sslContext.isDefaultProtocolList(enabledProtocols)) {
                    enabledProtocols =
                            sslContext.getDefaultProtocolList(!flag);
                }

                if (sslContext.isDefaultCipherSuiteList(enabledCipherSuites)) {
                    enabledCipherSuites =
                            sslContext.getDefaultCipherSuiteList(!flag);
                }
            }

            roleIsServer = !flag;
            serverModeSet = true;
            break;

        case cs_HANDSHAKE:
            /*
             * If we have a handshaker, but haven't started
             * SSL traffic, we can throw away our current
             * handshaker, and start from scratch.  Don't
             * need to call doneConnect() again, we already
             * have the streams.
             */
            assert(handshaker != null);
            if (!handshaker.activated()) {
                /*
                 * If we need to change the socket mode and the enabled
                 * protocols and cipher suites haven't specifically been
                 * set by the user, change them to the corresponding
                 * default ones.
                 */
                if (roleIsServer != (!flag)) {
                    if (sslContext.isDefaultProtocolList(enabledProtocols)) {
                        enabledProtocols =
                                sslContext.getDefaultProtocolList(!flag);
                    }

                    if (sslContext.isDefaultCipherSuiteList(
                                                    enabledCipherSuites)) {
                        enabledCipherSuites =
                            sslContext.getDefaultCipherSuiteList(!flag);
                    }
                }

                roleIsServer = !flag;
                connectionState = cs_START;
                initHandshaker();
                break;
            }

            // If handshake has started, that's an error.  Fall through...

        default:
            if (debug != null && Debug.isOn("ssl")) {
                System.out.println(Thread.currentThread().getName() +
                    ", setUseClientMode() invoked in state = " +
                    connectionState);
            }

            /*
             * We can let them continue if they catch this correctly,
             * we don't need to shut this down.
             */
            throw new IllegalArgumentException(
                "Cannot change mode after SSL traffic has started");
        }
    }

    @Override
    public synchronized boolean getUseClientMode() {
        return !roleIsServer;
    }


    /**
     * Returns the names of the cipher suites which could be enabled for use
     * on an SSL connection.  Normally, only a subset of these will actually
     * be enabled by default, since this list may include cipher suites which
     * do not support the mutual authentication of servers and clients, or
     * which do not protect data confidentiality.  Servers may also need
     * certain kinds of certificates to use certain cipher suites.
     *
     * @return an array of cipher suite names
     */
    @Override
    public String[] getSupportedCipherSuites() {
        return sslContext.getSupportedCipherSuiteList().toStringArray();
    }

    /**
     * Controls which particular cipher suites are enabled for use on
     * this connection.  The cipher suites must have been listed by
     * getCipherSuites() as being supported.  Even if a suite has been
     * enabled, it might never be used if no peer supports it or the
     * requisite certificates (and private keys) are not available.
     *
     * @param suites Names of all the cipher suites to enable.
     */
    @Override
    public synchronized void setEnabledCipherSuites(String[] suites) {
        enabledCipherSuites = new CipherSuiteList(suites);
        if ((handshaker != null) && !handshaker.activated()) {
            handshaker.setEnabledCipherSuites(enabledCipherSuites);
        }
    }

    /**
     * Returns the names of the SSL cipher suites which are currently enabled
     * for use on this connection.  When an SSL engine is first created,
     * all enabled cipher suites <em>(a)</em> protect data confidentiality,
     * by traffic encryption, and <em>(b)</em> can mutually authenticate
     * both clients and servers.  Thus, in some environments, this value
     * might be empty.
     *
     * @return an array of cipher suite names
     */
    @Override
    public synchronized String[] getEnabledCipherSuites() {
        return enabledCipherSuites.toStringArray();
    }


    /**
     * Returns the protocols that are supported by this implementation.
     * A subset of the supported protocols may be enabled for this connection
     * @return an array of protocol names.
     */
    @Override
    public String[] getSupportedProtocols() {
        return sslContext.getSuportedProtocolList().toStringArray();
    }

    /**
     * Controls which protocols are enabled for use on
     * this connection.  The protocols must have been listed by
     * getSupportedProtocols() as being supported.
     *
     * @param protocols protocols to enable.
     * @exception IllegalArgumentException when one of the protocols
     *  named by the parameter is not supported.
     */
    @Override
    public synchronized void setEnabledProtocols(String[] protocols) {
        enabledProtocols = new ProtocolList(protocols);
        if ((handshaker != null) && !handshaker.activated()) {
            handshaker.setEnabledProtocols(enabledProtocols);
        }
    }

    @Override
    public synchronized String[] getEnabledProtocols() {
        return enabledProtocols.toStringArray();
    }

    /**
     * Returns the SSLParameters in effect for this SSLEngine.
     */
    @Override
    public synchronized SSLParameters getSSLParameters() {
        SSLParameters params = super.getSSLParameters();

        // the super implementation does not handle the following parameters
        params.setEndpointIdentificationAlgorithm(identificationProtocol);
        params.setAlgorithmConstraints(algorithmConstraints);
        params.setSNIMatchers(sniMatchers);
        params.setServerNames(serverNames);
        params.setUseCipherSuitesOrder(preferLocalCipherSuites);
        params.setEnableRetransmissions(enableRetransmissions);
        params.setMaximumPacketSize(maximumPacketSize);

        return params;
    }

    /**
     * Applies SSLParameters to this engine.
     */
    @Override
    public synchronized void setSSLParameters(SSLParameters params) {
        super.setSSLParameters(params);

        // the super implementation does not handle the following parameters
        identificationProtocol = params.getEndpointIdentificationAlgorithm();
        algorithmConstraints = params.getAlgorithmConstraints();
        preferLocalCipherSuites = params.getUseCipherSuitesOrder();
        enableRetransmissions = params.getEnableRetransmissions();
        maximumPacketSize = params.getMaximumPacketSize();

        if (maximumPacketSize != 0) {
            outputRecord.changePacketSize(maximumPacketSize);
        } else {
            // use the implicit maximum packet size.
            maximumPacketSize = outputRecord.getMaxPacketSize();
        }

        List<SNIServerName> sniNames = params.getServerNames();
        if (sniNames != null) {
            serverNames = sniNames;
        }

        Collection<SNIMatcher> matchers = params.getSNIMatchers();
        if (matchers != null) {
            sniMatchers = matchers;
        }

        if ((handshaker != null) && !handshaker.started()) {
            handshaker.setIdentificationProtocol(identificationProtocol);
            handshaker.setAlgorithmConstraints(algorithmConstraints);
            handshaker.setMaximumPacketSize(maximumPacketSize);
            if (roleIsServer) {
                handshaker.setSNIMatchers(sniMatchers);
                handshaker.setUseCipherSuitesOrder(preferLocalCipherSuites);
            } else {
                handshaker.setSNIServerNames(serverNames);
            }
        }
    }

    /**
     * Returns a printable representation of this end of the connection.
     */
    @Override
    public String toString() {
        StringBuilder retval = new StringBuilder(80);

        retval.append(Integer.toHexString(hashCode()));
        retval.append("[");
        retval.append("SSLEngine[hostname=");
        String host = getPeerHost();
        retval.append((host == null) ? "null" : host);
        retval.append(" port=");
        retval.append(Integer.toString(getPeerPort()));
        retval.append(" role=" + (roleIsServer ? "Server" : "Client"));
        retval.append("] ");
        retval.append(getSession().getCipherSuite());
        retval.append("]");

        return retval.toString();
    }
}
