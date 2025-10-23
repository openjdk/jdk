/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.streams;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.streams.QuicSenderStream.SendingStreamState;

/**
 * A class to handle the writing queue of a {@link QuicSenderStream}.
 * This class maintains a queue of byte buffer containing stream data
 * that has not yet been packaged for sending. It also keeps track of
 * the max stream data value.
 * It acts as a mailbox between a producer (typically a {@link QuicStreamWriter}),
 * and a consumer (typically a {@link jdk.internal.net.http.quic.QuicConnectionImpl}).
 * This class is abstract: a concrete implementation of this class must only
 * implement {@link #wakeupProducer()} and {@link #wakeupConsumer()} which should
 * wake up the producer and consumer respectively, when data can be polled or
 * submitted from the queue.
 */
abstract class StreamWriterQueue {
    /**
     * The amount of data that a StreamWriterQueue is willing to buffer.
     * The queue will buffer excess data, but will not wake up the producer
     * until the excess is consumed.
     */
    private static final int BUFFER_SIZE =
            Utils.getIntegerProperty("jdk.httpclient.quic.streamBufferSize", 1 << 16);

    // The current buffer containing data to send.
    private ByteBuffer current;
    // The offset of the data that has been consumed
    private volatile long bytesConsumed;
    // The offset of the data that has been supplied by the
    // producer.
    // bytesProduced >= bytesConsumed at all times.
    private volatile long bytesProduced;
    // The stream size, when known, -1 otherwise.
    // The stream size may be known at the creation of the stream,
    // or at the latest when the last ByteBuffer is provided by
    // the producer.
    private volatile long streamSize = -1;
    // true if reset was requested, false otherwise
    private volatile boolean resetRequested;
    // The maximum offset that will be accepted by the peer at this
    // time. bytesConsumed <= maxStreamData at all times.
    private volatile long maxStreamData;
    // negative if stop sending was received; contains -(errorCode + 1)
    private volatile long stopSending;
    // The queue to buffer data before it's polled by the consumer
    private final ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();

    protected final void lock() {
        lock.lock();
    }

    protected final void unlock() {
        lock.unlock();
    }

    protected abstract Logger debug();

    /**
     * This method is called by the consumer to poll data from the stream
     * queue. This method will return a {@code ByteBuffer} with at most
     * {@code maxbytes} remaining bytes. The {@code ByteBuffer} may contain
     * less bytes if not enough bytes are available, or if there is not
     * enough {@linkplain #consumerCredit() credit} to send {@code maxbytes}
     * to the peer. Only stream credit is taken into account. Taking into
     * account connection credit is the responsibility of the caller.
     * If there is no credit, or if there is no data available, {@code null}
     * is returned. When credit and data are available again, {@link #wakeupConsumer()}
     * is called to wake up the consumer.
     *
     * @apiNote
     * This method increases the consumer offset. It must not be called concurrently
     * by two different threads.
     *
     * @implNote
     * If the producer was blocked due to full buffer before this method was called
     * and the method removes enough of buffered data,
     * {@link #wakeupProducer()} is called.
     *
     * @param   maxbytes the maximum number of bytes the consumer is prepared
     *                   to consume.
     * @return a {@code ByteBuffer} containing at most {@code maxbytes}, or {@code null}
     *  if no data is available or data is blocked by flow control.
     */
    public final ByteBuffer poll(int maxbytes) {
        boolean producerWasBlocked, producerUnblocked;
        long produced, consumed;
        ByteBuffer buffer;
        long credit = consumerCredit();
        assert credit >= 0 : credit;
        if (credit < maxbytes) {
            maxbytes = (int)credit;
        }
        if (maxbytes <= 0) return null;
        lock();
        try {
            producerWasBlocked = producerBlocked();
            buffer = current;
            if (buffer == null) {
                buffer = current = queue.poll();
            }
            if (buffer == null) {
                return null;
            }
            int remaining = buffer.remaining();
            int position = buffer.position();
            consumed = bytesConsumed;
            if (remaining <= maxbytes) {
                current = queue.poll();
                bytesConsumed = consumed = Math.addExact(consumed, remaining);
            } else {
                buffer = buffer.slice(position, maxbytes);
                current.position(position + maxbytes);
                bytesConsumed = consumed = Math.addExact(consumed, maxbytes);
            }
            long size = streamSize;
            produced = bytesProduced;
            producerUnblocked = producerWasBlocked && !producerBlocked();
            if (StreamWriterQueue.class.desiredAssertionStatus()) {
                assert consumed <= produced
                        : "consumed: " + consumed + ", produced: " + produced + ", size: " + size;
                assert size == -1 || consumed <= size
                        : "consumed: " + consumed + ", produced: " + produced + ", size: " + size;
                assert size == -1 || produced <= size
                        : "consumed: " + consumed + ", produced: " + produced + ", size: " + size;
            }
            if (size >= 0 && consumed == size) {
                switchState(SendingStreamState.DATA_SENT);
            }
        } finally {
            unlock();
        }
        if (producerUnblocked) {
            debug().log("producer unblocked produced:%s, consumed:%s",
                    produced, consumed);
            wakeupProducer();
        }
        return buffer;
    }

