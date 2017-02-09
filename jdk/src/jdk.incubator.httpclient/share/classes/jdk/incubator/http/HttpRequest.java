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

package jdk.incubator.http;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

/**
 * Represents one HTTP request which can be sent to a server.
 * {@Incubating }
 *
 * <p> {@code HttpRequest}s are built from {@code HttpRequest}
 * {@link HttpRequest.Builder builder}s. {@code HttpRequest} builders are
 * obtained by calling {@link HttpRequest#newBuilder(java.net.URI)
 * HttpRequest.newBuilder}.
 * A request's {@link java.net.URI}, headers and body can be set. Request bodies
 * are provided through a {@link BodyProcessor} object supplied to the
 * {@link Builder#DELETE(jdk.incubator.http.HttpRequest.BodyProcessor) DELETE},
 * {@link Builder#POST(jdk.incubator.http.HttpRequest.BodyProcessor) POST} or
 * {@link Builder#PUT(jdk.incubator.http.HttpRequest.BodyProcessor) PUT} methods.
 * {@link Builder#GET() GET} does not take a body. Once all required
 * parameters have been set in the builder, {@link Builder#build() } is called
 * to return the {@code HttpRequest}. Builders can also be copied
 * and modified multiple times in order to build multiple related requests that
 * differ in some parameters.
 *
 * <p> Two simple, example HTTP interactions are shown below:
 * <pre>
 * {@code
 *      HttpClient client = HttpClient.newHttpClient();
 *
 *      // GET
 *      HttpResponse<String> response = client.send(
 *          HttpRequest
 *              .newBuilder(new URI("http://www.foo.com/"))
 *              .headers("Foo", "foovalue", "Bar", "barvalue")
 *              .GET()
 *              .build(),
 *          BodyHandler.asString()
 *      );
 *      int statusCode = response.statusCode();
 *      String body = response.body();
 *
 *      // POST
 *      HttpResponse<Path> response = client.send(
 *          HttpRequest
 *              .newBuilder(new URI("http://www.foo.com/"))
 *              .headers("Foo", "foovalue", "Bar", "barvalue")
 *              .POST(BodyProcessor.fromString("Hello world"))
 *              .build(),
 *          BodyHandler.asFile(Paths.get("/path"))
 *      );
 *      int statusCode = response.statusCode();
 *      Path body = response.body(); // should be "/path"
 * }
 * </pre>
 * <p> The request is sent and the response obtained by calling one of the
 * following methods in {@link HttpClient}.
 * <ul><li>{@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler)} blocks
 * until the entire request has been sent and the response has been received.</li>
 * <li>{@link HttpClient#sendAsync(HttpRequest,HttpResponse.BodyHandler)} sends the
 * request and receives the response asynchronously. Returns immediately with a
 * {@link java.util.concurrent.CompletableFuture CompletableFuture}&lt;{@link
 * HttpResponse}&gt;.</li>
 * <li>{@link HttpClient#sendAsync(HttpRequest,HttpResponse.MultiProcessor) }
 * sends the request asynchronously, expecting multiple responses. This
 * capability is of most relevance to HTTP/2 server push, but can be used for
 * single responses (HTTP/1.1 or HTTP/2) also.</li>
 * </ul>
 *
 * <p> Once a {@link HttpResponse} is received, the headers, response code
 * and body (typically) are available. Whether the body has been read or not
 * depends on the type {@code <T>} of the response body. See below.
 *
 * <p> See below for discussion of synchronous versus asynchronous usage.
 *
 * <p> <b>Request bodies</b>
 *
 * <p> Request bodies are sent using one of the request processor implementations
 * below provided in {@link HttpRequest.BodyProcessor}, or else a custom implementation can be
 * used.
 * <ul>
 * <li>{@link BodyProcessor#fromByteArray(byte[]) fromByteArray(byte[])} from byte array</li>
 * <li>{@link BodyProcessor#fromByteArrays(Iterable) fromByteArrays(Iterable)}
 *      from an Iterable of byte arrays</li>
 * <li>{@link BodyProcessor#fromFile(java.nio.file.Path) fromFile(Path)} from the file located
 *     at the given Path</li>
 * <li>{@link BodyProcessor#fromString(java.lang.String) fromString(String)} from a String </li>
 * <li>{@link BodyProcessor#fromInputStream(Supplier) fromInputStream}({@link Supplier}&lt;
 *      {@link InputStream}&gt;) from an InputStream obtained from a Supplier</li>
 * <li>{@link BodyProcessor#noBody() } no request body is sent</li>
 * </ul>
 *
 * <p> <b>Response bodies</b>
 *
 * <p>Responses bodies are handled at two levels. When sending the request,
 * a response body handler is specified. This is a function ({@link HttpResponse.BodyHandler})
 * which will be called with the response status code and headers, once these are received. This
 * function is then expected to return a {@link HttpResponse.BodyProcessor}
 * {@code <T>} which is then used to read the response body converting it
 * into an instance of T. After this occurs, the response becomes
 * available in a {@link HttpResponse} and {@link HttpResponse#body()} can then
 * be called to obtain the body. Some implementations and examples of usage of both {@link
 * HttpResponse.BodyProcessor} and {@link HttpResponse.BodyHandler}
 * are provided in {@link HttpResponse}:
 * <p><b>Some of the pre-defined body handlers</b><br>
 * <ul>
 * <li>{@link HttpResponse.BodyHandler#asByteArray() BodyHandler.asByteArray()}
 * stores the body in a byte array</li>
 * <li>{@link HttpResponse.BodyHandler#asString() BodyHandler.asString()}
 * stores the body as a String </li>
 * <li>{@link HttpResponse.BodyHandler#asFile(java.nio.file.Path)
 * BodyHandler.asFile(Path)} stores the body in a named file</li>
 * <li>{@link HttpResponse.BodyHandler#discard(Object) BodyHandler.discard()}
 * discards the response body and returns the given value instead.</li>
 * </ul>
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
 * <i>asynchronous</i>. {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler) }
 * blocks the calling thread until the request has been sent and the response received.
 *
 * <p> {@link HttpClient#sendAsync(HttpRequest, HttpResponse.BodyHandler)}  is asynchronous and returns
 * immediately with a {@link java.util.concurrent.CompletableFuture}&lt;{@link
 * HttpResponse}&gt; and when this object completes (in a background thread) the
 * response has been received.
 *
 * <p> {@link HttpClient#sendAsync(HttpRequest,HttpResponse.MultiProcessor)}
 * is the variant for multi responses and is also asynchronous.
 *
 * <p> {@code CompletableFuture}s can be combined in different ways to declare the
 * dependencies among several asynchronous tasks, while allowing for the maximum
 * level of parallelism to be utilized.
 *
 * <p> <b>Security checks</b>
 *
 * <p> If a security manager is present then security checks are performed by
 * the sending methods. A {@link java.net.URLPermission} or {@link java.net.SocketPermission} is required to
 * access any destination origin server and proxy server utilised. {@code URLPermission}s
 * should be preferred in policy files over {@code SocketPermission}s given the more
 * limited scope of {@code URLPermission}. Permission is always implicitly granted to a
 * system's default proxies. The {@code URLPermission} form used to access proxies uses
 * a method parameter of {@code "CONNECT"} (for all kinds of proxying) and a url string
 * of the form {@code "socket://host:port"} where host and port specify the proxy's
 * address.
 *
 * <p> <b>Examples</b>
 * <pre>{@code
 *      HttpClient client = HttpClient
 *              .newBuilder()
 *              .build();
 *
 *      HttpRequest request = HttpRequest
 *              .newBuilder(new URI("http://www.foo.com/"))
 *              .POST(BodyProcessor.fromString("Hello world"))
 *              .build();
 *
 *      HttpResponse<Path> response =
 *          client.send(request, BodyHandler.asFile(Paths.get("/path")));
 *
 *      Path body = response.body();
 * }</pre>
 *
 * <p><b>Asynchronous Example</b>
 *
 * <p> The above example will work asynchronously, if {@link HttpClient#sendAsync
 * (HttpRequest, HttpResponse.BodyHandler) sendAsync} is used instead of
 * {@link HttpClient#send(HttpRequest,HttpResponse.BodyHandler) send}
 * in which case the returned object is a {@link CompletableFuture}{@code <HttpResponse>}
 * instead of {@link HttpResponse}. The following example shows how multiple requests
 * can be sent asynchronously. It also shows how dependent asynchronous operations
 * (receiving response, and receiving response body) can be chained easily using
 * one of the many methods in {@code CompletableFuture}.
 * <pre>
 * {@code
 *  // fetch a list of target URIs asynchronously and store them in Files.
 *
 *      List<URI> targets = ...
 *
 *      List<CompletableFuture<File>> futures = targets
 *          .stream()
 *          .map(target -> client
 *                  .sendAsync(
 *                      HttpRequest.newBuilder(target)
 *                                 .GET()
 *                                 .build(),
 *                      BodyHandler.asFile(Paths.get("base", target.getPath())))
 *                  .thenApply(response -> response.body())
 *                  .thenApply(path -> path.toFile()))
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
 * <p>
 * Unless otherwise stated, {@code null} parameter values will cause methods
 * of this class to throw {@code NullPointerException}.
 *
 * @since 9
 */
