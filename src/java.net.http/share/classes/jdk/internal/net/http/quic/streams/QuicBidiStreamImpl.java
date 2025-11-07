/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.streams;

import java.io.IOException;

import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.quic.QuicConnectionImpl;

/**
 * An implementation of a bidirectional stream.
 * A bidirectional stream implements both {@link QuicSenderStream}
 * and {@link QuicReceiverStream}.
 */
public final class QuicBidiStreamImpl extends AbstractQuicStream implements QuicBidiStream {

    // The sender part of this bidirectional stream
    private final QuicSenderStreamImpl   senderPart;

    // The receiver part of this bidirectional stream
    private final QuicReceiverStreamImpl receiverPart;

    QuicBidiStreamImpl(QuicConnectionImpl connection, long streamId) {
        this(connection, streamId, new QuicSenderStreamImpl(connection, streamId),
                new QuicReceiverStreamImpl(connection, streamId));
    }

    private QuicBidiStreamImpl(QuicConnectionImpl connection, long streamId,
                       QuicSenderStreamImpl sender, QuicReceiverStreamImpl receiver) {
        super(connection, streamId);
        this.senderPart = sender;
        this.receiverPart = receiver;
        assert isBidirectional();
    }

    @Override
    public ReceivingStreamState receivingState() {
        return receiverPart.receivingState();
    }

    @Override
    public QuicStreamReader connectReader(SequentialScheduler scheduler) {
        return receiverPart.connectReader(scheduler);
    }

    @Override
    public void disconnectReader(QuicStreamReader reader) {
        receiverPart.disconnectReader(reader);
    }

    @Override
    public void requestStopSending(long errorCode) {
        receiverPart.requestStopSending(errorCode);
    }

    @Override
    public boolean isStopSendingRequested() {
        return receiverPart.isStopSendingRequested();
    }

    @Override
    public long dataReceived() {
        return receiverPart.dataReceived();
    }

    @Override
    public long maxStreamData() {
        return receiverPart.maxStreamData();
    }

    @Override
    public SendingStreamState sendingState() {
        return senderPart.sendingState();
    }

    @Override
    public QuicStreamWriter connectWriter(SequentialScheduler scheduler) {
        return senderPart.connectWriter(scheduler);
    }

    @Override
    public void disconnectWriter(QuicStreamWriter writer) {
        senderPart.disconnectWriter(writer);
    }

    @Override
    public void reset(long errorCode) throws IOException {
        senderPart.reset(errorCode);
    }

    @Override
    public long dataSent() {
        return senderPart.dataSent();
    }

    /**
     * {@return the sender part implementation of this bidirectional stream}
     */
    public QuicSenderStreamImpl senderPart() {
        return senderPart;
    }

    /**
     * {@return the receiver part implementation of this bidirectional stream}
     */
    public QuicReceiverStreamImpl receiverPart() {
        return receiverPart;
    }

    @Override
    public boolean isDone() {
        return receiverPart.isDone() && senderPart.isDone();
    }

    @Override
    public long rcvErrorCode() {
        return receiverPart.rcvErrorCode();
    }

    @Override
    public long sndErrorCode() {
        return senderPart.sndErrorCode();
    }

    @Override
    public boolean stopSendingReceived() {
        return senderPart.stopSendingReceived();
    }
}
