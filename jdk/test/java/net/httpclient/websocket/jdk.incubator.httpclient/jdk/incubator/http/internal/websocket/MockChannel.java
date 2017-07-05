/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.incubator.http.internal.websocket;

import jdk.incubator.http.WebSocket.MessagePart;
import jdk.incubator.http.internal.websocket.Frame.Opcode;
import jdk.incubator.http.internal.websocket.TestSupport.F1;
import jdk.incubator.http.internal.websocket.TestSupport.F2;
import jdk.incubator.http.internal.websocket.TestSupport.InvocationChecker;
import jdk.incubator.http.internal.websocket.TestSupport.InvocationExpectation;
import jdk.incubator.http.internal.websocket.TestSupport.Mock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static jdk.incubator.http.internal.websocket.Frame.MAX_HEADER_SIZE_BYTES;

final class MockChannel implements RawChannel, Mock {

    /* Reads and writes must be able to be served concurrently, thus 2 threads */ // TODO: test this
    private final Executor executor = Executors.newFixedThreadPool(2);
    private final Object stateLock = new Object();
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private volatile boolean closed;
    private boolean isInputOpen = true;
    private boolean isOutputOpen = true;
    private final Frame.Reader reader = new Frame.Reader();
    private final MockFrameConsumer delegate;
    private final Iterator<ReadRule> readScenario;
    private ReadRule currentRule;
    private final AtomicBoolean handedOver = new AtomicBoolean();

    private MockChannel(Iterable<ReadRule> scenario,
                        Iterable<InvocationExpectation> expectations) {
        Iterator<ReadRule> iterator = scenario.iterator();
        if (!iterator.hasNext()) {
            throw new RuntimeException();
        }
        this.readScenario = iterator;
        this.currentRule = iterator.next();
        this.delegate = new MockFrameConsumer(expectations);
    }

    @Override
    public void registerEvent(RawEvent event) throws IOException {
        int ops = event.interestOps();
        if ((ops & SelectionKey.OP_WRITE) != 0) {
            synchronized (stateLock) {
                checkOpen();
                executor.execute(event::handle);
            }
        } else if ((ops & SelectionKey.OP_READ) != 0) {
            CompletionStage<?> cs;
            synchronized (readLock) {
                cs = currentRule().whenReady();
                synchronized (stateLock) {
                    checkOpen();
                    cs.thenRun(() -> executor.execute(event::handle));
                }
            }
        } else {
            throw new RuntimeException("Unexpected registration: " + ops);
        }
    }

    @Override
    public ByteBuffer initialByteBuffer() throws IllegalStateException {
        if (!handedOver.compareAndSet(false, true)) {
            throw new IllegalStateException();
        }
        return ByteBuffer.allocate(0);
    }

    @Override
    public ByteBuffer read() throws IOException {
        synchronized (readLock) {
            checkOpen();
            synchronized (stateLock) {
                if (!isInputOpen) {
                    return null;
                }
            }
            ByteBuffer r = currentRule().read();
            checkOpen();
            return r;
        }
    }

    @Override
    public long write(ByteBuffer[] src, int offset, int len) throws IOException {
        synchronized (writeLock) {
            checkOpen();
            synchronized (stateLock) {
                if (!isOutputOpen) {
                    throw new ClosedChannelException();
                }
            }
            long n = 0;
            for (int i = offset; i < offset + len && isOpen(); i++) {
                ByteBuffer b = src[i];
                int rem = src[i].remaining();
                while (b.hasRemaining() && isOpen()) {
                    reader.readFrame(b, delegate);
                }
                n += rem;
            }
            checkOpen();
            return n;
        }
    }

    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void shutdownInput() throws IOException {
        synchronized (stateLock) {
            if (!isOpen()) {
                throw new ClosedChannelException();
            }
            isInputOpen = false;
        }
    }

    @Override
    public void shutdownOutput() throws IOException {
        synchronized (stateLock) {
            if (!isOpen()) {
                throw new ClosedChannelException();
            }
            isOutputOpen = false;
        }
    }

    @Override
    public void close() {
        synchronized (stateLock) {
            closed = true;
        }
    }

    @Override
    public String toString() {
        return super.toString() + "[" + (closed ? "closed" : "open") + "]";
    }

    private ReadRule currentRule() {
        assert Thread.holdsLock(readLock);
        while (!currentRule.applies()) { // There should be the terminal rule which always applies
            currentRule = readScenario.next();
        }
        return currentRule;
    }

    private void checkOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    @Override
    public CompletableFuture<Void> expectations(long timeout, TimeUnit unit) {
        return delegate.expectations(timeout, unit);
    }

    private static class MockFrameConsumer extends FrameConsumer implements Mock {

        private final Frame.Masker masker = new Frame.Masker();

        MockFrameConsumer(Iterable<InvocationExpectation> expectations) {
            super(new MockMessageStreamConsumer(expectations));
        }

        @Override
        public void mask(boolean value) {
        }

        @Override
        public void maskingKey(int value) {
            masker.mask(value);
        }