    /**
     * Updates the flow control credit for this queue.
     * The maximum offset that will be accepted by the consumer
     * can only increase. Value that are less or equal to the
     * current value of the max stream data are ignored.
     *
     * @implSpec
     * If the consumer was blocked due to flow control before
     * this method was called, and the new value of the max
     * stream data allows to unblock the consumer, and data
     * is available, {@link #wakeupConsumer()} is called.
     *
     * @param data the maximum offset that will be accepted by
     *             the consumer
     * @return the maximum offset that will be accepted by the
     *   consumer.
     */
    public final long setMaxStreamData(long data) {
        assert data >= 0 : "maxStreamData: " + data;
        long max, produced, consumed;
        boolean consumerWasBlocked, consumerUnblocked;
        lock();
        try {
            max = maxStreamData;
            consumed = bytesConsumed;
            produced = bytesProduced;
            consumerWasBlocked = consumerBlocked();
            if (data <= max) return max;
            maxStreamData = max = data;
            consumerUnblocked = consumerWasBlocked && !consumerBlocked();
            if (StreamWriterQueue.class.desiredAssertionStatus()) {
                long size = streamSize;
                assert consumed <= produced;
                assert size == -1 || consumed <= size;
                assert size == -1 || produced <= size;
            }
        } finally {
            unlock();
        }
        debug().log("set max stream data: %s", max);
        if (consumerUnblocked && produced > 0) {
            debug().log("consumer unblocked produced:%s, consumed:%s, max stream data:%s",
                    produced, consumed, max);
            wakeupConsumer();
        }
        return max;
    }

    /**
     * Whether the producer is blocked due to flow control.
     *
     * @return whether the producer is blocked due to full buffers
     */
    public final boolean producerBlocked() {
        return producerCredit() <= 0;
    }

    /**
     * Whether the consumer is blocked due to flow control.
     *
     * @return whether the producer is blocked due to flow control
     */
    public final boolean consumerBlocked() {
        return consumerCredit() <= 0;
    }

    /**
     * {@return the offset of the data consumed by the consumer}
     *
     * @apiNote
     * The returned value is only weakly consistent: it is subject
     * to race conditions if {@link #poll(int)} is called concurrently
     * by another thread.
     */
    public final long bytesConsumed() {
        return bytesConsumed;
    }

    /**
     * {@return the offset of the data provided by the producer}
     *
     * @apiNote
     * The returned value is only weakly consistent: it is subject
     * to race conditions if {@link #submit(ByteBuffer, boolean)}
     * or {@link #queue(ByteBuffer)} are called concurrently
     * by another thread.
     */
    public final long bytesProduced() {
        return bytesProduced;
    }

