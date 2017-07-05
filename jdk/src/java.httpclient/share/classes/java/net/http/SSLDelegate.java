/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */
package java.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

/**
 * Implements the mechanics of SSL by managing an SSLEngine object.
 * One of these is associated with each SSLConnection.
 */
class SSLDelegate {

    final SSLEngine engine;
    final EngineWrapper wrapper;
    final Lock handshaking = new ReentrantLock();
    final SSLParameters sslParameters;
    final SocketChannel chan;
    final HttpClientImpl client;

    // alpn[] may be null
    SSLDelegate(SocketChannel chan, HttpClientImpl client, String[] alpn)
        throws IOException
    {
        SSLContext context = client.sslContext();
        engine = context.createSSLEngine();
        engine.setUseClientMode(true);
        SSLParameters sslp = client.sslParameters().orElse(null);
        if (sslp == null) {
            sslp = context.getDefaultSSLParameters();
        }
        sslParameters = Utils.copySSLParameters(sslp);
        if (alpn != null) {
            sslParameters.setApplicationProtocols(alpn);
            Log.logSSL("Setting application protocols: " + Arrays.toString(alpn));
        } else {
            Log.logSSL("Warning no application protocols proposed!");
        }
        engine.setSSLParameters(sslParameters);
        wrapper = new EngineWrapper(chan, engine);
        this.chan = chan;
        this.client = client;
    }

    SSLParameters getSSLParameters() {
        return sslParameters;
    }

    private static long countBytes(ByteBuffer[] buffers, int start, int number) {
        long c = 0;
        for (int i=0; i<number; i++) {
            c+= buffers[start+i].remaining();
        }
        return c;
    }


    static class WrapperResult {
        static WrapperResult createOK() {
            WrapperResult r = new WrapperResult();
            r.buf = null;
            r.result = new SSLEngineResult(Status.OK, NOT_HANDSHAKING, 0, 0);
            return r;
        }
        SSLEngineResult result;

        /* if passed in buffer was not big enough then the a reallocated buffer
         * is returned here  */
        ByteBuffer buf;
    }

    int app_buf_size;
    int packet_buf_size;

    enum BufType {
        PACKET,
        APPLICATION
    };

    ByteBuffer allocate (BufType type) {
        return allocate (type, -1);
    }

    // TODO: Use buffer pool for this
    ByteBuffer allocate (BufType type, int len) {
        assert engine != null;
        synchronized (this) {
            int size;
            if (type == BufType.PACKET) {
                if (packet_buf_size == 0) {
                    SSLSession sess = engine.getSession();
                    packet_buf_size = sess.getPacketBufferSize();
                }
                if (len > packet_buf_size) {
                    packet_buf_size = len;
                }
                size = packet_buf_size;
            } else {
                if (app_buf_size == 0) {
                    SSLSession sess = engine.getSession();
                    app_buf_size = sess.getApplicationBufferSize();
                }
                if (len > app_buf_size) {
                    app_buf_size = len;
                }
                size = app_buf_size;
            }
            return ByteBuffer.allocate (size);
        }
    }

    /* reallocates the buffer by :-
     * 1. creating a new buffer double the size of the old one
     * 2. putting the contents of the old buffer into the new one
     * 3. set xx_buf_size to the new size if it was smaller than new size
     *
     * flip is set to true if the old buffer needs to be flipped
     * before it is copied.
     */
    private ByteBuffer realloc (ByteBuffer b, boolean flip, BufType type) {
        synchronized (this) {
            int nsize = 2 * b.capacity();
            ByteBuffer n = allocate (type, nsize);
            if (flip) {
                b.flip();
            }
            n.put(b);
            b = n;
        }
        return b;
    }

    /**
     * This is a thin wrapper over SSLEngine and the SocketChannel, which
     * guarantees the ordering of wraps/unwraps with respect to the underlying
     * channel read/writes. It handles the UNDER/OVERFLOW status codes
     * It does not handle the handshaking status codes, or the CLOSED status code
     * though once the engine is closed, any attempt to read/write to it
     * will get an exception.  The overall result is returned.
     * It functions synchronously/blocking
     */
    class EngineWrapper {

        SocketChannel chan;
        SSLEngine engine;
        Object wrapLock, unwrapLock;
        ByteBuffer unwrap_src, wrap_dst;
        boolean closed = false;
        int u_remaining; // the number of bytes left in unwrap_src after an unwrap()

