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

import java.net.http.WSOutgoingMessage.Binary;
import java.net.http.WSOutgoingMessage.Close;
import java.net.http.WSOutgoingMessage.Ping;
import java.net.http.WSOutgoingMessage.Pong;
import java.net.http.WSOutgoingMessage.Text;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.net.http.Pair.pair;

/*
 * Prepares outgoing messages for transmission.  Verifies the WebSocket state,
 * places the message on the outbound queue, and notifies the signal handler.
 */
final class WSTransmitter {

    private final BlockingQueue<Pair<WSOutgoingMessage, CompletableFuture<WebSocket>>>
            backlog = new LinkedBlockingQueue<>();
    private final WSMessageSender sender;
    private final WSSignalHandler handler;
    private final WebSocket webSocket;
    private boolean previousMessageSent = true;
    private boolean canSendBinary = true;
    private boolean canSendText = true;

    WSTransmitter(WebSocket ws, Executor executor, RawChannel channel, Consumer<Throwable> errorHandler) {
        this.webSocket = ws;
        this.handler = new WSSignalHandler(executor, this::handleSignal);
        Consumer<Throwable> sendCompletion = (error) -> {
            synchronized (this) {
                if (error == null) {
                    previousMessageSent = true;
                    handler.signal();
                } else {
                    errorHandler.accept(error);
                    backlog.forEach(p -> p.second.completeExceptionally(error));
                    backlog.clear();
                }
            }
        };
        this.sender = new WSMessageSender(channel, sendCompletion);
    }

    CompletableFuture<WebSocket> sendText(CharSequence message, boolean isLast) {
        checkAndUpdateText(isLast);
        return acceptMessage(new Text(isLast, message));
    }

    CompletableFuture<WebSocket> sendBinary(ByteBuffer message, boolean isLast) {
        checkAndUpdateBinary(isLast);
        return acceptMessage(new Binary(isLast, message));
    }

    CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        checkSize(message.remaining(), 125);
        return acceptMessage(new Ping(message));
    }

    CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        checkSize(message.remaining(), 125);
        return acceptMessage(new Pong(message));
    }

    CompletableFuture<WebSocket> sendClose(WebSocket.CloseCode code, CharSequence reason) {
        return acceptMessage(createCloseMessage(code, reason));
    }

    CompletableFuture<WebSocket> sendClose() {
        return acceptMessage(new Close(ByteBuffer.allocate(0)));
    }

    private CompletableFuture<WebSocket> acceptMessage(WSOutgoingMessage m) {
        CompletableFuture<WebSocket> cf = new CompletableFuture<>();
        synchronized (this) {
            backlog.offer(pair(m, cf));
        }
        handler.signal();
        return cf;
    }

    /* Callback for pulling messages from the queue, and initiating the send. */
    private void handleSignal() {
        synchronized (this) {
            while (!backlog.isEmpty() && previousMessageSent) {
                previousMessageSent = false;
                Pair<WSOutgoingMessage, CompletableFuture<WebSocket>> p = backlog.peek();
                boolean sent = sender.trySendFully(p.first);
                if (sent) {
                    backlog.remove();
                    p.second.complete(webSocket);
                    previousMessageSent = true;
                }
            }
        }
    }

    private Close createCloseMessage(WebSocket.CloseCode code, CharSequence reason) {
        // TODO: move to construction of CloseDetail (JDK-8155621)
        ByteBuffer b = ByteBuffer.allocateDirect(125).putChar((char) code.getCode());
        CoderResult result = StandardCharsets.UTF_8.newEncoder()
                .encode(CharBuffer.wrap(reason), b, true);
        if (result.isError()) {
            try {
                result.throwException();
            } catch (CharacterCodingException e) {
                throw new IllegalArgumentException("Reason is a malformed UTF-16 sequence", e);
            }
        } else if (result.isOverflow()) {
            throw new IllegalArgumentException("Reason is too long");
        }
        return new Close(b.flip());
    }

    private void checkSize(int size, int maxSize) {
        if (size > maxSize) {
            throw new IllegalArgumentException(
                    format("The message is too long: %s;" +
                            " expected not longer than %s", size, maxSize)
            );
        }
    }

    private void checkAndUpdateText(boolean isLast) {
        if (!canSendText) {
            throw new IllegalStateException("Unexpected text message");
        }
        canSendBinary = isLast;
    }

    private void checkAndUpdateBinary(boolean isLast) {
        if (!canSendBinary) {
            throw new IllegalStateException("Unexpected binary message");
        }
        canSendText = isLast;
    }
}
