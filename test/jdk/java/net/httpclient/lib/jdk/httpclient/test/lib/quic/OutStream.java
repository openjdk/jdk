/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.httpclient.test.lib.quic;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Semaphore;

import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;

/**
 * An {@link OutputStream} which writes using a {@link QuicStreamWriter}
 */
final class OutStream extends OutputStream {

    private final QuicStreamWriter quicStreamWriter;
    private final Semaphore writeSemaphore;

    OutStream(final QuicStreamWriter quicStreamWriter, Semaphore writeSemaphore) {
        Objects.requireNonNull(quicStreamWriter);
        this.quicStreamWriter = quicStreamWriter;
        this.writeSemaphore = Objects.requireNonNull(writeSemaphore);
    }

    @Override
    public void write(final int b) throws IOException {
        this.write(new byte[]{(byte) (b & 0xff)});
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        while (quicStreamWriter.credit() < 0
                && !quicStreamWriter.stopSendingReceived()) {
            try {
                writeSemaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        // the data will be queued and won't be written immediately, and therefore
        // it needs to be copied.
        final ByteBuffer data = ByteBuffer.wrap(b.clone(), off, len);
        quicStreamWriter.scheduleForWriting(data, false);
    }

    @Override
    public void close() throws IOException {
        quicStreamWriter.scheduleForWriting(QuicStreamReader.EOF, true);
        super.close();
    }
}
