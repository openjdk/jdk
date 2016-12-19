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
package jdk.incubator.http.internal.common;


import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class AsyncWriteQueue implements Closeable {

    private static final int IDLE    = 0;     // nobody is flushing from the queue
    private static final int FLUSHING = 1;    // there is the only thread flushing from the queue
    private static final int REFLUSHING = 2;  // while one thread was flushing from the queue
                                              // the other thread put data into the queue.
                                              // flushing thread should recheck queue before switching to idle state.
    private static final int DELAYED = 3;     // flushing is delayed
                                              // either by PlainHttpConnection.WriteEvent registration, or
                                              // SSL handshaking

    private static final int CLOSED = 4;      // queue is closed

    private final AtomicInteger state = new AtomicInteger(IDLE);
    private final Deque<ByteBufferReference[]> queue = new ConcurrentLinkedDeque<>();
    private final BiConsumer<ByteBufferReference[], AsyncWriteQueue> consumeAction;

    // Queue may be processed in two modes:
    // 1. if(!doFullDrain) - invoke callback on each chunk
    // 2. if(doFullDrain)  - drain the whole queue, merge all chunks into the single array and invoke callback
    private final boolean doFullDrain;

    private ByteBufferReference[] delayedElement = null;

    public AsyncWriteQueue(BiConsumer<ByteBufferReference[], AsyncWriteQueue> consumeAction) {
        this(consumeAction, true);
    }

    public AsyncWriteQueue(BiConsumer<ByteBufferReference[], AsyncWriteQueue> consumeAction, boolean doFullDrain) {
        this.consumeAction = consumeAction;
        this.doFullDrain = doFullDrain;
    }

    public void put(ByteBufferReference[] e) throws IOException {
        ensureOpen();
        queue.addLast(e);
    }

    public void putFirst(ByteBufferReference[] e) throws IOException {
        ensureOpen();
        queue.addFirst(e);
    }

    /**
     * retruns true if flushing was performed
     * @return
     * @throws IOException
     */
    public boolean flush() throws IOException {
        while(true) {
            switch (state.get()) {
                case IDLE:
                    if(state.compareAndSet(IDLE, FLUSHING)) {
                        flushLoop();
                        return true;
                    }
                    break;
                case FLUSHING:
                    if(state.compareAndSet(FLUSHING, REFLUSHING)) {
                        return false;
                    }
                    break;
                case REFLUSHING:
                case DELAYED:
                    return false;
                case CLOSED:
                    throw new IOException("Queue closed");
            }
        }
    }

    /*
     *  race invocations of flushDelayed are not allowed.
     *  flushDelayed should be invoked only from:
     *   - SelectorManager thread
     *   - Handshaking thread
     */
    public void flushDelayed() throws IOException {
        ensureOpen();
        if(!state.compareAndSet(DELAYED, FLUSHING)) {
            ensureOpen(); // if CAS failed when close was set - throw proper exception
            throw new RuntimeException("Shouldn't happen");
        }
        flushLoop();
    }

    private ByteBufferReference[] drain(ByteBufferReference[] prev) {
        assert prev != null;
        if(doFullDrain) {
            ByteBufferReference[] next = queue.poll();
            if(next == null) {
                return prev;
            }
            List<ByteBufferReference> drained = new ArrayList<>();
            drained.addAll(Arrays.asList(prev));
            drained.addAll(Arrays.asList(next));
            while ((next = queue.poll()) != null) {
                drained.addAll(Arrays.asList(next));
            }
            return drained.toArray(new ByteBufferReference[0]);
        } else {
            return prev;
        }
    }

    private ByteBufferReference[] drain() {
        ByteBufferReference[] next = queue.poll();
        return next == null ? null : drain(next);
    }

    private void flushLoop() throws IOException {
        ByteBufferReference[] element;
        if (delayedElement != null) {
            element = drain(delayedElement);
            delayedElement = null;
        } else {
            element = drain();
        }
        while(true) {
            while (element != null) {
                consumeAction.accept(element, this);
                if (state.get() == DELAYED) {
                    return;
                }
                element = drain();
            }
            switch (state.get()) {
                case IDLE:
                case DELAYED:
                    throw new RuntimeException("Shouldn't happen");
                case FLUSHING:
                    if(state.compareAndSet(FLUSHING, IDLE)) {
                        return;
                    }
                    break;
                case REFLUSHING:
                    // We need to check if new elements were put after last poll() and do graceful exit
                    state.compareAndSet(REFLUSHING, FLUSHING);
                    break;
                case CLOSED:
                    throw new IOException("Queue closed");
            }
            element = drain();
        }
    }

    /*
     * The methods returns unprocessed chunk of buffers into beginning of the queue.
     * Invocation of the method allowed only inside consume callback,
     * and consume callback is invoked only when the queue in FLUSHING or REFLUSHING state.
     */
    public void setDelayed(ByteBufferReference[] delayedElement) throws IOException {
        while(true) {
            int state = this.state.get();
            switch (state) {
                case IDLE:
                case DELAYED:
                    throw new RuntimeException("Shouldn't happen");
                case FLUSHING:
                case REFLUSHING:
                    if(this.state.compareAndSet(state, DELAYED)) {
                        this.delayedElement = delayedElement;
                        return;
                    }
                    break;
                case CLOSED:
                    throw new IOException("Queue closed");
            }
        }

    }

    private void ensureOpen() throws IOException {
        if (state.get() == CLOSED) {
            throw new IOException("Queue closed");
        }
    }

    @Override
    public void close() throws IOException {
        state.getAndSet(CLOSED);
    }

}
