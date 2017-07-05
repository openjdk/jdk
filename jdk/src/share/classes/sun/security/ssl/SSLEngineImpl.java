/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.ssl;

import java.io.*;
import java.nio.*;
import java.nio.ReadOnlyBufferException;
import java.util.LinkedList;
import java.security.*;

import javax.crypto.BadPaddingException;

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;

import com.sun.net.ssl.internal.ssl.X509ExtendedTrustManager;

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
 * Handshaking is still done the same way as SSLSocket using the normal
 * InputStream/OutputStream abstactions.  We create
 * ClientHandshakers/ServerHandshakers, which produce/consume the
 * handshaking data.  The transfer of the data is largely handled by the
 * HandshakeInStream/HandshakeOutStreams.  Lastly, the
 * InputRecord/OutputRecords still have the same functionality, except
 * that they are overridden with EngineInputRecord/EngineOutputRecord,
 * which provide SSLEngine-specific functionality.
 *
 * Some of the major differences are:
 *
 * EngineInputRecord/EngineOutputRecord/EngineWriter:
 *
 *      In order to avoid writing whole new control flows for
 *      handshaking, and to reuse most of the same code, we kept most
 *      of the actual handshake code the same.  As usual, reading
 *      handshake data may trigger output of more handshake data, so
 *      what we do is write this data to internal buffers, and wait for
 *      wrap() to be called to give that data a ride.
 *
 *      All data is routed through
 *      EngineInputRecord/EngineOutputRecord.  However, all handshake
 *      data (ct_alert/ct_change_cipher_spec/ct_handshake) are passed
 *      through to the the underlying InputRecord/OutputRecord, and
 *      the data uses the internal buffers.
 *
 *      Application data is handled slightly different, we copy the data
 *      directly from the src to the dst buffers, and do all operations
 *      on those buffers, saving the overhead of multiple copies.
 *
 *      In the case of an inbound record, unwrap passes the inbound
 *      ByteBuffer to the InputRecord.  If the data is handshake data,
 *      the data is read into the InputRecord's internal buffer.  If
 *      the data is application data, the data is decoded directly into
 *      the dst buffer.
 *
 *      In the case of an outbound record, when the write to the
 *      "real" OutputStream's would normally take place, instead we
 *      call back up to the EngineOutputRecord's version of
 *      writeBuffer, at which time we capture the resulting output in a
 *      ByteBuffer, and send that back to the EngineWriter for internal
 *      storage.
 *
 *      EngineWriter is responsible for "handling" all outbound
 *      data, be it handshake or app data, and for returning the data
 *      to wrap() in the proper order.
 *
 * ClientHandshaker/ServerHandshaker/Handshaker:
 *      Methods which relied on SSLSocket now have work on either
 *      SSLSockets or SSLEngines.
 *
 * @author Brad Wetmore
 */
