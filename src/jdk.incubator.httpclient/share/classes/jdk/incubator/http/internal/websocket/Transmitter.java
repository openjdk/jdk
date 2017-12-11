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

package jdk.incubator.http.internal.websocket;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/*
 * Sends messages one at a time, in an asynchronous and non-blocking fashion.
 *
 * No matter whether the message has been fully sent or an error has occurred,
 * the transmitter reports the outcome to the supplied handler and becomes ready
 * to accept a new message. Until then, the transmitter is considered "busy" and
 * an IllegalStateException will be thrown on each attempt to invoke send.
 */
public class Transmitter {

    /* This flag is used solely for assertions */
    private final AtomicBoolean busy = new AtomicBoolean();
    private OutgoingMessage message;
    private Consumer<Exception> completionHandler;
    private final RawChannel channel;
    private final RawChannel.RawEvent event;

    public Transmitter(RawChannel channel) {
        this.channel = channel;
        this.event = createHandler();
    }

    /**
     * The supplied handler may be invoked in the calling thread.
     * A {@code StackOverflowError} may thus occur if there's a possibility
     * that this method is called again by the supplied handler.
     */
    public void send(OutgoingMessage message,
                     Consumer<Exception> completionHandler)
    {
        requireNonNull(message);
        requireNonNull(completionHandler);
        if (!busy.compareAndSet(false, true)) {
            throw new IllegalStateException();
        }
        send0(message, completionHandler);
    }

    public void close() throws IOException {
        channel.shutdownOutput();
    }

    private RawChannel.RawEvent createHandler() {
        return new RawChannel.RawEvent() {

            @Override
            public int interestOps() {
                return SelectionKey.OP_WRITE;
            }

            @Override
            public void handle() {
                // registerEvent(e) happens-before subsequent e.handle(), so
                // we're fine reading the stored message and the completionHandler
                send0(message, completionHandler);
            }
        };
    }

    private void send0(OutgoingMessage message, Consumer<Exception> handler) {
        boolean b = busy.get();
        assert b; // Please don't inline this, as busy.get() has memory
                  // visibility effects and we don't want the program behaviour
                  // to depend on whether the assertions are turned on
                  // or turned off
        try {
            boolean sent = message.sendTo(channel);
            if (sent) {
                busy.set(false);
                handler.accept(null);
            } else {
                // The message has not been fully sent, the transmitter needs to
                // remember the message until it can continue with sending it
                this.message = message;
                this.completionHandler = handler;
                try {
                    channel.registerEvent(event);
                } catch (IOException e) {
                    this.message = null;
                    this.completionHandler = null;
                    busy.set(false);
                    handler.accept(e);
                }
            }
        } catch (IOException e) {
            busy.set(false);
            handler.accept(e);
        }
    }
}