    /**
     * {@return the amount of produced data which has not been consumed yet}
     * This is independent of flow control.
     *
     * @apiNote
     * The returned value is only weakly consistent: it is subject
     * to race conditions if {@link #submit(ByteBuffer, boolean)}
     * or {@link #queue(ByteBuffer)} or
     * {@link #poll(int)} are called concurrently
     * by another thread.
     */
    public final long available() {
        return bytesProduced - bytesConsumed;
    }

    /**
     * {@return the stream size if known, {@code -1} otherwise}
     *
     * @apiNote
     * The returned value is only weakly consistent: it is subject
     * to race conditions if {@link #submit(ByteBuffer, boolean)}
     * is called concurrently by another thread.
     */
    public final long streamSize() {
        return streamSize;
    }

    /**
     * {@return the maximum offset that the peer is prepared to accept}
     *
     * @apiNote
     * The returned value is only weakly consistent: it is subject
     * to race conditions if {@link #setMaxStreamData(long)} is called
     * concurrently by another thread.
     */
    public final long maxStreamData() {
        return maxStreamData;
    }

    /**
     * {@return {@code true} if the consumer has reached the end of
     * this stream (equivalent to EOF)}
     * This is independent of flow control.
     *
     * @apiNote
     * The returned value is only weakly consistent: it is subject
     * to race conditions if {@link #submit(ByteBuffer, boolean)}
     * or {@link #poll(int)} are called concurrently
     * by another thread.
     */
    public final boolean isConsumerDone() {
        long size = streamSize;
        long consumed = bytesConsumed;
        assert size == -1 || size >= consumed;
        return size >= 0 && size <= consumed;
    }

    /**
     * {@return {@code true} if the producer has reached the end of
     * this stream (equivalent to EOF)}
     * This is independent of flow control.
     *
     * @apiNote
     * The returned value is only weakly consistent: it is subject
     * to race conditions if {@link #submit(ByteBuffer, boolean)}
     * is called concurrently by another thread.
     */
    public final boolean isProducerDone() {
        return streamSize >= 0;
    }

    /**
     * This method is called by the producer to submit data to this
     * stream. The producer should not modify the provided buffer
     * after this point. The provided buffer will be queued even if
     * the produced data exceeds the maximum offset that the peer
     * is prepared to accept.
     *
     * @apiNote
     * If sufficient credit is available, this method will wake
     * up the consumer.
     *
     * @param buffer a buffer containing data for the stream
     * @param last whether this is the last buffer that will ever be
     *             provided by the provided
     * @throws IOException if the stream was reset by peer
     * @throws IllegalStateException if the last data was submitted already
     */
    public final void submit(ByteBuffer buffer, boolean last) throws IOException {
        offer(buffer, last, false);
    }

    /**
     * This method is called by the producer to queue data to this
     * stream. The producer should not modify the provided buffer
     * after this point. The provided buffer will be queued even if
     * the produced data exceeds the maximum offset that the peer
     * is prepared to accept.
     *
     * @apiNote
     *  The consumer will not be woken, even if enough credit is
     *  available. More data should be submitted using
     *  {@link #submit(ByteBuffer, boolean)} in order to wake up the consumer.
     *
     * @param buffer a buffer containing data for the stream
     * @throws IOException if the stream was reset by peer
     * @throws IllegalStateException if the last data was submitted already
     */
    public final void queue(ByteBuffer buffer) throws IOException {
        offer(buffer, false, true);
    }

