/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.http.HttpRequest.BodyPublisher;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import jdk.internal.net.http.common.Demand;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;

public final class RequestPublishers {

    private RequestPublishers() { }

    public static class ByteArrayPublisher implements BodyPublisher {
        private final int length;
        private final byte[] content;
        private final int offset;
        private final int bufSize;

        public ByteArrayPublisher(byte[] content) {
            this(content, 0, content.length);
        }

        public ByteArrayPublisher(byte[] content, int offset, int length) {
            this(content, offset, length, Utils.BUFSIZE);
        }

        /* bufSize exposed for testing purposes */
        ByteArrayPublisher(byte[] content, int offset, int length, int bufSize) {
            this.content = content;
            this.offset = offset;
            this.length = length;
            this.bufSize = bufSize;
        }

        List<ByteBuffer> copy(byte[] content, int offset, int length) {
            List<ByteBuffer> bufs = new ArrayList<>();
            while (length > 0) {
                ByteBuffer b = ByteBuffer.allocate(Math.min(bufSize, length));
                int max = b.capacity();
                int tocopy = Math.min(max, length);
                b.put(content, offset, tocopy);
                offset += tocopy;
                length -= tocopy;
                b.flip();
                bufs.add(b);
            }
            return bufs;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            List<ByteBuffer> copy = copy(content, offset, length);
            var delegate = new PullPublisher<>(copy);
            delegate.subscribe(subscriber);
        }

        @Override
        public long contentLength() {
            return length;
        }
    }

    // This implementation has lots of room for improvement.
    public static class IterablePublisher implements BodyPublisher {
        private final Iterable<byte[]> content;
        private volatile long contentLength;

        public IterablePublisher(Iterable<byte[]> content) {
            this.content = Objects.requireNonNull(content);
        }

        // The ByteBufferIterator will iterate over the byte[] arrays in
        // the content one at the time.
        //
        class ByteBufferIterator implements Iterator<ByteBuffer> {
            final ConcurrentLinkedQueue<ByteBuffer> buffers = new ConcurrentLinkedQueue<>();
            final Iterator<byte[]> iterator = content.iterator();
            @Override
            public boolean hasNext() {
                return !buffers.isEmpty() || iterator.hasNext();
            }

            @Override
            public ByteBuffer next() {
                ByteBuffer buffer = buffers.poll();
                while (buffer == null) {
                    copy();
                    buffer = buffers.poll();
                }
                return buffer;
            }

            ByteBuffer getBuffer() {
                return Utils.getBuffer();
            }

            void copy() {
                byte[] bytes = iterator.next();
                int length = bytes.length;
                if (length == 0 && iterator.hasNext()) {
                    // avoid inserting empty buffers, except
                    // if that's the last.
                    return;
                }
                int offset = 0;
                do {
                    ByteBuffer b = getBuffer();
                    int max = b.capacity();

                    int tocopy = Math.min(max, length);
                    b.put(bytes, offset, tocopy);
                    offset += tocopy;
                    length -= tocopy;
                    b.flip();
                    buffers.add(b);
                } while (length > 0);
            }
        }

        public Iterator<ByteBuffer> iterator() {
            return new ByteBufferIterator();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            Iterable<ByteBuffer> iterable = this::iterator;
            var delegate = new PullPublisher<>(iterable);
            delegate.subscribe(subscriber);
        }

        static long computeLength(Iterable<byte[]> bytes) {
            // Avoid iterating just for the purpose of computing
            // a length, in case iterating is a costly operation
            // For HTTP/1.1 it means we will be using chunk encoding
            // when sending the request body.
            // For HTTP/2 it means we will not send the optional
            // Content-length header.
            return -1;
        }

        @Override
        public long contentLength() {
            if (contentLength == 0) {
                synchronized (this) {
                    if (contentLength == 0) {
                        contentLength = computeLength(content);
                    }
                }
            }
            return contentLength;
        }
    }

    public static class StringPublisher extends ByteArrayPublisher {
        public StringPublisher(String content, Charset charset) {
            super(content.getBytes(charset));
        }
    }

