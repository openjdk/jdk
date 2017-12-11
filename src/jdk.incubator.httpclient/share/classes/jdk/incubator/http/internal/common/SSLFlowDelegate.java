/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.common;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import jdk.incubator.http.internal.common.SubscriberWrapper.SchedulingAction;

/**
 * Implements SSL using two SubscriberWrappers.
 *
 * <p> Constructor takes two Flow.Subscribers: one that receives the network
 * data (after it has been encrypted by SSLFlowDelegate) data, and one that
 * receives the application data (before it has been encrypted by SSLFlowDelegate).
 *
 * <p> Methods upstreamReader() and upstreamWriter() return the corresponding
 * Flow.Subscribers containing Flows for the encrypted/decrypted upstream data.
 * See diagram below.
 *
 * <p> How Flow.Subscribers are used in this class, and where they come from:
 * <pre>
 * {@code
 *
 *
 *
 * --------->  data flow direction
 *
 *
 *                         +------------------+
 *        upstreamWriter   |                  | downWriter
 *        ---------------> |                  | ------------>
 *  obtained from this     |                  | supplied to constructor
 *                         | SSLFlowDelegate  |
 *        downReader       |                  | upstreamReader
 *        <--------------- |                  | <--------------
 * supplied to constructor |                  | obtained from this
 *                         +------------------+
 * }
 * </pre>
 */
public class SSLFlowDelegate {

    static final boolean DEBUG = Utils.DEBUG; // Revisit: temporary dev flag.
    final System.Logger debug =
            Utils.getDebugLogger(this::dbgString, DEBUG);

    final Executor exec;
    final Reader reader;
    final Writer writer;
    final SSLEngine engine;
    final String tubeName; // hack
    final CompletableFuture<Void> cf;
    final CompletableFuture<String> alpnCF; // completes on initial handshake
    final static ByteBuffer SENTINEL = Utils.EMPTY_BYTEBUFFER;
    volatile boolean close_notify_received;

    /**
     * Creates an SSLFlowDelegate fed from two Flow.Subscribers. Each
     * Flow.Subscriber requires an associated {@link CompletableFuture}
     * for errors that need to be signaled from downstream to upstream.
     */
    public SSLFlowDelegate(SSLEngine engine,
                           Executor exec,
                           Subscriber<? super List<ByteBuffer>> downReader,
                           Subscriber<? super List<ByteBuffer>> downWriter)
    {
        this.tubeName = String.valueOf(downWriter);
        this.reader = new Reader();
        this.writer = new Writer();
        this.engine = engine;
        this.exec = exec;
        this.handshakeState = new AtomicInteger(NOT_HANDSHAKING);
        this.cf = CompletableFuture.allOf(reader.completion(), writer.completion())
                                   .thenRun(this::normalStop);
        this.alpnCF = new MinimalFuture<>();

        // connect the Reader to the downReader and the
        // Writer to the downWriter.
        connect(downReader, downWriter);

        //Monitor.add(this::monitor);
    }

    /**
     * Returns true if the SSLFlowDelegate has detected a TLS
     * close_notify from the server.
     * @return true, if a close_notify was detected.
     */
    public boolean closeNotifyReceived() {
        return close_notify_received;
    }

    /**
     * Connects the read sink (downReader) to the SSLFlowDelegate Reader,
     * and the write sink (downWriter) to the SSLFlowDelegate Writer.
     * Called from within the constructor. Overwritten by SSLTube.
     *
     * @param downReader  The left hand side read sink (typically, the
     *                    HttpConnection read subscriber).
     * @param downWriter  The right hand side write sink (typically
     *                    the SocketTube write subscriber).
     */
    void connect(Subscriber<? super List<ByteBuffer>> downReader,
                 Subscriber<? super List<ByteBuffer>> downWriter) {
        this.reader.subscribe(downReader);
        this.writer.subscribe(downWriter);
    }

   /**
    * Returns a CompletableFuture<String> which completes after
    * the initial handshake completes, and which contains the negotiated
    * alpn.
    */
    public CompletableFuture<String> alpn() {
        return alpnCF;
    }

    private void setALPN() {
        // Handshake is finished. So, can retrieve the ALPN now
        if (alpnCF.isDone())
            return;
        String alpn = engine.getApplicationProtocol();
        debug.log(Level.DEBUG, "setALPN = %s", alpn);
        alpnCF.complete(alpn);
    }

