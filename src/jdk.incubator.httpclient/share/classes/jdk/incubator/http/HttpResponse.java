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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import jdk.incubator.http.ResponseSubscribers.MultiSubscriberImpl;
import static jdk.incubator.http.internal.common.Utils.unchecked;
import static jdk.incubator.http.internal.common.Utils.charsetFrom;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.AccessControlContext;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.net.ssl.SSLParameters;

/**
 * Represents a response to a {@link HttpRequest}.
 * {@Incubating}
 *
 * <p> A {@code HttpResponse} is available when the response status code and
 * headers have been received, and typically after the response body has also
 * been received. This depends on the response body handler provided when
 * sending the request. In all cases, the response body handler is invoked
 * before the body is read. This gives applications an opportunity to decide
 * how to handle the body.
 *
 * <p> Methods are provided in this class for accessing the response headers,
 * and response body.
 *
 * <p><b>Response handlers and subscribers</b>
 *
 * <p> Response bodies are handled at two levels. Application code supplies a
 * response handler ({@link BodyHandler}) which may examine the response status
 * code and headers, and which then returns a {@link BodySubscriber} to actually
 * read (or discard) the body and convert it into some useful Java object type.
 * The handler can return one of the pre-defined subscriber types, or a custom
 * subscriber, or if the body is to be discarded it can call {@link
 * BodySubscriber#discard(Object) discard} and return a subscriber which
 * discards the response body. Static implementations of both handlers and
 * subscribers are provided in {@linkplain BodyHandler BodyHandler} and
 * {@linkplain BodySubscriber BodySubscriber} respectively. In all cases, the
 * handler functions provided are convenience implementations which ignore the
 * supplied status code and headers and return the relevant pre-defined {@code
 * BodySubscriber}.
 *
 * <p> See {@link BodyHandler} for example usage.
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
     * Returns the {@link HttpRequest} corresponding to this response.
     *
     * <p> This may not be the original request provided by the caller,
     * for example, if that request was redirected.
     *
     * @see #previousResponse()
     *
     * @return the request
     */
    public abstract HttpRequest request();

    /**
     * Returns an {@code Optional} containing the previous intermediate response
     * if one was received. An intermediate response is one that is received
     * as a result of redirection or authentication. If no previous response
     * was received then an empty {@code Optional} is returned.
     *
     * @return an Optional containing the HttpResponse, if any.
     */
    public abstract Optional<HttpResponse<T>> previousResponse();

    /**
     * Returns the received response headers.
     *
     * @return the response headers
     */
    public abstract HttpHeaders headers();

    /**
     * Returns the body. Depending on the type of {@code T}, the returned body
     * may represent the body after it was read (such as {@code byte[]}, or
     * {@code String}, or {@code Path}) or it may represent an object with
     * which the body is read, such as an {@link java.io.InputStream}.
     *
     * <p> If this {@code HttpResponse} was returned from an invocation of
     * {@link #previousResponse()} then this method returns {@code null}
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


    private static String pathForSecurityCheck(Path path) {
        return path.toFile().getPath();
    }

    /** A body handler that is further restricted by a given ACC. */
    interface UntrustedBodyHandler<T> extends BodyHandler<T> {
        void setAccessControlContext(AccessControlContext acc);
    }

    /**
     * A Path body handler.
     *
     * Note: Exists mainly too allow setting of the senders ACC post creation of
     * the handler.
     */
    static class PathBodyHandler implements UntrustedBodyHandler<Path> {
        private final Path file;
        private final OpenOption[]openOptions;
        private volatile AccessControlContext acc;

        PathBodyHandler(Path file, OpenOption... openOptions) {
            this.file = file;
            this.openOptions = openOptions;
        }

        @Override
        public void setAccessControlContext(AccessControlContext acc) {
            this.acc = acc;
        }

        @Override
        public BodySubscriber<Path> apply(int statusCode, HttpHeaders headers) {
            ResponseSubscribers.PathSubscriber bs = (ResponseSubscribers.PathSubscriber)
                    BodySubscriber.asFileImpl(file, openOptions);
            bs.setAccessControlContext(acc);
            return bs;
        }
    }

    // Similar to Path body handler, but for file download. Supports setting ACC.
    static class FileDownloadBodyHandler implements UntrustedBodyHandler<Path> {
        private final Path directory;
        private final OpenOption[]openOptions;
        private volatile AccessControlContext acc;

        FileDownloadBodyHandler(Path directory, OpenOption... openOptions) {
            this.directory = directory;
            this.openOptions = openOptions;
        }

        @Override
        public void setAccessControlContext(AccessControlContext acc) {
            this.acc = acc;
        }

        @Override
        public BodySubscriber<Path> apply(int statusCode, HttpHeaders headers) {
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

            ResponseSubscribers.PathSubscriber bs = (ResponseSubscribers.PathSubscriber)
                    BodySubscriber.asFileImpl(file, openOptions);
            bs.setAccessControlContext(acc);
            return bs;
        }
    }

    /**
     * A handler for response bodies.
     * {@Incubating}
     *
     * <p> This is a function that takes two parameters: the response status code,
     * and the response headers, and which returns a {@linkplain BodySubscriber}.
     * The function is always called just before the response body is read. Its
     * implementation may examine the status code or headers and must decide,
     * whether to accept the response body or discard it, and if accepting it,
     * exactly how to handle it.
     *
     * <p> Some pre-defined implementations which do not utilize the status code
     * or headers (meaning the body is always accepted) are defined:
     * <ul><li>{@link #asByteArray() }</li>
     * <li>{@link #asByteArrayConsumer(java.util.function.Consumer)
     * asByteArrayConsumer(Consumer)}</li>
     * <li>{@link #asString(java.nio.charset.Charset) asString(Charset)}</li>
     * <li>{@link #asFile(Path, OpenOption...)
     * asFile(Path,OpenOption...)}</li>
     * <li>{@link #asFileDownload(java.nio.file.Path,OpenOption...)
     * asFileDownload(Path,OpenOption...)}</li>
     * <li>{@link #asInputStream() asInputStream()}</li>
     * <li>{@link #discard(Object) }</li>
     * <li>{@link #buffering(BodyHandler, int)
     * buffering(BodyHandler,int)}</li>
     * </ul>
     *
     * <p> These implementations return the equivalent {@link BodySubscriber}.
     * Alternatively, the handler can be used to examine the status code
     * or headers and return different body subscribers as appropriate.
     *
     * <p><b>Examples of handler usage</b>
     *
     * <p> The first example uses one of the predefined handler functions which
     * ignores the response headers and status, and always process the response
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
     * and headers, this information is still accessible from the
     * {@code HttpResponse} when it is returned.
     *
     * <p> In the second example, the function returns a different subscriber
     * depending on the status code.
     * <pre>
     * {@code
     *      HttpResponse<Path> resp1 = HttpRequest
     *              .create(URI.create("http://www.foo.com"))
     *              .GET()
     *              .response(
     *                  (status, headers) -> status == 200
     *                      ? BodySubscriber.asFile(Paths.get("/tmp/f"))
     *                      : BodySubscriber.discard(Paths.get("/NULL")));
     * }
     * </pre>
     *
     * @param <T> the response body type
     */
    @FunctionalInterface
    public interface BodyHandler<T> {

        /**
         * Returns a {@link BodySubscriber BodySubscriber} considering the given
         * response status code and headers. This method is always called before
         * the body is read and its implementation can decide to keep the body
         * and store it somewhere, or else discard it by returning the {@code
         * BodySubscriber} returned from {@link BodySubscriber#discard(Object)
         * discard}.
         *
         * @param statusCode the HTTP status code received
         * @param responseHeaders the response headers received
         * @return a body subscriber
         */
        public BodySubscriber<T> apply(int statusCode, HttpHeaders responseHeaders);

        /**
         * Returns a response body handler that returns a {@link BodySubscriber
         * BodySubscriber}{@code <Void>} obtained from {@linkplain
         * BodySubscriber#fromSubscriber(Subscriber)}, with the given
         * {@code subscriber}.
         *
         * <p> The response body is not available through this, or the {@code
         * HttpResponse} API, but instead all response body is forwarded to the
         * given {@code subscriber}, which should make it available, if
         * appropriate, through some other mechanism, e.g. an entry in a
         * database, etc.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * <p> For example:
         * <pre> {@code
         *  TextSubscriber subscriber = new TextSubscriber();
         *  HttpResponse<Void> response = client.sendAsync(request,
         *      BodyHandler.fromSubscriber(subscriber)).join();
         *  System.out.println(response.statusCode());
         * }</pre>
         *
         * @param subscriber the subscriber
         * @return a response body handler
         */
        public static BodyHandler<Void>
        fromSubscriber(Subscriber<? super List<ByteBuffer>> subscriber) {
            Objects.requireNonNull(subscriber);
            return (status, headers) -> BodySubscriber.fromSubscriber(subscriber,
                                                                      s -> null);
        }

        /**
         * Returns a response body handler that returns a {@link BodySubscriber
         * BodySubscriber}{@code <T>} obtained from {@link
         * BodySubscriber#fromSubscriber(Subscriber, Function)}, with the
         * given {@code subscriber} and {@code finisher} function.
         *
         * <p> The given {@code finisher} function is applied after the given
         * subscriber's {@code onComplete} has been invoked. The {@code finisher}
         * function is invoked with the given subscriber, and returns a value
         * that is set as the response's body.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * <p> For example:
         * <pre> {@code
         * TextSubscriber subscriber = ...;  // accumulates bytes and transforms them into a String
         * HttpResponse<String> response = client.sendAsync(request,
         *     BodyHandler.fromSubscriber(subscriber, TextSubscriber::getTextResult)).join();
         * String text = response.body();
         * }</pre>
         *
         * @param <S> the type of the Subscriber
         * @param <T> the type of the response body
         * @param subscriber the subscriber
         * @param finisher a function to be applied after the subscriber has completed
         * @return a response body handler
         */
        public static <S extends Subscriber<? super List<ByteBuffer>>,T> BodyHandler<T>
        fromSubscriber(S subscriber, Function<S,T> finisher) {
            Objects.requireNonNull(subscriber);
            Objects.requireNonNull(finisher);
            return (status, headers) -> BodySubscriber.fromSubscriber(subscriber,
                                                                      finisher);
        }

        /**
         * Returns a response body handler which discards the response body and
         * uses the given value as a replacement for it.
         *
         * @param <U> the response body type
         * @param value the value of U to return as the body, may be {@code null}
         * @return a response body handler
         */
        public static <U> BodyHandler<U> discard(U value) {
            return (status, headers) -> BodySubscriber.discard(value);
        }

        /**
         * Returns a {@code BodyHandler<String>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <String>} obtained from
         * {@link BodySubscriber#asString(Charset) BodySubscriber.asString(Charset)}.
         * The body is decoded using the given character set.
         *
         * @param charset the character set to convert the body with
         * @return a response body handler
         */
        public static BodyHandler<String> asString(Charset charset) {
            Objects.requireNonNull(charset);
            return (status, headers) -> BodySubscriber.asString(charset);
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <Path>} obtained from
         * {@link BodySubscriber#asFile(Path, OpenOption...)
         * BodySubscriber.asFile(Path,OpenOption...)}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the file, and {@link #body()} returns a
         * reference to its {@link Path}.
         *
         * @param file the filename to store the body in
         * @param openOptions any options to use when opening/creating the file
         * @return a response body handler
         * @throws SecurityException If a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file. The {@link
         *          SecurityManager#checkDelete(String) checkDelete} method is
         *          invoked to check delete access if the file is opened with
         *          the {@code DELETE_ON_CLOSE} option.
         */
        public static BodyHandler<Path> asFile(Path file, OpenOption... openOptions) {
            Objects.requireNonNull(file);
            List<OpenOption> opts = List.of(openOptions);
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                String fn = pathForSecurityCheck(file);
                sm.checkWrite(fn);
                if (opts.contains(StandardOpenOption.DELETE_ON_CLOSE))
                    sm.checkDelete(fn);
                if (opts.contains(StandardOpenOption.READ))
                    sm.checkRead(fn);
            }
            return new PathBodyHandler(file, openOptions);
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <Path>} obtained from
         * {@link BodySubscriber#asFile(Path) BodySubscriber.asFile(Path)}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the file, and {@link #body()} returns a
         * reference to its {@link Path}.
         *
         * @param file the file to store the body in
         * @return a response body handler
         * @throws SecurityException if a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file
         */
        public static BodyHandler<Path> asFile(Path file) {
            return BodyHandler.asFile(file, StandardOpenOption.CREATE,
                                            StandardOpenOption.WRITE);
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodySubscriber BodySubscriber}&lt;{@link Path}&gt;
         * where the download directory is specified, but the filename is
         * obtained from the {@code Content-Disposition} response header. The
         * {@code Content-Disposition} header must specify the <i>attachment</i>
         * type and must also contain a <i>filename</i> parameter. If the
         * filename specifies multiple path components only the final component
         * is used as the filename (with the given directory name).
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the file and {@link #body()} returns a
         * {@code Path} object for the file. The returned {@code Path} is the
         * combination of the supplied directory name and the file name supplied
         * by the server. If the destination directory does not exist or cannot
         * be written to, then the response will fail with an {@link IOException}.
         *
         * @param directory the directory to store the file in
         * @param openOptions open options
         * @return a response body handler
         * @throws SecurityException If a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file. The {@link
         *          SecurityManager#checkDelete(String) checkDelete} method is
         *          invoked to check delete access if the file is opened with
         *          the {@code DELETE_ON_CLOSE} option.
         */
         //####: check if the dir exists and is writable??
        public static BodyHandler<Path> asFileDownload(Path directory,
                                                       OpenOption... openOptions) {
            Objects.requireNonNull(directory);
            List<OpenOption> opts = List.of(openOptions);
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                String fn = pathForSecurityCheck(directory);
                sm.checkWrite(fn);
                if (opts.contains(StandardOpenOption.DELETE_ON_CLOSE))
                    sm.checkDelete(fn);
                if (opts.contains(StandardOpenOption.READ))
                    sm.checkRead(fn);
            }
            return new FileDownloadBodyHandler(directory, openOptions);
        }

        /**
         * Returns a {@code BodyHandler<InputStream>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <InputStream>} obtained
         * from {@link BodySubscriber#asInputStream() BodySubscriber.asInputStream}.
         *
         * <p> When the {@code HttpResponse} object is returned, the response
         * headers will have been completely read, but the body may not have
         * been fully received yet. The {@link #body()} method returns an
         * {@link InputStream} from which the body can be read as it is received.
         *
         * @apiNote See {@link BodySubscriber#asInputStream()} for more information.
         *
         * @return a response body handler
         */
        public static BodyHandler<InputStream> asInputStream() {
            return (status, headers) -> BodySubscriber.asInputStream();
        }

        /**
         * Returns a {@code BodyHandler<Void>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <Void>} obtained from
         * {@link BodySubscriber#asByteArrayConsumer(Consumer)
         * BodySubscriber.asByteArrayConsumer(Consumer)}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the consumer.
         *
         * @param consumer a Consumer to accept the response body
         * @return a response body handler
         */
        public static BodyHandler<Void> asByteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
            Objects.requireNonNull(consumer);
            return (status, headers) -> BodySubscriber.asByteArrayConsumer(consumer);
        }

        /**
         * Returns a {@code BodyHandler<byte[]>} that returns a
         * {@link BodySubscriber BodySubscriber}&lt;{@code byte[]}&gt; obtained
         * from {@link BodySubscriber#asByteArray() BodySubscriber.asByteArray()}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the byte array.
         *
         * @return a response body handler
         */
        public static BodyHandler<byte[]> asByteArray() {
            return (status, headers) -> BodySubscriber.asByteArray();
        }

        /**
         * Returns a {@code BodyHandler<String>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <String>} obtained from
         * {@link BodySubscriber#asString(java.nio.charset.Charset)
         * BodySubscriber.asString(Charset)}. The body is
         * decoded using the character set specified in
         * the {@code Content-encoding} response header. If there is no such
         * header, or the character set is not supported, then
         * {@link java.nio.charset.StandardCharsets#UTF_8 UTF_8} is used.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the string.
         *
         * @return a response body handler
         */
        public static BodyHandler<String> asString() {
            return (status, headers) -> BodySubscriber.asString(charsetFrom(headers));
        }

        /**
         * Returns a {@code BodyHandler} which, when invoked, returns a {@linkplain
         * BodySubscriber#buffering(BodySubscriber,int) buffering BodySubscriber}
         * that buffers data before delivering it to the downstream subscriber.
         * These {@code BodySubscriber} instances are created by calling
         * {@linkplain BodySubscriber#buffering(BodySubscriber,int)
         * BodySubscriber.buffering} with a subscriber obtained from the given
         * downstream handler and the {@code bufferSize} parameter.
         *
         * @param downstreamHandler the downstream handler
         * @param bufferSize the buffer size parameter passed to {@linkplain
         *        BodySubscriber#buffering(BodySubscriber,int) BodySubscriber.buffering}
         * @return a body handler
         * @throws IllegalArgumentException if {@code bufferSize <= 0}
         */
         public static <T> BodyHandler<T> buffering(BodyHandler<T> downstreamHandler,
                                                    int bufferSize) {
             Objects.requireNonNull(downstreamHandler);
             if (bufferSize <= 0)
                 throw new IllegalArgumentException("must be greater than 0");
             return (status, headers) -> BodySubscriber
                     .buffering(downstreamHandler.apply(status, headers),
                                bufferSize);
         }
    }

    /**
     * A subscriber for response bodies.
     * {@Incubating}
     *
     * <p> The object acts as a {@link Flow.Subscriber}&lt;{@link List}&lt;{@link
     * ByteBuffer}&gt;&gt; to the HTTP client implementation, which publishes
     * unmodifiable lists of ByteBuffers containing the response body. The Flow
     * of data, as well as the order of ByteBuffers in the Flow lists, is a
     * strictly ordered representation of the response body. Both the Lists and
     * the ByteBuffers, once passed to the subscriber, are no longer used by the
     * HTTP client. The subscriber converts the incoming buffers of data to some
     * user-defined object type {@code T}.
     *
     * <p> The {@link #getBody()} method returns a {@link CompletionStage}{@code
     * <T>} that provides the response body object. The {@code CompletionStage}
     * must be obtainable at any time. When it completes depends on the nature
     * of type {@code T}. In many cases, when {@code T} represents the entire
     * body after being read then it completes after the body has been read. If
     * {@code T} is a streaming type such as {@link java.io.InputStream} then it
     * completes before the body has been read, because the calling code uses it
     * to consume the data.
     *
     * @apiNote To ensure that all resources associated with the
     * corresponding exchange are properly released, an implementation
     * of {@code BodySubscriber} must ensure to {@linkplain
     * Flow.Subscription#request request} more data until {@link
     * #onComplete() onComplete} or {@link #onError(Throwable) onError}
     * are signalled, or {@linkplain Flow.Subscription#request cancel} its
     * {@linkplain #onSubscribe(Flow.Subscription) subscription}
     * if unable or unwilling to do so.
     * Calling {@code cancel} before exhausting the data may cause
     * the underlying HTTP connection to be closed and prevent it
     * from being reused for subsequent operations.
     *
     * @param <T> the response body type
     */
    public interface BodySubscriber<T>
            extends Flow.Subscriber<List<ByteBuffer>> {

        /**
         * Returns a {@code CompletionStage} which when completed will return
         * the response body object.
         *
         * @return a CompletionStage for the response body
         */
        public CompletionStage<T> getBody();

        /**
         * Returns a body subscriber that forwards all response body to the
         * given {@code Flow.Subscriber}. The {@linkplain #getBody()} completion
         * stage} of the returned body subscriber completes after one of the
         * given subscribers {@code onComplete} or {@code onError} has been
         * invoked.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * @param <S> the type of the Subscriber
         * @param subscriber the subscriber
         * @return a body subscriber
         */
        public static <S extends Subscriber<? super List<ByteBuffer>>> BodySubscriber<Void>
        fromSubscriber(S subscriber) {
            return new ResponseSubscribers.SubscriberAdapter<S,Void>(subscriber, s -> null);
        }

        /**
         * Returns a body subscriber that forwards all response body to the
         * given {@code Flow.Subscriber}. The {@linkplain #getBody()} completion
         * stage} of the returned body subscriber completes after one of the
         * given subscribers {@code onComplete} or {@code onError} has been
         * invoked.
         *
         * <p> The given {@code finisher} function is applied after the given
         * subscriber's {@code onComplete} has been invoked. The {@code finisher}
         * function is invoked with the given subscriber, and returns a value
         * that is set as the response's body.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * @param <S> the type of the Subscriber
         * @param <T> the type of the response body
         * @param subscriber the subscriber
         * @param finisher a function to be applied after the subscriber has
         *                 completed
         * @return a body subscriber
         */
        public static <S extends Subscriber<? super List<ByteBuffer>>,T> BodySubscriber<T>
        fromSubscriber(S subscriber,
                       Function<S,T> finisher) {
            return new ResponseSubscribers.SubscriberAdapter<S,T>(subscriber, finisher);
        }

        /**
         * Returns a body subscriber which stores the response body as a {@code
         * String} converted using the given {@code Charset}.
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @param charset the character set to convert the String with
         * @return a body subscriber
         */
        public static BodySubscriber<String> asString(Charset charset) {
            Objects.requireNonNull(charset);
            return new ResponseSubscribers.ByteArraySubscriber<>(
                    bytes -> new String(bytes, charset)
            );
        }

        /**
         * Returns a {@code BodySubscriber} which stores the response body as a
         * byte array.
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @return a body subscriber
         */
        public static BodySubscriber<byte[]> asByteArray() {
            return new ResponseSubscribers.ByteArraySubscriber<>(
                    Function.identity() // no conversion
            );
        }

        // no security check
        private static BodySubscriber<Path> asFileImpl(Path file, OpenOption... openOptions) {
            return new ResponseSubscribers.PathSubscriber(file, openOptions);
        }

        /**
         * Returns a {@code BodySubscriber} which stores the response body in a
         * file opened with the given options and name. The file will be opened
         * with the given options using {@link FileChannel#open(Path,OpenOption...)
         * FileChannel.open} just before the body is read. Any exception thrown
         * will be returned or thrown from {@link HttpClient#send(HttpRequest,
         * BodyHandler) HttpClient::send} or {@link HttpClient#sendAsync(HttpRequest,
         * BodyHandler) HttpClient::sendAsync} as appropriate.
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @param file the file to store the body in
         * @param openOptions the list of options to open the file with
         * @return a body subscriber
         * @throws SecurityException If a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file. The {@link
         *          SecurityManager#checkDelete(String) checkDelete} method is
         *          invoked to check delete access if the file is opened with the
         *          {@code DELETE_ON_CLOSE} option.
         */
        public static BodySubscriber<Path> asFile(Path file, OpenOption... openOptions) {
            Objects.requireNonNull(file);
            List<OpenOption> opts = List.of(openOptions);
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                String fn = pathForSecurityCheck(file);
                sm.checkWrite(fn);
                if (opts.contains(StandardOpenOption.DELETE_ON_CLOSE))
                    sm.checkDelete(fn);
                if (opts.contains(StandardOpenOption.READ))
                    sm.checkRead(fn);
            }
            return asFileImpl(file, openOptions);
        }

        /**
         * Returns a {@code BodySubscriber} which stores the response body in a
         * file opened with the given name. Has the same effect as calling
         * {@link #asFile(Path, OpenOption...) asFile} with the standard open
         * options {@code CREATE} and {@code WRITE}
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @param file the file to store the body in
         * @return a body subscriber
         * @throws SecurityException if a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file
         */
        public static BodySubscriber<Path> asFile(Path file) {
            return asFile(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }

        /**
         * Returns a {@code BodySubscriber} which provides the incoming body
         * data to the provided Consumer of {@code Optional<byte[]>}. Each
         * call to {@link Consumer#accept(java.lang.Object) Consumer.accept()}
         * will contain a non empty {@code Optional}, except for the final
         * invocation after all body data has been read, when the {@code
         * Optional} will be empty.
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @param consumer a Consumer of byte arrays
         * @return a BodySubscriber
         */
        public static BodySubscriber<Void> asByteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
            return new ResponseSubscribers.ConsumerSubscriber(consumer);
        }

        /**
         * Returns a {@code BodySubscriber} which streams the response body as
         * an {@link InputStream}.
         *
         * <p> The {@link HttpResponse} using this subscriber is available
         * immediately after the response headers have been read, without
         * requiring to wait for the entire body to be processed. The response
         * body can then be read directly from the {@link InputStream}.
         *
         * @apiNote To ensure that all resources associated with the
         * corresponding exchange are properly released the caller must
         * ensure to either read all bytes until EOF is reached, or call
         * {@link InputStream#close} if it is unable or unwilling to do so.
         * Calling {@code close} before exhausting the stream may cause
         * the underlying HTTP connection to be closed and prevent it
         * from being reused for subsequent operations.
         *
         * @return a body subscriber that streams the response body as an
         *         {@link InputStream}.
         */
        public static BodySubscriber<InputStream> asInputStream() {
            return new ResponseSubscribers.HttpResponseInputStream();
        }

        /**
         * Returns a response subscriber which discards the response body. The
         * supplied value is the value that will be returned from
         * {@link HttpResponse#body()}.
         *
         * @param <U> The type of the response body
         * @param value the value to return from HttpResponse.body(), may be {@code null}
         * @return a {@code BodySubscriber}
         */
        public static <U> BodySubscriber<U> discard(U value) {
            return new ResponseSubscribers.NullSubscriber<>(Optional.ofNullable(value));
        }

        /**
         * Returns a {@code BodySubscriber} which buffers data before delivering
         * it to the given downstream subscriber. The subscriber guarantees to
         * deliver {@code buffersize} bytes of data to each invocation of the
         * downstream's {@linkplain #onNext(Object) onNext} method, except for
         * the final invocation, just before {@linkplain #onComplete() onComplete}
         * is invoked. The final invocation of {@code onNext} may contain fewer
         * than {@code buffersize} bytes.
         *
         * <p> The returned subscriber delegates its {@link #getBody()} method
         * to the downstream subscriber.
         *
         * @param downstream the downstream subscriber
         * @param bufferSize the buffer size
         * @return a buffering body subscriber
         * @throws IllegalArgumentException if {@code bufferSize <= 0}
         */
         public static <T> BodySubscriber<T> buffering(BodySubscriber<T> downstream,
                                                       int bufferSize) {
             if (bufferSize <= 0)
                 throw new IllegalArgumentException("must be greater than 0");
             return new BufferingSubscriber<T>(downstream, bufferSize);
         }
    }

    /**
     * A response subscriber for a HTTP/2 multi response.
     * {@Incubating}
     *
     * <p> A multi response comprises a main response, and zero or more additional
     * responses. Each additional response is sent by the server in response to
     * requests (PUSH_PROMISEs) that the server also generates. Additional responses are
     * typically resources that the server expects the client will need which
     * are related to the initial request.
     * <p>
     * Note. Instead of implementing this interface, applications should consider
     * first using the mechanism (built on this interface) provided by
     * {@link MultiSubscriber#asMap(java.util.function.Function, boolean)
     * MultiSubscriber.asMap()} which is a slightly simplified, but also
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
     * <p> {@code MultiSubscriber}s are parameterized with a type {@code U} which
     * represents some meaningful aggregate of the responses received. This
     * would typically be a collection of response or response body objects.
     *
     * @param <U> a type representing the aggregated results
     * @param <T> a type representing all of the response bodies
     *
     * @since 9
     */
    public interface MultiSubscriber<U,T> {
        /**
         * Called for the main request from the user. This {@link HttpRequest}
         * parameter is the request that was supplied to {@link
         * HttpClient#sendAsync(HttpRequest, MultiSubscriber)}. The
         * implementation must return an {@link BodyHandler} for the response
         * body.
         *
         * @param request the request
         *
         * @return an optional body handler
         */
        BodyHandler<T> onRequest(HttpRequest request);

        /**
         * Called for each push promise that is received. The {@link HttpRequest}
         * parameter represents the PUSH_PROMISE. The implementation must return
         * an {@code Optional} of {@link BodyHandler} for the response body.
         * Different handlers (of the same type) can be returned for different
         * pushes within the same multi send. If no handler (an empty {@code
         * Optional}) is returned, then the push will be canceled. If required,
         * the {@code CompletableFuture<Void>} supplied to the {@code
         * onFinalPushPromise} parameter of {@link
         * #completion(CompletableFuture, CompletableFuture)} can be used to
         * determine when the final PUSH_PROMISE is received.
         *
         * @param pushPromise the push promise
         *
         * @return an optional body handler
         */
        Optional<BodyHandler<T>> onPushPromise(HttpRequest pushPromise);

        /**
         * Called for each response received. For each request either one of
         * onResponse() or onError() is guaranteed to be called, but not both.
         *
         * <p> Note: The reason for switching to this callback interface rather
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
         * @param request the main request or subsequent push promise
         * @param t the Throwable that caused the error
         */
        void onError(HttpRequest request, Throwable t);

        /**
         * Returns a {@link java.util.concurrent.CompletableFuture}{@code <U>}
         * which completes when the aggregate result object itself is available.
         * It is expected that the returned {@code CompletableFuture} will depend
         * on one of the given {@code CompletableFuture<Void}s which themselves
         * complete after all individual responses associated with the multi
         * response have completed, or after all push promises have been received.
         * This method is called after {@link #onRequest(HttpRequest)} but
         * before any other methods.
         *
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
         *
         * <p> There are two ways to use these handlers, depending on the value
         * of the <i>completion</I> parameter. If completion is true, then the
         * aggregated result will be available after all responses have
         * themselves completed. If <i>completion</i> is false, then the
         * aggregated result will be available immediately after the last push
         * promise was received. In the former case, this implies that all the
         * CompletableFutures in the map values will have completed. In the
         * latter case, they may or may not have completed yet.
         *
         * <p> The simplest way to use these handlers is to set completion to
         * {@code true}, and then all (results) values in the Map will be
         * accessible without blocking.
         * <p>
         * See {@link #asMap(java.util.function.Function, boolean)}
         * for a code sample of using this interface.
         *
         * <p> See {@link #asMap(Function, boolean)} for a code sample of using
         * this interface.
         *
         * @param <V> the body type used for all responses
         * @param reqHandler a function invoked for the user's request and each
         *                   push promise
         * @param completion {@code true} if the aggregate CompletableFuture
         *                   completes after all responses have been received,
         *                   or {@code false} after all push promises received
         *
         * @return a MultiSubscriber
         */
        public static <V> MultiSubscriber<MultiMapResult<V>,V> asMap(
                Function<HttpRequest, Optional<HttpResponse.BodyHandler<V>>> reqHandler,
                boolean completion) {
            return new MultiSubscriberImpl<V>(reqHandler.andThen(optv -> optv.get()),
                                              reqHandler,
                                              completion);
        }

        /**
         * Returns a general purpose handler for multi responses. This is a
         * convenience method which invokes {@link #asMap(Function,boolean)
         * asMap(Function, true)} meaning that the aggregate result
         * object completes after all responses have been received.
         *
         * <p><b>Example usage:</b>
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
         *              .sendAsync(request, MultiSubscriber.asMap(
         *                  (req) -> Optional.of(HttpResponse.BodyHandler.asString())))
         *              .join();
         * }</pre>
         *
         * <p> The lambda in this example is the simplest possible implementation,
         * where neither the incoming requests are examined, nor the response
         * headers, and every push that the server sends is accepted. When the
         * join() call returns, all {@code HttpResponse}s and their associated
         * body objects are available.
         *
         * @param <V> the body type used for all responses
         * @param reqHandler a function invoked for each push promise and the
         *                   main request
         * @return a MultiSubscriber
         */
        public static <V> MultiSubscriber<MultiMapResult<V>,V> asMap(
                Function<HttpRequest, Optional<HttpResponse.BodyHandler<V>>> reqHandler) {

            return asMap(reqHandler, true);
        }

    }
}
