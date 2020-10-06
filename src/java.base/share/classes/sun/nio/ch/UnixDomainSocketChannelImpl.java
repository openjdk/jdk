/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import sun.net.ext.ExtendedSocketOptions;

/**
 * An implementation of SocketChannels
 */

public class UnixDomainSocketChannelImpl extends SocketChannelImpl
{
    UnixDomainSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd, bound);
    }

    // Constructor for sockets obtained from server sockets
    //
    UnixDomainSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, SocketAddress isa)
        throws IOException
    {
        super(sp, fd, isa);
    }

    @Override
    SocketAddress implLocalAddress(FileDescriptor fd) throws IOException {
        return UnixDomainSockets.localAddress(fd);
    }

    @Override
    SocketAddress getRevealedLocalAddress(SocketAddress address) {
        UnixDomainSocketAddress uaddr = (UnixDomainSocketAddress)address;
        return UnixDomainSockets.getRevealedLocalAddress(uaddr);
    }

    @Override
    <T> void implSetOption(SocketOption<T> name, T value) throws IOException {
        Net.setSocketOption(getFD(), name, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    <T> T implGetOption(SocketOption<T> name) throws IOException {
        return (T) Net.getSocketOption(getFD(), name);
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_SNDBUF);
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_LINGER);
            set.addAll(ExtendedSocketOptions.unixSocketOptions());
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    @Override
    public Socket socket() {
        throw new UnsupportedOperationException("socket not supported");
    }

    /**
     * Read/write need to be overridden for JFR
     */
    @Override
    public int read(ByteBuffer buf) throws IOException {
        return super.read(buf);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException
    {
        return super.read(dsts, offset, length);
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        return super.write(buf);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException
    {
        return super.write(srcs, offset, length);
    }

    @Override
    SocketAddress implBind(SocketAddress local) throws IOException {
        UnixDomainSockets.checkPermission();
        UnixDomainSocketAddress usa = UnixDomainSockets.checkAddress(local);
        Path path = usa == null ? null : usa.getPath();
        UnixDomainSockets.bind(getFD(), path);
        if (usa == null || path.toString().equals("")) {
            return UnixDomainSockets.UNNAMED;
        } else {
            return UnixDomainSockets.localAddress(getFD());
        }
    }

    /**
     * Checks the permissions required for connect
     */
    @Override
    SocketAddress checkRemote(SocketAddress sa) throws IOException {
        Objects.requireNonNull(sa);
        UnixDomainSockets.checkPermission();
        UnixDomainSocketAddress usa = UnixDomainSockets.checkAddress(sa);
        return usa;
    }

    @Override
    int implConnect(FileDescriptor fd, SocketAddress sa) throws IOException {
        UnixDomainSocketAddress usa = (UnixDomainSocketAddress)sa;
        return UnixDomainSockets.connect(fd, usa.getPath());
    }

    String getRevealedLocalAddressAsString(SocketAddress sa) {
        UnixDomainSocketAddress usa = (UnixDomainSocketAddress)sa;
        return UnixDomainSockets.getRevealedLocalAddressAsString(usa);
    }
}