public abstract class HttpRequest {

    /**
     * Creates an HttpRequest.
     */
    protected HttpRequest() {}

    /**
     * A builder of {@link HttpRequest}s.
     * {@Incubating}
     *
     * <p> {@code HttpRequest.Builder}s are created by calling {@link
     * HttpRequest#newBuilder(URI)} or {@link HttpRequest#newBuilder()}.
     *
     * <p> Each of the setter methods in this class modifies the state of the
     * builder and returns <i>this</i> (ie. the same instance). The methods are
     * not synchronized and should not be called from multiple threads without
     * external synchronization.
     * <p>Note, that not all request headers may be set by user code. Some are
     * restricted for security reasons and others such as the headers relating
     * to authentication, redirection and cookie management are managed by
     * specific APIs rather than through directly user set headers.
     *
     * <p> The {@linkplain #build() build} method returns a new {@code
     * HttpRequest} each time it is invoked.
     *
     * @since 9
     */
    public abstract static class Builder {

        /**
         * Creates a Builder.
         */
        protected Builder() {}

        /**
         * Sets this {@code HttpRequest}'s request {@code URI}.
         *
         * @param uri the request URI
         * @return this request builder
         * @throws IllegalArgumentException if the {@code URI} scheme is not
         *         supported.
         */
        public abstract Builder uri(URI uri);