final public class SSLEngineImpl extends SSLEngine {

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
     * ALSO, note that the the purpose of handshaking (renegotiation is
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
     * for close_notify.  EngineWriter handles outboundDone.
     */
    private boolean             inboundDone = false;

    EngineWriter                writer;

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
    private SSLSessionImpl      sess;
    private Handshaker          handshaker;

    /*
     * Client authentication be off, requested, or required.
     *
     * This will be used by both this class and SSLSocket's variants.
     */
    static final byte           clauth_none = 0;
    static final byte           clauth_requested = 1;
    static final byte           clauth_required = 2;

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
    private byte                        doClientAuth;
    private CipherSuiteList             enabledCipherSuites;
    private boolean                     enableSessionCreation = true;
    EngineInputRecord                   inputRecord;
    EngineOutputRecord                  outputRecord;
    private AccessControlContext        acc;

    // hostname identification algorithm, the hostname identification is
    // disabled by default.
    private String                      identificationAlg = null;

    // Have we been told whether we're client or server?
    private boolean                     serverModeSet = false;
    private boolean                     roleIsServer;

    /*
     * The protocols we support are SSL Version 3.0) and
     * TLS (version 3.1).
     * In addition we support a pseudo protocol called
     * SSLv2Hello which when set will result in an SSL v2 Hello
     * being sent with SSLv3 or TLSv1 version info.
     */
    private ProtocolList        enabledProtocols;

    /*
     * The SSL version associated with this connection.
     */
    private ProtocolVersion     protocolVersion = ProtocolVersion.DEFAULT;

    /*
     * Crypto state that's reinitialized when the session changes.
     */
    private MAC                 readMAC, writeMAC;
    private CipherBox           readCipher, writeCipher;
    // NOTE: compression state would be saved here


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
    SSLEngineImpl(SSLContextImpl ctx) {
        super();
        init(ctx);
    }

    /**
     * Constructor for an SSLEngine from SSLContext.
     */
    SSLEngineImpl(SSLContextImpl ctx, String host, int port) {
        super(host, port);
        init(ctx);
    }

    /**
     * Initializes the Engine
     */
    private void init(SSLContextImpl ctx) {
        if (debug != null && Debug.isOn("ssl")) {
            System.out.println("Using SSLEngineImpl.");
        }

        sslContext = ctx;
        sess = SSLSessionImpl.nullSession;

        /*
         * State is cs_START until we initialize the handshaker.
         *
         * Apps using SSLEngine are probably going to be server.
         * Somewhat arbitrary choice.
         */
        roleIsServer = true;
        connectionState = cs_START;

        /*
         * default read and write side cipher and MAC support
         *
         * Note:  compression support would go here too
         */
        readCipher = CipherBox.NULL;
        readMAC = MAC.NULL;
        writeCipher = CipherBox.NULL;
        writeMAC = MAC.NULL;

        enabledCipherSuites = CipherSuiteList.getDefault();
        enabledProtocols = ProtocolList.getDefault();

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
        outputRecord =
            new EngineOutputRecord(Record.ct_application_data, this);
        inputRecord = new EngineInputRecord(this);
        inputRecord.enableFormatChecks();

        writer = new EngineWriter();
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
     *    object, initialize it, and advance the connection state (to
     *    cs_HANDSHAKE or cs_RENEGOTIATE, respectively).
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
            handshaker = new ServerHandshaker
                        (this, sslContext, enabledProtocols, doClientAuth);
        } else {
            handshaker = new ClientHandshaker
                        (this, sslContext, enabledProtocols);
        }
        handshaker.enabledCipherSuites = enabledCipherSuites;
        handshaker.setEnableSessionCreation(enableSessionCreation);
        if (connectionState == cs_RENEGOTIATE) {
            // don't use SSLv2Hello when renegotiating
            handshaker.output.r.setHelloVersion(protocolVersion);
        }
    }

