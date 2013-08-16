/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.net.*;
import java.security.GeneralSecurityException;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.AlgorithmConstraints;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.BadPaddingException;
import javax.net.ssl.*;

/**
 * Implementation of an SSL socket.  This is a normal connection type
 * socket, implementing SSL over some lower level socket, such as TCP.
 * Because it is layered over some lower level socket, it MUST override
 * all default socket methods.
 *
 * <P> This API offers a non-traditional option for establishing SSL
 * connections.  You may first establish the connection directly, then pass
 * that connection to the SSL socket constructor with a flag saying which
 * role should be taken in the handshake protocol.  (The two ends of the
 * connection must not choose the same role!)  This allows setup of SSL
 * proxying or tunneling, and also allows the kind of "role reversal"
 * that is required for most FTP data transfers.
 *
 * @see javax.net.ssl.SSLSocket
 * @see SSLServerSocket
 *
 * @author David Brownell
 */
final public class SSLSocketImpl extends BaseSSLSocketImpl {

    /*
     * ERROR HANDLING GUIDELINES
     * (which exceptions to throw and catch and which not to throw and catch)
     *
     * . if there is an IOException (SocketException) when accessing the
     *   underlying Socket, pass it through
     *
     * . do not throw IOExceptions, throw SSLExceptions (or a subclass)
     *
     * . for internal errors (things that indicate a bug in JSSE or a
     *   grossly misconfigured J2RE), throw either an SSLException or
     *   a RuntimeException at your convenience.
     *
     * . handshaking code (Handshaker or HandshakeMessage) should generally
     *   pass through exceptions, but can handle them if they know what to
     *   do.
     *
     * . exception chaining should be used for all new code. If you happen
     *   to touch old code that does not use chaining, you should change it.
     *
     * . there is a top level exception handler that sits at all entry
     *   points from application code to SSLSocket read/write code. It
     *   makes sure that all errors are handled (see handleException()).
     *
     * . JSSE internal code should generally not call close(), call
     *   closeInternal().
     */

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
     * - SENT_CLOSE sent a close_notify to the peer. For layered,
     *          non-autoclose socket, must now read close_notify
     *          from peer before closing the connection. For nonlayered or
     *          non-autoclose socket, close connection and go onto
     *          cs_CLOSED state.
     * - CLOSED after sending close_notify alert, & socket is closed.
     *          SSL connection objects are not reused.
     * - APP_CLOSED once the application calls close(). Then it behaves like
     *          a closed socket, e.g.. getInputStream() throws an Exception.
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
     *     <-----<    ^            ^  <-----<               v
     *START>----->HANDSHAKE>----->DATA>----->RENEGOTIATE  SENT_CLOSE
     *                v            v               v        |   |
     *                |            |               |        |   v
     *                +------------+---------------+        v ERROR
     *                |                                     |   |
     *                v                                     |   |
     *               ERROR>------>----->CLOSED<--------<----+-- +
     *                                     |
     *                                     v
     *                                 APP_CLOSED
     *
     * ALSO, note that the the purpose of handshaking (renegotiation is
     * included) is to assign a different, and perhaps new, session to
     * the connection.  The SSLv3 spec is a bit confusing on that new
     * protocol feature.
     */
    private static final int    cs_START = 0;
    private static final int    cs_HANDSHAKE = 1;
    private static final int    cs_DATA = 2;
    private static final int    cs_RENEGOTIATE = 3;
    private static final int    cs_ERROR = 4;
    private static final int   cs_SENT_CLOSE = 5;
    private static final int    cs_CLOSED = 6;
    private static final int    cs_APP_CLOSED = 7;


    /*
     * Client authentication be off, requested, or required.
     *
     * Migrated to SSLEngineImpl:
     *    clauth_none/cl_auth_requested/clauth_required
     */

    /*
     * Drives the protocol state machine.
     */
    private volatile int        connectionState;

    /*
     * Flag indicating if the next record we receive MUST be a Finished
     * message. Temporarily set during the handshake to ensure that
     * a change cipher spec message is followed by a finished message.
     */
    private boolean             expectingFinished;

    /*
     * For improved diagnostics, we detail connection closure
     * If the socket is closed (connectionState >= cs_ERROR),
     * closeReason != null indicates if the socket was closed
     * because of an error or because or normal shutdown.
     */
    private SSLException        closeReason;

    /*
     * Per-connection private state that doesn't change when the
     * session is changed.
     */
    private byte                doClientAuth;
    private boolean             roleIsServer;
    private boolean             enableSessionCreation = true;
    private String              host;
    private boolean             autoClose = true;
    private AccessControlContext acc;

    // The cipher suites enabled for use on this connection.
    private CipherSuiteList     enabledCipherSuites;

    // The endpoint identification protocol
    private String              identificationProtocol = null;

    // The cryptographic algorithm constraints
    private AlgorithmConstraints    algorithmConstraints = null;

    // The server name indication and matchers
    List<SNIServerName>         serverNames =
                                    Collections.<SNIServerName>emptyList();
    Collection<SNIMatcher>      sniMatchers =
                                    Collections.<SNIMatcher>emptyList();

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
     * The handshakeLock is used to ensure that only one thread performs
     * the *complete initial* handshake.  If someone is handshaking, any
     * stray application or startHandshake() requests who find the
     * connection state is cs_HANDSHAKE will stall on handshakeLock
     * until handshaking is done.  Once the handshake is done, we either
     * succeeded or failed, but we can never go back to the cs_HANDSHAKE
     * or cs_START state again.
     *
     * Note that the read/write() calls here in SSLSocketImpl are not
     * obviously synchronized.  In fact, it's very nonintuitive, and
     * requires careful examination of code paths.  Grab some coffee,
     * and be careful with any code changes.
     *
     * There can be only three threads active at a time in the I/O
     * subsection of this class.
     *    1.  startHandshake
     *    2.  AppInputStream
     *    3.  AppOutputStream
     * One thread could call startHandshake().
     * AppInputStream/AppOutputStream read() and write() calls are each
     * synchronized on 'this' in their respective classes, so only one
     * app. thread will be doing a SSLSocketImpl.read() or .write()'s at
     * a time.
     *
     * If handshaking is required (state cs_HANDSHAKE), and
     * getConnectionState() for some/all threads returns cs_HANDSHAKE,
     * only one can grab the handshakeLock, and the rest will stall
     * either on getConnectionState(), or on the handshakeLock if they
     * happen to successfully race through the getConnectionState().
     *
     * If a writer is doing the initial handshaking, it must create a
     * temporary reader to read the responses from the other side.  As a
     * side-effect, the writer's reader will have priority over any
     * other reader.  However, the writer's reader is not allowed to
     * consume any application data.  When handshakeLock is finally
     * released, we either have a cs_DATA connection, or a
     * cs_CLOSED/cs_ERROR socket.
     *
     * The writeLock is held while writing on a socket connection and
     * also to protect the MAC and cipher for their direction.  The
     * writeLock is package private for Handshaker which holds it while
     * writing the ChangeCipherSpec message.
     *
     * To avoid the problem of a thread trying to change operational
     * modes on a socket while handshaking is going on, we synchronize
     * on 'this'.  If handshaking has not started yet, we tell the
     * handshaker to change its mode.  If handshaking has started,
     * we simply store that request until the next pending session
     * is created, at which time the new handshaker's state is set.
     *
     * The readLock is held during readRecord(), which is responsible
     * for reading an InputRecord, decrypting it, and processing it.
     * The readLock ensures that these three steps are done atomically
     * and that once started, no other thread can block on InputRecord.read.
     * This is necessary so that processing of close_notify alerts
     * from the peer are handled properly.
     */
    final private Object        handshakeLock = new Object();
    final ReentrantLock         writeLock = new ReentrantLock();
    final private Object        readLock = new Object();