        EngineWrapper (SocketChannel chan, SSLEngine engine) throws IOException {
            this.chan = chan;
            this.engine = engine;
            wrapLock = new Object();
            unwrapLock = new Object();
            unwrap_src = allocate(BufType.PACKET);
            wrap_dst = allocate(BufType.PACKET);
        }

        void close () throws IOException {
        }

        WrapperResult wrapAndSend(ByteBuffer src, boolean ignoreClose)
            throws IOException
        {
            ByteBuffer[] buffers = new ByteBuffer[1];
            buffers[0] = src;
            return wrapAndSend(buffers, 0, 1, ignoreClose);
        }

        /* try to wrap and send the data in src. Handles OVERFLOW.
         * Might block if there is an outbound blockage or if another
         * thread is calling wrap(). Also, might not send any data
         * if an unwrap is needed.
         */
        WrapperResult wrapAndSend(ByteBuffer[] src,
                                  int offset,
                                  int len,
                                  boolean ignoreClose)
            throws IOException
        {
            if (closed && !ignoreClose) {
                throw new IOException ("Engine is closed");
            }
            Status status;
            WrapperResult r = new WrapperResult();
            synchronized (wrapLock) {
                wrap_dst.clear();
                do {
                    r.result = engine.wrap (src, offset, len, wrap_dst);
                    status = r.result.getStatus();
                    if (status == Status.BUFFER_OVERFLOW) {
                        wrap_dst = realloc (wrap_dst, true, BufType.PACKET);
                    }
                } while (status == Status.BUFFER_OVERFLOW);
                if (status == Status.CLOSED && !ignoreClose) {
                    closed = true;
                    return r;
                }
                if (r.result.bytesProduced() > 0) {
                    wrap_dst.flip();
                    int l = wrap_dst.remaining();
                    assert l == r.result.bytesProduced();
                    while (l>0) {
                        l -= chan.write (wrap_dst);
                    }
                }
            }
            return r;
        }

        /* block until a complete message is available and return it
         * in dst, together with the Result. dst may have been re-allocated
         * so caller should check the returned value in Result
         * If handshaking is in progress then, possibly no data is returned
         */
        WrapperResult recvAndUnwrap(ByteBuffer dst) throws IOException {
            Status status;
            WrapperResult r = new WrapperResult();
            r.buf = dst;
            if (closed) {
                throw new IOException ("Engine is closed");
            }
            boolean needData;
            if (u_remaining > 0) {
                unwrap_src.compact();
                unwrap_src.flip();
                needData = false;
            } else {
                unwrap_src.clear();
                needData = true;
            }
            synchronized (unwrapLock) {
                int x;
                do {
                    if (needData) {
                        do {
                        x = chan.read (unwrap_src);
                        } while (x == 0);
                        if (x == -1) {
                            throw new IOException ("connection closed for reading");
                        }
                        unwrap_src.flip();
                    }
                    r.result = engine.unwrap (unwrap_src, r.buf);
                    status = r.result.getStatus();
                    if (status == Status.BUFFER_UNDERFLOW) {
                        if (unwrap_src.limit() == unwrap_src.capacity()) {
                            /* buffer not big enough */
                            unwrap_src = realloc (
                                unwrap_src, false, BufType.PACKET
                            );
                        } else {
                            /* Buffer not full, just need to read more
                             * data off the channel. Reset pointers
                             * for reading off SocketChannel
                             */
                            unwrap_src.position (unwrap_src.limit());
                            unwrap_src.limit (unwrap_src.capacity());
                        }
                        needData = true;
                    } else if (status == Status.BUFFER_OVERFLOW) {
                        r.buf = realloc (r.buf, true, BufType.APPLICATION);
                        needData = false;
                    } else if (status == Status.CLOSED) {
                        closed = true;
                        r.buf.flip();
                        return r;
                    }
                } while (status != Status.OK);
            }
            u_remaining = unwrap_src.remaining();
            return r;
        }
    }

    WrapperResult sendData (ByteBuffer src) throws IOException {
        ByteBuffer[] buffers = new ByteBuffer[1];
        buffers[0] = src;
        return sendData(buffers, 0, 1);
    }

