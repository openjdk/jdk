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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import jdk.incubator.http.ResponseProcessors.MultiFile;
import jdk.incubator.http.ResponseProcessors.MultiProcessorImpl;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.net.ssl.SSLParameters;

/**
 * Represents a response to a {@link HttpRequest}. A {@code HttpResponse} is
 * available when the response status code and headers have been received, and
 * typically after the response body has also been received. This depends on
 * the response body handler provided when sending the request. In all cases,
 * the response body handler is invoked before the body is read. This gives
 * applications an opportunity to decide how to handle the body.
 *
 * <p> Methods are provided in this class for accessing the response headers,
 * and response body.
 * <p>
 * <b>Response handlers and processors</b>
 * <p>
 * Response bodies are handled at two levels. Application code supplies a response
 * handler ({@link BodyHandler}) which may examine the response status code
 * and headers, and which then returns a {@link BodyProcessor} to actually read
 * (or discard) the body and convert it into some useful Java object type. The handler
 * can return one of the pre-defined processor types, or a custom processor, or
 * if the body is to be discarded, it can call {@link BodyProcessor#discard(Object)
 * BodyProcessor.discard()} and return a processor which discards the response body.
 * Static implementations of both handlers and processors are provided in
 * {@link BodyHandler BodyHandler} and {@link BodyProcessor BodyProcessor} respectively.
 * In all cases, the handler functions provided are convenience implementations
 * which ignore the supplied status code and
 * headers and return the relevant pre-defined {@code BodyProcessor}.
 * <p>
 * See {@link BodyHandler} for example usage.
 *
 * @param <T> the response body type
 * @since 9
 */
public abstract class HttpResponse<T> {

    /**
     * Creates an HttpResponse.
     */
    protected HttpResponse() { }

    /**
     * Returns the status code for this response.
     *
     * @return the response code
     */
    public abstract int statusCode();

    /**
     * Returns the initial {@link HttpRequest} that initiated the exchange.
     *
     * @return the request
     */
    public abstract HttpRequest request();

    /**
     * Returns the final {@link HttpRequest} that was sent on the wire for the
     * exchange ( may, or may not, be the same as the initial request ).
     *
     * @return the request
     */
    public abstract HttpRequest finalRequest();

    /**
     * Returns the received response headers.
     *
     * @return the response headers
     */
    public abstract HttpHeaders headers();

    /**
     * Returns the received response trailers, if there are any, when they
     * become available. For many response processor types this will be at the same
     * time as the {@code HttpResponse} itself is available. In such cases, the
     * returned {@code CompletableFuture} will be already completed.
     *
     * @return a CompletableFuture of the response trailers (may be empty)
     */
    public abstract CompletableFuture<HttpHeaders> trailers();

    /**
     * Returns the body. Depending on the type of {@code T}, the returned body may
     * represent the body after it was read (such as {@code byte[]}, or
     * {@code String}, or {@code Path}) or it may represent an object with
     * which the body is read, such as an {@link java.io.InputStream}.
     *
     * @return the body
     */
    public abstract T body();

    /**
     * Returns the {@link javax.net.ssl.SSLParameters} in effect for this
     * response. Returns {@code null} if this is not a HTTPS response.
     *
     * @return the SSLParameters associated with the response
     */
    public abstract SSLParameters sslParameters();