    private InputRecord         inrec;

    /*
     * Crypto state that's reinitialized when the session changes.
     */
    private Authenticator       readAuthenticator, writeAuthenticator;
    private CipherBox           readCipher, writeCipher;
    // NOTE: compression state would be saved here

    /*
     * security parameters for secure renegotiation.
     */
    private boolean             secureRenegotiation;
    private byte[]              clientVerifyData;
    private byte[]              serverVerifyData;

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
     * If anyone wants to get notified about handshake completions,
     * they'll show up on this list.
     */
    private HashMap<HandshakeCompletedListener, AccessControlContext>
                                                        handshakeListeners;

    /*
     * Reuse the same internal input/output streams.
     */
    private InputStream         sockInput;
    private OutputStream        sockOutput;


    /*
     * These input and output streams block their data in SSL records,
     * and usually arrange integrity and privacy protection for those
     * records.  The guts of the SSL protocol are wrapped up in these
     * streams, and in the handshaking that establishes the details of
     * that integrity and privacy protection.
     */
    private AppInputStream      input;
    private AppOutputStream     output;

    /*
     * The protocol versions enabled for use on this connection.
     *
     * Note: we support a pseudo protocol called SSLv2Hello which when
     * set will result in an SSL v2 Hello being sent with SSL (version 3.0)
     * or TLS (version 3.1, 3.2, etc.) version info.
     */
    private ProtocolList enabledProtocols;

    /*
     * The SSL version associated with this connection.
     */
    private ProtocolVersion     protocolVersion = ProtocolVersion.DEFAULT;

    /* Class and subclass dynamic debugging support */
    private static final Debug debug = Debug.getInstance("ssl");

    /*
     * Is it the first application record to write?
     */
    private boolean isFirstAppOutputRecord = true;

    /*
     * If AppOutputStream needs to delay writes of small packets, we
     * will use this to store the data until we actually do the write.
     */
    private ByteArrayOutputStream heldRecordBuffer = null;

    //
    // CONSTRUCTORS AND INITIALIZATION CODE
    //

    /**
     * Constructs an SSL connection to a named host at a specified port,
     * using the authentication context provided.  This endpoint acts as
     * the client, and may rejoin an existing SSL session if appropriate.
     *
     * @param context authentication context to use
     * @param host name of the host with which to connect
     * @param port number of the server's port
     */
    SSLSocketImpl(SSLContextImpl context, String host, int port)
            throws IOException, UnknownHostException {
        super();
        this.host = host;
        this.serverNames =
            Utilities.addToSNIServerNameList(this.serverNames, this.host);
        init(context, false);
        SocketAddress socketAddress =
               host != null ? new InetSocketAddress(host, port) :
               new InetSocketAddress(InetAddress.getByName(null), port);
        connect(socketAddress, 0);
    }


    /**
     * Constructs an SSL connection to a server at a specified address.
     * and TCP port, using the authentication context provided.  This
     * endpoint acts as the client, and may rejoin an existing SSL session
     * if appropriate.
     *
     * @param context authentication context to use
     * @param address the server's host
     * @param port its port
     */
    SSLSocketImpl(SSLContextImpl context, InetAddress host, int port)
            throws IOException {
        super();
        init(context, false);
        SocketAddress socketAddress = new InetSocketAddress(host, port);
        connect(socketAddress, 0);
    }

    /**
     * Constructs an SSL connection to a named host at a specified port,
     * using the authentication context provided.  This endpoint acts as
     * the client, and may rejoin an existing SSL session if appropriate.
     *
     * @param context authentication context to use
     * @param host name of the host with which to connect
     * @param port number of the server's port
     * @param localAddr the local address the socket is bound to
     * @param localPort the local port the socket is bound to
     */
    SSLSocketImpl(SSLContextImpl context, String host, int port,
            InetAddress localAddr, int localPort)
            throws IOException, UnknownHostException {
        super();
        this.host = host;
        this.serverNames =
            Utilities.addToSNIServerNameList(this.serverNames, this.host);
        init(context, false);
        bind(new InetSocketAddress(localAddr, localPort));
        SocketAddress socketAddress =
               host != null ? new InetSocketAddress(host, port) :
               new InetSocketAddress(InetAddress.getByName(null), port);
        connect(socketAddress, 0);
    }


    /**
     * Constructs an SSL connection to a server at a specified address.
     * and TCP port, using the authentication context provided.  This
     * endpoint acts as the client, and may rejoin an existing SSL session
     * if appropriate.
     *
     * @param context authentication context to use
     * @param address the server's host
     * @param port its port
     * @param localAddr the local address the socket is bound to
     * @param localPort the local port the socket is bound to
     */
    SSLSocketImpl(SSLContextImpl context, InetAddress host, int port,
            InetAddress localAddr, int localPort)
            throws IOException {
        super();
        init(context, false);
        bind(new InetSocketAddress(localAddr, localPort));
        SocketAddress socketAddress = new InetSocketAddress(host, port);
        connect(socketAddress, 0);
    }

    /*
     * Package-private constructor used ONLY by SSLServerSocket.  The
     * java.net package accepts the TCP connection after this call is
     * made.  This just initializes handshake state to use "server mode",
     * giving control over the use of SSL client authentication.
     */
    SSLSocketImpl(SSLContextImpl context, boolean serverMode,
            CipherSuiteList suites, byte clientAuth,
            boolean sessionCreation, ProtocolList protocols,
            String identificationProtocol,
            AlgorithmConstraints algorithmConstraints,
            Collection<SNIMatcher> sniMatchers) throws IOException {

        super();
        doClientAuth = clientAuth;
        enableSessionCreation = sessionCreation;
        this.identificationProtocol = identificationProtocol;
        this.algorithmConstraints = algorithmConstraints;
        this.sniMatchers = sniMatchers;
        init(context, serverMode);

        /*
         * Override what was picked out for us.
         */
        enabledCipherSuites = suites;
        enabledProtocols = protocols;
    }


    /**
     * Package-private constructor used to instantiate an unconnected
     * socket. The java.net package will connect it, either when the
     * connect() call is made by the application.  This instance is
     * meant to set handshake state to use "client mode".
     */
    SSLSocketImpl(SSLContextImpl context) {
        super();
        init(context, false);
    }


    /**
     * Layer SSL traffic over an existing connection, rather than creating
     * a new connection.  The existing connection may be used only for SSL
     * traffic (using this SSLSocket) until the SSLSocket.close() call
     * returns. However, if a protocol error is detected, that existing
     * connection is automatically closed.
     *
     * <P> This particular constructor always uses the socket in the
     * role of an SSL client. It may be useful in cases which start
     * using SSL after some initial data transfers, for example in some
     * SSL tunneling applications or as part of some kinds of application
     * protocols which negotiate use of a SSL based security.
     *
     * @param sock the existing connection
     * @param context the authentication context to use
     */
    SSLSocketImpl(SSLContextImpl context, Socket sock, String host,
            int port, boolean autoClose) throws IOException {
        super(sock);
        // We always layer over a connected socket
        if (!sock.isConnected()) {
            throw new SocketException("Underlying socket is not connected");
        }
        this.host = host;
        this.serverNames =
            Utilities.addToSNIServerNameList(this.serverNames, this.host);
        init(context, false);
        this.autoClose = autoClose;
        doneConnect();
    }

    /**
     * Creates a server mode {@link Socket} layered over an
     * existing connected socket, and is able to read data which has
     * already been consumed/removed from the {@link Socket}'s
     * underlying {@link InputStream}.
     */
    SSLSocketImpl(SSLContextImpl context, Socket sock,
            InputStream consumed, boolean autoClose) throws IOException {
        super(sock, consumed);
        // We always layer over a connected socket
        if (!sock.isConnected()) {
            throw new SocketException("Underlying socket is not connected");
        }

        // In server mode, it is not necessary to set host and serverNames.
        // Otherwise, would require a reverse DNS lookup to get the hostname.

        init(context, true);
        this.autoClose = autoClose;
        doneConnect();
    }

