/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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


import java.io.IOException;
import java.net.ProtocolException;
import java.util.Objects;

import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.frame.ErrorFrame;

/**
 * Termination cause for an {@linkplain Http2Connection HTTP/2 connection}
 */
public abstract sealed class Http2TerminationCause {
    private String logMsg;
    private String peerVisibleReason;
    private final int closeCode;
    private final Throwable originalCause;
    private final IOException reportedCause;

    private Http2TerminationCause(final int closeCode, final Throwable closeCause) {
        this.closeCode = closeCode;
        this.originalCause = closeCause;
        if (closeCause != null) {
            this.logMsg = closeCause.toString();
        }
        this.reportedCause = toReportedCause(this.originalCause, this.logMsg);
    }

    private Http2TerminationCause(final int closeCode, final String loggedAs) {
        this.closeCode = closeCode;
        this.originalCause = null;
        this.logMsg = loggedAs;
        this.reportedCause = toReportedCause(null, this.logMsg);
    }

    /**
     * Returns the error code (specified for HTTP/2 ErrorFrame) that caused the
     * connection termination.
     */
    public final int getCloseCode() {
        return this.closeCode;
    }

    /**
     * Returns the {@link IOException} that is considered the cause of the connection termination.
     * Even a {@linkplain #isAbnormalClose() normal} termination will have
     * an {@code IOException} associated with it, so this method will always return a non-null instance.
     */
    public final IOException getCloseCause() {
        return this.reportedCause;
    }

    /**
     * Returns {@code true} if the connection was terminated due to some exception. {@code false}
     * otherwise.
     * A normal connection termination (for example, the connection idle timing out locally)
     * is not considered as an abnormal termination and this method returns {@code false} for
     * such cases.
     */
    public abstract boolean isAbnormalClose();

    /**
     * Returns the connection termination cause, represented as a string. Unlike the
     * {@linkplain #getPeerVisibleReason() peer-visible reason}, this log message will not be
     * sent across to the peer and it is thus allowed to include additional details that might
     * help debugging a connection termination.
     */
    public final String getLogMsg() {
        return logMsg;
    }

    /**
     * Returns the connection termination cause, represented as a string. This represents the
     * "debugData" that is sent to the peer in a
     * {@linkplain  jdk.internal.net.http.frame.GoAwayFrame GOAWAY frame}.
     */
    public final String getPeerVisibleReason() {
        return this.peerVisibleReason;
    }

    /**
     * Sets the connection termination cause, represented as a string, which will be sent
     * to the peer in a {@linkplain  jdk.internal.net.http.frame.GoAwayFrame GOAWAY frame}.
     * Unlike the {@link #getLogMsg() log message},
     * it is expected that this peer-visible reason will not contain anything that is not meant
     * to be viewed by the peer.
     */
    protected final void setPeerVisibleReason(final String reasonPhrase) {
        this.peerVisibleReason = reasonPhrase;
    }

    /**
     * Returns a connection termination cause that represents an
     * {@linkplain #isAbnormalClose() abnormal} termination due to the given {@code cause}.
     *
     * @param cause the termination cause, cannot be null.
     */
    public static Http2TerminationCause forException(final Throwable cause) {
        Objects.requireNonNull(cause);
        if (cause instanceof ProtocolException pe) {
            return new ProtocolError(pe);
        }
        return new InternalError(cause);
    }

    /**
     * Returns a connection termination cause that represents a
     * {@linkplain #isAbnormalClose() normal} termination.
     */
    public static Http2TerminationCause noErrorTermination() {
        return NoError.INSTANCE;
    }

    /**
     * Returns a connection termination cause that represents a
     * {@linkplain #isAbnormalClose() normal} termination due to the connection
     * being idle.
     */
    public static Http2TerminationCause idleTimedOut() {
        return NoError.IDLE_TIMED_OUT;
    }

