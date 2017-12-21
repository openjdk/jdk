/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URLPermission;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Represents one HTTP request which can be sent to a server.
 * {@Incubating }
 *
 * <p> {@code HttpRequest} instances are built from {@code HttpRequest}
 * {@linkplain HttpRequest.Builder builders}. {@code HttpRequest} builders
 * are obtained by calling {@link HttpRequest#newBuilder(URI) HttpRequest.newBuilder}.
 * A request's {@linkplain URI}, headers and body can be set. Request bodies are
 * provided through a {@link BodyPublisher} object supplied to the
 * {@link Builder#DELETE(BodyPublisher) DELETE},
 * {@link Builder#POST(BodyPublisher) POST} or
 * {@link Builder#PUT(BodyPublisher) PUT} methods.
 * {@link Builder#GET() GET} does not take a body. Once all required
 * parameters have been set in the builder, {@link Builder#build() } is called
 * to return the {@code HttpRequest}. Builders can also be copied and modified
 * multiple times in order to build multiple related requests that differ in
 * some parameters.
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
 *              .POST(BodyPublisher.fromString("Hello world"))
 *              .build(),
 *          BodyHandler.asFile(Paths.get("/path"))
 *      );
 *      int statusCode = response.statusCode();
 *      Path body = response.body(); // should be "/path"
 * }
 * </pre>
 *
 * <p> The request is sent and the response obtained by calling one of the
 * following methods in {@link HttpClient}.
 * <ul><li>{@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler)} blocks
 * until the entire request has been sent and the response has been received.</li>
 * <li>{@link HttpClient#sendAsync(HttpRequest,HttpResponse.BodyHandler)} sends the
 * request and receives the response asynchronously. Returns immediately with a
 * {@link java.util.concurrent.CompletableFuture CompletableFuture}&lt;{@link
 * HttpResponse}&gt;.</li>
 * <li>{@link HttpClient#sendAsync(HttpRequest, HttpResponse.MultiSubscriber) }
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
 * <p> Request bodies can be sent using one of the convenience request publisher
 * implementations below, provided in {@link BodyPublisher}. Alternatively, a
 * custom Publisher implementation can be used.
 * <ul>
 * <li>{@link BodyPublisher#fromByteArray(byte[]) fromByteArray(byte[])} from byte array</li>
 * <li>{@link BodyPublisher#fromByteArrays(Iterable) fromByteArrays(Iterable)}
 *      from an Iterable of byte arrays</li>
 * <li>{@link BodyPublisher#fromFile(java.nio.file.Path) fromFile(Path)} from the file located
 *     at the given Path</li>
 * <li>{@link BodyPublisher#fromString(java.lang.String) fromString(String)} from a String </li>
 * <li>{@link BodyPublisher#fromInputStream(Supplier) fromInputStream}({@link Supplier}&lt;
 *      {@link InputStream}&gt;) from an InputStream obtained from a Supplier</li>
 * <li>{@link BodyPublisher#noBody() } no request body is sent</li>
 * </ul>
 *
 * <p> <b>Response bodies</b>
 *
 * <p> Responses bodies are handled at two levels. When sending the request,
 * a response body handler is specified. This is a function ({@linkplain
 * HttpResponse.BodyHandler}) which will be called with the response status code
 * and headers, once they are received. This function is then expected to return
 * a {@link HttpResponse.BodySubscriber}{@code <T>} which is then used to read
 * the response body, converting it into an instance of T. After this occurs,
 * the response becomes available in a {@link HttpResponse}, and {@link
 * HttpResponse#body()} can then be called to obtain the actual body. Some
 * implementations and examples of usage of both {@link
 * HttpResponse.BodySubscriber} and {@link HttpResponse.BodyHandler} are
 * provided in {@link HttpResponse}:
 *
 * <p> <b>Some of the pre-defined body handlers</b><br>
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
 * request. These are handled using a special response subscriber called {@link
 * HttpResponse.MultiSubscriber}.
 *
 * <p> <b>Blocking/asynchronous behavior and thread usage</b>
 *
 * <p> There are two styles of request sending: <i>synchronous</i> and
 * <i>asynchronous</i>. {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler) }
 * blocks the calling thread until the request has been sent and the response received.
 *
 * <p> {@link HttpClient#sendAsync(HttpRequest, HttpResponse.BodyHandler)} is
 * asynchronous and returns immediately with a {@link CompletableFuture}&lt;{@link
 * HttpResponse}&gt; and when this object completes (possibly in a different
 * thread) the response has been received.
 *
 * <p> {@link HttpClient#sendAsync(HttpRequest, HttpResponse.MultiSubscriber)}
 * is the variant for multi responses and is also asynchronous.
 *
 * <p> Instances of {@code CompletableFuture} can be combined in different ways
 * to declare the dependencies among several asynchronous tasks, while allowing
 * for the maximum level of parallelism to be utilized.
 *
 * <p> <a id="securitychecks"></a><b>Security checks</b></a>
 *
 * <p> If a security manager is present then security checks are performed by
 * the HTTP Client's sending methods. An appropriate {@link URLPermission} is
 * required to access the destination server, and proxy server if one has
 * been configured. The {@code URLPermission} form used to access proxies uses a
 * method parameter of {@code "CONNECT"} (for all kinds of proxying) and a URL
 * string  of the form {@code "socket://host:port"} where host and port specify
 * the proxy's address.
 *
 * <p> In this implementation, if an explicit {@linkplain
 * HttpClient.Builder#executor(Executor) executor} has not been set for an
 * {@code HttpClient}, and a security manager has been installed, then the
 * default executor will execute asynchronous and dependent tasks in a context
 * that is granted no permissions. Custom {@linkplain HttpRequest.BodyPublisher
 * request body publishers}, {@linkplain HttpResponse.BodyHandler response body
 * handlers}, {@linkplain HttpResponse.BodySubscriber response body subscribers},
 * and {@linkplain WebSocket.Listener WebSocket Listeners}, if executing
 * operations that require privileges, should do so  within an appropriate
 * {@linkplain AccessController#doPrivileged(PrivilegedAction) privileged context}.
 *
 * <p> <b>Examples</b>
 * <pre>{@code
 *      HttpClient client = HttpClient
 *              .newBuilder()
 *              .build();
 *
 *      HttpRequest request = HttpRequest
 *              .newBuilder(new URI("http://www.foo.com/"))
 *              .POST(BodyPublisher.fromString("Hello world"))
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
 *
 * <p> Unless otherwise stated, {@code null} parameter values will cause methods
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
     * A builder of {@linkplain HttpRequest HTTP Requests}.
     * {@Incubating}
     *
     * <p> Instances of {@code HttpRequest.Builder} are created by calling {@link
     * HttpRequest#newBuilder(URI)} or {@link HttpRequest#newBuilder()}.
     *
     * <p> Each of the setter methods in this class modifies the state of the
     * builder and returns <i>this</i> (ie. the same instance). The methods are
     * not synchronized and should not be called from multiple threads without
     * external synchronization.
     *
     * <p> Note, that not all request headers may be set by user code. Some are
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
         *         supported
         */
        public abstract Builder uri(URI uri);

        /**
         * Requests the server to acknowledge the request before sending the
         * body. This is disabled by default. If enabled, the server is
         * requested to send an error response or a {@code 100 Continue}
         * response before the client sends the request body. This means the
         * request publisher for the request will not be invoked until this
         * interim response is received.
         *
         * @param enable {@code true} if Expect continue to be sent
         * @return this request builder
         */
        public abstract Builder expectContinue(boolean enable);

        /**
         * Sets the preferred {@link HttpClient.Version} for this request.
         *
         * <p> The corresponding {@link HttpResponse} should be checked for the
         * version that was actually used. If the version is not set in a
         * request, then the version requested will be that of the sending
         * {@link HttpClient}.
         *
         * @param version the HTTP protocol version requested
         * @return this request builder
         */
        public abstract Builder version(HttpClient.Version version);

        /**
         * Adds the given name value pair to the set of headers for this request.
         * The given value is added to the list of values for that name.
         *
         * @param name the header name
         * @param value the header value
         * @return this request builder
         * @throws IllegalArgumentException if the header name or value is not
         *         valid, see <a href="https://tools.ietf.org/html/rfc7230#section-3.2">
         *         RFC 7230 section-3.2</a>
         */
        public abstract Builder header(String name, String value);

        /**
         * Adds the given name value pairs to the set of headers for this
         * request. The supplied {@code String} instances must alternate as
         * header names and header values.
         * To add several values to the same name then the same name must
         * be supplied with each new value.
         *
         * @param headers the list of name value pairs
         * @return this request builder
         * @throws IllegalArgumentException if there are an odd number of
         *         parameters, or if a header name or value is not valid, see
         *         <a href="https://tools.ietf.org/html/rfc7230#section-3.2">
         *         RFC 7230 section-3.2</a>
         */
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
         * @param duration the timeout duration
         * @return this request builder
         * @throws IllegalArgumentException if the duration is non-positive
         */
        public abstract Builder timeout(Duration duration);

        /**
         * Sets the given name value pair to the set of headers for this
         * request. This overwrites any previously set values for name.
         *
         * @param name the header name
         * @param value the header value
         * @return this request builder
         * @throws IllegalArgumentException if the header name or value is not valid,
         *         see <a href="https://tools.ietf.org/html/rfc7230#section-3.2">
         *         RFC 7230 section-3.2</a>
         */
        public abstract Builder setHeader(String name, String value);

        /**
         * Sets the request method of this builder to GET.
         * This is the default.
         *
         * @return a {@code HttpRequest}
         */
        public abstract Builder GET();

        /**
         * Sets the request method of this builder to POST and sets its
         * request body publisher to the given value.
         *
         * @param bodyPublisher the body publisher
         *
         * @return a {@code HttpRequest}
         */
        public abstract Builder POST(BodyPublisher bodyPublisher);

        /**
         * Sets the request method of this builder to PUT and sets its
         * request body publisher to the given value.
         *
         * @param bodyPublisher the body publisher
         *
         * @return a {@code HttpRequest}
         */
        public abstract Builder PUT(BodyPublisher bodyPublisher);

        /**
         * Sets the request method of this builder to DELETE and sets its
         * request body publisher to the given value.
         *
         * @param bodyPublisher the body publisher
         *
         * @return a {@code HttpRequest}
         */

        public abstract Builder DELETE(BodyPublisher bodyPublisher);

        /**
         * Sets the request method and request body of this builder to the
         * given values.
         *
         * @apiNote The {@linkplain BodyPublisher#noBody() noBody} request
         * body publisher can be used where no request body is required or
         * appropriate.
         *
         * @param method the method to use
         * @param bodyPublisher the body publisher
         * @return a {@code HttpRequest}
         * @throws IllegalArgumentException if the method is unrecognised
         */
        public abstract Builder method(String method, BodyPublisher bodyPublisher);

        /**
         * Builds and returns a {@link HttpRequest}.
         *
         * @return the request
         * @throws IllegalStateException if a URI has not been set
         */
        public abstract HttpRequest build();

        /**
         * Returns an exact duplicate copy of this {@code Builder} based on
         * current state. The new builder can then be modified independently of
         * this builder.
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
     * Returns an {@code Optional} containing the {@link BodyPublisher} set on
     * this request. If no {@code BodyPublisher} was set in the requests's
     * builder, then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this request's {@code BodyPublisher}
     */
    public abstract Optional<BodyPublisher> bodyPublisher();

    /**
     * Returns the request method for this request. If not set explicitly,
     * the default method for any request is "GET".
     *
     * @return this request's method
     */
    public abstract String method();

    /**
     * Returns an {@code Optional} containing this request's timeout duration.
     * If the timeout duration was not set in the request's builder, then the
     * {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this request's timeout duration
     */
    public abstract Optional<Duration> timeout();

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
     * Returns an {@code Optional} containing the HTTP protocol version that
     * will be requested for this {@code HttpRequest}. If the version was not
     * set in the request's builder, then the {@code Optional} is empty.
     * In that case, the version requested will be that of the sending
     * {@link HttpClient}. The corresponding {@link HttpResponse} should be
     * queried to determine the version that was actually used.
     *
     * @return HTTP protocol version
     */
    public abstract Optional<HttpClient.Version> version();

    /**
     * The (user-accessible) request headers that this request was (or will be)
     * sent with.
     *
     * @return this request's HttpHeaders
     */
    public abstract HttpHeaders headers();

    /**
     * Tests this HTTP request instance for equality with the given object.
     *
     * <p> If the given object is not an {@code HttpRequest} then this
     * method returns {@code false}. Two HTTP requests are equal if their URI,
     * method, and headers fields are all equal.
     *
     * <p> This method satisfies the general contract of the {@link
     * Object#equals(Object) Object.equals} method.
     *
     * @param obj the object to which this object is to be compared
     * @return {@code true} if, and only if, the given object is an {@code
     *         HttpRequest} that is equal to this HTTP request
     */
    @Override
    public final boolean equals(Object obj) {
       if (! (obj instanceof HttpRequest))
           return false;
       HttpRequest that = (HttpRequest)obj;
       if (!that.method().equals(this.method()))
           return false;
       if (!that.uri().equals(this.uri()))
           return false;
       if (!that.headers().equals(this.headers()))
           return false;
       return true;
    }

    /**
     * Computes a hash code for this HTTP request instance.
     *
     * <p> The hash code is based upon the HTTP request's URI, method, and
     * header components, and satisfies the general contract of the
     * {@link Object#hashCode Object.hashCode} method.
     *
     * @return the hash-code value for this HTTP request
     */
    public final int hashCode() {
        return method().hashCode()
                + uri().hashCode()
                + headers().hashCode();
    }

    /**
     * A Publisher which converts high level Java objects into flows of
     * byte buffers suitable for sending as request bodies.
     * {@Incubating}
     *
     * <p> The {@code BodyPublisher} class implements {@link Flow.Publisher
     * Flow.Publisher&lt;ByteBuffer&gt;} which means that a {@code BodyPublisher}
     * acts as a publisher of {@linkplain ByteBuffer byte buffers}.
     *
     * <p> The HTTP client implementation subscribes to the publisher in order
     * to receive the flow of outgoing data buffers. The normal semantics of
     * {@link Flow.Subscriber} and {@link Flow.Publisher} are implemented by the
     * library and are expected from publisher implementations. Each outgoing
     * request results in one {@code Subscriber} subscribing to the {@code
     * BodyPublisher} in order to provide the sequence of byte buffers
     * containing the request body.
     * Instances of {@code ByteBuffer} published  by the publisher must be
     * allocated by the publisher, and must not be accessed after being handed
     * over to the library.
     * These subscriptions complete normally when the request is fully sent,
     * and can be canceled or terminated early through error. If a request
     * needs to be resent for any reason, then a new subscription is created
     * which is expected to generate the same data as before.
     *
     * <p> A publisher that reports a {@linkplain #contentLength() content
     * length} of {@code 0} may not be subscribed to by the HTTP client
     * implementation, as it has effectively no data to publish.
     */
    public interface BodyPublisher extends Flow.Publisher<ByteBuffer> {

        /**
         * Returns a request body publisher whose body is retrieved from the
         * given {@code Flow.Publisher}. The returned request body publisher
         * has an unknown content length.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodyPublisher} and {@code Flow.Publisher}, where the amount of
         * request body that the publisher will publish is unknown.
         *
         * @param publisher the publisher responsible for publishing the body
         * @return a BodyPublisher
         */
        static BodyPublisher fromPublisher(Flow.Publisher<? extends ByteBuffer> publisher) {
            return new RequestPublishers.PublisherAdapter(publisher, -1L);
        }

        /**
         * Returns a request body publisher whose body is retrieved from the
         * given {@code Flow.Publisher}. The returned request body publisher
         * has the given content length.
         *
         * <p> The given {@code contentLength} is a positive number, that
         * represents the exact amount of bytes the {@code publisher} must
         * publish.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodyPublisher} and {@code Flow.Publisher}, where the amount of
         * request body that the publisher will publish is known.
         *
         * @param publisher the publisher responsible for publishing the body
         * @param contentLength a positive number representing the exact
         *                      amount of bytes the publisher will publish
         * @throws IllegalArgumentException if the content length is
         *                                  non-positive
         * @return a BodyPublisher
         */
        static BodyPublisher fromPublisher(Flow.Publisher<? extends ByteBuffer> publisher,
                                           long contentLength) {
            if (contentLength < 1)
                throw new IllegalArgumentException("non-positive contentLength: " + contentLength);
            return new RequestPublishers.PublisherAdapter(publisher, contentLength);
        }

        /**
         * Returns a request body publisher whose body is the given {@code
         * String}, converted using the {@link StandardCharsets#UTF_8 UTF_8}
         * character set.
         *
         * @param body the String containing the body
         * @return a BodyPublisher
         */
        static BodyPublisher fromString(String body) {
            return fromString(body, UTF_8);
        }

        /**
         * Returns a request body publisher whose body is the given {@code
         * String}, converted using the given character set.
         *
         * @param s the String containing the body
         * @param charset the character set to convert the string to bytes
         * @return a BodyPublisher
         */
        static BodyPublisher fromString(String s, Charset charset) {
            return new RequestPublishers.StringPublisher(s, charset);
        }

        /**
         * A request body publisher that reads its data from an {@link
         * InputStream}. A {@link Supplier} of {@code InputStream} is used in
         * case the request needs to be repeated, as the content is not buffered.
         * The {@code Supplier} may return {@code null} on subsequent attempts,
         * in which case the request fails.
         *
         * @param streamSupplier a Supplier of open InputStreams
         * @return a BodyPublisher
         */
        // TODO (spec): specify that the stream will be closed
        static BodyPublisher fromInputStream(Supplier<? extends InputStream> streamSupplier) {
            return new RequestPublishers.InputStreamPublisher(streamSupplier);
        }

        /**
         * Returns a request body publisher whose body is the given byte array.
         *
         * @param buf the byte array containing the body
         * @return a BodyPublisher
         */
        static BodyPublisher fromByteArray(byte[] buf) {
            return new RequestPublishers.ByteArrayPublisher(buf);
        }

        /**
         * Returns a request body publisher whose body is the content of the
         * given byte array of {@code length} bytes starting from the specified
         * {@code offset}.
         *
         * @param buf the byte array containing the body
         * @param offset the offset of the first byte
         * @param length the number of bytes to use
         * @return a BodyPublisher
         * @throws IndexOutOfBoundsException if the sub-range is defined to be
         *                                   out-of-bounds
         */
        static BodyPublisher fromByteArray(byte[] buf, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, buf.length);
            return new RequestPublishers.ByteArrayPublisher(buf, offset, length);
        }

        private static String pathForSecurityCheck(Path path) {
            return path.toFile().getPath();
        }

        /**
         * A request body publisher that takes data from the contents of a File.
         *
         * @param path the path to the file containing the body
         * @return a BodyPublisher
         * @throws java.io.FileNotFoundException if the path is not found
         * @throws SecurityException if a security manager has been installed
         *          and it denies {@link SecurityManager#checkRead(String)
         *          read access} to the given file
         */
        static BodyPublisher fromFile(Path path) throws FileNotFoundException {
            Objects.requireNonNull(path);
            SecurityManager sm = System.getSecurityManager();
            if (sm != null)
                sm.checkRead(pathForSecurityCheck(path));
            if (Files.notExists(path))
                throw new FileNotFoundException(path + " not found");
            return new RequestPublishers.FilePublisher(path);
        }

        /**
         * A request body publisher that takes data from an {@code Iterable}
         * of byte arrays. An {@link Iterable} is provided which supplies
         * {@link Iterator} instances. Each attempt to send the request results
         * in one invocation of the {@code Iterable}.
         *
         * @param iter an Iterable of byte arrays
         * @return a BodyPublisher
         */
        static BodyPublisher fromByteArrays(Iterable<byte[]> iter) {
            return new RequestPublishers.IterablePublisher(iter);
        }

        /**
         * A request body publisher which sends no request body.
         *
         * @return a BodyPublisher which completes immediately and sends
         *         no request body.
         */
        static BodyPublisher noBody() {
            return new RequestPublishers.EmptyPublisher();
        }

        /**
         * Returns the content length for this request body. May be zero
         * if no request body being sent, greater than zero for a fixed
         * length content, or less than zero for an unknown content length.
         *
         * This method may be invoked before the publisher is subscribed to.
         * This method may be invoked more than once by the HTTP client
         * implementation, and MUST return the same constant value each time.
         *
         * @return the content length for this request body, if known
         */
        long contentLength();
    }
}