    public String monitor() {
        StringBuilder sb = new StringBuilder();
        sb.append("SSL: HS state: " + states(handshakeState));
        sb.append(" Engine state: " + engine.getHandshakeStatus().toString());
        sb.append(" LL : ");
        synchronized(stateList) {
            for (String s: stateList) {
                sb.append(s).append(" ");
            }
        }
        sb.append("\r\n");
        sb.append("Reader:: ").append(reader.toString());
        sb.append("\r\n");
        sb.append("Writer:: ").append(writer.toString());
        sb.append("\r\n===================================");
        return sb.toString();
    }

    protected SchedulingAction enterReadScheduling() {
        return SchedulingAction.CONTINUE;
    }


    /**
     * Processing function for incoming data. Pass it thru SSLEngine.unwrap().
     * Any decrypted buffers returned to be passed downstream.
     * Status codes:
     *     NEED_UNWRAP: do nothing. Following incoming data will contain
     *                  any required handshake data
     *     NEED_WRAP: call writer.addData() with empty buffer
     *     NEED_TASK: delegate task to executor
     *     BUFFER_OVERFLOW: allocate larger output buffer. Repeat unwrap
     *     BUFFER_UNDERFLOW: keep buffer and wait for more data
     *     OK: return generated buffers.
     *
     * Upstream subscription strategy is to try and keep no more than
     * TARGET_BUFSIZE bytes in readBuf
     */
    class Reader extends SubscriberWrapper {
        final SequentialScheduler scheduler;
        static final int TARGET_BUFSIZE = 16 * 1024;
        volatile ByteBuffer readBuf;
        volatile boolean completing = false;
        final Object readBufferLock = new Object();
        final System.Logger debugr =
            Utils.getDebugLogger(this::dbgString, DEBUG);

        class ReaderDownstreamPusher implements Runnable {
            @Override public void run() { processData(); }
        }

        Reader() {
            super();
            scheduler = SequentialScheduler.synchronizedScheduler(
                                                new ReaderDownstreamPusher());
            this.readBuf = ByteBuffer.allocate(1024);
            readBuf.limit(0); // keep in read mode
        }

        protected SchedulingAction enterScheduling() {
            return enterReadScheduling();
        }

        public final String dbgString() {
            return "SSL Reader(" + tubeName + ")";
        }

        /**
         * entry point for buffers delivered from upstream Subscriber
         */
        @Override
        public void incoming(List<ByteBuffer> buffers, boolean complete) {
            debugr.log(Level.DEBUG, () -> "Adding " + Utils.remaining(buffers)
                        + " bytes to read buffer");
            addToReadBuf(buffers, complete);
            scheduler.runOrSchedule();
        }

        @Override
        public String toString() {
            return "READER: " + super.toString() + " readBuf: " + readBuf.toString()
                    + " count: " + count.toString();
        }

        private void reallocReadBuf() {
            int sz = readBuf.capacity();
            ByteBuffer newb = ByteBuffer.allocate(sz*2);
            readBuf.flip();
            Utils.copy(readBuf, newb);
            readBuf = newb;
        }

        @Override
        protected long upstreamWindowUpdate(long currentWindow, long downstreamQsize) {
            if (readBuf.remaining() > TARGET_BUFSIZE) {
                return 0;
            } else {
                return super.upstreamWindowUpdate(currentWindow, downstreamQsize);
            }
        }

        // readBuf is kept ready for reading outside of this method
        private void addToReadBuf(List<ByteBuffer> buffers, boolean complete) {
            synchronized (readBufferLock) {
                for (ByteBuffer buf : buffers) {
                    readBuf.compact();
                    while (readBuf.remaining() < buf.remaining())
                        reallocReadBuf();
                    readBuf.put(buf);
                    readBuf.flip();
                }
                if (complete) {
                    this.completing = complete;
                }
            }
        }

        void schedule() {
            scheduler.runOrSchedule();
        }

        void stop() {
            debugr.log(Level.DEBUG, "stop");
            scheduler.stop();
        }

        AtomicInteger count = new AtomicInteger(0);

