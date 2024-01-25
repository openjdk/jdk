/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package sun.nio.ch;

import jdk.internal.event.SocketReadEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.IntSupplier;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * An InputStream that reads bytes from a socket channel.
 */
class SocketInputStream extends InputStream {
    private final SocketChannelImpl sc;
    private final IntSupplier timeoutSupplier;

    /**
     * Initialize a SocketInputStream that reads from the given socket channel.
     * @param sc the socket channel
     * @param timeoutSupplier supplies the read timeout, in milliseconds
     */
    SocketInputStream(SocketChannelImpl sc, IntSupplier timeoutSupplier) {
        this.sc = sc;
        this.timeoutSupplier = timeoutSupplier;
    }

    /**
     * Initialize a SocketInputStream that reads from the given socket channel.
     */
    SocketInputStream(SocketChannelImpl sc) {
        this(sc, () -> 0);
    }

    @Override
    public int read() throws IOException {
        byte[] a = new byte[1];
        int n = read(a, 0, 1);
        return (n > 0) ? (a[0] & 0xff) : -1;
    }

    private int implRead(byte[] b, int off, int len, int timeout) throws IOException {
        if (timeout > 0) {
            long nanos = MILLISECONDS.toNanos(timeout);
            return sc.blockingRead(b, off, len, nanos);
        } else {
            return sc.blockingRead(b, off, len, 0);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int timeout = timeoutSupplier.getAsInt();
        if (!SocketReadEvent.enabled()) {
            return implRead(b, off, len, timeout);
        }
        long start = SocketReadEvent.timestamp();
        int n = implRead(b, off, len, timeout);
        SocketReadEvent.offer(start, n, sc.remoteAddress(), timeout);
        return n;
    }

    @Override
    public int available() throws IOException {
        return sc.available();
    }

    @Override
    public void close() throws IOException {
        sc.close();
    }
}
