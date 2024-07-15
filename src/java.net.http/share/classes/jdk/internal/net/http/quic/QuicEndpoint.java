/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.TimeLine;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.QuicSelector.QuicNioSelector;
import jdk.internal.net.http.quic.QuicSelector.QuicVirtualThreadPoller;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.HeadersType;
import jdk.internal.util.OperatingSystem;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;

import static jdk.internal.net.http.quic.QuicEndpoint.ChannelType.BLOCKING_WITH_VIRTUAL_THREADS;
import static jdk.internal.net.http.quic.QuicEndpoint.ChannelType.NON_BLOCKING_WITH_SELECTOR;
import static jdk.internal.net.http.quic.TerminationCause.forTransportError;
import static jdk.internal.net.quic.QuicTransportErrors.NO_ERROR;


/**
 * A QUIC Endpoint. A QUIC endpoint encapsulate a DatagramChannel
 * and is registered with a Selector. It subscribes for read and
 * write events from the selector, and implements a readLoop and
 * a writeLoop.
 * <p>
 * The read event or write event are triggered by the selector
 * thread. When the read event is triggered, all available datagrams
 * are read from the channel and pushed into a read queue.
 * Then the readLoop is triggered.
 * When the write event is triggered, the key interestOps are
 * modified to pause write events, and the writeLoop is triggered.
 * <p>
 * The readLoop and writeLoop should never execute on the selector
 * thread, but rather, in the client's executor.
 * <p>
 * When the writeLoop is triggered, it polls the writeQueue and
 * writes as many datagram as it can to the channel. At the end,
 * if there still remains some datagrams in the writeQueue, the
 * write event is resumed. Otherwise, the writeLoop is next
 * triggered when new datagrams are added to the writeQueue.
 * <p>
 * When the readLoop is triggered, it polls the read queue
 * and attempts to match each received packet with a
 * QuicConnection. If no connection matches, it attempts
 * to match the packet with stateless reset tokens.
 * If no stateless reset token match, the packet is
 * discarded.
 * <p>
 */