        // work function where it all happens
        void processData() {
            try {
                debugr.log(Level.DEBUG, () -> "processData: " + readBuf.remaining()
                           + " bytes to unwrap "
                           + states(handshakeState)
                           + ", " + engine.getHandshakeStatus());
                int len;
                boolean complete = false;
                while ((len = readBuf.remaining()) > 0) {
                    boolean handshaking = false;
                    try {
                        EngineResult result;
                        synchronized (readBufferLock) {
                            complete = this.completing;
                            result = unwrapBuffer(readBuf);
                            debugr.log(Level.DEBUG, "Unwrapped: %s", result.result);
                        }
                        if (result.bytesProduced() > 0) {
                            debugr.log(Level.DEBUG, "sending %d", result.bytesProduced());
                            count.addAndGet(result.bytesProduced());
                            outgoing(result.destBuffer, false);
                        }
                        if (result.status() == Status.BUFFER_UNDERFLOW) {
                            debugr.log(Level.DEBUG, "BUFFER_UNDERFLOW");
                            // not enough data in the read buffer...
                            requestMore();
                            synchronized (readBufferLock) {
                                // check if we have received some data
                                if (readBuf.remaining() > len) continue;
                                return;
                            }
                        }
                        if (complete && result.status() == Status.CLOSED) {
                            debugr.log(Level.DEBUG, "Closed: completing");
                            outgoing(Utils.EMPTY_BB_LIST, true);
                            return;
                        }
                        if (result.handshaking() && !complete) {
                            debugr.log(Level.DEBUG, "handshaking");
                            doHandshake(result, READER);
                            resumeActivity();
                            handshaking = true;
                        } else {
                            if ((handshakeState.getAndSet(NOT_HANDSHAKING) & ~DOING_TASKS) == HANDSHAKING) {
                                setALPN();
                                handshaking = false;
                                resumeActivity();
                            }
                        }
                    } catch (IOException ex) {
                        errorCommon(ex);
                        handleError(ex);
                    }
                    if (handshaking && !complete)
                        return;
                }
                if (!complete) {
                    synchronized (readBufferLock) {
                        complete = this.completing && !readBuf.hasRemaining();
                    }
                }
                if (complete) {
                    debugr.log(Level.DEBUG, "completing");
                    // Complete the alpnCF, if not already complete, regardless of
                    // whether or not the ALPN is available, there will be no more
                    // activity.
                    setALPN();
                    outgoing(Utils.EMPTY_BB_LIST, true);
                }
            } catch (Throwable ex) {
                errorCommon(ex);
                handleError(ex);
            }
        }
    }

    /**
     * Returns a CompletableFuture which completes after all activity
     * in the delegate is terminated (whether normally or exceptionally).
     *
     * @return
     */
    public CompletableFuture<Void> completion() {
        return cf;
    }

    public interface Monitorable {
        public String getInfo();
    }

    public static class Monitor extends Thread {
        final List<Monitorable> list;
        static Monitor themon;

        static {
            themon = new Monitor();
            themon.start(); // uncomment to enable Monitor
        }

        Monitor() {
            super("Monitor");
            setDaemon(true);
            list = Collections.synchronizedList(new LinkedList<>());
        }

        void addTarget(Monitorable o) {
            list.add(o);
        }

        public static void add(Monitorable o) {
            themon.addTarget(o);
        }

        @Override
        public void run() {
            System.out.println("Monitor starting");
            while (true) {
                try {Thread.sleep(20*1000); } catch (Exception e) {}
                synchronized (list) {
                    for (Monitorable o : list) {
                        System.out.println(o.getInfo());
                        System.out.println("-------------------------");
                    }
                }
                System.out.println("--o-o-o-o-o-o-o-o-o-o-o-o-o-o-");

            }
        }
    }

    /**
     * Processing function for outgoing data. Pass it thru SSLEngine.wrap()
     * Any encrypted buffers generated are passed downstream to be written.
     * Status codes:
     *     NEED_UNWRAP: call reader.addData() with empty buffer
     *     NEED_WRAP: call addData() with empty buffer
     *     NEED_TASK: delegate task to executor
     *     BUFFER_OVERFLOW: allocate larger output buffer. Repeat wrap
     *     BUFFER_UNDERFLOW: shouldn't happen on writing side
     *     OK: return generated buffers
     */
    class Writer extends SubscriberWrapper {
        final SequentialScheduler scheduler;
        // queues of buffers received from upstream waiting
        // to be processed by the SSLEngine
        final List<ByteBuffer> writeList;
        final System.Logger debugw =
            Utils.getDebugLogger(this::dbgString, DEBUG);
        volatile boolean completing;
        boolean completed; // only accessed in processData

        class WriterDownstreamPusher extends SequentialScheduler.CompleteRestartableTask {
            @Override public void run() { processData(); }
        }

        Writer() {
            super();
            writeList = Collections.synchronizedList(new LinkedList<>());
            scheduler = new SequentialScheduler(new WriterDownstreamPusher());
        }

