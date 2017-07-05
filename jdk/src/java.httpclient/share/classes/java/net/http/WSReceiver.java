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
import java.io.UncheckedIOException;
import java.net.ProtocolException;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.ERROR;
import static java.net.http.WSUtils.EMPTY_BYTE_BUFFER;
import static java.net.http.WSUtils.logger;

/*
 * Receives incoming data from the channel and converts it into a sequence of
 * messages, which are then passed to the listener.
 */
final class WSReceiver {

    private final Listener listener;
    private final WebSocket webSocket;
    private final Supplier<WSShared<ByteBuffer>> buffersSupplier =
            new WSSharedPool<>(() -> ByteBuffer.allocateDirect(32768), 2);
    private final RawChannel channel;
    private final RawChannel.RawEvent channelEvent;
    private final WSSignalHandler handler;
    private final AtomicLong demand = new AtomicLong();
    private final AtomicBoolean readable = new AtomicBoolean();
    private boolean started;
    private volatile boolean closed;
    private final WSFrame.Reader reader = new WSFrame.Reader();
    private final WSFrameConsumer frameConsumer;
    private WSShared<ByteBuffer> buf = WSShared.wrap(EMPTY_BYTE_BUFFER);
    private WSShared<ByteBuffer> data; // TODO: initialize with leftovers from the RawChannel

    WSReceiver(Listener listener, WebSocket webSocket, Executor executor,
               RawChannel channel) {
        this.listener = listener;
        this.webSocket = webSocket;
        this.channel = channel;
        handler = new WSSignalHandler(executor, this::react);
        channelEvent = createChannelEvent();
        this.frameConsumer = new WSFrameConsumer(new MessageConsumer());
    }

    private void react() {
        synchronized (this) {
            while (demand.get() > 0 && !closed) {
                try {
                    if (data == null) {
                        if (!getData()) {
                            break;
                        }
                    }
                    reader.readFrame(data, frameConsumer);
                    if (!data.hasRemaining()) {
                        data.dispose();
                        data = null;
                    }
                    // In case of exception we don't need to clean any state,
                    // since it's the terminal condition anyway. Nothing will be
                    // retried.
                } catch (WSProtocolException e) {
                    // Translate into ProtocolException
                    closeExceptionally(new ProtocolException().initCause(e));
                } catch (Exception e) {
                    closeExceptionally(e);
                }
            }
        }
    }

    void request(long n) {
        long newDemand = demand.accumulateAndGet(n, (p, i) -> p + i < 0 ? Long.MAX_VALUE : p + i);
        handler.signal();
        assert newDemand >= 0 : newDemand;
    }

    private boolean getData() throws IOException {
        if (!readable.get()) {
            return false;
        }
        if (!buf.hasRemaining()) {
            buf.dispose();
            buf = buffersSupplier.get();
            assert buf.hasRemaining() : buf;
        }
        int oldPosition = buf.position();
        int oldLimit = buf.limit();
        int numRead = channel.read(buf.buffer());
        if (numRead > 0) {
            data = buf.share(oldPosition, oldPosition + numRead);
            buf.select(buf.limit(), oldLimit); // Move window to the free region
            return true;
        } else if (numRead == 0) {
            readable.set(false);
            channel.registerEvent(channelEvent);
            return false;
        } else {
            assert numRead < 0 : numRead;
            throw new WSProtocolException
                    ("7.2.1.", "Stream ended before a Close frame has been received");
        }
    }

    void start() {
        synchronized (this) {
            if (started) {
                throw new IllegalStateException("Already started");
            }
            started = true;
            try {
                channel.registerEvent(channelEvent);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            try {
                listener.onOpen(webSocket);
            } catch (Exception e) {
                closeExceptionally(new RuntimeException("onOpen threw an exception", e));
            }
        }
    }

    private void close() { // TODO: move to WS.java
        closed = true;
    }

    private void closeExceptionally(Throwable error) {  // TODO: move to WS.java
        close();
        try {
            listener.onError(webSocket, error);
        } catch (Exception e) {
            logger.log(ERROR, "onError threw an exception", e);
        }
    }

    private final class MessageConsumer implements WSMessageConsumer {

        @Override
        public void onText(WebSocket.MessagePart part, WSShared<CharBuffer> data) {
            decrementDemand();
            CompletionStage<?> cs;
            try {
                cs = listener.onText(webSocket, data.buffer(), part);
            } catch (Exception e) {
                closeExceptionally(new RuntimeException("onText threw an exception", e));
                return;
            }
            follow(cs, data, "onText");
        }

        @Override
        public void onBinary(WebSocket.MessagePart part, WSShared<ByteBuffer> data) {
            decrementDemand();
            CompletionStage<?> cs;
            try {
                cs = listener.onBinary(webSocket, data.buffer(), part);
            } catch (Exception e) {
                closeExceptionally(new RuntimeException("onBinary threw an exception", e));
                return;
            }
            follow(cs, data, "onBinary");
        }

        @Override
        public void onPing(WSShared<ByteBuffer> data) {
            decrementDemand();
            CompletionStage<?> cs;
            try {
                cs = listener.onPing(webSocket, data.buffer());
            } catch (Exception e) {
                closeExceptionally(new RuntimeException("onPing threw an exception", e));
                return;
            }
            follow(cs, data, "onPing");
        }

        @Override
        public void onPong(WSShared<ByteBuffer> data) {
            decrementDemand();
            CompletionStage<?> cs;
            try {
                cs = listener.onPong(webSocket, data.buffer());
            } catch (Exception e) {
                closeExceptionally(new RuntimeException("onPong threw an exception", e));
                return;
            }
            follow(cs, data, "onPong");
        }

        @Override
        public void onClose(WebSocket.CloseCode code, CharSequence reason) {
            decrementDemand();
            try {
                close();
                listener.onClose(webSocket, Optional.ofNullable(code), reason.toString());
            } catch (Exception e) {
                logger.log(ERROR, "onClose threw an exception", e);
            }
        }
    }

    private void follow(CompletionStage<?> cs, WSDisposable d, String source) {
        if (cs == null) {
            d.dispose();
        } else {
            cs.whenComplete((whatever, error) -> {
                if (error != null) {
                    String m = "CompletionStage returned by " + source + " completed exceptionally";
                    closeExceptionally(new RuntimeException(m, error));
                }
                d.dispose();
            });
        }
    }

    private void decrementDemand() {
        long newDemand = demand.decrementAndGet();
        assert newDemand >= 0 : newDemand;
    }

    private RawChannel.RawEvent createChannelEvent() {
        return new RawChannel.RawEvent() {

            @Override
            public int interestOps() {
                return SelectionKey.OP_READ;
            }

            @Override
            public void handle() {
                boolean wasNotReadable = readable.compareAndSet(false, true);
                assert wasNotReadable;
                handler.signal();
            }

            @Override
            public String toString() {
                return "Read readiness event [" + channel + "]";
            }
        };
    }
}
