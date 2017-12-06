/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Abstracts out I/O channel for the WebSocket implementation. The latter then
 * deals with input and output streams of messages and does not have to
 * understand the state machine of channels (e.g. how exactly they are closed).
 * Mocking this type will allow testing WebSocket message exchange in isolation.
 */
public class TransportSupplier {

    protected final RawChannel channel; /* Exposed for testing purposes */
    private final Object lock = new Object();
    private Transmitter transmitter;
    private Receiver receiver;
    private boolean receiverShutdown;
    private boolean transmitterShutdown;
    private boolean closed;

    public TransportSupplier(RawChannel channel) {
        this.channel = channel;
    }

    public Receiver receiver(MessageStreamConsumer consumer) {
        synchronized (lock) {
            if (receiver == null) {
                receiver = newReceiver(consumer);
            }
            return receiver;
        }
    }

    public Transmitter transmitter() {
        synchronized (lock) {
            if (transmitter == null) {
                transmitter = newTransmitter();
            }
            return transmitter;
        }
    }

    protected Receiver newReceiver(MessageStreamConsumer consumer) {
        return new Receiver(consumer, channel) {
            @Override
            public void close() throws IOException {
                synchronized (lock) {
                    if (!closed) {
                        try {
                            super.close();
                        } finally {
                            receiverShutdown = true;
                            if (transmitterShutdown) {
                                closed = true;
                                channel.close();
                            }
                        }
                    }
                }
            }
        };
    }

    protected Transmitter newTransmitter() {
        return new Transmitter(channel) {
            @Override
            public void close() throws IOException {
                synchronized (lock) {
                    if (!closed) {
                        try {
                            super.close();
                        } finally {
                            transmitterShutdown = true;
                            if (receiverShutdown) {
                                closed = true;
                                channel.close();
                            }
                        }
                    }
                }
            }
        };
    }
}
