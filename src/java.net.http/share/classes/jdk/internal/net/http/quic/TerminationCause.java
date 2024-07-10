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
package jdk.internal.net.http.quic;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLHandshakeException;

import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;

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

    public final long getCloseCode() {
        return this.closeCode;
    }

    final Optional<Throwable> getOriginalCause() {
        return Optional.ofNullable(this.originalCause);
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
        return new AppLayerClose(closeCode, null);
    }

    public static TerminationCause appLayerException(final long closeCode,
                                                     final Throwable cause) {
        return new AppLayerClose(closeCode, cause);
    }

    private static IOException toReportedCause(final Throwable original,
                                               final String fallbackExceptionMsg) {
        if (original == null) {
            if (fallbackExceptionMsg != null) {
                return new IOException(fallbackExceptionMsg);
            }
            return new ClosedChannelException();
        } else if (original instanceof QuicTransportException qte) {
            return new IOException(qte.getMessage());
        } else if (original instanceof SSLHandshakeException) {
            return new SSLHandshakeException(original.getMessage(), original);
        } else if (original instanceof ConnectException) {
            return (ConnectException) new ConnectException(original.getMessage())
                    .initCause(original);
        } else if (original.getClass() == IOException.class) {
            return new IOException(original.getMessage(), original);
        } else {
            return new IOException(original);
        }
    }


    static final class TransportError extends TerminationCause {
        final long frameType;
        final QuicTLSEngine.KeySpace keySpace;

        private TransportError(final QuicTransportErrors err) {
            super(err.code(), null);
            this.frameType = 0; // unknown frame type
            this.keySpace = null;
            loggedAs(err.name());
        }

        private TransportError(final QuicTransportException exception) {
            super(exception.getErrorCode(), exception);
            this.frameType = exception.getFrameType();
            this.keySpace = exception.getKeySpace();
            peerVisibleReason(exception.getReason());
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
}
