/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import jdk.internal.misc.JavaNetInetAddressAccess;
import jdk.internal.misc.SharedSecrets;

/**
 * Implementation of an SSL socket.
 * <P>
 * This is a normal connection type socket, implementing SSL over some lower
 * level socket, such as TCP.  Because it is layered over some lower level
 * socket, it MUST override all default socket methods.
 * <P>
 * This API offers a non-traditional option for establishing SSL
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
public final class SSLSocketImpl
        extends BaseSSLSocketImpl implements SSLTransport {

    final SSLContextImpl            sslContext;
    final TransportContext          conContext;

    private final AppInputStream    appInput = new AppInputStream();
    private final AppOutputStream   appOutput = new AppOutputStream();

    private String                  peerHost;
    private boolean                 autoClose;
    private boolean                 isConnected = false;
    private boolean                 tlsIsClosed = false;

    /*
     * Is the local name service trustworthy?
     *
     * If the local name service is not trustworthy, reverse host name
     * resolution should not be performed for endpoint identification.
     */
    private static final boolean trustNameService =
            Utilities.getBooleanProperty("jdk.tls.trustNameService", false);

    /**
     * Package-private constructor used to instantiate an unconnected
     * socket.
     *
     * This instance is meant to set handshake state to use "client mode".
     */
    SSLSocketImpl(SSLContextImpl sslContext) {
        super();
        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContext, this,
                new SSLSocketInputRecord(handshakeHash),
                new SSLSocketOutputRecord(handshakeHash), true);
    }

    /**
     * Package-private constructor used to instantiate a server socket.
     *
     * This instance is meant to set handshake state to use "server mode".
     */
    SSLSocketImpl(SSLContextImpl sslContext, SSLConfiguration sslConfig) {
        super();
        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContext, this, sslConfig,
                new SSLSocketInputRecord(handshakeHash),
                new SSLSocketOutputRecord(handshakeHash));
    }

    /**
     * Constructs an SSL connection to a named host at a specified
     * port, using the authentication context provided.
     *
     * This endpoint acts as the client, and may rejoin an existing SSL session
     * if appropriate.
     */
    SSLSocketImpl(SSLContextImpl sslContext, String peerHost,
            int peerPort) throws IOException, UnknownHostException {
        super();
        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContext, this,
                new SSLSocketInputRecord(handshakeHash),
                new SSLSocketOutputRecord(handshakeHash), true);
        this.peerHost = peerHost;
        SocketAddress socketAddress =
               peerHost != null ? new InetSocketAddress(peerHost, peerPort) :
               new InetSocketAddress(InetAddress.getByName(null), peerPort);
        connect(socketAddress, 0);
    }

    /**
     * Constructs an SSL connection to a server at a specified
     * address, and TCP port, using the authentication context
     * provided.
     *
     * This endpoint acts as the client, and may rejoin an existing SSL
     * session if appropriate.
     */
    SSLSocketImpl(SSLContextImpl sslContext,
            InetAddress address, int peerPort) throws IOException {
        super();
        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContext, this,
                new SSLSocketInputRecord(handshakeHash),
                new SSLSocketOutputRecord(handshakeHash), true);

        SocketAddress socketAddress = new InetSocketAddress(address, peerPort);
        connect(socketAddress, 0);
    }

    /**
     * Constructs an SSL connection to a named host at a specified
     * port, using the authentication context provided.
     *
     * This endpoint acts as the client, and may rejoin an existing SSL
     * session if appropriate.
     */
    SSLSocketImpl(SSLContextImpl sslContext,
            String peerHost, int peerPort, InetAddress localAddr,
            int localPort) throws IOException, UnknownHostException {
        super();
        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContext, this,
                new SSLSocketInputRecord(handshakeHash),
                new SSLSocketOutputRecord(handshakeHash), true);
        this.peerHost = peerHost;

        bind(new InetSocketAddress(localAddr, localPort));
        SocketAddress socketAddress =
               peerHost != null ? new InetSocketAddress(peerHost, peerPort) :
               new InetSocketAddress(InetAddress.getByName(null), peerPort);
        connect(socketAddress, 0);
    }

    /**
     * Constructs an SSL connection to a server at a specified
     * address, and TCP port, using the authentication context
     * provided.
     *
     * This endpoint acts as the client, and may rejoin an existing SSL
     * session if appropriate.
     */
    SSLSocketImpl(SSLContextImpl sslContext,
            InetAddress peerAddr, int peerPort,
            InetAddress localAddr, int localPort) throws IOException {
        super();
        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContext, this,
                new SSLSocketInputRecord(handshakeHash),
                new SSLSocketOutputRecord(handshakeHash), true);

        bind(new InetSocketAddress(localAddr, localPort));
        SocketAddress socketAddress = new InetSocketAddress(peerAddr, peerPort);
        connect(socketAddress, 0);
    }

    /**
     * Creates a server mode {@link Socket} layered over an
     * existing connected socket, and is able to read data which has
     * already been consumed/removed from the {@link Socket}'s
     * underlying {@link InputStream}.
     */
    SSLSocketImpl(SSLContextImpl sslContext, Socket sock,
            InputStream consumed, boolean autoClose) throws IOException {
        super(sock, consumed);
        // We always layer over a connected socket
        if (!sock.isConnected()) {
            throw new SocketException("Underlying socket is not connected");
        }

        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContext, this,
                new SSLSocketInputRecord(handshakeHash),
                new SSLSocketOutputRecord(handshakeHash), false);
        this.autoClose = autoClose;
        doneConnect();
    }

    /**
     * Layer SSL traffic over an existing connection, rather than
     * creating a new connection.
     *
     * The existing connection may be used only for SSL traffic (using this
     * SSLSocket) until the SSLSocket.close() call returns. However, if a
     * protocol error is detected, that existing connection is automatically
     * closed.
     * <p>
     * This particular constructor always uses the socket in the
     * role of an SSL client. It may be useful in cases which start
     * using SSL after some initial data transfers, for example in some
     * SSL tunneling applications or as part of some kinds of application
     * protocols which negotiate use of a SSL based security.
     */
    SSLSocketImpl(SSLContextImpl sslContext, Socket sock,
            String peerHost, int port, boolean autoClose) throws IOException {
        super(sock);
        // We always layer over a connected socket
        if (!sock.isConnected()) {
            throw new SocketException("Underlying socket is not connected");
        }

        this.sslContext = sslContext;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContext, this,
                new SSLSocketInputRecord(handshakeHash),
                new SSLSocketOutputRecord(handshakeHash), true);
        this.peerHost = peerHost;
        this.autoClose = autoClose;
        doneConnect();
    }

    @Override
    public void connect(SocketAddress endpoint,
            int timeout) throws IOException {

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

    @Override
    public String[] getSupportedCipherSuites() {
        return CipherSuite.namesOf(sslContext.getSupportedCipherSuites());
    }

    @Override
    public synchronized String[] getEnabledCipherSuites() {
        return CipherSuite.namesOf(conContext.sslConfig.enabledCipherSuites);
    }

    @Override
    public synchronized void setEnabledCipherSuites(String[] suites) {
        conContext.sslConfig.enabledCipherSuites =
                CipherSuite.validValuesOf(suites);
    }

    @Override
    public String[] getSupportedProtocols() {
        return ProtocolVersion.toStringArray(
                sslContext.getSupportedProtocolVersions());
    }

    @Override
    public synchronized String[] getEnabledProtocols() {
        return ProtocolVersion.toStringArray(
                conContext.sslConfig.enabledProtocols);
    }

    @Override
    public synchronized void setEnabledProtocols(String[] protocols) {
        if (protocols == null) {
            throw new IllegalArgumentException("Protocols cannot be null");
        }

        conContext.sslConfig.enabledProtocols =
                ProtocolVersion.namesOf(protocols);
    }

    @Override
    public synchronized SSLSession getSession() {
        try {
            // start handshaking, if failed, the connection will be closed.
            ensureNegotiated();
        } catch (IOException ioe) {
            if (SSLLogger.isOn && SSLLogger.isOn("handshake")) {
                SSLLogger.severe("handshake failed", ioe);
            }

            return SSLSessionImpl.nullSession;
        }

        return conContext.conSession;
    }

    @Override
    public synchronized SSLSession getHandshakeSession() {
        if (conContext.handshakeContext != null) {
            return conContext.handshakeContext.handshakeSession;
        }

        return null;
    }

    @Override
    public synchronized void addHandshakeCompletedListener(
            HandshakeCompletedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        conContext.sslConfig.addHandshakeCompletedListener(listener);
    }

    @Override
    public synchronized void removeHandshakeCompletedListener(
            HandshakeCompletedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        conContext.sslConfig.removeHandshakeCompletedListener(listener);
    }

    @Override
    public synchronized void startHandshake() throws IOException {
        checkWrite();
        try {
            conContext.kickstart();

            // All initial handshaking goes through this operation until we
            // have a valid SSL connection.
            //
            // Handle handshake messages only, need no application data.
            if (!conContext.isNegotiated) {
                readRecord();
            }
        } catch (IOException ioe) {
            conContext.fatal(Alert.HANDSHAKE_FAILURE,
                "Couldn't kickstart handshaking", ioe);
        } catch (Exception oe) {    // including RuntimeException
            handleException(oe);
        }
    }

    @Override
    public synchronized void setUseClientMode(boolean mode) {
        conContext.setUseClientMode(mode);
    }

    @Override
    public synchronized boolean getUseClientMode() {
        return conContext.sslConfig.isClientMode;
    }

    @Override
    public synchronized void setNeedClientAuth(boolean need) {
        conContext.sslConfig.clientAuthType =
                (need ? ClientAuthType.CLIENT_AUTH_REQUIRED :
                        ClientAuthType.CLIENT_AUTH_NONE);
    }

    @Override
    public synchronized boolean getNeedClientAuth() {
        return (conContext.sslConfig.clientAuthType ==
                        ClientAuthType.CLIENT_AUTH_REQUIRED);
    }

    @Override
    public synchronized void setWantClientAuth(boolean want) {
        conContext.sslConfig.clientAuthType =
                (want ? ClientAuthType.CLIENT_AUTH_REQUESTED :
                        ClientAuthType.CLIENT_AUTH_NONE);
    }

    @Override
    public synchronized boolean getWantClientAuth() {
        return (conContext.sslConfig.clientAuthType ==
                        ClientAuthType.CLIENT_AUTH_REQUESTED);
    }

    @Override
    public synchronized void setEnableSessionCreation(boolean flag) {
        conContext.sslConfig.enableSessionCreation = flag;
    }

    @Override
    public synchronized boolean getEnableSessionCreation() {
        return conContext.sslConfig.enableSessionCreation;
    }

    @Override
    public synchronized boolean isClosed() {
        return tlsIsClosed && conContext.isClosed();
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            conContext.close();
        } catch (IOException ioe) {
            // ignore the exception
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.warning("connection context closure failed", ioe);
            }
        } finally {
            tlsIsClosed = true;
        }
    }

    @Override
    public synchronized InputStream getInputStream() throws IOException {
        if (isClosed() || conContext.isInboundDone()) {
            throw new SocketException("Socket or inbound is closed");
        }

        if (!isConnected) {
            throw new SocketException("Socket is not connected");
        }

        return appInput;
    }

    private synchronized void ensureNegotiated() throws IOException {
        if (conContext.isNegotiated ||
                conContext.isClosed() || conContext.isBroken) {
            return;
        }

        startHandshake();
    }

    /**
     * InputStream for application data as returned by
     * SSLSocket.getInputStream().
     */
    private class AppInputStream extends InputStream {
        // One element array used to implement the single byte read() method
        private final byte[] oneByte = new byte[1];

        // the temporary buffer used to read network
        private ByteBuffer buffer;

        // Is application data available in the stream?
        private boolean appDataIsAvailable;

        AppInputStream() {
            this.appDataIsAvailable = false;
            this.buffer = ByteBuffer.allocate(4096);
        }

        /**
         * Return the minimum number of bytes that can be read
         * without blocking.
         */
        @Override
        public int available() throws IOException {
            // Currently not synchronized.
            if ((!appDataIsAvailable) || checkEOF()) {
                return 0;
            }

            return buffer.remaining();
        }

        /**
         * Read a single byte, returning -1 on non-fault EOF status.
         */
        @Override
        public synchronized int read() throws IOException {
            int n = read(oneByte, 0, 1);
            if (n <= 0) {   // EOF
                return -1;
            }

            return oneByte[0] & 0xFF;
        }

        /**
         * Reads up to {@code len} bytes of data from the input stream
         * into an array of bytes.
         *
         * An attempt is made to read as many as {@code len} bytes, but a
         * smaller number may be read. The number of bytes actually read
         * is returned as an integer.
         *
         * If the layer above needs more data, it asks for more, so we
         * are responsible only for blocking to fill at most one buffer,
         * and returning "-1" on non-fault EOF status.
         */
        @Override
        public synchronized int read(byte[] b, int off, int len)
                throws IOException {
            if (b == null) {
                throw new NullPointerException("the target buffer is null");
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException(
                        "buffer length: " + b.length + ", offset; " + off +
                        ", bytes to read:" + len);
            } else if (len == 0) {
                return 0;
            }

            if (checkEOF()) {
                return -1;
            }

            // start handshaking if the connection has not been negotiated.
            if (!conContext.isNegotiated &&
                    !conContext.isClosed() && !conContext.isBroken) {
                ensureNegotiated();
            }

            // Read the available bytes at first.
            int remains = available();
            if (remains > 0) {
                int howmany = Math.min(remains, len);
                buffer.get(b, off, howmany);

                return howmany;
            }

            appDataIsAvailable = false;
            int volume = 0;
            try {
                /*
                 * Read data if needed ... notice that the connection
                 * guarantees that handshake, alert, and change cipher spec
                 * data streams are handled as they arrive, so we never
                 * see them here.
                 */
                while (volume == 0) {
                    // Clear the buffer for a new record reading.
                    buffer.clear();

                    // grow the buffer if needed
                    int inLen = conContext.inputRecord.bytesInCompletePacket();
                    if (inLen < 0) {    // EOF
                        handleEOF(null);

                        // if no exception thrown
                        return -1;
                    }

                    // Is this packet bigger than SSL/TLS normally allows?
                    if (inLen > SSLRecord.maxLargeRecordSize) {
                        throw new SSLProtocolException(
                                "Illegal packet size: " + inLen);
                    }

                    if (inLen > buffer.remaining()) {
                        buffer = ByteBuffer.allocate(inLen);
                    }

                    volume = readRecord(buffer);
                    buffer.flip();
                    if (volume < 0) {   // EOF
                        // treat like receiving a close_notify warning message.
                        conContext.isInputCloseNotified = true;
                        conContext.closeInbound();
                        return -1;
                    } else if (volume > 0) {
                        appDataIsAvailable = true;
                        break;
                    }
                }

                // file the destination buffer
                int howmany = Math.min(len, volume);
                buffer.get(b, off, howmany);
                return howmany;
            } catch (Exception e) {   // including RuntimeException
                // shutdown and rethrow (wrapped) exception as appropriate
                handleException(e);

                // dummy for compiler
                return -1;
            }
        }

        /**
         * Skip n bytes.
         *
         * This implementation is somewhat less efficient than possible, but
         * not badly so (redundant copy).  We reuse the read() code to keep
         * things simpler. Note that SKIP_ARRAY is static and may garbled by
         * concurrent use, but we are not interested in the data anyway.
         */
        @Override
        public synchronized long skip(long n) throws IOException {
            // dummy array used to implement skip()
            byte[] skipArray = new byte[256];

            long skipped = 0;
            while (n > 0) {
                int len = (int)Math.min(n, skipArray.length);
                int r = read(skipArray, 0, len);
                if (r <= 0) {
                    break;
                }
                n -= r;
                skipped += r;
            }

            return skipped;
        }

        @Override
        public void close() throws IOException {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.finest("Closing input stream");
            }

            conContext.closeInbound();
        }
    }

    @Override
    public synchronized OutputStream getOutputStream() throws IOException {
        if (isClosed() || conContext.isOutboundDone()) {
            throw new SocketException("Socket or outbound is closed");
        }

        if (!isConnected) {
            throw new SocketException("Socket is not connected");
        }

        return appOutput;
    }


    /**
     * OutputStream for application data as returned by
     * SSLSocket.getOutputStream().
     */
    private class AppOutputStream extends OutputStream {
        // One element array used to implement the write(byte) method
        private final byte[] oneByte = new byte[1];

        @Override
        public void write(int i) throws IOException {
            oneByte[0] = (byte)i;
            write(oneByte, 0, 1);
        }

        @Override
        public synchronized void write(byte[] b,
                int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException("the source buffer is null");
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException(
                        "buffer length: " + b.length + ", offset; " + off +
                        ", bytes to read:" + len);
            } else if (len == 0) {
                return;
            }

            // start handshaking if the connection has not been negotiated.
            if (!conContext.isNegotiated &&
                    !conContext.isClosed() && !conContext.isBroken) {
                ensureNegotiated();
            }

            // check if the Socket is invalid (error or closed)
            checkWrite();

            // Delegate the writing to the underlying socket.
            try {
                writeRecord(b, off, len);
                checkWrite();
            } catch (IOException ioe) {
                // shutdown and rethrow (wrapped) exception as appropriate
                handleException(ioe);
            }
        }

        @Override
        public void close() throws IOException {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.finest("Closing output stream");
            }

            conContext.closeOutbound();
        }
    }

    @Override
    public synchronized SSLParameters getSSLParameters() {
        return conContext.sslConfig.getSSLParameters();
    }

    @Override
    public synchronized void setSSLParameters(SSLParameters params) {
        conContext.sslConfig.setSSLParameters(params);

        if (conContext.sslConfig.maximumPacketSize != 0) {
            conContext.outputRecord.changePacketSize(
                    conContext.sslConfig.maximumPacketSize);
        }
    }

    @Override
    public synchronized String getApplicationProtocol() {
        return conContext.applicationProtocol;
    }

    @Override
    public synchronized String getHandshakeApplicationProtocol() {
        if (conContext.handshakeContext != null) {
            return conContext.handshakeContext.applicationProtocol;
        }

        return null;
    }

    @Override
    public synchronized void setHandshakeApplicationProtocolSelector(
            BiFunction<SSLSocket, List<String>, String> selector) {
        conContext.sslConfig.socketAPSelector = selector;
    }

    @Override
    public synchronized BiFunction<SSLSocket, List<String>, String>
            getHandshakeApplicationProtocolSelector() {
        return conContext.sslConfig.socketAPSelector;
    }

    private synchronized void writeRecord(byte[] source,
            int offset, int length) throws IOException {
        if (conContext.isOutboundDone()) {
            throw new SocketException("Socket or outbound closed");
        }

        //
        // Don't bother to really write empty records.  We went this
        // far to drive the handshake machinery, for correctness; not
        // writing empty records improves performance by cutting CPU
        // time and network resource usage.  However, some protocol
        // implementations are fragile and don't like to see empty
        // records, so this also increases robustness.
        //
        if (length > 0) {
            try {
                conContext.outputRecord.deliver(source, offset, length);
            } catch (SSLHandshakeException she) {
                // may be record sequence number overflow
                conContext.fatal(Alert.HANDSHAKE_FAILURE, she);
            } catch (IOException e) {
                conContext.fatal(Alert.UNEXPECTED_MESSAGE, e);
            }
        }

        // Is the sequence number is nearly overflow?
        if (conContext.outputRecord.seqNumIsHuge()) {
            tryKeyUpdate();
        }
    }

    private synchronized int readRecord() throws IOException {
        while (!conContext.isInboundDone()) {
            try {
                Plaintext plainText = decode(null);
                if ((plainText.contentType == ContentType.HANDSHAKE.id) &&
                        conContext.isNegotiated) {
                    return 0;
                }
            } catch (SSLException ssle) {
                throw ssle;
            } catch (IOException ioe) {
                if (!(ioe instanceof SSLException)) {
                    throw new SSLException("readRecord", ioe);
                } else {
                    throw ioe;
                }
            }
        }

        return -1;
    }

    private synchronized int readRecord(ByteBuffer buffer) throws IOException {
        while (!conContext.isInboundDone()) {
            /*
             * clean the buffer and check if it is too small, e.g. because
             * the AppInputStream did not have the chance to see the
             * current packet length but rather something like that of the
             * handshake before. In that case we return 0 at this point to
             * give the caller the chance to adjust the buffer.
             */
            buffer.clear();
            int inLen = conContext.inputRecord.bytesInCompletePacket();
            if (inLen < 0) {    // EOF
                handleEOF(null);

                // if no exception thrown
                return -1;
            }

            if (buffer.remaining() < inLen) {
                return 0;
            }

            try {
                Plaintext plainText = decode(buffer);
                if (plainText.contentType == ContentType.APPLICATION_DATA.id) {
                    return buffer.position();
                }
            } catch (SSLException ssle) {
                throw ssle;
            } catch (IOException ioe) {
                if (!(ioe instanceof SSLException)) {
                    throw new SSLException("readRecord", ioe);
                } else {
                    throw ioe;
                }
            }
        }

        //
        // couldn't read, due to some kind of error
        //
        return -1;
    }

    private Plaintext decode(ByteBuffer destination) throws IOException {
        Plaintext plainText;
        try {
            if (destination == null) {
                plainText = SSLTransport.decode(conContext,
                        null, 0, 0, null, 0, 0);
            } else {
                plainText = SSLTransport.decode(conContext,
                        null, 0, 0, new ByteBuffer[]{destination}, 0, 1);
            }
        } catch (EOFException eofe) {
            // EOFException is special as it is related to close_notify.
            plainText = handleEOF(eofe);
        }

        // Is the sequence number is nearly overflow?
        if (plainText != Plaintext.PLAINTEXT_NULL &&
                conContext.inputRecord.seqNumIsHuge()) {
            tryKeyUpdate();
        }

        return plainText;
    }

    /**
     * Try renegotiation or key update for sequence number wrap.
     *
     * Note that in order to maintain the handshake status properly, we check
     * the sequence number after the last record reading/writing process.  As
     * we request renegotiation or close the connection for wrapped sequence
     * number when there is enough sequence number space left to handle a few
     * more records, so the sequence number of the last record cannot be
     * wrapped.
     */
    private void tryKeyUpdate() throws IOException {
        // Don't bother to kickstart the renegotiation or key update when the
        // local is asking for it.
        if ((conContext.handshakeContext == null) &&
                !conContext.isClosed() && !conContext.isBroken) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.finest("key update to wrap sequence number");
            }
            conContext.keyUpdate();
        }
    }

    private void closeSocket(boolean selfInitiated) throws IOException {
        if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
            SSLLogger.fine("close the ssl connection " +
                (selfInitiated ? "(initiative)" : "(passive)"));
        }

        if (autoClose || !isLayered()) {
            super.close();
        } else if (selfInitiated) {
            // wait for close_notify alert to clear input stream.
            waitForClose();
        }
    }

   /**
    * Wait for close_notify alert for a graceful closure.
    *
    * [RFC 5246] If the application protocol using TLS provides that any
    * data may be carried over the underlying transport after the TLS
    * connection is closed, the TLS implementation must receive the responding
    * close_notify alert before indicating to the application layer that
    * the TLS connection has ended.  If the application protocol will not
    * transfer any additional data, but will only close the underlying
    * transport connection, then the implementation MAY choose to close the
    * transport without waiting for the responding close_notify.
    */
    private void waitForClose() throws IOException {
        if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
            SSLLogger.fine("wait for close_notify or alert");
        }

        while (!conContext.isInboundDone()) {
            try {
                Plaintext plainText = decode(null);
                // discard and continue
                if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                    SSLLogger.finest(
                        "discard plaintext while waiting for close", plainText);
                }
            } catch (Exception e) {   // including RuntimeException
                handleException(e);
            }
        }
    }

    /**
     * Initialize the handshaker and socket streams.
     *
     * Called by connect, the layered constructor, and SSLServerSocket.
     */
    synchronized void doneConnect() throws IOException {
        // In server mode, it is not necessary to set host and serverNames.
        // Otherwise, would require a reverse DNS lookup to get the hostname.
        if ((peerHost == null) || (peerHost.length() == 0)) {
            boolean useNameService =
                    trustNameService && conContext.sslConfig.isClientMode;
            useImplicitHost(useNameService);
        } else {
            conContext.sslConfig.serverNames =
                    Utilities.addToSNIServerNameList(
                            conContext.sslConfig.serverNames, peerHost);
        }

        InputStream sockInput = super.getInputStream();
        conContext.inputRecord.setReceiverStream(sockInput);

        OutputStream sockOutput = super.getOutputStream();
        conContext.inputRecord.setDeliverStream(sockOutput);
        conContext.outputRecord.setDeliverStream(sockOutput);

        this.isConnected = true;
    }

    private void useImplicitHost(boolean useNameService) {
        // Note: If the local name service is not trustworthy, reverse
        // host name resolution should not be performed for endpoint
        // identification.  Use the application original specified
        // hostname or IP address instead.

        // Get the original hostname via jdk.internal.misc.SharedSecrets
        InetAddress inetAddress = getInetAddress();
        if (inetAddress == null) {      // not connected
            return;
        }

        JavaNetInetAddressAccess jna =
                SharedSecrets.getJavaNetInetAddressAccess();
        String originalHostname = jna.getOriginalHostName(inetAddress);
        if ((originalHostname != null) &&
                (originalHostname.length() != 0)) {

            this.peerHost = originalHostname;
            if (conContext.sslConfig.serverNames.isEmpty() &&
                    !conContext.sslConfig.noSniExtension) {
                conContext.sslConfig.serverNames =
                        Utilities.addToSNIServerNameList(
                                conContext.sslConfig.serverNames, peerHost);
            }

            return;
        }

        // No explicitly specified hostname, no server name indication.
        if (!useNameService) {
            // The local name service is not trustworthy, use IP address.
            this.peerHost = inetAddress.getHostAddress();
        } else {
            // Use the underlying reverse host name resolution service.
            this.peerHost = getInetAddress().getHostName();
        }
    }

    // ONLY used by HttpsClient to setup the URI specified hostname
    //
    // Please NOTE that this method MUST be called before calling to
    // SSLSocket.setSSLParameters(). Otherwise, the {@code host} parameter
    // may override SNIHostName in the customized server name indication.
    public synchronized void setHost(String host) {
        this.peerHost = host;
        this.conContext.sslConfig.serverNames =
                Utilities.addToSNIServerNameList(
                        conContext.sslConfig.serverNames, host);
    }

    /**
     * Return whether we have reached end-of-file.
     *
     * If the socket is not connected, has been shutdown because of an error
     * or has been closed, throw an Exception.
     */
    synchronized boolean checkEOF() throws IOException {
        if (conContext.isClosed()) {
            return true;
        } else if (conContext.isInputCloseNotified || conContext.isBroken) {
            if (conContext.closeReason == null) {
                return true;
            } else {
                throw new SSLException(
                    "Connection has been shutdown: " + conContext.closeReason,
                    conContext.closeReason);
            }
        }

        return false;
    }

    /**
     * Check if we can write data to this socket.
     */
    synchronized void checkWrite() throws IOException {
        if (checkEOF() || conContext.isOutboundClosed()) {
            // we are at EOF, write must throw Exception
            throw new SocketException("Connection closed");
        }
        if (!isConnected) {
            throw new SocketException("Socket is not connected");
        }
    }

    /**
     * Handle an exception.
     *
     * This method is called by top level exception handlers (in read(),
     * write()) to make sure we always shutdown the connection correctly
     * and do not pass runtime exception to the application.
     *
     * This method never returns normally, it always throws an IOException.
     */
    private void handleException(Exception cause) throws IOException {
        if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
            SSLLogger.warning("handling exception", cause);
        }

        // Don't close the Socket in case of timeouts or interrupts.
        if (cause instanceof InterruptedIOException) {
            throw (IOException)cause;
        }

        // need to perform error shutdown
        boolean isSSLException = (cause instanceof SSLException);
        Alert alert;
        if (isSSLException) {
            if (cause instanceof SSLHandshakeException) {
                alert = Alert.HANDSHAKE_FAILURE;
            } else {
                alert = Alert.UNEXPECTED_MESSAGE;
            }
        } else {
            if (cause instanceof IOException) {
                alert = Alert.UNEXPECTED_MESSAGE;
            } else {
                // RuntimeException
                alert = Alert.INTERNAL_ERROR;
            }
        }
        conContext.fatal(alert, cause);
    }

    private Plaintext handleEOF(EOFException eofe) throws IOException {
        if (requireCloseNotify || conContext.handshakeContext != null) {
            SSLException ssle;
            if (conContext.handshakeContext != null) {
                ssle = new SSLHandshakeException(
                        "Remote host terminated the handshake");
            } else {
                ssle = new SSLProtocolException(
                        "Remote host terminated the connection");
            }

            if (eofe != null) {
                ssle.initCause(eofe);
            }
            throw ssle;
        } else {
            // treat as if we had received a close_notify
            conContext.isInputCloseNotified = true;
            conContext.transport.shutdown();

            return Plaintext.PLAINTEXT_NULL;
        }
    }


    @Override
    public String getPeerHost() {
        return peerHost;
    }

    @Override
    public int getPeerPort() {
        return getPort();
    }

    @Override
    public boolean useDelegatedTask() {
        return false;
    }

    @Override
    public void shutdown() throws IOException {
        if (!isClosed()) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.fine("close the underlying socket");
            }

            try {
                if (conContext.isInputCloseNotified) {
                    // Close the connection, no wait for more peer response.
                    closeSocket(false);
                } else {
                    // Close the connection, may wait for peer close_notify.
                    closeSocket(true);
                }
            } finally {
                tlsIsClosed = true;
            }
        }
    }
}