    public static class EmptyPublisher implements BodyPublisher {
        private final Flow.Publisher<ByteBuffer> delegate =
                new PullPublisher<ByteBuffer>(Collections.emptyList(), null);

        @Override
        public long contentLength() {
            return 0;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            delegate.subscribe(subscriber);
        }
    }

    /**
     * Publishes the content of a given file.
     */
    public static class FilePublisher implements BodyPublisher {

        private final Path path;
        private final long length;

        /**
         * Factory for creating FilePublisher.
         */
        public static FilePublisher create(Path path)
                throws FileNotFoundException {

            if (Files.notExists(path))
                throw new FileNotFoundException(path + " not found");

            long length;
            try {
                length = Files.size(path);
            } catch (IOException ioe) {
                length = -1;
            }

            return new FilePublisher(path, length);
        }

        private FilePublisher(Path name, long length) {
            path = name;
            this.length = length;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            InputStream is = null;
            Throwable t = null;
            try {
                // Throw `FileNotFoundException` to match the specification of `BodyPublishers::ofFile
                if (!Files.isRegularFile(path)) {
                    throw new FileNotFoundException(path + " (Not a regular file)");
                }
                is = Files.newInputStream(path);
            } catch (NoSuchFileException nsfe) {
                // Throw `FileNotFoundException` to match the specification of `BodyPublishers::ofFile`
                t = new FileNotFoundException(path + " (No such file or directory)");
            } catch (UncheckedIOException | UndeclaredThrowableException ue) {
                t = ue.getCause();
            } catch (Throwable th) {
                t = th;
            }
            final InputStream fis = is;
            PullPublisher<ByteBuffer> publisher;
            if (t == null) {
                publisher = new PullPublisher<>(() -> new StreamIterator(fis));
            } else {
                publisher = new PullPublisher<>(null, t);
            }
            publisher.subscribe(subscriber);
        }

        @Override
        public long contentLength() {
            return length;
        }
    }

    /**
     * Reads one buffer ahead all the time, blocking in hasNext()
     */
    public static class StreamIterator implements Iterator<ByteBuffer> {
        final InputStream is;
        final Supplier<? extends ByteBuffer> bufSupplier;
        private volatile boolean eof;
        volatile ByteBuffer nextBuffer;
        volatile boolean need2Read = true;
        volatile boolean haveNext;
        final ReentrantLock stateLock = new ReentrantLock();

        StreamIterator(InputStream is) {
            this(is, Utils::getBuffer);
        }

        StreamIterator(InputStream is, Supplier<? extends ByteBuffer> bufSupplier) {
            this.is = is;
            this.bufSupplier = bufSupplier;
        }

//        Throwable error() {
//            return error;
//        }

        private int read() throws IOException {
            if (eof)
                return -1;
            nextBuffer = bufSupplier.get();
            nextBuffer.clear();
            byte[] buf = nextBuffer.array();
            int offset = nextBuffer.arrayOffset();
            int cap = nextBuffer.capacity();
            int n = is.read(buf, offset, cap);
            if (n == -1) {
                eof = true;
                return -1;
            }
            //flip
            nextBuffer.limit(n);
            nextBuffer.position(0);
            return n;
        }