        @Override
        public void payloadData(ByteBuffer data) {
            int p = data.position();
            int l = data.limit();
            masker.transferMasking(data, data);
//            select(p, l, data); FIXME
            super.payloadData(data);
        }

        @Override
        public CompletableFuture<Void> expectations(long timeout, TimeUnit unit) {
            return ((Mock) getOutput()).expectations(timeout, unit);
        }
    }

    private static final class MockMessageStreamConsumer implements MessageStreamConsumer, Mock {

        private final InvocationChecker checker;

        MockMessageStreamConsumer(Iterable<InvocationExpectation> expectations) {
            checker = new InvocationChecker(expectations);
        }

        @Override
        public void onText(MessagePart part, CharSequence data) {
            checker.checkInvocation("onText", part, data);
        }

        @Override
        public void onBinary(MessagePart part, ByteBuffer data) {
            checker.checkInvocation("onBinary", part, data);
        }

        @Override
        public void onPing(ByteBuffer data) {
            checker.checkInvocation("onPing", data);
        }

        @Override
        public void onPong(ByteBuffer data) {
            checker.checkInvocation("onPong", data);
        }

        @Override
        public void onClose(OptionalInt statusCode, CharSequence reason) {
            checker.checkInvocation("onClose", statusCode, reason);
        }

        @Override
        public void onError(Exception e) {
            checker.checkInvocation("onError", e);
        }

        @Override
        public void onComplete() {
            checker.checkInvocation("onComplete");
        }

        @Override
        public CompletableFuture<Void> expectations(long timeout, TimeUnit unit) {
            return checker.expectations(timeout, unit);
        }
    }

    public static final class Builder {

        private final Frame.HeaderWriter b = new Frame.HeaderWriter();
        private final List<InvocationExpectation> expectations = new LinkedList<>();
        private final List<ReadRule> scenario = new LinkedList<>();

        Builder expectPing(F1<? super ByteBuffer, Boolean> predicate) {
            InvocationExpectation e = new InvocationExpectation("onPing",
                    args -> predicate.apply((ByteBuffer) args[0]));
            expectations.add(e);
            return this;
        }

        Builder expectPong(F1<? super ByteBuffer, Boolean> predicate) {
            InvocationExpectation e = new InvocationExpectation("onPong",
                    args -> predicate.apply((ByteBuffer) args[0]));
            expectations.add(e);
            return this;
        }

        Builder expectClose(F2<? super Integer, ? super String, Boolean> predicate) {
            InvocationExpectation e = new InvocationExpectation("onClose",
                    args -> predicate.apply((Integer) args[0], (String) args[1]));
            expectations.add(e);
            return this;
        }

        Builder provideFrame(boolean fin, boolean rsv1, boolean rsv2,
                             boolean rsv3, Opcode opcode, ByteBuffer data) {

            ByteBuffer b = ByteBuffer.allocate(MAX_HEADER_SIZE_BYTES + data.remaining());
            this.b.fin(fin).rsv1(rsv1).rsv2(rsv2).rsv3(rsv3).opcode(opcode).noMask()
                    .payloadLen(data.remaining()).write(b);

            int p = data.position();
            int l = data.limit();
            b.put(data);
            b.flip();
//            select(p, l, data); FIXME

            ReadRule r = new ReadRule() {

                private volatile boolean provided;

                @Override
                public CompletionStage<?> whenReady() {
                    return NOW;
                }

                @Override
                public ByteBuffer read() throws IOException {
                    provided = true;
                    return data;
                }

                @Override
                public boolean applies() {
                    return !provided;
                }
            };
            scenario.add(r);
            return this;
        }

        Builder provideEos() {
            ReadRule r = new ReadRule() {

                @Override
                public CompletionStage<?> whenReady() {
                    return NOW;
                }

                @Override
                public ByteBuffer read() throws IOException {
                    return null;
                }

                @Override
                public boolean applies() {
                    return true;
                }
            };
            scenario.add(r);
            return this;
        }

        Builder provideException(Supplier<? extends IOException> s) {
            return this;
        }

        MockChannel build() {
            LinkedList<ReadRule> scenario = new LinkedList<>(this.scenario);
            scenario.add(new Terminator());
            return new MockChannel(scenario, new LinkedList<>(expectations));
        }
    }

    private interface ReadRule {

        /*
         * Returns a CS which when completed means `read(ByteBuffer dst)` can be
         * invoked
         */
        CompletionStage<?> whenReady();

        ByteBuffer read() throws IOException;

        /*
         * Returns true if this rule still applies, otherwise returns false
         */
        boolean applies();
    }

    public static final class Terminator implements ReadRule {

        @Override
        public CompletionStage<?> whenReady() {
            return NEVER;
        }

        @Override
        public ByteBuffer read() {
            return ByteBuffer.allocate(0);
        }

        @Override
        public boolean applies() {
            return true;
        }
    }

    private static final CompletionStage<?> NOW = CompletableFuture.completedStage(null);
    private static final CompletionStage<?> NEVER = new CompletableFuture();
}
