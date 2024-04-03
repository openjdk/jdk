/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.sasl.SaslException;

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

    private final Thread worker;    // Initialized in constructor

    private boolean v3 = true;     // Set in setV3()

    public final String host;  // used by LdapClient for generating exception messages
                               // used by StartTlsResponse when creating an SSL socket
    public final int port;     // used by LdapClient for generating exception messages
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
    private final LdapClient parent;

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

    // Is connection upgraded to SSL via STARTTLS extended operation
    private volatile boolean isUpgradedToStartTls;

    // Lock to maintain isUpgradedToStartTls state
    final ReentrantLock startTlsLock = new ReentrantLock();

    // Connection instance lock
    private final ReentrantLock lock = new ReentrantLock();

    private static final boolean IS_HOSTNAME_VERIFICATION_DISABLED
            = hostnameVerificationDisabledValue();

    private static boolean hostnameVerificationDisabledValue() {
        PrivilegedAction<String> act = () -> System.getProperty(
                "com.sun.jndi.ldap.object.disableEndpointIdentification");
        @SuppressWarnings("removal")
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
            Throwable realException = e.getCause();
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

        SocketFactory factory = getSocketFactory(socketFactory);
        assert factory != null;
        Socket socket = createConnectionSocket(host, port, factory, connectTimeout);

        // the handshake for SSL connection with server and reset timeout for the socket
        if (socket instanceof SSLSocket sslSocket) {
            try {
                initialSSLHandshake(sslSocket, connectTimeout);
            } catch (Exception e) {
                // 8314063 the socket is not closed after the failure of handshake
                // close the socket while the error happened
                closeOpenedSocket(socket);
                throw e;
            }
        }
        return socket;
    }

    private SocketFactory getSocketFactory(String socketFactoryName) throws Exception {
        if (socketFactoryName == null) {
            if (debug) {
                System.err.println("Connection: using default SocketFactory");
            }
            return SocketFactory.getDefault();
        } else {
            if (debug) {
                System.err.println("Connection: loading supplied SocketFactory: " + socketFactoryName);
            }
            @SuppressWarnings("unchecked")
            Class<? extends SocketFactory> socketFactoryClass =
                    (Class<? extends SocketFactory>) Obj.helper.loadClass(socketFactoryName);
            Method getDefault =
                    socketFactoryClass.getMethod("getDefault");
            SocketFactory factory = (SocketFactory) getDefault.invoke(null, new Object[]{});
            return factory;
        }
    }

    private Socket createConnectionSocket(String host, int port, SocketFactory factory,
                                          int connectTimeout) throws IOException {
        Socket socket = null;

        // if timeout is supplied, try to use unconnected socket for connecting with timeout
        if (connectTimeout > 0) {
            if (debug) {
                System.err.println("Connection: creating socket with a connect timeout");
            }
            try {
                // unconnected socket
                socket = factory.createSocket();
            } catch (IOException e) {
                // unconnected socket is likely not supported by the SocketFactory
                if (debug) {
                    System.err.println("Connection: unconnected socket not supported by SocketFactory");
                }
            }
            if (socket != null) {
                InetSocketAddress endpoint = createInetSocketAddress(host, port);
                // connect socket with a timeout
                socket.connect(endpoint, connectTimeout);
            }
        }

        // either no timeout was supplied or unconnected socket did not work
        if (socket == null) {
            // create connected socket
            if (debug) {
                System.err.println("Connection: creating connected socket with no connect timeout");
            }
            socket = factory.createSocket(host, port);
        }
        return socket;
    }

    // For LDAP connect timeouts on LDAP over SSL connections must treat
    // the SSL handshake following socket connection as part of the timeout.
    // So explicitly set a socket read timeout, trigger the SSL handshake,
    // then reset the timeout.
    private void initialSSLHandshake(SSLSocket sslSocket, int connectTimeout) throws Exception {

            if (!IS_HOSTNAME_VERIFICATION_DISABLED) {
                SSLParameters param = sslSocket.getSSLParameters();
                param.setEndpointIdentificationAlgorithm("LDAPS");
                sslSocket.setSSLParameters(param);
            }
            setHandshakeCompletedListener(sslSocket);
            if (connectTimeout > 0) {
                int socketTimeout = sslSocket.getSoTimeout();
                sslSocket.setSoTimeout(connectTimeout); // reuse full timeout value
                sslSocket.startHandshake();
                sslSocket.setSoTimeout(socketTimeout);
            }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Methods to IO to the LDAP server
    //
    ////////////////////////////////////////////////////////////////////////////

    int getMsgId() {
        lock.lock();
        try {
            return ++outMsgId;
        } finally {
            lock.unlock();
        }
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
            lock.lock();
            try {
                outStream.write(ber.getBuf(), 0, ber.getDataLen());
                outStream.flush();
            } finally {
                lock.unlock();
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
    BerDecoder readReply(LdapRequest ldr) throws NamingException {
        BerDecoder rber;

        // If socket closed, don't even try
        lock.lock();
        try {
            if (sock == null) {
                throw new ServiceUnavailableException(host + ":" + port +
                    "; socket closed");
            }
        } finally {
            lock.unlock();
        }

        IOException ioException = null;
        try {
            // if no timeout is set so we wait infinitely until
            // a response is received OR until the connection is closed or cancelled
            // http://docs.oracle.com/javase/8/docs/technotes/guides/jndi/jndi-ldap.html#PROP
            rber = ldr.getReplyBer(readTimeout);
        } catch (InterruptedException ex) {
            throw new InterruptedNamingException(
                "Interrupted during LDAP operation");
        } catch (IOException ioe) {
            // Connection is timed out OR closed/cancelled
            // getReplyBer throws IOException when the requests needs to be abandoned
            ioException = ioe;
            rber = null;
        }

        if (rber == null) {
            abandonRequest(ldr, null);
        }
        // ioException can be not null in the following cases:
        //  a) The response is timed-out
        //  b) LDAP request connection has been closed
        // If the request has been cancelled - CommunicationException is
        // thrown directly from LdapRequest.getReplyBer, since there is no
        // need to abandon request.
        // The exception message is initialized in LdapRequest::getReplyBer
        if (ioException != null) {
            // Throw CommunicationException after all cleanups are done
            String message = ioException.getMessage();
            var ce = new CommunicationException(message);
            ce.initCause(ioException);
            throw ce;
        }
        return rber;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Methods to add, find, delete, and abandon requests made to server
    //
    ////////////////////////////////////////////////////////////////////////////

    private void addRequest(LdapRequest ldapRequest) {
        lock.lock();
        try {
            LdapRequest ldr = pendingRequests;
            if (ldr == null) {
                pendingRequests = ldapRequest;
                ldapRequest.next = null;
            } else {
                ldapRequest.next = pendingRequests;
                pendingRequests = ldapRequest;
            }
        } finally {
            lock.unlock();
        }
    }

    LdapRequest findRequest(int msgId) {
        lock.lock();
        try {
            LdapRequest ldr = pendingRequests;
            while (ldr != null) {
                if (ldr.msgId == msgId) {
                    return ldr;
                }
                ldr = ldr.next;
            }
            return null;
        } finally {
            lock.unlock();
        }

    }

    void removeRequest(LdapRequest req) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
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

            lock.lock();
            try {
                outStream.write(ber.getBuf(), 0, ber.getDataLen());
                outStream.flush();
            } finally {
                lock.unlock();
            }

        } catch (IOException ex) {
            //System.err.println("ldap.abandon: " + ex);
        }

        // Don't expect any response for the abandon request.
    }

    void abandonOutstandingReqs(Control[] reqCtls) {
        lock.lock();
        try {
            LdapRequest ldr = pendingRequests;

            while (ldr != null) {
                abandonRequest(ldr, reqCtls);
                pendingRequests = ldr = ldr.next;
            }
        } finally {
            lock.unlock();
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

            lock.lock();
            try {
                outStream.write(ber.getBuf(), 0, ber.getDataLen());
                outStream.flush();
            } finally {
                lock.unlock();
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
        lock.lock();
        try {
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

                    flushAndCloseOutputStream();
                    // 8313657 socket is not closed until GC is run
                    closeOpenedSocket(sock);
                    tryUnpauseReader();

                    if (!notifyParent) {
                        LdapRequest ldr = pendingRequests;
                        while (ldr != null) {
                            ldr.cancel();
                            ldr = ldr.next;
                        }
                    }
                    if (isTlsConnection() && tlsHandshakeListener != null) {
                        if (closureReason != null) {
                            CommunicationException ce = new CommunicationException();
                            ce.setRootCause(closureReason);
                            tlsHandshakeListener.tlsHandshakeCompleted.completeExceptionally(ce);
                        } else {
                            tlsHandshakeListener.tlsHandshakeCompleted.cancel(false);
                        }
                    }
                    sock = null;
                }
                nparent = notifyParent;
            }
            if (nparent) {
                LdapRequest ldr = pendingRequests;
                while (ldr != null) {
                    ldr.close();
                    ldr = ldr.next;
                }
            }
        } finally {
            lock.unlock();
        }
        if (nparent) {
            parent.processConnectionClosure();
        }
    }

    // flush and close output stream
    private void flushAndCloseOutputStream() {
        try {
            outStream.flush();
        } catch (IOException ioEx) {
            if (debug)
                System.err.println("Connection.flushOutputStream: OutputStream flush problem " + ioEx);
        }
        try {
            outStream.close();
        } catch (IOException ioEx) {
            if (debug)
                System.err.println("Connection.closeOutputStream: OutputStream close problem " + ioEx);
        }
    }

    // close socket
    private void closeOpenedSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException ioEx) {
            if (debug) {
                System.err.println("Connection.closeConnectionSocket: Socket close problem: " + ioEx);
                System.err.println("Socket isClosed: " + sock.isClosed());
            }
        }
    }

    // unpause reader
    private void tryUnpauseReader() {
        try {
            unpauseReader();
        } catch (IOException ioEx) {
            if (debug)
                System.err.println("Connection.tryUnpauseReader: unpauseReader problem " + ioEx);
        }
    }

    // Assume everything is "quiet"
    // "synchronize" might lead to deadlock so don't synchronize method
    // Use streamLock instead for synchronizing update to stream

    public void replaceStreams(InputStream newIn, OutputStream newOut) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /*
     * Replace streams and set isUpdradedToStartTls flag to the provided value
     */
    public void replaceStreams(InputStream newIn, OutputStream newOut, boolean isStartTls) {
        lock.lock();
        try {
            startTlsLock.lock();
            try {
                replaceStreams(newIn, newOut);
                isUpgradedToStartTls = isStartTls;
            } finally {
                startTlsLock.unlock();
            }
        } finally {
            lock.unlock();
        }
    }

    /*
     * Returns true if connection was upgraded to SSL with STARTTLS extended operation
     */
    public boolean isUpgradedToStartTls() {
        return isUpgradedToStartTls;
    }

    /**
     * Used by Connection thread to read inStream into a local variable.
     * This ensures that there is no contention between the main thread
     * and the Connection thread when the main thread updates inStream.
     */
    private InputStream getInputStream() {
        lock.lock();
        try {
            return inStream;
        } finally {
            lock.unlock();
        }
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

    // lock for reader to wait on while paused
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pauseCondition = pauseLock.newCondition();
    private boolean paused = false;           // paused state of reader

    /*
     * Unpauses reader thread if it was paused
     */
    private void unpauseReader() throws IOException {
        pauseLock.lock();
        try {
            if (paused) {
                if (debug) {
                    System.err.println("Unpausing reader; read from: " +
                                        inStream);
                }
                paused = false;
                pauseCondition.signal();
            }
        } finally {
            pauseLock.unlock();
        }
    }

     /*
     * Pauses reader so that it stops reading from the input stream.
     * Reader blocks on pauseLock instead of read().
     * MUST be called with pauseLock locked.
     */
    private void pauseReader() throws IOException {
        assert pauseLock.isHeldByCurrentThread();
        if (debug) {
            System.err.println("Pausing reader;  was reading from: " +
                                inStream);
        }
        paused = true;
        try {
            while (paused) {
                pauseCondition.await(); // notified by unpauseReader
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
                        // Check the length of length field, since seqlen is int
                        // the number of bytes can't be greater than 4
                        if (seqlenlen > 4) {
                            throw new IOException("Length coded with too many bytes: " + seqlenlen);
                        }

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

                    if (seqlenlen > bytesread) {
                        throw new IOException("Unexpected EOF while reading length");
                    }

                    if (seqlen < 0) {
                        throw new IOException("Length too big: " + (((long) seqlen) & 0xFFFFFFFFL));
                    }
                    // read in seqlen bytes
                    byte[] left = readFully(in, seqlen);
                    inbuf = Arrays.copyOf(inbuf, offset + left.length);
                    System.arraycopy(left, 0, inbuf, offset, left.length);
                    offset += left.length;

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
                                pauseLock.lock();
                                try {
                                    needPause = ldr.addReplyBer(retBer);
                                    if (needPause) {
                                        /*
                                         * Go into paused state; release
                                         * pauseLock
                                         */
                                        pauseReader();
                                    }

                                    // else release pauseLock
                                } finally {
                                    pauseLock.unlock();
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

    public boolean isTlsConnection() {
        return (sock instanceof SSLSocket) || isUpgradedToStartTls;
    }

    /*
     * tlsHandshakeListener can be created for initial secure connection
     * and updated by StartTLS extended operation. It is used later by LdapClient
     * to create TLS Channel Binding data on the base of TLS server certificate
     */
    private volatile HandshakeListener tlsHandshakeListener;

    public void setHandshakeCompletedListener(SSLSocket sslSocket) {
        lock.lock();
        try {
            if (tlsHandshakeListener != null)
                tlsHandshakeListener.tlsHandshakeCompleted.cancel(false);

            tlsHandshakeListener = new HandshakeListener();
            sslSocket.addHandshakeCompletedListener(tlsHandshakeListener);
        } finally {
            lock.unlock();
        }
    }

    public X509Certificate getTlsServerCertificate()
        throws SaslException {
        try {
            if (isTlsConnection() && tlsHandshakeListener != null)
                return tlsHandshakeListener.tlsHandshakeCompleted.get();
        } catch (InterruptedException iex) {
            throw new SaslException("TLS Handshake Exception ", iex);
        } catch (ExecutionException eex) {
            throw new SaslException("TLS Handshake Exception ", eex.getCause());
        }
        return null;
    }

    private class HandshakeListener implements HandshakeCompletedListener {

        private final CompletableFuture<X509Certificate> tlsHandshakeCompleted =
                new CompletableFuture<>();
        @Override
        public void handshakeCompleted(HandshakeCompletedEvent event) {
            try {
                X509Certificate tlsServerCert = null;
                Certificate[] certs;
                if (event.getSocket().getUseClientMode()) {
                    certs = event.getPeerCertificates();
                } else {
                    certs = event.getLocalCertificates();
                }
                if (certs != null && certs.length > 0 &&
                        certs[0] instanceof X509Certificate) {
                    tlsServerCert = (X509Certificate) certs[0];
                }
                tlsHandshakeCompleted.complete(tlsServerCert);
            } catch (SSLPeerUnverifiedException ex) {
                CommunicationException ce = new CommunicationException();
                ce.setRootCause(closureReason);
                tlsHandshakeCompleted.completeExceptionally(ex);
            }
        }
    }
}