        /**
         * Close stream in this instance.
         * UncheckedIOException may be thrown if IOE happens at InputStream::close.
         */
        private void closeStream() {
            try {
                is.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean hasNext() {
            stateLock.lock();
            try {
                return hasNext0();
            } finally {
                stateLock.unlock();
            }
        }

        private boolean hasNext0() {
            if (need2Read) {
                try {
                    haveNext = read() != -1;
                    if (haveNext) {
                        need2Read = false;
                    }
                } catch (IOException e) {
                    haveNext = false;
                    need2Read = false;
                    throw new UncheckedIOException(e);
                } finally {
                    if (!haveNext) {
                        closeStream();
                    }
                }
            }
            return haveNext;
        }

        @Override
        public ByteBuffer next() {
            stateLock.lock();
            try {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                need2Read = true;
                return nextBuffer;
            } finally {
                stateLock.unlock();
            }
        }

    }

    public static class InputStreamPublisher implements BodyPublisher {
        private final Supplier<? extends InputStream> streamSupplier;

        public InputStreamPublisher(Supplier<? extends InputStream> streamSupplier) {
            this.streamSupplier = Objects.requireNonNull(streamSupplier);
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            PullPublisher<ByteBuffer> publisher;
            InputStream is = streamSupplier.get();
            if (is == null) {
                Throwable t = new IOException("streamSupplier returned null");
                publisher = new PullPublisher<>(null, t);
            } else  {
                publisher = new PullPublisher<>(iterableOf(is), null);
            }
            publisher.subscribe(subscriber);
        }

        protected Iterable<ByteBuffer> iterableOf(InputStream is) {
            return () -> new StreamIterator(is);
        }

        @Override
        public long contentLength() {
            return -1;
        }
    }

    public static final class PublisherAdapter implements BodyPublisher {

        private final Publisher<? extends ByteBuffer> publisher;
        private final long contentLength;

        public PublisherAdapter(Publisher<? extends ByteBuffer> publisher,
                         long contentLength) {
            this.publisher = Objects.requireNonNull(publisher);
            this.contentLength = contentLength;
        }

        @Override
        public final long contentLength() {
            return contentLength;
        }

        @Override
        public final void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            publisher.subscribe(subscriber);
        }
    }


    public static BodyPublisher concat(BodyPublisher... publishers) {
        if (publishers.length == 0) {
            return new EmptyPublisher();
        } else if (publishers.length == 1) {
            return Objects.requireNonNull(publishers[0]);
        } else {
            return new AggregatePublisher(List.of(publishers));
        }
    }

    /**
     * An aggregate publisher acts as a proxy between a subscriber
     * and a list of publishers. It lazily subscribes to each publisher
     * in sequence in order to publish a request body that is
     * composed from all the bytes obtained from each publisher.
     * For instance, the following two publishers are equivalent, even
     * though they may result in a different count of {@code onNext}
     * invocations.
     * <pre>{@code
     *   var bp1 = BodyPublishers.ofString("ab");
     *   var bp2 = BodyPublishers.concat(BodyPublishers.ofString("a"),
     *                                   BodyPublisher.ofByteArray(new byte[] {(byte)'b'}));
     * }</pre>
     *
     */
    private static final class AggregatePublisher implements BodyPublisher {
        final List<BodyPublisher> bodies;
        AggregatePublisher(List<BodyPublisher> bodies) {
            this.bodies = bodies;
        }

        // -1 must be returned if any publisher returns -1
        // Otherwise, we can just sum the contents.
        @Override
        public long contentLength() {
            long length =  bodies.stream()
                    .mapToLong(BodyPublisher::contentLength)
                    .reduce((a,b) -> a < 0 || b < 0 ? -1 : a + b)
                    .orElse(0);
            // In case of overflow in any operation but the last, length
            // will be -1.
            // In case of overflow in the last reduce operation, length
            // will be negative, but not necessarily -1: in that case,
            // return -1
            if (length < 0) return -1;
            return length;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new AggregateSubscription(bodies, subscriber));
        }
    }

