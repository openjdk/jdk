/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;
import java.util.function.Supplier;
import java.net.http.HttpRequest.BodyPublisher;
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
                synchronized(this) {
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
     * <p>
     * Privileged actions are performed within a limited doPrivileged that only
     * asserts the specific, read, file permission that was checked during the
     * construction of this FilePublisher. This only applies if the file system
     * that created the file provides interoperability with {@code java.io.File}.
     */
    public static class FilePublisher implements BodyPublisher {

        private final Path path;
        private final long length;
        private final Function<Path, InputStream> inputStreamSupplier;

        private static String pathForSecurityCheck(Path path) {
            return path.toFile().getPath();
        }

        /**
         * Factory for creating FilePublisher.
         *
         * Permission checks are performed here before construction of the
         * FilePublisher. Permission checking and construction are deliberately
         * and tightly co-located.
         */
        public static FilePublisher create(Path path)
                throws FileNotFoundException {
            SecurityManager sm = System.getSecurityManager();
            FilePermission filePermission = null;
            boolean defaultFS = true;

            try {
                String fn = pathForSecurityCheck(path);
                if (sm != null) {
                    FilePermission readPermission = new FilePermission(fn, "read");
                    sm.checkPermission(readPermission);
                    filePermission = readPermission;
                }
            } catch (UnsupportedOperationException uoe) {
                defaultFS = false;
                // Path not associated with the default file system
                // Test early if an input stream can still be obtained
                try {
                    if (sm != null) {
                        Files.newInputStream(path).close();
                    }
                } catch (IOException ioe) {
                    if (ioe instanceof FileNotFoundException) {
                        throw (FileNotFoundException) ioe;
                    } else {
                        var ex = new FileNotFoundException(ioe.getMessage());
                        ex.initCause(ioe);
                        throw ex;
                    }
                }
            }

            // existence check must be after permission checks
            if (Files.notExists(path))
                throw new FileNotFoundException(path + " not found");

            Permission perm = filePermission;
            assert perm == null || perm.getActions().equals("read");
            AccessControlContext acc = sm != null ?
                    AccessController.getContext() : null;
            boolean finalDefaultFS = defaultFS;
            Function<Path, InputStream> inputStreamSupplier = (p) ->
                    createInputStream(p, acc, perm, finalDefaultFS);

            long length;
            try {
                length = Files.size(path);
            } catch (IOException ioe) {
                length = -1;
            }

            return new FilePublisher(path, length, inputStreamSupplier);
        }

        private static InputStream createInputStream(Path path,
                                                     AccessControlContext acc,
                                                     Permission perm,
                                                     boolean defaultFS) {
            try {
                if (acc != null) {
                    PrivilegedExceptionAction<InputStream> pa = defaultFS
                            ? () -> new FileInputStream(path.toFile())
                            : () -> Files.newInputStream(path);
                    return perm != null
                            ? AccessController.doPrivileged(pa, acc, perm)
                            : AccessController.doPrivileged(pa, acc);
                } else {
                    return defaultFS
                            ? new FileInputStream(path.toFile())
                            : Files.newInputStream(path);
                }
            } catch (PrivilegedActionException pae) {
                throw toUncheckedException(pae.getCause());
            } catch (IOException io) {
                throw new UncheckedIOException(io);
            }
        }

        private static RuntimeException toUncheckedException(Throwable t) {
            if (t instanceof RuntimeException)
                throw (RuntimeException) t;
            if (t instanceof Error)
                throw (Error) t;
            if (t instanceof IOException)
                throw new UncheckedIOException((IOException) t);
            throw new UndeclaredThrowableException(t);
        }

        private FilePublisher(Path name,
                              long length,
                              Function<Path, InputStream> inputStreamSupplier) {
            path = name;
            this.length = length;
            this.inputStreamSupplier = inputStreamSupplier;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            InputStream is = null;
            Throwable t = null;
            try {
                is = inputStreamSupplier.apply(path);
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

        private int read() {
            if (eof)
                return -1;
            nextBuffer = bufSupplier.get();
            nextBuffer.clear();
            byte[] buf = nextBuffer.array();
            int offset = nextBuffer.arrayOffset();
            int cap = nextBuffer.capacity();
            try {
                int n = is.read(buf, offset, cap);
                if (n == -1) {
                    eof = true;
                    is.close();
                    return -1;
                }
                //flip
                nextBuffer.limit(n);
                nextBuffer.position(0);
                return n;
            } catch (IOException ex) {
                return -1;
            }
        }

        @Override
        public synchronized boolean hasNext() {
            if (need2Read) {
                haveNext = read() != -1;
                if (haveNext) {
                    need2Read = false;
                }
                return haveNext;
            }
            return haveNext;
        }

        @Override
        public synchronized ByteBuffer next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            need2Read = true;
            return nextBuffer;
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
}
