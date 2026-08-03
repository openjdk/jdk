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
import jdk.internal.net.http.quic.BuffersReader;

import jdk.internal.net.http.http3.Http3Error;

/**
 * An instance of MalformedFrame can be returned by
 * {@link AbstractHttp3Frame#decode(BuffersReader, LongPredicate, Logger)}
 * when a malformed frame is detected. This should cause the caller
 * to send an error to its peer, and possibly throw an
 * exception to the higher layer.
 */
public class MalformedFrame extends AbstractHttp3Frame {

    private final long errorCode;
    private final String msg;
    private final Throwable cause;

    /**
     * Creates Connection Error malformed frame
     *
     * @param errorCode - error code
     * @param msg       - internal debug message
     */
    public MalformedFrame(long type, long errorCode, String msg) {
        this(type, errorCode, msg, null);
    }

    /**
     * Creates Connection Error malformed frame
     *
     * @param errorCode - error code
     * @param msg       - internal debug message
     * @param cause     - internal cause for the error, if available
     *                 (can be null)
     */
    public MalformedFrame(long type, long errorCode, String msg, Throwable cause) {
        super(type);
        this.errorCode = errorCode;
        this.msg = msg;
        this.cause = cause;
    }

    @Override
    public String toString() {
        return super.toString() + " MalformedFrame, Error: "
                + Http3Error.stringForCode(errorCode)
                + " reason: " + msg;
    }

    /**
     * {@inheritDoc}
     * @implSpec this method always returns 0
     */
    @Override
    public long length() {
        return 0; // Not Applicable
    }

    /**
     * {@inheritDoc}
     * @implSpec this method always returns 0
     */
    @Override
    public long size() {
        return 0; // Not applicable
    }

    /**
     * {@return the {@linkplain Http3Error#code() HTTP/3 error code} that
     * should be reported to the peer}
     */
    public long getErrorCode() {
        return errorCode;
    }

    /**
     * {@return a message that describe the error}
     */
    public String getMessage() {
        return msg;
    }

    /**
     * {@return the cause of the error, if available, {@code null} otherwise}
     *
     * @apiNote
     * This is useful for logging and diagnosis purpose, typically when the
     * error is an {@linkplain Http3Error#H3_INTERNAL_ERROR internal error}.
     */
    public Throwable getCause() {
        return cause;
    }

}