    /*
     * Report the current status of the Handshaker
     */
    private HandshakeStatus getHSStatus(HandshakeStatus hss) {

        if (hss != null) {
            return hss;
        }

        synchronized (this) {
            if (writer.hasOutboundData()) {
                return HandshakeStatus.NEED_WRAP;
            } else if (handshaker != null) {
                if (handshaker.taskOutstanding()) {
                    return HandshakeStatus.NEED_TASK;
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

    synchronized private void checkTaskThrown() throws SSLException {
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
    synchronized private int getConnectionState() {
        return connectionState;
    }

    synchronized private void setConnectionState(int state) {
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
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return getHSStatus(null);
    }

    /*
     * When a connection finishes handshaking by enabling use of a newly
     * negotiated session, each end learns about it in two halves (read,
     * and write).  When both read and write ciphers have changed, and the
     * last handshake message has been read, the connection has joined
     * (rejoined) the new session.
     *
     * NOTE:  The SSLv3 spec is rather unclear on the concepts here.
     * Sessions don't change once they're established (including cipher
     * suite and master secret) but connections can join them (and leave
     * them).  They're created by handshaking, though sometime handshaking
     * causes connections to join up with pre-established sessions.
     *
     * Synchronized on "this" from readRecord.
     */
    private void changeReadCiphers() throws SSLException {
        if (connectionState != cs_HANDSHAKE
                && connectionState != cs_RENEGOTIATE) {
            throw new SSLProtocolException(
                "State error, change cipher specs");
        }

        // ... create decompressor

        CipherBox oldCipher = readCipher;

        try {
            readCipher = handshaker.newReadCipher();
            readMAC = handshaker.newReadMAC();
        } catch (GeneralSecurityException e) {
            // "can't happen"
            throw (SSLException)new SSLException
                                ("Algorithm missing:  ").initCause(e);
        }

        /*
         * Dispose of any intermediate state in the underlying cipher.
         * For PKCS11 ciphers, this will release any attached sessions,
         * and thus make finalization faster.
         *
         * Since MAC's doFinal() is called for every SSL/TLS packet, it's
         * not necessary to do the same with MAC's.
         */
        oldCipher.dispose();
    }

    /*
     * used by Handshaker to change the active write cipher, follows
     * the output of the CCS message.
     *
     * Also synchronized on "this" from readRecord/delegatedTask.
     */
    void changeWriteCiphers() throws SSLException {
        if (connectionState != cs_HANDSHAKE
                && connectionState != cs_RENEGOTIATE) {
            throw new SSLProtocolException(
                "State error, change cipher specs");
        }

        // ... create compressor

        CipherBox oldCipher = writeCipher;

        try {
            writeCipher = handshaker.newWriteCipher();
            writeMAC = handshaker.newWriteMAC();
        } catch (GeneralSecurityException e) {
            // "can't happen"
            throw (SSLException)new SSLException
                                ("Algorithm missing:  ").initCause(e);
        }

        // See comment above.
        oldCipher.dispose();
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
        // Note that handshaker.kickstart() writes the message
        // to its HandshakeOutStream, which calls back into
        // SSLSocketImpl.writeRecord() to send it.
        //
        if (!handshaker.started()) {
            if (handshaker instanceof ClientHandshaker) {
                // send client hello
                handshaker.kickstart();
            } else {    // instanceof ServerHandshaker
                if (connectionState == cs_HANDSHAKE) {
                    // initial handshake, no kickstart message to send
                } else {
                    // we want to renegotiate, send hello request
                    handshaker.kickstart();
                    // hello request is not included in the handshake
                    // hashes, reset them
                    handshaker.handshakeHash.reset();
                }
            }
        }
    }

    /*
     * Start a SSLEngine handshake
     */
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
     * the unwrapLock, which blocks multiple unwraps from occuring.
     */
    public SSLEngineResult unwrap(ByteBuffer netData, ByteBuffer [] appData,
            int offset, int length) throws SSLException {

        EngineArgs ea = new EngineArgs(netData, appData, offset, length);

        try {
            synchronized (unwrapLock) {
                return readNetRecord(ea);
            }
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
        } finally {
            /*
             * Just in case something failed to reset limits properly.
             */
            ea.resetLim();
        }
    }

    /*
     * Makes additional checks for unwrap, but this time more
     * specific to this packet and the current state of the machine.
     */
    private SSLEngineResult readNetRecord(EngineArgs ea) throws IOException {

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
            return new SSLEngineResult(
                Status.OK, hsStatus, 0, 0);
        }

        /*
         * Check the packet to make sure enough is here.
         * This will also indirectly check for 0 len packets.
         */
        int packetLen = inputRecord.bytesInCompletePacket(ea.netData);

        // Is this packet bigger than SSL/TLS normally allows?
        if (packetLen > sess.getPacketBufferSize()) {
            if (packetLen > Record.maxLargeRecordSize) {
                throw new SSLProtocolException(
                    "Input SSL/TLS record too big: max = " +
                    Record.maxLargeRecordSize +
                    " len = " + packetLen);
            } else {
                // Expand the expected maximum packet/application buffer
                // sizes.
                sess.expandBufferSizes();
            }
        }

        /*
         * Check for OVERFLOW.
         *
         * To be considered: We could delay enforcing the application buffer
         * free space requirement until after the initial handshaking.
         */
        if ((packetLen - Record.headerSize) > ea.getAppRemaining()) {
            return new SSLEngineResult(Status.BUFFER_OVERFLOW, hsStatus, 0, 0);
        }

        // check for UNDERFLOW.
        if ((packetLen == -1) || (ea.netData.remaining() < packetLen)) {
            return new SSLEngineResult(
                Status.BUFFER_UNDERFLOW, hsStatus, 0, 0);
        }

        /*
         * We're now ready to actually do the read.
         * The only result code we really need to be exactly
         * right is the HS finished, for signaling to
         * HandshakeCompletedListeners.
         */
        try {
            hsStatus = readRecord(ea);
        } catch (SSLException e) {
            throw e;
        } catch (IOException e) {
            SSLException ex = new SSLException("readRecord");
            ex.initCause(e);
            throw ex;
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
        hsStatus = getHSStatus(hsStatus);

        return new SSLEngineResult(status, hsStatus,
            ea.deltaNet(), ea.deltaApp());
    }

    /*
     * Actually do the read record processing.
     *
     * Returns a Status if it can make specific determinations
     * of the engine state.  In particular, we need to signal
     * that a handshake just completed.
     *
     * It would be nice to be symmetrical with the write side and move
     * the majority of this to EngineInputRecord, but there's too much
     * SSLEngine state to do that cleanly.  It must still live here.
     */
    private HandshakeStatus readRecord(EngineArgs ea) throws IOException {

        HandshakeStatus hsStatus = null;

        /*
         * The various operations will return new sliced BB's,
         * this will avoid having to worry about positions and
         * limits in the netBB.
         */
        ByteBuffer readBB = null;
        ByteBuffer decryptedBB = null;

        if (getConnectionState() != cs_ERROR) {

            /*
             * Read a record ... maybe emitting an alert if we get a
             * comprehensible but unsupported "hello" message during
             * format checking (e.g. V2).
             */
            try {
                readBB = inputRecord.read(ea.netData);
            } catch (IOException e) {
                fatal(Alerts.alert_unexpected_message, e);
            }

            /*
             * The basic SSLv3 record protection involves (optional)
             * encryption for privacy, and an integrity check ensuring
             * data origin authentication.  We do them both here, and
             * throw a fatal alert if the integrity check fails.
             */
            try {
                decryptedBB = inputRecord.decrypt(readCipher, readBB);
            } catch (BadPaddingException e) {
                // RFC 2246 states that decryption_failed should be used
                // for this purpose. However, that allows certain attacks,
                // so we just send bad record MAC. We also need to make
                // sure to always check the MAC to avoid a timing attack
                // for the same issue. See paper by Vaudenay et al.
                //
                // rewind the BB if necessary.
                readBB.rewind();

                inputRecord.checkMAC(readMAC, readBB);

                // use the same alert types as for MAC failure below
                byte alertType = (inputRecord.contentType() ==
                    Record.ct_handshake) ?
                        Alerts.alert_handshake_failure :
                        Alerts.alert_bad_record_mac;
                fatal(alertType, "Invalid padding", e);
            }

            if (!inputRecord.checkMAC(readMAC, decryptedBB)) {
                if (inputRecord.contentType() == Record.ct_handshake) {
                    fatal(Alerts.alert_handshake_failure,
                        "bad handshake record MAC");
                } else {
                    fatal(Alerts.alert_bad_record_mac, "bad record MAC");
                }
            }

            // if (!inputRecord.decompress(c))
            //     fatal(Alerts.alert_decompression_failure,
            //     "decompression failure");


            /*
             * Process the record.
             */

            synchronized (this) {
                switch (inputRecord.contentType()) {
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

                    /*
                     * process the handshake record ... may contain just
                     * a partial handshake message or multiple messages.
                     *
                     * The handshaker state machine will ensure that it's
                     * a finished message.
                     */
                    handshaker.process_record(inputRecord, expectingFinished);
                    expectingFinished = false;

                    if (handshaker.isDone()) {
                        sess = handshaker.getSession();
                        if (!writer.hasOutboundData()) {
                            hsStatus = HandshakeStatus.FINISHED;
                        }
                        handshaker = null;
                        connectionState = cs_DATA;

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

                    /*
                     * Don't return data once the inbound side is
                     * closed.
                     */
                    if (!inboundDone) {
                        ea.scatter(decryptedBB.slice());
                    }
                    break;

                case Record.ct_alert:
                    recvAlert();
                    break;

                case Record.ct_change_cipher_spec:
                    if ((connectionState != cs_HANDSHAKE
                                && connectionState != cs_RENEGOTIATE)
                            || inputRecord.available() != 1
                            || inputRecord.read() != 1) {
                        fatal(Alerts.alert_unexpected_message,
                            "illegal change cipher spec msg, state = "
                            + connectionState);
                    }

                    //
                    // The first message after a change_cipher_spec
                    // record MUST be a "Finished" handshake record,
                    // else it's a protocol violation.  We force this
                    // to be checked by a minor tweak to the state
                    // machine.
                    //
                    changeReadCiphers();
                    // next message MUST be a finished message
                    expectingFinished = true;
                    break;

                default:
                    //
                    // TLS requires that unrecognized records be ignored.
                    //
                    if (debug != null && Debug.isOn("ssl")) {
                        System.out.println(threadName() +
                            ", Received record type: "
                            + inputRecord.contentType());
                    }
                    break;
                } // switch
            } // synchronized (this)
        }

        return hsStatus;
    }


    //
    // write/wrap side
    //


    /**
     * Wraps a buffer.  Does a variety of checks before grabbing
     * the wrapLock, which blocks multiple wraps from occuring.
     */
    public SSLEngineResult wrap(ByteBuffer [] appData,
            int offset, int length, ByteBuffer netData) throws SSLException {

        EngineArgs ea = new EngineArgs(appData, offset, length, netData);

        /*
         * We can be smarter about using smaller buffer sizes later.
         * For now, force it to be large enough to handle any
         * valid SSL/TLS record.
         */
        if (netData.remaining() < outputRecord.maxRecordSize) {
            return new SSLEngineResult(
                Status.BUFFER_OVERFLOW, getHSStatus(null), 0, 0);
        }

        try {
            synchronized (wrapLock) {
                return writeAppRecord(ea);
            }
        } catch (Exception e) {
            ea.resetPos();

            fatal(Alerts.alert_internal_error,
                "problem unwrapping net record", e);
            return null;  // make compiler happy
        } finally {
            /*
             * Just in case something didn't reset limits properly.
             */
            ea.resetLim();
        }
    }

    /*
     * Makes additional checks for unwrap, but this time more
     * specific to this packet and the current state of the machine.
     */
    private SSLEngineResult writeAppRecord(EngineArgs ea) throws IOException {

        Status status = null;
        HandshakeStatus hsStatus = null;

        /*
         * See if the handshaker needs to report back some SSLException.
         */
        checkTaskThrown();

        /*
         * short circuit if we're closed/closing.
         */
        if (writer.isOutboundDone()) {
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
         * doing any more wrapping, because we could be in the middle
         * of receiving a handshake message, for example, a finished
         * message which would change the ciphers.
         */
        if (hsStatus == HandshakeStatus.NEED_TASK) {
            return new SSLEngineResult(
                Status.OK, hsStatus, 0, 0);
        }

        /*
         * This will obtain any waiting outbound data, or will
         * process the outbound appData.
         */
        try {
            synchronized (writeLock) {
                hsStatus = writeRecord(outputRecord, ea);
            }
        } catch (SSLException e) {
            throw e;
        } catch (IOException e) {
            SSLException ex = new SSLException("Write problems");
            ex.initCause(e);
            throw ex;
        }

        /*
         * writeRecord might have reported some status.
         * Now check for the remaining cases.
         *
         * status above should cover:  NEED_WRAP/FINISHED
         */
        status = (isOutboundDone() ? Status.CLOSED : Status.OK);
        hsStatus = getHSStatus(hsStatus);

        return new SSLEngineResult(status, hsStatus,
            ea.deltaApp(), ea.deltaNet());
    }

    /*
     * Central point to write/get all of the outgoing data.
     */
    private HandshakeStatus writeRecord(EngineOutputRecord eor,
            EngineArgs ea) throws IOException {

        // eventually compress as well.
        return writer.writeRecord(eor, ea, writeMAC, writeCipher);
    }

    /*
     * Non-application OutputRecords go through here.
     */
    void writeRecord(EngineOutputRecord eor) throws IOException {
        // eventually compress as well.
        writer.writeRecord(eor, writeMAC, writeCipher);
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
            System.out.println(threadName() + ", closeOutboundInternal()");
        }

        /*
         * Already closed, ignore
         */
        if (writer.isOutboundDone()) {
            return;
        }

        switch (connectionState) {

        /*
         * If we haven't even started yet, don't bother reading inbound.
         */
        case cs_START:
            writer.closeOutbound();
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
            writer.closeOutbound();
            break;
        }

        // See comment in changeReadCiphers()
        writeCipher.dispose();

        connectionState = cs_CLOSED;
    }

    synchronized public void closeOutbound() {
        /*
         * Dump out a close_notify to the remote side
         */
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(threadName() + ", called closeOutbound()");
        }

        closeOutboundInternal();
    }

    /**
     * Returns the outbound application data closure state
     */
    public boolean isOutboundDone() {
        return writer.isOutboundDone();
    }

    /**
     * Signals that no more inbound network data will be sent
     * to this <code>SSLEngine</code>.
     */
    private void closeInboundInternal() {

        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(threadName() + ", closeInboundInternal()");
        }

        /*
         * Already closed, ignore
         */
        if (inboundDone) {
            return;
        }

        closeOutboundInternal();
        inboundDone = true;

        // See comment in changeReadCiphers()
        readCipher.dispose();

        connectionState = cs_CLOSED;
    }

    /*
     * Close the inbound side of the connection.  We grab the
     * lock here, and do the real work in the internal verison.
     * We do check for truncation attacks.
     */
    synchronized public void closeInbound() throws SSLException {
        /*
         * Currently closes the outbound side as well.  The IETF TLS
         * working group has expressed the opinion that 1/2 open
         * connections are not allowed by the spec.  May change
         * someday in the future.
         */
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(threadName() + ", called closeInbound()");
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
    synchronized public boolean isInboundDone() {
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
    synchronized public SSLSession getSession() {
        return sess;
    }

    /**
     * Returns a delegated <code>Runnable</code> task for
     * this <code>SSLEngine</code>.
     */
    synchronized public Runnable getDelegatedTask() {
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
                System.out.println(threadName() +
                    ", fatal: engine already closed.  Rethrowing " +
                    cause.toString());
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            } else if (cause instanceof SSLException) {
                throw (SSLException)cause;
            } else if (cause instanceof Exception) {
                SSLException ssle = new SSLException(
                    "fatal SSLEngine condition");
                ssle.initCause(cause);
                throw ssle;
            }
        }

        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(threadName()
                        + ", fatal error: " + description +
                        ": " + diagnostic + "\n" + cause.toString());
        }

        /*
         * Ok, this engine's going down.
         */
        int oldState = connectionState;
        connectionState = cs_ERROR;

        inboundDone = true;

        sess.invalidate();

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

        writer.closeOutbound();

        connectionState = cs_CLOSED;

        // See comment in changeReadCiphers()
        readCipher.dispose();
        writeCipher.dispose();

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
    private void recvAlert() throws IOException {
        byte level = (byte)inputRecord.read();
        byte description = (byte)inputRecord.read();
        if (description == -1) { // check for short message
            fatal(Alerts.alert_illegal_parameter, "Short alert message");
        }

        if (debug != null && (Debug.isOn("record") ||
                Debug.isOn("handshake"))) {
            synchronized (System.out) {
                System.out.print(threadName());
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
        if (connectionState >= cs_CLOSED) {
            return;
        }

        EngineOutputRecord r = new EngineOutputRecord(Record.ct_alert, this);
        r.setVersion(protocolVersion);

        boolean useDebug = debug != null && Debug.isOn("ssl");
        if (useDebug) {
            synchronized (System.out) {
                System.out.print(threadName());
                System.out.print(", SEND " + protocolVersion + " ALERT:  ");
                if (level == Alerts.alert_fatal) {
                    System.out.print("fatal, ");
                } else if (level == Alerts.alert_warning) {
                    System.out.print("warning, ");
                } else {
                    System.out.print("<level = " + (0x0ff & level) + ">, ");
                }
                System.out.println("description = "
                        + Alerts.alertDescription(description));
            }
        }

        r.write(level);
        r.write(description);
        try {
            writeRecord(r);
        } catch (IOException e) {
            if (useDebug) {
                System.out.println(threadName() +
                    ", Exception sending alert: " + e);
            }
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
    synchronized public void setEnableSessionCreation(boolean flag) {
        enableSessionCreation = flag;

        if ((handshaker != null) && !handshaker.started()) {
            handshaker.setEnableSessionCreation(enableSessionCreation);
        }
    }

    /**
     * Returns true if new connections may cause creation of new SSL
     * sessions.
     */
    synchronized public boolean getEnableSessionCreation() {
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
    synchronized public void setNeedClientAuth(boolean flag) {
        doClientAuth = (flag ?
            SSLEngineImpl.clauth_required : SSLEngineImpl.clauth_none);

        if ((handshaker != null) &&
                (handshaker instanceof ServerHandshaker) &&
                !handshaker.started()) {
            ((ServerHandshaker) handshaker).setClientAuth(doClientAuth);
        }
    }

    synchronized public boolean getNeedClientAuth() {
        return (doClientAuth == SSLEngineImpl.clauth_required);
    }

    /**
     * Sets the flag controlling whether a server mode engine
     * *REQUESTS* SSL client authentication.
     *
     * As long as handshaking has not started, we can change
     * whether client authentication is requested.  Otherwise,
     * we will need to wait for the next handshake.
     */
    synchronized public void setWantClientAuth(boolean flag) {
        doClientAuth = (flag ?
            SSLEngineImpl.clauth_requested : SSLEngineImpl.clauth_none);

        if ((handshaker != null) &&
                (handshaker instanceof ServerHandshaker) &&
                !handshaker.started()) {
            ((ServerHandshaker) handshaker).setClientAuth(doClientAuth);
        }
    }

    synchronized public boolean getWantClientAuth() {
        return (doClientAuth == SSLEngineImpl.clauth_requested);
    }


    /**
     * Sets the flag controlling whether the engine is in SSL
     * client or server mode.  Must be called before any SSL
     * traffic has started.
     */
    synchronized public void setUseClientMode(boolean flag) {
        switch (connectionState) {

        case cs_START:
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
            if (!handshaker.started()) {
                roleIsServer = !flag;
                connectionState = cs_START;
                initHandshaker();
                break;
            }

            // If handshake has started, that's an error.  Fall through...

        default:
            if (debug != null && Debug.isOn("ssl")) {
                System.out.println(threadName() +
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

    synchronized public boolean getUseClientMode() {
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
    public String[] getSupportedCipherSuites() {
        CipherSuiteList.clearAvailableCache();
        return CipherSuiteList.getSupported().toStringArray();
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
    synchronized public void setEnabledCipherSuites(String[] suites) {
        enabledCipherSuites = new CipherSuiteList(suites);
        if ((handshaker != null) && !handshaker.started()) {
            handshaker.enabledCipherSuites = enabledCipherSuites;
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
    synchronized public String[] getEnabledCipherSuites() {
        return enabledCipherSuites.toStringArray();
    }


    /**
     * Returns the protocols that are supported by this implementation.
     * A subset of the supported protocols may be enabled for this connection
     * @ returns an array of protocol names.
     */
    public String[] getSupportedProtocols() {
        return ProtocolList.getSupported().toStringArray();
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
    synchronized public void setEnabledProtocols(String[] protocols) {
        enabledProtocols = new ProtocolList(protocols);
        if ((handshaker != null) && !handshaker.started()) {
            handshaker.setEnabledProtocols(enabledProtocols);
        }
    }

    synchronized public String[] getEnabledProtocols() {
        return enabledProtocols.toStringArray();
    }

    /**
     * Try to configure the endpoint identification algorithm of the engine.
     *
     * @param identificationAlgorithm the algorithm used to check the
     *          endpoint identity.
     * @return true if the identification algorithm configuration success.
     */
    synchronized public boolean trySetHostnameVerification(
        String identificationAlgorithm) {
        if (sslContext.getX509TrustManager() instanceof
                X509ExtendedTrustManager) {
            this.identificationAlg = identificationAlgorithm;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the endpoint identification algorithm of the engine.
     */
    synchronized public String getHostnameVerification() {
        return identificationAlg;
    }

    /**
     * Return the name of the current thread. Utility method.
     */
    private static String threadName() {
        return Thread.currentThread().getName();
    }

    /**
     * Returns a printable representation of this end of the connection.
     */
    public String toString() {
        StringBuilder retval = new StringBuilder(80);

        retval.append(Integer.toHexString(hashCode()));
        retval.append("[");
        retval.append("SSLEngine[hostname=");
        String host = getPeerHost();
        retval.append((host == null) ? "null" : host);
        retval.append(" port=");
        retval.append(Integer.toString(getPeerPort()));
        retval.append("] ");
        retval.append(getSession().getCipherSuite());
        retval.append("]");

        return retval.toString();
    }
}
