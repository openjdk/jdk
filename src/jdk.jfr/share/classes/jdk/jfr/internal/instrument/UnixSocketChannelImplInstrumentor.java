/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;

import jdk.jfr.events.Handlers;
import jdk.jfr.internal.handlers.EventHandler;

/**
 * See {@link JITracer} for an explanation of this code.
 */
@JIInstrumentationTarget("sun.nio.ch.UnixDomainSocketChannelImpl")
final class UnixSocketChannelImplInstrumentor {

    private UnixSocketChannelImplInstrumentor() {
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int read(ByteBuffer dst) throws IOException {
        EventHandler handler = Handlers.UNIX_SOCKET_READ;
        if (!handler.isEnabled()) {
            return read(dst);
        }
        UnixDomainSocketAddress remoteAddress = (UnixDomainSocketAddress)getRemoteAddress();
        int bytesRead = 0;
        long start  = 0;
        try {
            start = EventHandler.timestamp();;
            bytesRead = read(dst);
        } finally {
            long duration = EventHandler.timestamp() - start;
            if (handler.shouldCommit(duration))  {
                String path = remoteAddress.getPath().toString();
                if (bytesRead < 0) {
                    handler.write(start, duration, path, 0L, true);
                } else {
                    handler.write(start, duration, path, bytesRead, false);
                }
            }
        }
        return bytesRead;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        EventHandler handler = Handlers.UNIX_SOCKET_READ;
        if (!handler.isEnabled()) {
            return read(dsts, offset, length);
        }
        UnixDomainSocketAddress remoteAddress = (UnixDomainSocketAddress)getRemoteAddress();

        long bytesRead = 0;
        long start = 0;
        try {
            start = EventHandler.timestamp();
            bytesRead = read(dsts, offset, length);
        } finally {
            long duration = EventHandler.timestamp() - start;
            if (handler.shouldCommit(duration)) {
                String path = remoteAddress.getPath().toString();
                if (bytesRead < 0) {
                    handler.write(start, duration, path, 0L, true);
                } else {
                    handler.write(start, duration, path, bytesRead, false);
                }
            }
        }
        return bytesRead;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int write(ByteBuffer buf) throws IOException {
        EventHandler handler = Handlers.UNIX_SOCKET_WRITE;
        if (!handler.isEnabled()) {
            return write(buf);
        }
        UnixDomainSocketAddress remoteAddress = (UnixDomainSocketAddress)getRemoteAddress();
        int bytesWritten = 0;
        long start = 0;
        try {
            start = EventHandler.timestamp();
            bytesWritten = write(buf);
        } finally {
            long duration = EventHandler.timestamp() - start;
            if (handler.shouldCommit(duration)) {
                String path = remoteAddress.getPath().toString();
                handler.write(start, duration, path, bytesWritten);
            }
        }
        return bytesWritten;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        EventHandler handler = Handlers.UNIX_SOCKET_WRITE;
        if (!handler.isEnabled()) {
            return write(srcs, offset, length);
        }
        UnixDomainSocketAddress remoteAddress = (UnixDomainSocketAddress)getRemoteAddress();
        long bytesWritten = 0;
        long start = 0;
        try {
            start = EventHandler.timestamp();
            bytesWritten = write(srcs, offset, length);
        } finally {
            long duration = EventHandler.timestamp() - start;
            if (handler.shouldCommit(duration)) {
                String path = remoteAddress.getPath().toString();
                handler.write(start, duration, path, bytesWritten);
            }
        }
        return bytesWritten;
    }

    public SocketAddress getRemoteAddress() throws IOException {
        // is replaced by call to instrumented class
        return null;
    }
}
