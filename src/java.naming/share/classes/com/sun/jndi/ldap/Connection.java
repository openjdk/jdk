/*
 * Copyright (c) 1999, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.ldap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocket;

import javax.naming.CommunicationException;
import javax.naming.ServiceUnavailableException;
import javax.naming.NamingException;
import javax.naming.InterruptedNamingException;

import javax.naming.ldap.Control;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;

/**
  * A thread that creates a connection to an LDAP server.
  * After the connection, the thread reads from the connection.
  * A caller can invoke methods on the instance to read LDAP responses
  * and to send LDAP requests.
  * <p>
  * There is a one-to-one correspondence between an LdapClient and
  * a Connection. Access to Connection and its methods is only via
  * LdapClient with two exceptions: SASL authentication and StartTLS.
  * SASL needs to access Connection's socket IO streams (in order to do encryption
  * of the security layer). StartTLS needs to do replace IO streams
  * and close the IO  streams on nonfatal close. The code for SASL
  * authentication can be treated as being the same as from LdapClient
  * because the SASL code is only ever called from LdapClient, from
  * inside LdapClient's synchronized authenticate() method. StartTLS is called
  * directly by the application but should only occur when the underlying
  * connection is quiet.
  * <p>
  * In terms of synchronization, worry about data structures
  * used by the Connection thread because that usage might contend
  * with calls by the main threads (i.e., those that call LdapClient).
  * Main threads need to worry about contention with each other.
  * Fields that Connection thread uses:
  *     inStream - synced access and update; initialized in constructor;
  *           referenced outside class unsync'ed (by LdapSasl) only
  *           when connection is quiet
  *     traceFile, traceTagIn, traceTagOut - no sync; debugging only
  *     parent - no sync; initialized in constructor; no updates
  *     pendingRequests - sync
  *     pauseLock - per-instance lock;
  *     paused - sync via pauseLock (pauseReader())
  * Members used by main threads (LdapClient):
  *     host, port - unsync; read-only access for StartTLS and debug messages
  *     setBound(), setV3() - no sync; called only by LdapClient.authenticate(),
  *             which is a sync method called only when connection is "quiet"
  *     getMsgId() - sync
  *     writeRequest(), removeRequest(),findRequest(), abandonOutstandingReqs() -
  *             access to shared pendingRequests is sync
  *     writeRequest(),  abandonRequest(), ldapUnbind() - access to outStream sync
  *     cleanup() - sync
  *     readReply() - access to sock sync
  *     unpauseReader() - (indirectly via writeRequest) sync on pauseLock
  * Members used by SASL auth (main thread):
  *     inStream, outStream - no sync; used to construct new stream; accessed
  *             only when conn is "quiet" and not shared
  *     replaceStreams() - sync method
  * Members used by StartTLS:
  *     inStream, outStream - no sync; used to record the existing streams;
  *             accessed only when conn is "quiet" and not shared
  *     replaceStreams() - sync method
  * <p>
  * Handles anonymous, simple, and SASL bind for v3; anonymous and simple
  * for v2.
  * %%% made public for access by LdapSasl %%%
  *
  * @author Vincent Ryan
  * @author Rosanna Lee
  * @author Jagane Sundar
  */
public final class Connection implements Runnable {

    private static final boolean debug = false;
    private static final int dump = 0; // > 0 r, > 1 rw


    final private Thread worker;    // Initialized in constructor

    private boolean v3 = true;       // Set in setV3()

    final public String host;  // used by LdapClient for generating exception messages
                         // used by StartTlsResponse when creating an SSL socket
    final public int port;     // used by LdapClient for generating exception messages
                         // used by StartTlsResponse when creating an SSL socket

    private boolean bound = false;   // Set in setBound()

    // All three are initialized in constructor and read-only afterwards
    private OutputStream traceFile = null;
    private String traceTagIn = null;
    private String traceTagOut = null;

    // Initialized in constructor; read and used externally (LdapSasl);
    // Updated in replaceStreams() during "quiet", unshared, period
    public InputStream inStream;   // must be public; used by LdapSasl

    // Initialized in constructor; read and used externally (LdapSasl);
    // Updated in replaceOutputStream() during "quiet", unshared, period
    public OutputStream outStream; // must be public; used by LdapSasl

