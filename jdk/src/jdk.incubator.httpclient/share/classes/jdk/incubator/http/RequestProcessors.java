/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import jdk.incubator.http.internal.common.Utils;

class RequestProcessors {
    // common base class for Publisher and Subscribers used here
    abstract static class ProcessorBase {
        HttpClientImpl client;

        synchronized void setClient(HttpClientImpl client) {
            this.client = client;
        }

        synchronized HttpClientImpl getClient() {
            return client;
        }
    }

    static class ByteArrayProcessor extends ProcessorBase
        implements HttpRequest.BodyProcessor
    {
        private volatile Flow.Publisher<ByteBuffer> delegate;
        private final int length;
        private final byte[] content;
        private final int offset;

        ByteArrayProcessor(byte[] content) {
            this(content, 0, content.length);
        }

        ByteArrayProcessor(byte[] content, int offset, int length) {
            this.content = content;
            this.offset = offset;
            this.length = length;
        }

        List<ByteBuffer> copy(byte[] content, int offset, int length) {
            List<ByteBuffer> bufs = new ArrayList<>();
            while (length > 0) {
                ByteBuffer b = ByteBuffer.allocate(Math.min(Utils.BUFSIZE, length));
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
    static class IterableProcessor extends ProcessorBase
        implements HttpRequest.BodyProcessor
    {
        private volatile Flow.Publisher<ByteBuffer> delegate;
        private final Iterable<byte[]> content;
        private volatile long contentLength;

        IterableProcessor(Iterable<byte[]> content) {
            this.content = content;
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

    static class StringProcessor extends ByteArrayProcessor {
        public StringProcessor(String content, Charset charset) {
            super(content.getBytes(charset));
        }
    }

    static class EmptyProcessor extends ProcessorBase implements HttpRequest.BodyProcessor
    {
        PseudoPublisher<ByteBuffer> delegate = new PseudoPublisher<>();

        @Override
        public long contentLength() {
            return 0;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            delegate.subscribe(subscriber);
        }
    }

    static class FileProcessor extends InputStreamProcessor
        implements HttpRequest.BodyProcessor
    {
        File file;

        FileProcessor(Path name) {
            super(() -> create(name));
            file = name.toFile();
        }

        static FileInputStream create(Path name) {
            try {
                return new FileInputStream(name.toFile());
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
        }
        @Override
        public long contentLength() {
            return file.length();
        }
    }

    /**
     * Reads one buffer ahead all the time, blocking in hasNext()
     */
    static class StreamIterator implements Iterator<ByteBuffer> {
        final InputStream is;
        ByteBuffer nextBuffer;
        boolean need2Read = true;
        boolean haveNext;
        Throwable error;

        StreamIterator(InputStream is) {
            this.is = is;
        }

        Throwable error() {
            return error;
        }

        private int read() {
            nextBuffer = Utils.getBuffer();
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
                error = ex;
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

    static class InputStreamProcessor extends ProcessorBase
        implements HttpRequest.BodyProcessor
    {
        private final Supplier<? extends InputStream> streamSupplier;
        private Flow.Publisher<ByteBuffer> delegate;

        InputStreamProcessor(Supplier<? extends InputStream> streamSupplier) {
            this.streamSupplier = streamSupplier;
        }

        @Override
        public synchronized void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            if (!(subscriber instanceof ProcessorBase)) {
                throw new UnsupportedOperationException();
            }
            ProcessorBase base = (ProcessorBase)subscriber;
            HttpClientImpl client = base.getClient();
            InputStream is = streamSupplier.get();
            if (is == null) {
                throw new UncheckedIOException(new IOException("no inputstream supplied"));
            }
            this.delegate = new PullPublisher<>(() -> new StreamIterator(is));
            delegate.subscribe(subscriber);
        }

        @Override
        public long contentLength() {
            return -1;
        }
    }
}
