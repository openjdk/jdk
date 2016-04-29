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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import static javax.net.ssl.SSLEngineResult.Status.*;
import javax.net.ssl.*;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

/**
 * Asynchronous wrapper around SSLEngine. send and receive is fully non
 * blocking. When handshaking is required, a thread is created to perform
 * the handshake and application level sends do not take place during this time.
 *
 * Is implemented using queues and functions operating on the receiving end
 * of each queue.
 *
 * Application writes to:
 *        ||
 *        \/
 *     appOutputQ
 *        ||
 *        \/
 * appOutputQ read by "upperWrite" method which does SSLEngine.wrap
 * and writes to
 *        ||
 *        \/
 *     channelOutputQ
 *        ||
 *        \/
 * channelOutputQ is read by "lowerWrite" method which is invoked from
 * OP_WRITE events on the socket (from selector thread)
 *
 * Reading side is as follows
 * --------------------------
 *
 * "upperRead" method reads off channelInputQ and calls SSLEngine.unwrap and
 * when decrypted data is returned, it is passed to the user's Consumer<ByteBuffer>
 *        /\
 *        ||
 *     channelInputQ
 *        /\
 *        ||
 * "lowerRead" method puts buffers into channelInputQ. It is invoked from
 * OP_READ events from the selector.
 *
 * Whenever handshaking is required, the doHandshaking() method is called
 * which creates a thread to complete the handshake. It takes over the
 * channelInputQ from upperRead, and puts outgoing packets on channelOutputQ.
 * Selector events are delivered to lowerRead and lowerWrite as normal.
 *
 * Errors
 *
 * Any exception thrown by the engine or channel, causes all Queues to be closed
 * the channel to be closed, and the error is reported to the user's
 * Consumer<Throwable>
 */
public class AsyncSSLDelegate implements Closeable, AsyncConnection {

    // outgoing buffers put in this queue first and may remain here
    // while SSL handshaking happening.
    final Queue<ByteBuffer> appOutputQ;

    // queue of wrapped ByteBuffers waiting to be sent on socket channel
    //final Queue<ByteBuffer> channelOutputQ;

    // Bytes read into this queue before being unwrapped. Backup on this
    // Q should only happen when the engine is stalled due to delegated tasks
    final Queue<ByteBuffer> channelInputQ;

    // input occurs through the read() method which is expected to be called
    // when the selector signals some data is waiting to be read. All incoming
    // handshake data is handled in this method, which means some calls to
    // read() may return zero bytes of user data. This is not a sign of spinning,
    // just that the handshake mechanics are being executed.

    final SSLEngine engine;
    final SSLParameters sslParameters;
    //final SocketChannel chan;
    final HttpConnection lowerOutput;
    final HttpClientImpl client;
    final ExecutorService executor;
    final BufferHandler bufPool;
    Consumer<ByteBuffer> receiver;
    Consumer<Throwable> errorHandler;
    // Locks.
    final Object reader = new Object();
    final Object writer = new Object();
    // synchronizing handshake state
    final Object handshaker = new Object();
    // flag set when reader or writer is blocked waiting for handshake to finish
    boolean writerBlocked;
    boolean readerBlocked;

    // some thread is currently doing the handshake
    boolean handshaking;

    // alpn[] may be null. upcall is callback which receives incoming decoded bytes off socket

    AsyncSSLDelegate(HttpConnection lowerOutput, HttpClientImpl client, String[] alpn)
    {
        SSLContext context = client.sslContext();
        executor = client.executorService();
        bufPool = client;
        appOutputQ = new Queue<>();
        appOutputQ.registerPutCallback(this::upperWrite);
        //channelOutputQ = new Queue<>();
        //channelOutputQ.registerPutCallback(this::lowerWrite);
        engine = context.createSSLEngine();
        engine.setUseClientMode(true);
        SSLParameters sslp = client.sslParameters().orElse(null);
        if (sslp == null) {
            sslp = context.getSupportedSSLParameters();
            //sslp = context.getDefaultSSLParameters();
            //printParams(sslp);
        }
        sslParameters = Utils.copySSLParameters(sslp);
        if (alpn != null) {
            sslParameters.setApplicationProtocols(alpn);
            Log.logSSL("Setting application protocols: " + Arrays.toString(alpn));
        } else {
            Log.logSSL("No application protocols proposed");
        }
        engine.setSSLParameters(sslParameters);
        engine.setEnabledCipherSuites(sslp.getCipherSuites());
        engine.setEnabledProtocols(sslp.getProtocols());
        this.lowerOutput = lowerOutput;
        this.client = client;
        this.channelInputQ = new Queue<>();
        this.channelInputQ.registerPutCallback(this::upperRead);
    }