    // Initialized in constructor; read and used externally (TLS) to
    // get new IO streams; closed during cleanup
    public Socket sock;            // for TLS

    // For processing "disconnect" unsolicited notification
    // Initialized in constructor
    final private LdapClient parent;

    // Incremented and returned in sync getMsgId()
    private int outMsgId = 0;

    //
    // The list of ldapRequests pending on this binding
    //
    // Accessed only within sync methods
    private LdapRequest pendingRequests = null;

    volatile IOException closureReason = null;
    volatile boolean useable = true;  // is Connection still useable

    int readTimeout;
    int connectTimeout;
    private static final boolean IS_HOSTNAME_VERIFICATION_DISABLED
            = hostnameVerificationDisabledValue();

    private static boolean hostnameVerificationDisabledValue() {
        PrivilegedAction<String> act = () -> System.getProperty(
                "com.sun.jndi.ldap.object.disableEndpointIdentification");
        String prop = AccessController.doPrivileged(act);
        if (prop == null) {
            return false;
        }
        return prop.isEmpty() ? true : Boolean.parseBoolean(prop);
    }
    // true means v3; false means v2
    // Called in LdapClient.authenticate() (which is synchronized)
    // when connection is "quiet" and not shared; no need to synchronize
    void setV3(boolean v) {
        v3 = v;
    }

