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

import java.net.http.WSFrame.HeaderBuilder;
import java.net.http.WSFrame.Masker;
import java.net.http.WSOutgoingMessage.Binary;
import java.net.http.WSOutgoingMessage.Close;
import java.net.http.WSOutgoingMessage.Ping;
import java.net.http.WSOutgoingMessage.Pong;
import java.net.http.WSOutgoingMessage.Text;
import java.net.http.WSOutgoingMessage.Visitor;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.security.SecureRandom;
import java.util.function.Consumer;

import static java.net.http.WSFrame.MAX_HEADER_SIZE_BYTES;
import static java.net.http.WSFrame.Opcode.BINARY;
import static java.net.http.WSFrame.Opcode.CLOSE;
import static java.net.http.WSFrame.Opcode.CONTINUATION;
import static java.net.http.WSFrame.Opcode.PING;
import static java.net.http.WSFrame.Opcode.PONG;
import static java.net.http.WSFrame.Opcode.TEXT;
import static java.util.Objects.requireNonNull;

/*
 * A Sender of outgoing messages.  Given a message,
 *
 *     1) constructs the frame
 *     2) initiates the channel write
 *     3) notifies when the message has been sent
 */
final class WSMessageSender {

    private final Visitor frameBuilderVisitor;
    private final Consumer<Throwable> completionEventConsumer;
    private final WSWriter writer;
    private final ByteBuffer[] buffers = new ByteBuffer[2];

    WSMessageSender(RawChannel channel, Consumer<Throwable> completionEventConsumer) {
        // Single reusable buffer that holds a header
        this.buffers[0] = ByteBuffer.allocateDirect(MAX_HEADER_SIZE_BYTES);
        this.frameBuilderVisitor = new FrameBuilderVisitor();
        this.completionEventConsumer = completionEventConsumer;
        this.writer = new WSWriter(channel, this.completionEventConsumer);
    }

    /*
     * Tries to send the given message fully. Invoked once per message.
     */
    boolean trySendFully(WSOutgoingMessage m) {
        requireNonNull(m);
        synchronized (this) {
            try {
                return sendNow(m);
            } catch (Exception e) {
                completionEventConsumer.accept(e);
                return false;
            }
        }
    }

    private boolean sendNow(WSOutgoingMessage m) {
        buffers[0].clear();
        m.accept(frameBuilderVisitor);
        buffers[0].flip();
        return writer.tryWriteFully(buffers);
    }

    /*
     * Builds and initiates a write of a frame, from a given message.
     */
    class FrameBuilderVisitor implements Visitor {

        private final SecureRandom random = new SecureRandom();
        private final WSCharsetToolkit.Encoder encoder = new WSCharsetToolkit.Encoder();
        private final Masker masker = new Masker();
        private final HeaderBuilder headerBuilder = new HeaderBuilder();
        private boolean previousIsLast = true;

        @Override
        public void visit(Text message) {
            try {
                buffers[1] = encoder.encode(CharBuffer.wrap(message.characters));
            } catch (CharacterCodingException e) {
                completionEventConsumer.accept(e);
                return;
            }
            int mask = random.nextInt();
            maskAndRewind(buffers[1], mask);
            headerBuilder
                    .fin(message.isLast)
                    .opcode(previousIsLast ? TEXT : CONTINUATION)
                    .payloadLen(buffers[1].remaining())
                    .mask(mask)
                    .build(buffers[0]);
            previousIsLast = message.isLast;
        }

        @Override
        public void visit(Binary message) {
            buffers[1] = message.bytes;
            int mask = random.nextInt();
            maskAndRewind(buffers[1], mask);
            headerBuilder
                    .fin(message.isLast)
                    .opcode(previousIsLast ? BINARY : CONTINUATION)
                    .payloadLen(message.bytes.remaining())
                    .mask(mask)
                    .build(buffers[0]);
            previousIsLast = message.isLast;
        }

        @Override
        public void visit(Ping message) {
            buffers[1] = message.bytes;
            int mask = random.nextInt();
            maskAndRewind(buffers[1], mask);
            headerBuilder
                    .fin(true)
                    .opcode(PING)
                    .payloadLen(message.bytes.remaining())
                    .mask(mask)
                    .build(buffers[0]);
        }

        @Override
        public void visit(Pong message) {
            buffers[1] = message.bytes;
            int mask = random.nextInt();
            maskAndRewind(buffers[1], mask);
            headerBuilder
                    .fin(true)
                    .opcode(PONG)
                    .payloadLen(message.bytes.remaining())
                    .mask(mask)
                    .build(buffers[0]);
        }

        @Override
        public void visit(Close message) {
            buffers[1] = message.bytes;
            int mask = random.nextInt();
            maskAndRewind(buffers[1], mask);
            headerBuilder
                    .fin(true)
                    .opcode(CLOSE)
                    .payloadLen(buffers[1].remaining())
                    .mask(mask)
                    .build(buffers[0]);
        }

        private void maskAndRewind(ByteBuffer b, int mask) {
            int oldPos = b.position();
            masker.mask(mask).applyMask(b, b);
            b.position(oldPos);
        }
    }
}