    /**
     * Initializes the client socket.
     */
    private void init(SSLContextImpl context, boolean isServer) {
        sslContext = context;
        sess = SSLSessionImpl.nullSession;
        handshakeSession = null;

        /*
         * role is as specified, state is START until after
         * the low level connection's established.
         */
        roleIsServer = isServer;
        connectionState = cs_START;

        /*
         * default read and write side cipher and MAC support
         *
         * Note:  compression support would go here too
         */
        readCipher = CipherBox.NULL;
        readAuthenticator = MAC.NULL;
        writeCipher = CipherBox.NULL;
        writeAuthenticator = MAC.NULL;

        // initial security parameters for secure renegotiation
        secureRenegotiation = false;
        clientVerifyData = new byte[0];
        serverVerifyData = new byte[0];

        enabledCipherSuites =
                sslContext.getDefaultCipherSuiteList(roleIsServer);
        enabledProtocols =
                sslContext.getDefaultProtocolList(roleIsServer);

        inrec = null;

        // save the acc
        acc = AccessController.getContext();

        input = new AppInputStream(this);
        output = new AppOutputStream(this);
    }

    /**
     * Connects this socket to the server with a specified timeout
     * value.
     *
     * This method is either called on an unconnected SSLSocketImpl by the
     * application, or it is called in the constructor of a regular
     * SSLSocketImpl. If we are layering on top on another socket, then
     * this method should not be called, because we assume that the
     * underlying socket is already connected by the time it is passed to
     * us.
     *
     * @param   endpoint the <code>SocketAddress</code>
     * @param   timeout  the timeout value to be used, 0 is no timeout
     * @throws  IOException if an error occurs during the connection
     * @throws  SocketTimeoutException if timeout expires before connecting
     */
    @Override
    public void connect(SocketAddress endpoint, int timeout)
            throws IOException {

        if (isLayered()) {
            throw new SocketException("Already connected");
        }

        if (!(endpoint instanceof InetSocketAddress)) {
            throw new SocketException(
                                  "Cannot handle non-Inet socket addresses.");
        }

        super.connect(endpoint, timeout);
        doneConnect();
    }

    /**
     * Initialize the handshaker and socket streams.
     *
     * Called by connect, the layered constructor, and SSLServerSocket.
     */
    void doneConnect() throws IOException {
        /*
         * Save the input and output streams.  May be done only after
         * java.net actually connects using the socket "self", else
         * we get some pretty bizarre failure modes.
         */
        sockInput = super.getInputStream();
        sockOutput = super.getOutputStream();

        /*
         * Move to handshaking state, with pending session initialized
         * to defaults and the appropriate kind of handshaker set up.
         */
        initHandshaker();
    }

    synchronized private int getConnectionState() {
        return connectionState;
    }

    synchronized private void setConnectionState(int state) {
        connectionState = state;
    }

    AccessControlContext getAcc() {
        return acc;
    }

    //
    // READING AND WRITING RECORDS
    //

    /*
     * AppOutputStream calls may need to buffer multiple outbound
     * application packets.
     *
     * All other writeRecord() calls will not buffer, so do not hold
     * these records.
     */
    void writeRecord(OutputRecord r) throws IOException {
        writeRecord(r, false);
    }

    /*
     * Record Output. Application data can't be sent until the first
     * handshake establishes a session.
     *
     * NOTE:  we let empty records be written as a hook to force some
     * TCP-level activity, notably handshaking, to occur.
     */
    void writeRecord(OutputRecord r, boolean holdRecord) throws IOException {
        /*
         * The loop is in case of HANDSHAKE --> ERROR transitions, etc
         */
    loop:
        while (r.contentType() == Record.ct_application_data) {
            /*
             * Not all states support passing application data.  We
             * synchronize access to the connection state, so that
             * synchronous handshakes can complete cleanly.
             */
            switch (getConnectionState()) {

            /*
             * We've deferred the initial handshaking till just now,
             * when presumably a thread's decided it's OK to block for
             * longish periods of time for I/O purposes (as well as
             * configured the cipher suites it wants to use).
             */
            case cs_HANDSHAKE:
                performInitialHandshake();
                break;

            case cs_DATA:
            case cs_RENEGOTIATE:
                break loop;

            case cs_ERROR:
                fatal(Alerts.alert_close_notify,
                    "error while writing to socket");
                break; // dummy

            case cs_SENT_CLOSE:
            case cs_CLOSED:
            case cs_APP_CLOSED:
                // we should never get here (check in AppOutputStream)
                // this is just a fallback
                if (closeReason != null) {
                    throw closeReason;
                } else {
                    throw new SocketException("Socket closed");
                }

            /*
             * Else something's goofy in this state machine's use.
             */
            default:
                throw new SSLProtocolException("State error, send app data");
            }
        }

        //
        // Don't bother to really write empty records.  We went this
        // far to drive the handshake machinery, for correctness; not
        // writing empty records improves performance by cutting CPU
        // time and network resource usage.  However, some protocol
        // implementations are fragile and don't like to see empty
        // records, so this also increases robustness.
        //
        if (!r.isEmpty()) {

            // If the record is a close notify alert, we need to honor
            // socket option SO_LINGER. Note that we will try to send
            // the close notify even if the SO_LINGER set to zero.
            if (r.isAlert(Alerts.alert_close_notify) && getSoLinger() >= 0) {

                // keep and clear the current thread interruption status.
                boolean interrupted = Thread.interrupted();
                try {
                    if (writeLock.tryLock(getSoLinger(), TimeUnit.SECONDS)) {
                        try {
                            writeRecordInternal(r, holdRecord);
                        } finally {
                            writeLock.unlock();
                        }
                    } else {
                        SSLException ssle = new SSLException(
                                "SO_LINGER timeout," +
                                " close_notify message cannot be sent.");


                        // For layered, non-autoclose sockets, we are not
                        // able to bring them into a usable state, so we
                        // treat it as fatal error.
                        if (isLayered() && !autoClose) {
                            // Note that the alert description is
                            // specified as -1, so no message will be send
                            // to peer anymore.
                            fatal((byte)(-1), ssle);
                        } else if ((debug != null) && Debug.isOn("ssl")) {
                            System.out.println(
                                Thread.currentThread().getName() +
                                ", received Exception: " + ssle);
                        }

                        // RFC2246 requires that the session becomes
                        // unresumable if any connection is terminated
                        // without proper close_notify messages with
                        // level equal to warning.
                        //
                        // RFC4346 no longer requires that a session not be
                        // resumed if failure to properly close a connection.
                        //
                        // We choose to make the session unresumable if
                        // failed to send the close_notify message.
                        //
                        sess.invalidate();
                    }
                } catch (InterruptedException ie) {
                    // keep interrupted status
                    interrupted = true;
                }

                // restore the interrupted status
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            } else {
                writeLock.lock();
                try {
                    writeRecordInternal(r, holdRecord);
                } finally {
                    writeLock.unlock();
                }
            }
        }
    }