    /**
     * Queues a buffer in the writing queue.
     *
     * @param buffer      the buffer to queue
     * @param last        whether this is the last data for the stream
     * @param waitForMore whether we should wait for the next submission before
     *                    waking up the consumer
     * @throws IOException if the stream was reset by peer
     * @throws IllegalStateException if the last data was submitted already
     */
    private void offer(ByteBuffer buffer, boolean last, boolean waitForMore)
            throws IOException {
        long length = buffer.remaining();
        long consumed, produced, max;
        boolean wakeupConsumer;
        lock();
        try {
            long stopSending = this.stopSending;
            if (stopSending < 0) {
                throw new IOException("Stream %s reset by peer: errorCode %s"
                        .formatted(streamId(), 1 - stopSending));
            }
            if (resetRequested) return;
            if (streamSize >= 0) {
                throw new IllegalStateException("Too many bytes provided");
            }
            consumed = bytesConsumed;
            max = maxStreamData;
            produced = Math.addExact(bytesProduced, length);
            bytesProduced = produced;
            if (length > 0 || last) {
                // allow to queue a zero-length buffer if it's the last.
                queue.offer(buffer);
            }
            if (last) {
                streamSize = produced;
            }
            assert consumed <= produced;
            wakeupConsumer = consumed < max && consumed < produced
                    || consumed == produced && last;
        } finally {
            unlock();
        }
        if (wakeupConsumer && !waitForMore) {
            debug().log("consumer unblocked produced:%s, consumed:%s, max stream data:%s",
                    produced, consumed, max);
            wakeupConsumer();
        }
    }

    /**
     * {@return the credit of the producer}
     * @implSpec
     * this is the desired buffer size minus the amount of data already buffered.
     */
    public final long producerCredit() {
        lock();
        try {
            return BUFFER_SIZE - available();
        } finally {
            unlock();
        }
    }

    /**
     * {@return the credit of the consumer}
     * @implSpec
     * This is equivalent to {@link #maxStreamData()} - {@link #bytesConsumed()}.
     */
    public final long consumerCredit() {
        lock();
        try {
            return maxStreamData - bytesConsumed;
        } finally {
            unlock();
        }
    }

    /**
     * {@return the amount of available data that can be sent
     *  with respect to flow control in this stream}.
     *  This does not take into account the global connection
     *  flow control.
     */
    public final long readyToSend() {
        long consumed, produced, max;
        lock();
        try {
            consumed = bytesConsumed;
            max = maxStreamData;
            produced = bytesProduced;
        } finally {
            unlock();
        }
        assert max >= consumed;
        assert produced >= consumed;
        return Math.min(max - consumed, produced - consumed);
    }

    public final void markReset() {
        lock();
        try {
            resetRequested = true;
        } finally {
            unlock();
        }
    }

    final void close() {
        lock();
        try {
            bytesProduced = bytesConsumed;
            queue.clear();
            current = null;
        } finally {
            unlock();
        }
    }

    /**
     * Called when a stop sending frame is received for this stream
     * @param errorCode the error code
     */
    protected final boolean stopSending(long errorCode) {
        long stopSending;
        lock();
        try {
            if (resetRequested) return false;
            if (streamSize >= 0 && bytesConsumed == streamSize) return false;
            if ((stopSending = this.stopSending) < 0) return false;
            this.stopSending = stopSending = - (errorCode + 1);
        } finally {
            unlock();
        }
        assert stopSending < 0 && stopSending == - (errorCode + 1);
        return true;
    }

    /**
     * {@return -1 minus the error code that was supplied by the peer
     * when requesting for stop sending}
     * @apiNote a strictly negative value indicates that the stream was
     *    reset by the peer. The error code supplied by the peer
     *    can be obtained with the formula: <pre>{@code
     *    long errorCode = - (resetByPeer() + 1);
     *    }</pre>
     */
    final long resetByPeer() {
        return stopSending;
    }

    /**
     * This method is called to wake up the consumer when there is
     * credit and data available for the consumer.
     */
    protected abstract void wakeupConsumer();

    /**
     * This method is called to wake up the producer when there is
     * credit available for the producer.
     */
    protected abstract void wakeupProducer();

    /**
     * Called to switch the sending state when data has been sent.
     * @param dataSent the new state - typically {@link SendingStreamState#DATA_SENT}
     */
    protected abstract void switchState(SendingStreamState dataSent);

    /**
     * {@return the stream id this queue was created for}
     */
    protected abstract long streamId();

}