    /**
     * Returns the {@code URI} that the response was received from. This may be
     * different from the request {@code URI} if redirection occurred.
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
     * A handler for response bodies. This is a function that takes two
     * parameters: the response status code, and the response headers,
     * and which returns a {@link BodyProcessor}. The function is always called
     * just before the response body is read. Its implementation may examine the
     * status code or headers and must decide, whether to accept the response
     * body or discard it, and if accepting it, exactly how to handle it.
     * <p>
     * Some pre-defined implementations which do not utilize the status code
     * or headers (meaning the body is always accepted) are defined:
     * <ul><li>{@link #asByteArray() }</li>
     * <li>{@link #asByteArrayConsumer(java.util.function.Consumer)
     * asByteArrayConsumer(Consumer)}</li>
     * <li>{@link #asFileDownload(java.nio.file.Path,OpenOption...)
     * asFileDownload(Path,OpenOption...)}</li>
     * <li>{@link #discard(Object) }</li>
     * <li>{@link #asString(java.nio.charset.Charset)
     * asString(Charset)}</li></ul>
     * <p>
     * These implementations return the equivalent {@link BodyProcessor}.
     * Alternatively, the handler can be used to examine the status code
     * or headers and return different body processors as appropriate.
     * <p>
     * <b>Examples of handler usage</b>
     * <p>
     * The first example uses one of the predefined handler functions which
     * ignore the response headers and status, and always process the response
     * body in the same way.
     * <pre>
     * {@code
     *      HttpResponse<Path> resp = HttpRequest
     *              .create(URI.create("http://www.foo.com"))
     *              .GET()
     *              .response(BodyHandler.asFile(Paths.get("/tmp/f")));
     * }
     * </pre>
     * Note, that even though these pre-defined handlers ignore the status code
     * and headers, this information is still accessible from the {@code HttpResponse}
     * when it is returned.
     * <p>
     * In the second example, the function returns a different processor depending
     * on the status code.
     * <pre>
     * {@code
     *      HttpResponse<Path> resp1 = HttpRequest
     *              .create(URI.create("http://www.foo.com"))
     *              .GET()
     *              .response(
     *                  (status, headers) -> status == 200
     *                      ? BodyProcessor.asFile(Paths.get("/tmp/f"))
     *                      : BodyProcessor.discard(Paths.get("/NULL")));
     * }
     * </pre>
     *
     * @param <T> the response body type.
     */
    @FunctionalInterface
    public interface BodyHandler<T> {

        /**
         * Return a {@link BodyProcessor BodyProcessor} considering the given response status
         * code and headers. This method is always called before the body is read
         * and its implementation can decide to keep the body and store it somewhere
         * or else discard it, by  returning the {@code BodyProcessor} returned
         * from {@link BodyProcessor#discard(java.lang.Object) discard()}.
         *
         * @param statusCode the HTTP status code received
         * @param responseHeaders the response headers received
         * @return
         */
        public BodyProcessor<T> apply(int statusCode, HttpHeaders responseHeaders);

        /**
         * Returns a response body handler which discards the response body and
         * uses the given value as a replacement for it.
         *
         * @param <U> the response body type
         * @param value the value of U to return as the body
         * @return
         */
        public static <U> BodyHandler<U> discard(U value) {
            return (status, headers) -> BodyProcessor.discard(value);
        }

        /**
         * Returns a {@code BodyHandler<String>} that returns a
         * {@link BodyProcessor BodyProcessor}{@code <String>} obtained from
         * {@link BodyProcessor#asString(java.nio.charset.Charset)
         * BodyProcessor.asString(Charset)}. If a charset is provided, the
         * body is decoded using it. If charset is {@code null} then the processor
         * tries to determine the character set from the {@code Content-encoding}
         * header. If that charset is not supported then
         * {@link java.nio.charset.StandardCharsets#UTF_8 UTF_8} is used.
         *
         * @param charset the name of the charset to interpret the body as. If
         * {@code null} then charset determined from Content-encoding header
         * @return a response handler
         */
        public static BodyHandler<String> asString(Charset charset) {
            return (status, headers) -> {
                if (charset != null) {
                    return BodyProcessor.asString(charset);
                }
                return BodyProcessor.asString(charsetFrom(headers));
            };
        }