    /**
     * Put buffers to appOutputQ, and call upperWrite() if q was empty.
     *
     * @param src
     */
    public void write(ByteBuffer[] src) throws IOException {
        appOutputQ.putAll(src);
    }

    public void write(ByteBuffer buf) throws IOException {
        ByteBuffer[] a = new ByteBuffer[1];
        a[0] = buf;
        write(a);
    }

    @Override
    public void close() {
        Utils.close(appOutputQ, channelInputQ, lowerOutput);
    }

    /**
     * Attempts to wrap buffers from appOutputQ and place them on the
     * channelOutputQ for writing. If handshaking is happening, then the
     * process stalls and last buffers taken off the appOutputQ are put back
     * into it until handshaking completes.
     *
     * This same method is called to try and resume output after a blocking
     * handshaking operation has completed.
     */
    private void upperWrite() {
        try {
            EngineResult r = null;
            ByteBuffer[] buffers = appOutputQ.pollAll(Utils.EMPTY_BB_ARRAY);
            int bytes = Utils.remaining(buffers);
            while (bytes > 0) {
                synchronized (writer) {
                    r = wrapBuffers(buffers);
                    int bytesProduced = r.bytesProduced();
                    int bytesConsumed = r.bytesConsumed();
                    bytes -= bytesConsumed;
                    if (bytesProduced > 0) {
                        // pass destination buffer to channelOutputQ.
                        lowerOutput.write(r.destBuffer);
                    }
                    synchronized (handshaker) {
                        if (r.handshaking()) {
                            // handshaking is happening or is needed
                            // so we put the buffers back on Q to process again
                            // later. It's possible that some may have already
                            // been processed, which is ok.
                            appOutputQ.pushbackAll(buffers);
                            writerBlocked = true;
                            if (!handshaking()) {
                                // execute the handshake in another thread.
                                // This method will be called again to resume sending
                                // later
                                doHandshake(r);
                            }
                            return;
                        }
                    }
                }
            }
            returnBuffers(buffers);
        } catch (Throwable t) {
            t.printStackTrace();
            close();
        }
    }

    private void doHandshake(EngineResult r) {
        handshaking = true;
        channelInputQ.registerPutCallback(null);
        executor.execute(() -> {
            try {
                doHandshakeImpl(r);
                channelInputQ.registerPutCallback(this::upperRead);
            } catch (Throwable t) {
                t.printStackTrace();
                close();
            }
        });
    }

    private void returnBuffers(ByteBuffer[] bufs) {
        for (ByteBuffer buf : bufs)
            client.returnBuffer(buf);
    }

    /**
     * Return true if some thread is currently doing the handshake
     *
     * @return
     */
    boolean handshaking() {
        synchronized(handshaker) {
            return handshaking;
        }
    }

    /**
     * Executes entire handshake in calling thread.
     * Returns after handshake is completed or error occurs
     * @param r
     * @throws IOException
     */
    private void doHandshakeImpl(EngineResult r) throws IOException {
        while (true) {
            SSLEngineResult.HandshakeStatus status = r.handshakeStatus();
            if (status == NEED_TASK) {
                LinkedList<Runnable> tasks = obtainTasks();
                for (Runnable task : tasks)
                    task.run();
                r = handshakeWrapAndSend();
            } else if (status == NEED_WRAP) {
                r = handshakeWrapAndSend();
            } else if (status == NEED_UNWRAP) {
                r = handshakeReceiveAndUnWrap();
            }
            if (!r.handshaking())
                break;
        }
        boolean dowrite = false;
        boolean doread = false;
        // Handshake is finished. Now resume reading and/or writing
        synchronized(handshaker) {
            handshaking = false;
            if (writerBlocked) {
                writerBlocked = false;
                dowrite = true;
            }
            if (readerBlocked) {
                readerBlocked = false;
                doread = true;
            }
        }
        if (dowrite)
            upperWrite();
        if (doread)
            upperRead();
    }

