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
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.ProxySelector;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/**
 * Represents one HTTP request which can be sent to a server. {@code
 * HttpRequest}s are built from {@code HttpRequest} {@link HttpRequest.Builder
 * builder}s. {@code HttpRequest} builders are obtained from a {@link HttpClient}
 * by calling {@link HttpClient#request(java.net.URI) HttpClient.request}, or
 * by calling {@link #create(java.net.URI) HttpRequest.create} which returns a
 * builder on the <a href="HttpClient.html#defaultclient">default</a> client.
 * A request's {@link java.net.URI}, headers and body can be set. Request bodies
 * are provided through a {@link BodyProcessor} object. Once all required
 * parameters have been set in the builder, one of the builder methods should be
 * called, which sets the request method and returns a {@code HttpRequest}.
 * These methods are {@link Builder#GET() GET}, {@link HttpRequest.Builder#POST()
 * POST} and {@link HttpRequest.Builder#PUT() PUT} which return a GET, POST or
 * PUT request respectively. Alternatively, {@link
 * HttpRequest.Builder#method(String) method} can be called to set an arbitrary
 * method type (and return a {@code HttpRequest}). Builders can also be copied
 * and modified multiple times in order to build multiple related requests that
 * differ in some parameters.
 *
 * <p> Two simple, example HTTP interactions are shown below:
 * <pre>
 * {@code
 *      // GET
 *      HttpResponse response = HttpRequest
 *          .create(new URI("http://www.foo.com"))
 *          .headers("Foo", "foovalue", "Bar", "barvalue")
 *          .GET()
 *          .response();
 *
 *      int statusCode = response.statusCode();
 *      String responseBody = response.body(asString());
 *
 *      // POST
 *      response = HttpRequest
 *          .create(new URI("http://www.foo.com"))
 *          .body(fromString("param1=foo,param2=bar"))
 *          .POST()
 *          .response();}
 * </pre>
 *
 * <p> The request is sent and the response obtained by calling one of the
 * following methods.
 * <ul><li>{@link #response() response} blocks until the entire request has been
 * sent and the response status code and headers have been received.</li>
 * <li>{@link #responseAsync() responseAsync} sends the request and receives the
 * response asynchronously. Returns immediately with a
 * {@link java.util.concurrent.CompletableFuture CompletableFuture}&lt;{@link
 * HttpResponse}&gt;.</li>
 * <li>{@link #multiResponseAsync(HttpResponse.MultiProcessor) multiResponseAsync}
 * sends the request asynchronously, expecting multiple responses. This
 * capability is of most relevance to HTTP/2 server push, but can be used for
 * single responses (HTTP/1.1 or HTTP/2) also.</li>
 * </ul>
 *
 * <p> Once a request has been sent, it is an error to try and send it again.
 *
 * <p> Once a {@code HttpResponse} is received, the headers and response code are
 * available. The body can then be received by calling one of the body methods
 * on {@code HttpResponse}.
 *
 * <p> See below for discussion of synchronous versus asynchronous usage.
 *
 * <p> <b>Request bodies</b>
 *
 * <p> Request bodies are sent using one of the request processor implementations
 * below provided in {@code HttpRequest}, or else a custom implementation can be
 * used.
 * <ul>
 * <li>{@link #fromByteArray(byte[]) } from byte array</li>
 * <li>{@link #fromByteArrays(java.util.Iterator) fromByteArrays(Iterator)}
 *      from an iterator of byte arrays</li>
 * <li>{@link #fromFile(java.nio.file.Path) fromFile(Path)} from the file located
 *     at the given Path</li>
 * <li>{@link #fromString(java.lang.String) fromString(String)} from a String </li>
 * <li>{@link #fromInputStream(java.io.InputStream) fromInputStream(InputStream)}
 *      request body from InputStream</li>
 * <li>{@link #noBody() } no request body is sent</li>
 * </ul>
 *
 * <p> <b>Response bodies</b>
 *
 * <p> Responses bodies are handled by the {@link HttpResponse.BodyProcessor}
 * {@code <T>} supplied to the {@link HttpResponse#body(HttpResponse.BodyProcessor)
 * HttpResponse.body} and {@link HttpResponse#bodyAsync(HttpResponse.BodyProcessor)
 * HttpResponse.bodyAsync} methods. Some implementations of {@code
 * HttpResponse.BodyProcessor} are provided in {@link HttpResponse}:
 * <ul>
 * <li>{@link HttpResponse#asByteArray() } stores the body in a byte array</li>
 * <li>{@link HttpResponse#asString()} stores the body as a String </li>
 * <li>{@link HttpResponse#asFile(java.nio.file.Path) } stores the body in a
 * named file</li>
 * <li>{@link HttpResponse#ignoreBody() } ignores any received response body</li>
 * </ul>
 *
 * <p> The output of a response processor is the response body, and its
 * parameterized type {@code T} determines the type of the body object returned
 * from {@code HttpResponse.body} and {@code HttpResponse.bodyAsync}. Therefore,
 * as an example, the second response processor in the list above has the type
 * {@code HttpResponse.BodyProcessor<String>} which means the type returned by
 * {@code HttpResponse.body()} is a String. Response processors can be defined
 * to return potentially any type as body.
 *
 * <p> <b>Multi responses</b>
 *
 * <p> With HTTP/2 it is possible for a server to return a main response and zero
 * or more additional responses (known as server pushes) to a client-initiated
 * request. These are handled using a special response processor called {@link
 * HttpResponse.MultiProcessor}.
 *
 * <p> <b>Blocking/asynchronous behavior and thread usage</b>
 *
 * <p> There are two styles of request sending: <i>synchronous</i> and
 * <i>asynchronous</i>. {@link #response() response} blocks the calling thread
 * until the request has been sent and the response received.
 *
 * <p> {@link #responseAsync() responseAsync} is asynchronous and returns
 * immediately with a {@link java.util.concurrent.CompletableFuture}&lt;{@link
 * HttpResponse}&gt; and when this object completes (in a background thread) the
 * response has been received.
 *
 * <p> {@link #multiResponseAsync(HttpResponse.MultiProcessor) multiResponseAsync}
 * is the variant for multi responses and is also asynchronous.
 *
 * <p> CompletableFutures can be combined in different ways to declare the
 * dependencies among several asynchronous tasks, while allowing for the maximum
 * level of parallelism to be utilized.
 *
 * <p> <b>Security checks</b>
 *
 * <p> If a security manager is present then security checks are performed by
 * the {@link #response() } and {@link #responseAsync() } methods. A {@link
 * java.net.URLPermission} or {@link java.net.SocketPermission} is required to
 * access any destination origin server and proxy server utilised. URLPermissions
 * should be preferred in policy files over SocketPermissions given the more
 * limited scope of URLPermission. Permission is always implicitly granted to a
 * system's default proxies. The URLPermission form used to access proxies uses
 * a method parameter of "CONNECT" (for all kinds of proxying) and a url string
 * of the form "socket://host:port" where host and port specify the proxy's
 * address.
 *
 * <p> <b>Examples</b>
 * <pre>
 *     import static java.net.http.HttpRequest.*;
 *     import static java.net.http.HttpResponse.*;
 *
 *     //Simple blocking
 *
 *     HttpResponse r1 = HttpRequest.create(new URI("http://www.foo.com/"))
 *                                  .GET()
 *                                 .response();
 *     int responseCode = r1.statusCode());
 *     String body = r1.body(asString());
 *
 *     HttpResponse r2 = HttpRequest.create(new URI("http://www.foo.com/"))
 *                                  .GET()
 *                                  .response();
 *
 *     System.out.println("Response was " + r1.statusCode());
 *     Path body1 = r2.body(asFile(Paths.get("/tmp/response.txt")));
 *     // Content stored in /tmp/response.txt
 *
 *     HttpResponse r3 = HttpRequest.create(new URI("http://www.foo.com/"))
 *                                  .body(fromString("param1=1, param2=2"))
 *                                  .POST()
 *                                  .response();
 *
 *     Void body2 = r3.body(ignoreBody()); // body is Void in this case
 * </pre>
 *
 * <p><b>Asynchronous Example</b>
 *
 * <p> All of the above examples will work asynchronously, if {@link
 * #responseAsync()} is used instead of {@link #response()} in which case the
 * returned object is a {@code CompletableFuture<HttpResponse>} instead of
 * {@code HttpResponse}. The following example shows how multiple requests can
 * be sent asynchronously. It also shows how dependent asynchronous operations
 * (receiving response, and receiving response body) can be chained easily using
 * one of the many methods in {@code CompletableFuture}.
 * <pre>
 * {@code
 *      // fetch a list of target URIs asynchronously and store them in Files.
 *
 *      List<URI> targets = ...
 *
 *      List<CompletableFuture<File>> futures = targets
 *          .stream()
 *          .map(target -> {
 *              return HttpRequest
 *                  .create(target)
 *                  .GET()
 *                  .responseAsync()
 *                  .thenCompose(response -> {
 *                      Path dest = Paths.get("base", target.getPath());
 *                      if (response.statusCode() == 200) {
 *                          return response.bodyAsync(asFile(dest));
 *                      } else {
 *                          return CompletableFuture.completedFuture(dest);
 *                      }
 *                  })
 *                  // convert Path -> File
 *                  .thenApply((Path dest) -> {
 *                      return dest.toFile();
 *                  });
 *              })
 *          .collect(Collectors.toList());
 *
 *      // all async operations waited for here
 *
 *      CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
 *          .join();
 *
 *      // all elements of futures have completed and can be examined.
 *      // Use File.exists() to check whether file was successfully downloaded
 * }
 * </pre>
 *
 * @since 9
 */
