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
package java.net.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javax.net.ssl.SSLParameters;

/**
 * Represents a response to a {@link HttpRequest}. A {@code HttpResponse} is
 * available when the response status code and headers have been received, but
 * before the response body is received.
 *
 * <p> Methods are provided in this class for accessing the response headers,
 * and status code immediately and also methods for retrieving the response body.
 * Static methods are provided which implement {@link BodyProcessor} for
 * standard body types such as {@code String, byte arrays, files}.
 *
 * <p> The {@link #body(BodyProcessor) body} or {@link #bodyAsync(BodyProcessor)
 * bodyAsync} which retrieve any response body must be called to ensure that the
 * TCP connection can be re-used subsequently, and any response trailers
 * accessed, if they exist, unless it is known that no response body was received.
 *
 * @since 9
 */
public abstract class HttpResponse {

    HttpResponse() { }

    /**
     * Returns the status code for this response.
     *
     * @return the response code
     */
    public abstract int statusCode();

    /**
     * Returns the {@link HttpRequest} for this response.
     *
     * @return the request
     */
    public abstract HttpRequest request();

    /**
     * Returns the received response headers.
     *
     * @return the response headers
     */
    public abstract HttpHeaders headers();

    /**
     * Returns the received response trailers, if there are any. This must only
     * be called after the response body has been received.
     *
     * @return the response trailers (may be empty)
     * @throws IllegalStateException if the response body has not been received
     *                               yet
     */
    public abstract HttpHeaders trailers();

    /**
     * Returns the body, blocking if necessary. The type T is determined by the
     * {@link BodyProcessor} implementation supplied. The body object will be
     * returned immediately if it is a type (such as {@link java.io.InputStream}
     * which reads the data itself. If the body object represents the fully read
     * body then it blocks until it is fully read.
     *
     * @param <T> the type of the returned body object
     * @param processor the processor to handle the response body
     * @return the body
     * @throws java.io.UncheckedIOException if an I/O error occurs reading the
     *                                      response
     */
    public abstract <T> T body(BodyProcessor<T> processor);

    /**
     * Returns a {@link java.util.concurrent.CompletableFuture} of type T. This
     * always returns immediately and the future completes when the body object
     * is available. The body will be available immediately if it is a type
     * (such as {@link java.io.InputStream} which reads the data itself. If the
     * body object represents the fully read body then it will not be available
     * until it is fully read.
     *
     * @param <T> the type of the returned body object
     * @param processor the processor to handle the response body
     * @return a CompletableFuture
     */
    public abstract <T> CompletableFuture<T> bodyAsync(BodyProcessor<T> processor);

    /**
     * Returns the {@link javax.net.ssl.SSLParameters} in effect for this
     * response. Returns {@code null} if this is not a https response.
     *
     * @return the SSLParameters associated with the response
     */
    public abstract SSLParameters sslParameters();

    /**
     * Returns the URI that the response was received from. This may be
     * different from the request URI if redirection occurred.
     *
     * @return the URI of the response
     */
    public abstract URI uri();

    /**
     * Returns the HTTP protocol version that was used for this response.
     *
     * @return HTTP protocol version
     */
    public abstract HttpClient.Version version();