public abstract sealed class QuicEndpoint implements AutoCloseable
        permits QuicEndpoint.QuicSelectableEndpoint, QuicEndpoint.QuicVirtualThreadedEndpoint {

    private static final int INCOMING_MAX_DATAGRAM;
    static final boolean DGRAM_SEND_ASYNC;
    static final int MAX_BUFFERED_HIGH;
    static final int MAX_BUFFERED_LOW;
    static final boolean IS_WINDOWS;
    static {
        // This default value is the maximum payload size of
        // an IPv6 datagram, which is 65527 (which is bigger
        // than that of an IPv4).
        // We have only one direct buffer of this size per endpoint.
        final int defSize = 65527;
        int size = Utils.getIntegerProperty("jdk.httpclient.quic.maxUdpPayloadSize", defSize);
        // don't allow the value to be below 1200 and above 65527, to conform with RFC-9000,
        // section 18.2.
        if (size < 1200 || size > 65527) {
            // fallback to default size
            size = defSize;
        }
        INCOMING_MAX_DATAGRAM = size;
        // TODO: evaluate pros and cons WRT performance and decide for one or the other
        //       before GA.
        DGRAM_SEND_ASYNC = Utils.getBooleanProperty("jdk.internal.httpclient.quic.sendAsync", false);
        int maxBufferHigh = Math.clamp(Utils.getIntegerProperty("jdk.httpclient.quic.maxBufferedHigh",
                512 << 10), 128 << 10, 6 << 20);
        int maxBufferLow = Math.clamp(Utils.getIntegerProperty("jdk.httpclient.quic.maxBufferedLow",
                384 << 10), 64 << 10,  6 << 20);
        if (maxBufferLow >= maxBufferHigh) maxBufferLow = maxBufferHigh >> 1;
        MAX_BUFFERED_HIGH = maxBufferHigh;
        MAX_BUFFERED_LOW = maxBufferLow;
        IS_WINDOWS = OperatingSystem.isWindows();
    }

    public record Datagram(SocketAddress source, ByteBuffer payload) {}
    public record QuicDatagram(QuicPacketReceiver connection, SocketAddress destination, ByteBuffer payload) {}

    /**
     * An enum identifying the type of channels used and supported by QuicEndpoint and
     * QuicSelector
     */
    public enum ChannelType {
        NON_BLOCKING_WITH_SELECTOR,
        BLOCKING_WITH_VIRTUAL_THREADS;
        public boolean isBlocking() {
            return this == BLOCKING_WITH_VIRTUAL_THREADS;
        }
    };

    // A temporary internal property to switch between two QuicSelector implementation:
    // - if true, uses QuicNioSelector, an implementation based non-blocking and channels
    //   and an NIO Selector
    // - if false, uses QuicVirtualThreadPoller, an implementation that use Virtual Threads
    //   to poll blocking channels
    // On windows, we default to using non-blocking IO with a Selector in order
    // to avoid a potential deadlock in WEPoll (see JDK-8334574).
    private static final boolean USE_NIO_SELECTOR =
            Utils.getBooleanProperty("jdk.internal.httpclient.quic.useNioSelector",
                    OperatingSystem.isWindows());
    /**
     * The configured channel type
     */
    public static final ChannelType CONFIGURED_CHANNEL_TYPE = USE_NIO_SELECTOR
            ? NON_BLOCKING_WITH_SELECTOR
            : BLOCKING_WITH_VIRTUAL_THREADS;

    final Logger debug = Utils.getDebugLogger(this::name);
    private final QuicInstance quicInstance;
    private final String name;
    private final DatagramChannel channel;
    private final ByteBuffer receiveBuffer;
    private final PrivilegedExceptionAction<SocketAddress> privilegedChannelReceive
            = new PrivilegedReceive();
    final Executor executor;
    final ConcurrentLinkedQueue<Datagram> readQueue = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<QuicDatagram> writeQueue = new ConcurrentLinkedQueue<>();
    final QuicTimerQueue timerQueue;
    private volatile boolean closed;

    // A synchronous scheduler to consume the readQueue list;
    final SequentialScheduler readLoopScheduler =
            SequentialScheduler.lockingScheduler(this::readLoop);

    // A synchronous scheduler to consume the writeQueue list;
    final SequentialScheduler writeLoopScheduler =
            SequentialScheduler.lockingScheduler(this::writeLoop);

    // A ConcurrentMap to store registered connections.
    // This may not be the best collection type to use.
    // Another possibility would be to create tree where
    // the id bytes would be used to locate the connection id.
    // We would however have to provide a multi-thread safe version
    // of that tree which is a more complex task. For now, let's
    // use a simple ConcurrentHashMap.
    // the key in this map is a local connection id to which packets will be destined from the
    // peer.
    private final ConcurrentMap<QuicConnectionId, QuicPacketReceiver> connections =
            new ConcurrentHashMap<>();

    // Key used to encrypt tokens before storing in {@link #peerIssuedResetTokens}
    private final Key tokenEncryptionKey;

    // keeps a link of the peer issued stateless reset token to the corresponding connection that
    // will be closed if the specific stateless reset token is received
    private final ConcurrentMap<StatelessResetToken, QuicPacketReceiver> peerIssuedResetTokens =
            new ConcurrentHashMap<>();

    private static ByteBuffer allocateReceiveBuffer() {
        return ByteBuffer.allocateDirect(INCOMING_MAX_DATAGRAM);
    }

    private final AtomicInteger buffered = new AtomicInteger();
    volatile boolean readingStalled;

    public int buffer(int bytes) {
        return buffered.addAndGet(bytes);
    }

    public int unbuffer(int bytes) {
        var newval = buffered.addAndGet(-bytes);
        assert newval >= 0;
        if (newval <= MAX_BUFFERED_LOW) {
            resumeReading();
        }
        return newval;
    }

    boolean bufferTooBig() {
        return buffered.get() >= MAX_BUFFERED_HIGH;
    }

    public int buffered() {
        return buffered.get();
    }

    boolean stopReading() {
        return readingStalled;
    }

    abstract void resumeReading();

    abstract void pauseReading();

    boolean readingStalled() { return readingStalled; }

    private QuicEndpoint(QuicInstance quicInstance,
                         DatagramChannel channel,
                         String name,
                         QuicTimerQueue timerQueue) {
        this.quicInstance = quicInstance;
        this.name = name;
        this.channel = channel;
        this.receiveBuffer = allocateReceiveBuffer();
        this.executor = quicInstance.executor();
        this.timerQueue = timerQueue;
        if (debug.on()) debug.log("created for %s", channel);
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            tokenEncryptionKey = kg.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES key generator not available", e);
        }
    }

    public String name() {
        return name;
    }

    public DatagramChannel channel() {
        return channel;
    }

    Executor writeLoopExecutor() { return executor; }

    public SocketAddress getLocalAddress() throws IOException {
        // Revisit: consider whether this should be done in a doPrivileged, to
        // get an accurate result in the presence of a security manager
        return channel.getLocalAddress();
    }

    public String getLocalAddressString() {
        try {
            return String.valueOf(channel.getLocalAddress());
        } catch (IOException io) {
            return "No address available";
        }
    }

    int getMaxUdpPayloadSize() {
        return INCOMING_MAX_DATAGRAM;
    }

    protected abstract ChannelType channelType();

    /**
     * A {@link QuicEndpoint} implementation based on non blocking
     * {@linkplain DatagramChannel Datagram Channels} and using a
     * NIO {@link Selector}.
     * This implementation is tied to a {@link QuicNioSelector}.
     */
    public static final class QuicSelectableEndpoint extends QuicEndpoint {
        volatile SelectionKey key;

        private QuicSelectableEndpoint(QuicInstance quicInstance,
                                       DatagramChannel channel,
                                       String name,
                                       QuicTimerQueue timerQueue) {
            super(quicInstance, channel, name, timerQueue);
            assert !channel.isBlocking() : "SelectableQuicEndpoint channel is blocking";
        }

        @Override
        public ChannelType channelType() {
            return NON_BLOCKING_WITH_SELECTOR;
        }

        /**
         * Attaches this endpoint to a selector.
         *
         * @param selector the selector to attach to
         * @throws ClosedChannelException if the channel is already closed
         */
        public void attach(Selector selector) throws ClosedChannelException {
            var key = this.key;
            assert key == null;
            // this block is needed to coordinate with detach() and
            // selected(). See comment in selected().
            // OK to use synchronized: only safe method calls
            synchronized (this) {
                this.key = super.channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
            }
        }

        @Override
        void resumeReading() {
            boolean resumed = false;
            SelectionKey key;
            synchronized (this) {
                key = this.key;
                if (key != null && key.isValid()) {
                    if (isClosed() || isChannelClosed()) return;
                    int ops = key.interestOps();
                    int newops = ops | SelectionKey.OP_READ;
                    if (ops != newops) {
                        key.interestOpsOr(SelectionKey.OP_READ);
                        readingStalled = false;
                        resumed = true;
                    }
                }
            }
            if (resumed) {
                // System.out.println(this + " endpoint resumed reading");
                if (debug.on()) debug.log("endpoint resumed reading");
                key.selector().wakeup();
            }
        }

        @Override
        void pauseReading() {
            boolean paused = false;
            synchronized (this) {
                if (readingStalled) return;
                if (key != null && key.isValid() && bufferTooBig()) {
                    if (isClosed() || isChannelClosed()) return;
                    int ops = key.interestOps();
                    int newops = ops & ~SelectionKey.OP_READ;
                    if (ops != newops) {
                        key.interestOpsAnd(~SelectionKey.OP_READ);
                        readingStalled = true;
                        paused = true;
                    }
                }
            }
            if (paused) {
                // System.out.println(this + " endpoint paused reading");
                if (debug.on()) debug.log("endpoint paused reading");
            }
        }

        /**
         * Invoked by the {@link QuicSelector} when this channel
         * is selected.
         *
         * @param readyOps The operations that are ready for this endpoint.
         */
        public void selected(int readyOps) {
            try {
                var key = this.key;
                if (key == null) {
                    // null keys have been observed here.
                    // key can only be null if it's been cancelled, by detach()
                    // or if the call to channel::register hasn't returned yet
                    // the synchronized block below will block until
                    // channel::register returns if needed.
                    // This can only happen once, when attaching the channel,
                    // so there should be no performance issue in synchronizing
                    // here.
                    // OK to use synchronized: no method calls
                    synchronized (this) {
                        key = this.key;
                    }
                }

                if (key == null) {
                    if (debug.on()) {
                        debug.log("key is null");
                        if (QuicEndpoint.class.desiredAssertionStatus()) {
                            Thread.dumpStack();
                        }
                    }
                    return;
                }

                if (debug.on()) {
                    debug.log("selected(interest=%s, ready=%s)",
                            Utils.interestOps(key),
                            Utils.readyOps(key));
                }

                int interestOps = key.interestOps();

                // Some operations may be ready even when we are not interested.
                // Typically, a channel may be ready for writing even if we have
                // nothing to write. The events we need to invoke are therefore
                // at the intersection of the ready set with the interest set.
                int event = readyOps & interestOps;
                if ((event & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                    onReadEvent();
                }
                if ((event & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                    onWriteEvent();
                }
                if (debug.on()) {
                    debug.log("interestOps: %s", Utils.interestOps(key));
                }
            } finally {
                if (!channel().isOpen()) {
                    if (key != null) key.cancel();
                    close();
                }
            }
        }

        private void onReadEvent() {
            var key = this.key;
            try {
                if (debug.on()) debug.log("onReadEvent");
                channelReadLoop();
            } finally {
                if (debug.on()) {
                    debug.log("Leaving readEvent: ops=%s", Utils.interestOps(key));
                }
            }
        }

        private void onWriteEvent() {
            // trigger code that will process the received
            // datagrams asynchronously
            // => Use a sequential scheduler, making sure it never
            //    runs on this thread.
            // Do we need a pub/sub mechanism here?
            // The write event will be paused/resumed by the
            // writeLoop if needed
            if (debug.on()) debug.log("onWriteEvent");
            var key = this.key;
            if (key != null && key.isValid()) {
                int previous;
                synchronized (this) {
                    previous = key.interestOpsAnd(~SelectionKey.OP_WRITE);
                }
                if (debug.on()) debug.log("key changed from %s to: %s",
                        Utils.describeOps(previous), Utils.interestOps(key));
            }
            writeLoopScheduler.runOrSchedule(writeLoopExecutor());
            if (debug.on() && key != null) {
                debug.log("Leaving writeEvent: ops=%s", Utils.interestOps(key));
            }
        }

        @Override
        void writeLoop() {
            super.writeLoop();
            // update selection key if needed
            var key = this.key;
            try {
                if (key != null && key.isValid()) {
                    int ops, newops;
                    synchronized (this) {
                        ops = newops = key.interestOps();
                        if (writeQueue.isEmpty()) {
                            // we have nothing else to write for now
                            newops &= ~SelectionKey.OP_WRITE;
                        } else {
                            // there's more to write
                            newops |= SelectionKey.OP_WRITE;
                        }
                        if (newops != ops && key.selector().isOpen()) {
                            key.interestOps(newops);
                            key.selector().wakeup();
                        }
                    }
                    if (debug.on()) {
                        debug.log("leaving writeLoop: ops=%s", Utils.describeOps(newops));
                    }
                }
            } catch (CancelledKeyException x) {
                if (debug.on()) debug.log("key cancelled");
                if (writeQueue.isEmpty()) return;
                else {
                    closeWriteQueue(x);
                }
            }
        }

        @Override
        void readLoop() {
            try {
                super.readLoop();
            } finally {
                if (debug.on()) {
                    debug.log("leaving readLoop: ops=%s", Utils.interestOps(key));
                }
            }
        }

        @Override
        public void detach() {
            var key = this.key;
            if (key == null) return;
            if (debug.on()) {
                debug.log("cancelling key: " + key);
            }
            // this block is needed to coordinate with attach() and
            // selected(). See comment in selected().
            // OK to use synchronized: only safe method calls
            synchronized (this) {
                key.cancel();
                this.key = null;
            }
        }
    }

    /**
     * A {@link QuicEndpoint} implementation based on blocking
     * {@linkplain DatagramChannel Datagram Channels} and using a
     * Virtual Threads to poll the channel.
     * This implementation is tied to a {@link QuicVirtualThreadPoller}.
     */
    public static final class QuicVirtualThreadedEndpoint extends QuicEndpoint {
        Future<?> key;
        volatile QuicVirtualThreadPoller poller;
        boolean readingDone;

        private QuicVirtualThreadedEndpoint(QuicInstance quicInstance,
                                            DatagramChannel channel,
                                            String name,
                                            QuicTimerQueue timerQueue) {
            super(quicInstance, channel, name, timerQueue);
        }

        @Override
        boolean stopReading() {
            synchronized (this) {
                return readingDone = super.stopReading();
            }
        }

        @Override
        void resumeReading() {
            boolean resumed;
            boolean resumedInOtherThread = false;
            QuicVirtualThreadPoller poller;
            synchronized (this) {
                resumed = readingStalled;
                readingStalled = false;
                poller = this.poller;
                // readingDone is false here, it means reading already resumed
                //    no need to start a new reading thread
                if (poller != null && (resumedInOtherThread = readingDone)) {
                    readingDone = false;
                    attach(poller);
                }
            }
            if (resumedInOtherThread) {
                // last time stopReading() was called it returned true, so we know
                //    the previous poller thread has stopped reading and will exit.
                //    We attached a new poller above, so reading will resume in that
                //    other thread
                // System.out.println(this + " endpoint resumed reading in new virtual thread");
                if (debug.on()) debug.log("endpoint resumed reading in new virtual thread");
            } else if (resumed) {
                // readingStalled was true, and readingDone was false - which means some
                //   poller thread is already active, and will find readingStalled == true
                //   and will continue reading. So reading will resume in the currently
                //   active poller thread
                // System.out.println(this + " endpoint resumed reading in same virtual thread");
                if (debug.on()) debug.log("endpoint resumed reading in same virtual thread");
            } // if readingStalled was false and readingDone was false there is nothing to do.
        }

        @Override
        void pauseReading() {
            boolean paused = false;
            synchronized (this) {
                if (bufferTooBig()) paused = readingStalled = true;
            }
            if (paused) {
                // System.out.println(this + " endpoint paused reading");
                if (debug.on()) debug.log("endpoint paused reading");
            }
        }

        @Override
        public ChannelType channelType() {
            return BLOCKING_WITH_VIRTUAL_THREADS;
        }

        void attach(QuicVirtualThreadPoller poller) {
            this.poller = poller;
            var future = poller.startReading(this);
            synchronized (this) {
                this.key = future;
            }
        }

        Executor writeLoopExecutor() {
            QuicVirtualThreadPoller poller = this.poller;
            if (poller == null) return executor;
            return poller.readLoopExecutor();
        }

        private boolean isDoPrivilegedNeeded() {
            // This a hack to detect whether we run on the server side.
            // In that case, because of JDK-8305530, a doPrivileged is needed
            return super.quicInstance.getClass().getModule() != this.getClass().getModule();
        }

        private final SequentialScheduler channelScheduler = SequentialScheduler.lockingScheduler(this::channelReadLoop0);

        @Override
        void channelReadLoop() {
            channelScheduler.runOrSchedule();
        }

        @SuppressWarnings({"removal"})
        private void channelReadLoop0() {
            var sm = System.getSecurityManager();
            if (sm != null && isDoPrivilegedNeeded()) {
                // needed to work around JDK-8305530
                PrivilegedAction<?> channelReadLoop = () -> {
                    channelReadLoop1();
                    return null;
                };
                AccessController.doPrivileged(channelReadLoop);
            } else {
                channelReadLoop1();
            }
        }

        private void channelReadLoop1() {
            super.channelReadLoop();
        }

        @Override
        public void detach() {
            var key = this.key;
            try {
                if (key != null) {
                    // do not interrupt the reading task if running:
                    // closing the channel later on will ensure that the
                    // task eventually terminates.
                    key.cancel(false);
                }
            } catch (Throwable e) {
                if (debug.on()) {
                    debug.log("Failed to cancel future: " + e);
                }
            }
        }
    }

    private ByteBuffer copyOnHeap(ByteBuffer buffer) {
        ByteBuffer onHeap = ByteBuffer.allocate(buffer.remaining());
        return onHeap.put(buffer).flip();
    }

    void channelReadLoop() {
        // we can't prevent incoming datagram from being received
        // at this level of the stack. If there is a datagram available,
        // we must read it immediately and put it in the read queue.
        // Flow control, which is implemented at the higher level,
        // should ensure that the queue does not overflow.
        //
        // At the moment we have a single endpoint, a single
        // channel, and a single selector thread, so we can do
        // the reading directly in the selector thread and offload
        // the parsing (the readLoop) to the executor.
        boolean nonBlocking = channelType() == NON_BLOCKING_WITH_SELECTOR;
        int count;
        final var buffer = this.receiveBuffer;
        buffer.clear();
        final int initialStart = 1;   // start readloop at first buffer
        // if blocking we want to nudge the scheduler after each read since we don't
        // know how much the next receive will take. If non-blocking, we nudge it
        // after three consecutive read.
        final int maxBeforeStart = nonBlocking ? 3 : 1; // nudge again it after 3 buffers
        int readLoopStarted = initialStart;
        int totalpkt = 0;
        try {
            @SuppressWarnings("removal")
            final SecurityManager sm = System.getSecurityManager();
            int sincepkt = 0;
            while (!isClosed() && !stopReading()) {
                var pos = buffer.position();
                var limit = buffer.limit();
                if (debug.on())
                    debug.log("receiving with buffer(pos=%s, limit=%s)", pos, limit);
                assert pos == 0;
                assert limit > pos;
                final SocketAddress source;
                if (sm == null) {
                    source = channel.receive(buffer);
                } else {
                    try {
                        @SuppressWarnings("removal")
                        var unused = source = AccessController.doPrivileged(this.privilegedChannelReceive);
                    } catch (PrivilegedActionException pae) {
                        throw pae.getCause();
                    }
                }
                if (source == null) {
                    if (debug.on()) debug.log("nothing to read...");
                    if (nonBlocking) break;
                    assert channel.isBlocking();
                }
                totalpkt++;
                sincepkt++;
                buffer.flip();
                count = buffer.remaining();
                if (debug.on()) {
                    debug.log("received %s bytes from %s", count, source);
                }
                if (count > 0) {
                    if (debug.on()) {
                        debug.log("adding %d to read queue from %s,queue size %s",
                                count, source, readQueue.size());
                    }
                    // TODO: add some basic check to drop the packet here if:
                    // - it is too small,
                    // - it is not a quic packet
                    int buffered = buffer(count);
                    readQueue.add(new Datagram(source, copyOnHeap(buffer)));
                    buffer.clear();
                    if (--readLoopStarted == 0 || buffered >= MAX_BUFFERED_HIGH) {
                        readLoopStarted = maxBeforeStart;
                        if (debug.on()) debug.log("triggering readLoop");
                        readLoopScheduler.runOrSchedule(executor);
                        Deadline now;
                        Deadline pending;
                        if (nonBlocking && totalpkt > 1 && (pending = timerQueue.pendingScheduledDeadline())
                                .isBefore(now = timeSource().instant())) {
                            // we have read 3 packets, some events are pending, return
                            // to the selector to process the event queue
                            assert this instanceof QuicEndpoint.QuicSelectableEndpoint
                                    : "unexpected endpoint type: " + this.getClass() + "@[" + name + "]";
                            assert Thread.currentThread() instanceof QuicSelector.QuicSelectorThread;
                            if (Log.quicRetransmit() || Log.quicTimer()) {
                                Log.logQuic(name() + ": reschedule needed: " + Utils.debugDeadline(now, pending)
                                        + ", totalpkt: " + totalpkt
                                        + ", sincepkt: " + sincepkt);
                            } else if (debug.on()) {
                                debug.log("reschedule needed: " + Utils.debugDeadline(now, pending)
                                        + ", totalpkt: " + totalpkt
                                        + ", sincepkt: " + sincepkt);
                            }
                            timerQueue.processEventsAndReturnNextDeadline(now, executor);
                            sincepkt = 0;
                        }
                    }
                    if (this.buffered.get() >= MAX_BUFFERED_HIGH) {
                        pauseReading();
                        readLoopScheduler.runOrSchedule(executor);
                    }
                } else {
                    if (debug.on()) debug.log("Dropped empty datagram");
                }
            }
            // trigger code that will process the received
            // datagrams asynchronously
            // => Use a sequential scheduler, making sure it never
            //    runs on this thread.
            if (!readQueue.isEmpty() && readLoopStarted != maxBeforeStart) {
                if (debug.on()) debug.log("triggering readLoop: queue size " + readQueue.size());
                readLoopScheduler.runOrSchedule(executor);
            }
        } catch (Throwable t) {
            // TODO: special handling for interrupts?
            onReadError(t);
        } finally {
            if (nonBlocking) {
                if (Log.quicRetransmit() || Log.quicTimer()) {
                    Log.logQuic(name() + ": channelReadLoop totalpkt:" + totalpkt);
                } else if (debug.on()) {
                    debug.log("channelReadLoop totalpkt:" + totalpkt);
                }
            }
        }
    }


    @SuppressWarnings("removal")
    private int sendSM(ByteBuffer datagram, SocketAddress destination) throws IOException {
        PrivilegedExceptionAction<Integer> send = () -> channel.send(datagram, destination);
        try {
            return AccessController.doPrivileged(send);
        } catch (PrivilegedActionException pae) {
            throw Utils.throwRuntimeOrIOException(pae);
        }
    }

    private int send(ByteBuffer datagram, SocketAddress destination) throws IOException {
        @SuppressWarnings("removal")
        var sm = System.getSecurityManager();
        if (sm == null) {
            return channel.send(datagram, destination);
        } else {
            // unlike with SocketChannel, sending requires a permission
            return sendSM(datagram, destination);
        }
    }

    void writeLoop() {
        try {
            writeLoop0();
        } catch (Throwable error) {
            if (!expectExceptions && !closed) {
                if (Log.errors()) {
                    Log.logError(name + ": failed to write to channel: " + error);
                    Log.logError(error);
                }
                abort(error);
            }
        }
    }

    boolean sendDatagram(QuicDatagram datagram) throws IOException {
        int sent;
        var payload = datagram.payload();
        var tosend = payload.remaining();
        final var dest = datagram.destination();
        if (isClosed() || isChannelClosed()) {
            if (debug.on()) {
                debug.log("endpoint or channel closed; skipping sending of datagram(%d) to %s",
                        tosend, dest);
            }
            return false;
        }
        if (debug.on()) {
            debug.log("sending datagram(%d) to %s",
                    tosend, dest);
        }
        sent = send(payload, dest);
        if (debug.on()) debug.log("sent %d bytes to %s", sent, dest);
        if (sent == 0 && sent != tosend) return false;
        assert sent == tosend;
        if (datagram.connection != null) {
            datagram.connection.datagramSent(datagram);
        }
        return true;
    }

    void onSendError(QuicDatagram datagram, int tosend, IOException x) {
        // close the connection this came from?
        // close all the connections whose destination is that address?
        var connection = datagram.connection();
        var dest = datagram.destination();
        String msg = x.getMessage();
        if (msg != null && msg.contains("too big")) {
            int max = -1;
            if (connection instanceof QuicConnectionImpl cimpl) {
                max = cimpl.getMaxDatagramSize();
            }
            msg = "on endpoint %s: Failed to send datagram (%s bytes, max: %s) to %s: %s"
                    .formatted(this.name, tosend, max, dest, x);
            if (connection == null && debug.on()) debug.log(msg);
            x = new SocketException(msg, x);
        }
        if (connection != null) {
            connection.datagramDiscarded(datagram);
            connection.onWriteError(x);
            if (!channel.isOpen()) {
                abort(x);
            }
        }
    }

    private void writeLoop0() {
        // write as much as we can
        while (!writeQueue.isEmpty()) {
            var datagram = writeQueue.peek();
            var payload = datagram.payload();
            var tosend = payload.remaining();
            try {
                if (sendDatagram(datagram)) {
                    var rem = writeQueue.poll();
                    assert rem == datagram;
                } else break;
            } catch (IOException x) {
                // close the connection this came from?
                // close all the connections whose destination is that address?
                onSendError(datagram, tosend, x);
                var rem = writeQueue.poll();
                assert rem == datagram;
            }
        }

    }

    void closeWriteQueue(Throwable t) {
        QuicDatagram qd;
        while ((qd = writeQueue.poll()) != null) {
            if (qd.connection != null) {
                qd.connection.onWriteError(t);
            }
        }
    }

    private ByteBuffer peekConnectionBytes(HeadersType headersType, ByteBuffer payload) {
        var cidlen = quicInstance.idFactory().connectionIdLength();
        return switch (headersType) {
            case LONG -> QuicPacket.peekLongConnectionId(payload);
            case SHORT -> QuicPacket.peekShortConnectionId(payload, cidlen);
            default -> null;
        };
    }

    // The readloop is triggered whenever new datagrams are
    // added to the read queue.
    void readLoop() {
        try {
            if (debug.on()) debug.log("readLoop");
            while (!readQueue.isEmpty()) {
                var datagram = readQueue.poll();
                var payload = datagram.payload();
                var source = datagram.source();
                int remaining = payload.remaining();
                unbuffer(remaining);
                if (debug.on()) {
                    debug.log("readLoop: type(%x) %d from %s",
                            payload.hasRemaining() ? payload.get(0) : 0,
                            remaining,
                            source);
                }

                // Retrieve the connection ID from the first packet in the datagram
                // defer handling coalesced packets to the connection
                var pos = payload.position();
                var headersType = QuicPacket.peekHeaderType(payload, pos);
                final ByteBuffer destConnId = peekConnectionBytes(headersType, payload);
                if (debug.on()) {
                    var cidlen = destConnId == null ? 0 : destConnId.remaining();
                    debug.log("headers(%s), connectionId(%d), datagram(%d)",
                            headersType, cidlen, payload.remaining());
                }
                QuicPacketReceiver connection = findQuicConnectionFor(source, destConnId);
                try {
                    if (connection == null) {
                        // maybe stateless reset? or a connection attempt?
                        unmatchedQuicPacket(datagram, headersType, payload, destConnId);
                    } else {
                        connection.processIncoming(datagram.source(), destConnId, headersType, payload);
                    }
                } catch (Throwable t) {
                    if (debug.on()) debug.log("Failed to handle datagram: " + t, t);
                    Log.logError(t);
                }
            }
        } catch (Throwable t) {
            onReadError(t);
        }
    }

    private void onReadError(Throwable t) {
        if (!expectExceptions) {
            if (debug.on()) {
                debug.log("Error handling event: ", t);
            }
            Log.logError(t);
            if (t instanceof RejectedExecutionException
                    || t instanceof ClosedChannelException
                    || t instanceof AssertionError) {
                expectExceptions = true;
                abort(t);
            }
        }
    }

    /**
     * checks if the received datagram contains a stateless reset token;
     * returns the associated connection if true, null otherwise
     * @param datagram received datagram
     * @param buffer datagram contents
     * @return connection associated with the stateless token, or {@code null}
     */
    protected QuicPacketReceiver checkStatelessReset(final Datagram datagram, final ByteBuffer buffer) {
        // We couldn't identify the connection: maybe that's a stateless reset?
        if (closed) return null;
        if (debug.on()) {
            debug.log("Attempting stateless reset for datagram[%d, %s]%n",
                    buffer.remaining(), datagram.source());
        }
        if (buffer.remaining() < 21) {
            // too short to be a stateless reset:
            // RFC 9000:
            // Endpoints MUST discard packets that are too small to be valid QUIC packets.
            // To give an example, with the set of AEAD functions defined in [QUIC-TLS],
            // short header packets that are smaller than 21 bytes are never valid.
            return null;
        }
        final byte[] tokenBytes = new byte[16];
        buffer.get(buffer.limit() - 16, tokenBytes);
        final var token = makeToken(tokenBytes);
        QuicPacketReceiver connection = peerIssuedResetTokens.get(token);
        if (closed) return null;
        if (connection != null) {
            if (debug.on()) {
                debug.log("Received reset token: %s for connection: %s",
                        HexFormat.of().formatHex(tokenBytes), connection);
            }
        }
        return connection;
    }

    private StatelessResetToken makeToken(byte[] tokenBytes) {
        // encrypt token to block timing attacks, see RFC 9000 section 10.3.1
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, tokenEncryptionKey);
            byte[] encryptedBytes = cipher.doFinal(tokenBytes);
            return new StatelessResetToken(encryptedBytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                 IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * Called when parsing a quic packet that couldn't be matched to any registered
     * connection.
     *
     * @param datagram   The datagram containing the packet
     * @param headersType The quic packet type
     * @param buffer     A buffer positioned at the start of the unmatched quic packet.
     *                   The buffer may contain more coalesced quic packets.
     * @param idbytes    destination connection ID
     */
    protected void unmatchedQuicPacket(Datagram datagram,
                                       HeadersType headersType,
                                       ByteBuffer buffer,
                                       ByteBuffer idbytes) throws IOException {
        QuicInstance instance = quicInstance;
        if (debug.on()) {
            debug.log("Unmatched packet in datagram [%s, %d, %s] for %s", headersType,
                    buffer.remaining(), datagram.source(), instance);
        }
        if (closed) {
            if (debug.on()) {
                debug.log("closed: ignoring unmatched datagram");
            }
            return;
        }
        if (headersType == HeadersType.SHORT) {
            if (debug.on()) debug.log("Unmatched packet: ONERTT");
            // maybe a stateless reset?
            final QuicPacketReceiver connection = checkStatelessReset(datagram, buffer);
            if (connection != null) {
                // it's indeed a stateless reset token. let the identified connection handle it
                connection.processStatelessReset();
            } else if (buffer.remaining() >= 44) {
                // if not a stateless reset, check if we should send one
                final ByteBuffer reset = quicInstance.idFactory().statelessReset(idbytes);
                if (reset != null && !closed) {
                    QuicConnectionId cid = quicInstance.idFactory().unsafeConnectionIdFor(idbytes);
                    if (Log.quic()) {
                        Log.logQuic("unmatched packet received on connection id {0}; sending a" +
                                " stateless reset to {1}", cid, datagram.source);
                    } else if (debug.on()) {
                        debug.log("Large (size=%d) unmatched packet received on connection id %s;" +
                                        " will send a stateless reset to %s", buffer.remaining(),
                                cid, datagram.source);
                    }
                    send(reset, datagram.source);
                }
            }
            return;
        }
        if (debug.on()) debug.log("Unmatched packet: delegating to instance");
        instance.unmatchedQuicPacket(datagram.source(), headersType, buffer);
    }

    // Parses the list of active connection
    // Attempts to find one that matches
    // If none match return null
    // Revisit:
    //  if we had an efficient sorted tree where we could locate a connection id
    //  from the idbytes we wouldn't need to use an "unsafe connection id"
    //  quick and dirty solution for now: we use a ConcurrentHashMap and construct
    //  a throw away QuicConnectionId that wrap our mutable idbytes.
    //  This is OK since the classes that may see these bytes are all internal
    //  and won't mutate them.
    QuicPacketReceiver findQuicConnectionFor(SocketAddress peerAddress, ByteBuffer idbytes) {
        if (idbytes == null) return null;
        var cid = quicInstance.unsafeConnectionIdFor(idbytes);
        if (cid == null) {
            if (debug.on()) {
                debug.log("No connection match for: %s", Utils.asHexString(idbytes));
            }
            return null;
        }
        if (debug.on()) {
            debug.log("Looking up QuicConnection for: %s", cid);
        }
        var quicConnection = connections.get(cid);
        assert quicConnection == null || allConnectionIds(quicConnection).anyMatch(cid::equals);
        return quicConnection;
    }

    private static Stream<QuicConnectionId> allConnectionIds(QuicPacketReceiver quicConnection) {
        return Stream.concat(quicConnection.connectionIds(), quicConnection.initialConnectionId().stream());
    }

    /**
     * Detach the channel from the selector implementation
     */
    public abstract void detach();

    void closeConnection(QuicPacketReceiver c) {
        try {
            if (c instanceof QuicConnectionImpl connection) {
                final TerminationCause terminationCause = forTransportError(NO_ERROR)
                        .loggedAs("QUIC endpoint closed - no error");
                connection.terminator.terminate(terminationCause);
            }
        } catch (Throwable t) {
            if (debug.on()) {
                debug.log("Failed to close connection %s: %s", c, t);
            }
        } finally {
            if (c != null) c.shutdown();
        }
    }

    // Called in case of RejectedExecutionException, or shutdownNow;
    void abortConnection(QuicPacketReceiver c, Throwable error) {
        try {
            if (c instanceof QuicConnectionImpl connection) {
                connection.terminator.terminate(TerminationCause.forException(error));
            }
        } catch (Throwable t) {
            if (debug.on()) {
                debug.log("Failed to close connection %s: %s", c, t);
            }
        } finally {
            if (c != null) c.shutdown();
        }
    }

    boolean isClosed() {
        return closed;
    }

    private void detachAndCloseChannel() throws IOException {
        try {
            detach();
        } finally {
            channel.close();
        }
    }

    volatile boolean expectExceptions;
    @Override
    public void close() {
        if (closed) return;
        // OK to use synchronized: no method calls
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        assert closed;
        try {
            while (!connections.isEmpty()) {
                if (debug.on())
                    debug.log("closing %d connections", connections.size());
                for (var cid : connections.keySet()) {
                    closeConnection(connections.remove(cid));
                }
            }
        } finally {
            try {
                // TODO: do we need to wait for something (ACK?)
                //       before actually stopping all loop and closing the channel?
                if (debug.on()) {
                    debug.log("Closing channel " + channel + " of endpoint " + this);
                }
                writeLoopScheduler.stop();
                readLoopScheduler.stop();
                QuicDatagram datagram;
                while ((datagram = writeQueue.poll()) != null) {
                    if (datagram.connection != null) {
                        datagram.connection.datagramDropped(datagram);
                    }
                }
                expectExceptions = true;
                detachAndCloseChannel();
            } catch (IOException io) {
                if (debug.on())
                    debug.log("Failed to detach and close channel: " + io);
            }
        }
    }

    // Called in case of RejectedExecutionException, or shutdownNow;
    public void abort(Throwable error) {

        if (closed) return;
        // OK to use synchronized: no method calls
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        assert closed;
        if (debug.on()) {
            debug.log("aborting: " + error);
        }
        writeLoopScheduler.stop();
        readLoopScheduler.stop();
        QuicDatagram datagram;
        while ((datagram = writeQueue.poll()) != null) {
            if (datagram.connection != null) {
                datagram.connection.datagramDropped(datagram);
            }
        }
        try {
            while (!connections.isEmpty()) {
                if (debug.on())
                    debug.log("closing %d connections", connections.size());
                for (var cid : connections.keySet()) {
                    abortConnection(connections.remove(cid), error);
                }
            }
        } finally {
            try {
                if (debug.on()) {
                    debug.log("Closing channel " + channel + " of endpoint " + this);
                }
                detachAndCloseChannel();
            } catch (IOException io) {
                if (debug.on())
                    debug.log("Failed to detach and close channel: " + io);
            }
        }
    }


    @Override
    public String toString() {
        return name;
    }

    boolean forceSendAsync() {
        return DGRAM_SEND_ASYNC || !writeQueue.isEmpty();
                // TODO remove
                //  perform all writes in a virtual thread. This should trigger
                //  JDK-8334574 more frequently.
                // || (IS_WINDOWS
                //   && channelType().isBlocking()
                //   && !Thread.currentThread().isVirtual());
    }

    /**
     * Schedule a datagram for writing to the underlying channel.
     * If any datagram is pending the given datagram is appended
     * to the list of pending datagrams for writing.
     * @param source the source connection
     * @param destination the destination address
     * @param payload the encrypted datagram
     */
    public void pushDatagram(QuicPacketReceiver source, SocketAddress destination, ByteBuffer payload) {
        int tosend = payload.remaining();
        if (debug.on()) {
            debug.log("attempting to send datagram [%s bytes]", tosend);
        }
        var datagram = new QuicDatagram(source, destination, payload);
        try {
            // if DGRAM_SEND_ASYNC is true we don't attempt to send from the current
            // thread but push the datagram on the queue and invoke the write loop.
            if (forceSendAsync() || !sendDatagram(datagram)) {
                if (tosend == payload.remaining()) {
                    writeQueue.add(datagram);
                    if (debug.on()) {
                        debug.log("datagram [%s bytes] added to write queue, queue size %s",
                                tosend, writeQueue.size());
                    }
                    writeLoopScheduler.runOrSchedule(writeLoopExecutor());
                } else {
                    source.datagramDropped(datagram);
                    if (debug.on()) {
                        debug.log("datagram [%s bytes] dropped: payload partially consumed, remaining %s",
                                tosend, payload.remaining());
                    }
                }
            }
        } catch (IOException io) {
            onSendError(datagram, tosend, io);
        }
    }

    /**
     * Called to schedule sending of a datagram that contains a {@code ConnectionCloseFrame}.
     * This will replace the {@link QuicConnectionImpl} with a {@link ClosedConnection} that
     * will replay the datagram containing the  {@code ConnectionCloseFrame} whenever a packet
     * for that connection is received.
     * @param connection   the connection being closed
     * @param destination  the peer address
     * @param datagram     the datagram
     */
    public void pushClosingDatagram(QuicConnectionImpl connection, InetSocketAddress destination, ByteBuffer datagram) {
        if (debug.on()) debug.log("Pushing closing datagram for " + connection.logTag());
        closing(connection, datagram.slice());
        pushDatagram(connection, destination, datagram);
    }

    /**
     * Called to schedule sending of a datagram that contains a single {@code ConnectionCloseFrame}
     * sent in response to a {@code ConnectionClose} frame.
     * This will completely remove the connection from the connection map.
     * @param connection   the connection being closed
     * @param destination  the peer address
     * @param datagram     the datagram
     */
    public void pushClosedDatagram(QuicConnectionImpl connection,
                                   InetSocketAddress destination,
                                   ByteBuffer datagram) {
        if (debug.on()) debug.log("Pushing closed datagram for " + connection.logTag());
        removeConnection(connection);
        pushDatagram(connection, destination, datagram);
    }

    /**
     * This will completely remove the connection from the endpoint. Any subsequent packets
     * directed to this connection from a peer, may end up receiving a stateless reset
     * from this endpoint.
     *
     * @param connection the connection to be removed
     */
    void removeConnection(final QuicPacketReceiver connection) {
        if (debug.on()) debug.log("removing connection " + connection);
        // remove the connection completely
        connection.connectionIds().forEach(connections::remove);
        // remove references to this connection from the map which holds the peer issued
        // reset tokens
        dropPeerIssuedResetTokensFor(connection);
    }

    public final int connectionCount() {
        return connections.size();
    }

    // drop peer issued stateless tokes for the given connection
    private void dropPeerIssuedResetTokensFor(QuicPacketReceiver connection) {
        // remove references to this connection from the map which holds the peer issued
        // reset tokens
        peerIssuedResetTokens.values().removeIf(conn -> connection == conn);
    }

    // remap peer issued stateless token from connection `from` to connection `to`
    private void remapPeerIssuedResetToken(QuicPacketReceiver from, QuicPacketReceiver to) {
        assert from != null;
        assert to != null;
        peerIssuedResetTokens.replaceAll((tok, c) -> c == from ? to : c);
    }

    public void draining(final QuicPacketReceiver connection) {
        // remap the connection to a DrainingConnection
        if (closed) return;
        connection.connectionIds().forEach((id) ->
                connections.compute(id, (i, r) -> remapDraining(i, r) ));
    }

    private DrainingConnection remapDraining(QuicConnectionId id, QuicPacketReceiver conn) {
        if (closed) return null;
        var debugOn =  debug.on() && !Thread.currentThread().isVirtual();
        if (conn instanceof ClosingConnection closing) {
            if (debugOn) debug.log("remapping %s to DrainingConnection", id);
            final var draining = closing.toDraining();
            remapPeerIssuedResetToken(closing, draining);
            draining.startTimer();
            return draining;
        } else if (conn instanceof DrainingConnection draining) {
            return draining;
        } else if (conn instanceof QuicConnectionImpl impl) {
            final long idleTimeout = impl.peerPtoMs() * 3; // 3 PTO
            if (debugOn) debug.log("remapping %s to DrainingConnection", id);
            var draining = new DrainingConnection(id, idleTimeout);
            // we can ignore stateless reset in the draining state.
            remapPeerIssuedResetToken(impl, draining);
            draining.startTimer();
            return draining;
        } else if (conn == null) {
            // connection absent (was probably removed), don't remap to draining
            if (debugOn) {
                debug.log("no existing connection present for %s, won't remap to draining", id);
            }
            return null;
        } else {
            assert false : "unexpected connection type: " + conn; // just remove
            return null;
        }
    }

    protected void closing(QuicConnectionImpl connection, ByteBuffer datagram) {
        if (closed) return;
        ByteBuffer closing = ByteBuffer.allocate(datagram.limit());
        closing.put(datagram.slice());
        closing.flip();
        connection.connectionIds().forEach((id) ->
                connections.compute(id, (i, r) -> remapClosing(i, r, closing)));
    }

    private ClosedConnection remapClosing(QuicConnectionId id, QuicPacketReceiver conn, ByteBuffer datagram) {
        if (closed) return null;
        var debugOn =  debug.on() && !Thread.currentThread().isVirtual();
        if (conn instanceof ClosingConnection closing) {
            closing.closePackets.add(datagram);
            return closing;
        } else if (conn instanceof DrainingConnection draining) {
            return draining;
        } else if (conn instanceof QuicConnectionImpl impl) {
            final long idleTimeout = impl.peerPtoMs() * 3; // 3 PTO
            if (debugOn) debug.log("remapping %s to ClosingConnection", id);
            var closing = new ClosingConnection(id, idleTimeout, datagram);
            remapPeerIssuedResetToken(impl, closing);
            closing.startTimer();
            return closing;
        } else if (conn == null) {
            // connection absent (was probably removed), don't remap to closing
            if (debugOn) {
                debug.log("no existing connection present for %s, won't remap to closing", id);
            }
            return null;
        } else {
            assert false : "unexpected connection type: " + conn; // just remove
            return null;
        }
    }

    public void registerNewConnection(QuicConnectionImpl quicConnection) throws IOException {
        if (closed) throw new ClosedChannelException();
        quicConnection.connectionIds().forEach((id) -> putConnection(id, quicConnection));
    }

    /**
     * A peer issues a stateless reset token which it can then send to close the connection. This
     * method links the peer issued token against the connection that needs to be closed if/when
     * that stateless reset token arrives in the packet.
     *
     * @param statelessResetToken the peer issued (16 byte) stateless reset token
     * @param connection the connection to link the token against
     */
    void associateStatelessResetToken(final byte[] statelessResetToken, final QuicPacketReceiver connection) {
        Objects.requireNonNull(connection);
        Objects.requireNonNull(statelessResetToken);
        final int tokenLength = statelessResetToken.length;
        if (statelessResetToken.length != 16) {
            throw new IllegalArgumentException("Invalid stateless reset token length " + tokenLength);
        }
        if (debug.on()) {
            debug.log("associating stateless reset token with connection %s", connection);
        }
        this.peerIssuedResetTokens.put(makeToken(statelessResetToken), connection);
    }

    /**
     * Discard the stateless reset token that this endpoint might have previously
     * {@link #associateStatelessResetToken(byte[], QuicPacketReceiver) associated any connection}
     * with
     * @param statelessResetToken The stateless reset token
     */
    void forgetStatelessResetToken(final byte[] statelessResetToken) {
        // just a tiny optimization - we know stateless reset token must be of 16 bytes, if the passed
        // value isn't, then no point doing any more work
        if (statelessResetToken.length != 16) {
            return;
        }
        this.peerIssuedResetTokens.remove(makeToken(statelessResetToken));
    }

    /**
     * {@return the timer queue associated with this endpoint}
     */
    public QuicTimerQueue timer() {
        return timerQueue;
    }

    public boolean isChannelClosed() {
        return !channel().isOpen();
    }

    /**
     * {@return the time source associated with this endpoint}
     * @apiNote
     * There is a unique global {@linkplain TimeSource#source()} for the whole
     * JVM, but this method can be overridden in tests to define an alternative
     * timeline for the test.
     */
    protected TimeLine timeSource() {
        return TimeSource.source();
    }

    private void putConnection(QuicConnectionId id, QuicConnectionImpl quicConnection) {
        // ideally we'd want to use an immutable byte buffer as a key here.
        // but we don't have that. So we use the connection id instead.
        var old = connections.put(id, quicConnection);
        assert old == null : "%s already registered with %s (%s)"
                .formatted(old, id, old == quicConnection ? "old == new" : "old != new");
    }


    /**
     * Represent a closing or draining quic connection: if we receive any packet
     * for this connection we ignore them (if in draining state) or replay the
     * closed packets in decreasing frequency: we reply to the
     * first packet, then to the third, then to the seventh, etc...
     * We stop replying after 16*16/2.
     */
    public sealed abstract class ClosedConnection implements QuicPacketReceiver, QuicTimedEvent
            permits QuicEndpoint.ClosingConnection, QuicEndpoint.DrainingConnection {

        // default time we keep the ClosedConnection alive while closing/draining - if
        // PTO information is not available (if 0 is passed as idleTimeoutMs when creating
        // an instance of this class)
        final static long NO_IDLE_TIMEOUT = 2000;
        final QuicConnectionId localConnectionId;
        final long maxIdleTimeMs;
        final long id;
        int more = 1;
        int waitformore;
        volatile Deadline deadline;
        volatile Deadline updatedDeadline;

        ClosedConnection(QuicConnectionId localConnectionId,
                         long maxIdleTimeMs) {
            this.id = QuicTimerQueue.newEventId();
            this.maxIdleTimeMs = maxIdleTimeMs == 0 ? NO_IDLE_TIMEOUT : maxIdleTimeMs;
            this.deadline = Deadline.MAX;
            this.updatedDeadline = Deadline.MAX;
            this.localConnectionId = localConnectionId;
        }

        @Override
        public final QuicConnectionId localConnectionId() {
            return localConnectionId;
        }

        @Override
        public final void processIncoming(SocketAddress source, ByteBuffer destConnId, HeadersType headersType, ByteBuffer buffer) {
            Deadline updated = updatedDeadline;
            var waitformore = this.waitformore;
            // Deadline.MIN will be set in case of write errors
            if (updated != Deadline.MIN && waitformore == 0) {
                var more = this.more;
                this.waitformore = more;
                this.more = more = more << 1;
                if (more > 16) {
                    // the server doesn't seem to take into account our
                    // connection close frame. Just stop responding
                    updatedDeadline = Deadline.MIN;
                } else {
                    updatedDeadline = updated.plusMillis(maxIdleTimeMs);
                }
                handleIncoming(source, destConnId, headersType, buffer);
            } else {
                this.waitformore = waitformore - 1;
                dropIncoming(source, destConnId, headersType, buffer);
            }

            timer().reschedule(this, updatedDeadline);
        }

        protected void handleIncoming(SocketAddress source, ByteBuffer idbytes,
                                      HeadersType headersType, ByteBuffer buffer) {
            dropIncoming(source, idbytes, headersType, buffer);
        }

        protected abstract void dropIncoming(SocketAddress source, ByteBuffer idbytes,
                                    HeadersType headersType, ByteBuffer buffer);

        @Override
        public final void onWriteError(Throwable t) {
            if (debug.on())
                debug.log("failed to write close packet", t);
            removeConnection(this);
            // handle() will be called, which will cause
            // the timer queue to remove this object
            updatedDeadline = Deadline.MIN;
            timer().reschedule(this);
        }

        public final void startTimer() {
            deadline = updatedDeadline = timeSource().instant().plusMillis(maxIdleTimeMs);
            timer().offer(this);
        }

        @Override
        public final Deadline deadline() {
            return deadline;
        }

        @Override
        public final Deadline handle() {
            removeConnection(this);
            // Deadline.MAX means do not reschedule
            return updatedDeadline = Deadline.MAX;
        }

        @Override
        public final Deadline refreshDeadline() {
            // Returning Deadline.MIN here will cause handle() to
            // be called and will remove this task from the timer queue.
            return deadline = updatedDeadline;
        }

        @Override
        public final long eventId() {
            return id;
        }

        @Override
        public final void processStatelessReset() {
            // the peer has sent us a stateless reset: no need to
            //   replay CloseConnectionFrame. Just remove this connection.
            removeConnection(this);
            // handle() will be called, which will cause
            // the timer queue to remove this object
            updatedDeadline = Deadline.MIN;
            timer().reschedule(this);
        }

        public void shutdown() {
            updatedDeadline = Deadline.MIN;
            timer().reschedule(this);
        }
    }


    /**
     * Represent a closing quic connection: if we receive any packet for this
     * connection we simply replay the packet(s) that contained the
     * ConnectionCloseFrame frame.
     * Packets are replayed in decreasing frequency. We reply to the
     * first packet, then to the third, then to the seventh, etc...
     * We stop replying after 16*16/2.
     */
    public final class ClosingConnection extends ClosedConnection {

        final List<ByteBuffer> closePackets = Collections.synchronizedList(new ArrayList<>());

        ClosingConnection(QuicConnectionId localConnectionId,
                          long maxIdleTimeMs,
                          ByteBuffer... closePackets) {
            super(localConnectionId, maxIdleTimeMs);
            this.closePackets.addAll(Arrays.asList(closePackets));
        }

        @Override
        public void handleIncoming(SocketAddress source, ByteBuffer idbytes,
                                   HeadersType headersType, ByteBuffer buffer) {
            if (isClosed() || isChannelClosed()) {
                // don't respond with any more datagrams and instead just drop
                // the incoming ones since the channel is closed
                dropIncoming(source, idbytes, headersType, buffer);
                return;
            }
            if (debug.on() && !closePackets.isEmpty()) {
                debug.log("ClosingConnection(%s): sending closed packets", localConnectionId);
            }
            for (ByteBuffer buf : closePackets) {
                pushDatagram(this, source, buf.slice());
            }
        }

        @Override
        protected void dropIncoming(SocketAddress source, ByteBuffer idbytes, HeadersType headersType, ByteBuffer buffer) {
            if (debug.on()) {
                debug.log("ClosingConnection(%s): dropping %s packet", localConnectionId, headersType);
            }
        }

        private DrainingConnection toDraining() {
            return new DrainingConnection(localConnectionId, maxIdleTimeMs);
        }
    }

    /**
     * Represent a draining quic connection: if we receive any packet for this
     * connection we simply ignore them.
     */
    public final class DrainingConnection extends ClosedConnection {

        DrainingConnection(QuicConnectionId localConnectionId,
                           long maxIdleTimeMs) {
            super(localConnectionId, maxIdleTimeMs);
        }

        @Override
        public void dropIncoming(SocketAddress source, ByteBuffer idbytes, HeadersType headersType, ByteBuffer buffer) {
            if (debug.on()) {
                debug.log("DrainingConnection(%s): dropping %s packet", localConnectionId, headersType);
            }
        }

    }

    private record StatelessResetToken (byte[] token) {
        StatelessResetToken(final byte[] token) {
            this.token = token.clone();
        }
        @Override
        public int hashCode() {
            return Arrays.hashCode(token);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof StatelessResetToken other) {
                return Arrays.equals(token, other.token);
            }
            return false;
        }
    }

    /**
     * {@return a new {@link QuicEndpoint} of the given {@code endpointType}}
     * @param endpointType the concrete endpoint type, one of {@link QuicSelectableEndpoint
     *          QuicSelectableEndpoint.class} or {@link QuicVirtualThreadedEndpoint
     *          QuicVirtualThreadedEndpoint.class}.
     * @param quicInstance the quic instance
     * @param name         the endpoint name
     * @param bindAddress  the address to bind to
     * @param timerQueue   the timer queue
     * @param <T> the concrete endpoint type, one of {@link QuicSelectableEndpoint}
     *           or {@link QuicVirtualThreadedEndpoint}
     * @throws IOException if an IOException occurs
     * @throws IllegalArgumentException if the given endpoint type is not one of
     *         {@link QuicSelectableEndpoint QuicSelectableEndpoint.class} or
     *         {@link QuicVirtualThreadedEndpoint QuicVirtualThreadedEndpoint.class}
     */
    @SuppressWarnings({"unchecked", "removal"})
    private static <T extends QuicEndpoint> T create(Class<T> endpointType,
                                                     QuicInstance quicInstance,
                                                     String name,
                                                     SocketAddress bindAddress,
                                                     QuicTimerQueue timerQueue,
                                                     AccessControlContext acc) throws IOException {
        PrivilegedExceptionAction<DatagramChannel> action = () -> {
            DatagramChannel channel = DatagramChannel.open();
            // avoid dependency on extnet
            Optional<SocketOption<?>> df = channel.supportedOptions().stream().
                    filter(o -> "IP_DONTFRAGMENT".equals(o.name())).findFirst();
            if (df.isPresent()) {
                // TODO on some platforms this doesn't work on dual stack sockets
                // see Net#shouldSetBothIPv4AndIPv6Options
                channel.setOption((SocketOption<Boolean>)df.get(), true);
            }
            if (QuicSelectableEndpoint.class.isAssignableFrom(endpointType)) {
                channel.configureBlocking(false);
            }
            Consumer<String> logSink = Log.quic() ? Log::logQuic : null;
            Utils.configureChannelBuffers(logSink, channel,
                    quicInstance.getReceiveBufferSize(), quicInstance.getSendBufferSize());
            return channel.bind(bindAddress); // could do that on attach instead?
        };
        DatagramChannel channel;
        try {
            @SuppressWarnings("removal")
            var unused = channel = AccessController.doPrivileged(action, acc);
        } catch (PrivilegedActionException e) {
            throw Utils.throwRuntimeOrIOException(e);
        }

        if (endpointType.isAssignableFrom(QuicSelectableEndpoint.class)) {
            return endpointType.cast(new QuicSelectableEndpoint(quicInstance, channel, name, timerQueue));
        } else if (endpointType.isAssignableFrom(QuicVirtualThreadedEndpoint.class)) {
            return endpointType.cast(new QuicVirtualThreadedEndpoint(quicInstance, channel, name, timerQueue));
        } else {
            throw new IllegalArgumentException(endpointType.getName());
        }
    }

    private final class PrivilegedReceive implements PrivilegedExceptionAction<SocketAddress> {
        @Override
        public SocketAddress run() throws IOException {
            return QuicEndpoint.this.channel.receive(QuicEndpoint.this.receiveBuffer);
        }
    }

    public static final class QuicEndpointFactory {

        @SuppressWarnings("removal")
        private final  AccessControlContext acc;

        @SuppressWarnings("removal")
        public QuicEndpointFactory() {
            this.acc = AccessController.getContext();
        }

        /**
         * {@return a new {@code QuicSelectableEndpoint}}
         *
         * @param quicInstance the quic instance
         * @param name         the endpoint name
         * @param bindAddress  the address to bind to
         * @param timerQueue   the timer queue
         * @throws IOException if an IOException occurs
         */
        @SuppressWarnings("removal")
        public QuicSelectableEndpoint createSelectableEndpoint(QuicInstance quicInstance,
                                                                      String name,
                                                                      SocketAddress bindAddress,
                                                                      QuicTimerQueue timerQueue)
                throws IOException {
            return create(QuicSelectableEndpoint.class, quicInstance, name, bindAddress, timerQueue, acc);
        }

        /**
         * {@return a new {@code QuicVirtualThreadedEndpoint}}
         *
         * @param quicInstance the quic instance
         * @param name         the endpoint name
         * @param bindAddress  the address to bind to
         * @param timerQueue   the timer queue
         * @throws IOException if an IOException occurs
         */
        @SuppressWarnings("removal")
        public QuicVirtualThreadedEndpoint createVirtualThreadedEndpoint(QuicInstance quicInstance,
                                                                                String name,
                                                                                SocketAddress bindAddress,
                                                                                QuicTimerQueue timerQueue)
                throws IOException {
            return create(QuicVirtualThreadedEndpoint.class, quicInstance, name, bindAddress, timerQueue, acc);
        }
    }

    /**
     * Registers the given endpoint with the given selector.
     * <p>
     * An endpoint of class {@link QuicSelectableEndpoint} is only
     * compatible with a selector of type {@link QuicNioSelector}.
     * An endpoint of tyoe {@link QuicVirtualThreadedEndpoint} is only
     * compatible with a selector of type {@link QuicVirtualThreadPoller}.
     * <br>
     * If the given endpoint implementation is not compatible with
     * the given selector implementation an {@link IllegalStateException}
     * is thrown.
     *
     * @param endpoint the endpoint
     * @param selector the selector
     * @param debug    a logger for debugging
     *
     * @return the registered endpoint
     *
     * @throws IOException if an IOException occurs
     * @throws IllegalStateException if the endpoint and selector implementations
     *         are not compatible
     */
    public static void registerWithSelector(QuicEndpoint endpoint, QuicSelector<?> selector, Logger debug)
            throws IOException {
        if (selector instanceof QuicVirtualThreadPoller poller) {
            var loopingEndpoint = (QuicVirtualThreadedEndpoint) endpoint;
            poller.register(loopingEndpoint);
        } else if (selector instanceof QuicNioSelector selectable) {
            var selectableEndpoint = (QuicEndpoint.QuicSelectableEndpoint) endpoint;
            selectable.register(selectableEndpoint);
        } else {
            throw new IllegalStateException("Incompatible selector and endpoint implementations: %s <-> %s"
                    .formatted(selector.getClass(), endpoint.getClass()));
        }
        if (debug.on()) debug.log("endpoint registered with selector");
    }
}
