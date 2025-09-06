/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;

import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.quic.QuicTransportErrors;

/**
 * An {@code InputStream} which reads its data from a {@link BlockingQueue}
 */
final class QueueInputStream extends InputStream {
    private final BlockingQueue<ByteBuffer> incomingData;
    private final ByteBuffer eofIndicator;
    private final QuicStreamReader streamReader;
    // error needs volatile access as it is set by a different thread
    private volatile Throwable error;
    // current might not need volatile access as it should only
    // be read/set by the reading thread. However available()
    // might conceivably be called by multiple threads.
    private volatile ByteBuffer current;

    QueueInputStream(final BlockingQueue<ByteBuffer> incomingData,
                     final ByteBuffer eofIndicator,
                     QuicStreamReader streamReader) {
        this.incomingData = incomingData;
        this.eofIndicator = eofIndicator;
        this.streamReader = streamReader;
    }

    private ByteBuffer current() throws InterruptedException {
        ByteBuffer current = this.current;
        // if eof, there should no more byte buffer
        if (current == eofIndicator) return eofIndicator;
        if (current == null || !current.hasRemaining()) {
            return (current = this.current = incomingData.take());
        }
        return current;
    }

    private boolean eof() {
        ByteBuffer current = this.current;
        return current == eofIndicator;
    }

    @Override
    public int read() throws IOException {
        final byte[] data = new byte[1];
        final int numRead = this.read(data, 0, data.length);
        // can't be 0, since we block till we receive at least 1 byte of data
        assert numRead != 0 : "No data read";
        if (numRead == -1) {
            return -1;
        }
        return data[0];
    }

    // concurrent calls to read() should not and are not supported
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        int totalRead = 0;
        while (totalRead < len) {
            ByteBuffer bb = null;
            checkError();
            try {
                bb = current();
            } catch (InterruptedException e) {
                streamReader.stream().requestStopSending(QuicTransportErrors.NO_ERROR.code());
                // TODO: should close here
                error(e);
                Thread.currentThread().interrupt();
                throw toIOException(e);
            }
            if (bb == eofIndicator) {
                return totalRead == 0 ? -1 : totalRead;
            }
            final int available = bb.remaining();
            if (available > 0) {
                final int numToTransfer = Math.min(available, (len - totalRead));
                bb.get(b, off + totalRead, numToTransfer);
                totalRead += numToTransfer;
            }
            // if more data is available, take more, else if we have read at least 1 byte
            // then return back
            if (totalRead > 0 && incomingData.peek() == null) {
                return totalRead;
            }
        }
        if (totalRead > 0) return totalRead;
        // if we reach here then len must be 0
        checkError();
        assert len == 0;
        return eof() ? -1 : 0;
    }

    @Override
    public int available() throws IOException {
        var bb = current;
        if (bb == null || !bb.hasRemaining()) bb = incomingData.peek();
        if (bb == null || bb == eofIndicator) return 0;
        return bb.remaining();
    }

    // we only check for errors after the incoming data queue
    // has been emptied - except for interrupt.
    private void checkError() throws IOException {
        var error = this.error;
        if (error == null) return;
        if (error instanceof InterruptedException)
            throw new IOException("closed by interrupt");
        var bb = current;
        if (bb == eofIndicator || (bb != null && bb.hasRemaining())) return;
        // we create a new exception to have the caller in the
        // stack trace.
        if (incomingData.isEmpty()) throw toIOException(error);
    }

    // called if an error comes from upstream
    void error(Throwable error) {
        boolean firstError = false;
        // only keep the first error
        synchronized (this) {
            var e = this.error;
            if (e == null) {
                e = this.error = error;
                firstError = true;
            }
        }
        // unblock read if needed
        if (firstError) {
            incomingData.add(ByteBuffer.allocate(0));
        }
    }

    static IOException toIOException(Throwable error) {
        return new IOException(error);
    }
}