    /**
     * Returns a {@link BodyProcessor}&lt;{@link java.nio.file.Path}&gt; where
     * the file is created if it does not already exist. When the Path object is
     * returned, the body has been completely written to the file.
     *
     * @param file the file to store the body in
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<Path> asFile(Path file) {
        return asFile(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /**
     * Returns a {@link BodyProcessor}&lt;{@link java.nio.file.Path}&gt; where
     * the download directory is specified, but the filename is obtained from
     * the Content-Disposition response header. The Content-Disposition header
     * must specify the <i>attachment</i> type and must also contain a
     * <i>filename</i> parameter. If the filename specifies multiple path
     * components only the final component is used as the filename (with the
     * given directory name). When the Path object is returned, the body has
     * been completely written to the file. The returned Path is the combination
     * of the supplied directory name and the file name supplied by the server.
     * If the destination directory does not exist or cannot be written to, then
     * the response will fail with an IOException.
     *
     * @param directory the directory to store the file in
     * @param openOptions open options
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<Path> asFileDownload(Path directory,
                                                     OpenOption... openOptions) {
        return new AbstractResponseProcessor<Path>() {

            FileChannel fc;
            Path file;

            @Override
            public Path onResponseBodyStartImpl(long contentLength,
                                                HttpHeaders headers)
                throws IOException
            {
                String dispoHeader = headers.firstValue("Content-Disposition")
                        .orElseThrow(() -> new IOException("No Content-Disposition"));
                if (!dispoHeader.startsWith("attachment;")) {
                    throw new IOException("Unknown Content-Disposition type");
                }
                int n = dispoHeader.indexOf("filename=");
                if (n == -1) {
                    throw new IOException("Bad Content-Disposition type");
                }
                String disposition = dispoHeader.substring(n + 9,
                                                           dispoHeader.lastIndexOf(';'));
                file = Paths.get(directory.toString(), disposition);
                fc = FileChannel.open(file, openOptions);
                return null;
            }

            @Override
            public void onResponseBodyChunkImpl(ByteBuffer b) throws IOException {
                fc.write(b);
            }

            @Override
            public Path onResponseComplete() throws IOException {
                fc.close();
                return file;
            }

            @Override
            public void onResponseError(Throwable t) {
                try {
                    if (fc != null) {
                        fc.close();
                    }
                } catch (IOException e) {
                }
            }
        };
    }

    /**
     * Returns a {@link BodyProcessor}&lt;{@link java.nio.file.Path}&gt;.
     *
     * <p> {@link HttpResponse}s returned using this response processor complete
     * after the entire response, including body has been read.
     *
     * @param file the filename to store the body in
     * @param openOptions any options to use when opening/creating the file
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<Path> asFile(Path file,
                                             OpenOption... openOptions) {
        return new AbstractResponseProcessor<Path>() {

            FileChannel fc;

            @Override
            public Path onResponseBodyStartImpl(long contentLength,
                                                HttpHeaders headers)
                throws IOException
            {
                fc = FileChannel.open(file, openOptions);
                return null;
            }

            @Override
            public void onResponseBodyChunkImpl(ByteBuffer b)
                throws IOException
            {
                fc.write(b);
            }

            @Override
            public Path onResponseComplete() throws IOException {
                fc.close();
                return file;
            }

            @Override
            public void onResponseError(Throwable t) {
                try {
                    if (fc != null) {
                        fc.close();
                    }
                } catch (IOException e) {
                }
            }
        };
    }

    static class ByteArrayResponseProcessor {

        static final int INITIAL_BUFLEN = 1024;

        byte[] buffer;
        int capacity;
        boolean knownLength;
        int position;

        ByteArrayResponseProcessor() { }

        public byte[] onStart(long contentLength) throws IOException {
            if (contentLength > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "byte array response limited to MAX_INT size");
            }
            capacity = (int) contentLength;
            if (capacity != -1) {
                buffer = new byte[capacity];
                knownLength = true;
            } else {
                buffer = new byte[INITIAL_BUFLEN];
                capacity = INITIAL_BUFLEN;
                knownLength = false;
            }
            position = 0;
            return null;
        }

        public void onBodyContent(ByteBuffer b) throws IOException {
            int toCopy = b.remaining();
            int size = capacity;
            if (toCopy > capacity - position) {
                // resize
                size += toCopy * 2;
            }
            if (size != capacity) {
                if (knownLength) {
                    // capacity should have been right from start
                    throw new IOException("Inconsistent content length");
                }
                byte[] newbuf = new byte[size];
                System.arraycopy(buffer, 0, newbuf, 0, position);
                buffer = newbuf;
                capacity = size;
            }
            int srcposition = b.arrayOffset() + b.position();
            System.arraycopy(b.array(), srcposition, buffer, position, toCopy);
            b.position(b.limit());
            position += toCopy;
        }

        public byte[] onComplete() throws IOException {
            if (knownLength) {
                if (position != capacity) {
                    throw new IOException("Wrong number of bytes received");
                }
                return buffer;
            }
            byte[] buf1 = new byte[position];
            System.arraycopy(buffer, 0, buf1, 0, position);
            return buf1;
        }

        public void onError(Throwable t) {
            // TODO:
        }
    }

    static final byte[] EMPTY = new byte[0];

    /**
     * Returns a response processor which supplies the response body to the
     * given Consumer. Each time data is received the consumer is invoked with a
     * byte[] containing at least one byte of data. After the final buffer is
     * received, the consumer is invoked one last time, with an empty byte
     * array.
     *
     * @param consumer a Consumer to accept the response body
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<Void> asByteArrayConsumer(Consumer<byte[]> consumer) {
        return new AbstractResponseProcessor<Void>() {
            @Override
            public Void onResponseBodyStartImpl(long clen,
                                                HttpHeaders h)
                throws IOException
            {
                return null;
            }

            @Override
            public void onResponseError(Throwable t) {
            }

            @Override
            public void onResponseBodyChunkImpl(ByteBuffer b) throws IOException {
                if (!b.hasRemaining()) {
                    return;
                }
                byte[] buf = new byte[b.remaining()];
                b.get(buf);
                consumer.accept(buf);
            }

            @Override
            public Void onResponseComplete() throws IOException {
                consumer.accept(EMPTY);
                return null;
            }
        };
    }

    /**
     * Returns a BodyProcessor which delivers the response data to a
     * {@link java.util.concurrent.Flow.Subscriber}{@code ByteBuffer}.
     * <p>
     * The given {@code Supplier<U>} is invoked when the Flow is completed in
     * order to convert the flow data into the U object that is returned as the
     * response body.
     *
     * @param <U> the response body type
     * @param subscriber the Flow.Subscriber
     * @param bufferSize the maximum number of bytes of data to be supplied in
     * each ByteBuffer
     * @param bodySupplier an object that converts the received data to the body
     * type U.
     * @return a BodyProcessor
     *
     * public static <U> BodyProcessor<Flow.Subscriber<ByteBuffer>>
     * asFlowSubscriber() {
     *
     * return new BodyProcessor<U>() { Flow.Subscriber<ByteBuffer> subscriber;
     * LongConsumer flowController; FlowSubscription subscription; Supplier<U>
     * bodySupplier; int bufferSize; // down-stream Flow window. long
     * buffersWindow; // upstream window long bytesWindow;
     * LinkedList<ByteBuffer> buffers = new LinkedList<>();
     *
     * class FlowSubscription implements Subscription { int recurseLevel = 0;
     * @Override public void request(long n) { boolean goodToGo = recurseLevel++
     * == 0;
     *
     * while (goodToGo && buffers.size() > 0 && n > 0) { ByteBuffer buf =
     * buffers.get(0); subscriber.onNext(buf); n--; } buffersWindow += n;
     * flowController.accept(n * bufferSize); recurseLevel--; }
     *
     * @Override public void cancel() { // ?? set flag and throw exception on
     * next receipt of buffer } }
     *
     * @Override public U onResponseBodyStart(long contentLength, HttpHeaders
     * responseHeaders, LongConsumer flowController) throws IOException {
     * this.subscriber = subscriber; this.flowController = flowController;
     * this.subscription = new FlowSubscription(); this.bufferSize = bufferSize;
     * subscriber.onSubscribe(subscription); return null; }
     *
     * @Override public void onResponseError(Throwable t) {
     * subscriber.onError(t); }
     *
     * @Override public void onResponseBodyChunk(ByteBuffer b) throws
     * IOException { if (buffersWindow > 0) { buffersWindow --;
     * subscriber.onNext(b); } else { buffers.add(b); // or could combine
     * buffers? } }
     *
     * @Override public U onResponseComplete() throws IOException {
     * subscriber.onComplete(); return bodySupplier.get(); } }; }
     */
    private static final ByteBuffer EOF = ByteBuffer.allocate(0);
    private static final ByteBuffer CLOSED = ByteBuffer.allocate(0);