public abstract class HttpRequest {

    HttpRequest() {}

    /**
     * A builder of {@link HttpRequest}s. {@code HttpRequest.Builder}s are
     * created by calling {@link HttpRequest#create(URI)} or {@link
     * HttpClient#request(URI)}.
     *
     * <p> Each of the setter methods in this class modifies the state of the
     * builder and returns <i>this</i> (ie. the same instance). The methods are
     * not synchronized and should not be called from multiple threads without
     * external synchronization.
     *
     * <p> The build methods return a new {@code HttpRequest} each time they are
     * called.
     *
     * @since 9
     */
    public abstract static class Builder {

        Builder() {}

        /**
         * Sets this HttpRequest's request URI.
         *
         * @param uri the request URI
         * @return this request builder
         */
        public abstract Builder uri(URI uri);

        /**
         * Specifies whether this request will automatically follow redirects
         * issued by the server. The default value for this setting is the value
         * of {@link HttpClient#followRedirects() }
         *
         * @param policy the redirection policy
         * @return this request builder
         */
        public abstract Builder followRedirects(HttpClient.Redirect policy);

        /**
         * Request server to acknowledge request before sending request
         * body. This is disabled by default. If enabled, the server is requested
         * to send an error response or a 100-Continue response before the client
         * sends the request body. This means the request processor for the
         * request will not be invoked until this interim response is received.
         *
         * @param enable {@code true} if Expect continue to be sent
         * @return this request builder
         */
        public abstract Builder expectContinue(boolean enable);

