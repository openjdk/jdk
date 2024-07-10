/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.http3.streams;

import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.quic.QuicTransportErrors;

public final class Http3Streams {
    public static final int CONTROL_STREAM_CODE = 0x00;
    public static final int PUSH_STREAM_CODE = 0x01;
    public static final int QPACK_ENCODER_STREAM_CODE = 0x02;
    public static final int QPACK_DECODER_STREAM_CODE = 0x03;

    private Http3Streams() { throw new InternalError(); }

    public enum StreamType {
        CONTROL(CONTROL_STREAM_CODE),
        PUSH(PUSH_STREAM_CODE),
        QPACK_ENCODER(QPACK_ENCODER_STREAM_CODE),
        QPACK_DECODER(QPACK_DECODER_STREAM_CODE);
        final int code;
        StreamType(int code) {
            this.code = code;
        }
        public final int code() {
            return code;
        }
        public static Optional<StreamType> ofCode(int code) {
            return EnumSet.allOf(StreamType.class).stream()
                    .filter(s -> s.code() == code)
                    .findFirst();
        }
    }

    /**
     * {@return true if the given stream has errors}
     * @apiNote {@link QuicStream#hasError()} will return true
     *          if the error code is {@code HTTP3_NO_ERROR}, since
     *          the Quic layer doesn't know about the semantic of
     *          HTTP/3 code. This method will return fixes that by
     *          returning false if the error is HTTP3_NO_ERROR.
     * @param stream a stream that may have errors
     */
    public static boolean hasError(QuicStream stream) {
        if (stream == null || !stream.hasError()) return false;
        long rcvErrorCode = -1;
        if (stream instanceof QuicReceiverStream rcv) {
            rcvErrorCode = rcv.rcvErrorCode();
            if (rcvErrorCode > 0) {
                return rcvErrorCode != Http3Error.H3_NO_ERROR.code();
            }
        }
        long sndErrorCode = -1;
        if (stream instanceof QuicSenderStream snd) {
            sndErrorCode = snd.sndErrorCode();
            if (sndErrorCode > 0) {
                return sndErrorCode != Http3Error.H3_NO_ERROR.code();
            }
        }
        assert  sndErrorCode <= 0 && rcvErrorCode <= 0;
        return false;
    }

    public static boolean hasSndError(QuicSenderStream stream) {
        long sndErrorCode = stream.sndErrorCode();
        if (sndErrorCode > 0) {
            return sndErrorCode != Http3Error.H3_NO_ERROR.code();
        }
        return false;
    }

    public static boolean hasRcvError(QuicReceiverStream stream) {
        long rcvErrorCode = stream.rcvErrorCode();
        if (rcvErrorCode > 0) {
            return rcvErrorCode != Http3Error.H3_NO_ERROR.code();
        }
        return false;
    }

    /**
     * {@return an optional string that represents the error state of the
     *          stream, or {@code Optional.empty()} if no error code
     *          has been received or sent}
     * @param stream a quic stream that may have errors
     */
    public static Optional<String> errorCodeAsString(QuicStream stream) {
        long sndErrorCode = -1;
        long rcvErrorCode = -1;
        if (stream instanceof QuicReceiverStream rcv) {
            rcvErrorCode = rcv.rcvErrorCode();
        }
        if (stream instanceof QuicSenderStream snd) {
            sndErrorCode = snd.sndErrorCode();
        }
        if (rcvErrorCode >= 0 || sndErrorCode >= 0) {
            Stream<String> rcv = rcvErrorCode >= 0
                    ? Stream.of("RCV: " + Http3Error.http3OrQuic(rcvErrorCode))
                    : Stream.empty();
            Stream<String> snd = sndErrorCode >= 0
                    ? Stream.of("SND: " + Http3Error.http3OrQuic(sndErrorCode))
                    : Stream.empty();
           return Optional.of(Stream.concat(rcv, snd)
                   .collect(Collectors.joining(",", "errorCode(", ")" )));
        }
        return Optional.empty();
    }

    /**
     * If the stream {@linkplain #hasError(QuicStream) has errors}, prints a message
     * recording the {@linkplain #errorCodeAsString(QuicStream) error state} of the
     * stream through the given logger. The message is of the form:
     * {@code <name> <stream-id>: <error-state>}.
     * If the given {@code name} is null or empty, {@code "Stream"} is substituted
     * to {@code <name>}.
     * @param logger the logger to log through
     * @param stream a quic stream that may have errors
     * @param name a name for the stream, e.g {@code "Control stream"}, or {@code null}.
     */
    public static void debugErrorCode(Logger logger, QuicStream stream, String name) {
        if (logger.on()) {
            var errorCodeStr = errorCodeAsString(stream);
            if (errorCodeStr.isPresent()) {
                var what = (name == null || name.isEmpty()) ? "Stream" : name;
                logger.log("%s %s: %s", what, stream.streamId(), errorCodeStr.get());
            }
        }
    }

    public static boolean isReserved(long streamType) {
        return streamType % 31 == 2 && streamType > 31;
    }
}
