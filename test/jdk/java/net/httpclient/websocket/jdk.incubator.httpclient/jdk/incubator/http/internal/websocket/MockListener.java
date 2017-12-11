/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.incubator.http.internal.websocket;

import jdk.incubator.http.WebSocket;
import jdk.incubator.http.WebSocket.MessagePart;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static jdk.incubator.http.internal.websocket.TestSupport.fullCopy;

public class MockListener implements WebSocket.Listener {

    private final long bufferSize;
    private long count;
    private final List<ListenerInvocation> invocations = new ArrayList<>();
    private final CompletableFuture<?> lastCall = new CompletableFuture<>();

    /*
     * Typical buffer sizes: 1, n, Long.MAX_VALUE
     */
    public MockListener(long bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException();
        }
        this.bufferSize = bufferSize;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.printf("onOpen(%s)%n", webSocket);
        invocations.add(new OnOpen(webSocket));
        onOpen0(webSocket);
    }

    protected void onOpen0(WebSocket webSocket) {
        replenish(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket,
                                     CharSequence message,
                                     MessagePart part) {
        System.out.printf("onText(%s, %s, %s)%n", webSocket, message, part);
        invocations.add(new OnText(webSocket, message.toString(), part));
        return onText0(webSocket, message, part);
    }

    protected CompletionStage<?> onText0(WebSocket webSocket,
                                         CharSequence message,
                                         MessagePart part) {
        replenish(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket,
                                       ByteBuffer message,
                                       MessagePart part) {
        System.out.printf("onBinary(%s, %s, %s)%n", webSocket, message, part);
        invocations.add(new OnBinary(webSocket, fullCopy(message), part));
        return onBinary0(webSocket, message, part);
    }

    protected CompletionStage<?> onBinary0(WebSocket webSocket,
                                           ByteBuffer message,
                                           MessagePart part) {
        replenish(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        System.out.printf("onPing(%s, %s)%n", webSocket, message);
        invocations.add(new OnPing(webSocket, fullCopy(message)));
        return onPing0(webSocket, message);
    }

    protected CompletionStage<?> onPing0(WebSocket webSocket, ByteBuffer message) {
        replenish(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        System.out.printf("onPong(%s, %s)%n", webSocket, message);
        invocations.add(new OnPong(webSocket, fullCopy(message)));
        return onPong0(webSocket, message);
    }

    protected CompletionStage<?> onPong0(WebSocket webSocket, ByteBuffer message) {
        replenish(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket,
                                      int statusCode,
                                      String reason) {
        System.out.printf("onClose(%s, %s, %s)%n", webSocket, statusCode, reason);
        invocations.add(new OnClose(webSocket, statusCode, reason));
        lastCall.complete(null);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.out.printf("onError(%s, %s)%n", webSocket, error);
        invocations.add(new OnError(webSocket, error == null ? null : error.getClass()));
        lastCall.complete(null);
    }

    public CompletableFuture<?> onCloseOrOnErrorCalled() {
        return lastCall.copy();
    }

    protected void replenish(WebSocket webSocket) {
        if (--count <= 0) {
            count = bufferSize - bufferSize / 2;
        }
        webSocket.request(count);
    }

    public List<ListenerInvocation> invocations() {
        return new ArrayList<>(invocations);
    }

    public abstract static class ListenerInvocation {

        public static OnOpen onOpen(WebSocket webSocket) {
            return new OnOpen(webSocket);
        }

        public static OnText onText(WebSocket webSocket,
                                    String text,
                                    MessagePart part) {
            return new OnText(webSocket, text, part);
        }

        public static OnBinary onBinary(WebSocket webSocket,
                                        ByteBuffer data,
                                        MessagePart part) {
            return new OnBinary(webSocket, data, part);
        }

        public static OnPing onPing(WebSocket webSocket,
                                    ByteBuffer data) {
            return new OnPing(webSocket, data);
        }

        public static OnPong onPong(WebSocket webSocket,
                                    ByteBuffer data) {
            return new OnPong(webSocket, data);
        }

        public static OnClose onClose(WebSocket webSocket,
                                      int statusCode,
                                      String reason) {
            return new OnClose(webSocket, statusCode, reason);
        }

        public static OnError onError(WebSocket webSocket,
                                      Class<? extends Throwable> clazz) {
            return new OnError(webSocket, clazz);
        }

        final WebSocket webSocket;

        private ListenerInvocation(WebSocket webSocket) {
            this.webSocket = webSocket;
        }
    }

    public static final class OnOpen extends ListenerInvocation {

        public OnOpen(WebSocket webSocket) {
            super(webSocket);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListenerInvocation that = (ListenerInvocation) o;
            return Objects.equals(webSocket, that.webSocket);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(webSocket);
        }
    }

    public static final class OnText extends ListenerInvocation {

        final String text;
        final MessagePart part;

        public OnText(WebSocket webSocket, String text, MessagePart part) {
            super(webSocket);
            this.text = text;
            this.part = part;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OnText onText = (OnText) o;
            return Objects.equals(text, onText.text) &&
                    part == onText.part &&
                    Objects.equals(webSocket, onText.webSocket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, part, webSocket);
        }

        @Override
        public String toString() {
            return String.format("onText(%s, %s, %s)", webSocket, text, part);
        }
    }

    public static final class OnBinary extends ListenerInvocation {

        final ByteBuffer data;
        final MessagePart part;

        public OnBinary(WebSocket webSocket, ByteBuffer data, MessagePart part) {
            super(webSocket);
            this.data = data;
            this.part = part;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OnBinary onBinary = (OnBinary) o;
            return Objects.equals(data, onBinary.data) &&
                    part == onBinary.part &&
                    Objects.equals(webSocket, onBinary.webSocket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data, part, webSocket);
        }

        @Override
        public String toString() {
            return String.format("onBinary(%s, %s, %s)", webSocket, data, part);
        }
    }

    public static final class OnPing extends ListenerInvocation {

        final ByteBuffer data;

        public OnPing(WebSocket webSocket, ByteBuffer data) {
            super(webSocket);
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OnPing onPing = (OnPing) o;
            return Objects.equals(data, onPing.data) &&
                    Objects.equals(webSocket, onPing.webSocket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data, webSocket);
        }

        @Override
        public String toString() {
            return String.format("onPing(%s, %s)", webSocket, data);
        }
    }

    public static final class OnPong extends ListenerInvocation {

        final ByteBuffer data;

        public OnPong(WebSocket webSocket, ByteBuffer data) {
            super(webSocket);
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OnPong onPong = (OnPong) o;
            return Objects.equals(data, onPong.data) &&
                    Objects.equals(webSocket, onPong.webSocket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data, webSocket);
        }

        @Override
        public String toString() {
            return String.format("onPong(%s, %s)", webSocket, data);
        }
    }

    public static final class OnClose extends ListenerInvocation {

        final int statusCode;
        final String reason;

        public OnClose(WebSocket webSocket, int statusCode, String reason) {
            super(webSocket);
            this.statusCode = statusCode;
            this.reason = reason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OnClose onClose = (OnClose) o;
            return statusCode == onClose.statusCode &&
                    Objects.equals(reason, onClose.reason) &&
                    Objects.equals(webSocket, onClose.webSocket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(statusCode, reason, webSocket);
        }

        @Override
        public String toString() {
            return String.format("onClose(%s, %s, %s)", webSocket, statusCode, reason);
        }
    }

    public static final class OnError extends ListenerInvocation {

        final Class<? extends Throwable> clazz;

        public OnError(WebSocket webSocket, Class<? extends Throwable> clazz) {
            super(webSocket);
            this.clazz = clazz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OnError onError = (OnError) o;
            return Objects.equals(clazz, onError.clazz) &&
                    Objects.equals(webSocket, onError.webSocket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, webSocket);
        }

        @Override
        public String toString() {
            return String.format("onError(%s, %s)", webSocket, clazz);
        }
    }
}