        /**
         * Overrides the {@link HttpClient#version()  } setting for this
         * request.
         *
         * @param version the HTTP protocol version requested
         * @return this request builder
         */
        public abstract Builder version(HttpClient.Version version);

        /**
         * Adds the given name value pair to the set of headers for this request.
         *
         * @param name the header name
         * @param value the header value
         * @return this request builder
         */
        public abstract Builder header(String name, String value);

        /**
         * Overrides the ProxySelector set on the request's client for this
         * request.
         *
         * @param proxy the ProxySelector to use
         * @return this request builder
         */
        public abstract Builder proxy(ProxySelector proxy);

        /**
         * Adds the given name value pairs to the set of headers for this
         * request. The supplied Strings must alternate as names and values.
         *
         * @param headers the list of String name value pairs
         * @return this request builder
         * @throws IllegalArgumentException if there is an odd number of
         *                                  parameters
         */
        public abstract Builder headers(String... headers);

        /**
         * Sets a timeout for this request. If the response is not received
         * within the specified timeout then a {@link HttpTimeoutException} is
         * thrown from {@link #response() } or {@link #responseAsync() }
         * completes exceptionally with a {@code HttpTimeoutException}.
         *
         * @param unit the timeout units
         * @param timeval the number of units to wait for
         * @return this request builder
         */
        public abstract Builder timeout(TimeUnit unit, long timeval);

        /**
         * Sets the given name value pair to the set of headers for this
         * request. This overwrites any previously set values for name.
         *
         * @param name the header name
         * @param value the header value
         * @return this request builder
         */
        public abstract Builder setHeader(String name, String value);

