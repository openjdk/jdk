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

import jdk.incubator.http.internal.frame.DataFrame;
import jdk.incubator.http.internal.frame.Http2Frame;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Http2Frame Producer-Consumer queue which either allows to consume all frames in blocking way
 * or allows to consume it asynchronously. In the latter case put operation from the producer thread
 * executes consume operation in the given executor.
 */
public class AsyncDataReadQueue implements Closeable {

    @FunctionalInterface
    public interface DataConsumer {
        /**
         *
         * @param t - frame
         * @return true if consuming should be continued. false when END_STREAM was received.
         * @throws Throwable
         */
        boolean accept(Http2Frame t) throws Throwable;
    }

    private static final int BLOCKING = 0;
    private static final int FLUSHING = 1;
    private static final int REFLUSHING = 2;
    private static final int ASYNC  = 3;
    private static final int CLOSED = 4;


    private final AtomicInteger state = new AtomicInteger(BLOCKING);
    private final BlockingQueue<Http2Frame> queue = new LinkedBlockingQueue<>();
    private Executor executor;
    private DataConsumer onData;
    private Consumer<Throwable> onError;

    public AsyncDataReadQueue() {
    }

    public boolean tryPut(Http2Frame f) {
        if(state.get() == CLOSED) {
            return false;
        } else {
            queue.offer(f);
            flushAsync(false);
            return true;
        }
    }

    public void put(Http2Frame f) throws IOException {
        if(!tryPut(f))
            throw new IOException("stream closed");
    }

    public void blockingReceive(DataConsumer onData, Consumer<Throwable> onError) {
        if (state.get() == CLOSED) {
            onError.accept(new IOException("stream closed"));
            return;
        }
        assert state.get() == BLOCKING;
        try {
            while (onData.accept(queue.take()));
            assert state.get() == CLOSED;
        } catch (Throwable e) {
            onError.accept(e);
        }
    }

    public void asyncReceive(Executor executor, DataConsumer onData,
                             Consumer<Throwable> onError) {
        if (state.get() == CLOSED) {
            onError.accept(new IOException("stream closed"));
            return;
        }

        assert state.get() == BLOCKING;

        // Validates that fields not already set.
        if (!checkCanSet("executor", this.executor, onError)
            || !checkCanSet("onData", this.onData, onError)
            || !checkCanSet("onError", this.onError, onError)) {
            return;
        }

        this.executor = executor;
        this.onData = onData;
        this.onError = onError;

        // This will report an error if asyncReceive is called twice,
        // because we won't be in BLOCKING state if that happens
        if (!this.state.compareAndSet(BLOCKING, ASYNC)) {
            onError.accept(new IOException(
                  new IllegalStateException("State: "+this.state.get())));
            return;
        }

        flushAsync(true);
    }

    private static <T> boolean checkCanSet(String name, T oldval, Consumer<Throwable> onError) {
        if (oldval != null) {
            onError.accept(new IOException(
                     new IllegalArgumentException(name)));
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        int prevState = state.getAndSet(CLOSED);
        if(prevState == BLOCKING) {
            // wake up blocked take()
            queue.offer(new DataFrame(0, DataFrame.END_STREAM, new ByteBufferReference[0]));
        }
    }

    private void flushAsync(boolean alreadyInExecutor) {
        while(true) {
            switch (state.get()) {
                case BLOCKING:
                case CLOSED:
                case REFLUSHING:
                    return;
                case ASYNC:
                    if(state.compareAndSet(ASYNC, FLUSHING)) {
                        if(alreadyInExecutor) {
                            flushLoop();
                        } else {
                            executor.execute(this::flushLoop);
                        }
                        return;
                    }
                    break;
                case FLUSHING:
                    if(state.compareAndSet(FLUSHING, REFLUSHING)) {
                        return;
                    }
                    break;
            }
        }
    }

    private void flushLoop() {
        try {
            while(true) {
                Http2Frame frame = queue.poll();
                while (frame != null) {
                    if(!onData.accept(frame)) {
                        assert state.get() == CLOSED;
                        return; // closed
                    }
                    frame = queue.poll();
                }
                switch (state.get()) {
                    case BLOCKING:
                        assert false;
                        break;
                    case ASYNC:
                        throw new RuntimeException("Shouldn't happen");
                    case FLUSHING:
                        if(state.compareAndSet(FLUSHING, ASYNC)) {
                            return;
                        }
                        break;
                    case REFLUSHING:
                        // We need to check if new elements were put after last
                        // poll() and do graceful exit
                        state.compareAndSet(REFLUSHING, FLUSHING);
                        break;
                    case CLOSED:
                        return;
                }
            }
        } catch (Throwable e) {
            onError.accept(e);
            close();
        }
    }
}
