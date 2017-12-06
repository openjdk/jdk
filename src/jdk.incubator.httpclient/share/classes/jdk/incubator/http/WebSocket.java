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
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A WebSocket client.
 * {@Incubating}
 *
 * <p> To create a {@code WebSocket} use the {@link HttpClient#newWebSocketBuilder}
 * method. To close a {@code WebSocket} use one of the {@code sendClose} or
 * {@code abort} methods.
 *
 * <p> WebSocket messages are sent through a {@code WebSocket} and received
 * through the {@code WebSocket}'s {@code Listener}. Messages can be sent until
 * the output is closed, and received until the input is closed.
 * A {@code WebSocket} whose output and input are both closed may be considered
 * itself closed. To check these states use {@link #isOutputClosed()} and
 * {@link #isInputClosed()}.
 *
 * <p> Methods that send messages return {@code CompletableFuture} which
 * completes normally if the message is sent or completes exceptionally if an
 * error occurs.
 *
 * <p> To receive a message, first request it. If {@code n} messages are
 * requested, the listener will receive up to {@code n} more invocations of the
 * designated methods from the {@code WebSocket}. To request messages use
 * {@link #request(long)}. Request is an additive operation, that is
 * {@code request(n)} followed by {@code request(m)} is equivalent to
 * {@code request(n + m)}.
 *
 * <p> When sending or receiving a message in parts, a whole message is
 * transferred as a sequence of one or more invocations where the last
 * invocation is identified via an additional method argument.
 *
 * <p> Unless otherwise stated, {@code null} arguments will cause methods
 * of {@code WebSocket} to throw {@code NullPointerException}, similarly,
 * {@code WebSocket} will not pass {@code null} arguments to methods of
 * {@code Listener}.
 *
 * @implSpec Methods of {@code WebSocket} are failure-atomic in respect to
 * {@code NullPointerException}, {@code IllegalArgumentException} and
 * {@code IllegalStateException}. That is, if a method throws said exception, or
 * a returned {@code CompletableFuture} completes exceptionally with said
 * exception, the {@code WebSocket} will behave as if the method has not been
 * invoked at all.
 *
 * <p> A {@code WebSocket} invokes methods of its listener in a thread-safe
 * manner.
 *
 * <p> {@code WebSocket} handles Ping and Close messages automatically (as per
 * RFC 6455) by replying with Pong and Close messages respectively. If the
 * listener receives Ping or Close messages, no mandatory actions from the
 * listener are required.
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
     * <p> To obtain a {@code WebSocket} configure a builder as required by
     * calling intermediate methods (the ones that return the builder itself),
     * then call {@code buildAsync()}. If an intermediate method is not called,
     * an appropriate default value (or behavior) will be assumed.
     *
     * <p> Unless otherwise stated, {@code null} arguments will cause methods of
     * {@code Builder} to throw {@code NullPointerException}.
     *
     * @since 9
     */
    interface Builder {

        /**
         * Adds the given name-value pair to the list of additional HTTP headers
         * sent during the opening handshake.
         *
         * <p> Headers defined in WebSocket Protocol are illegal. If this method
         * is not invoked, no additional HTTP headers will be sent.
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
         * Sets a timeout for establishing a WebSocket connection.
         *
         * <p> If the connection is not established within the specified
         * duration then building of the {@code WebSocket} will fail with
         * {@link HttpTimeoutException}. If this method is not invoked then the
         * infinite timeout is assumed.
         *
         * @param timeout
         *         the timeout, non-{@linkplain Duration#isNegative() negative},
         *         non-{@linkplain Duration#ZERO ZERO}
         *
         * @return this builder
         */
        Builder connectTimeout(Duration timeout);

        /**
         * Sets a request for the given subprotocols.
         *
         * <p> After the {@code WebSocket} has been built, the actual
         * subprotocol can be queried via
         * {@link WebSocket#getSubprotocol WebSocket.getSubprotocol()}.
         *
         * <p> Subprotocols are specified in the order of preference. The most
         * preferred subprotocol is specified first. If there are any additional
         * subprotocols they are enumerated from the most preferred to the least
         * preferred.
         *
         * <p> Subprotocols not conforming to the syntax of subprotocol
         * identifiers are illegal. If this method is not invoked then no
         * subprotocols will be requested.
         *
         * @param mostPreferred
         *         the most preferred subprotocol
         * @param lesserPreferred
         *         the lesser preferred subprotocols
         *
         * @return this builder
         */
        Builder subprotocols(String mostPreferred, String... lesserPreferred);

        /**
         * Builds a {@link WebSocket} connected to the given {@code URI} and
         * associated with the given {@code Listener}.
         *
         * <p> Returns a {@code CompletableFuture} which will either complete
         * normally with the resulting {@code WebSocket} or complete
         * exceptionally with one of the following errors:
         * <ul>
         * <li> {@link IOException} -
         *          if an I/O error occurs
         * <li> {@link WebSocketHandshakeException} -
         *          if the opening handshake fails
         * <li> {@link HttpTimeoutException} -
         *          if the opening handshake does not complete within
         *          the timeout
         * <li> {@link InterruptedException} -
         *          if the operation is interrupted
         * <li> {@link SecurityException} -
         *          if a security manager has been installed and it denies
         *          {@link java.net.URLPermission access} to {@code uri}.
         *          <a href="HttpRequest.html#securitychecks">Security checks</a>
         *          contains more information relating to the security context
         *          in which the the listener is invoked.
         * <li> {@link IllegalArgumentException} -
         *          if any of the arguments of this builder's methods are
         *          illegal
         * </ul>
         *
         * @param uri
         *         the WebSocket URI
         * @param listener
         *         the listener
         *
         * @return a {@code CompletableFuture} with the {@code WebSocket}
         */
        CompletableFuture<WebSocket> buildAsync(URI uri, Listener listener);
    }

    /**
     * The receiving interface of {@code WebSocket}.
     * {@Incubating}
     *
     * <p> A {@code WebSocket} invokes methods on its listener when it receives
     * messages or encounters events. The invoking {@code WebSocket} is passed
     * as an argument to {@code Listener}'s methods. A {@code WebSocket} invokes
     * methods on its listener in a thread-safe manner.
     *
     * <p> Unless otherwise stated if a listener's method throws an exception or
     * a {@code CompletionStage} returned from a method completes exceptionally,
     * the {@code WebSocket} will invoke {@code onError} with this exception.
     *
     * <p> If a listener's method returns {@code null} rather than a
     * {@code CompletionStage}, {@code WebSocket} will behave as if the listener
     * returned a {@code CompletionStage} that is already completed normally.
     *
     * @since 9
     */
    interface Listener {

        /**
         * A {@code WebSocket} has been connected.
         *
         * <p> This is the first invocation and it is made at most once. This
         * method is typically used to make an initial request for messages.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket that has been connected
         */
        default void onOpen(WebSocket webSocket) { webSocket.request(1); }

        /**
         * A Text message has been received.
         *
         * <p> If a whole message has been received, this method will be invoked
         * with {@code MessagePart.WHOLE} marker. Otherwise, it will be invoked
         * with {@code FIRST}, possibly a number of times with {@code PART} and,
         * finally, with {@code LAST} markers. If this message is partial, it
         * may be an incomplete UTF-16 sequence. However, the concatenation of
         * all messages through the last will be a complete UTF-16 sequence.
         *
         * <p> Return a {@code CompletionStage} which will be used by the
         * {@code WebSocket} as a signal it may reclaim the
         * {@code CharSequence}. Do not access the {@code CharSequence} after
         * this {@ode CompletionStage} has completed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @implNote This method is always invoked with character sequences
         * which are complete UTF-16 sequences.
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param message
         *         the message
         * @param part
         *         the part
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code CharSequence} may be reclaimed; or {@code null} if it may be
         * reclaimed immediately
         */
        default CompletionStage<?> onText(WebSocket webSocket,
                                          CharSequence message,
                                          MessagePart part) {
            webSocket.request(1);
            return null;
        }

        /**
         * A Binary message has been received.
         *
         * <p> If a whole message has been received, this method will be invoked
         * with {@code MessagePart.WHOLE} marker. Otherwise, it will be invoked
         * with {@code FIRST}, possibly a number of times with {@code PART} and,
         * finally, with {@code LAST} markers.
         *
         * <p> This message consists of bytes from the buffer's position to
         * its limit.
         *
         * <p> Return a {@code CompletionStage} which will be used by the
         * {@code WebSocket} as a signal it may reclaim the
         * {@code ByteBuffer}. Do not access the {@code ByteBuffer} after
         * this {@ode CompletionStage} has completed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param message
         *         the message
         * @param part
         *         the part
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code ByteBuffer} may be reclaimed; or {@code null} if it may be
         * reclaimed immediately
         */
        default CompletionStage<?> onBinary(WebSocket webSocket,
                                            ByteBuffer message,
                                            MessagePart part) {
            webSocket.request(1);
            return null;
        }

        /**
         * A Ping message has been received.
         *
         * <p> The message consists of not more than {@code 125} bytes from
         * the buffer's position to its limit.
         *
         * <p> Return a {@code CompletionStage} which will be used by the
         * {@code WebSocket} as a signal it may reclaim the
         * {@code ByteBuffer}. Do not access the {@code ByteBuffer} after
         * this {@ode CompletionStage} has completed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param message
         *         the message
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code ByteBuffer} may be reclaimed; or {@code null} if it may be
         * reclaimed immediately
         */
        default CompletionStage<?> onPing(WebSocket webSocket,
                                          ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        /**
         * A Pong message has been received.
         *
         * <p> The message consists of not more than {@code 125} bytes from
         * the buffer's position to its limit.
         *
         * <p> Return a {@code CompletionStage} which will be used by the
         * {@code WebSocket} as a signal it may reclaim the
         * {@code ByteBuffer}. Do not access the {@code ByteBuffer} after
         * this {@ode CompletionStage} has completed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param message
         *         the message
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code ByteBuffer} may be reclaimed; or {@code null} if it may be
         * reclaimed immediately
         */
        default CompletionStage<?> onPong(WebSocket webSocket,
                                          ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        /**
         * A Close message has been received.
         *
         * <p> This is the last invocation from the {@code WebSocket}. By the
         * time this invocation begins the {@code WebSocket}'s input will have
         * been closed. Be prepared to receive this invocation at any time after
         * {@code onOpen} regardless of whether or not any messages have been
         * requested from the {@code WebSocket}.
         *
         * <p> A Close message consists of a status code and a reason for
         * closing. The status code is an integer from the range
         * {@code 1000 <= code <= 65535}. The {@code reason} is a string which
         * has an UTF-8 representation not longer than {@code 123} bytes.
         *
         * <p> Return a {@code CompletionStage} that will be used by the
         * {@code WebSocket} as a signal that it may close the output. The
         * {@code WebSocket} will close the output at the earliest of completion
         * of the returned {@code CompletionStage} or invoking a
         * {@link WebSocket#sendClose(int, String) sendClose} method.
         *
         * <p> If an exception is thrown from this method or a
         * {@code CompletionStage} returned from it completes exceptionally,
         * the resulting behaviour is undefined.
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param statusCode
         *         the status code
         * @param reason
         *         the reason
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code WebSocket} may be closed; or {@code null} if it may be
         * closed immediately
         */
        default CompletionStage<?> onClose(WebSocket webSocket,
                                           int statusCode,
                                           String reason) {
            return null;
        }

        /**
         * An unrecoverable error has occurred.
         *
         * <p> This is the last invocation from the {@code WebSocket}. By the
         * time this invocation begins both {@code WebSocket}'s input and output
         * will have been closed. Be prepared to receive this invocation at any
         * time after {@code onOpen} regardless of whether or not any messages
         * have been requested from the {@code WebSocket}.
         *
         * <p> If an exception is thrown from this method, resulting behavior is
         * undefined.
         *
         * @param webSocket
         *         the WebSocket on which the error has occurred
         * @param error
         *         the error
         */
        default void onError(WebSocket webSocket, Throwable error) { }
    }

    /**
     * A marker used by {@link WebSocket.Listener} for identifying partial
     * messages.
     * {@Incubating}
     *
     * @since 9
     */
    enum MessagePart {

        /**
         * The first part of a message.
         */
        FIRST,

        /**
         * A middle part of a message.
         */
        PART,

        /**
         * The last part of a message.
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
     * <p> To send a Text message invoke this method only after the previous
     * Text or Binary message has been sent. The character sequence must not be
     * modified until the {@code CompletableFuture} returned from this method
     * has completed.
     *
     * <p> A {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalArgumentException} -
     *          if {@code message} is a malformed UTF-16 sequence
     * <li> {@link IllegalStateException} -
     *          if {@code sendClose} has been invoked
     *          or if the previous message has not been sent yet
     *          or if a previous Binary message was sent with
     *          {@code isLast == false}
     * <li> {@link IOException} -
     *          if an I/O error occurs
     * </ul>
     *
     * @implNote If a partial UTF-16 sequence is passed to this method, a
     * {@code CompletableFuture} returned will complete exceptionally with
     * {@code IOException}.
     *
     * @param message
     *         the message
     * @param isLast
     *         {@code true} if this is the last part of the message,
     *         {@code false} otherwise
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the message has been sent
     */
    CompletableFuture<WebSocket> sendText(CharSequence message, boolean isLast);

    /**
     * Sends a Binary message with bytes from the given {@code ByteBuffer}.
     *
     * <p> To send a Binary message invoke this method only after the previous
     * Text or Binary message has been sent. The message consists of bytes from
     * the buffer's position to its limit. Upon normal completion of a
     * {@code CompletableFuture} returned from this method the buffer will have
     * no remaining bytes. The buffer must not be accessed until after that.
     *
     * <p> The {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalStateException} -
     *          if {@code sendClose} has been invoked
     *          or if the previous message has not been sent yet
     *          or if a previous Text message was sent with
     *              {@code isLast == false}
     * <li> {@link IOException} -
     *          if an I/O error occurs
     * </ul>
     *
     * @param message
     *         the message
     * @param isLast
     *         {@code true} if this is the last part of the message,
     *         {@code false} otherwise
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the message has been sent
     */
    CompletableFuture<WebSocket> sendBinary(ByteBuffer message, boolean isLast);

    /**
     * Sends a Ping message with bytes from the given {@code ByteBuffer}.
     *
     * <p> The message consists of not more than {@code 125} bytes from the
     * buffer's position to its limit. Upon normal completion of a
     * {@code CompletableFuture} returned from this method the buffer will
     * have no remaining bytes. The buffer must not be accessed until after that.
     *
     * <p> The {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalArgumentException} -
     *          if the message is too long
     * <li> {@link IllegalStateException} -
     *          if {@code sendClose} has been invoked
     * <li> {@link IOException} -
     *          if an I/O error occurs
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the Ping message has been sent
     */
    CompletableFuture<WebSocket> sendPing(ByteBuffer message);

    /**
     * Sends a Pong message with bytes from the given {@code ByteBuffer}.
     *
     * <p> The message consists of not more than {@code 125} bytes from the
     * buffer's position to its limit. Upon normal completion of a
     * {@code CompletableFuture} returned from this method the buffer will have
     * no remaining bytes. The buffer must not be accessed until after that.
     *
     * <p> The {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalArgumentException} -
     *          if the message is too long
     * <li> {@link IllegalStateException} -
     *          if {@code sendClose} has been invoked
     * <li> {@link IOException} -
     *          if an I/O error occurs
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the Pong message has been sent
     */
    CompletableFuture<WebSocket> sendPong(ByteBuffer message);

    /**
     * Sends a Close message with the given status code and the reason,
     * initiating an orderly closure.
     *
     * <p> When this method returns the output will have been closed.
     *
     * <p> The {@code statusCode} is an integer from the range
     * {@code 1000 <= code <= 4999}. Status codes {@code 1002}, {@code 1003},
     * {@code 1006}, {@code 1007}, {@code 1009}, {@code 1010}, {@code 1012},
     * {@code 1013} and {@code 1015} are illegal. Behaviour in respect to other
     * status codes is implementation-specific. The {@code reason} is a string
     * that has an UTF-8 representation not longer than {@code 123} bytes.
     *
     * <p> Use the provided integer constant {@link #NORMAL_CLOSURE} as a status
     * code and an empty string as a reason in a typical case.
     *
     * <p> A {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalArgumentException} -
     *          if {@code statusCode} or {@code reason} are illegal
     * <li> {@link IOException} -
     *          if an I/O error occurs
     * </ul>
     *
     * @implSpec An endpoint sending a Close message might not receive a
     * complementing Close message in a timely manner for a variety of reasons.
     * The {@code WebSocket} implementation is responsible for providing a
     * closure mechanism that guarantees that once {@code sendClose} method has
     * been invoked the {@code WebSocket} will close regardless of whether or
     * not a Close frame has been received and without further intervention from
     * the user of this API. Method {@code sendClose} is designed to be,
     * possibly, the last call from the user of this API.
     *
     * @param statusCode
     *         the status code
     * @param reason
     *         the reason
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the Close message has been sent
     */
    CompletableFuture<WebSocket> sendClose(int statusCode, String reason);

    /**
     * Requests {@code n} more messages from this {@code WebSocket}.
     *
     * <p> This {@code WebSocket} will invoke its listener's {@code onText},
     * {@code onBinary}, {@code onPing}, {@code onPong} or {@code onClose}
     * methods up to {@code n} more times.
     *
     * <p> This method may be invoked at any time.
     *
     * @param n
     *         the number of messages requested
     *
     * @throws IllegalArgumentException
     *         if {@code n <= 0}
     */
    void request(long n);

    /**
     * Returns the subprotocol for this {@code WebSocket}.
     *
     * <p> This method may be invoked at any time.
     *
     * @return the subprotocol for this {@code WebSocket}, or an empty
     * {@code String} if there's no subprotocol
     */
    String getSubprotocol();

    /**
     * Tells whether or not this {@code WebSocket} is permanently closed
     * for sending messages.
     *
     * <p> If this method returns {@code true}, subsequent invocations will also
     * return {@code true}. This method may be invoked at any time.
     *
     * @return {@code true} if closed, {@code false} otherwise
     */
    boolean isOutputClosed();

    /**
     * Tells whether or not this {@code WebSocket} is permanently closed
     * for receiving messages.
     *
     * <p> If this method returns {@code true}, subsequent invocations will also
     * return {@code true}. This method may be invoked at any time.
     *
     * @return {@code true} if closed, {@code false} otherwise
     */
    boolean isInputClosed();

    /**
     * Closes this {@code WebSocket} abruptly.
     *
     * <p> When this method returns both the input and output will have been
     * closed. This method may be invoked at any time. Subsequent invocations
     * have no effect.
     *
     * @apiNote Depending on its implementation, the state (for example, whether
     * or not a message is being transferred at the moment) and possible errors
     * while releasing associated resources, this {@code WebSocket} may invoke
     * its listener's {@code onError}.
     */
    void abort();
}
