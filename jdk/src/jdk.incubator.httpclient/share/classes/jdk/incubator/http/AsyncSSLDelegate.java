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
 * questions.
 */

package jdk.incubator.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static javax.net.ssl.SSLEngineResult.Status.*;
import javax.net.ssl.*;

import jdk.incubator.http.internal.common.AsyncWriteQueue;
import jdk.incubator.http.internal.common.ByteBufferPool;
import jdk.incubator.http.internal.common.ByteBufferReference;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.Queue;
import jdk.incubator.http.internal.common.Utils;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import jdk.incubator.http.internal.common.ExceptionallyCloseable;

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
 * and does async write to PlainHttpConnection
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
 * "asyncReceive" method puts buffers into channelInputQ. It is invoked from
 * OP_READ events from the selector.
 *
 * Whenever handshaking is required, the doHandshaking() method is called
 * which creates a thread to complete the handshake. It takes over the
 * channelInputQ from upperRead, and puts outgoing packets on channelOutputQ.
 * Selector events are delivered to asyncReceive and lowerWrite as normal.
 *
 * Errors
 *
 * Any exception thrown by the engine or channel, causes all Queues to be closed
 * the channel to be closed, and the error is reported to the user's
 * Consumer<Throwable>
 */
class AsyncSSLDelegate implements ExceptionallyCloseable, AsyncConnection {

    // outgoing buffers put in this queue first and may remain here
    // while SSL handshaking happening.
    final AsyncWriteQueue appOutputQ = new AsyncWriteQueue(this::upperWrite);

    // Bytes read into this queue before being unwrapped. Backup on this
    // Q should only happen when the engine is stalled due to delegated tasks
    final Queue<ByteBufferReference> channelInputQ;

    // input occurs through the read() method which is expected to be called
    // when the selector signals some data is waiting to be read. All incoming
    // handshake data is handled in this method, which means some calls to
    // read() may return zero bytes of user data. This is not a sign of spinning,
    // just that the handshake mechanics are being executed.

    final SSLEngine engine;
    final SSLParameters sslParameters;
    final HttpConnection lowerOutput;
    final HttpClientImpl client;
    final String serverName;
    // should be volatile to provide proper synchronization(visibility) action
    volatile Consumer<ByteBufferReference> asyncReceiver;
    volatile Consumer<Throwable> errorHandler;
    volatile boolean connected = false;

    // Locks.
    final Object reader = new Object();
    // synchronizing handshake state
    final Semaphore handshaker = new Semaphore(1);
    final String[] alpn;

    // alpn[] may be null. upcall is callback which receives incoming decoded bytes off socket

    AsyncSSLDelegate(HttpConnection lowerOutput, HttpClientImpl client, String[] alpn, String sname)
    {
        SSLContext context = client.sslContext();
        this.serverName = sname;
        engine = context.createSSLEngine();
        engine.setUseClientMode(true);
        SSLParameters sslp = client.sslParameters()
                                   .orElseGet(context::getSupportedSSLParameters);
        sslParameters = Utils.copySSLParameters(sslp);
        if (alpn != null) {
            Log.logSSL("AsyncSSLDelegate: Setting application protocols: " + Arrays.toString(alpn));
            sslParameters.setApplicationProtocols(alpn);
        } else {
            Log.logSSL("AsyncSSLDelegate: no applications set!");
        }
        if (serverName != null) {
            SNIHostName sn = new SNIHostName(serverName);
            sslParameters.setServerNames(List.of(sn));
        }
        logParams(sslParameters);
        engine.setSSLParameters(sslParameters);
        this.lowerOutput = lowerOutput;
        this.client = client;
        this.channelInputQ = new Queue<>();
        this.channelInputQ.registerPutCallback(this::upperRead);
        this.alpn = alpn;
    }

    @Override
    public void writeAsync(ByteBufferReference[] src) throws IOException {
        appOutputQ.put(src);
    }

    @Override
    public void writeAsyncUnordered(ByteBufferReference[] buffers) throws IOException {
        appOutputQ.putFirst(buffers);
    }

    @Override
    public void flushAsync() throws IOException {
        if (appOutputQ.flush()) {
            lowerOutput.flushAsync();
        }
    }

    SSLEngine getEngine() {
        return engine;
    }

    @Override
    public void closeExceptionally(Throwable t) {
        Utils.close(t, appOutputQ, channelInputQ, lowerOutput);
    }

    @Override
    public void close() {
        Utils.close(appOutputQ, channelInputQ, lowerOutput);
    }