    /**
     * Returns a connection termination cause that represents an
     * {@linkplain #isAbnormalClose() abnormal} termination due to the given {@code errorCode}.
     * Although this method does no checks for the {@code errorCode}, it is expected to be one
     * of the error codes specified by the HTTP/2 RFC for the ErrorFrame.
     *
     * @param errorCode the error code
     * @param loggedAs  optional log message to be associated with this termination cause
     */
    public static Http2TerminationCause forH2Error(final int errorCode, final String loggedAs) {
        if (errorCode == ErrorFrame.PROTOCOL_ERROR) {
            return new ProtocolError(loggedAs);
        } else if (errorCode == ErrorFrame.FLOW_CONTROL_ERROR) {
            // we treat flow control error as a protocol error currently
            return new ProtocolError(loggedAs, true);
        }
        return new H2StandardError(errorCode, loggedAs);
    }

    private static IOException toReportedCause(final Throwable original,
                                               final String fallbackExceptionMsg) {
        if (original == null) {
            return fallbackExceptionMsg == null
                    ? new IOException("connection terminated")
                    : new IOException(fallbackExceptionMsg);
        } else if (original instanceof IOException ioe) {
            return ioe;
        } else {
            return Utils.toIOException(original);
        }
    }

    private static final class NoError extends Http2TerminationCause {
        private static final IOException NO_ERROR_MARKER =
                new IOException("HTTP/2 connection closed normally - no error");
        private static final IOException NO_ERROR_IDLE_TIMED_OUT_MARKER =
                new IOException("HTTP/2 connection idle timed out - no error");

        static {
            // remove the stacktrace from the marker exception instances
            NO_ERROR_MARKER.setStackTrace(new StackTraceElement[0]);
            NO_ERROR_IDLE_TIMED_OUT_MARKER.setStackTrace(new StackTraceElement[0]);
        }

        private static final NoError INSTANCE = new NoError(false);
        private static final NoError IDLE_TIMED_OUT = new NoError(true);

        private final boolean idleTimedOut;

        private NoError(final boolean idleTimedOut) {
            super(ErrorFrame.NO_ERROR,
                    idleTimedOut ? NO_ERROR_IDLE_TIMED_OUT_MARKER : NO_ERROR_MARKER);
            this.idleTimedOut = idleTimedOut;
            setPeerVisibleReason(idleTimedOut ? "idle timed out" : "no error");
        }

        @Override
        public boolean isAbnormalClose() {
            return false;
        }

        @Override
        public String toString() {
            return this.idleTimedOut
                    ? "No error - idle timed out"
                    : "No error - normal termination";
        }
    }

    private static sealed class H2StandardError extends Http2TerminationCause {
        private H2StandardError(final int errCode, final String msg) {
            super(errCode, msg);
            setPeerVisibleReason(ErrorFrame.stringForCode(errCode));
        }

        private H2StandardError(final int errCode, final Throwable cause) {
            super(errCode, cause);
            setPeerVisibleReason(ErrorFrame.stringForCode(errCode));
        }

        @Override
        public boolean isAbnormalClose() {
            return getCloseCode() != ErrorFrame.NO_ERROR;
        }

        @Override
        public String toString() {
            return ErrorFrame.stringForCode(this.getCloseCode());
        }
    }

    private static final class ProtocolError extends H2StandardError {
        private ProtocolError(final String msg) {
            this(msg, false);
        }

        private ProtocolError(final String msg, final boolean flowControlError) {
            super(flowControlError
                            ? ErrorFrame.FLOW_CONTROL_ERROR
                            : ErrorFrame.PROTOCOL_ERROR,
                    new ProtocolException(msg));
        }

        private ProtocolError(final ProtocolException pe) {
            super(ErrorFrame.PROTOCOL_ERROR, pe);
        }

        @Override
        public boolean isAbnormalClose() {
            return true;
        }

        @Override
        public String toString() {
            return "Protocol error - " + this.getLogMsg();
        }
    }

    private static final class InternalError extends Http2TerminationCause {
        private InternalError(final Throwable cause) {
            super(ErrorFrame.INTERNAL_ERROR, cause);
        }

        @Override
        public boolean isAbnormalClose() {
            return true;
        }

        @Override
        public String toString() {
            return "Internal error - " + this.getLogMsg();
        }
    }
}