        @Override
        protected void incoming(List<ByteBuffer> buffers, boolean complete) {
            assert complete ? buffers ==  Utils.EMPTY_BB_LIST : true;
            assert buffers != Utils.EMPTY_BB_LIST ? complete == false : true;
            if (complete) {
                debugw.log(Level.DEBUG, "adding SENTINEL");
                completing = true;
                writeList.add(SENTINEL);
            } else {
                writeList.addAll(buffers);
            }
            debugw.log(Level.DEBUG, () -> "added " + buffers.size()
                        + " (" + Utils.remaining(buffers)
                        + " bytes) to the writeList");
            scheduler.runOrSchedule();
        }

        public final String dbgString() {
            return "SSL Writer(" + tubeName + ")";
        }

        protected void onSubscribe() {
            doHandshake(EngineResult.INIT, INIT);
            resumeActivity();
        }

        void schedule() {
            scheduler.runOrSchedule();
        }

        void stop() {
            debugw.log(Level.DEBUG, "stop");
            scheduler.stop();
        }

        @Override
        public boolean closing() {
            return closeNotifyReceived();
        }

        private boolean isCompleting() {
            return completing;
        }

        @Override
        protected long upstreamWindowUpdate(long currentWindow, long downstreamQsize) {
            if (writeList.size() > 10)
                return 0;
            else
                return super.upstreamWindowUpdate(currentWindow, downstreamQsize);
        }

        private boolean hsTriggered() {
            synchronized(writeList) {
                for (ByteBuffer b : writeList)
                    if (b == HS_TRIGGER)
                        return true;
                return false;
            }
        }

        private void processData() {
            boolean completing = isCompleting();

            try {
                debugw.log(Level.DEBUG, () -> "processData(" + Utils.remaining(writeList) + ")");
                while (Utils.remaining(writeList) > 0 || hsTriggered()
                        || needWrap()) {
                    ByteBuffer[] outbufs = writeList.toArray(Utils.EMPTY_BB_ARRAY);
                    EngineResult result = wrapBuffers(outbufs);
                    debugw.log(Level.DEBUG, "wrapBuffer returned %s", result.result);

                    if (result.status() == Status.CLOSED) {
                        if (result.bytesProduced() <= 0)
                            return;

                        if (!completing && !completed) {
                            completing = this.completing = true;
                            // There could still be some outgoing data in outbufs.
                            writeList.add(SENTINEL);
                        }
                    }

                    boolean handshaking = false;
                    if (result.handshaking()) {
                        debugw.log(Level.DEBUG, "handshaking");
                        doHandshake(result, WRITER);
                        handshaking = true;
                    } else {
                        if ((handshakeState.getAndSet(NOT_HANDSHAKING) & ~DOING_TASKS) == HANDSHAKING) {
                            setALPN();
                            resumeActivity();
                        }
                    }
                    cleanList(writeList); // tidy up the source list
                    sendResultBytes(result);
                    if (handshaking && !completing) {
                        if (writeList.isEmpty() && !result.needUnwrap()) {
                            writer.addData(HS_TRIGGER);
                        }
                        if (needWrap()) continue;
                        return;
                    }
                }
                if (completing && Utils.remaining(writeList) == 0) {
                    /*
                    System.out.println("WRITER DOO 3");
                    engine.closeOutbound();
                    EngineResult result = wrapBuffers(Utils.EMPTY_BB_ARRAY);
                    sendResultBytes(result);
                    */
                    if (!completed) {
                        completed = true;
                        writeList.clear();
                        outgoing(Utils.EMPTY_BB_LIST, true);
                    }
                    return;
                }
                if (writeList.isEmpty() && needWrap()) {
                    writer.addData(HS_TRIGGER);
                }
            } catch (Throwable ex) {
                handleError(ex);
            }
        }

        private boolean needWrap() {
            return engine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP;
        }

        private void sendResultBytes(EngineResult result) {
            if (result.bytesProduced() > 0) {
                debugw.log(Level.DEBUG, "Sending %d bytes downstream",
                           result.bytesProduced());
                outgoing(result.destBuffer, false);
            }
        }

        @Override
        public String toString() {
            return "WRITER: " + super.toString() +
                    " writeList size " + Integer.toString(writeList.size());
                    //" writeList: " + writeList.toString();
        }
    }

    private void handleError(Throwable t) {
        debug.log(Level.DEBUG, "handleError", t);
        cf.completeExceptionally(t);
        // no-op if already completed
        alpnCF.completeExceptionally(t);
        reader.stop();
        writer.stop();
    }