    // prototype using ByteBuffer based flow control. InputStream feeds off a
    // BlockingQueue. Size of Q is determined from the the bufsize (bytes) and
    // the default ByteBuffer size. bufsize should be a reasonable multiple of
    // ByteBuffer size to prevent underflow/starvation. The InputStream updates
    // the flowControl window by one as each ByteBuffer is fully consumed.
    // Special sentinels are used to indicate stream closed and EOF.
    /**
     * Returns a response body processor which provides an InputStream to read
     * the body.
     *
     * @implNote This mechanism is provided primarily for backwards
     * compatibility for code that expects InputStream. It is recommended for
     * better performance to use one of the other response processor
     * implementations.
     *
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<InputStream> asInputStream() {
        return new BodyProcessor<InputStream>() {
            int queueSize = 2;
            private volatile Throwable throwable;

            BlockingQueue<ByteBuffer> queue  = new LinkedBlockingQueue<>();

            private void closeImpl() {
                try {
                    queue.put(CLOSED);
                } catch (InterruptedException e) { }
            }

            @Override
            public InputStream onResponseBodyStart(long contentLength,
                                                   HttpHeaders responseHeaders,
                                                   LongConsumer flowController)
                throws IOException
            {
                flowController.accept(queueSize);

                return new InputStream() {
                    ByteBuffer buffer;

                    @Override
                    public int read() throws IOException {
                        byte[] bb = new byte[1];
                        int n = read(bb, 0, 1);
                        if (n == -1) {
                            return -1;
                        } else {
                            return bb[0];
                        }
                    }

                    @Override
                    public int read(byte[] bb) throws IOException {
                        return read(bb, 0, bb.length);
                    }

                    @Override
                    public int read(byte[] bb, int offset, int length)
                        throws IOException
                    {
                        int n;
                        if (getBuffer()) {
                            return -1; // EOF
                        } else {
                            int remaining = buffer.remaining();
                            if (length >= remaining) {
                                buffer.get(bb, offset, remaining);
                                return remaining;
                            } else {
                                buffer.get(bb, offset, length);
                                return length;
                            }
                        }
                    }

                    @Override
                    public void close() {
                        closeImpl();
                    }

                    private boolean getBuffer() throws IOException {
                        while (buffer == null || (buffer != EOF &&
                                buffer != CLOSED && !buffer.hasRemaining())) {
                            try {
                                buffer = queue.take();
                                flowController.accept(1);
                            } catch (InterruptedException e) {
                                throw new IOException(e);
                            }
                        }
                        if (buffer == CLOSED) {
                            if (throwable != null) {
                                if (throwable instanceof IOException) {
                                    throw (IOException) throwable;
                                } else {
                                    throw new IOException(throwable);
                                }
                            }
                            throw new IOException("Closed");
                        }

                        if (buffer == EOF) {
                            return true; // EOF
                        }
                        return false; // not EOF
                    }

                };
            }

            @Override
            public void onResponseError(Throwable t) {
                throwable = t;
                closeImpl();
            }

            @Override
            public void onResponseBodyChunk(ByteBuffer b) throws IOException {
                try {
                    queue.put(Utils.copy(b));
                } catch (InterruptedException e) {
                    // shouldn't happen as queue should never block
                    throw new IOException(e);
                }
            }

            @Override
            public InputStream onResponseComplete() throws IOException {
                try {
                    queue.put(EOF);
                } catch (InterruptedException e) {
                    throw new IOException(e); // can't happen
                }
                return null;
            }

        };
    }

    /**
     * Common super class that takes care of flow control
     *
     * @param <T>
     */
    private static abstract class AbstractResponseProcessor<T>
        implements BodyProcessor<T>
    {
        LongConsumer flowController;

        @Override
        public final T onResponseBodyStart(long contentLength,
                                           HttpHeaders responseHeaders,
                                           LongConsumer flowController)
            throws IOException
        {
            this.flowController = flowController;
            flowController.accept(1);
            return onResponseBodyStartImpl(contentLength, responseHeaders);
        }

        public abstract T onResponseBodyStartImpl(long contentLength,
                                                  HttpHeaders responseHeaders)
            throws IOException;

        public abstract void onResponseBodyChunkImpl(ByteBuffer b)
            throws IOException;

        @Override
        public final void onResponseBodyChunk(ByteBuffer b) throws IOException {
            onResponseBodyChunkImpl(b);
            flowController.accept(1);
        }
    }

