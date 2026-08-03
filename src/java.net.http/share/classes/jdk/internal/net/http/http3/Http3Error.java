/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.http3;

import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

import jdk.internal.net.quic.QuicTransportErrors;

/**
 * This enum models HTTP/3 error codes as specified in
 * <a href="https://www.rfc-editor.org/rfc/rfc9114.html#name-http-3-error-codes">RFC 9114, Section 8</a>,
 * augmented with QPack error codes as specified in
 * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-6">RFC 9204, Section 6</a>.
 */
public enum Http3Error {

    /**
     * No error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * This is used when the connection or stream
     * needs to be closed, but there is no error to signal.
     * }</pre></blockquote>
     */
    H3_NO_ERROR (0x0100), // 256

    /**
     * General protocol error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * Peer violated protocol requirements in a way that does
     * not match a more specific error code, or endpoint declines
     * to use the more specific error code.
     * }</pre></blockquote>
     */
    H3_GENERAL_PROTOCOL_ERROR (0x0101), // 257

    /**
     * Internal error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * An internal error has occurred in the HTTP stack.
     * }</pre></blockquote>
     */
    H3_INTERNAL_ERROR (0x0102), // 258

    /**
     * Stream creation error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * The endpoint detected that its peer created a stream that
     * it will not accept.
     * }</pre></blockquote>
     */
    H3_STREAM_CREATION_ERROR (0x0103), // 259

    /**
     * Critical stream closed error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * A stream required by the HTTP/3 connection was closed or reset.
     * }</pre></blockquote>
     */
    H3_CLOSED_CRITICAL_STREAM (0x0104), // 260

    /**
     * Frame unexpected error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * A frame was received that was not permitted in the
     * current state or on the current stream.
     * }</pre></blockquote>
     */
    H3_FRAME_UNEXPECTED (0x0105), // 261

    /**
     * Frame error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * A frame that fails to satisfy layout requirements or with
     * an invalid size was received.
     * }</pre></blockquote>
     */
    H3_FRAME_ERROR (0x0106), // 262

    /**
     * Excessive load error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * The endpoint detected that its peer is exhibiting a behavior
     * that might be generating excessive load.
     * }</pre></blockquote>
     */
    H3_EXCESSIVE_LOAD (0x0107), // 263

    /**
     * Stream ID or Push ID error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * A Stream ID or Push ID was used incorrectly, such as exceeding
     * a limit, reducing a limit, or being reused.
     * }</pre></blockquote>
     */
    H3_ID_ERROR (0x0108), // 264

    /**
     * Settings error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * An endpoint detected an error in the payload of a SETTINGS frame.
     * }</pre></blockquote>
     */
    H3_SETTINGS_ERROR (0x0109), // 265

    /**
     * Missing settings error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * No SETTINGS frame was received at the beginning of the control
     * stream.
     * }</pre></blockquote>
     */
    H3_MISSING_SETTINGS (0x010a), // 266

    /**
     * Request rejected error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * A server rejected a request without performing any application
     * processing.
     * }</pre></blockquote>
     */
    H3_REQUEST_REJECTED (0x010b), // 267

    /**
     * Request cancelled error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * The request or its response (including pushed response) is
     * cancelled.
     * }</pre></blockquote>
     */
    H3_REQUEST_CANCELLED (0x010c), // 268

    /**
     * Request incomplete error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * The client's stream terminated without containing a
     * fully-formed request.
     * }</pre></blockquote>
     */
    H3_REQUEST_INCOMPLETE (0x010d), //269

    /**
     * Message error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * An HTTP message was malformed and cannot be processed.
     * }</pre></blockquote>
     */
    H3_MESSAGE_ERROR (0x010e), // 270

    /**
     * Connect error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * The TCP connection established in response to a CONNECT
     * request was reset or abnormally closed.
     * }</pre></blockquote>
     */
    H3_CONNECT_ERROR (0x010f), // 271

    /**
     * Version fallback error
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#section-8.1">
     *     RFC 9114, Section 8.1</a>:
     * <blockquote><pre>{@code
     * The requested operation cannot be served over HTTP/3.
     * The peer should retry over HTTP/1.1.
     * }</pre></blockquote>
     */
    H3_VERSION_FALLBACK (0x0110), // 272

    /**
     * QPack decompression error
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-6">
     *     RFC 9204, Section 6</a>:
     * <blockquote><pre>{@code
     * The decoder failed to interpret an encoded field section
     * and is not able to continue decoding that field section.
     * }</pre></blockquote>
     */
    QPACK_DECOMPRESSION_FAILED (0x0200), // 512

    /**
     * Qpack encoder stream error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-6">
     *     RFC 9204, Section 6</a>:
     * <blockquote><pre>{@code
     * The decoder failed to interpret an encoder instruction
     * received on the encoder stream.
     * }</pre></blockquote>
     */
    QPACK_ENCODER_STREAM_ERROR (0x0201), // 513

    /**
     * Qpack decoder stream error
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-6">
     *     RFC 9204, Section 6</a>:
     * <blockquote><pre>{@code
     * The encoder failed to interpret a decoder instruction
     * received on the decoder stream.
     * }</pre></blockquote>
     */
    QPACK_DECODER_STREAM_ERROR (0x0202); // 514

    final long errorCode;
    Http3Error(long errorCode) {
        this.errorCode = errorCode;
    }

    public long code() {
        return errorCode;
    }

    public static Optional<Http3Error> fromCode(long code) {
        return Stream.of(values()).filter((v) -> v.code() == code)
                .findFirst();
    }

    public static String stringForCode(long code) {
        return fromCode(code).map(Http3Error::name).orElse(unknown(code));
    }

    private static String unknown(long code) {
        return "UnknownError(code=0x" + HexFormat.of().withUpperCase().toHexDigits(code) + ")";
    }

    /**
     * {@return true if the given code is {@link Http3Error#H3_NO_ERROR} or equivalent}
     * Unknown error codes are treated as equivalent to {@code H3_NO_ERROR}
     * @param code an HTTP/3 code error code
     */
    public static boolean isNoError(long code) {
        return fromCode(code).orElse(H3_NO_ERROR) == Http3Error.H3_NO_ERROR;
    }
}
