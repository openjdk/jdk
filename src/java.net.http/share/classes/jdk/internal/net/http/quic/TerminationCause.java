/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.io.IOException;
import java.util.Objects;

import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import static jdk.internal.net.quic.QuicTransportErrors.NO_ERROR;

// TODO: document this
public abstract sealed class TerminationCause {
    private String logMsg;
    private String peerVisibleReason;
    private final long closeCode;
    private final Throwable originalCause;
    private final IOException reportedCause;

    private TerminationCause(final long closeCode, final Throwable closeCause) {
        this.closeCode = closeCode;
        this.originalCause = closeCause;
        if (closeCause != null) {
            this.logMsg = closeCause.toString();
        }
        this.reportedCause = toReportedCause(this.originalCause, this.logMsg);
    }

    private TerminationCause(final long closeCode, final String loggedAs) {
        this.closeCode = closeCode;
        this.originalCause = null;
        this.logMsg = loggedAs;
        this.reportedCause = toReportedCause(this.originalCause, this.logMsg);
    }

    public final long getCloseCode() {
        return this.closeCode;
    }

    public final IOException getCloseCause() {
        return this.reportedCause;
    }

    public final String getLogMsg() {
        return logMsg;
    }

    public final TerminationCause loggedAs(final String logMsg) {
        this.logMsg = logMsg;
        return this;
    }

    public final String getPeerVisibleReason() {
        return this.peerVisibleReason;
    }

    public final TerminationCause peerVisibleReason(final String reasonPhrase) {
        this.peerVisibleReason = reasonPhrase;
        return this;
    }

    public abstract boolean isAppLayer();

    public static TerminationCause forTransportError(final QuicTransportErrors err) {
        return new TransportError(err);
    }

    public static TerminationCause forTransportError(long errorCode, String loggedAs, long frameType) {
        return new TransportError(errorCode, loggedAs, frameType);
    }

    static SilentTermination forSilentTermination(final String loggedAs) {
        return new SilentTermination(loggedAs);
    }

    public static TerminationCause forException(final Throwable cause) {
        Objects.requireNonNull(cause);
        if (cause instanceof QuicTransportException qte) {
            return new TransportError(qte);
        }
        return new InternalError(cause);
    }

    // allows for higher (application) layer to inform the connection terminator
    // that the higher layer had completed a graceful shutdown of the connection
    // and the QUIC layer can now do an immediate close of the connection using
    // the {@code closeCode}
    public static TerminationCause appLayerClose(final long closeCode) {
        return new AppLayerClose(closeCode, (Throwable)null);
    }

    public static TerminationCause appLayerClose(final long closeCode, String loggedAs) {
        return new AppLayerClose(closeCode, loggedAs);
    }

    public static TerminationCause appLayerException(final long closeCode,
                                                     final Throwable cause) {
        return new AppLayerClose(closeCode, cause);
    }

    private static IOException toReportedCause(final Throwable original,
                                               final String fallbackExceptionMsg) {
        if (original == null) {
            return fallbackExceptionMsg == null
                    ? new IOException("connection terminated")
                    : new IOException(fallbackExceptionMsg);
        } else if (original instanceof QuicTransportException qte) {
            return new IOException(qte.getMessage());
        } else if (original instanceof IOException ioe) {
            return ioe;
        } else {
            return new IOException(original);
        }
    }


    static final class TransportError extends TerminationCause {
        final long frameType;
        final QuicTLSEngine.KeySpace keySpace;

        private TransportError(final QuicTransportErrors err) {
            super(err.code(), err.name());
            this.frameType = 0; // unknown frame type
            this.keySpace = null;
        }

        private TransportError(final QuicTransportException exception) {
            super(exception.getErrorCode(), exception);
            this.frameType = exception.getFrameType();
            this.keySpace = exception.getKeySpace();
            peerVisibleReason(exception.getReason());
        }

        public TransportError(long errorCode, String loggedAs, long frameType) {
            super(errorCode, loggedAs);
            this.frameType = frameType;
            keySpace = null;
        }

        @Override
        public boolean isAppLayer() {
            return false;
        }
    }

    static final class InternalError extends TerminationCause {

        private InternalError(final Throwable cause) {
            super(QuicTransportErrors.INTERNAL_ERROR.code(), cause);
        }

        @Override
        public boolean isAppLayer() {
            return false;
        }
    }

    static final class AppLayerClose extends TerminationCause {
        private AppLayerClose(final long closeCode, String loggedAs) {
            super(closeCode, loggedAs);
        }

        // TODO: allow optionally to specify "name" of the close code for app layer
        // like "H3_GENERAL_PROTOCOL_ERROR" (helpful in logging)
        private AppLayerClose(final long closeCode, final Throwable cause) {
            super(closeCode, cause);
        }

        @Override
        public boolean isAppLayer() {
            return true;
        }
    }

    static final class SilentTermination extends TerminationCause {

        private SilentTermination(final String loggedAs) {
            // the error code won't play any role, since silent termination
            // doesn't cause any packets to be generated or sent to the peer
            super(NO_ERROR.code(), loggedAs);
        }

        @Override
        public boolean isAppLayer() {
            return false; // doesn't play a role in context of silent termination
        }
    }
}