        /**
         * Request server to acknowledge request before sending request
         * body. This is disabled by default. If enabled, the server is requested
         * to send an error response or a {@code 100 Continue} response before the client
         * sends the request body. This means the request processor for the
         * request will not be invoked until this interim response is received.
         *
         * @param enable {@code true} if Expect continue to be sent
         * @return this request builder
         */
        public abstract Builder expectContinue(boolean enable);

        /**
         * Overrides the {@link HttpClient#version()  } setting for this
         * request. This sets the version requested. The corresponding
         * {@link HttpResponse} should be checked for the version that was
         * used.
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

//        /**
//         * Overrides the {@code ProxySelector} set on the request's client for this
//         * request.
//         *
//         * @param proxy the ProxySelector to use
//         * @return this request builder
//         */
//        public abstract Builder proxy(ProxySelector proxy);

        /**
         * Adds the given name value pairs to the set of headers for this
         * request. The supplied {@code String}s must alternate as names and values.
         *
         * @param headers the list of String name value pairs
         * @return this request builder
         * @throws IllegalArgumentException if there is an odd number of
         *                                  parameters
         */
        // TODO (spec): consider signature change
        // public abstract Builder headers(java.util.Map.Entry<String,String>... headers);
        public abstract Builder headers(String... headers);

        /**
         * Sets a timeout for this request. If the response is not received
         * within the specified timeout then a {@link HttpTimeoutException} is
         * thrown from {@link HttpClient#send(jdk.incubator.http.HttpRequest,
         * jdk.incubator.http.HttpResponse.BodyHandler) HttpClient::send} or
         * {@link HttpClient#sendAsync(jdk.incubator.http.HttpRequest,
         * jdk.incubator.http.HttpResponse.BodyHandler) HttpClient::sendAsync}
         * completes exceptionally with a {@code HttpTimeoutException}. The effect
         * of not setting a timeout is the same as setting an infinite Duration, ie.
         * block forever.
         *
         * @param duration
         * @return this request builder
         */
        public abstract Builder timeout(Duration duration);

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
         * Sets the request method of this builder to GET.
         *
         * @return a {@code HttpRequest}
         */
        public abstract Builder GET();

