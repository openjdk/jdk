/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.qpack;

import jdk.internal.net.http.http3.Http3Error;

/**
 * Represents a QPack related failure as a failure cause and
 * an HTTP/3 error code.
 */
public final class QPackException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 8443631555257118370L;

    private final boolean isConnectionError;
    private final Http3Error http3Error;

    public QPackException(Http3Error http3Error, Throwable cause, boolean isConnectionError) {
        super(cause);
        this.isConnectionError = isConnectionError;
        this.http3Error = http3Error;
    }

    public static QPackException encoderStreamError(Throwable cause) {
        throw new QPackException(Http3Error.QPACK_ENCODER_STREAM_ERROR, cause, true);
    }

    public static QPackException decoderStreamError(Throwable cause) {
        throw new QPackException(Http3Error.QPACK_DECODER_STREAM_ERROR, cause, true);
    }

    public static QPackException decompressionFailed(Throwable cause, boolean isConnectionError) {
        throw new QPackException(Http3Error.QPACK_DECOMPRESSION_FAILED, cause, isConnectionError);
    }


    public Http3Error http3Error() {
        return http3Error;
    }

    public boolean isConnectionError() {
        return isConnectionError;
    }
}