    private void writeRecordInternal(OutputRecord r,
            boolean holdRecord) throws IOException {

        // r.compress(c);
        r.encrypt(writeAuthenticator, writeCipher);

        if (holdRecord) {
            // If we were requested to delay the record due to possibility
            // of Nagle's being active when finally got to writing, and
            // it's actually not, we don't really need to delay it.
            if (getTcpNoDelay()) {
                holdRecord = false;
            } else {
                // We need to hold the record, so let's provide
                // a per-socket place to do it.
                if (heldRecordBuffer == null) {
                    // Likely only need 37 bytes.
                    heldRecordBuffer = new ByteArrayOutputStream(40);
                }
            }
        }
        r.write(sockOutput, holdRecord, heldRecordBuffer);

        /*
         * Check the sequence number state
         *
         * Note that in order to maintain the connection I/O
         * properly, we check the sequence number after the last
         * record writing process. As we request renegotiation
         * or close the connection for wrapped sequence number
         * when there is enough sequence number space left to
         * handle a few more records, so the sequence number
         * of the last record cannot be wrapped.
         */
        if (connectionState < cs_ERROR) {
            checkSequenceNumber(writeAuthenticator, r.contentType());
        }

        // turn off the flag of the first application record
        if (isFirstAppOutputRecord &&
                r.contentType() == Record.ct_application_data) {
            isFirstAppOutputRecord = false;
        }
    }

    /*
     * Need to split the payload except the following cases:
     *
     * 1. protocol version is TLS 1.1 or later;
     * 2. bulk cipher does not use CBC mode, including null bulk cipher suites.
     * 3. the payload is the first application record of a freshly
     *    negotiated TLS session.
     * 4. the CBC protection is disabled;
     *
     * More details, please refer to AppOutputStream.write(byte[], int, int).
     */
    boolean needToSplitPayload() {
        writeLock.lock();
        try {
            return (protocolVersion.v <= ProtocolVersion.TLS10.v) &&
                    writeCipher.isCBCMode() && !isFirstAppOutputRecord &&
                    Record.enableCBCProtection;
        } finally {
            writeLock.unlock();
        }
    }

    /*
     * Read an application data record.  Alerts and handshake
     * messages are handled directly.
     */
    void readDataRecord(InputRecord r) throws IOException {
        if (getConnectionState() == cs_HANDSHAKE) {
            performInitialHandshake();
        }
        readRecord(r, true);
    }