        /**
         * Get the Charset from the Content-encoding header. Defaults to
         * UTF_8
         */
        private static Charset charsetFrom(HttpHeaders headers) {
            String encoding = headers.firstValue("Content-encoding")
                    .orElse("UTF_8");
            try {
                return Charset.forName(encoding);
            } catch (IllegalArgumentException e) {
                return StandardCharsets.UTF_8;
            }
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodyProcessor BodyProcessor}{@code <Path>} obtained from
         * {@link BodyProcessor#asFile(Path) BodyProcessor.asFile(Path)}.
         * <p>
         * When the {@code HttpResponse} object is returned, the body has been completely
         * written to the file, and {@link #body()} returns a reference to its
         * {@link Path}.
         *
         * @param file the file to store the body in
         * @return a response handler
         */
        public static BodyHandler<Path> asFile(Path file) {
            return (status, headers) -> BodyProcessor.asFile(file);
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodyProcessor BodyProcessor}&lt;{@link Path}&gt;
         * where the download directory is specified, but the filename is
         * obtained from the {@code Content-Disposition} response header. The
         * {@code Content-Disposition} header must specify the <i>attachment</i> type
         * and must also contain a
         * <i>filename</i> parameter. If the filename specifies multiple path
         * components only the final component is used as the filename (with the
         * given directory name). When the {@code HttpResponse} object is
         * returned, the body has been completely written to the file and {@link
         * #body()} returns a {@code Path} object for the file. The returned {@code Path} is the
         * combination of the supplied directory name and the file name supplied
         * by the server. If the destination directory does not exist or cannot
         * be written to, then the response will fail with an {@link IOException}.
         *
         * @param directory the directory to store the file in
         * @param openOptions open options
         * @return a response handler
         */
        public static BodyHandler<Path> asFileDownload(Path directory, OpenOption... openOptions) {
            return (status, headers) -> {
                String dispoHeader = headers.firstValue("Content-Disposition")
                        .orElseThrow(() -> unchecked(new IOException("No Content-Disposition")));
                if (!dispoHeader.startsWith("attachment;")) {
                    throw unchecked(new IOException("Unknown Content-Disposition type"));
                }
                int n = dispoHeader.indexOf("filename=");
                if (n == -1) {
                    throw unchecked(new IOException("Bad Content-Disposition type"));
                }
                int lastsemi = dispoHeader.lastIndexOf(';');
                String disposition;
                if (lastsemi < n) {
                    disposition = dispoHeader.substring(n + 9);
                } else {
                    disposition = dispoHeader.substring(n + 9, lastsemi);
                }
                Path file = Paths.get(directory.toString(), disposition);
                return BodyProcessor.asFile(file, openOptions);
            };
        }

        private static UncheckedIOException unchecked(IOException e) {
            return new UncheckedIOException(e);
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodyProcessor BodyProcessor}{@code <Path>} obtained from
         * {@link BodyProcessor#asFile(java.nio.file.Path, java.nio.file.OpenOption...)
         * BodyProcessor.asFile(Path,OpenOption...)}.
         * <p>
         * When the {@code HttpResponse} object is returned, the body has been completely
         * written to the file, and {@link #body()} returns a reference to its
         * {@link Path}.
         *
         * @param file the filename to store the body in
         * @param openOptions any options to use when opening/creating the file
         * @return a response handler
         */
        public static BodyHandler<Path> asFile(Path file, OpenOption... openOptions) {
            return (status, headers) -> BodyProcessor.asFile(file, openOptions);
        }

        /**
         * Returns a {@code BodyHandler<Void>} that returns a
         * {@link BodyProcessor BodyProcessor}{@code <Void>} obtained from
         * {@link BodyProcessor#asByteArrayConsumer(java.util.function.Consumer)
         * BodyProcessor.asByteArrayConsumer(Consumer)}.
         * <p>
         * When the {@code HttpResponse} object is returned, the body has been completely
         * written to the consumer.
         *
         * @param consumer a Consumer to accept the response body
         * @return a a response handler
         */
        public static BodyHandler<Void> asByteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
            return (status, headers) -> BodyProcessor.asByteArrayConsumer(consumer);
        }

        /**
         * Returns a {@code BodyHandler<byte[]>} that returns a
         * {@link BodyProcessor BodyProcessor}&lt;{@code byte[]}&gt; obtained
         * from {@link BodyProcessor#asByteArray() BodyProcessor.asByteArray()}.
         * <p>
         * When the {@code HttpResponse} object is returned, the body has been completely
         * written to the byte array.
         *
         * @return a response handler
         */
        public static BodyHandler<byte[]> asByteArray() {
            return (status, headers) -> BodyProcessor.asByteArray();
        }

        /**
         * Returns a {@code BodyHandler<String>} that returns a
         * {@link BodyProcessor BodyProcessor}{@code <String>} obtained from
         * {@link BodyProcessor#asString(java.nio.charset.Charset)
         * BodyProcessor.asString(Charset)}. The body is
         * decoded using the character set specified in
         * the {@code Content-encoding} response header. If there is no such
         * header, or the character set is not supported, then
         * {@link java.nio.charset.StandardCharsets#UTF_8 UTF_8} is used.
         * <p>
         * When the {@code HttpResponse} object is returned, the body has been completely
         * written to the string.
         *
         * @return a response handler
         */
        public static BodyHandler<String> asString() {
            return (status, headers) -> BodyProcessor.asString(charsetFrom(headers));
        }
    }

    /**
     * A processor for response bodies. The object acts as a
     * {@link Flow.Subscriber}&lt;{@link ByteBuffer}&gt; to the HTTP client implementation
     * which publishes ByteBuffers containing the response body. The processor
     * converts the incoming buffers of data to some user-defined object type {@code T}.
     * <p>
     * The {@link #getBody()} method returns a {@link CompletionStage}{@code <T>}
     * that provides the response body object. The {@code CompletionStage} must
     * be obtainable at any time. When it completes depends on the nature
     * of type {@code T}. In many cases, when {@code T} represents the entire body after being
     * read then it completes after the body has been read. If {@code T} is a streaming
     * type such as {@link java.io.InputStream} then it completes before the
     * body has been read, because the calling code uses it to consume the data.
     *
     * @param <T> the response body type
     */
    public interface BodyProcessor<T>
            extends Flow.Subscriber<ByteBuffer> {

        /**
         * Returns a {@code CompletionStage} which when completed will return the
         * response body object.
         *
         * @return a CompletionStage for the response body
         */
        public CompletionStage<T> getBody();

        /**
         * Returns a body processor which stores the response body as a {@code
         * String} converted using the given {@code Charset}.
         * <p>
         * The {@link HttpResponse} using this processor is available after the
         * entire response has been read.
         *
         * @param charset the character set to convert the String with
         * @return a body processor
         */
        public static BodyProcessor<String> asString(Charset charset) {
            return new ResponseProcessors.ByteArrayProcessor<>(
                    bytes -> new String(bytes, charset)
            );
        }

        /**
         * Returns a {@code BodyProcessor} which stores the response body as a
         * byte array.
         * <p>
         * The {@link HttpResponse} using this processor is available after the
         * entire response has been read.
         *
         * @return a body processor
         */
        public static BodyProcessor<byte[]> asByteArray() {
            return new ResponseProcessors.ByteArrayProcessor<>(
                    Function.identity() // no conversion
            );
        }

        /**
         * Returns a {@code BodyProcessor} which stores the response body in a
         * file opened with the given options and name. The file will be opened
         * with the given options using
         * {@link java.nio.channels.FileChannel#open(java.nio.file.Path,java.nio.file.OpenOption...)
         * FileChannel.open} just before the body is read. Any exception thrown will be returned
         * or thrown from {@link HttpClient#send(jdk.incubator.http.HttpRequest,
         * jdk.incubator.http.HttpResponse.BodyHandler) HttpClient::send}
         * or {@link HttpClient#sendAsync(jdk.incubator.http.HttpRequest,
         * jdk.incubator.http.HttpResponse.BodyHandler) HttpClient::sendAsync}
         * as appropriate.
         * <p>
         * The {@link HttpResponse} using this processor is available after the
         * entire response has been read.
         *
         * @param file the file to store the body in
         * @param openOptions the list of options to open the file with
         * @return a body processor
         */
        public static BodyProcessor<Path> asFile(Path file, OpenOption... openOptions) {
            return new ResponseProcessors.PathProcessor(file, openOptions);
        }

        /**
         * Returns a {@code BodyProcessor} which provides the incoming body
         * data to the provided Consumer of {@code Optional<byte[]>}. Each
         * call to {@link Consumer#accept(java.lang.Object) Consumer.accept()}
         * will contain a non empty {@code Optional}, except for the final invocation after
         * all body data has been read, when the {@code Optional} will be empty.
         * <p>
         * The {@link HttpResponse} using this processor is available after the
         * entire response has been read.
         *
         * @param consumer a Consumer of byte arrays
         * @return a BodyProcessor
         */
        public static BodyProcessor<Void> asByteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
            return new ResponseProcessors.ConsumerProcessor(consumer);
        }

        /**
         * Returns a {@code BodyProcessor} which stores the response body in a
         * file opened with the given name. Has the same effect as calling
         * {@link #asFile(java.nio.file.Path, java.nio.file.OpenOption...) asFile}
         * with the standard open options {@code CREATE} and {@code WRITE}
         * <p>
         * The {@link HttpResponse} using this processor is available after the
         * entire response has been read.
         *
         * @param file the file to store the body in
         * @return a body processor
         */
        public static BodyProcessor<Path> asFile(Path file) {
            return new ResponseProcessors.PathProcessor(
                    file,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }

        /**
         * Returns a response processor which discards the response body. The
         * supplied value is the value that will be returned from
         * {@link HttpResponse#body()}.
         *
         * @param <U> The type of the response body
         * @param value the value to return from HttpResponse.body()
         * @return a {@code BodyProcessor}
         */
        public static <U> BodyProcessor<U> discard(U value) {
            return new ResponseProcessors.NullProcessor<>(Optional.ofNullable(value));
        }
    }

    /**
     * A response processor for a HTTP/2 multi response. A multi response
     * comprises a main response, and zero or more additional responses. Each
     * additional response is sent by the server in response to requests that
     * the server also generates. Additional responses are typically resources
     * that the server expects the client will need which are related to the
     * initial request.
     * <p>
     * Note. Instead of implementing this interface, applications should consider
     * first using the mechanism (built on this interface) provided by
     * {@link MultiProcessor#asMap(java.util.function.Function, boolean)
     * MultiProcessor.asMap()} which is a slightly simplified, but
     * general purpose interface.
     * <p>
     * The server generated requests are also known as <i>push promises</i>.
     * The server is permitted to send any number of these requests up to the
     * point where the main response is fully received. Therefore, after
     * completion of the main response, the final number of additional
     * responses is known. Additional responses may be canceled, but given that
     * the server does not wait for any acknowledgment before sending the
     * response, this must be done quickly to avoid unnecessary data transmission.
     *
     * <p> {@code MultiProcessor}s are parameterized with a type {@code U} which
     * represents some meaningful aggregate of the responses received. This
     * would typically be a collection of response or response body objects.
     *
     * @param <U> a type representing the aggregated results
     * @param <T> a type representing all of the response bodies
     *
     * @since 9
     */
    public interface MultiProcessor<U,T> {
        /**
         * Called for the main request and each push promise that is received.
         * The first call will always be for the main request that was sent
         * by the caller. This {@link HttpRequest} parameter
         * represents the initial request or subsequent PUSH_PROMISE. The
         * implementation must return an {@code Optional} of {@link BodyHandler} for
         * the response body. Different handlers (of the same type) can be returned
         * for different pushes within the same multi send. If no handler
         * (an empty {@code Optional}) is returned, then the push will be canceled. It is
         * an error to not return a valid {@code BodyHandler} for the initial (main) request.
         *
         * @param request the main request or subsequent push promise
         *
         * @return an optional body handler
         */
        Optional<BodyHandler<T>> onRequest(HttpRequest request);

        /**
         * Called for each response received. For each request either one of
         * onResponse() or onError() is guaranteed to be called, but not both.
         *
         * [Note] The reason for switching to this callback interface rather
         * than using CompletableFutures supplied to onRequest() is that there
         * is a subtle interaction between those CFs and the CF returned from
         * completion() (or when onComplete() was called formerly). The completion()
         * CF will not complete until after all of the work done by the onResponse()
         * calls is done. Whereas if you just create CF's dependent on a supplied
         * CF (to onRequest()) then the implementation has no visibility of the
         * dependent CFs and can't guarantee to call onComplete() (or complete
         * the completion() CF) after the dependent CFs complete.
         *
         * @param response the response received
         */
        void onResponse(HttpResponse<T> response);

        /**
         * Called if an error occurs receiving a response. For each request
         * either one of onResponse() or onError() is guaranteed to be called,
         * but not both.
         *
         * @param request
         * @param t the Throwable that caused the error
         */
        void onError(HttpRequest request, Throwable t);

        /**
         * Returns a {@link java.util.concurrent.CompletableFuture}{@code <U>}
         * which completes when the aggregate result object itself is available.
         * It is expected that the returned {@code CompletableFuture} will depend
         * on one of the given {@code CompletableFuture<Void}s which themselves complete
         * after all individual responses associated with the multi response
         * have completed, or after all push promises have been received.
         * <p>
         * @implNote Implementations might follow the pattern shown below
         * <pre>
         * {@code
         *      CompletableFuture<U> completion(
         *              CompletableFuture<Void> onComplete,
         *              CompletableFuture<Void> onFinalPushPromise)
         *      {
         *          return onComplete.thenApply((v) -> {
         *              U u = ... instantiate and populate a U instance
         *              return u;
         *          });
         *      }
         * }
         * </pre>
         * <p>
         *
         * @param onComplete a CompletableFuture which completes after all
         * responses have been received relating to this multi request.
         *
         * @param onFinalPushPromise CompletableFuture which completes after all
         * push promises have been received.
         *
         * @return the aggregate CF response object
         */
        CompletableFuture<U> completion(CompletableFuture<Void> onComplete,
                CompletableFuture<Void> onFinalPushPromise);

        /**
         * Returns a general purpose handler for multi responses. The aggregated
         * result object produced by this handler is a
         * {@code Map<HttpRequest,CompletableFuture<HttpResponse<V>>>}. Each
         * request (both the original user generated request and each server
         * generated push promise) is returned as a key of the map. The value
         * corresponding to each key is a
         * {@code CompletableFuture<HttpResponse<V>>}.
         * <p>
         * There are two ways to use these handlers, depending on the value of
         * the <i>completion</I> parameter. If completion is true, then the
         * aggregated result will be available after all responses have
         * themselves completed. If <i>completion</i> is false, then the
         * aggregated result will be available immediately after the last push
         * promise was received. In the former case, this implies that all the
         * CompletableFutures in the map values will have completed. In the
         * latter case, they may or may not have completed yet.
         * <p>
         * The simplest way to use these handlers is to set completion to
         * {@code true}, and then all (results) values in the Map will be
         * accessible without blocking.
         * <p>
         * See {@link #asMap(java.util.function.Function, boolean)
         * }
         * for a code sample of using this interface.
         *
         * @param <V> the body type used for all responses
         * @param pushHandler a function invoked for each request or push
         * promise
         * @param completion {@code true} if the aggregate CompletableFuture
         * completes after all responses have been received, or {@code false}
         * after all push promises received.
         *
         * @return a MultiProcessor
         */
        public static <V> MultiProcessor<MultiMapResult<V>,V> asMap(
            Function<HttpRequest, Optional<HttpResponse.BodyHandler<V>>> pushHandler,
                boolean completion) {

            return new MultiProcessorImpl<V>(pushHandler, completion);
        }

        /**
         * Returns a general purpose handler for multi responses. This is a
         * convenience method which invokes {@link #asMap(java.util.function.Function,boolean)
         * asMap(Function, true)} meaning that the aggregate result
         * object completes after all responses have been received.
         * <p>
         * <b>Example usage:</b>
         * <br>
         * <pre>
         * {@code
         *          HttpRequest request = HttpRequest.newBuilder()
         *                  .uri(URI.create("https://www.foo.com/"))
         *                  .GET()
         *                  .build();
         *
         *          HttpClient client = HttpClient.newHttpClient();
         *
         *          Map<HttpRequest,CompletableFuture<HttpResponse<String>>> results = client
         *              .sendAsync(request, MultiProcessor.asMap(
         *                  (req) -> Optional.of(HttpResponse.BodyHandler.asString())))
         *              .join();
         * }</pre>
         * <p>
         * The lambda in this example is the simplest possible implementation,
         * where neither the incoming requests are examined, nor the response
         * headers, and every push that the server sends is accepted. When the
         * join() call returns, all {@code HttpResponse}s and their associated
         * body objects are available.
         *
         * @param <V>
         * @param pushHandler a function invoked for each request or push
         * promise
         * @return a MultiProcessor
         */
        public static <V> MultiProcessor<MultiMapResult<V>,V> asMap(
            Function<HttpRequest, Optional<HttpResponse.BodyHandler<V>>> pushHandler) {

            return asMap(pushHandler, true);
        }

        /**
         * Returns a {@code MultiProcessor} which writes the response bodies to
         * files under a given root directory and which returns an aggregate
         * response map that is a {@code Map<HttpRequest, HttpResponse<Path>>}.
         * The keyset of the {@code Map} represents the original request and any
         * additional requests generated by the server. The values are the
         * responses containing the paths of the destination files. Each file
         * uses the URI path of the request relative to the destination parent
         * directorycprovided.
         *
         * <p>
         * All incoming additional requests (push promises) are accepted by this
         * multi response processor. Errors are effectively ignored and any
         * failed responses are simply omitted from the result {@code Map}.
         * Other implementations of {@code MultiProcessor} may handle these
         * situations.
         *
         * <p>
         * <b>Example usage</b>
         * <pre>
         * {@code
         *    HttpClient client = ..
         *    HttpRequest request = HttpRequest
         *               .create(new URI("https://www.foo.com/"))
         *               .version(Version.HTTP2)
         *               .GET();
         *
         *    Map<HttpRequest, HttpResponse<Path>>> map = client
         *               .sendAsync(HttpResponse.MultiProcessor.multiFile("/usr/destination"))
         *               .join();
         *
         * }
         * </pre>
         * TEMPORARILY REMOVING THIS FROM API. MIGHT NOT BE NEEDED.
         *
         * @param destination the destination parent directory of all response
         * bodies
         * @return a MultiProcessor
         */
        private static MultiProcessor<MultiMapResult<Path>,Path> multiFile(Path destination) {
            MultiFile mf = new MultiFile(destination);
            return new MultiProcessorImpl<Path>(mf::handlePush, true);
        }
    }
}
