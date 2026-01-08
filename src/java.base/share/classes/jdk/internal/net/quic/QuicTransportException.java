/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.quic;

/**
 * Exception that wraps QUIC transport error codes.
 * Thrown in response to packets or frames that violate QUIC protocol.
 * This is a fatal exception; connection is always closed when this exception is caught.
 *
 * <p>For a list of errors see:
 * https://www.rfc-editor.org/rfc/rfc9000.html#name-transport-error-codes
 */
public final class QuicTransportException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 5259674758792412464L;

    private final QuicTLSEngine.KeySpace keySpace;
    private final long frameType;
    private final long errorCode;

    /**
     * Constructs a new {@code QuicTransportException}.
     *
     * @param reason    the reason why the exception occurred
     * @param keySpace  the key space in which the frame appeared.
     *                  May be {@code null}, for instance, in
     *                  case of {@link QuicTransportErrors#INTERNAL_ERROR}.
     * @param frameType the frame type of the frame whose parsing / handling
     *                  caused the error.
     *                  May be 0 if not related to any specific frame.
     * @param errorCode a quic transport error
     */
    public QuicTransportException(String reason, QuicTLSEngine.KeySpace keySpace,
                                  long frameType, QuicTransportErrors errorCode) {
        super(reason);
        this.keySpace = keySpace;
        this.frameType = frameType;
        this.errorCode = errorCode.code();
    }

    /**
     * Constructs a new {@code QuicTransportException}. For use with TLS alerts.
     *
     * @param reason    the reason why the exception occurred
     * @param keySpace  the key space in which the frame appeared.
     *                  May be {@code null}, for instance, in
     *                  case of {@link QuicTransportErrors#INTERNAL_ERROR}.
     * @param frameType the frame type of the frame whose parsing / handling
     *                  caused the error.
     *                  May be 0 if not related to any specific frame.
     * @param errorCode a quic transport error code
     * @param cause     the cause
     */
    public QuicTransportException(String reason, QuicTLSEngine.KeySpace keySpace,
                                  long frameType, long errorCode, Throwable cause) {
        super(reason, cause);
        this.keySpace = keySpace;
        this.frameType = frameType;
        this.errorCode = errorCode;
    }

    /**
     * {@return the reason to include in the {@code ConnectionCloseFrame}}
     */
    public String getReason() {
        return getMessage();
    }

    /**
     * {@return the key space for which the error occurred, or {@code null}}
     */
    public QuicTLSEngine.KeySpace getKeySpace() {
        return keySpace;
    }

    /**
     * {@return the frame type for which the error occurred, or 0}
     */
    public long getFrameType() {
        return frameType;
    }

    /**
     * {@return the transport error that occurred}
     */
    public long getErrorCode() {
        return errorCode;
    }
}