    private void normalStop() {
        reader.stop();
        writer.stop();
    }

    private void cleanList(List<ByteBuffer> l) {
        synchronized (l) {
            Iterator<ByteBuffer> iter = l.iterator();
            while (iter.hasNext()) {
                ByteBuffer b = iter.next();
                if (!b.hasRemaining() && b != SENTINEL) {
                    iter.remove();
                }
            }
        }
    }

    /**
     * States for handshake. We avoid races when accessing/updating the AtomicInt
     * because updates always schedule an additional call to both the read()
     * and write() functions.
     */
    private static final int NOT_HANDSHAKING = 0;
    private static final int HANDSHAKING = 1;
    private static final int INIT = 2;
    private static final int DOING_TASKS = 4; // bit added to above state
    private static final ByteBuffer HS_TRIGGER = ByteBuffer.allocate(0);

    private static final int READER = 1;
    private static final int WRITER = 2;

    private static String states(AtomicInteger state) {
        int s = state.get();
        StringBuilder sb = new StringBuilder();
        int x = s & ~DOING_TASKS;
        switch (x) {
            case NOT_HANDSHAKING:
                sb.append(" NOT_HANDSHAKING ");
                break;
            case HANDSHAKING:
                sb.append(" HANDSHAKING ");
                break;
            case INIT:
                sb.append(" INIT ");
                break;
            default:
                throw new InternalError();
        }
        if ((s & DOING_TASKS) > 0)
            sb.append("|DOING_TASKS");
        return sb.toString();
    }

    private void resumeActivity() {
        reader.schedule();
        writer.schedule();
    }

    final AtomicInteger handshakeState;
    final ConcurrentLinkedQueue<String> stateList = new ConcurrentLinkedQueue<>();

    private void doHandshake(EngineResult r, int caller) {
        int s = handshakeState.getAndAccumulate(HANDSHAKING, (current, update) -> update | (current & DOING_TASKS));
        stateList.add(r.handshakeStatus().toString());
        stateList.add(Integer.toString(caller));
        switch (r.handshakeStatus()) {
            case NEED_TASK:
                if ((s & DOING_TASKS) > 0) // someone else was doing tasks
                    return;
                List<Runnable> tasks = obtainTasks();
                executeTasks(tasks);
                break;
            case NEED_WRAP:
                writer.addData(HS_TRIGGER);
                break;
            case NEED_UNWRAP:
            case NEED_UNWRAP_AGAIN:
                // do nothing else
                break;
            default:
                throw new InternalError("Unexpected handshake status:"
                                        + r.handshakeStatus());
        }
    }

    private List<Runnable> obtainTasks() {
        List<Runnable> l = new ArrayList<>();
        Runnable r;
        while ((r = engine.getDelegatedTask()) != null) {
            l.add(r);
        }
        return l;
    }

    private void executeTasks(List<Runnable> tasks) {
        exec.execute(() -> {
            handshakeState.getAndUpdate((current) -> current | DOING_TASKS);
            List<Runnable> nextTasks = tasks;
            do {
                try {
                    nextTasks.forEach((r) -> {
                        r.run();
                    });
                    if (engine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        nextTasks = obtainTasks();
                    } else break;
                } catch (Throwable t) {
                    handleError(t);
                }
            } while(true);
            handshakeState.getAndUpdate((current) -> current & ~DOING_TASKS);
            writer.addData(HS_TRIGGER);
            resumeActivity();
        });
    }


    EngineResult unwrapBuffer(ByteBuffer src) throws IOException {
        ByteBuffer dst = getAppBuffer();
        while (true) {
            SSLEngineResult sslResult = engine.unwrap(src, dst);
            switch (sslResult.getStatus()) {
                case BUFFER_OVERFLOW:
                    // may happen only if app size buffer was changed.
                    // get it again if app buffer size changed
                    int appSize = engine.getSession().getApplicationBufferSize();
                    ByteBuffer b = ByteBuffer.allocate(appSize + dst.position());
                    dst.flip();
                    b.put(dst);
                    dst = b;
                    break;
                case CLOSED:
                    return doClosure(new EngineResult(sslResult));
                case BUFFER_UNDERFLOW:
                    // handled implicitly by compaction/reallocation of readBuf
                    return new EngineResult(sslResult);
                case OK:
                     dst.flip();
                     return new EngineResult(sslResult, dst);
            }
        }
    }