        /**
         * Sets a request body for this builder. See {@link HttpRequest}
         * for example {@code BodyProcessor} implementations.
         * If no body is specified, then no body is sent with the request.
         *
         * @param reqproc the request body processor
         * @return this request builder
         */
        public abstract Builder body(BodyProcessor reqproc);

        /**
         * Builds and returns a GET {@link HttpRequest} from this builder.
         *
         * @return a {@code HttpRequest}
         */
        public abstract HttpRequest GET();

        /**
         * Builds and returns a POST {@link HttpRequest} from this builder.
         *
         * @return a {@code HttpRequest}
         */
        public abstract HttpRequest POST();

        /**
         * Builds and returns a PUT {@link HttpRequest} from this builder.
         *
         * @return a {@code HttpRequest}
         */
        public abstract HttpRequest PUT();

        /**
         * Builds and returns a {@link HttpRequest} from this builder using
         * the given method String. The method string is case-sensitive, and
         * may be rejected if an upper-case string is not used.
         *
         * @param method the method to use
         * @return a {@code HttpRequest}
         * @throws IllegalArgumentException if an unrecognised method is used
         */
        public abstract HttpRequest method(String method);

        /**
         * Returns an exact duplicate copy of this Builder based on current
         * state. The new builder can then be modified independently of this
         * builder.
         *
         * @return an exact copy of this Builder
         */
        public abstract Builder copy();
    }

    /**
     * Creates a HttpRequest builder from the <i>default</i> HttpClient.
     *
     * @param uri the request URI
     * @return a new request builder
     */
    public static HttpRequest.Builder create(URI uri) {
        return HttpClient.getDefault().request(uri);
    }

    /**
     * Returns the follow-redirects setting for this request.
     *
     * @return follow redirects setting
     */
    public abstract HttpClient.Redirect followRedirects();

    /**
     * Returns the response to this request, by sending it and blocking if
     * necessary to get the response. The {@link HttpResponse} contains the
     * response status and headers.
     *
     * @return a HttpResponse for this request
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the operation was interrupted
     * @throws SecurityException if the caller does not have the required
     *                           permission
     * @throws IllegalStateException if called more than once or if
     *                               responseAsync() called previously
     */
    public abstract HttpResponse response()
        throws IOException, InterruptedException;

    /**
     * Sends the request and returns the response asynchronously. This method
     * returns immediately with a {@link CompletableFuture}&lt;{@link
     * HttpResponse}&gt;
     *
     * @return a {@code CompletableFuture<HttpResponse>}
     * @throws IllegalStateException if called more than once or if response()
     *                               called previously.
     */
    public abstract CompletableFuture<HttpResponse> responseAsync();

    /**
     * Sends the request asynchronously expecting multiple responses.
     *
     * <p> This method must be given a {@link HttpResponse.MultiProcessor} to
     * handle the multiple responses.
     *
     * <p> If a security manager is set, the caller must possess a {@link
     * java.net.URLPermission} for the request's URI, method and any user set
     * headers. The security manager is also checked for each incoming
     * additional server generated request/response. Any request that fails the
     * security check, is canceled and ignored.
     *
     * <p> This method can be used for both HTTP/1.1 and HTTP/2, but in cases
     * where multiple responses are not supported, the MultiProcessor
     * only receives the main response.
     *
     * <p> The aggregate {@code CompletableFuture} returned from this method
     * returns a {@code <U>} defined by the {@link HttpResponse.MultiProcessor}
     * implementation supplied. This will typically be a Collection of
     * HttpResponses or of some response body type.
     *
     * @param <U> the aggregate response type
     * @param rspproc the MultiProcessor for the request
     * @return a {@code CompletableFuture<U>}
     * @throws IllegalStateException if the request has already been sent.
     */
    public abstract <U> CompletableFuture<U>
    multiResponseAsync(HttpResponse.MultiProcessor<U> rspproc);

    /**
     * Returns the request method for this request. If not set explicitly,
     * the default method for any request is "GET".
     *
     * @return this request's method
     */
    public abstract String method();

    /**
     * Returns this request's {@link HttpRequest.Builder#expectContinue(boolean)
     * expect continue } setting.
     *
     * @return this request's expect continue setting
     */
    public abstract boolean expectContinue();