    /**
     * send the data in the given ByteBuffer. If a handshake is needed
     * then this is handled within this method. When this call returns,
     * all of the given user data has been sent and any handshake has been
     * completed. Caller should check if engine has been closed.
     */
    WrapperResult sendData (ByteBuffer[] src, int offset, int len) throws IOException {
        WrapperResult r = WrapperResult.createOK();
        while (countBytes(src, offset, len) > 0) {
            r = wrapper.wrapAndSend(src, offset, len, false);
            Status status = r.result.getStatus();
            if (status == Status.CLOSED) {
                doClosure ();
                return r;
            }
            HandshakeStatus hs_status = r.result.getHandshakeStatus();
            if (hs_status != HandshakeStatus.FINISHED &&
                hs_status != HandshakeStatus.NOT_HANDSHAKING)
            {
                doHandshake(hs_status);
            }
        }
        return r;
    }

    /**
     * read data thru the engine into the given ByteBuffer. If the
     * given buffer was not large enough, a new one is allocated
     * and returned. This call handles handshaking automatically.
     * Caller should check if engine has been closed.
     */
    WrapperResult recvData (ByteBuffer dst) throws IOException {
        /* we wait until some user data arrives */
        int mark = dst.position();
        WrapperResult r = null;
        assert dst.position() == 0;
        while (dst.position() == 0) {
            r = wrapper.recvAndUnwrap (dst);
            dst = (r.buf != dst) ? r.buf: dst;
            Status status = r.result.getStatus();
            if (status == Status.CLOSED) {
                doClosure ();
                return r;
            }

            HandshakeStatus hs_status = r.result.getHandshakeStatus();
            if (hs_status != HandshakeStatus.FINISHED &&
                hs_status != HandshakeStatus.NOT_HANDSHAKING)
            {
                doHandshake (hs_status);
            }
        }
        Utils.flipToMark(dst, mark);
        return r;
    }

    /* we've received a close notify. Need to call wrap to send
     * the response
     */
    void doClosure () throws IOException {
        try {
            handshaking.lock();
            ByteBuffer tmp = allocate(BufType.APPLICATION);
            WrapperResult r;
            do {
                tmp.clear();
                tmp.flip ();
                r = wrapper.wrapAndSend(tmp, true);
            } while (r.result.getStatus() != Status.CLOSED);
        } finally {
            handshaking.unlock();
        }
    }

    /* do the (complete) handshake after acquiring the handshake lock.
     * If two threads call this at the same time, then we depend
     * on the wrapper methods being idempotent. eg. if wrapAndSend()
     * is called with no data to send then there must be no problem
     */
    @SuppressWarnings("fallthrough")
    void doHandshake (HandshakeStatus hs_status) throws IOException {
        boolean wasBlocking = false;
        try {
            wasBlocking = chan.isBlocking();
            handshaking.lock();
            chan.configureBlocking(true);
            ByteBuffer tmp = allocate(BufType.APPLICATION);
            while (hs_status != HandshakeStatus.FINISHED &&
                   hs_status != HandshakeStatus.NOT_HANDSHAKING)
            {
                WrapperResult r = null;
                switch (hs_status) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            /* run in current thread, because we are already
                             * running an external Executor
                             */
                            task.run();
                        }
                        /* fall thru - call wrap again */
                    case NEED_WRAP:
                        tmp.clear();
                        tmp.flip();
                        r = wrapper.wrapAndSend(tmp, false);
                        break;

                    case NEED_UNWRAP:
                        tmp.clear();
                        r = wrapper.recvAndUnwrap (tmp);
                        if (r.buf != tmp) {
                            tmp = r.buf;
                        }
                        assert tmp.position() == 0;
                        break;
                }
                hs_status = r.result.getHandshakeStatus();
            }
            Log.logSSL(getSessionInfo());
            if (!wasBlocking) {
                chan.configureBlocking(false);
            }
        } finally {
            handshaking.unlock();
        }
    }

    String getSessionInfo() {
        StringBuilder sb = new StringBuilder();
        String application = engine.getApplicationProtocol();
        SSLSession sess = engine.getSession();
        String cipher = sess.getCipherSuite();
        String protocol = sess.getProtocol();
        sb.append("Handshake complete alpn: ")
                .append(application)
                .append(", Cipher: ")
                .append(cipher)
                .append(", Protocol: ")
                .append(protocol);
        return sb.toString();
    }
}