    private static final class AggregateSubscription
            implements Flow.Subscription, Flow.Subscriber<ByteBuffer> {
        final Flow.Subscriber<? super ByteBuffer> subscriber; // upstream
        final Queue<BodyPublisher> bodies;
        final SequentialScheduler scheduler;
        final Demand demand = new Demand(); // from upstream
        final Demand demanded = new Demand(); // requested downstream
        final AtomicReference<Throwable> error = new AtomicReference<>();
        volatile Throwable illegalRequest;
        volatile BodyPublisher publisher; // downstream
        volatile Flow.Subscription subscription; // downstream
        volatile boolean cancelled;
        AggregateSubscription(List<BodyPublisher> bodies, Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.bodies = new ConcurrentLinkedQueue<>(bodies);
            this.subscriber = subscriber;
            this.scheduler = SequentialScheduler.lockingScheduler(this::run);
        }

        @Override
        public void request(long n) {
            synchronized (this) {
                // We are finished when publisher is null and bodies
                // is empty. This means that the data from the last
                // publisher in the list has been consumed.
                // If we are finished or cancelled, do nothing.
                if (cancelled || (publisher == null && bodies.isEmpty())) {
                    return;
                }
            }
            try {
                demand.increase(n);
            } catch (IllegalArgumentException x) {
                // request() should not throw - the scheduler will
                // invoke onError on the subscriber.
                illegalRequest = x;
            }
            scheduler.runOrSchedule();
        }

        @Override
        public void cancel() {
            cancelled = true;
            scheduler.runOrSchedule();
        }

        private boolean cancelSubscription(Flow.Subscription subscription) {
            if (subscription != null) {
                synchronized (this) {
                    if (this.subscription == subscription) {
                        this.subscription = null;
                        this.publisher = null;
                    }
                }
                subscription.cancel();
            }
            // This method is called when cancel is true, so
            // we should always stop the scheduler here
            scheduler.stop();
            return subscription != null;
        }

        public void run() {
            try {
                BodyPublisher publisher;
                Flow.Subscription subscription = null;
                while (error.get() == null
                        && (!demand.isFulfilled()
                        || (this.publisher == null && !bodies.isEmpty()))) {
                    boolean cancelled = this.cancelled;
                    // make sure we see a consistent state.
                    synchronized (this) {
                        publisher = this.publisher;
                        subscription = this.subscription;
                    }
                    Throwable illegalRequest = this.illegalRequest;
                    if (cancelled) {
                        bodies.clear();
                        cancelSubscription(subscription);
                        return;
                    }
                    if (publisher == null && !bodies.isEmpty()) {
                        // synchronize here to avoid race condition with
                        // request(long) which could otherwise observe a
                        // null publisher and an empty bodies list when
                        // polling the last publisher.
                        synchronized (this) {
                            this.publisher = publisher = bodies.poll();
                        }
                        publisher.subscribe(this);
                    } else if (publisher == null) {
                        return;
                    }
                    if (illegalRequest != null) {
                        onError(illegalRequest);
                        return;
                    }
                    long n = 0;
                    // synchronize to avoid race condition with
                    // publisherDone()
                    synchronized (this) {
                        if ((subscription = this.subscription) == null) return;
                        if (!demand.isFulfilled()) {
                            n = demand.decreaseAndGet(demand.get());
                            demanded.increase(n);
                        }
                    }
                    if (n > 0 && !cancelled) {
                        subscription.request(n);
                    }
                }
            } catch (Throwable t) {
                onError(t);
            }
        }

        // It is important to synchronize when setting
        // publisher to null to avoid race conditions
        // with request(long)
        private synchronized void publisherDone() {
            publisher = null;
            subscription = null;
        }


        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            // synchronize for asserting in a consistent state.
            synchronized (this) {
                // we shouldn't be able to observe a null publisher
                // when onSubscribe is called, unless - possibly - if
                // there was some error...
                assert publisher != null || error.get() != null;
                this.subscription = subscription;
            }
            scheduler.runOrSchedule();
        }

        @Override
        public void onNext(ByteBuffer item) {
            // make sure to cancel the downstream subscription if we receive
            // an item after the aggregate subscription was cancelled or
            // an error was reported.
            if (cancelled || error.get() != null) {
                cancelSubscription(this.subscription);
                return;
            }
            demanded.tryDecrement();
            subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            if (error.compareAndSet(null, throwable)) {
                publisherDone();
                subscriber.onError(throwable);
                scheduler.stop();
            }
        }

        private synchronized boolean completeAndContinue() {
            if (publisher != null && !bodies.isEmpty()) {
                while (!demanded.isFulfilled()) {
                    demand.increase(demanded.decreaseAndGet(demanded.get()));
                }
                publisherDone();
                return true; // continue
            } else {
                publisherDone();
                return false; // stop
            }
        }

        @Override
        public void onComplete() {
            if (completeAndContinue()) {
                scheduler.runOrSchedule();
            } else {
                if (!cancelled) {
                    subscriber.onComplete();
                }
                scheduler.stop();
            }
        }
    }

}