    // The code below can be uncommented to shake out
    // the implementation by inserting random delays and trigger
    // handshake in the SelectorManager thread (upperRead)
    // static final java.util.Random random =
    //    new java.util.Random(System.currentTimeMillis());

    /**
     * Attempts to wrap buffers from appOutputQ and place them on the
     * channelOutputQ for writing. If handshaking is happening, then the
     * process stalls and last buffers taken off the appOutputQ are put back
     * into it until handshaking completes.
     *
     * This same method is called to try and resume output after a blocking
     * handshaking operation has completed.
     */
    private void upperWrite(ByteBufferReference[] refs, AsyncWriteQueue delayCallback) {
        // currently delayCallback is not used. Use it when it's needed to execute handshake in another thread.
        try {
            ByteBuffer[] buffers = ByteBufferReference.toBuffers(refs);
            int bytes = Utils.remaining(buffers);
            while (bytes > 0) {
                EngineResult r = wrapBuffers(buffers);
                int bytesProduced = r.bytesProduced();
                int bytesConsumed = r.bytesConsumed();
                bytes -= bytesConsumed;
                if (bytesProduced > 0) {
                    lowerOutput.writeAsync(new ByteBufferReference[]{r.destBuffer});
                }

                // The code below can be uncommented to shake out
                // the implementation by inserting random delays and trigger
                // handshake in the SelectorManager thread (upperRead)

                // int sleep = random.nextInt(100);
                // if (sleep > 20) {
                //   Thread.sleep(sleep);
                // }

                // handshaking is happening or is needed
                if (r.handshaking()) {
                    Log.logTrace("Write: needs handshake");
                    doHandshakeNow("Write");
                }
            }
            ByteBufferReference.clear(refs);
        } catch (Throwable t) {
            closeExceptionally(t);
            errorHandler.accept(t);
        }
    }

    // Connecting at this level means the initial handshake has completed.
    // This means that the initial SSL parameters are available including
    // ALPN result.
    void connect() throws IOException, InterruptedException {
        doHandshakeNow("Init");
        connected = true;
    }

    boolean connected() {
        return connected;
    }

    private void startHandshake(String tag) {
        Runnable run = () -> {
            try {
                doHandshakeNow(tag);
            } catch (Throwable t) {
                Log.logTrace("{0}: handshake failed: {1}", tag, t);
                closeExceptionally(t);
                errorHandler.accept(t);
            }
        };
        client.executor().execute(run);
    }

    private void doHandshakeNow(String tag)
        throws IOException, InterruptedException
    {
        handshaker.acquire();
        try {
            channelInputQ.disableCallback();
            lowerOutput.flushAsync();
            Log.logTrace("{0}: Starting handshake...", tag);
            doHandshakeImpl();
            Log.logTrace("{0}: Handshake completed", tag);
            // don't unblock the channel here, as we aren't sure yet, whether ALPN
            // negotiation succeeded. Caller will call enableCallback() externally
        } finally {
            handshaker.release();
        }
    }

    public void enableCallback() {
        channelInputQ.enableCallback();
    }

     /**
     * Executes entire handshake in calling thread.
     * Returns after handshake is completed or error occurs
     */
    private void doHandshakeImpl() throws IOException {
        engine.beginHandshake();
        while (true) {
            SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
            switch(status) {
                case NEED_TASK: {
                    List<Runnable> tasks = obtainTasks();
                    for (Runnable task : tasks) {
                        task.run();
                    }
                } break;
                case NEED_WRAP:
                    handshakeWrapAndSend();
                    break;
                case NEED_UNWRAP: case NEED_UNWRAP_AGAIN:
                    handshakeReceiveAndUnWrap();
                    break;
                case FINISHED:
                    return;
                case NOT_HANDSHAKING:
                    return;
                default:
                    throw new InternalError("Unexpected Handshake Status: "
                                             + status);
            }
        }
    }

    // acknowledge a received CLOSE request from peer
    void doClosure() throws IOException {
        //while (!wrapAndSend(emptyArray))
            //;
    }

    List<Runnable> obtainTasks() {
        List<Runnable> l = new ArrayList<>();
        Runnable r;
        while ((r = engine.getDelegatedTask()) != null) {
            l.add(r);
        }
        return l;
    }

    @Override
    public void setAsyncCallbacks(Consumer<ByteBufferReference> asyncReceiver,
                                  Consumer<Throwable> errorReceiver,
                                  Supplier<ByteBufferReference> readBufferSupplier) {
        this.asyncReceiver = asyncReceiver;
        this.errorHandler = errorReceiver;
        // readBufferSupplier is not used,
        // because of AsyncSSLDelegate has its own appBufferPool
    }

