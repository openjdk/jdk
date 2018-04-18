/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
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
import java.util.function.Supplier;
import java.net.http.HttpRequest.BodyPublisher;
import jdk.internal.net.http.common.Utils;

public final class RequestPublishers {

    private RequestPublishers() { }

    public static class ByteArrayPublisher implements BodyPublisher {
        private volatile Flow.Publisher<ByteBuffer> delegate;
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
            this.delegate = new PullPublisher<>(copy);
            delegate.subscribe(subscriber);
        }

        @Override
        public long contentLength() {
            return length;
        }
    }

    // This implementation has lots of room for improvement.
    public static class IterablePublisher implements BodyPublisher {
        private volatile Flow.Publisher<ByteBuffer> delegate;
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
            this.delegate = new PullPublisher<>(iterable);
            delegate.subscribe(subscriber);
        }

        static long computeLength(Iterable<byte[]> bytes) {
            long len = 0;
            for (byte[] b : bytes) {
                len = Math.addExact(len, (long)b.length);
            }
            return len;
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
     *
     * Privileged actions are performed within a limited doPrivileged that only
     * asserts the specific, read, file permission that was checked during the
     * construction of this FilePublisher.
     */
    public static class FilePublisher implements BodyPublisher  {

        private static final FilePermission[] EMPTY_FILE_PERMISSIONS = new FilePermission[0];

        private final File file;
        private final FilePermission[] filePermissions;

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
        public static FilePublisher create(Path path) throws FileNotFoundException {
            FilePermission filePermission = null;
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                String fn = pathForSecurityCheck(path);
                FilePermission readPermission = new FilePermission(fn, "read");
                sm.checkPermission(readPermission);
                filePermission = readPermission;
            }

            // existence check must be after permission checks
            if (Files.notExists(path))
                throw new FileNotFoundException(path + " not found");

            return new FilePublisher(path, filePermission);
        }

        private FilePublisher(Path name, FilePermission filePermission) {
            assert filePermission != null ? filePermission.getActions().equals("read") : true;
            file = name.toFile();
            this.filePermissions = filePermission == null ? EMPTY_FILE_PERMISSIONS
                    : new FilePermission[] { filePermission };
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            InputStream is;
            if (System.getSecurityManager() == null) {
                try {
                    is = new FileInputStream(file);
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            } else {
                try {
                    PrivilegedExceptionAction<FileInputStream> pa =
                            () -> new FileInputStream(file);
                    is = AccessController.doPrivileged(pa, null, filePermissions);
                } catch (PrivilegedActionException pae) {
                    throw new UncheckedIOException((IOException) pae.getCause());
                }
            }
            PullPublisher<ByteBuffer> publisher =
                    new PullPublisher<>(() -> new StreamIterator(is));
            publisher.subscribe(subscriber);
        }

        @Override
        public long contentLength() {
            if (System.getSecurityManager() == null) {
                return file.length();
            } else {
                PrivilegedAction<Long> pa = () -> file.length();
                return AccessController.doPrivileged(pa, null, filePermissions);
            }
        }
    }

    /**
     * Reads one buffer ahead all the time, blocking in hasNext()
     */
    public static class StreamIterator implements Iterator<ByteBuffer> {
        final InputStream is;
        final Supplier<? extends ByteBuffer> bufSupplier;
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
            nextBuffer = bufSupplier.get();
            nextBuffer.clear();
            byte[] buf = nextBuffer.array();
            int offset = nextBuffer.arrayOffset();
            int cap = nextBuffer.capacity();
            try {
                int n = is.read(buf, offset, cap);
                if (n == -1) {
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
