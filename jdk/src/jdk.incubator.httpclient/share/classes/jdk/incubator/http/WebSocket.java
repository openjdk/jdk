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
import java.net.ProtocolException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A WebSocket client conforming to RFC&nbsp;6455.
 * {@Incubating}
 *
 * <p> A {@code WebSocket} provides full-duplex communication over a TCP
 * connection.
 *
 * <p> To create a {@code WebSocket} use a {@linkplain HttpClient#newWebSocketBuilder(
 * URI, Listener) builder}. Once a {@code WebSocket} is built, it's ready
 * to send and receive messages. When the {@code WebSocket} is no longer needed
 * it must be closed: a Close message must both be {@linkplain #sendClose
 * sent} and {@linkplain Listener#onClose(WebSocket, int, String) received}.
 * The {@code WebSocket} may be also closed {@linkplain #abort() abruptly}.
 *
 * <p> Once closed the {@code WebSocket} remains {@linkplain #isClosed() closed}
 * and cannot be reopened.
 *
 * <p> Messages of type {@code X} (where {@code X} is one of: Text, Binary,
 * Ping, Pong or Close) are sent and received asynchronously through the {@code
 * WebSocket.send{X}} and {@link WebSocket.Listener}{@code .on{X}} methods
 * respectively. Each method returns a {@link CompletionStage} which completes
 * when the operation has completed.
 *
 * <p> Note that messages (of any type) are received only if {@linkplain
 * #request(long) requested}.
 *
 * <p> One outstanding send operation is permitted. No further send operation
 * can be initiated before the previous one has completed. When sending, a
 * message must not be modified until the returned {@link CompletableFuture}
 * completes (either normally or exceptionally).
 *
 * <p> Text and Binary messages can be sent and received as a whole or in parts.
 * A whole message is transferred as a sequence of one or more invocations of a
 * corresponding method where the last invocation is identified via an
 * additional method argument.
 *
 * <p> If the message is contained in a {@link ByteBuffer}, bytes are considered
 * arranged from the {@code buffer}'s {@link ByteBuffer#position() position} to
 * the {@code buffer}'s {@link ByteBuffer#limit() limit}.
 *
 * <p> Unless otherwise stated, {@code null} parameter values will cause methods
 * and constructors to throw {@link NullPointerException}.
 *
 * @implNote This implementation's methods do not block before returning
 * a {@code CompletableFuture}.
 *
 * @since 9
 */
public interface WebSocket {

    /**
     * The WebSocket Close message status code (<code>{@value}</code>),
     * indicating normal closure, meaning that the purpose for which the
     * connection was established has been fulfilled.
     *
     * @see #sendClose(int, String)
     * @see Listener#onClose(WebSocket, int, String)
     */
    int NORMAL_CLOSURE = 1000;

    /**
     * A builder for creating {@code WebSocket} instances.
     * {@Incubating}
     *
     * <p> To build a {@code WebSocket}, {@linkplain HttpClient#newWebSocketBuilder(
     * URI, Listener) create} a builder, configure it as required by
     * calling intermediate methods (the ones that return the builder itself),
     * then finally call {@link #buildAsync()} to get a {@link
     * CompletableFuture} with resulting {@code WebSocket}.
     *
     * <p> If an intermediate method has not been called, an appropriate
     * default value (or behavior) will be used. Unless otherwise noted, a
     * repeated call to an intermediate method overwrites the previous value (or
     * overrides the previous behaviour).
     *
     * <p> Instances of {@code Builder} are not safe for use by multiple threads
     * without external synchronization.
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
         */
        Builder header(String name, String value);

        /**
         * Includes a request for the given subprotocols during the opening
         * handshake.
         *
         * <p> Among the requested subprotocols at most one will be chosen by
         * the server. This subprotocol will be available from {@link
         * WebSocket#getSubprotocol}. Subprotocols are specified in the order of
         * preference.
         *
         * <p> Each of the given subprotocols must conform to the relevant
         * rules defined in the WebSocket Protocol.
         *
         * <p> If this method is not invoked then no subprotocols are requested.
         *
         * @param mostPreferred
         *         the most preferred subprotocol
         * @param lesserPreferred
         *         the lesser preferred subprotocols, with the least preferred
         *         at the end
         *
         * @return this builder
         */
        Builder subprotocols(String mostPreferred, String... lesserPreferred);

        /**
         * Sets a timeout for the opening handshake.
         *
         * <p> If the opening handshake does not complete within the specified
         * duration then the {@code CompletableFuture} returned from {@link
         * #buildAsync()} completes exceptionally with a {@link
         * HttpTimeoutException}.
         *
         * <p> If this method is not invoked then the timeout is deemed infinite.
         *
         * @param timeout
         *         the timeout, non-{@linkplain Duration#isNegative() negative},
         *         non-{@linkplain Duration#ZERO ZERO}
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
         * <li> {@link IOException} -
         *          if an I/O error occurs
         * <li> {@link WebSocketHandshakeException} -
         *          if the opening handshake fails
         * <li> {@link HttpTimeoutException} -
         *          if the opening handshake does not complete within
         *          the specified {@linkplain #connectTimeout(Duration) duration}
         * <li> {@link InterruptedException} -
         *          if the operation was interrupted
         * <li> {@link SecurityException} -
         *          if a security manager is set, and the caller does not
         *          have a {@link java.net.URLPermission} for the WebSocket URI
         * <li> {@link IllegalArgumentException} -
         *          if any of the additional {@link #header(String, String)
         *          headers} are illegal;
         *          or if any of the WebSocket Protocol rules relevant to {@link
         *          #subprotocols(String, String...) subprotocols} are violated;
         *          or if the {@link #connectTimeout(Duration) connect timeout}
         *          is invalid;
         * </ul>
         *
         * @return a {@code CompletableFuture} with the {@code WebSocket}
         */
        CompletableFuture<WebSocket> buildAsync();
    }

    /**
     * A listener for events and messages on a {@code WebSocket}.
     * {@Incubating}
     *
     * <p> Each method of {@code Listener} corresponds to a type of event or a
     * type of message. The {@code WebSocket} argument of the method is the
     * {@code WebSocket} the event has occurred (the message has been received)
     * on. All methods with the same {@code WebSocket} argument are invoked in a
     * sequential
     * (and <a href="../../../java/util/concurrent/package-summary.html#MemoryVisibility">happens-before</a>)
     * order, one after another, possibly by different threads.
     *
     * <ul>
     * <li> {@link #onOpen(WebSocket) onOpen} <br>
     * This method is invoked first.
     * <li> {@link #onText(WebSocket, CharSequence, WebSocket.MessagePart)
     * onText}, {@link #onBinary(WebSocket, ByteBuffer, WebSocket.MessagePart)
     * onBinary}, {@link #onPing(WebSocket, ByteBuffer) onPing} and {@link
     * #onPong(WebSocket, ByteBuffer) onPong} <br>
     * These methods are invoked zero or more times after {@code onOpen}.
     * <li> {@link #onClose(WebSocket, int, String) onClose}, {@link
     * #onError(WebSocket, Throwable) onError} <br>
     * Only one of these methods is invoked, and that method is invoked last.
     * </ul>
     *
     * <p> Messages received by the {@code Listener} conform to the WebSocket
     * Protocol, otherwise {@code onError} with a {@link ProtocolException} is
     * invoked.
     *
     * <p> If a whole message is received, then the corresponding method
     * ({@code onText} or {@code onBinary}) will be invoked with {@link
     * WebSocket.MessagePart#WHOLE WHOLE} marker. Otherwise the method will be
     * invoked with {@link WebSocket.MessagePart#FIRST FIRST}, zero or more
     * times with {@link WebSocket.MessagePart#PART PART} and, finally, with
     * {@link WebSocket.MessagePart#LAST LAST} markers.
     *
     * If any of the methods above throws an exception, {@code onError} is then
     * invoked with the same {@code WebSocket} and this exception. Exceptions
     * thrown from {@code onError} or {@code onClose} are ignored.
     *
     * <p> When the method returns, the message is deemed received (in
     * particular, if contained in a {@code ByteBuffer buffer}, the data is
     * deemed received completely regardless of the result {@code
     * buffer.hasRemaining()} upon the method's return. After this further
     * messages may be received.
     *
     * <p> These invocations begin asynchronous processing which might not end
     * with the invocation. To provide coordination, methods of {@code Listener}
     * return a {@link CompletionStage CompletionStage}.
     * The {@code CompletionStage} signals the {@code WebSocket} that the
     * processing of a message has ended. For convenience, methods may return
     * {@code null}, which (by convention) means the same as returning an
     * already completed (normally) {@code CompletionStage}.
     * If the returned {@code CompletionStage} completes exceptionally, then
     * {@link #onError(WebSocket, Throwable) onError} will be invoked with the
     * same {@code WebSocket} and this exception.
     *
     * <p> Control of the message passes to the {@code Listener} with the
     * invocation of the method. Control of the message returns to the {@code
     * WebSocket} at the earliest of, either returning {@code null} from the
     * method, or the completion of the {@code CompletionStage} returned from
     * the method. The {@code WebSocket} does not access the message while it's
     * not in its control. The {@code Listener} must not access the message
     * after its control has been returned to the {@code WebSocket}.
     *
     * <p> A {@code WebSocket} implementation never invokes {@code Listener}'s
     * methods with {@code null}s as their arguments.
     *
     * @since 9
     */
    interface Listener {

        /**
         * Notifies the {@code Listener} that it is connected to the provided
         * {@code WebSocket}.
         *
         * <p> The {@code onOpen} method does not correspond to any message from
         * the WebSocket Protocol. It is a synthetic event and the first {@code
         * Listener}'s method to be invoked.
         *
         * <p> This method is usually used to make an initial {@linkplain
         * WebSocket#request(long) request} for messages.
         *
         * <p> If an exception is thrown from this method then {@link
         * #onError(WebSocket, Throwable) onError} will be invoked with the same
         * {@code WebSocket} and this exception.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         * }</pre>
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
         * #onError(WebSocket, Throwable) onError} will be invoked with the same
         * {@code WebSocket} and this exception.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
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
         * @return a {@code CompletionStage} which completes when the message
         * processing is done; or {@code null} if already done
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
         * #onError(WebSocket, Throwable) onError} will be invoked with the same
         * {@code WebSocket} and this exception.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket
         * @param message
         *         the message
         * @param part
         *         the part
         *
         * @return a {@code CompletionStage} which completes when the message
         * processing is done; or {@code null} if already done
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
         * <p> The {@code WebSocket} handles Ping messages by replying with
         * appropriate Pong messages using a strategy of its choice, but within
         * the boundaries of the WebSocket Protocol. The {@code WebSocket} may
         * invoke {@code onPing} after handling a Ping message, before doing so
         * or in parallel with it. In other words no particular ordering is
         * guaranteed. If an error occurs while implementation handles this Ping
         * message, then {@code onError} will be invoked with this error. For
         * more details on handling Ping messages see RFC 6455 sections
         * <a href="https://tools.ietf.org/html/rfc6455#section-5.5.2">5.5.2. Ping</a>
         * and
         * <a href="https://tools.ietf.org/html/rfc6455#section-5.5.3">5.5.3. Pong</a>.
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
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket
         * @param message
         *         the message
         *
         * @return a {@code CompletionStage} which completes when the message
         * processing is done; or {@code null} if already done
         */
        default CompletionStage<?> onPing(WebSocket webSocket,
                                          ByteBuffer message) {
            webSocket.request(1);
            return null;
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
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket
         * @param message
         *         the message
         *
         * @return a {@code CompletionStage} which completes when the message
         * processing is done; or {@code null} if already done
         */
        default CompletionStage<?> onPong(WebSocket webSocket,
                                          ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        /**
         * Receives a Close message.
         *
         * <p> A Close message consists of a status code and a reason for
         * closing. The status code is an integer in the range {@code 1000 <=
         * code <= 65535}. The {@code reason} is a short string that has an
         * UTF-8 representation not longer than {@code 123} bytes. For more
         * details on Close message, status codes and reason see RFC 6455 sections
         * <a href="https://tools.ietf.org/html/rfc6455#section-5.5.1">5.5.1. Close</a>
         * and
         * <a href="https://tools.ietf.org/html/rfc6455#section-7.4">7.4. Status Codes</a>.
         *
         * <p> After the returned {@code CompletionStage} has completed
         * (normally or exceptionally), the {@code WebSocket} completes the
         * closing handshake by replying with an appropriate Close message.
         *
         * <p> This implementation replies with a Close message that has the
         * same code this message has and an empty reason.
         *
         * <p> {@code onClose} is the last invocation on the {@code Listener}.
         * It is invoked at most once, but after {@code onOpen}. If an exception
         * is thrown from this method, it is ignored.
         *
         * <p> The {@code WebSocket} will close at the earliest of completion of
         * the returned {@code CompletionStage} or sending a Close message. In
         * particular, if a Close message has been {@linkplain WebSocket#sendClose
         * sent} before, then this invocation completes the closing handshake
         * and by the time this method is invoked, the {@code WebSocket} will
         * have been closed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket
         * @param statusCode
         *         the status code
         * @param reason
         *         the reason
         *
         * @return a {@code CompletionStage} which completes when the {@code
         * WebSocket} can be closed; or {@code null} if it can be closed immediately
         *
         * @see #NORMAL_CLOSURE
         */
        default CompletionStage<?> onClose(WebSocket webSocket,
                                           int statusCode,
                                           String reason) {
            return null;
        }

        /**
         * Notifies an I/O or protocol error has occurred.
         *
         * <p> The {@code onError} method does not correspond to any message
         * from the WebSocket Protocol. It is a synthetic event and the last
         * {@code Listener}'s method to be invoked. It is invoked at most once
         * but after {@code onOpen}. If an exception is thrown from this method,
         * it is ignored.
         *
         * <p> Note that the WebSocket Protocol requires <i>some</i> errors
         * occur in the incoming destination must be fatal to the connection. In
         * such cases the implementation takes care of <i>Failing the WebSocket
         * Connection</i>: by the time {@code onError} is invoked, the {@code
         * WebSocket} will have been closed. Any outstanding or subsequent send
         * operation will complete exceptionally with an {@code IOException}.
         * For more details on Failing the WebSocket Connection see RFC 6455
         * section <a href="https://tools.ietf.org/html/rfc6455#section-7.1.7">7.1.7. Fail the WebSocket Connection</a>.
         *
         * @apiNote Errors associated with sending messages are reported to the
         * {@code CompletableFuture}s {@code sendX} methods return, rather than
         * to this this method.
         *
         * @implSpec The default implementation of this method does nothing.
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
     * {@Incubating}
     *
     * @see Listener#onText(WebSocket, CharSequence, MessagePart)
     * @see Listener#onBinary(WebSocket, ByteBuffer, MessagePart)
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
     * <p> The {@code CharSequence} must not be modified until the returned
     * {@code CompletableFuture} completes (either normally or exceptionally).
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IllegalArgumentException} -
     *          if {@code message} is a malformed UTF-16 sequence
     * <li> {@link IllegalStateException} -
     *          if the {@code WebSocket} is closed;
     *          or if a Close message has been sent;
     *          or if there is an outstanding send operation;
     *          or if a previous Binary message was sent with {@code isLast == false}
     * <li> {@link IOException} -
     *          if an I/O error occurs during this operation;
     *          or if the {@code WebSocket} has been closed due to an error;
     * </ul>
     *
     * @implNote This implementation does not accept partial UTF-16 sequences.
     * In case such a sequence is passed, a returned {@code CompletableFuture}
     * completes exceptionally with {@code IOException}.
     *
     * @param message
     *         the message
     * @param isLast
     *         {@code true} if this is the last part of the message,
     *         {@code false} otherwise
     *
     * @return a {@code CompletableFuture} with this {@code WebSocket}
     */
    CompletableFuture<WebSocket> sendText(CharSequence message, boolean isLast);

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
     * <li> {@link IllegalStateException} -
     *          if the {@code WebSocket} is closed;
     *          or if a Close message has been sent;
     *          or if there is an outstanding send operation;
     *          or if a previous Text message was sent with {@code isLast == false}
     * <li> {@link IOException} -
     *          if an I/O error occurs during this operation;
     *          or if the {@code WebSocket} has been closed due to an error
     * </ul>
     *
     * @param message
     *         the message
     * @param isLast
     *         {@code true} if this is the last part of the message,
     *         {@code false} otherwise
     *
     * @return a {@code CompletableFuture} with this {@code WebSocket}
     */
    CompletableFuture<WebSocket> sendBinary(ByteBuffer message, boolean isLast);

    /**
     * Sends a Ping message with bytes from the given ByteBuffer.
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
     * <li> {@link IllegalArgumentException} -
     *          if {@code message.remaining() > 125}
     * <li> {@link IllegalStateException} -
     *          if the {@code WebSocket} is closed;
     *          or if a Close message has been sent;
     *          or if there is an outstanding send operation
     * <li> {@link IOException} -
     *          if an I/O error occurs during this operation;
     *          or if the {@code WebSocket} has been closed due to an error
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a {@code CompletableFuture} with this {@code WebSocket}
     */
    CompletableFuture<WebSocket> sendPing(ByteBuffer message);

    /**
     * Sends a Pong message with bytes from the given ByteBuffer.
     *
     * <p> Returns a {@code CompletableFuture<WebSocket>} which completes
     * normally when the message has been sent or completes exceptionally if an
     * error occurs.
     *
     * <p> A Pong message may be unsolicited or may be sent in response to a
     * previously received Ping. In latter case the contents of the Pong must be
     * identical to the originating Ping.
     *
     * <p> The message must consist of not more than {@code 125} bytes: {@code
     * message.remaining() <= 125}.
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IllegalArgumentException} -
     *          if {@code message.remaining() > 125}
     * <li> {@link IllegalStateException} -
     *          if the {@code WebSocket} is closed;
     *          or if a Close message has been sent;
     *          or if there is an outstanding send operation
     * <li> {@link IOException} -
     *          if an I/O error occurs during this operation;
     *          or if the {@code WebSocket} has been closed due to an error
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a {@code CompletableFuture} with this {@code WebSocket}
     */
    CompletableFuture<WebSocket> sendPong(ByteBuffer message);

    /**
     * Sends a Close message with the given status code and the reason.
     *
     * <p> When this method has been invoked, no further messages can be sent.
     *
     * <p> The {@code statusCode} is an integer in the range {@code 1000 <= code
     * <= 4999}. However, not all status codes may be legal in some
     * implementations. Regardless of an implementation,
     * <code>{@value jdk.incubator.http.WebSocket#NORMAL_CLOSURE}</code>
     * is always legal and {@code 1002}, {@code 1003}, {@code 1005}, {@code
     * 1006}, {@code 1007}, {@code 1009}, {@code 1010}, {@code 1012}, {@code
     * 1013} and {@code 1015} are always illegal codes.
     *
     * <p> The {@code reason} is a short string that must have an UTF-8
     * representation not longer than {@code 123} bytes. For more details on
     * Close message, status codes and reason see RFC 6455 sections
     * <a href="https://tools.ietf.org/html/rfc6455#section-5.5.1">5.5.1. Close</a>
     * and
     * <a href="https://tools.ietf.org/html/rfc6455#section-7.4">7.4. Status Codes</a>.
     *
     * <p> The method returns a {@code CompletableFuture<WebSocket>} which
     * completes normally when the message has been sent or completes
     * exceptionally if an error occurs.
     *
     * <p> The returned {@code CompletableFuture} can complete exceptionally
     * with:
     * <ul>
     * <li> {@link IllegalArgumentException} -
     *          if the {@code statusCode} has an illegal value;
     *          or if {@code reason} doesn't have an UTF-8 representation of
     *          length {@code <= 123}
     * <li> {@link IOException} -
     *          if an I/O error occurs during this operation;
     *          or the {@code WebSocket} has been closed due to an error
     * </ul>
     *
     * <p> If this method has already been invoked or the {@code WebSocket} is
     * closed, then subsequent invocations of this method have no effect and the
     * returned {@code CompletableFuture} completes normally.
     *
     * <p> If a Close message has been {@linkplain Listener#onClose(WebSocket,
     * int, String) received} before, then this invocation completes the closing
     * handshake and by the time the returned {@code CompletableFuture}
     * completes, the {@code WebSocket} will have been closed.
     *
     * @param statusCode
     *         the status code
     * @param reason
     *         the reason
     *
     * @return a {@code CompletableFuture} with this {@code WebSocket}
     */
    CompletableFuture<WebSocket> sendClose(int statusCode, String reason);

    /**
     * Allows {@code n} more messages to be received by the {@link Listener
     * Listener}.
     *
     * <p> The actual number of received messages might be fewer if a Close
     * message is received, the {@code WebSocket} closes or an error occurs.
     *
     * <p> A {@code WebSocket} that has just been built, hasn't requested
     * anything yet. Usually the initial request for messages is made in {@link
     * Listener#onOpen(jdk.incubator.http.WebSocket) Listener.onOpen}.
     *
     * <p> If the {@code WebSocket} is closed then invoking this method has no
     * effect.
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
     * which has been chosen for this {@code WebSocket}.
     *
     * @return a subprotocol, or an empty {@code String} if there is none
     */
    String getSubprotocol();

    /**
     * Tells whether the {@code WebSocket} is closed.
     *
     * <p> When a {@code WebSocket} is closed no further messages can be sent or
     * received.
     *
     * @return {@code true} if the {@code WebSocket} is closed,
     *         {@code false} otherwise
     */
    boolean isClosed();

    /**
     * Closes the {@code WebSocket} abruptly.
     *
     * <p> This method may be invoked at any time. This method closes the
     * underlying TCP connection and puts the {@code WebSocket} into a closed
     * state.
     *
     * <p> As the result {@link Listener#onClose(WebSocket, int, String)
     * Listener.onClose} will be invoked unless either {@code onClose} or {@link
     * Listener#onError(WebSocket, Throwable) onError} has been invoked before.
     * In which case no additional invocation will happen.
     *
     * <p> If the {@code WebSocket} is already closed then invoking this method
     * has no effect.
     *
     * @throws IOException
     *         if an I/O error occurs
     */
    void abort() throws IOException;
}