    @Override
    public void startReading() {
        // maybe this class does not need to implement AsyncConnection
    }

    @Override
    public void stopAsyncReading() {
        // maybe this class does not need to implement AsyncConnection
    }


    static class EngineResult {
        final SSLEngineResult result;
        final ByteBufferReference destBuffer;


        // normal result
        EngineResult(SSLEngineResult result) {
            this(result, null);
        }

        EngineResult(SSLEngineResult result, ByteBufferReference destBuffer) {
            this.result = result;
            this.destBuffer = destBuffer;
        }

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
            lowerOutput.writeAsync(new ByteBufferReference[]{r.destBuffer});
            lowerOutput.flushAsync();
        }
        return r;
    }

    // called during handshaking. It blocks until a complete packet
    // is available, unwraps it and returns.
    void handshakeReceiveAndUnWrap() throws IOException {
        ByteBufferReference ref = channelInputQ.take();
        while (true) {
            // block waiting for input
            EngineResult r = unwrapBuffer(ref.get());
            SSLEngineResult.Status status = r.status();
            if (status == BUFFER_UNDERFLOW) {
                // wait for another buffer to arrive
                ByteBufferReference ref1 = channelInputQ.take();
                ref = combine (ref, ref1);
                continue;
            }
            // OK
            // theoretically possible we could receive some user data
            if (r.bytesProduced() > 0) {
                asyncReceiver.accept(r.destBuffer);
            } else {
                r.destBuffer.clear();
            }
            // it is also possible that a delegated task could be needed
            // even though they are handled in the calling function
            if (r.handshakeStatus() == NEED_TASK) {
                obtainTasks().stream().forEach((task) -> task.run());
            }

            if (!ref.get().hasRemaining()) {
                ref.clear();
                return;
            }
        }
    }

    EngineResult wrapBuffer(ByteBuffer src) throws SSLException {
        ByteBuffer[] bufs = new ByteBuffer[1];
        bufs[0] = src;
        return wrapBuffers(bufs);
    }

    private final ByteBufferPool netBufferPool = new ByteBufferPool();
    private final ByteBufferPool appBufferPool = new ByteBufferPool();

    /**
     * provides buffer of sslEngine@getPacketBufferSize().
     * used for encrypted buffers after wrap or before unwrap.
     * @return ByteBufferReference
     */
    public ByteBufferReference getNetBuffer() {
        return netBufferPool.get(engine.getSession().getPacketBufferSize());
    }

    /**
     * provides buffer of sslEngine@getApplicationBufferSize().
     * @return ByteBufferReference
     */
    private ByteBufferReference getAppBuffer() {
        return appBufferPool.get(engine.getSession().getApplicationBufferSize());
    }

    EngineResult wrapBuffers(ByteBuffer[] src) throws SSLException {
        ByteBufferReference dst = getNetBuffer();
        while (true) {
            SSLEngineResult sslResult = engine.wrap(src, dst.get());
            switch (sslResult.getStatus()) {
                case BUFFER_OVERFLOW:
                    // Shouldn't happen. We allocated buffer with packet size
                    // get it again if net buffer size was changed
                    dst = getNetBuffer();
                    break;
                case CLOSED:
                case OK:
                    dst.get().flip();
                    return new EngineResult(sslResult, dst);
                case BUFFER_UNDERFLOW:
                    // Shouldn't happen.  Doesn't returns when wrap()
                    // underflow handled externally
                    return new EngineResult(sslResult);
                default:
                    assert false;
            }
        }
    }

    EngineResult unwrapBuffer(ByteBuffer srcbuf) throws IOException {
        ByteBufferReference dst = getAppBuffer();
        while (true) {
            SSLEngineResult sslResult = engine.unwrap(srcbuf, dst.get());
            switch (sslResult.getStatus()) {
                case BUFFER_OVERFLOW:
                    // may happen only if app size buffer was changed.
                    // get it again if app buffer size changed
                    dst = getAppBuffer();
                    break;
                case CLOSED:
                    doClosure();
                    throw new IOException("Engine closed");
                case BUFFER_UNDERFLOW:
                    dst.clear();
                    return new EngineResult(sslResult);
                case OK:
                     dst.get().flip();
                     return new EngineResult(sslResult, dst);
            }
        }
    }

    /**
     * Asynchronous read input. Call this when selector fires.
     * Unwrap done in upperRead because it also happens in
     * doHandshake() when handshake taking place
     */
    public void asyncReceive(ByteBufferReference buffer) {
        try {
            channelInputQ.put(buffer);
        } catch (Throwable t) {
            closeExceptionally(t);
            errorHandler.accept(t);
        }
    }

    private ByteBufferReference pollInput() throws IOException {
        return channelInputQ.poll();
    }

    private ByteBufferReference pollInput(ByteBufferReference next) throws IOException {
        return next == null ? channelInputQ.poll() : next;
    }

    public void upperRead() {
        ByteBufferReference src;
        ByteBufferReference next = null;
        synchronized (reader) {
            try {
                src = pollInput();
                if (src == null) {
                    return;
                }
                while (true) {
                    EngineResult r = unwrapBuffer(src.get());
                    switch (r.result.getStatus()) {
                        case BUFFER_UNDERFLOW:
                            // Buffer too small. Need to combine with next buf
                            next = pollInput(next);
                            if (next == null) {
                                // no data available.
                                // push buffer back until more data available
                                channelInputQ.pushback(src);
                                return;
                            } else {
                                src = shift(src, next);
                                if (!next.get().hasRemaining()) {
                                    next.clear();
                                    next = null;
                                }
                            }
                            break;
                        case OK:
                            // check for any handshaking work
                            if (r.handshaking()) {
                                // handshaking is happening or is needed
                                // so we put the buffer back on Q to process again
                                // later.
                                Log.logTrace("Read: needs handshake");
                                channelInputQ.pushback(src);
                                startHandshake("Read");
                                return;
                            }
                            asyncReceiver.accept(r.destBuffer);
                    }
                    if (src.get().hasRemaining()) {
                        continue;
                    }
                    src.clear();
                    src = pollInput(next);
                    next = null;
                    if (src == null) {
                        return;
                    }
                }
            } catch (Throwable t) {
                closeExceptionally(t);
                errorHandler.accept(t);
            }
        }
    }

    ByteBufferReference shift(ByteBufferReference ref1, ByteBufferReference ref2) {
        ByteBuffer buf1 = ref1.get();
        if (buf1.capacity() < engine.getSession().getPacketBufferSize()) {
            ByteBufferReference newRef = getNetBuffer();
            ByteBuffer newBuf = newRef.get();
            newBuf.put(buf1);
            buf1 = newBuf;
            ref1.clear();
            ref1 = newRef;
        } else {
            buf1.compact();
        }
        ByteBuffer buf2 = ref2.get();
        Utils.copy(buf2, buf1, Math.min(buf1.remaining(), buf2.remaining()));
        buf1.flip();
        return ref1;
    }


    ByteBufferReference combine(ByteBufferReference ref1, ByteBufferReference ref2) {
        ByteBuffer buf1 = ref1.get();
        ByteBuffer buf2 = ref2.get();
        int avail1 = buf1.capacity() - buf1.remaining();
        if (buf2.remaining() < avail1) {
            buf1.compact();
            buf1.put(buf2);
            buf1.flip();
            ref2.clear();
            return ref1;
        }
        int newsize = buf1.remaining() + buf2.remaining();
        ByteBuffer newbuf = ByteBuffer.allocate(newsize); // getting rid of buffer pools
        newbuf.put(buf1);
        newbuf.put(buf2);
        newbuf.flip();
        ref1.clear();
        ref2.clear();
        return ByteBufferReference.of(newbuf);
    }

    SSLParameters getSSLParameters() {
        return sslParameters;
    }

    static void logParams(SSLParameters p) {
        if (!Log.ssl()) {
            return;
        }

        if (p == null) {
            Log.logSSL("SSLParameters: Null params");
            return;
        }

        final StringBuilder sb = new StringBuilder("SSLParameters:");
        final List<Object> params = new ArrayList<>();
        if (p.getCipherSuites() != null) {
            for (String cipher : p.getCipherSuites()) {
                sb.append("\n    cipher: {")
                  .append(params.size()).append("}");
                params.add(cipher);
            }
        }

        // SSLParameters.getApplicationProtocols() can't return null
        // JDK 8 EXCL START
        for (String approto : p.getApplicationProtocols()) {
            sb.append("\n    application protocol: {")
              .append(params.size()).append("}");
            params.add(approto);
        }
        // JDK 8 EXCL END

        if (p.getProtocols() != null) {
            for (String protocol : p.getProtocols()) {
                sb.append("\n    protocol: {")
                  .append(params.size()).append("}");
                params.add(protocol);
            }
        }

        if (p.getServerNames() != null) {
            for (SNIServerName sname : p.getServerNames()) {
                 sb.append("\n    server name: {")
                  .append(params.size()).append("}");
                params.add(sname.toString());
            }
        }
        sb.append('\n');

        Log.logSSL(sb.toString(), params.toArray());
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