    /**
     * Returns a {@link BodyProcessor}&lt;byte[]&gt; which returns the response
     * body as a {@code byte array}.
     *
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<byte[]> asByteArray() {
        ByteArrayResponseProcessor brp = new ByteArrayResponseProcessor();

        return new AbstractResponseProcessor<byte[]>() {

            @Override
            public byte[] onResponseBodyStartImpl(long contentLength,
                                                  HttpHeaders h)
                throws IOException
            {
                brp.onStart(contentLength);
                return null;
            }

            @Override
            public void onResponseBodyChunkImpl(ByteBuffer b)
                throws IOException
            {
                brp.onBodyContent(b);
            }

            @Override
            public byte[] onResponseComplete() throws IOException {
                return brp.onComplete();
            }

            @Override
            public void onResponseError(Throwable t) {
                brp.onError(t);
            }
        };
    }

    /**
     * Returns a response processor which decodes the body using the character
     * set specified in the {@code Content-encoding} response header. If there
     * is no such header, or the character set is not supported, then
     * {@link java.nio.charset.StandardCharsets#ISO_8859_1 ISO_8859_1} is used.
     *
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<String> asString() {
        return asString(null);
    }

    /**
     * Returns a MultiProcessor that handles multiple responses, writes the
     * response bodies to files and which returns an aggregate response object
     * that is a {@code Map<URI,Path>}. The keyset of the Map represents the
     * URIs of the original request and any additional requests generated by the
     * server. The values are the paths of the destination files. Each path uses
     * the URI path of the request offset from the destination parent directory
     * provided.
     *
     * <p> All incoming additional requests (push promises) are accepted by this
     * multi response processor. Errors are effectively ignored and any failed
     * responses are simply omitted from the result Map. Other implementations
     * of MultiProcessor can handle these situations
     *
     * <p><b>Example usage</b>
     * <pre>
     * {@code
     *    CompletableFuture<Map<URI,Path>> cf =
     *    HttpRequest.create(new URI("https://www.foo.com/"))
     *               .version(Version.HTTP2)
     *               .GET()
     *               .sendAsyncMulti(HttpResponse.multiFile("/usr/destination"));
     *
     *    Map<URI,Path> results = cf.join();
     * }
     * </pre>
     *
     * @param destination the destination parent directory of all response
     * bodies
     * @return a MultiProcessor
     */
    public static MultiProcessor<Map<URI, Path>> multiFile(Path destination) {

        return new MultiProcessor<Map<URI, Path>>() {
            Map<URI, CompletableFuture<Path>> bodyCFs = new HashMap<>();

            Map<URI, Path> results = new HashMap<>();

            @Override
            public BiFunction<HttpRequest, CompletableFuture<HttpResponse>, Boolean>
            onStart(HttpRequest mainRequest,
                    CompletableFuture<HttpResponse> response) {
                bodyCFs.put(mainRequest.uri(), getBody(mainRequest, response));
                return (HttpRequest additional, CompletableFuture<HttpResponse> cf) -> {
                    CompletableFuture<Path> bcf = getBody(additional, cf);
                    bodyCFs.put(additional.uri(), bcf);
                    // we accept all comers
                    return true;
                };
            }

            private CompletableFuture<Path> getBody(HttpRequest req,
                                                    CompletableFuture<HttpResponse> cf) {
                URI u = req.uri();
                String path = u.getPath();
                return cf.thenCompose((HttpResponse resp) -> {
                    return resp.bodyAsync(HttpResponse.asFile(destination.resolve(path)));
                });
            }

            @Override
            public Map<URI, Path> onComplete() {
                // all CFs have completed normally or in error.
                Set<Map.Entry<URI, CompletableFuture<Path>>> entries = bodyCFs.entrySet();
                for (Map.Entry<URI, CompletableFuture<Path>> entry : entries) {
                    CompletableFuture<Path> v = entry.getValue();
                    URI uri = entry.getKey();
                    if (v.isDone() && !v.isCompletedExceptionally()) {
                        results.put(uri, v.join());
                    }
                }
                return results;
            }
        };
    }