    // acknowledge a received CLOSE request from peer
    void doClosure() throws IOException {
        //while (!wrapAndSend(emptyArray))
            //;
    }

    LinkedList<Runnable> obtainTasks() {
        LinkedList<Runnable> l = new LinkedList<>();
        Runnable r;
        while ((r = engine.getDelegatedTask()) != null)
            l.add(r);
        return l;
    }

    @Override
    public synchronized void setAsyncCallbacks(Consumer<ByteBuffer> asyncReceiver, Consumer<Throwable> errorReceiver) {
        this.receiver = asyncReceiver;
        this.errorHandler = errorReceiver;
    }

    @Override
    public void startReading() {
        // maybe this class does not need to implement AsyncConnection
    }

    static class EngineResult {
        ByteBuffer destBuffer;
        ByteBuffer srcBuffer;
        SSLEngineResult result;
        Throwable t;

        boolean handshaking() {
            SSLEngineResult.HandshakeStatus s = result.getHandshakeStatus();
            return s != FINISHED && s != NOT_HANDSHAKING;
        }

        int bytesConsumed() {
            return result.bytesConsumed();
        }

        int bytesProduced() {
            return result.bytesProduced();
        }

        Throwable exception() {
            return t;
        }

        SSLEngineResult.HandshakeStatus handshakeStatus() {
            return result.getHandshakeStatus();
        }

        SSLEngineResult.Status status() {
            return result.getStatus();
        }
    }

    EngineResult handshakeWrapAndSend() throws IOException {
        EngineResult r = wrapBuffer(Utils.EMPTY_BYTEBUFFER);
        if (r.bytesProduced() > 0) {
            lowerOutput.write(r.destBuffer);
        }
        return r;
    }

    // called during handshaking. It blocks until a complete packet
    // is available, unwraps it and returns.
    EngineResult handshakeReceiveAndUnWrap() throws IOException {
        ByteBuffer buf = channelInputQ.take();
        while (true) {
            // block waiting for input
            EngineResult r = unwrapBuffer(buf);
            SSLEngineResult.Status status = r.status();
            if (status == BUFFER_UNDERFLOW) {
                // wait for another buffer to arrive
                ByteBuffer buf1 = channelInputQ.take();
                buf = combine (buf, buf1);
                continue;
            }
            // OK
            // theoretically possible we could receive some user data
            if (r.bytesProduced() > 0) {
                receiver.accept(r.destBuffer);
            }
            if (!buf.hasRemaining())
                return r;
        }
    }

    EngineResult wrapBuffer(ByteBuffer src) throws SSLException {
        ByteBuffer[] bufs = new ByteBuffer[1];
        bufs[0] = src;
        return wrapBuffers(bufs);
    }

    EngineResult wrapBuffers(ByteBuffer[] src) throws SSLException {
        EngineResult r = new EngineResult();
        ByteBuffer dst = bufPool.getBuffer();
        while (true) {
            r.result = engine.wrap(src, dst);
            switch (r.result.getStatus()) {
                case BUFFER_OVERFLOW:
                    dst = getPacketBuffer();
                    break;
                case CLOSED:
                case OK:
                    dst.flip();
                    r.destBuffer = dst;
                    return r;
                case BUFFER_UNDERFLOW:
                    // underflow handled externally
                    bufPool.returnBuffer(dst);
                    return r;
                default:
                    assert false;
            }
        }
    }

    EngineResult unwrapBuffer(ByteBuffer srcbuf) throws IOException {
        EngineResult r = new EngineResult();
        r.srcBuffer = srcbuf;

        ByteBuffer dst = bufPool.getBuffer();
        while (true) {
            r.result = engine.unwrap(srcbuf, dst);
            switch (r.result.getStatus()) {
                case BUFFER_OVERFLOW:
                    // dest buffer not big enough. Reallocate
                    int oldcap = dst.capacity();
                    dst = getApplicationBuffer();
                    assert dst.capacity() > oldcap;
                    break;
                case CLOSED:
                    doClosure();
                    throw new IOException("Engine closed");
                case BUFFER_UNDERFLOW:
                    bufPool.returnBuffer(dst);
                    return r;
                case OK:
                    dst.flip();
                    r.destBuffer = dst;
                    return r;
            }
        }
    }

