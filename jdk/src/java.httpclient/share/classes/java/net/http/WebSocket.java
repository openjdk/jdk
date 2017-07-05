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
import java.net.ProtocolException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A WebSocket client conforming to RFC&nbsp;6455.
 *
 * <p> A {@code WebSocket} provides full-duplex communication over a TCP
 * connection.
 *
 * <p> To create a {@code WebSocket} use a {@linkplain #newBuilder(URI, Listener)
 * builder}. Once a {@code WebSocket} is obtained, it's ready to send and
 * receive messages. When the {@code WebSocket} is no longer
 * needed it must be closed: a Close message must both be {@linkplain
 * #sendClose() sent} and {@linkplain Listener#onClose(WebSocket, Optional,
 * String) received}. Otherwise, invoke {@link #abort() abort} to close abruptly.
 *
 * <p> Once closed the {@code WebSocket} remains closed and cannot be reopened.
 *
 * <p> Messages of type {@code X} are sent through the {@code WebSocket.sendX}
 * methods and received through {@link WebSocket.Listener}{@code .onX} methods
 * asynchronously. Each of the methods returns a {@link CompletionStage} which
 * completes when the operation has completed.
 *
 * <p> Messages are received only if {@linkplain #request(long) requested}.
 *
 * <p> One outstanding send operation is permitted: if another send operation is
 * initiated before the previous one has completed, an {@link
 * IllegalStateException IllegalStateException} will be thrown. When sending, a
 * message should not be modified until the returned {@code CompletableFuture}
 * completes (either normally or exceptionally).
 *
 * <p> Messages can be sent and received as a whole or in parts. A whole message
 * is a sequence of one or more messages in which the last message is marked
 * when it is sent or received.
 *
 * <p> If the message is contained in a {@link ByteBuffer}, bytes are considered
 * arranged from the {@code buffer}'s {@link ByteBuffer#position() position} to
 * the {@code buffer}'s {@link ByteBuffer#limit() limit}.
 *
 * <p> Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method of this type will cause a {@link NullPointerException
 * NullPointerException} to be thrown.
 *
 * @implNote The default implementation's methods do not block before returning
 * a {@code CompletableFuture}.
 *
 * @since 9
 */
public interface WebSocket {

    /**
     * Creates a builder of {@code WebSocket}s connected to the given URI and
     * receiving events with the given {@code Listener}.
     *
     * <p> Equivalent to:
     * <pre>{@code
     *     WebSocket.newBuilder(uri, HttpClient.getDefault())
     * }</pre>
     *
     * @param uri
     *         the WebSocket URI as defined in the WebSocket Protocol
     *         (with "ws" or "wss" scheme)
     *
     * @param listener
     *         the listener
     *
     * @throws IllegalArgumentException
     *         if the {@code uri} is not a WebSocket URI
     * @throws SecurityException
     *         if running under a security manager and the caller does
     *         not have permission to access the
     *         {@linkplain HttpClient#getDefault() default HttpClient}
     *
     * @return a builder
     */
    static Builder newBuilder(URI uri, Listener listener) {
        return newBuilder(uri, HttpClient.getDefault(), listener);
    }

    /**
     * Creates a builder of {@code WebSocket}s connected to the given URI and
     * receiving events with the given {@code Listener}.
     *
     * <p> Providing a custom {@code client} allows for finer control over the
     * opening handshake.
     *
     * <p> <b>Example</b>
     * <pre>{@code
     *     HttpClient client = HttpClient.create()
     *             .proxy(ProxySelector.of(new InetSocketAddress("proxy.example.com", 80)))
     *             .build();
     *     ...
     *     WebSocket.newBuilder(URI.create("ws://websocket.example.com"), client, listener)...
     * }</pre>
     *
     * @param uri
     *         the WebSocket URI as defined in the WebSocket Protocol
     *         (with "ws" or "wss" scheme)
     *
     * @param client
     *         the HttpClient
     * @param listener
     *         the listener
     *
     * @throws IllegalArgumentException
     *         if the uri is not a WebSocket URI
     *
     * @return a builder
     */
    static Builder newBuilder(URI uri, HttpClient client, Listener listener) {
        return new WSBuilder(uri, client, listener);
    }

    /**
     * A builder for creating {@code WebSocket} instances.
     *
     * <p> To build a {@code WebSocket}, instantiate a builder, configure it
     * as required by calling intermediate methods (the ones that return the
     * builder itself), then finally call {@link #buildAsync()} to get a {@link
     * CompletableFuture} with resulting {@code WebSocket}.
     *
     * <p> If an intermediate method has not been called, an appropriate
     * default value (or behavior) will be used. Unless otherwise noted, a
     * repeated call to an intermediate method overwrites the previous value (or
     * overrides the previous behaviour), if no exception is thrown.
     *
     * <p> Instances of {@code Builder} may not be safe for use by multiple
     * threads.
     *
     * @since 9
     */
    interface Builder {

        /**
         * Adds the given name-value pair to the list of additional headers for
         * the opening handshake.
         *
         * <p> Headers defined in WebSocket Protocol are not allowed to be added.
         *
         * @param name
         *         the header name
         * @param value
         *         the header value
         *
         * @return this builder
         *
         * @throws IllegalArgumentException
         *         if the {@code name} is a WebSocket defined header name
         */
        Builder header(String name, String value);

        /**
         * Includes a request for the given subprotocols during the opening
         * handshake.
         *
         * <p> Among the requested subprotocols at most one will be chosen by
         * the server. When the {@code WebSocket} is connected, the subprotocol
         * in use is available from {@link WebSocket#getSubprotocol}.
         * Subprotocols may be specified in the order of preference.
         *
         * <p> Each of the given subprotocols must conform to the relevant
         * rules defined in the WebSocket Protocol.
         *
         * @param mostPreferred
         *         the most preferred subprotocol
         * @param lesserPreferred
         *         the lesser preferred subprotocols, with the least preferred
         *         at the end
         *
         * @return this builder
         *
         * @throws IllegalArgumentException
         *         if any of the WebSocket Protocol rules relevant to
         *         subprotocols are violated
         */
        Builder subprotocols(String mostPreferred, String... lesserPreferred);

        /**
         * Sets a timeout for the opening handshake.
         *
         * <p> If the opening handshake is not finished within the specified
         * amount of time then {@link #buildAsync()} completes exceptionally
         * with a {@code HttpTimeoutException}.
         *
         * <p> If this method is not invoked then the timeout is deemed infinite.
         *
         * @param timeout
         *         the timeout
         *
         * @return this builder
         */
        Builder connectTimeout(Duration timeout);

        /**
         * Builds a {@code WebSocket}.
         *
         * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
         * normally with the {@code WebSocket} when it is connected or completes
         * exceptionally if an error occurs.
         *
         * <p> {@code CompletableFuture} may complete exceptionally with the
         * following errors:
         * <ul>
         * <li> {@link IOException}
         *          if an I/O error occurs
         * <li> {@link InterruptedException}
         *          if the operation was interrupted
         * <li> {@link SecurityException}
         *          if a security manager is set, and the caller does not
         *          have a {@link java.net.URLPermission} for the WebSocket URI
         * <li> {@link WebSocketHandshakeException}
         *          if the opening handshake fails
         * </ul>
         *
         * @return a {@code CompletableFuture} with the {@code WebSocket}
         */
        CompletableFuture<WebSocket> buildAsync();
    }

    /**
     * A listener for events and messages on a {@code WebSocket}.
     *
     * <p> Each method below corresponds to a type of event.
     * <ul>
     * <li> {@link #onOpen onOpen} <br>
     * This method is always the first to be invoked.
     * <li> {@link #onText(WebSocket, CharSequence, WebSocket.MessagePart)
     * onText}, {@link #onBinary(WebSocket, ByteBuffer, WebSocket.MessagePart)
     * onBinary}, {@link #onPing(WebSocket, ByteBuffer) onPing} and {@link
     * #onPong(WebSocket, ByteBuffer) onPong} <br>
     * These methods are invoked zero or more times after {@code onOpen}.
     * <li> {@link #onClose(WebSocket, Optional, String) onClose}, {@link
     * #onError(WebSocket, Throwable) onError} <br>
     * Only one of these methods is invoked, and that method is invoked last and
     * at most once.
     * </ul>
     *
     * <pre><code>
     *     onOpen (onText|onBinary|onPing|onPong)* (onClose|onError)?
     * </code></pre>
     *
     * <p> Messages received by the {@code Listener} conform to the WebSocket
     * Protocol, otherwise {@code onError} with a {@link ProtocolException} is
     * invoked.
     *
     * <p> If a whole message is received, then the corresponding method
     * ({@code onText} or {@code onBinary}) will be invoked with {@link
     * WebSocket.MessagePart#WHOLE WHOLE} marker. Otherwise the method will be
     * invoked with {@link WebSocket.MessagePart#FIRST FIRST}, zero or more
     * times with {@link WebSocket.MessagePart#FIRST PART} and, finally, with
     * {@link WebSocket.MessagePart#LAST LAST} markers.
     *
     * <pre><code>
     *     WHOLE|(FIRST PART* LAST)
     * </code></pre>
     *
     * <p> All methods are invoked in a sequential (and
     * <a href="../../../java/util/concurrent/package-summary.html#MemoryVisibility">
     * happens-before</a>) order, one after another, possibly by different
     * threads. If any of the methods above throws an exception, {@code onError}
     * is then invoked with that exception. Exceptions thrown from {@code
     * onError} or {@code onClose} are ignored.
     *
     * <p> When the method returns, the message is deemed received. After this
     * another messages may be received.
     *
     * <p> These invocations begin asynchronous processing which might not end
     * with the invocation. To provide coordination, methods of {@code
     * Listener} return a {@link CompletionStage CompletionStage}. The {@code
     * CompletionStage} signals the {@code WebSocket} that the
     * processing of a message has ended. For
     * convenience, methods may return {@code null}, which means
     * the same as returning an already completed {@code CompletionStage}. If
     * the returned {@code CompletionStage} completes exceptionally, then {@link
     * #onError(WebSocket, Throwable) onError} will be invoked with the
     * exception.
     *
     * <p> Control of the message passes to the {@code Listener} with the
     * invocation of the method. Control of the message returns to the {@code
     * WebSocket} at the earliest of, either returning {@code null} from the
     * method, or the completion of the {@code CompletionStage} returned from
     * the method. The {@code WebSocket} does not access the message while it's
     * not in its control. The {@code Listener} must not access the message
     * after its control has been returned to the {@code WebSocket}.
     *
     * <p> It is the responsibility of the listener to make additional
     * {@linkplain WebSocket#request(long) message requests}, when ready, so
     * that messages are received eventually.
     *
     * <p> Methods above are never invoked with {@code null}s as their
     * arguments.
     *
     * @since 9
     */
    interface Listener {

        /**
         * Notifies the {@code Listener} that it is connected to the provided
         * {@code WebSocket}.
         *
         * <p> The {@code onOpen} method does not correspond to any message
         * from the WebSocket Protocol. It is a synthetic event. It is the first
         * {@code Listener}'s method to be invoked. No other {@code Listener}'s
         * methods are invoked before this one. The method is usually used to
         * make an initial {@linkplain WebSocket#request(long) request} for
         * messages.
         *
         * <p> If an exception is thrown from this method then {@link
         * #onError(WebSocket, Throwable) onError} will be invoked with the
         * exception.
         *
         * @implSpec The default implementation {@linkplain WebSocket#request(long)
         * requests one message}.
         *
         * @param webSocket
         *         the WebSocket
         */
        default void onOpen(WebSocket webSocket) { webSocket.request(1); }

        /**
         * Receives a Text message.
         *
         * <p> The {@code onText} method is invoked zero or more times between
         * {@code onOpen} and ({@code onClose} or {@code onError}).
         *
         * <p> This message may be a partial UTF-16 sequence. However, the
         * concatenation of all messages through the last will be a whole UTF-16
         * sequence.
         *
         * <p> If an exception is thrown from this method or the returned {@code
         * CompletionStage} completes exceptionally, then {@link
         * #onError(WebSocket, Throwable) onError} will be invoked with the
         * exception.
         *
         * @implSpec The default implementation {@linkplain WebSocket#request(long)
         * requests one more message}.
         *
         * @implNote This implementation passes only complete UTF-16 sequences
         * to the {@code onText} method.
         *
         * @param webSocket
         *         the WebSocket
         * @param message
         *         the message
         * @param part
         *         the part
         *
         * @return a CompletionStage that completes when the message processing
         * is done; or {@code null} if already done
         */
        default CompletionStage<?> onText(WebSocket webSocket,
                                          CharSequence message,
                                          MessagePart part) {
            webSocket.request(1);
            return null;
        }

        /**
         * Receives a Binary message.
         *
         * <p> The {@code onBinary} method is invoked zero or more times
         * between {@code onOpen} and ({@code onClose} or {@code onError}).
         *
         * <p> If an exception is thrown from this method or the returned {@code
         * CompletionStage} completes exceptionally, then {@link
         * #onError(WebSocket, Throwable) onError} will be invoked with this
         * exception.
         *
         * @implSpec The default implementation {@linkplain WebSocket#request(long)
         * requests one more message}.
         *
         * @param webSocket
         *         the WebSocket
         * @param message
         *         the message
         * @param part
         *         the part
         *
         * @return a CompletionStage that completes when the message processing
         * is done; or {@code null} if already done
         */
        default CompletionStage<?> onBinary(WebSocket webSocket,
                                            ByteBuffer message,
                                            MessagePart part) {
            webSocket.request(1);
            return null;
        }

        /**
         * Receives a Ping message.
         *
         * <p> A Ping message may be sent or received by either client or
         * server. It may serve either as a keepalive or as a means to verify
         * that the remote endpoint is still responsive.
         *
         * <p> The message will consist of not more than {@code 125} bytes:
         * {@code message.remaining() <= 125}.
         *
         * <p> The {@code onPing} is invoked zero or more times in between
         * {@code onOpen} and ({@code onClose} or {@code onError}).
         *
         * <p> If an exception is thrown from this method or the returned {@code
         * CompletionStage} completes exceptionally, then {@link
         * #onError(WebSocket, Throwable) onError} will be invoked with this
         * exception.
         *
         * @implNote
         *
         * <p> Replies with a Pong message and requests one more message when
         * the Pong has been sent.
         *
         * @param webSocket
         *         the WebSocket
         * @param message
         *         the message
         *
         * @return a CompletionStage that completes when the message processing
         * is done; or {@code null} if already done
         */
        default CompletionStage<?> onPing(WebSocket webSocket,
                                          ByteBuffer message) {
            return webSocket.sendPong(message).thenRun(() -> webSocket.request(1));
        }

        /**
         * Receives a Pong message.
         *
         * <p> A Pong message may be unsolicited or may be received in response
         * to a previously sent Ping. In the latter case, the contents of the
         * Pong is identical to the originating Ping.
         *
         * <p> The message will consist of not more than {@code 125} bytes:
         * {@code message.remaining() <= 125}.
         *
         * <p> The {@code onPong} method is invoked zero or more times in
         * between {@code onOpen} and ({@code onClose} or {@code onError}).
         *
         * <p> If an exception is thrown from this method or the returned {@code
         * CompletionStage} completes exceptionally, then {@link
         * #onError(WebSocket, Throwable) onError} will be invoked with this
         * exception.
         *
         * @implSpec The default implementation {@linkplain WebSocket#request(long)
         * requests one more message}.
         *
         * @param webSocket
         *         the WebSocket
         * @param message
         *         the message
         *
         * @return a CompletionStage that completes when the message processing
         * is done; or {@code null} if already done
         */
        default CompletionStage<?> onPong(WebSocket webSocket,
                                          ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        /**
         * Receives a Close message.
         *
         * <p> Once a Close message is received, the server will not send any
         * more messages.
         *
         * <p> A Close message may consist of a status code and a reason for
         * closing. The reason will have a UTF-8 representation not longer than
         * {@code 123} bytes. The reason may be useful for debugging or passing
         * information relevant to the connection but is not necessarily human
         * readable.
         *
         * <p> {@code onClose} is the last invocation on the {@code Listener}.
         * It is invoked at most once, but after {@code onOpen}. If an exception
         * is thrown from this method, it is ignored.
         *
         * @implSpec The default implementation does nothing.
         *
         * @param webSocket
         *         the WebSocket
         * @param code
         *         an {@code Optional} describing the close code, or
         *         an empty {@code Optional} if the message doesn't contain it
         * @param reason
         *         the reason of close; can be empty
         */
        default void onClose(WebSocket webSocket, Optional<CloseCode> code,
                             String reason) { }

        /**
         * Notifies an I/O or protocol error has occurred on the {@code
         * WebSocket}.
         *
         * <p> The {@code onError} method does not correspond to any message
         * from the WebSocket Protocol. It is a synthetic event. {@code onError}
         * is the last invocation on the {@code Listener}. It is invoked at most
         * once but after {@code onOpen}. If an exception is thrown from this
         * method, it is ignored.
         *
         * <p> The WebSocket Protocol requires some errors occurs in the
         * incoming destination must be fatal to the connection. In such cases
         * the implementation takes care of closing the {@code WebSocket}. By
         * the time {@code onError} is invoked, no more messages can be sent on
         * this {@code WebSocket}.
         *
         * @apiNote Errors associated with {@code sendX} methods are reported to
         * the {@code CompletableFuture} these methods return.
         *
         * @implSpec The default implementation does nothing.
         *
         * @param webSocket
         *         the WebSocket
         * @param error
         *         the error
         */
        default void onError(WebSocket webSocket, Throwable error) { }
    }

    /**
     * A marker used by {@link WebSocket.Listener} in cases where a partial
     * message may be received.
     *
     * @since 9
     */
    enum MessagePart {

        /**
         * The first part of a message in a sequence.
         */
        FIRST,

        /**
         * A middle part of a message in a sequence.
         */
        PART,

        /**
         * The last part of a message in a sequence.
         */
        LAST,

        /**
         * A whole message consisting of a single part.
         */
        WHOLE
    }

    /**
     * Sends a Text message with characters from the given {@code CharSequence}.
     *
     * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
     * normally when the message has been sent or completes exceptionally if an
     * error occurs.
     *
     * <p> The {@code CharSequence} should not be modified until the returned
     * {@code CompletableFuture} completes (either normally or exceptionally).
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IOException}
     *          if an I/O error occurs during this operation
     * <li> {@link IllegalStateException}
     *          if the {@code WebSocket} closes while this operation is in progress;
     *          or if a Close message has been sent already;
     *          or if there is an outstanding send operation;
     *          or if a previous Binary message was not sent with {@code isLast == true}
     * </ul>
     *
     * @implNote This implementation does not accept partial UTF-16
     * sequences. In case such a sequence is passed, a returned {@code
     * CompletableFuture} completes exceptionally.
     *
     * @param message
     *         the message
     * @param isLast
     *         {@code true} if this is the last part of the message,
     *         {@code false} otherwise
     *
     * @return a CompletableFuture with this WebSocket
     *
     * @throws IllegalArgumentException
     *         if {@code message} is a malformed (or an incomplete) UTF-16 sequence
     */
    CompletableFuture<WebSocket> sendText(CharSequence message, boolean isLast);

    /**
     * Sends a whole Text message with characters from the given {@code
     * CharSequence}.
     *
     * <p> This is a convenience method. For the general case, use {@link
     * #sendText(CharSequence, boolean)}.
     *
     * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
     * normally when the message has been sent or completes exceptionally if an
     * error occurs.
     *
     * <p> The {@code CharSequence} should not be modified until the returned
     * {@code CompletableFuture} completes (either normally or exceptionally).
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IOException}
     *          if an I/O error occurs during this operation
     * <li> {@link IllegalStateException}
     *          if the {@code WebSocket} closes while this operation is in progress;
     *          or if a Close message has been sent already;
     *          or if there is an outstanding send operation;
     *          or if a previous Binary message was not sent with {@code isLast == true}
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a CompletableFuture with this WebSocket
     *
     * @throws IllegalArgumentException
     *         if {@code message} is a malformed (or an incomplete) UTF-16 sequence
     */
    default CompletableFuture<WebSocket> sendText(CharSequence message) {
        return sendText(message, true);
    }

    /**
     * Sends a Binary message with bytes from the given {@code ByteBuffer}.
     *
     * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
     * normally when the message has been sent or completes exceptionally if an
     * error occurs.
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IOException}
     *          if an I/O error occurs during this operation
     * <li> {@link IllegalStateException}
     *          if the {@code WebSocket} closes while this operation is in progress;
     *          or if a Close message has been sent already;
     *          or if there is an outstanding send operation;
     *          or if a previous Text message was not sent with {@code isLast == true}
     * </ul>
     *
     * @param message
     *         the message
     * @param isLast
     *         {@code true} if this is the last part of the message,
     *         {@code false} otherwise
     *
     * @return a CompletableFuture with this WebSocket
     */
    CompletableFuture<WebSocket> sendBinary(ByteBuffer message, boolean isLast);

    /**
     * Sends a Ping message.
     *
     * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
     * normally when the message has been sent or completes exceptionally if an
     * error occurs.
     *
     * <p> A Ping message may be sent or received by either client or server.
     * It may serve either as a keepalive or as a means to verify that the
     * remote endpoint is still responsive.
     *
     * <p> The message must consist of not more than {@code 125} bytes: {@code
     * message.remaining() <= 125}.
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IOException}
     *          if an I/O error occurs during this operation
     * <li> {@link IllegalStateException}
     *          if the {@code WebSocket} closes while this operation is in progress;
     *          or if a Close message has been sent already;
     *          or if there is an outstanding send operation
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a CompletableFuture with this WebSocket
     *
     * @throws IllegalArgumentException
     *         if {@code message.remaining() > 125}
     */
    CompletableFuture<WebSocket> sendPing(ByteBuffer message);

    /**
     * Sends a Pong message.
     *
     * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
     * normally when the message has been sent or completes exceptionally if an
     * error occurs.
     *
     * <p> A Pong message may be unsolicited or may be sent in response to a
     * previously received Ping. In latter case the contents of the Pong is
     * identical to the originating Ping.
     *
     * <p> The message must consist of not more than {@code 125} bytes: {@code
     * message.remaining() <= 125}.
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IOException}
     *          if an I/O error occurs during this operation
     * <li> {@link IllegalStateException}
     *          if the {@code WebSocket} closes while this operation is in progress;
     *          or if a Close message has been sent already;
     *          or if there is an outstanding send operation
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a CompletableFuture with this WebSocket
     *
     * @throws IllegalArgumentException
     *         if {@code message.remaining() > 125}
     */
    CompletableFuture<WebSocket> sendPong(ByteBuffer message);

    /**
     * Sends a Close message with the given close code and the reason.
     *
     * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
     * normally when the message has been sent or completes exceptionally if an
     * error occurs.
     *
     * <p> A Close message may consist of a status code and a reason for
     * closing. The reason must have a UTF-8 representation not longer than
     * {@code 123} bytes. The reason may be useful for debugging or passing
     * information relevant to the connection but is not necessarily human
     * readable.
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IOException}
     *          if an I/O error occurs during this operation
     * <li> {@link IllegalStateException}
     *          if the {@code WebSocket} closes while this operation is in progress;
     *          or if a Close message has been sent already;
     *          or if there is an outstanding send operation
     * </ul>
     *
     * @param code
     *         the close code
     * @param reason
     *         the reason; can be empty
     *
     * @return a CompletableFuture with this WebSocket
     *
     * @throws IllegalArgumentException
     *         if {@code reason} doesn't have an UTF-8 representation not longer
     *         than {@code 123} bytes
     */
    CompletableFuture<WebSocket> sendClose(CloseCode code, CharSequence reason);

    /**
     * Sends an empty Close message.
     *
     * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
     * normally when the message has been sent or completes exceptionally if an
     * error occurs.
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IOException}
     *          if an I/O error occurs during this operation
     * <li> {@link IllegalStateException}
     *          if the {@code WebSocket} closes while this operation is in progress;
     *          or if a Close message has been sent already;
     *          or if there is an outstanding send operation
     * </ul>
     *
     * @return a CompletableFuture with this WebSocket
     */
    CompletableFuture<WebSocket> sendClose();

    /**
     * Allows {@code n} more messages to be received by the {@link Listener
     * Listener}.
     *
     * <p> The actual number of received messages might be fewer if a Close
     * message is received, the connection closes or an error occurs.
     *
     * <p> A {@code WebSocket} that has just been created, hasn't requested
     * anything yet. Usually the initial request for messages is done in {@link
     * Listener#onOpen(java.net.http.WebSocket) Listener.onOpen}.
     *
     * @implNote This implementation does not distinguish between partial and
     * whole messages, because it's not known beforehand how a message will be
     * received.
     *
     * <p> If a server sends more messages than requested, this implementation
     * queues up these messages on the TCP connection and may eventually force
     * the sender to stop sending through TCP flow control.
     *
     * @param n
     *         the number of messages
     *
     * @throws IllegalArgumentException
     *         if {@code n < 0}
     */
    void request(long n);

    /**
     * Returns a {@linkplain Builder#subprotocols(String, String...) subprotocol}
     * in use.
     *
     * @return a subprotocol, or {@code null} if there is none
     */
    String getSubprotocol();

    /**
     * Tells whether the {@code WebSocket} is closed.
     *
     * <p> A {@code WebSocket} deemed closed when either the underlying socket
     * is closed or the closing handshake is completed.
     *
     * @return {@code true} if the {@code WebSocket} is closed,
     *         {@code false} otherwise
     */
    boolean isClosed();

    /**
     * Closes the {@code WebSocket} abruptly.
     *
     * <p> This method closes the underlying TCP connection. If the {@code
     * WebSocket} is already closed then invoking this method has no effect.
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    void abort() throws IOException;

    /**
     * A {@code WebSocket} close status code.
     *
     * <p> Some codes <a href="https://tools.ietf.org/html/rfc6455#section-7.4">
     * specified</a> in the WebSocket Protocol are defined as named constants
     * here. Others can be {@linkplain #of(int) retrieved on demand}.
     *
     * <p> This is a
     * <a href="../../lang/doc-files/ValueBased.html">value-based</a> class;
     * use of identity-sensitive operations (including reference equality
     * ({@code ==}), identity hash code, or synchronization) on instances of
     * {@code CloseCode} may have unpredictable results and should be avoided.
     *
     * @since 9
     */
    final class CloseCode {

        /**
         * Indicates a normal close, meaning that the purpose for which the
         * connection was established has been fulfilled.
         *
         * <p> Numerical representation: {@code 1000}
         */
        public static final CloseCode NORMAL_CLOSURE
                = new CloseCode(1000, "NORMAL_CLOSURE");

        /**
         * Indicates that an endpoint is "going away", such as a server going
         * down or a browser having navigated away from a page.
         *
         * <p> Numerical representation: {@code 1001}
         */
        public static final CloseCode GOING_AWAY
                = new CloseCode(1001, "GOING_AWAY");

        /**
         * Indicates that an endpoint is terminating the connection due to a
         * protocol error.
         *
         * <p> Numerical representation: {@code 1002}
         */
        public static final CloseCode PROTOCOL_ERROR
                = new CloseCode(1002, "PROTOCOL_ERROR");

        /**
         * Indicates that an endpoint is terminating the connection because it
         * has received a type of data it cannot accept (e.g., an endpoint that
         * understands only text data MAY send this if it receives a binary
         * message).
         *
         * <p> Numerical representation: {@code 1003}
         */
        public static final CloseCode CANNOT_ACCEPT
                = new CloseCode(1003, "CANNOT_ACCEPT");

        /**
         * Indicates that an endpoint is terminating the connection because it
         * has received data within a message that was not consistent with the
         * type of the message (e.g., non-UTF-8 [RFC3629] data within a text
         * message).
         *
         * <p> Numerical representation: {@code 1007}
         */
        public static final CloseCode NOT_CONSISTENT
                = new CloseCode(1007, "NOT_CONSISTENT");

        /**
         * Indicates that an endpoint is terminating the connection because it
         * has received a message that violates its policy. This is a generic
         * status code that can be returned when there is no other more suitable
         * status code (e.g., {@link #CANNOT_ACCEPT} or {@link #TOO_BIG}) or if
         * there is a need to hide specific details about the policy.
         *
         * <p> Numerical representation: {@code 1008}
         */
        public static final CloseCode VIOLATED_POLICY
                = new CloseCode(1008, "VIOLATED_POLICY");

        /**
         * Indicates that an endpoint is terminating the connection because it
         * has received a message that is too big for it to process.
         *
         * <p> Numerical representation: {@code 1009}
         */
        public static final CloseCode TOO_BIG
                = new CloseCode(1009, "TOO_BIG");

        /**
         * Indicates that an endpoint is terminating the connection because it
         * encountered an unexpected condition that prevented it from fulfilling
         * the request.
         *
         * <p> Numerical representation: {@code 1011}
         */
        public static final CloseCode UNEXPECTED_CONDITION
                = new CloseCode(1011, "UNEXPECTED_CONDITION");

        private static final Map<Integer, CloseCode> cached = Map.ofEntries(
                entry(NORMAL_CLOSURE),
                entry(GOING_AWAY),
                entry(PROTOCOL_ERROR),
                entry(CANNOT_ACCEPT),
                entry(NOT_CONSISTENT),
                entry(VIOLATED_POLICY),
                entry(TOO_BIG),
                entry(UNEXPECTED_CONDITION)
        );

        /**
         * Returns a {@code CloseCode} from its numerical representation.
         *
         * <p> The given {@code code} should be in the range {@code 1000 <= code
         * <= 4999}, and should not be equal to any of the following codes:
         * {@code 1004}, {@code 1005}, {@code 1006} and {@code 1015}.
         *
         * @param code
         *         numerical representation
         *
         * @return a close code corresponding to the provided numerical value
         *
         * @throws IllegalArgumentException
         *         if {@code code} violates any of the requirements above
         */
        public static CloseCode of(int code) {
            if (code < 1000 || code > 4999) {
                throw new IllegalArgumentException("Out of range: " + code);
            }
            if (code == 1004 || code == 1005 || code == 1006 || code == 1015) {
                throw new IllegalArgumentException("Reserved: " + code);
            }
            CloseCode closeCode = cached.get(code);
            return closeCode != null ? closeCode : new CloseCode(code, "");
        }

        private final int code;
        private final String description;

        private CloseCode(int code, String description) {
            assert description != null;
            this.code = code;
            this.description = description;
        }

        /**
         * Returns a numerical representation of this close code.
         *
         * @return a numerical representation
         */
        public int getCode() {
            return code;
        }

        /**
         * Compares this close code to the specified object.
         *
         * @param o
         *         the object to compare this {@code CloseCode} against
         *
         * @return {@code true} iff the argument is a close code with the same
         * {@linkplain #getCode() numerical representation} as this one
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CloseCode)) {
                return false;
            }
            CloseCode that = (CloseCode) o;
            return code == that.code;
        }

        @Override
        public int hashCode() {
            return code;
        }

        /**
         * Returns a human-readable representation of this close code.
         *
         * @apiNote The representation is not designed to be parsed; the format
         * may change unexpectedly.
         *
         * @return a string representation
         */
        @Override
        public String toString() {
            return code + (description.isEmpty() ? "" : (": " + description));
        }

        private static Map.Entry<Integer, CloseCode> entry(CloseCode cc) {
            return Map.entry(cc.getCode(), cc);
        }
    }
}
