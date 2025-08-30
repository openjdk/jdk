/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.http3.frames;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.BuffersReader;
import jdk.internal.net.http.quic.BuffersReader.ListBuffersReader;

/**
 * A FramesDecoder accumulates buffers until a frame can be
 * decoded. It also supports decoding {@linkplain PartialFrame
 * partial frames} and {@linkplain #readPayloadBytes() reading
 * their payload} incrementally.
 * @apiNote
 * When the frame decoder {@linkplain #poll() returns} a partial
 * frame, the same frame will be returned until its payload has been
 * {@linkplain PartialFrame#remaining() fully} {@linkplain #readPayloadBytes()
 * read}.
 * The caller is supposed to call {@link #readPayloadBytes()} until
 * {@link #poll()} returns a different frame. At this point there will be no
 * {@linkplain PartialFrame#remaining() remaining} payload bytes to read for
 * the previous frame.
 * <br>
 * The sequence of calls: {@snippet :
 *    framesDecoder.submit(buffer);
 *    while ((frame = framesDecoder.poll()) != null) {
 *        if (frame instanceof PartialFrame partial) {
 *            var nextPayloadBytes = framesDecoder.readPayloadBytes();
 *            if (nextPayloadBytes == null || nextPayloadBytes.isEmpty()) {
 *                 // no more data is available at this moment
 *                 break;
 *            }
 *            // nextPayloadBytes are the next bytes for the payload
 *            // of the partial frame
 *            deliverBytes(partial, nextPayloadBytes);
 *        } else ...
 *            // got a full frame...
 *    }
 * }
 * makes it possible to incrementally deliver payload bytes for
 * a frame - since {@code poll()} will always return the same partial
 * frame until all its payload has been read.
 */
public class FramesDecoder {

    private final Logger debug = Utils.getDebugLogger(this::dbgTag);
    private final ListBuffersReader framesReader = BuffersReader.list();
    private final ReentrantLock lock = new ReentrantLock();

    private final Supplier<String> dbgTag;
    private final LongPredicate isAllowed;

    // the current partial frame or null
    PartialFrame partialFrame;
    boolean eof;

    /**
     * A new {@code FramesDecoder} that accepts all frames.
     * @param dbgTag a debug tag for logging
     */
    public FramesDecoder(String dbgTag) {
        this(dbgTag, FramesDecoder::allAllowed);
    }

    /**
     * A new {@code FramesDecoder} that accepts only frames
     * authorized by the given {@code isAllowed} predicate.
     * If a frame is not allowed, a {@link MalformedFrame} is
     * returned.
     * @param dbgTag a debug tag for logging
     */
    public FramesDecoder(String dbgTag, LongPredicate isAllowed) {
        this(() -> dbgTag, Objects.requireNonNull(isAllowed));
    }

    /**
     * A new {@code FramesDecoder} that accepts only frames
     * authorized by the given {@code isAllowed} predicate.
     * If a frame is not allowed, a {@link MalformedFrame} is
     * returned.
     * @param dbgTag a debug tag for logging
     */
    public FramesDecoder(Supplier<String> dbgTag, LongPredicate isAllowed) {
        this.dbgTag = dbgTag;
        this.isAllowed = Objects.requireNonNull(isAllowed);
    }

    String dbgTag() { return dbgTag.get(); }