    /**
     * Asynchronous read input. Call this when selector fires.
     * Unwrap done in upperRead because it also happens in
     * doHandshake() when handshake taking place
     */
    public void lowerRead(ByteBuffer buffer) {
        try {
            channelInputQ.put(buffer);
        } catch (Throwable t) {
            close();
            errorHandler.accept(t);
        }
    }

    public void upperRead() {
        EngineResult r;
        ByteBuffer srcbuf;
        synchronized (reader) {
            try {
                srcbuf = channelInputQ.poll();
                if (srcbuf == null) {
                    return;
                }
                while (true) {
                    r = unwrapBuffer(srcbuf);
                    switch (r.result.getStatus()) {
                        case BUFFER_UNDERFLOW:
                            // Buffer too small. Need to combine with next buf
                            ByteBuffer nextBuf = channelInputQ.poll();
                            if (nextBuf == null) {
                                // no data available. push buffer back until more data available
                                channelInputQ.pushback(srcbuf);
                                return;
                            } else {
                                srcbuf = combine(srcbuf, nextBuf);
                            }
                            break;
                        case OK:
                            // check for any handshaking work
                            synchronized (handshaker) {
                                if (r.handshaking()) {
                                    // handshaking is happening or is needed
                                    // so we put the buffer back on Q to process again
                                    // later.
                                    channelInputQ.pushback(srcbuf);
                                    readerBlocked = true;
                                    if (!handshaking()) {
                                        // execute the handshake in another thread.
                                        // This method will be called again to resume sending
                                        // later
                                        doHandshake(r);
                                    }
                                    return;
                                }
                            }
                            ByteBuffer dst = r.destBuffer;
                            if (dst.hasRemaining()) {
                                receiver.accept(dst);
                            }
                    }
                    if (srcbuf.hasRemaining()) {
                        continue;
                    }
                    srcbuf = channelInputQ.poll();
                    if (srcbuf == null) {
                        return;
                    }
                }
            } catch (Throwable t) {
                Utils.close(lowerOutput);
                errorHandler.accept(t);
            }
        }
    }

    /**
     * Get a new buffer that is the right size for application buffers.
     *
     * @return
     */
    ByteBuffer getApplicationBuffer() {
        SSLSession session = engine.getSession();
        int appBufsize = session.getApplicationBufferSize();
        bufPool.setMinBufferSize(appBufsize);
        return bufPool.getBuffer(appBufsize);
    }

    ByteBuffer getPacketBuffer() {
        SSLSession session = engine.getSession();
        int packetBufSize = session.getPacketBufferSize();
        bufPool.setMinBufferSize(packetBufSize);
        return bufPool.getBuffer(packetBufSize);
    }

    ByteBuffer combine(ByteBuffer buf1, ByteBuffer buf2) {
        int avail1 = buf1.capacity() - buf1.remaining();
        if (buf2.remaining() < avail1) {
            buf1.compact();
            buf1.put(buf2);
            buf1.flip();
            return buf1;
        }
        int newsize = buf1.remaining() + buf2.remaining();
        ByteBuffer newbuf = bufPool.getBuffer(newsize);
        newbuf.put(buf1);
        newbuf.put(buf2);
        newbuf.flip();
        return newbuf;
    }

    SSLParameters getSSLParameters() {
        return sslParameters;
    }

    static void printParams(SSLParameters p) {
        System.out.println("SSLParameters:");
        if (p == null) {
            System.out.println("Null params");
            return;
        }
        for (String cipher : p.getCipherSuites()) {
                System.out.printf("cipher: %s\n", cipher);
        }
        for (String approto : p.getApplicationProtocols()) {
                System.out.printf("application protocol: %s\n", approto);
        }
        for (String protocol : p.getProtocols()) {
                System.out.printf("protocol: %s\n", protocol);
        }
        if (p.getServerNames() != null)
        for (SNIServerName sname : p.getServerNames()) {
                System.out.printf("server name: %s\n", sname.toString());
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