        /**
         * Sets the request method of this builder to POST and sets its
         * request body processor to the given value.
         *
         * @param body the body processor
         *
         * @return a {@code HttpRequest}
         */
        public abstract Builder POST(BodyProcessor body);

        /**
         * Sets the request method of this builder to PUT and sets its
         * request body processor to the given value.
         *
         * @param body the body processor
         *
         * @return a {@code HttpRequest}
         */
        public abstract Builder PUT(BodyProcessor body);

        /**
         * Sets the request method of this builder to DELETE and sets its
         * request body processor to the given value.
         *
         * @param body the body processor
         *
         * @return a {@code HttpRequest}
         */

        public abstract Builder DELETE(BodyProcessor body);

        /**
         * Sets the request method and request body of this builder to the
         * given values.
         *
         * @param body the body processor
         * @param method the method to use
         * @return a {@code HttpRequest}
         * @throws IllegalArgumentException if an unrecognized method is used
         */
        public abstract Builder method(String method, BodyProcessor body);

        /**
         * Builds and returns a {@link HttpRequest}.
         *
         * @return the request
         */
        public abstract HttpRequest build();

        /**
         * Returns an exact duplicate copy of this {@code Builder} based on current
         * state. The new builder can then be modified independently of this
         * builder.
         *
         * @return an exact copy of this Builder
         */
        public abstract Builder copy();
    }

    /**
     * Creates a {@code HttpRequest} builder.
     *
     * @param uri the request URI
     * @return a new request builder
     * @throws IllegalArgumentException if the URI scheme is not supported.
     */
    public static HttpRequest.Builder newBuilder(URI uri) {
        return new HttpRequestBuilderImpl(uri);
    }

    /**
     * Creates a {@code HttpRequest} builder.
     *
     * @return a new request builder
     */
    public static HttpRequest.Builder newBuilder() {
        return new HttpRequestBuilderImpl();
    }

    /**
     * Returns an {@code Optional} containing the {@link BodyProcessor}
     * set on this request. If no {@code BodyProcessor} was set in the
     * requests's builder, then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this request's
     *         {@code BodyProcessor}
     */
    public abstract Optional<BodyProcessor> bodyProcessor();

    /**
     * Returns the request method for this request. If not set explicitly,
     * the default method for any request is "GET".
     *
     * @return this request's method
     */
    public abstract String method();

    /**
     * Returns the duration for this request.
     *
     * @return this requests duration
     */
    public abstract Duration duration();

    /**
     * Returns this request's {@link HttpRequest.Builder#expectContinue(boolean)
     * expect continue } setting.
     *
     * @return this request's expect continue setting
     */
    public abstract boolean expectContinue();

    /**
     * Returns this request's request {@code URI}.
     *
     * @return this request's URI
     */
    public abstract URI uri();

    /**
     * Returns the HTTP protocol version that will be requested for this
     * {@code HttpRequest}. The corresponding {@link HttpResponse} should be
     * queried to determine the version that was actually used.
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
     * A request body handler which sends no request body.
     *
     * @return a BodyProcessor
     */
    public static BodyProcessor noBody() {
        return new RequestProcessors.EmptyProcessor();
    }