    /**
     * Returns a {@link BodyProcessor}&lt;{@link String}&gt;.
     *
     * @param charset the name of the charset to interpret the body as. If
     * {@code null} then the processor tries to determine the character set from
     * the {@code Content-encoding} header. If that charset is not supported
     * then {@link java.nio.charset.StandardCharsets#ISO_8859_1 ISO_8859_1} is
     * used.
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<String> asString(Charset charset) {

        ByteArrayResponseProcessor brp = new ByteArrayResponseProcessor();

        return new AbstractResponseProcessor<String>() {
            Charset cs = charset;
            HttpHeaders headers;

            @Override
            public String onResponseBodyStartImpl(long contentLength,
                                                  HttpHeaders h)
                throws IOException
            {
                headers = h;
                brp.onStart(contentLength);
                return null;
            }

            @Override
            public void onResponseBodyChunkImpl(ByteBuffer b) throws IOException {
                brp.onBodyContent(b);
            }

            @Override
            public String onResponseComplete() throws IOException {
                byte[] buf = brp.onComplete();
                if (cs == null) {
                    cs = headers.firstValue("Content-encoding")
                                .map((String s) -> Charset.forName(s))
                                .orElse(StandardCharsets.ISO_8859_1);
                }
                return new String(buf, cs);
            }

            @Override
            public void onResponseError(Throwable t) {
                brp.onError(t);
            }

        };
    }

    /**
     * Returns a response processor which ignores the response body.
     *
     * @return a {@code BodyProcessor}
     */
    public static BodyProcessor<Void> ignoreBody() {
        return asByteArrayConsumer((byte[] buf) -> { /* ignore */ });
    }