    // FIXME: acknowledge a received CLOSE request from peer
    EngineResult doClosure(EngineResult r) throws IOException {
        debug.log(Level.DEBUG,
                "doClosure(%s): %s [isOutboundDone: %s, isInboundDone: %s]",
                r.result, engine.getHandshakeStatus(),
                engine.isOutboundDone(), engine.isInboundDone());
        if (engine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
            // we have received TLS close_notify and need to send
            // an acknowledgement back. We're calling doHandshake
            // to finish the close handshake.
            if (engine.isInboundDone() && !engine.isOutboundDone()) {
                debug.log(Level.DEBUG, "doClosure: close_notify received");
                close_notify_received = true;
                doHandshake(r, READER);
            }
        }
        return r;
    }

    /**
     * Returns the upstream Flow.Subscriber of the reading (incoming) side.
     * This flow must be given the encrypted data read from upstream (eg socket)
     * before it is decrypted.
     */
    public Flow.Subscriber<List<ByteBuffer>> upstreamReader() {
        return reader;
    }

    /**
     * Returns the upstream Flow.Subscriber of the writing (outgoing) side.
     * This flow contains the plaintext data before it is encrypted.
     */
    public Flow.Subscriber<List<ByteBuffer>> upstreamWriter() {
        return writer;
    }

    public boolean resumeReader() {
        return reader.signalScheduling();
    }

    public void resetReaderDemand() {
        reader.resetDownstreamDemand();
    }

    static class EngineResult {
        final SSLEngineResult result;
        final ByteBuffer destBuffer;

        // normal result
        EngineResult(SSLEngineResult result) {
            this(result, null);
        }

        EngineResult(SSLEngineResult result, ByteBuffer destBuffer) {
            this.result = result;
            this.destBuffer = destBuffer;
        }

        // Special result used to trigger handshaking in constructor
        static EngineResult INIT =
            new EngineResult(
                new SSLEngineResult(SSLEngineResult.Status.OK, HandshakeStatus.NEED_WRAP, 0, 0));

        boolean handshaking() {
            HandshakeStatus s = result.getHandshakeStatus();
            return s != HandshakeStatus.FINISHED
                   && s != HandshakeStatus.NOT_HANDSHAKING
                   && result.getStatus() != Status.CLOSED;
        }

        boolean needUnwrap() {
            HandshakeStatus s = result.getHandshakeStatus();
            return s == HandshakeStatus.NEED_UNWRAP;
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

    public ByteBuffer getNetBuffer() {
        return ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
    }

    private ByteBuffer getAppBuffer() {
        return ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
    }

    final String dbgString() {
        return "SSLFlowDelegate(" + tubeName + ")";
    }

    @SuppressWarnings("fallthrough")
    EngineResult wrapBuffers(ByteBuffer[] src) throws SSLException {
        debug.log(Level.DEBUG, () -> "wrapping "
                    + Utils.remaining(src) + " bytes");
        ByteBuffer dst = getNetBuffer();
        while (true) {
            SSLEngineResult sslResult = engine.wrap(src, dst);
            debug.log(Level.DEBUG, () -> "SSLResult: " + sslResult);
            switch (sslResult.getStatus()) {
                case BUFFER_OVERFLOW:
                    // Shouldn't happen. We allocated buffer with packet size
                    // get it again if net buffer size was changed
                    debug.log(Level.DEBUG, "BUFFER_OVERFLOW");
                    int appSize = engine.getSession().getApplicationBufferSize();
                    ByteBuffer b = ByteBuffer.allocate(appSize + dst.position());
                    dst.flip();
                    b.put(dst);
                    dst = b;
                    break; // try again
                case CLOSED:
                    debug.log(Level.DEBUG, "CLOSED");
                    // fallthrough. There could be some remaining data in dst.
                    // CLOSED will be handled by the caller.
                case OK:
                    dst.flip();
                    final ByteBuffer dest = dst;
                    debug.log(Level.DEBUG, () -> "OK => produced: "
                                           + dest.remaining()
                                           + " not wrapped: "
                                           + Utils.remaining(src));
                    return new EngineResult(sslResult, dest);
                case BUFFER_UNDERFLOW:
                    // Shouldn't happen.  Doesn't returns when wrap()
                    // underflow handled externally
                    // assert false : "Buffer Underflow";
                    debug.log(Level.DEBUG, "BUFFER_UNDERFLOW");
                    return new EngineResult(sslResult);
                default:
                    debug.log(Level.DEBUG, "ASSERT");
                    assert false;
            }
        }
    }
}
