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

import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public abstract class MockTransmitter extends Transmitter {

    private final long startTime = System.currentTimeMillis();

    private final Queue<OutgoingMessage> messages = new ConcurrentLinkedQueue<>();

    public MockTransmitter() {
        super(null);
    }

    @Override
    public void send(OutgoingMessage message,
                     Consumer<Exception> completionHandler) {
        System.out.printf("[%6s ms.] begin send(%s)%n",
                          System.currentTimeMillis() - startTime,
                          message);
        messages.add(message);
        whenSent().whenComplete((r, e) -> {
            System.out.printf("[%6s ms.] complete send(%s)%n",
                              System.currentTimeMillis() - startTime,
                              message);
            if (e != null) {
                completionHandler.accept((Exception) e);
            } else {
                completionHandler.accept(null);
            }
        });
        System.out.printf("[%6s ms.] end send(%s)%n",
                          System.currentTimeMillis() - startTime,
                          message);
    }

    @Override
    public void close() { }

    protected abstract CompletionStage<?> whenSent();

    public Queue<OutgoingMessage> queue() {
        return messages;
    }
}