    /**
     * A processor for response bodies, which determines the type of the
     * response body returned from {@link HttpResponse}. Response processors can
     * either return an object that represents the body itself (after it has
     * been read) or else an object that is used to read the body (such as an
     * {@code InputStream}). The parameterized type {@code <T>} is the type of
     * the returned body object from
     * {@link HttpResponse#body(BodyProcessor) HttpResponse.body} and
     * (indirectly) from {@link HttpResponse#bodyAsync(BodyProcessor)
     * HttpResponse.bodyAsync}.
     *
     * <p> Implementations of this interface are provided in {@link HttpResponse}
     * which write responses to {@code String, byte[], File, Consumer<byte[]>}.
     * Custom implementations can also be used.
     *
     * <p> The methods of this interface may be called from multiple threads,
     * but only one method is invoked at a time, and behaves as if called from
     * one thread.
     *
     * @param <T> the type of the response body
     *
     * @since 9
     */
    public interface BodyProcessor<T> {

        /**
         * Called immediately before the response body is read. If {@code <T>}
         * is an object used to read or accept the response body, such as a
         * {@code Consumer} or {@code InputStream} then it should be returned
         * from this method, and the body object will be returned before any
         * data is read. If {@code <T>} represents the body itself after being
         * read, then this method must return {@code null} and the body will be
         * returned from {@link #onResponseComplete()}. In both cases, the
         * actual body data is provided by the
         * {@link #onResponseBodyChunk(ByteBuffer) onResponseBodyChunk} method
         * in exactly the same way.
         *
         * <p> flowController is a consumer of long values and is used for
         * updating a flow control window as follows. The window represents the
         * number of times
         * {@link #onResponseBodyChunk(java.nio.ByteBuffer) onResponseBodyChunk}
         * may be called before receiving further updates to the window. Each
         * time it is called, the window is reduced by {@code 1}. When the
         * window reaches zero {@code onResponseBodyChunk()} will not be called
         * again until the window has opened again with further calls to
         * flowController.accept().
         * {@link java.util.function.LongConsumer#accept(long) flowcontroller.accept()}
         * must be called to open (increase) the window by the specified amount.
         * The initial value is zero. This implies that if {@code
         * onResponseBodyStart()} does not call {@code flowController.accept()}
         * with a positive value no data will ever be delivered.
         *
         * @param contentLength {@code -1} signifies unknown content length.
         *                      Otherwise, a positive integer, or zero.
         * @param responseHeaders the response headers
         * @param flowController a LongConsumer used to update the flow control
         *                       window
         * @return {@code null} or an object that can be used to read the
         *         response body.
         * @throws IOException if an exception occurs starting the response
         *                     body receive
         */
        T onResponseBodyStart(long contentLength,
                              HttpHeaders responseHeaders,
                              LongConsumer flowController)
            throws IOException;

