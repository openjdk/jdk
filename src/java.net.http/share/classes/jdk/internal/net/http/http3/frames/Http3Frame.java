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

import java.util.function.LongPredicate;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.BuffersReader;
import jdk.internal.net.http.quic.VariableLengthEncoder;

import static jdk.internal.net.http.http3.frames.Http3FrameType.DATA;
import static jdk.internal.net.http.http3.frames.Http3FrameType.HEADERS;
import static jdk.internal.net.http.http3.frames.Http3FrameType.PUSH_PROMISE;
import static jdk.internal.net.http.http3.frames.Http3FrameType.UNKNOWN;
import static jdk.internal.net.http.http3.frames.Http3FrameType.asString;
import static jdk.internal.net.http.http3.frames.Http3FrameType.isIllegalType;

/**
 * An HTTP/3 frame
 */
public sealed interface Http3Frame permits AbstractHttp3Frame {

    /**
     * {@return the type of this frame}
     */
    long type();

    /**
     * {@return the length of this frame}
     */
    long length();


    /**
     * {@return the portion of the frame payload that can be read
     *          after the frame was created, when the current frame
     *          can be read as a partial frame, otherwise 0, if
     *          the payload can't be streamed}
     */
    default long streamingLength() { return 0;}

    /**
     * Attempts to decode an HTTP/3 frame from the bytes accumulated
     * in the reader.
     *
     * @apiNote
     *
     * If an error is detected while parsing the frame, a {@link MalformedFrame}
     * error will be returned
     *
     * @param reader              the reader containing the bytes
     * @param isFrameTypeAllowed  a predicate to test whether a given
     *                            frame type is allowed in this context
     * @param debug               a logger to log debug traces
     * @return the decoded frame, or {@code null} if some bytes are
     *                            missing to decode the frame
     */
     static Http3Frame decode(BuffersReader reader, LongPredicate isFrameTypeAllowed, Logger debug) {
        long pos = reader.position();
        long limit = reader.limit();
        long remaining = reader.remaining();
        long type = -1;
        long before = reader.read();
        Http3Frame frame;
        try {
            int tsize = VariableLengthEncoder.peekEncodedValueSize(reader, pos);
            if (tsize == -1 || remaining - tsize < 0) return null;
            type = VariableLengthEncoder.peekEncodedValue(reader, pos);
            if (type == -1) return null;
            if (isIllegalType(type) || !isFrameTypeAllowed.test(type)) {
                var msg = "H3_FRAME_UNEXPECTED: Frame "
                        + asString(type)
                        + " is not allowed on this stream";
                if (debug.on()) debug.log(msg);
                frame = new MalformedFrame(type, Http3Error.H3_FRAME_UNEXPECTED.code(), msg);
                reader.clear();
                return frame;
            }

            int lsize = VariableLengthEncoder.peekEncodedValueSize(reader, pos + tsize);
            if (lsize == -1 || remaining - tsize - lsize < 0) return null;
            final long length = VariableLengthEncoder.peekEncodedValue(reader, pos + tsize);
            var frameType = Http3FrameType.forType(type);
            if (debug.on()) {
                debug.log("Decoding %s(length=%s)", frameType, length);
            }
            if (frameType == UNKNOWN) {
                if (debug.on()) {
                    debug.log("decode partial unknown frame: "
                                    + "pos:%s, limit:%s, remaining:%s," +
                                    " tsize:%s, lsize:%s, length:%s",
                            pos, limit, remaining, tsize, lsize, length);
                }
                reader.position(pos + tsize + lsize);
                reader.release();
                return new UnknownFrame(type, length);
            } else if (frameType.maxLength() < length) {
                var msg = "H3_FRAME_ERROR: Frame " + asString(type) + " length too long";
                if (debug.on()) debug.log(msg);
                frame = new MalformedFrame(type, Http3Error.H3_FRAME_ERROR.code(), msg);
                reader.clear();
                return frame;
            }

            if (frameType == HEADERS) {
                if (length == 0) {
                    var msg = "H3_FRAME_ERROR: Frame " + asString(type) + " does not contain headers";
                    if (debug.on()) debug.log(msg);
                    frame = new MalformedFrame(type, Http3Error.H3_FRAME_ERROR.code(), msg);
                    reader.clear();
                    return frame;
                }
                reader.position(pos + tsize + lsize);
                reader.release();
                return new HeadersFrame(length);
            }

            if (frameType == DATA) {
                reader.position(pos + tsize + lsize);
                reader.release();
                return new DataFrame(length);
            }

            if (frameType == PUSH_PROMISE) {
                int pidsize = VariableLengthEncoder.peekEncodedValueSize(reader, pos + tsize + lsize);
                if (length == 0 || length < pidsize) {
                    var msg = "H3_FRAME_ERROR: Frame " + asString(type) + " length too short to fit pushID";
                    if (debug.on()) debug.log(msg);
                    frame = new MalformedFrame(type, Http3Error.H3_FRAME_ERROR.code(), msg);
                    reader.clear();
                    return frame;
                }
                if (length == pidsize) {
                    var msg = "H3_FRAME_ERROR: Frame " + asString(type) + " does not contain headers";
                    if (debug.on()) debug.log(msg);
                    frame = new MalformedFrame(type, Http3Error.H3_FRAME_ERROR.code(), msg);
                    reader.clear();
                    return frame;
                }
                if (pidsize == -1 || remaining - tsize - lsize - pidsize < 0) return null;
                long pushId = VariableLengthEncoder.peekEncodedValue(reader, pos + tsize + lsize);
                reader.position(pos + tsize + lsize + pidsize);
                reader.release();
                return new PushPromiseFrame(pushId, length - pidsize);
            }

            if (length + tsize + lsize > reader.remaining()) {
                // we haven't moved the reader's position.
                // we'll be called back when new bytes are available and
                // we'll resume reading type + length from the same position
                // again, until we have enough to read the frame.
                return null;
            }

            assert isFrameTypeAllowed.test(type);

            frame = switch(frameType) {
                case SETTINGS ->     SettingsFrame.decodeFrame(reader, debug);
                case GOAWAY ->       GoAwayFrame.decodeFrame(reader, debug);
                case CANCEL_PUSH ->  CancelPushFrame.decodeFrame(reader, debug);
                case MAX_PUSH_ID ->  MaxPushIdFrame.decodeFrame(reader, debug);
                default -> {
                    reader.position(pos + tsize + lsize);
                    reader.release();
                    yield  new UnknownFrame(type, length);
                }
            };

            long read;
            if (frame instanceof MalformedFrame || frame == null) {
                return frame;
            } else if ((read = (reader.read() - before - tsize - lsize)) != length) {
                String msg = ("H3_FRAME_ERROR: Frame %s payload length does not match" +
                        " frame length (length=%s, payload=%s)")
                        .formatted(asString(type), length, read);
                if (debug.on()) debug.log(msg);
                reader.release(); // mark reader read
                reader.position(reader.position() + tsize + lsize + length);
                reader.release();
                return new MalformedFrame(type, Http3Error.H3_FRAME_ERROR.code(), msg);
            } else {
                return frame;
            }
        } catch (Throwable t) {
            if (debug.on()) debug.log("Failed to decode frame", t);
            reader.clear(); // mark reader read
            return new MalformedFrame(type, Http3Error.H3_INTERNAL_ERROR.code(), t.getMessage(), t);
        }
    }
}