    /**
     * Submit a new buffer to this frames decoder
     * @param buffer a new buffer from the stream
     */
    public void submit(ByteBuffer buffer) {
        lock.lock();
        try {
            if (buffer == QuicStreamReader.EOF) {
                eof = true;
            } else {
                framesReader.add(buffer);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@return an {@code Http3Frame}, possibly {@linkplain PartialFrame partial},
     * or {@code null} if not enough bytes have been receive to decode (at least
     * partially) a frame}
     * If a frame is illegal or not allowed, a {@link MalformedFrame} is
     * returned. The caller is supposed to {@linkplain #clear() clear} all data
     * and proceed to close the connection in that case.
     */
    public Http3Frame poll() {
        lock.lock();
        try {
            if (partialFrame != null) {
                if (partialFrame.remaining() != 0) {
                    return partialFrame;
                } else partialFrame = null;
            }
            var frame = Http3Frame.decode(framesReader, this::isAllowed, debug);
            if (frame instanceof PartialFrame partial) {
                partialFrame = partial;
            }
            return frame;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@return the next payload bytes for the current partial frame,
     *          or {@code null} if no partial frame}
     * If EOF has been reached ({@link QuicStreamReader#EOF EOF} was
     * {@linkplain #submit(ByteBuffer) submitted}, and all buffers have
     * been read, the returned list will contain {@link QuicStreamReader#EOF
     * EOF}
     */
    public List<ByteBuffer> readPayloadBytes() {
        lock.lock();
        try {
            if (partialFrame == null || partialFrame.remaining() == 0) {
                partialFrame = null;
                return null;
            }
            if (eof && !framesReader.hasRemaining()) {
                return List.of(QuicStreamReader.EOF);
            }
            return partialFrame.nextPayloadBytes(framesReader);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@return true if EOF has been reached and all buffers have been read}
     */
    public boolean eof() {
        lock.lock();
        try {
            if (!eof) return false;
            if (!framesReader.hasRemaining()) return true;
            if (partialFrame != null) {
                // still some payload data to read...
                if (partialFrame.remaining() > 0) return false;
            }
            var pos = framesReader.position();
            try {
                // if there's not enough data to decode a new frame or a new
                // partial frame then since no more data will ever come, we do have
                // reached EOF. If however, we can read a frame from the remaining
                // data in the buffer, then EOF is not reached yet.
                // The next call to poll() will return that frame.
                var frame = Http3Frame.decode(framesReader, this::isAllowed, debug);
                return frame == null;
            } finally {
                // restore position for the next call to poll.
                framesReader.position(pos);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@return true if all buffers have been read}
     */
    public boolean clean() {
        lock.lock();
        try {
            if (partialFrame != null) {
                // still some payload data to read...
                if (partialFrame.remaining() > 0) return false;
            }
            return !framesReader.hasRemaining();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears any unconsumed buffers.
     */
    public void clear() {
        lock.lock();
        try {
            partialFrame = null;
            framesReader.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Can be overridden by subclasses to avoid parsing a frame
     * fully if the frame is not allowed on this stream, or
     * according to the stream state.
     *
     * @implSpec
     * This method delegates to the {@linkplain #FramesDecoder(String, LongPredicate)
     * predicate} given at construction time. If {@linkplain #FramesDecoder(String)
     * no predicate} was given this method returns true.
     *
     * @param frameType the frame type
     * @return true if the frame is allowed
     */
    protected boolean isAllowed(long frameType) {
        return isAllowed.test(frameType);
    }

    /**
     * A predicate that returns true for all frames types allowed
     * on the server->client control stream.
     * @param frameType a frame type
     * @return whether a frame of this type is allowed on a control stream.
     */
    public static boolean isAllowedOnControlStream(long frameType) {
        if (frameType == Http3FrameType.DATA.type()) return false;
        if (frameType == Http3FrameType.HEADERS.type()) return false;
        if (frameType == Http3FrameType.PUSH_PROMISE.type()) return false;
        if (frameType == Http3FrameType.MAX_PUSH_ID.type()) return false;
        if (Http3FrameType.isIllegalType(frameType)) return false;
        return true;
    }

    /**
     * A predicate that returns true for all frames types allowed
     * on the client->server control stream.
     * @param frameType a frame type
     * @return whether a frame of this type is allowed on a control stream.
     */
    public static boolean isAllowedOnClientControlStream(long frameType) {
        if (frameType == Http3FrameType.DATA.type()) return false;
        if (frameType == Http3FrameType.HEADERS.type()) return false;
        if (frameType == Http3FrameType.PUSH_PROMISE.type()) return false;
        if (Http3FrameType.isIllegalType(frameType)) return false;
        return true;
    }

    /**
     * A predicate that returns true for all frames types allowed
     * on a request/response stream.
     * @param frameType a frame type
     * @return whether a frame of this type is allowed on a request/response
     * stream.
     */
    public static boolean isAllowedOnRequestStream(long frameType) {
        if (frameType == Http3FrameType.SETTINGS.type()) return false;
        if (frameType == Http3FrameType.CANCEL_PUSH.type()) return false;
        if (frameType == Http3FrameType.GOAWAY.type()) return false;
        if (frameType == Http3FrameType.MAX_PUSH_ID.type()) return false;
        if (Http3FrameType.isIllegalType(frameType)) return false;
        return true;
    }


    /**
     * A predicate that returns true for all frames types allowed
     * on a push promise stream.
     * @param frameType a frame type
     * @return whether a frame of this type is allowed on a request/response
     * stream.
     */
    public static boolean isAllowedOnPromiseStream(long frameType) {
        if (frameType == Http3FrameType.SETTINGS.type()) return false;
        if (frameType == Http3FrameType.CANCEL_PUSH.type()) return false;
        if (frameType == Http3FrameType.GOAWAY.type()) return false;
        if (frameType == Http3FrameType.MAX_PUSH_ID.type()) return false;
        if (frameType == Http3FrameType.PUSH_PROMISE.type()) return false;
        if (Http3FrameType.isIllegalType(frameType)) return false;
        return true;
    }

    private static boolean allAllowed(long frameType) {
        return true;
    }
}