    // A BIND request has been successfully made on this connection
    // When cleaning up, remember to do an UNBIND
    // Called in LdapClient.authenticate() (which is synchronized)
    // when connection is "quiet" and not shared; no need to synchronize
    void setBound() {
        bound = true;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Create an LDAP Binding object and bind to a particular server
    //
    ////////////////////////////////////////////////////////////////////////////

    Connection(LdapClient parent, String host, int port, String socketFactory,
        int connectTimeout, int readTimeout, OutputStream trace) throws NamingException {

        this.host = host;
        this.port = port;
        this.parent = parent;
        this.readTimeout = readTimeout;
        this.connectTimeout = connectTimeout;

        if (trace != null) {
            traceFile = trace;
            traceTagIn = "<- " + host + ":" + port + "\n\n";
            traceTagOut = "-> " + host + ":" + port + "\n\n";
        }

        //
        // Connect to server
        //
        try {
            sock = createSocket(host, port, socketFactory, connectTimeout);

            if (debug) {
                System.err.println("Connection: opening socket: " + host + "," + port);
            }

            inStream = new BufferedInputStream(sock.getInputStream());
            outStream = new BufferedOutputStream(sock.getOutputStream());

        } catch (InvocationTargetException e) {
            Throwable realException = e.getTargetException();
            // realException.printStackTrace();

            CommunicationException ce =
                new CommunicationException(host + ":" + port);
            ce.setRootCause(realException);
            throw ce;
        } catch (Exception e) {
            // We need to have a catch all here and
            // ignore generic exceptions.
            // Also catches all IO errors generated by socket creation.
            CommunicationException ce =
                new CommunicationException(host + ":" + port);
            ce.setRootCause(e);
            throw ce;
        }

        worker = Obj.helper.createThread(this);
        worker.setDaemon(true);
        worker.start();
    }

    /*
     * Create an InetSocketAddress using the specified hostname and port number.
     */
    private InetSocketAddress createInetSocketAddress(String host, int port) {
            return new InetSocketAddress(host, port);
    }

    /*
     * Create a Socket object using the specified socket factory and time limit.
     *
     * If a timeout is supplied and unconnected sockets are supported then
     * an unconnected socket is created and the timeout is applied when
     * connecting the socket. If a timeout is supplied but unconnected sockets
     * are not supported then the timeout is ignored and a connected socket
     * is created.
     */
    private Socket createSocket(String host, int port, String socketFactory,
            int connectTimeout) throws Exception {

        Socket socket = null;

        if (socketFactory != null) {

            // create the factory

            @SuppressWarnings("unchecked")
            Class<? extends SocketFactory> socketFactoryClass =
                (Class<? extends SocketFactory>)Obj.helper.loadClass(socketFactory);
            Method getDefault =
                socketFactoryClass.getMethod("getDefault", new Class<?>[]{});
            SocketFactory factory = (SocketFactory) getDefault.invoke(null, new Object[]{});

            // create the socket

            if (connectTimeout > 0) {

                InetSocketAddress endpoint =
                        createInetSocketAddress(host, port);

                // unconnected socket
                socket = factory.createSocket();

                if (debug) {
                    System.err.println("Connection: creating socket with " +
                            "a timeout using supplied socket factory");
                }

                // connected socket
                socket.connect(endpoint, connectTimeout);
            }

            // continue (but ignore connectTimeout)
            if (socket == null) {
                if (debug) {
                    System.err.println("Connection: creating socket using " +
                        "supplied socket factory");
                }
                // connected socket
                socket = factory.createSocket(host, port);
            }
        } else {

            if (connectTimeout > 0) {

                    InetSocketAddress endpoint = createInetSocketAddress(host, port);

                    socket = new Socket();

                    if (debug) {
                        System.err.println("Connection: creating socket with " +
                            "a timeout");
                    }
                    socket.connect(endpoint, connectTimeout);
            }

            // continue (but ignore connectTimeout)

            if (socket == null) {
                if (debug) {
                    System.err.println("Connection: creating socket");
                }
                // connected socket
                socket = new Socket(host, port);
            }
        }

        // For LDAP connect timeouts on LDAP over SSL connections must treat
        // the SSL handshake following socket connection as part of the timeout.
        // So explicitly set a socket read timeout, trigger the SSL handshake,
        // then reset the timeout.
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            int socketTimeout = sslSocket.getSoTimeout();
            if (!IS_HOSTNAME_VERIFICATION_DISABLED) {
                SSLParameters param = sslSocket.getSSLParameters();
                param.setEndpointIdentificationAlgorithm("LDAPS");
                sslSocket.setSSLParameters(param);
            }
            if (connectTimeout > 0) {
                sslSocket.setSoTimeout(connectTimeout); // reuse full timeout value
            }
            sslSocket.startHandshake();
            sslSocket.setSoTimeout(socketTimeout);
        }
        return socket;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Methods to IO to the LDAP server
    //
    ////////////////////////////////////////////////////////////////////////////

    synchronized int getMsgId() {
        return ++outMsgId;
    }

    LdapRequest writeRequest(BerEncoder ber, int msgId) throws IOException {
        return writeRequest(ber, msgId, false /* pauseAfterReceipt */, -1);
    }

    LdapRequest writeRequest(BerEncoder ber, int msgId,
        boolean pauseAfterReceipt) throws IOException {
        return writeRequest(ber, msgId, pauseAfterReceipt, -1);
    }

    LdapRequest writeRequest(BerEncoder ber, int msgId,
        boolean pauseAfterReceipt, int replyQueueCapacity) throws IOException {

        LdapRequest req =
            new LdapRequest(msgId, pauseAfterReceipt, replyQueueCapacity);
        addRequest(req);

        if (traceFile != null) {
            Ber.dumpBER(traceFile, traceTagOut, ber.getBuf(), 0, ber.getDataLen());
        }


        // unpause reader so that it can get response
        // NOTE: Must do this before writing request, otherwise might
        // create a race condition where the writer unblocks its own response
        unpauseReader();

        if (debug) {
            System.err.println("Writing request to: " + outStream);
        }

        try {
            synchronized (this) {
                outStream.write(ber.getBuf(), 0, ber.getDataLen());
                outStream.flush();
            }
        } catch (IOException e) {
            cleanup(null, true);
            throw (closureReason = e); // rethrow
        }

        return req;
    }

    /**
     * Reads a reply; waits until one is ready.
     */
    BerDecoder readReply(LdapRequest ldr)
            throws IOException, NamingException {
        BerDecoder rber;

        // Track down elapsed time to workaround spurious wakeups
        long elapsedMilli = 0;
        long elapsedNano = 0;

        while (((rber = ldr.getReplyBer()) == null) &&
                (readTimeout <= 0 || elapsedMilli < readTimeout))
        {
            try {
                // If socket closed, don't even try
                synchronized (this) {
                    if (sock == null) {
                        throw new ServiceUnavailableException(host + ":" + port +
                            "; socket closed");
                    }
                }
                synchronized (ldr) {
                    // check if condition has changed since our last check
                    rber = ldr.getReplyBer();
                    if (rber == null) {
                        if (readTimeout > 0) {  // Socket read timeout is specified
                            long beginNano = System.nanoTime();

                            // will be woken up before readTimeout if reply is
                            // available
                            ldr.wait(readTimeout - elapsedMilli);
                            elapsedNano += (System.nanoTime() - beginNano);
                            elapsedMilli += elapsedNano / 1000_000;
                            elapsedNano %= 1000_000;

                        } else {
                            // no timeout is set so we wait infinitely until
                            // a response is received
                            // http://docs.oracle.com/javase/8/docs/technotes/guides/jndi/jndi-ldap.html#PROP
                            ldr.wait();
                        }
                    } else {
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                throw new InterruptedNamingException(
                    "Interrupted during LDAP operation");
            }
        }

        if ((rber == null) && (elapsedMilli >= readTimeout)) {
            abandonRequest(ldr, null);
            throw new NamingException("LDAP response read timed out, timeout used:"
                            + readTimeout + "ms." );

        }
        return rber;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Methods to add, find, delete, and abandon requests made to server
    //
    ////////////////////////////////////////////////////////////////////////////

    private synchronized void addRequest(LdapRequest ldapRequest) {

        LdapRequest ldr = pendingRequests;
        if (ldr == null) {
            pendingRequests = ldapRequest;
            ldapRequest.next = null;
        } else {
            ldapRequest.next = pendingRequests;
            pendingRequests = ldapRequest;
        }
    }

    synchronized LdapRequest findRequest(int msgId) {

        LdapRequest ldr = pendingRequests;
        while (ldr != null) {
            if (ldr.msgId == msgId) {
                return ldr;
            }
            ldr = ldr.next;
        }
        return null;

    }

    synchronized void removeRequest(LdapRequest req) {
        LdapRequest ldr = pendingRequests;
        LdapRequest ldrprev = null;

        while (ldr != null) {
            if (ldr == req) {
                ldr.cancel();

                if (ldrprev != null) {
                    ldrprev.next = ldr.next;
                } else {
                    pendingRequests = ldr.next;
                }
                ldr.next = null;
            }
            ldrprev = ldr;
            ldr = ldr.next;
        }
    }

    void abandonRequest(LdapRequest ldr, Control[] reqCtls) {
        // Remove from queue
        removeRequest(ldr);

        BerEncoder ber = new BerEncoder(256);
        int abandonMsgId = getMsgId();

        //
        // build the abandon request.
        //
        try {
            ber.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
                ber.encodeInt(abandonMsgId);
                ber.encodeInt(ldr.msgId, LdapClient.LDAP_REQ_ABANDON);

                if (v3) {
                    LdapClient.encodeControls(ber, reqCtls);
                }
            ber.endSeq();

            if (traceFile != null) {
                Ber.dumpBER(traceFile, traceTagOut, ber.getBuf(), 0,
                    ber.getDataLen());
            }

            synchronized (this) {
                outStream.write(ber.getBuf(), 0, ber.getDataLen());
                outStream.flush();
            }

        } catch (IOException ex) {
            //System.err.println("ldap.abandon: " + ex);
        }

        // Don't expect any response for the abandon request.
    }

    synchronized void abandonOutstandingReqs(Control[] reqCtls) {
        LdapRequest ldr = pendingRequests;

        while (ldr != null) {
            abandonRequest(ldr, reqCtls);
            pendingRequests = ldr = ldr.next;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Methods to unbind from server and clear up resources when object is
    // destroyed.
    //
    ////////////////////////////////////////////////////////////////////////////

    private void ldapUnbind(Control[] reqCtls) {

        BerEncoder ber = new BerEncoder(256);
        int unbindMsgId = getMsgId();

        //
        // build the unbind request.
        //

        try {

            ber.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
                ber.encodeInt(unbindMsgId);
                // IMPLICIT TAGS
                ber.encodeByte(LdapClient.LDAP_REQ_UNBIND);
                ber.encodeByte(0);

                if (v3) {
                    LdapClient.encodeControls(ber, reqCtls);
                }
            ber.endSeq();

            if (traceFile != null) {
                Ber.dumpBER(traceFile, traceTagOut, ber.getBuf(),
                    0, ber.getDataLen());
            }

            synchronized (this) {
                outStream.write(ber.getBuf(), 0, ber.getDataLen());
                outStream.flush();
            }

        } catch (IOException ex) {
            //System.err.println("ldap.unbind: " + ex);
        }

        // Don't expect any response for the unbind request.
    }

    /**
     * @param reqCtls Possibly null request controls that accompanies the
     *    abandon and unbind LDAP request.
     * @param notifyParent true means to call parent LdapClient back, notifying
     *    it that the connection has been closed; false means not to notify
     *    parent. If LdapClient invokes cleanup(), notifyParent should be set to
     *    false because LdapClient already knows that it is closing
     *    the connection. If Connection invokes cleanup(), notifyParent should be
     *    set to true because LdapClient needs to know about the closure.
     */
    void cleanup(Control[] reqCtls, boolean notifyParent) {
        boolean nparent = false;

        synchronized (this) {
            useable = false;

            if (sock != null) {
                if (debug) {
                    System.err.println("Connection: closing socket: " + host + "," + port);
                }
                try {
                    if (!notifyParent) {
                        abandonOutstandingReqs(reqCtls);
                    }
                    if (bound) {
                        ldapUnbind(reqCtls);
                    }
                } finally {
                    try {
                        outStream.flush();
                        sock.close();
                        unpauseReader();
                    } catch (IOException ie) {
                        if (debug)
                            System.err.println("Connection: problem closing socket: " + ie);
                    }
                    if (!notifyParent) {
                        LdapRequest ldr = pendingRequests;
                        while (ldr != null) {
                            ldr.cancel();
                            ldr = ldr.next;
                        }
                    }
                    sock = null;
                }
                nparent = notifyParent;
            }
            if (nparent) {
                LdapRequest ldr = pendingRequests;
                while (ldr != null) {

                    synchronized (ldr) {
                        ldr.notify();
                        ldr = ldr.next;
                    }
                }
            }
        }
        if (nparent) {
            parent.processConnectionClosure();
        }
    }


    // Assume everything is "quiet"
    // "synchronize" might lead to deadlock so don't synchronize method
    // Use streamLock instead for synchronizing update to stream

    synchronized public void replaceStreams(InputStream newIn, OutputStream newOut) {
        if (debug) {
            System.err.println("Replacing " + inStream + " with: " + newIn);
            System.err.println("Replacing " + outStream + " with: " + newOut);
        }

        inStream = newIn;

        // Cleanup old stream
        try {
            outStream.flush();
        } catch (IOException ie) {
            if (debug)
                System.err.println("Connection: cannot flush outstream: " + ie);
        }

        // Replace stream
        outStream = newOut;
    }

    /**
     * Used by Connection thread to read inStream into a local variable.
     * This ensures that there is no contention between the main thread
     * and the Connection thread when the main thread updates inStream.
     */
    synchronized private InputStream getInputStream() {
        return inStream;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Code for pausing/unpausing the reader thread ('worker')
    //
    ////////////////////////////////////////////////////////////////////////////

    /*
     * The main idea is to mark requests that need the reader thread to
     * pause after getting the response. When the reader thread gets the response,
     * it waits on a lock instead of returning to the read(). The next time a
     * request is sent, the reader is automatically unblocked if necessary.
     * Note that the reader must be unblocked BEFORE the request is sent.
     * Otherwise, there is a race condition where the request is sent and
     * the reader thread might read the response and be unblocked
     * by writeRequest().
     *
     * This pause gives the main thread (StartTLS or SASL) an opportunity to
     * update the reader's state (e.g., its streams) if necessary.
     * The assumption is that the connection will remain quiet during this pause
     * (i.e., no intervening requests being sent).
     *<p>
     * For dealing with StartTLS close,
     * when the read() exits either due to EOF or an exception,
     * the reader thread checks whether there is a new stream to read from.
     * If so, then it reattempts the read. Otherwise, the EOF or exception
     * is processed and the reader thread terminates.
     * In a StartTLS close, the client first replaces the SSL IO streams with
     * plain ones and then closes the SSL socket.
     * If the reader thread attempts to read, or was reading, from
     * the SSL socket (that is, it got to the read BEFORE replaceStreams()),
     * the SSL socket close will cause the reader thread to
     * get an EOF/exception and reexamine the input stream.
     * If the reader thread sees a new stream, it reattempts the read.
     * If the underlying socket is still alive, then the new read will succeed.
     * If the underlying socket has been closed also, then the new read will
     * fail and the reader thread exits.
     * If the reader thread attempts to read, or was reading, from the plain
     * socket (that is, it got to the read AFTER replaceStreams()), the
     * SSL socket close will have no effect on the reader thread.
     *
     * The check for new stream is made only
     * in the first attempt at reading a BER buffer; the reader should
     * never be in midst of reading a buffer when a nonfatal close occurs.
     * If this occurs, then the connection is in an inconsistent state and
     * the safest thing to do is to shut it down.
     */

    private Object pauseLock = new Object();  // lock for reader to wait on while paused
    private boolean paused = false;           // paused state of reader

    /*
     * Unpauses reader thread if it was paused
     */
    private void unpauseReader() throws IOException {
        synchronized (pauseLock) {
            if (paused) {
                if (debug) {
                    System.err.println("Unpausing reader; read from: " +
                                        inStream);
                }
                paused = false;
                pauseLock.notify();
            }
        }
    }

     /*
     * Pauses reader so that it stops reading from the input stream.
     * Reader blocks on pauseLock instead of read().
     * MUST be called from within synchronized (pauseLock) clause.
     */
    private void pauseReader() throws IOException {
        if (debug) {
            System.err.println("Pausing reader;  was reading from: " +
                                inStream);
        }
        paused = true;
        try {
            while (paused) {
                pauseLock.wait(); // notified by unpauseReader
            }
        } catch (InterruptedException e) {
            throw new InterruptedIOException(
                    "Pause/unpause reader has problems.");
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // The LDAP Binding thread. It does the mux/demux of multiple requests
    // on the same TCP connection.
    //
    ////////////////////////////////////////////////////////////////////////////


    public void run() {
        byte inbuf[];   // Buffer for reading incoming bytes
        int inMsgId;    // Message id of incoming response
        int bytesread;  // Number of bytes in inbuf
        int br;         // Temp; number of bytes read from stream
        int offset;     // Offset of where to store bytes in inbuf
        int seqlen;     // Length of ASN sequence
        int seqlenlen;  // Number of sequence length bytes
        boolean eos;    // End of stream
        BerDecoder retBer;    // Decoder for ASN.1 BER data from inbuf
        InputStream in = null;

        try {
            while (true) {
                try {
                    // type and length (at most 128 octets for long form)
                    inbuf = new byte[129];

                    offset = 0;
                    seqlen = 0;
                    seqlenlen = 0;

                    in = getInputStream();

                    // check that it is the beginning of a sequence
                    bytesread = in.read(inbuf, offset, 1);
                    if (bytesread < 0) {
                        if (in != getInputStream()) {
                            continue;   // a new stream to try
                        } else {
                            break; // EOF
                        }
                    }

                    if (inbuf[offset++] != (Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR))
                        continue;

                    // get length of sequence
                    bytesread = in.read(inbuf, offset, 1);
                    if (bytesread < 0)
                        break; // EOF
                    seqlen = inbuf[offset++];

                    // if high bit is on, length is encoded in the
                    // subsequent length bytes and the number of length bytes
                    // is equal to & 0x80 (i.e. length byte with high bit off).
                    if ((seqlen & 0x80) == 0x80) {
                        seqlenlen = seqlen & 0x7f;  // number of length bytes

                        bytesread = 0;
                        eos = false;

                        // Read all length bytes
                        while (bytesread < seqlenlen) {
                            br = in.read(inbuf, offset+bytesread,
                                seqlenlen-bytesread);
                            if (br < 0) {
                                eos = true;
                                break; // EOF
                            }
                            bytesread += br;
                        }

                        // end-of-stream reached before length bytes are read
                        if (eos)
                            break;  // EOF

                        // Add contents of length bytes to determine length
                        seqlen = 0;
                        for( int i = 0; i < seqlenlen; i++) {
                            seqlen = (seqlen << 8) + (inbuf[offset+i] & 0xff);
                        }
                        offset += bytesread;
                    }

                    // read in seqlen bytes
                    byte[] left = readFully(in, seqlen);
                    inbuf = Arrays.copyOf(inbuf, offset + left.length);
                    System.arraycopy(left, 0, inbuf, offset, left.length);
                    offset += left.length;
/*
if (dump > 0) {
System.err.println("seqlen: " + seqlen);
System.err.println("bufsize: " + offset);
System.err.println("bytesleft: " + bytesleft);
System.err.println("bytesread: " + bytesread);
}
*/


                    try {
                        retBer = new BerDecoder(inbuf, 0, offset);

                        if (traceFile != null) {
                            Ber.dumpBER(traceFile, traceTagIn, inbuf, 0, offset);
                        }

                        retBer.parseSeq(null);
                        inMsgId = retBer.parseInt();
                        retBer.reset(); // reset offset

                        boolean needPause = false;

                        if (inMsgId == 0) {
                            // Unsolicited Notification
                            parent.processUnsolicited(retBer);
                        } else {
                            LdapRequest ldr = findRequest(inMsgId);

                            if (ldr != null) {

                                /**
                                 * Grab pauseLock before making reply available
                                 * to ensure that reader goes into paused state
                                 * before writer can attempt to unpause reader
                                 */
                                synchronized (pauseLock) {
                                    needPause = ldr.addReplyBer(retBer);
                                    if (needPause) {
                                        /*
                                         * Go into paused state; release
                                         * pauseLock
                                         */
                                        pauseReader();
                                    }

                                    // else release pauseLock
                                }
                            } else {
                                // System.err.println("Cannot find" +
                                //              "LdapRequest for " + inMsgId);
                            }
                        }
                    } catch (Ber.DecodeException e) {
                        //System.err.println("Cannot parse Ber");
                    }
                } catch (IOException ie) {
                    if (debug) {
                        System.err.println("Connection: Inside Caught " + ie);
                        ie.printStackTrace();
                    }

                    if (in != getInputStream()) {
                        // A new stream to try
                        // Go to top of loop and continue
                    } else {
                        if (debug) {
                            System.err.println("Connection: rethrowing " + ie);
                        }
                        throw ie;  // rethrow exception
                    }
                }
            }

            if (debug) {
                System.err.println("Connection: end-of-stream detected: "
                    + in);
            }
        } catch (IOException ex) {
            if (debug) {
                System.err.println("Connection: Caught " + ex);
            }
            closureReason = ex;
        } finally {
            cleanup(null, true); // cleanup
        }
        if (debug) {
            System.err.println("Connection: Thread Exiting");
        }
    }

    private static byte[] readFully(InputStream is, int length)
        throws IOException
    {
        byte[] buf = new byte[Math.min(length, 8192)];
        int nread = 0;
        while (nread < length) {
            int bytesToRead;
            if (nread >= buf.length) {  // need to allocate a larger buffer
                bytesToRead = Math.min(length - nread, buf.length + 8192);
                if (buf.length < nread + bytesToRead) {
                    buf = Arrays.copyOf(buf, nread + bytesToRead);
                }
            } else {
                bytesToRead = buf.length - nread;
            }
            int count = is.read(buf, nread, bytesToRead);
            if (count < 0) {
                if (buf.length != nread)
                    buf = Arrays.copyOf(buf, nread);
                break;
            }
            nread += count;
        }
        return buf;
    }

    // This code must be uncommented to run the LdapAbandonTest.
    /*public void sendSearchReqs(String dn, int numReqs) {
        int i;
        String attrs[] = null;
        for(i = 1; i <= numReqs; i++) {
            BerEncoder ber = new BerEncoder(2048);

            try {
            ber.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
                ber.encodeInt(i);
                ber.beginSeq(LdapClient.LDAP_REQ_SEARCH);
                    ber.encodeString(dn == null ? "" : dn);
                    ber.encodeInt(0, LdapClient.LBER_ENUMERATED);
                    ber.encodeInt(3, LdapClient.LBER_ENUMERATED);
                    ber.encodeInt(0);
                    ber.encodeInt(0);
                    ber.encodeBoolean(true);
                    LdapClient.encodeFilter(ber, "");
                    ber.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
                        ber.encodeStringArray(attrs);
                    ber.endSeq();
                ber.endSeq();
            ber.endSeq();
            writeRequest(ber, i);
            //System.err.println("wrote request " + i);
            } catch (Exception ex) {
            //System.err.println("ldap.search: Caught " + ex + " building req");
            }

        }
    } */
}
