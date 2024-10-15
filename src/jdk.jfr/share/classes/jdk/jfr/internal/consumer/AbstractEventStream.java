/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.consumer;

import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import jdk.jfr.Configuration;
import jdk.jfr.EventType;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.MetadataEvent;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.SecuritySupport;

/*
 * Purpose of this class is to simplify the implementation of
 * an event stream.
 */
public abstract class AbstractEventStream implements EventStream {
    private static final AtomicLong counter = new AtomicLong();

    private final CountDownLatch terminated = new CountDownLatch(1);
    private final Runnable flushOperation = () -> dispatcher().runFlushActions();
    @SuppressWarnings("removal")
    private final AccessControlContext accessControllerContext;
    private final StreamConfiguration streamConfiguration = new StreamConfiguration();
    private final List<Configuration> configurations;
    private final ParserState parserState = new ParserState();
    private volatile boolean closeOnComplete = true;
    private Dispatcher dispatcher;
    private boolean daemon = false;


    AbstractEventStream(@SuppressWarnings("removal") AccessControlContext acc, List<Configuration> configurations) throws IOException {
        this.accessControllerContext = Objects.requireNonNull(acc);
        this.configurations = configurations;
    }

    @Override
    public abstract void start();

    @Override
    public abstract void startAsync();

    @Override
    public abstract void close();

    protected final Dispatcher dispatcher() {
        if (streamConfiguration.hasChanged()) { // quick check
            synchronized (streamConfiguration) {
                dispatcher = new Dispatcher(streamConfiguration);
                streamConfiguration.setChanged(false);
                if (Logger.shouldLog(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG)) {
                    Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, dispatcher.toString());
                }
            }
        }
        return dispatcher;
    }

    @Override
    public final void setOrdered(boolean ordered) {
        streamConfiguration.setOrdered(ordered);
    }

    @Override
    public final void setReuse(boolean reuse) {
        streamConfiguration.setReuse(reuse);
    }

    // Only used if -Xlog:jfr+event* is specified
    public final void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    // When set to false, it becomes the callers responsibility
    // to invoke close() and clean up resources. By default,
    // the resource is cleaned up when the process() call has finished.
    public final void setCloseOnComplete(boolean closeOnComplete) {
        this.closeOnComplete = closeOnComplete;
    }

    @Override
    public final void setStartTime(Instant startTime) {
        Objects.requireNonNull(startTime, "startTime");
        synchronized (streamConfiguration) {
            if (streamConfiguration.started) {
                throw new IllegalStateException("Stream is already started");
            }
            if (startTime.isBefore(Instant.EPOCH)) {
                startTime = Instant.EPOCH;
            }
            streamConfiguration.setStartTime(startTime);
        }
    }

    @Override
    public final void setEndTime(Instant endTime) {
        Objects.requireNonNull(endTime, "endTime");
        synchronized (streamConfiguration) {
            if (streamConfiguration.started) {
                throw new IllegalStateException("Stream is already started");
            }
            streamConfiguration.setEndTime(endTime);
        }
    }

    @Override
    public final void onEvent(Consumer<RecordedEvent> action) {
        Objects.requireNonNull(action, "action");
        streamConfiguration.addEventAction(action);
    }

    @Override
    public final void onEvent(String eventName, Consumer<RecordedEvent> action) {
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(action, "action");
        streamConfiguration.addEventAction(eventName, action);
    }

    @Override
    public final void onFlush(Runnable action) {
        Objects.requireNonNull(action, "action");
        streamConfiguration.addFlushAction(action);
    }

    @Override
    public final void onClose(Runnable action) {
        Objects.requireNonNull(action, "action");
        streamConfiguration.addCloseAction(action);
    }

    @Override
    public final void onError(Consumer<Throwable> action) {
        Objects.requireNonNull(action, "action");
        streamConfiguration.addErrorAction(action);
    }

    @Override
    public final boolean remove(Object action) {
        Objects.requireNonNull(action, "action");
        return streamConfiguration.remove(action);
    }

    @Override
    public final void awaitTermination() throws InterruptedException {
        awaitTermination(Duration.ofMillis(0));
    }

    @Override
    public final void awaitTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        long nanos;
        try {
            nanos = timeout.toNanos();
        } catch (ArithmeticException a) {
            nanos = Long.MAX_VALUE;
        }
        if (nanos == 0) {
            terminated.await();
        } else {
            terminated.await(nanos, TimeUnit.NANOSECONDS);
        }
    }

    protected abstract void process() throws IOException;

    protected abstract boolean isRecordingStream();

    protected final void closeParser() {
        parserState.close();
    }

    protected final boolean isClosed() {
        return parserState.isClosed();
    }

    protected final ParserState parserState() {
        return parserState;
    }

    public final void startAsync(long startNanos) {
        startInternal(startNanos);
        Runnable r = () -> run(accessControllerContext);
        Thread thread = SecuritySupport.createThreadWitNoPermissions(nextThreadName(), r);
        SecuritySupport.setDaemonThread(thread, daemon);
        thread.start();
    }

    public final void start(long startNanos) {
        startInternal(startNanos);
        run(accessControllerContext);
    }

    protected final Runnable getFlushOperation() {
        return flushOperation;
    }


    protected final void onFlush() {
       Runnable r = getFlushOperation();
       if (r != null) {
           r.run();
       }
    }

    private void startInternal(long startNanos) {
        synchronized (streamConfiguration) {
            if (streamConfiguration.started) {
                throw new IllegalStateException("Event stream can only be started once");
            }
            if (isRecordingStream() && streamConfiguration.startTime == null) {
                streamConfiguration.setStartNanos(startNanos);
            }
            streamConfiguration.setStarted(true);
        }
    }

    private void execute() {
        try {
            process();
        } catch (IOException ioe) {
            // This can happen if a chunk file is removed, or
            // a file is access that has been closed
            // This is "normal" behavior for streaming and the
            // stream will be closed when this happens.
        } finally {
            Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "Execution of stream ended.");
            try {
                if (closeOnComplete) {
                    close();
                }
            } finally {
                terminated.countDown();
            }
        }
    }

    @SuppressWarnings("removal")
    private void run(AccessControlContext accessControlContext) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                execute();
                return null;
            }
        }, accessControlContext);
    }

    private String nextThreadName() {
        return "JFR Event Stream " + counter.incrementAndGet();
    }

    @Override
    public void onMetadata(Consumer<MetadataEvent> action) {
        Objects.requireNonNull(action, "action");
        synchronized (streamConfiguration) {
            if (streamConfiguration.started) {
                throw new IllegalStateException("Stream is already started");
            }
        }
        streamConfiguration.addMetadataAction(action);
    }

    protected final void onMetadata(ChunkParser parser) {
        if (parser.hasStaleMetadata()) {
            if (dispatcher.hasMetadataHandler()) {
                List<EventType> ce = parser.getEventTypes();
                List<EventType> pe = parser.getPreviousEventTypes();
                if (ce != pe) {
                    MetadataEvent me = JdkJfrConsumer.instance().newMetadataEvent(pe, ce, configurations);
                    dispatcher.runMetadataActions(me);
                }
                parser.setStaleMetadata(false);
            }
        }
    }
}
