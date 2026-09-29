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
package jdk.internal.net.http;

import jdk.internal.net.http.http3.frames.DataFrame;
import jdk.internal.net.http.http3.frames.HeadersFrame;
import jdk.internal.net.http.http3.frames.Http3Frame;
import jdk.internal.net.http.http3.frames.Http3FrameType;
import jdk.internal.net.http.http3.frames.MalformedFrame;
import jdk.internal.net.http.http3.frames.PushPromiseFrame;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.http3.frames.UnknownFrame;

/**
 * Verifies that when a HTTP3 frame arrives on a stream, then that particular frame type
 * is in the expected order as compared to the previous frame type that was received.
 * In effect, does what the RFC-9114, section 4.1 and section 6.2.1 specifies.
 * Note that the H3FrameOrderVerifier is only responsible for checking the order in which a
 * frame type is received on a stream. It isn't responsible for checking if that particular frame
 * type is expected to be received on a particular stream type.
 */
abstract class H3FrameOrderVerifier {
    long currentProcessingFrameType = -1; // -1 implies no frame being processed currently
    long lastCompletedFrameType = -1; // -1 implies no frame processing has completed yet

    /**
     * {@return a frame order verifier for HTTP3 request/response stream}
     */
    static H3FrameOrderVerifier newForRequestResponseStream() {
        return new ResponseStreamVerifier(false);
    }

    /**
     * {@return a frame order verifier for HTTP3 push promise stream}
     */
    static H3FrameOrderVerifier newForPushPromiseStream() {
        return new ResponseStreamVerifier(true);
    }

    /**
     * {@return a frame order verifier for HTTP3 control stream}
     */
    static H3FrameOrderVerifier newForControlStream() {
        return new ControlStreamVerifier();
    }

    /**
     * @param frame The frame that has been received
     *              {@return true if the {@code frameType} processing can start. false otherwise}
     */
    abstract boolean allowsProcessing(final Http3Frame frame);

    /**
     * Marks the receipt of complete content of a frame that was currently being processed
     *
     * @param frame The frame whose content was fully received
     * @throws IllegalStateException If the passed frame type wasn't being currently processed
     */
    void completed(final Http3Frame frame) {
        if (frame instanceof UnknownFrame) {
            return;
        }
        final long frameType = frame.type();
        if (currentProcessingFrameType != frameType) {
            throw new IllegalStateException("Unexpected completion of processing " +
                    "of frame type (" + frameType + "): "
                    + Http3FrameType.asString(frameType) + ", expected " +
                    Http3FrameType.asString(currentProcessingFrameType));
        }
        currentProcessingFrameType = -1;
        lastCompletedFrameType = frameType;
    }

    private static final class ControlStreamVerifier extends H3FrameOrderVerifier {

        @Override
        boolean allowsProcessing(final Http3Frame frame) {
            if (frame instanceof MalformedFrame) {
                // a malformed frame can come in any time, so we allow it to be processed
                // and we don't "track" it either
                return true;
            }
            if (frame instanceof UnknownFrame) {
                //  unknown frames can come in any time, we allow them to be processed
                // and we don't track their processing/completion. However, if an unknown frame
                // is the first frame on a control stream then that's an error and we return "false"
                // to prevent processing that frame.
                // RFC-9114, section 9, which states - "where a known frame type is required to be
                // in a specific location, such as the SETTINGS frame as the first frame of the
                // control stream, an unknown frame type does not satisfy that requirement and
                // SHOULD be treated as an error"
                return lastCompletedFrameType != -1;
            }
            final long frameType = frame.type();
            if (currentProcessingFrameType != -1) {
                // we are in the middle of processing a particular frame type and we
                // only expect additional frames of only that type
                return frameType == currentProcessingFrameType;
            }
            // we are not currently processing any frame
            if (lastCompletedFrameType == -1) {
                // there was no previous frame either, so this is the first frame to have been
                // received
                if (frameType != SettingsFrame.TYPE) {
                    // unexpected first frame type
                    return false;
                }
                currentProcessingFrameType = frameType;
                // expected first frame type
                return true;
            }
            // there's no specific ordering specified on control stream other than expecting
            // the SETTINGS frame to be the first received (which we have already verified before
            // reaching here)
            currentProcessingFrameType = frameType;
            return true;
        }
    }

    private static final class ResponseStreamVerifier extends H3FrameOrderVerifier {
        private boolean headerSeen;
        private boolean dataSeen;
        private boolean trailerCompleted;
        private final boolean pushStream;

        private ResponseStreamVerifier(boolean pushStream) {
            this.pushStream = pushStream;
        }

        @Override
        boolean allowsProcessing(final Http3Frame frame) {
            if (frame instanceof MalformedFrame) {
                // a malformed frame can come in any time, so we allow it to be processed
                // and we don't track their processing/completion
                return true;
            }
            if (frame instanceof UnknownFrame) {
                // unknown frames can come in any time, we allow them to be processed
                // and we don't track their processing/completion
                return true;
            }
            final long frameType = frame.type();
            if (currentProcessingFrameType != -1) {
                // we are in the middle of processing a particular frame type and we
                // only expect additional frames of only that type
                return frameType == currentProcessingFrameType;
            }
            if (frameType == DataFrame.TYPE) {
                if (!headerSeen || trailerCompleted) {
                    // DATA is not permitted before HEADERS or after trailer
                    return false;
                }
                dataSeen = true;
            } else if (frameType == HeadersFrame.TYPE) {
                if (trailerCompleted) {
                    // HEADERS is not permitted after trailer
                    return false;
                }
                headerSeen = true;
                if (dataSeen) {
                    trailerCompleted = true;
                }
            } else if (frameType == PushPromiseFrame.TYPE) {
                // a push promise is only permitted on a response,
                // and not on a push stream
                if (pushStream) {
                    return false;
                }
            } else {
                // no other frames permitted
                return false;
            }

            currentProcessingFrameType = frameType;
            return true;
        }
    }
}