    /**
     * A processor which converts high level Java objects into flows of
     * {@link java.nio.ByteBuffer}s suitable for sending as request bodies.
     * {@Incubating}
     * <p>
     * {@code BodyProcessor}s implement {@link Flow.Publisher} which means they
     * act as a publisher of byte buffers.
     * <p>
     * The HTTP client implementation subscribes to the processor in
     * order to receive the flow of outgoing data buffers. The normal semantics
     * of {@link Flow.Subscriber} and {@link Flow.Publisher} are implemented
     * by the library and expected from processor implementations.
     * Each outgoing request results in one {@code Subscriber} subscribing to the
     * {@code Publisher} in order to provide the sequence of {@code ByteBuffer}s containing
     * the request body. {@code ByteBuffer}s must be allocated by the processor,
     * and must not be accessed after being handed over to the library.
     * These subscriptions complete normally when the request is fully
     * sent, and can be canceled or terminated early through error. If a request
     * needs to be resent for any reason, then a new subscription is created
     * which is expected to generate the same data as before.
     */
    public interface BodyProcessor extends Flow.Publisher<ByteBuffer> {

        /**
         * Returns a request body processor whose body is the given {@code String},
         * converted using the {@link java.nio.charset.StandardCharsets#UTF_8 UTF_8}
         * character set.
         *
         * @param body the String containing the body
         * @return a BodyProcessor
         */
        static BodyProcessor fromString(String body) {
            return fromString(body, StandardCharsets.UTF_8);
        }

        /**
         * Returns a request body processor whose body is the given {@code String}, converted
         * using the given character set.
         *
         * @param s the String containing the body
         * @param charset the character set to convert the string to bytes
         * @return a BodyProcessor
         */
        static BodyProcessor fromString(String s, Charset charset) {
            return new RequestProcessors.StringProcessor(s, charset);
        }

        /**
         * A request body processor that reads its data from an {@link java.io.InputStream}.
         * A {@link Supplier} of {@code InputStream} is used in case the request needs
         * to be sent again as the content is not buffered. The {@code Supplier} may return
         * {@code null} on subsequent attempts in which case, the request fails.
         *
         * @param streamSupplier a Supplier of open InputStreams
         * @return a BodyProcessor
         */
        // TODO (spec): specify that the stream will be closed
        static BodyProcessor fromInputStream(Supplier<? extends InputStream> streamSupplier) {
            return new RequestProcessors.InputStreamProcessor(streamSupplier);
        }

        /**
         * Returns a request body processor whose body is the given byte array.
         *
         * @param buf the byte array containing the body
         * @return a BodyProcessor
         */
        static BodyProcessor fromByteArray(byte[] buf) {
            return new RequestProcessors.ByteArrayProcessor(buf);
        }

        /**
         * Returns a request body processor whose body is the content of the given byte
         * array of {@code length} bytes starting from the specified
         * {@code offset}.
         *
         * @param buf the byte array containing the body
         * @param offset the offset of the first byte
         * @param length the number of bytes to use
         * @return a BodyProcessor
         */
        static BodyProcessor fromByteArray(byte[] buf, int offset, int length) {
            return new RequestProcessors.ByteArrayProcessor(buf, offset, length);
        }

        /**
         * A request body processor that takes data from the contents of a File.
         *
         * @param path the path to the file containing the body
         * @return a BodyProcessor
         * @throws java.io.FileNotFoundException if path not found
         */
        static BodyProcessor fromFile(Path path) throws FileNotFoundException {
            return new RequestProcessors.FileProcessor(path);
        }

        /**
         * A request body processor that takes data from an {@code Iterable} of byte arrays.
         * An {@link Iterable} is provided which supplies {@link Iterator} instances.
         * Each attempt to send the request results in one invocation of the
         * {@code Iterable}
         *
         * @param iter an Iterable of byte arrays
         * @return a BodyProcessor
         */
        static BodyProcessor fromByteArrays(Iterable<byte[]> iter) {
            return new RequestProcessors.IterableProcessor(iter);
        }
        /**
         * Returns the content length for this request body. May be zero
         * if no request content being sent, greater than zero for a fixed
         * length content, and less than zero for an unknown content length.
         *
         * @return the content length for this request body if known
         */
        long contentLength();

//        /**
//         * Returns a used {@code ByteBuffer} to this request processor. When the
//         * HTTP implementation has finished sending the contents of a buffer,
//         * this method is called to return it to the processor for re-use.
//         *
//         * @param buffer a used ByteBuffer
//         */
        //void returnBuffer(ByteBuffer buffer);
    }
}