        /**
         * Called if an error occurs while reading the response body. This
         * terminates the operation and no further calls will occur after this.
         *
         * @param t the Throwable
         */
        void onResponseError(Throwable t);

        /**
         * Called for each buffer of data received for this response.
         * ByteBuffers can be reused as soon as this method returns.
         *
         * @param b a ByteBuffer whose position is at the first byte that can be
         *          read, and whose limit is after the last byte that can be read
         * @throws IOException in case of I/O error
         */
        void onResponseBodyChunk(ByteBuffer b) throws IOException;

        /**
         * Called after the last time
         * {@link #onResponseBodyChunk(java.nio.ByteBuffer)} has been called and
         * returned indicating that the entire content has been read. This
         * method must return an object that represents or contains the response
         * body just received, but only if an object was not returned from
         * {@link #onResponseBodyStart(long, HttpHeaders, LongConsumer)
         * onResponseBodyStart}.
         *
         * @return a T, or {@code null} if an object was already returned
         * @throws IOException in case of I/O error
         */
        T onResponseComplete() throws IOException;
    }

    /**
     * A response processor for a HTTP/2 multi response. A multi response
     * comprises a main response, and zero or more additional responses. Each
     * additional response is sent by the server in response to requests that
     * the server also generates. Additional responses are typically resources
     * that the server guesses the client will need which are related to the
     * initial request.
     *
     * <p>The server generated requests are also known as <i>push promises</i>.
     * The server is permitted to send any number of these requests up to the
     * point where the main response is fully received. Therefore, after
     * completion of the main response body, the final number of additional
     * responses is known. Additional responses may be cancelled, but given that
     * the server does not wait for any acknowledgment before sending the
     * response, this must be done quickly to avoid unnecessary data transmission.
     *
     * <p> {@code MultiProcessor}s are parameterised with a type {@code T} which
     * represents some meaningful aggregate of the responses received. This
     * would typically be a Collection of response or response body objects. One
     * example implementation can be found at {@link
     * HttpResponse#multiFile(java.nio.file.Path)}.
     *
     * @param <T> a type representing the aggregated results
     *
     * @since 9
     */
    public interface MultiProcessor<T> {

        /**
         * Called before or soon after a multi request is sent. The request that
         * initiated the multi response is supplied, as well as a
         * CompletableFuture for the main response. The implementation of this
         * method must return a BiFunction which is called once for each push
         * promise received.
         *
         * <p> The parameters to the {@code BiFunction} are the {@code HttpRequest}
         * for the push promise and a {@code CompletableFuture} for its
         * response. The function must return a Boolean indicating whether the
         * push promise has been accepted (true) or should be canceled (false).
         * The CompletableFutures for any canceled pushes are themselves
         * completed exceptionally soon after the function returns.
         *
         * @param mainRequest the main request
         * @param response a CompletableFuture for the main response
         * @return a BiFunction that is called for each push promise
         */
        BiFunction<HttpRequest, CompletableFuture<HttpResponse>, Boolean>
        onStart(HttpRequest mainRequest,
                CompletableFuture<HttpResponse> response);

        /**
         * Called after all responses associated with the multi response have
         * been fully processed, including response bodies.
         *
         * <p> Example types for {@code T} could be Collections of response body
         * types or {@code Map}s from request {@code URI} to a response body
         * type.
         *
         * @return the aggregate response object
         */
        T onComplete();
    }
}
