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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpHeaders;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

/*
 * @test
 * @summary An example on how to read a response body with InputStream...
 * @run main/othervm HttpInputStreamTest
 * @author daniel fuchs
 */
public class HttpInputStreamTest {

    public static boolean DEBUG = Boolean.getBoolean("test.debug");

    /**
     * A simple HttpResponse.BodyHandler that creates a live
     * InputStream to read the response body from the underlying ByteBuffer
     * Flow.
     * The InputStream is made immediately available for consumption, before
     * the response body is fully received.
     */
    public static class HttpInputStreamHandler
        implements HttpResponse.BodyHandler<InputStream>    {

        public static final int MAX_BUFFERS_IN_QUEUE = 1;

        private final int maxBuffers;

        public HttpInputStreamHandler() {
            this(MAX_BUFFERS_IN_QUEUE);
        }

        public HttpInputStreamHandler(int maxBuffers) {
            this.maxBuffers = maxBuffers <= 0 ? MAX_BUFFERS_IN_QUEUE : maxBuffers;
        }

        @Override
        public synchronized HttpResponse.BodyProcessor<InputStream>
                apply(int i, HttpHeaders hh) {
            return new HttpResponseInputStream(maxBuffers);
        }

        /**
         * An InputStream built on top of the Flow API.
         */
        private static class HttpResponseInputStream extends InputStream
                    implements HttpResponse.BodyProcessor<InputStream> {

            // An immutable ByteBuffer sentinel to mark that the last byte was received.
            private static final ByteBuffer LAST = ByteBuffer.wrap(new byte[0]);

            // A queue of yet unprocessed ByteBuffers received from the flow API.
            private final BlockingQueue<ByteBuffer> buffers;
            private volatile Flow.Subscription subscription;
            private volatile boolean closed;
            private volatile Throwable failed;
            private volatile ByteBuffer current;

            HttpResponseInputStream() {
                this(MAX_BUFFERS_IN_QUEUE);
            }

            HttpResponseInputStream(int maxBuffers) {
                int capacity = maxBuffers <= 0 ? MAX_BUFFERS_IN_QUEUE : maxBuffers;
                this.buffers = new ArrayBlockingQueue<>(capacity);
            }

            @Override
            public CompletionStage<InputStream> getBody() {
                // Return the stream immediately, before the
                // response body is received.
                // This makes it possible for senAsync().get().body()
                // to complete before the response body is received.
                return CompletableFuture.completedStage(this);
            }

            // Returns the current byte buffer to read from.
            // If the current buffer has no remaining data, will take the
            // next buffer from the buffers queue, possibly blocking until
            // a new buffer is made available through the Flow API, or the
            // end of the flow is reached.
            private ByteBuffer current() throws IOException {
                while (current == null || !current.hasRemaining()) {
                    // Check whether the stream is claused or exhausted
                    if (closed || failed != null) {
                        throw new IOException("closed", failed);
                    }
                    if (current == LAST) break;

                    try {
                        // Take a new buffer from the queue, blocking
                        // if none is available yet...
                        if (DEBUG) System.err.println("Taking Buffer");
                        current = buffers.take();
                        if (DEBUG) System.err.println("Buffer Taken");

                        // Check whether some exception was encountered
                        // upstream
                        if (closed || failed != null) {
                            throw new IOException("closed", failed);
                        }

                        // Check whether we're done.
                        if (current == LAST) break;

                        // Inform the producer that it can start sending
                        // us a new buffer
                        Flow.Subscription s = subscription;
                        if (s != null) s.request(1);

                    } catch (InterruptedException ex) {
                        // continue
                    }
                }
                assert current == LAST || current.hasRemaining();
                return current;
            }

            @Override
            public int read(byte[] bytes, int off, int len) throws IOException {
                // get the buffer to read from, possibly blocking if
                // none is available
                ByteBuffer buffer;
                if ((buffer = current()) == LAST) return -1;

                // don't attempt to read more than what is available
                // in the current buffer.
                int read = Math.min(buffer.remaining(), len);
                assert read > 0 && read <= buffer.remaining();

                // buffer.get() will do the boundary check for us.
                buffer.get(bytes, off, read);
                return read;
            }

            @Override
            public int read() throws IOException {
                ByteBuffer buffer;
                if ((buffer = current()) == LAST) return -1;
                return buffer.get() & 0xFF;
            }

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(Math.max(2, buffers.remainingCapacity() + 1));
            }

            @Override
            public synchronized void onNext(ByteBuffer t) {
                try {
                    if (DEBUG) System.err.println("next buffer received");
                    buffers.put(t);
                    if (DEBUG) System.err.println("buffered offered");
                } catch (Exception ex) {
                    failed = ex;
                    try {
                        close();
                    } catch (IOException ex1) {
                        // OK
                    }
                }
            }

            @Override
            public void onError(Throwable thrwbl) {
                failed = thrwbl;
            }

            @Override
            public synchronized void onComplete() {
                subscription = null;
                onNext(LAST);
            }

            @Override
            public void close() throws IOException {
                synchronized (this) {
                    closed = true;
                    Flow.Subscription s = subscription;
                    if (s != null) {
                        s.cancel();
                    }
                    subscription = null;
                }
                super.close();
            }

        }
    }

    /**
     * Examine the response headers to figure out the charset used to
     * encode the body content.
     * If the content type is not textual, returns an empty Optional.
     * Otherwise, returns the body content's charset, defaulting to
     * ISO-8859-1 if none is explicitly specified.
     * @param headers The response headers.
     * @return The charset to use for decoding the response body, if
     *         the response body content is text/...
     */
    public static Optional<Charset> getCharset(HttpHeaders headers) {
        Optional<String> contentType = headers.firstValue("Content-Type");
        Optional<Charset> charset = Optional.empty();
        if (contentType.isPresent()) {
            final String[] values = contentType.get().split(";");
            if (values[0].startsWith("text/")) {
                charset = Optional.of(Stream.of(values)
                    .map(x -> x.toLowerCase(Locale.ROOT))
                    .map(String::trim)
                    .filter(x -> x.startsWith("charset="))
                    .map(x -> x.substring("charset=".length()))
                    .findFirst()
                    .orElse("ISO-8859-1"))
                    .map(Charset::forName);
            }
        }
        return charset;
    }

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest
            .newBuilder(new URI("http://hg.openjdk.java.net/jdk9/sandbox/jdk/shortlog/http-client-branch/"))
            .GET()
            .build();

        // This example shows how to return an InputStream that can be used to
        // start reading the response body before the response is fully received.
        // In comparison, the snipet below (which uses
        // HttpResponse.BodyHandler.asString()) obviously will not return before the
        // response body is fully read:
        //
        // System.out.println(
        //    client.sendAsync(request, HttpResponse.BodyHandler.asString()).get().body());

        CompletableFuture<HttpResponse<InputStream>> handle =
            client.sendAsync(request, new HttpInputStreamHandler());
        if (DEBUG) System.err.println("Request sent");

        HttpResponse<InputStream> pending = handle.get();

        // At this point, the response headers have been received, but the
        // response body may not have arrived yet. This comes from
        // the implementation of HttpResponseInputStream::getBody above,
        // which returns an already completed completion stage, without
        // waiting for any data.
        // We can therefore access the headers - and the body, which
        // is our live InputStream, without waiting...
        HttpHeaders responseHeaders = pending.headers();

        // Get the charset declared in the response headers.
        // The optional will be empty if the content type is not
        // of type text/...
        Optional<Charset> charset = getCharset(responseHeaders);

        try (InputStream is = pending.body();
            // We assume a textual content type. Construct an InputStream
            // Reader with the appropriate Charset.
            // charset.get() will throw NPE if the content is not textual.
            Reader r = new InputStreamReader(is, charset.get())) {

            char[] buff = new char[32];
            int off=0, n=0;
            if (DEBUG) System.err.println("Start receiving response body");
            if (DEBUG) System.err.println("Charset: " + charset.get());

            // Start consuming the InputStream as the data arrives.
            // Will block until there is something to read...
            while ((n = r.read(buff, off, buff.length - off)) > 0) {
                assert (buff.length - off) > 0;
                assert n <= (buff.length - off);
                if (n == (buff.length - off)) {
                    System.out.print(buff);
                    off = 0;
                } else {
                    off += n;
                }
                assert off < buff.length;
            }

            // last call to read may not have filled 'buff' completely.
            // flush out the remaining characters.
            assert off >= 0 && off < buff.length;
            for (int i=0; i < off; i++) {
                System.out.print(buff[i]);
            }

            // We're done!
            System.out.println("Done!");
        }
    }

}