    /*
     * Clear the pipeline of records from the peer, optionally returning
     * application data.   Caller is responsible for knowing that it's
     * possible to do this kind of clearing, if they don't want app
     * data -- e.g. since it's the initial SSL handshake.
     *
     * Don't synchronize (this) during a blocking read() since it
     * protects data which is accessed on the write side as well.
     */
    private void readRecord(InputRecord r, boolean needAppData)
            throws IOException {
        int state;

        // readLock protects reading and processing of an InputRecord.
        // It keeps the reading from sockInput and processing of the record
        // atomic so that no two threads can be blocked on the
        // read from the same input stream at the same time.
        // This is required for example when a reader thread is
        // blocked on the read and another thread is trying to
        // close the socket. For a non-autoclose, layered socket,
        // the thread performing the close needs to read the close_notify.
        //
        // Use readLock instead of 'this' for locking because
        // 'this' also protects data accessed during writing.
      synchronized (readLock) {
        /*
         * Read and handle records ... return application data
         * ONLY if it's needed.
         */

        while (((state = getConnectionState()) != cs_CLOSED) &&
                (state != cs_ERROR) && (state != cs_APP_CLOSED)) {
            /*
             * Read a record ... maybe emitting an alert if we get a
             * comprehensible but unsupported "hello" message during
             * format checking (e.g. V2).
             */
            try {
                r.setAppDataValid(false);
                r.read(sockInput, sockOutput);
            } catch (SSLProtocolException e) {
                try {
                    fatal(Alerts.alert_unexpected_message, e);
                } catch (IOException x) {
                    // discard this exception
                }
                throw e;
            } catch (EOFException eof) {
                boolean handshaking = (getConnectionState() <= cs_HANDSHAKE);
                boolean rethrow = requireCloseNotify || handshaking;
                if ((debug != null) && Debug.isOn("ssl")) {
                    System.out.println(Thread.currentThread().getName() +
                        ", received EOFException: "
                        + (rethrow ? "error" : "ignored"));
                }
                if (rethrow) {
                    SSLException e;
                    if (handshaking) {
                        e = new SSLHandshakeException
                            ("Remote host closed connection during handshake");
                    } else {
                        e = new SSLProtocolException
                            ("Remote host closed connection incorrectly");
                    }
                    e.initCause(eof);
                    throw e;
                } else {
                    // treat as if we had received a close_notify
                    closeInternal(false);
                    continue;
                }
            }


            /*
             * The basic SSLv3 record protection involves (optional)
             * encryption for privacy, and an integrity check ensuring
             * data origin authentication.  We do them both here, and
             * throw a fatal alert if the integrity check fails.
             */
            try {
                r.decrypt(readAuthenticator, readCipher);
            } catch (BadPaddingException e) {
                byte alertType = (r.contentType() == Record.ct_handshake)
                                        ? Alerts.alert_handshake_failure
                                        : Alerts.alert_bad_record_mac;
                fatal(alertType, e.getMessage(), e);
            }

            // if (!r.decompress(c))
            //     fatal(Alerts.alert_decompression_failure,
            //         "decompression failure");

            /*
             * Process the record.
             */
            synchronized (this) {
              switch (r.contentType()) {
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
                    handshaker.process_record(r, expectingFinished);
                    expectingFinished = false;

                    if (handshaker.invalidated) {
                        handshaker = null;
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
                        handshaker = null;
                        connectionState = cs_DATA;

                        //
                        // Tell folk about handshake completion, but do
                        // it in a separate thread.
                        //
                        if (handshakeListeners != null) {
                            HandshakeCompletedEvent event =
                                new HandshakeCompletedEvent(this, sess);

                            Thread t = new NotifyHandshakeThread(
                                handshakeListeners.entrySet(), event);
                            t.start();
                        }
                    }

                    if (needAppData || connectionState != cs_DATA) {
                        continue;
                    }
                    break;

                case Record.ct_application_data:
                    // Pass this right back up to the application.
                    if (connectionState != cs_DATA
                            && connectionState != cs_RENEGOTIATE
                            && connectionState != cs_SENT_CLOSE) {
                        throw new SSLProtocolException(
                            "Data received in non-data state: " +
                            connectionState);
                    }
                    if (expectingFinished) {
                        throw new SSLProtocolException
                                ("Expecting finished message, received data");
                    }
                    if (!needAppData) {
                        throw new SSLException("Discarding app data");
                    }

                    r.setAppDataValid(true);
                    break;

                case Record.ct_alert:
                    recvAlert(r);
                    continue;

                case Record.ct_change_cipher_spec:
                    if ((connectionState != cs_HANDSHAKE
                                && connectionState != cs_RENEGOTIATE)
                            || r.available() != 1
                            || r.read() != 1) {
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
                    continue;

                default:
                    //
                    // TLS requires that unrecognized records be ignored.
                    //
                    if (debug != null && Debug.isOn("ssl")) {
                        System.out.println(Thread.currentThread().getName() +
                            ", Received record type: "
                            + r.contentType());
                    }
                    continue;
              } // switch

              /*
               * Check the sequence number state
               *
               * Note that in order to maintain the connection I/O
               * properly, we check the sequence number after the last
               * record reading process. As we request renegotiation
               * or close the connection for wrapped sequence number
               * when there is enough sequence number space left to
               * handle a few more records, so the sequence number
               * of the last record cannot be wrapped.
               */
              if (connectionState < cs_ERROR) {
                  checkSequenceNumber(readAuthenticator, r.contentType());
              }

              return;
            } // synchronized (this)
        }

        //
        // couldn't read, due to some kind of error
        //
        r.close();
        return;
      }  // synchronized (readLock)
    }

    /**
     * Check the sequence number state
     *
     * RFC 4346 states that, "Sequence numbers are of type uint64 and
     * may not exceed 2^64-1.  Sequence numbers do not wrap. If a TLS
     * implementation would need to wrap a sequence number, it must
     * renegotiate instead."
     */
    private void checkSequenceNumber(Authenticator authenticator, byte type)
            throws IOException {

        /*
         * Don't bother to check the sequence number for error or
         * closed connections, or NULL MAC.
         */
        if (connectionState >= cs_ERROR || authenticator == MAC.NULL) {
            return;
        }

        /*
         * Conservatively, close the connection immediately when the
         * sequence number is close to overflow
         */
        if (authenticator.seqNumOverflow()) {
            /*
             * TLS protocols do not define a error alert for sequence
             * number overflow. We use handshake_failure error alert
             * for handshaking and bad_record_mac for other records.
             */
            if (debug != null && Debug.isOn("ssl")) {
                System.out.println(Thread.currentThread().getName() +
                    ", sequence number extremely close to overflow " +
                    "(2^64-1 packets). Closing connection.");

            }

            fatal(Alerts.alert_handshake_failure, "sequence number overflow");
        }

        /*
         * Ask for renegotiation when need to renew sequence number.
         *
         * Don't bother to kickstart the renegotiation when the local is
         * asking for it.
         */
        if ((type != Record.ct_handshake) && authenticator.seqNumIsHuge()) {
            if (debug != null && Debug.isOn("ssl")) {
                System.out.println(Thread.currentThread().getName() +
                        ", request renegotiation " +
                        "to avoid sequence number overflow");
            }

            startHandshake();
        }
    }

    //
    // HANDSHAKE RELATED CODE
    //

    /**
     * Return the AppInputStream. For use by Handshaker only.
     */
    AppInputStream getAppInputStream() {
        return input;
    }

    /**
     * Return the AppOutputStream. For use by Handshaker only.
     */
    AppOutputStream getAppOutputStream() {
        return output;
    }

    /**
     * Initialize the handshaker object. This means:
     *
     *  . if a handshake is already in progress (state is cs_HANDSHAKE
     *    or cs_RENEGOTIATE), do nothing and return
     *
     *  . if the socket is already closed, throw an Exception (internal error)
     *
     *  . otherwise (cs_START or cs_DATA), create the appropriate handshaker
     *    object, and advance the connection state (to cs_HANDSHAKE or
     *    cs_RENEGOTIATE, respectively).
     *
     * This method is called right after a new socket is created, when
     * starting renegotiation, or when changing client/ server mode of the
     * socket.
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
                    secureRenegotiation, clientVerifyData, serverVerifyData);
            handshaker.setSNIMatchers(sniMatchers);
        } else {
            handshaker = new ClientHandshaker(this, sslContext,
                    enabledProtocols,
                    protocolVersion, connectionState == cs_HANDSHAKE,
                    secureRenegotiation, clientVerifyData, serverVerifyData);
            handshaker.setSNIServerNames(serverNames);
        }
        handshaker.setEnabledCipherSuites(enabledCipherSuites);
        handshaker.setEnableSessionCreation(enableSessionCreation);
    }

    /**
     * Synchronously perform the initial handshake.
     *
     * If the handshake is already in progress, this method blocks until it
     * is completed. If the initial handshake has already been completed,
     * it returns immediately.
     */
    private void performInitialHandshake() throws IOException {
        // use handshakeLock and the state check to make sure only
        // one thread performs the handshake
        synchronized (handshakeLock) {
            if (getConnectionState() == cs_HANDSHAKE) {
                kickstartHandshake();

                /*
                 * All initial handshaking goes through this
                 * InputRecord until we have a valid SSL connection.
                 * Once initial handshaking is finished, AppInputStream's
                 * InputRecord can handle any future renegotiation.
                 *
                 * Keep this local so that it goes out of scope and is
                 * eventually GC'd.
                 */
                if (inrec == null) {
                    inrec = new InputRecord();

                    /*
                     * Grab the characteristics already assigned to
                     * AppInputStream's InputRecord.  Enable checking for
                     * SSLv2 hellos on this first handshake.
                     */
                    inrec.setHandshakeHash(input.r.getHandshakeHash());
                    inrec.setHelloVersion(input.r.getHelloVersion());
                    inrec.enableFormatChecks();
                }

                readRecord(inrec, false);
                inrec = null;
            }
        }
    }

    /**
     * Starts an SSL handshake on this connection.
     */
    @Override
    public void startHandshake() throws IOException {
        // start an ssl handshake that could be resumed from timeout exception
        startHandshake(true);
    }

    /**
     * Starts an ssl handshake on this connection.
     *
     * @param resumable indicates the handshake process is resumable from a
     *          certain exception. If <code>resumable</code>, the socket will
     *          be reserved for exceptions like timeout; otherwise, the socket
     *          will be closed, no further communications could be done.
     */
    private void startHandshake(boolean resumable) throws IOException {
        checkWrite();
        try {
            if (getConnectionState() == cs_HANDSHAKE) {
                // do initial handshake
                performInitialHandshake();
            } else {
                // start renegotiation
                kickstartHandshake();
            }
        } catch (Exception e) {
            // shutdown and rethrow (wrapped) exception as appropriate
            handleException(e, resumable);
        }
    }

    /**
     * Kickstart the handshake if it is not already in progress.
     * This means:
     *
     *  . if handshaking is already underway, do nothing and return
     *
     *  . if the socket is not connected or already closed, throw an
     *    Exception.
     *
     *  . otherwise, call initHandshake() to initialize the handshaker
     *    object and progress the state. Then, send the initial
     *    handshaking message if appropriate (always on clients and
     *    on servers when renegotiating).
     */
    private synchronized void kickstartHandshake() throws IOException {

        switch (connectionState) {

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

        /*
         * The only way to get a socket in the state is when
         * you have an unconnected socket.
         */
        case cs_START:
            throw new SocketException(
                "handshaking attempted on unconnected socket");

        default:
            throw new SocketException("connection is closed");
        }

        //
        // Kickstart handshake state machine if we need to ...
        //
        // Note that handshaker.kickstart() writes the message
        // to its HandshakeOutStream, which calls back into
        // SSLSocketImpl.writeRecord() to send it.
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
            } else {
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

    //
    // CLOSURE RELATED CALLS
    //

    /**
     * Return whether the socket has been explicitly closed by the application.
     */
    @Override
    public boolean isClosed() {
        return connectionState == cs_APP_CLOSED;
    }

    /**
     * Return whether we have reached end-of-file.
     *
     * If the socket is not connected, has been shutdown because of an error
     * or has been closed, throw an Exception.
     */
    boolean checkEOF() throws IOException {
        switch (getConnectionState()) {
        case cs_START:
            throw new SocketException("Socket is not connected");

        case cs_HANDSHAKE:
        case cs_DATA:
        case cs_RENEGOTIATE:
        case cs_SENT_CLOSE:
            return false;

        case cs_APP_CLOSED:
            throw new SocketException("Socket is closed");

        case cs_ERROR:
        case cs_CLOSED:
        default:
            // either closed because of error, or normal EOF
            if (closeReason == null) {
                return true;
            }
            IOException e = new SSLException
                        ("Connection has been shutdown: " + closeReason);
            e.initCause(closeReason);
            throw e;

        }
    }

    /**
     * Check if we can write data to this socket. If not, throw an IOException.
     */
    void checkWrite() throws IOException {
        if (checkEOF() || (getConnectionState() == cs_SENT_CLOSE)) {
            // we are at EOF, write must throw Exception
            throw new SocketException("Connection closed by remote host");
        }
    }

    protected void closeSocket() throws IOException {

        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                                                ", called closeSocket()");
        }

        super.close();
    }