    /**
     * Returns this request's request URI.
     *
     * @return this request's URI
     */
    public abstract URI uri();

    /**
     * Returns this request's {@link HttpClient}.
     *
     * @return this request's HttpClient
     */
    public abstract HttpClient client();

    /**
     * Returns the HTTP protocol version that this request will use or used.
     *
     * @return HTTP protocol version
     */
    public abstract HttpClient.Version version();

    /**
     * The (user-accessible) request headers that this request was (or will be)
     * sent with.
     *
     * @return this request's HttpHeaders
     */
    public abstract HttpHeaders headers();

    /**
     * Returns a request processor whose body is the given String, converted
     * using the {@link java.nio.charset.StandardCharsets#ISO_8859_1 ISO_8859_1}
     * character set.
     *
     * @param body the String containing the body
     * @return a BodyProcessor
     */
    public static BodyProcessor fromString(String body) {
        return fromString(body, StandardCharsets.ISO_8859_1);
    }

    /**
     * A request processor that takes data from the contents of a File.
     *
     * @param path the path to the file containing the body
     * @return a BodyProcessor
     */
    public static BodyProcessor fromFile(Path path) {
        FileChannel fc;
        long size;

        try {
            fc = FileChannel.open(path);
            size = fc.size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new BodyProcessor() {
            LongConsumer flow;

            @Override
            public long onRequestStart(HttpRequest hr, LongConsumer flow) {
                // could return exact file length, but for now -1
                this.flow = flow;
                flow.accept(1);
                if (size != 0) {
                    return size;
                } else {
                    return -1;
                }
            }

            @Override
            public boolean onRequestBodyChunk(ByteBuffer buffer) throws IOException {
                int n = fc.read(buffer);
                if (n == -1) {
                    fc.close();
                    return true;
                }
                flow.accept(1);
                return false;
            }

            @Override
            public void onRequestError(Throwable t) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    Log.logError(ex.toString());
                }
            }
        };
    }

    /**
     * Returns a request processor whose body is the given String, converted
     * using the given character set.
     *
     * @param s the String containing the body
     * @param charset the character set to convert the string to bytes
     * @return a BodyProcessor
     */
    public static BodyProcessor fromString(String s, Charset charset) {
        return fromByteArray(s.getBytes(charset));
    }

    /**
     * Returns a request processor whose body is the given byte array.
     *
     * @param buf the byte array containing the body
     * @return a BodyProcessor
     */
    public static BodyProcessor fromByteArray(byte[] buf) {
        return fromByteArray(buf, 0, buf.length);
    }

    /**
     * Returns a request processor whose body is the content of the given byte
     * array length bytes starting from the specified offset.
     *
     * @param buf the byte array containing the body
     * @param offset the offset of the first byte
     * @param length the number of bytes to use
     * @return a BodyProcessor
     */
    public static BodyProcessor fromByteArray(byte[] buf, int offset, int length) {

        return new BodyProcessor() {
            LongConsumer flow;
            byte[] barray;
            int index;
            int sent;

            @Override
            public long onRequestStart(HttpRequest hr, LongConsumer flow) {
                this.flow = flow;
                flow.accept(1);
                barray = buf;
                index = offset;
                return length;
            }

            @Override
            public boolean onRequestBodyChunk(ByteBuffer buffer)
                throws IOException
            {
                if (sent == length) {
                    return true;
                }

                int remaining = buffer.remaining();
                int left = length - sent;
                int n = remaining > left ? left : remaining;
                buffer.put(barray, index, n);
                index += n;
                sent += n;
                flow.accept(1);
                return sent == length;
            }

            @Override
            public void onRequestError(Throwable t) {
                Log.logError(t.toString());
            }
        };
    }

    /**
     * A request processor that takes data from an Iterator of byte arrays.
     *
     * @param iter an Iterator of byte arrays
     * @return a BodyProcessor
     */
    public static BodyProcessor fromByteArrays(Iterator<byte[]> iter) {

        return new BodyProcessor() {
            LongConsumer flow;
            byte[] current;
            int curIndex;

            @Override
            public long onRequestStart(HttpRequest hr, LongConsumer flow) {
                this.flow = flow;
                flow.accept(1);
                return -1;
            }

            @Override
            public boolean onRequestBodyChunk(ByteBuffer buffer)
                throws IOException
            {
                int remaining;

                while ((remaining = buffer.remaining()) > 0) {
                    if (current == null) {
                        if (!iter.hasNext()) {
                            return true;
                        }
                        current = iter.next();
                        curIndex = 0;
                    }
                    int n = Math.min(remaining, current.length - curIndex);
                    buffer.put(current, curIndex, n);
                    curIndex += n;

                    if (curIndex == current.length) {
                        current = null;
                        flow.accept(1);
                        return false;
                    }
                }
                flow.accept(1);
                return false;
            }

            @Override
            public void onRequestError(Throwable t) {
                Log.logError(t.toString());
            }
        };
    }

    /**
     * A request processor that reads its data from an InputStream.
     *
     * @param stream an InputStream
     * @return a BodyProcessor
     */
    public static BodyProcessor fromInputStream(InputStream stream) {
        // for now, this blocks. It could be offloaded to a separate thread
        // to do reading and guarantee that onRequestBodyChunk() won't block
        return new BodyProcessor() {
            LongConsumer flow;

            @Override
            public long onRequestStart(HttpRequest hr, LongConsumer flow) {
                this.flow = flow;
                flow.accept(1);
                return -1;
            }

            @Override
            public boolean onRequestBodyChunk(ByteBuffer buffer)
                throws IOException
            {
                int remaining = buffer.remaining();
                int n = stream.read(buffer.array(), buffer.arrayOffset(), remaining);
                if (n == -1) {
                    stream.close();
                    return true;
                }
                buffer.position(buffer.position() + n);
                flow.accept(1);
                return false;
            }

            @Override
            public void onRequestError(Throwable t) {
                Log.logError(t.toString());
            }
        };
    }

    /**
     * A request processor which sends no request body.
     *
     * @return a BodyProcessor
     */
    public static BodyProcessor noBody() {
        return new BodyProcessor() {

            @Override
            public long onRequestStart(HttpRequest hr, LongConsumer flow) {
                return 0;
            }

            @Override
            public boolean onRequestBodyChunk(ByteBuffer buffer)
                throws IOException
            {
                throw new InternalError("should never reach here");
            }

            @Override
            public void onRequestError(Throwable t) {
                Log.logError(t.toString());
            }
        };
    }

    /**
     * A request processor which obtains the request body from some source.
     * Implementations of this interface are provided which allow request bodies
     * to be supplied from standard types, such as {@code String, byte[], File,
     * InputStream}. Other implementations can be provided.
     *
     * <p> The methods of this interface may be called from multiple threads,
     * but only one method is invoked at a time, and behaves as if called from
     * one thread.
     *
     * <p> See {@link HttpRequest} for implementations that take request bodies
     * from {@code byte arrays, Strings, Paths} etc.
     *
     * @since 9
     */
    public interface BodyProcessor {

        /**
         * Called before a request is sent. Is expected to return the content
         * length of the request body. Zero means no content. Less than zero
         * means an unknown positive content-length, and the body will be
         * streamed.
         *
         * <p> The flowController object must be used to manage the flow of
         * calls to {@link #onRequestBodyChunk(ByteBuffer)}. The typical usage
         * for a non-blocking processor is to call it once inside
         * onRequestStart() and once during each call to onRequestBodyChunk().
         *
         * @param hr the request
         * @param flowController the HttpFlowController
         * @return the content length
         * @throws IOException if an I/O error occurs
         */
        long onRequestStart(HttpRequest hr, LongConsumer flowController)
            throws IOException;

        /**
         * Called if sending a request body fails.
         *
         * @implSpec The default implementation does nothing.
         *
         * @param t the Throwable that caused the failure
         */
        default void onRequestError(Throwable t) { }

        /**
         * Called to obtain a buffer of data to send. The data must be placed
         * in the provided buffer. The implementation should not block. The
         * boolean return code notifies the protocol implementation if the
         * supplied buffer is the final one (or not).
         *
         * @param buffer a ByteBuffer to write data into
         * @return whether or not this is the last buffer
         * @throws IOException if an I/O error occurs
         */
        boolean onRequestBodyChunk(ByteBuffer buffer) throws IOException;

        /**
         * Called when the request body has been completely sent.
         *
         * @implSpec The default implementation does nothing
         */
        default void onComplete() {
            // TODO: need to call this
        }
    }
}
