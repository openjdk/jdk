/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.http.internal.websocket.TestSupport.F1;
import jdk.incubator.http.internal.websocket.TestSupport.F2;
import jdk.incubator.http.internal.websocket.TestSupport.F3;
import jdk.incubator.http.internal.websocket.TestSupport.InvocationChecker;
import jdk.incubator.http.internal.websocket.TestSupport.InvocationExpectation;
import jdk.incubator.http.internal.websocket.TestSupport.Mock;
import jdk.incubator.http.WebSocket;
import jdk.incubator.http.WebSocket.Listener;
import jdk.incubator.http.WebSocket.MessagePart;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

final class MockListener implements Listener, Mock {

    private final InvocationChecker checker;

    @Override
    public CompletableFuture<Void> expectations(long timeout, TimeUnit unit) {
        return checker.expectations(timeout, unit);
    }

    public static final class Builder {

        private final List<InvocationExpectation> expectations = new LinkedList<>();

        Builder expectOnOpen(F1<? super WebSocket, Boolean> predicate) {
            InvocationExpectation e = new InvocationExpectation("onOpen",
                    args -> predicate.apply((WebSocket) args[0]));
            expectations.add(e);
            return this;
        }

        Builder expectOnPing(F2<? super WebSocket, ? super ByteBuffer, Boolean> predicate) {
            InvocationExpectation e = new InvocationExpectation("onPing",
                    args -> predicate.apply((WebSocket) args[0], (ByteBuffer) args[1]));
            expectations.add(e);
            return this;
        }

        Builder expectOnClose(F3<? super WebSocket, ? super Integer, ? super String, Boolean> predicate) {
            expectations.add(new InvocationExpectation("onClose",
                    args -> predicate.apply((WebSocket) args[0], (Integer) args[1], (String) args[2])));
            return this;
        }

        Builder expectOnError(F2<? super WebSocket, ? super Throwable, Boolean> predicate) {
            expectations.add(new InvocationExpectation("onError",
                    args -> predicate.apply((WebSocket) args[0], (Throwable) args[1])));
            return this;
        }

        MockListener build() {
            return new MockListener(new LinkedList<>(expectations));
        }
    }

    private MockListener(List<InvocationExpectation> expectations) {
        this.checker = new InvocationChecker(expectations);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        checker.checkInvocation("onOpen", webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence message,
                                     MessagePart part) {
        checker.checkInvocation("onText", webSocket, message, part);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer message,
                                       MessagePart part) {
        checker.checkInvocation("onBinary", webSocket, message, part);
        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        checker.checkInvocation("onPing", webSocket, message);
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        checker.checkInvocation("onPong", webSocket, message);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode,
                                      String reason) {
        checker.checkInvocation("onClose", webSocket, statusCode, reason);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        checker.checkInvocation("onError", webSocket, error);
    }
}