    private void closeSocket(boolean selfInitiated) throws IOException {
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                ", called closeSocket(" + selfInitiated + ")");
        }
        if (!isLayered() || autoClose) {
            super.close();
        } else if (selfInitiated) {
            // layered && non-autoclose
            // read close_notify alert to clear input stream
            waitForClose(false);
        }
    }

    /*
     * Closing the connection is tricky ... we can't officially close the
     * connection until we know the other end is ready to go away too,
     * and if ever the connection gets aborted we must forget session
     * state (it becomes invalid).
     */

    /**
     * Closes the SSL connection.  SSL includes an application level
     * shutdown handshake; you should close SSL sockets explicitly
     * rather than leaving it for finalization, so that your remote
     * peer does not experience a protocol error.
     */
    @Override
    public void close() throws IOException {
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                                                    ", called close()");
        }
        closeInternal(true);  // caller is initiating close
        setConnectionState(cs_APP_CLOSED);
    }

    /**
     * Don't synchronize the whole method because waitForClose()
     * (which calls readRecord()) might be called.
     *
     * @param selfInitiated Indicates which party initiated the close.
     * If selfInitiated, this side is initiating a close; for layered and
     * non-autoclose socket, wait for close_notify response.
     * If !selfInitiated, peer sent close_notify; we reciprocate but
     * no need to wait for response.
     */
    private void closeInternal(boolean selfInitiated) throws IOException {
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                        ", called closeInternal(" + selfInitiated + ")");
        }

        int state = getConnectionState();
        boolean closeSocketCalled = false;
        Throwable cachedThrowable = null;
        try {
            switch (state) {
            case cs_START:
                // unconnected socket or handshaking has not been initialized
                closeSocket(selfInitiated);
                break;

            /*
             * If we're closing down due to error, we already sent (or else
             * received) the fatal alert ... no niceties, blow the connection
             * away as quickly as possible (even if we didn't allocate the
             * socket ourselves; it's unusable, regardless).
             */
            case cs_ERROR:
                closeSocket();
                break;

            /*
             * Sometimes close() gets called more than once.
             */
            case cs_CLOSED:
            case cs_APP_CLOSED:
                 break;

            /*
             * Otherwise we indicate clean termination.
             */
            // case cs_HANDSHAKE:
            // case cs_DATA:
            // case cs_RENEGOTIATE:
            // case cs_SENT_CLOSE:
            default:
                synchronized (this) {
                    if (((state = getConnectionState()) == cs_CLOSED) ||
                       (state == cs_ERROR) || (state == cs_APP_CLOSED)) {
                        return;  // connection was closed while we waited
                    }
                    if (state != cs_SENT_CLOSE) {
                        try {
                            warning(Alerts.alert_close_notify);
                            connectionState = cs_SENT_CLOSE;
                        } catch (Throwable th) {
                            // we need to ensure socket is closed out
                            // if we encounter any errors.
                            connectionState = cs_ERROR;
                            // cache this for later use
                            cachedThrowable = th;
                            closeSocketCalled = true;
                            closeSocket(selfInitiated);
                        }
                    }
                }
                // If state was cs_SENT_CLOSE before, we don't do the actual
                // closing since it is already in progress.
                if (state == cs_SENT_CLOSE) {
                    if (debug != null && Debug.isOn("ssl")) {
                        System.out.println(Thread.currentThread().getName() +
                            ", close invoked again; state = " +
                            getConnectionState());
                    }
                    if (selfInitiated == false) {
                        // We were called because a close_notify message was
                        // received. This may be due to another thread calling
                        // read() or due to our call to waitForClose() below.
                        // In either case, just return.
                        return;
                    }
                    // Another thread explicitly called close(). We need to
                    // wait for the closing to complete before returning.
                    synchronized (this) {
                        while (connectionState < cs_CLOSED) {
                            try {
                                this.wait();
                            } catch (InterruptedException e) {
                                // ignore
                            }
                        }
                    }
                    if ((debug != null) && Debug.isOn("ssl")) {
                        System.out.println(Thread.currentThread().getName() +
                            ", after primary close; state = " +
                            getConnectionState());
                    }
                    return;
                }

                if (!closeSocketCalled)  {
                    closeSocketCalled = true;
                    closeSocket(selfInitiated);
                }

                break;
            }
        } finally {
            synchronized (this) {
                // Upon exit from this method, the state is always >= cs_CLOSED
                connectionState = (connectionState == cs_APP_CLOSED)
                                ? cs_APP_CLOSED : cs_CLOSED;
                // notify any threads waiting for the closing to finish
                this.notifyAll();
            }
            if (closeSocketCalled) {
                // Dispose of ciphers since we've closed socket
                disposeCiphers();
            }
            if (cachedThrowable != null) {
               /*
                * Rethrow the error to the calling method
                * The Throwable caught can only be an Error or RuntimeException
                */
                if (cachedThrowable instanceof Error)
                    throw (Error) cachedThrowable;
                if (cachedThrowable instanceof RuntimeException)
                    throw (RuntimeException) cachedThrowable;
            }
        }
    }

    /**
     * Reads a close_notify or a fatal alert from the input stream.
     * Keep reading records until we get a close_notify or until
     * the connection is otherwise closed.  The close_notify or alert
     * might be read by another reader,
     * which will then process the close and set the connection state.
     */
    void waitForClose(boolean rethrow) throws IOException {
        if (debug != null && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                ", waiting for close_notify or alert: state "
                + getConnectionState());
        }

        try {
            int state;

            while (((state = getConnectionState()) != cs_CLOSED) &&
                   (state != cs_ERROR) && (state != cs_APP_CLOSED)) {
                // create the InputRecord if it isn't intialized.
                if (inrec == null) {
                    inrec = new InputRecord();
                }

                // Ask for app data and then throw it away
                try {
                    readRecord(inrec, true);
                } catch (SocketTimeoutException e) {
                    // if time out, ignore the exception and continue
                }
            }
            inrec = null;
        } catch (IOException e) {
            if (debug != null && Debug.isOn("ssl")) {
                System.out.println(Thread.currentThread().getName() +
                    ", Exception while waiting for close " +e);
            }
            if (rethrow) {
                throw e; // pass exception up
            }
        }
    }

    /**
     * Called by closeInternal() only. Be sure to consider the
     * synchronization locks carefully before calling it elsewhere.
     */
    private void disposeCiphers() {
        // See comment in changeReadCiphers()
        synchronized (readLock) {
            readCipher.dispose();
        }
        // See comment in changeReadCiphers()
        writeLock.lock();
        try {
            writeCipher.dispose();
        } finally {
            writeLock.unlock();
        }
    }

    //
    // EXCEPTION AND ALERT HANDLING
    //

    /**
     * Handle an exception. This method is called by top level exception
     * handlers (in read(), write()) to make sure we always shutdown the
     * connection correctly and do not pass runtime exception to the
     * application.
     */
    void handleException(Exception e) throws IOException {
        handleException(e, true);
    }

    /**
     * Handle an exception. This method is called by top level exception
     * handlers (in read(), write(), startHandshake()) to make sure we
     * always shutdown the connection correctly and do not pass runtime
     * exception to the application.
     *
     * This method never returns normally, it always throws an IOException.
     *
     * We first check if the socket has already been shutdown because of an
     * error. If so, we just rethrow the exception. If the socket has not
     * been shutdown, we sent a fatal alert and remember the exception.
     *
     * @param e the Exception
     * @param resumable indicates the caller process is resumable from the
     *          exception. If <code>resumable</code>, the socket will be
     *          reserved for exceptions like timeout; otherwise, the socket
     *          will be closed, no further communications could be done.
     */
    synchronized private void handleException(Exception e, boolean resumable)
        throws IOException {
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                        ", handling exception: " + e.toString());
        }

        // don't close the Socket in case of timeouts or interrupts if
        // the process is resumable.
        if (e instanceof InterruptedIOException && resumable) {
            throw (IOException)e;
        }

        // if we've already shutdown because of an error,
        // there is nothing to do except rethrow the exception
        if (closeReason != null) {
            if (e instanceof IOException) { // includes SSLException
                throw (IOException)e;
            } else {
                // this is odd, not an IOException.
                // normally, this should not happen
                // if closeReason has been already been set
                throw Alerts.getSSLException(Alerts.alert_internal_error, e,
                                      "Unexpected exception");
            }
        }

        // need to perform error shutdown
        boolean isSSLException = (e instanceof SSLException);
        if ((isSSLException == false) && (e instanceof IOException)) {
            // IOException from the socket
            // this means the TCP connection is already dead
            // we call fatal just to set the error status
            try {
                fatal(Alerts.alert_unexpected_message, e);
            } catch (IOException ee) {
                // ignore (IOException wrapped in SSLException)
            }
            // rethrow original IOException
            throw (IOException)e;
        }

        // must be SSLException or RuntimeException
        byte alertType;
        if (isSSLException) {
            if (e instanceof SSLHandshakeException) {
                alertType = Alerts.alert_handshake_failure;
            } else {
                alertType = Alerts.alert_unexpected_message;
            }
        } else {
            alertType = Alerts.alert_internal_error;
        }
        fatal(alertType, e);
    }

    /*
     * Send a warning alert.
     */
    void warning(byte description) {
        sendAlert(Alerts.alert_warning, description);
    }

    synchronized void fatal(byte description, String diagnostic)
            throws IOException {
        fatal(description, diagnostic, null);
    }

    synchronized void fatal(byte description, Throwable cause)
            throws IOException {
        fatal(description, null, cause);
    }

    /*
     * Send a fatal alert, and throw an exception so that callers will
     * need to stand on their heads to accidentally continue processing.
     */
    synchronized void fatal(byte description, String diagnostic,
            Throwable cause) throws IOException {
        if ((input != null) && (input.r != null)) {
            input.r.close();
        }
        sess.invalidate();
        if (handshakeSession != null) {
            handshakeSession.invalidate();
        }

        int oldState = connectionState;
        if (connectionState < cs_ERROR) {
            connectionState = cs_ERROR;
        }

        /*
         * Has there been an error received yet?  If not, remember it.
         * By RFC 2246, we don't bother waiting for a response.
         * Fatal errors require immediate shutdown.
         */
        if (closeReason == null) {
            /*
             * Try to clear the kernel buffer to avoid TCP connection resets.
             */
            if (oldState == cs_HANDSHAKE) {
                sockInput.skip(sockInput.available());
            }

            // If the description equals -1, the alert won't be sent to peer.
            if (description != -1) {
                sendAlert(Alerts.alert_fatal, description);
            }
            if (cause instanceof SSLException) { // only true if != null
                closeReason = (SSLException)cause;
            } else {
                closeReason =
                    Alerts.getSSLException(description, cause, diagnostic);
            }
        }

        /*
         * Clean up our side.
         */
        closeSocket();
        // Another thread may have disposed the ciphers during closing
        if (connectionState < cs_CLOSED) {
            connectionState = (oldState == cs_APP_CLOSED) ? cs_APP_CLOSED
                                                              : cs_CLOSED;

            // We should lock readLock and writeLock if no deadlock risks.
            // See comment in changeReadCiphers()
            readCipher.dispose();
            writeCipher.dispose();
        }

        throw closeReason;
    }


    /*
     * Process an incoming alert ... caller must already have synchronized
     * access to "this".
     */
    private void recvAlert(InputRecord r) throws IOException {
        byte level = (byte)r.read();
        byte description = (byte)r.read();
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
                    closeInternal(false);  // reply to close
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
        if (connectionState >= cs_SENT_CLOSE) {
            return;
        }

        // For initial handshaking, don't send alert message to peer if
        // handshaker has not started.
        if (connectionState == cs_HANDSHAKE &&
            (handshaker == null || !handshaker.started())) {
            return;
        }

        OutputRecord r = new OutputRecord(Record.ct_alert);
        r.setVersion(protocolVersion);

        boolean useDebug = debug != null && Debug.isOn("ssl");
        if (useDebug) {
            synchronized (System.out) {
                System.out.print(Thread.currentThread().getName());
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
                System.out.println(Thread.currentThread().getName() +
                    ", Exception sending alert: " + e);
            }
        }
    }

    //
    // VARIOUS OTHER METHODS
    //

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
            readAuthenticator = handshaker.newReadAuthenticator();
        } catch (GeneralSecurityException e) {
            // "can't happen"
            throw new SSLException("Algorithm missing:  ", e);
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

    // used by Handshaker
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
            writeAuthenticator = handshaker.newWriteAuthenticator();
        } catch (GeneralSecurityException e) {
            // "can't happen"
            throw new SSLException("Algorithm missing:  ", e);
        }

        // See comment above.
        oldCipher.dispose();

        // reset the flag of the first application record
        isFirstAppOutputRecord = true;
    }

    /*
     * Updates the SSL version associated with this connection.
     * Called from Handshaker once it has determined the negotiated version.
     */
    synchronized void setVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
        output.r.setVersion(protocolVersion);
    }

    synchronized String getHost() {
        // Note that the host may be null or empty for localhost.
        if (host == null || host.length() == 0) {
            host = getInetAddress().getHostName();
        }
        return host;
    }

    // ONLY used by HttpsClient to setup the URI specified hostname
    //
    // Please NOTE that this method MUST be called before calling to
    // SSLSocket.setSSLParameters(). Otherwise, the {@code host} parameter
    // may override SNIHostName in the customized server name indication.
    synchronized public void setHost(String host) {
        this.host = host;
        this.serverNames =
            Utilities.addToSNIServerNameList(this.serverNames, this.host);
    }

    /**
     * Gets an input stream to read from the peer on the other side.
     * Data read from this stream was always integrity protected in
     * transit, and will usually have been confidentiality protected.
     */
    @Override
    synchronized public InputStream getInputStream() throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }

        /*
         * Can't call isConnected() here, because the Handshakers
         * do some initialization before we actually connect.
         */
        if (connectionState == cs_START) {
            throw new SocketException("Socket is not connected");
        }

        return input;
    }

    /**
     * Gets an output stream to write to the peer on the other side.
     * Data written on this stream is always integrity protected, and
     * will usually be confidentiality protected.
     */
    @Override
    synchronized public OutputStream getOutputStream() throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }

        /*
         * Can't call isConnected() here, because the Handshakers
         * do some initialization before we actually connect.
         */
        if (connectionState == cs_START) {
            throw new SocketException("Socket is not connected");
        }

        return output;
    }

    /**
     * Returns the the SSL Session in use by this connection.  These can
     * be long lived, and frequently correspond to an entire login session
     * for some user.
     */
    @Override
    public SSLSession getSession() {
        /*
         * Force a synchronous handshake, if appropriate.
         */
        if (getConnectionState() == cs_HANDSHAKE) {
            try {
                // start handshaking, if failed, the connection will be closed.
                startHandshake(false);
            } catch (IOException e) {
                // handshake failed. log and return a nullSession
                if (debug != null && Debug.isOn("handshake")) {
                      System.out.println(Thread.currentThread().getName() +
                          ", IOException in getSession():  " + e);
                }
            }
        }
        synchronized (this) {
            return sess;
        }
    }

    @Override
    synchronized public SSLSession getHandshakeSession() {
        return handshakeSession;
    }

    synchronized void setHandshakeSession(SSLSessionImpl session) {
        handshakeSession = session;
    }

    /**
     * Controls whether new connections may cause creation of new SSL
     * sessions.
     *
     * As long as handshaking has not started, we can change
     * whether we enable session creations.  Otherwise,
     * we will need to wait for the next handshake.
     */
    @Override
    synchronized public void setEnableSessionCreation(boolean flag) {
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
    synchronized public boolean getEnableSessionCreation() {
        return enableSessionCreation;
    }


    /**
     * Sets the flag controlling whether a server mode socket
     * *REQUIRES* SSL client authentication.
     *
     * As long as handshaking has not started, we can change
     * whether client authentication is needed.  Otherwise,
     * we will need to wait for the next handshake.
     */
    @Override
    synchronized public void setNeedClientAuth(boolean flag) {
        doClientAuth = (flag ?
            SSLEngineImpl.clauth_required : SSLEngineImpl.clauth_none);

        if ((handshaker != null) &&
                (handshaker instanceof ServerHandshaker) &&
                !handshaker.activated()) {
            ((ServerHandshaker) handshaker).setClientAuth(doClientAuth);
        }
    }

    @Override
    synchronized public boolean getNeedClientAuth() {
        return (doClientAuth == SSLEngineImpl.clauth_required);
    }

    /**
     * Sets the flag controlling whether a server mode socket
     * *REQUESTS* SSL client authentication.
     *
     * As long as handshaking has not started, we can change
     * whether client authentication is requested.  Otherwise,
     * we will need to wait for the next handshake.
     */
    @Override
    synchronized public void setWantClientAuth(boolean flag) {
        doClientAuth = (flag ?
            SSLEngineImpl.clauth_requested : SSLEngineImpl.clauth_none);

        if ((handshaker != null) &&
                (handshaker instanceof ServerHandshaker) &&
                !handshaker.activated()) {
            ((ServerHandshaker) handshaker).setClientAuth(doClientAuth);
        }
    }

    @Override
    synchronized public boolean getWantClientAuth() {
        return (doClientAuth == SSLEngineImpl.clauth_requested);
    }


    /**
     * Sets the flag controlling whether the socket is in SSL
     * client or server mode.  Must be called before any SSL
     * traffic has started.
     */
    @Override
    @SuppressWarnings("fallthrough")
    synchronized public void setUseClientMode(boolean flag) {
        switch (connectionState) {

        case cs_START:
            /*
             * If we need to change the socket mode and the enabled
             * protocols haven't specifically been set by the user,
             * change them to the corresponding default ones.
             */
            if (roleIsServer != (!flag) &&
                    sslContext.isDefaultProtocolList(enabledProtocols)) {
                enabledProtocols = sslContext.getDefaultProtocolList(!flag);
            }
            roleIsServer = !flag;
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
                 * protocols haven't specifically been set by the user,
                 * change them to the corresponding default ones.
                 */
                if (roleIsServer != (!flag) &&
                        sslContext.isDefaultProtocolList(enabledProtocols)) {
                    enabledProtocols = sslContext.getDefaultProtocolList(!flag);
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
            throw new IllegalArgumentException(
                "Cannot change mode after SSL traffic has started");
        }
    }

    @Override
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
    synchronized public void setEnabledCipherSuites(String[] suites) {
        enabledCipherSuites = new CipherSuiteList(suites);
        if ((handshaker != null) && !handshaker.activated()) {
            handshaker.setEnabledCipherSuites(enabledCipherSuites);
        }
    }

    /**
     * Returns the names of the SSL cipher suites which are currently enabled
     * for use on this connection.  When an SSL socket is first created,
     * all enabled cipher suites <em>(a)</em> protect data confidentiality,
     * by traffic encryption, and <em>(b)</em> can mutually authenticate
     * both clients and servers.  Thus, in some environments, this value
     * might be empty.
     *
     * @return an array of cipher suite names
     */
    @Override
    synchronized public String[] getEnabledCipherSuites() {
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
    synchronized public void setEnabledProtocols(String[] protocols) {
        enabledProtocols = new ProtocolList(protocols);
        if ((handshaker != null) && !handshaker.activated()) {
            handshaker.setEnabledProtocols(enabledProtocols);
        }
    }

    @Override
    synchronized public String[] getEnabledProtocols() {
        return enabledProtocols.toStringArray();
    }

    /**
     * Assigns the socket timeout.
     * @see java.net.Socket#setSoTimeout
     */
    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        if ((debug != null) && Debug.isOn("ssl")) {
            System.out.println(Thread.currentThread().getName() +
                ", setSoTimeout(" + timeout + ") called");
        }

        super.setSoTimeout(timeout);
    }

    /**
     * Registers an event listener to receive notifications that an
     * SSL handshake has completed on this connection.
     */
    @Override
    public synchronized void addHandshakeCompletedListener(
            HandshakeCompletedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
        if (handshakeListeners == null) {
            handshakeListeners = new
                HashMap<HandshakeCompletedListener, AccessControlContext>(4);
        }
        handshakeListeners.put(listener, AccessController.getContext());
    }


    /**
     * Removes a previously registered handshake completion listener.
     */
    @Override
    public synchronized void removeHandshakeCompletedListener(
            HandshakeCompletedListener listener) {
        if (handshakeListeners == null) {
            throw new IllegalArgumentException("no listeners");
        }
        if (handshakeListeners.remove(listener) == null) {
            throw new IllegalArgumentException("listener not registered");
        }
        if (handshakeListeners.isEmpty()) {
            handshakeListeners = null;
        }
    }

    /**
     * Returns the SSLParameters in effect for this SSLSocket.
     */
    @Override
    synchronized public SSLParameters getSSLParameters() {
        SSLParameters params = super.getSSLParameters();

        // the super implementation does not handle the following parameters
        params.setEndpointIdentificationAlgorithm(identificationProtocol);
        params.setAlgorithmConstraints(algorithmConstraints);
        params.setSNIMatchers(sniMatchers);
        params.setServerNames(serverNames);

        return params;
    }

    /**
     * Applies SSLParameters to this socket.
     */
    @Override
    synchronized public void setSSLParameters(SSLParameters params) {
        super.setSSLParameters(params);

        // the super implementation does not handle the following parameters
        identificationProtocol = params.getEndpointIdentificationAlgorithm();
        algorithmConstraints = params.getAlgorithmConstraints();

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
            if (roleIsServer) {
                handshaker.setSNIMatchers(sniMatchers);
            } else {
                handshaker.setSNIServerNames(serverNames);
            }
        }
    }

    //
    // We allocate a separate thread to deliver handshake completion
    // events.  This ensures that the notifications don't block the
    // protocol state machine.
    //
    private static class NotifyHandshakeThread extends Thread {

        private Set<Map.Entry<HandshakeCompletedListener,AccessControlContext>>
                targets;        // who gets notified
        private HandshakeCompletedEvent event;          // the notification

        NotifyHandshakeThread(
            Set<Map.Entry<HandshakeCompletedListener,AccessControlContext>>
            entrySet, HandshakeCompletedEvent e) {

            super("HandshakeCompletedNotify-Thread");
            targets = new HashSet<>(entrySet);          // clone the entry set
            event = e;
        }

        @Override
        public void run() {
            // Don't need to synchronize, as it only runs in one thread.
            for (Map.Entry<HandshakeCompletedListener,AccessControlContext>
                entry : targets) {

                final HandshakeCompletedListener l = entry.getKey();
                AccessControlContext acc = entry.getValue();
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        l.handshakeCompleted(event);
                        return null;
                    }
                }, acc);
            }
        }
    }

    /**
     * Returns a printable representation of this end of the connection.
     */
    @Override
    public String toString() {
        StringBuffer retval = new StringBuffer(80);

        retval.append(Integer.toHexString(hashCode()));
        retval.append("[");
        retval.append(sess.getCipherSuite());
        retval.append(": ");

        retval.append(super.toString());
        retval.append("]");

        return retval.toString();
    }
}
