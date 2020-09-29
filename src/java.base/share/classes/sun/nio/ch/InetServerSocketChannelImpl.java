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
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import sun.net.NetHooks;
import sun.net.ext.ExtendedSocketOptions;

/**
 * An implementation of ServerSocketChannels for AF_INET/AF_INET6 sockets
 */

class InetServerSocketChannelImpl
    extends ServerSocketChannelImpl
{
    // the protocol family requested by the user, or Net.UNSPEC if not specified
    private final ProtocolFamily family;

    // set true when exclusive binding is on and SO_REUSEADDR is emulated
    private boolean isReuseAddress;

    InetServerSocketChannelImpl(SelectorProvider sp) throws IOException {
        this(sp, Net.isIPv6Available()
                ? StandardProtocolFamily.INET6
                : StandardProtocolFamily.INET);
    }

    InetServerSocketChannelImpl(SelectorProvider sp, ProtocolFamily family)
        throws IOException
    {
        super(sp, Net.serverSocket(family, true), false);
        this.family = family;
    }

    InetServerSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd, bound);
        this.family =  Net.isIPv6Available()
                ? StandardProtocolFamily.INET6
                : StandardProtocolFamily.INET;
    }


    @Override
    InetSocketAddress implLocalAddress(FileDescriptor fd) throws IOException {
        return Net.localAddress(fd);
    }

    @Override
    protected SocketAddress getRevealedLocalAddress(SocketAddress addr) {
        return Net.getRevealedLocalAddress((InetSocketAddress)addr);
    }

    @Override
    protected String getRevealedLocalAddressAsString(SocketAddress addr) {
        return Net.getRevealedLocalAddressAsString((InetSocketAddress)addr);
    }

    /**
     * Returns the local address, or null if not bound
     */
    @Override
    InetSocketAddress localAddress() {
        return (InetSocketAddress)super.localAddress();
    }

    @Override
    <T> void implSetOption(SocketOption<T> name, T value) throws IOException {
        if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
            // SO_REUSEADDR emulated when using exclusive bind
            isReuseAddress = (Boolean) value;
        } else {
            Net.setSocketOption(getFD(), Net.UNSPEC, name, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    <T> T implGetOption(SocketOption<T> name) throws IOException {
        if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
            // SO_REUSEADDR emulated when using exclusive bind
            return (T) Boolean.valueOf(isReuseAddress);
        }
        return (T) Net.getSocketOption(getFD(), Net.UNSPEC, name);
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_REUSEADDR);
            if (Net.isReusePortAvailable()) {
                set.add(StandardSocketOptions.SO_REUSEPORT);
            }
            set.addAll(ExtendedSocketOptions.serverSocketOptions());
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    @Override
    SocketAddress implBind(SocketAddress local, int backlog) throws IOException {
        InetSocketAddress isa;
        if (local == null) {
            isa = new InetSocketAddress(Net.anyLocalAddress(family), 0);
        } else {
            isa = Net.checkAddress(local, family);
        }
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkListen(isa.getPort());
        NetHooks.beforeTcpBind(getFD(), isa.getAddress(), isa.getPort());
        Net.bind(family, getFD(), isa.getAddress(), isa.getPort());
        Net.listen(getFD(), backlog < 1 ? 50 : backlog);
        return Net.localAddress(getFD());
    }

    @Override
    protected int implAccept(FileDescriptor fd, FileDescriptor newfd, SocketAddress[] addrs)
        throws IOException
    {
        InetSocketAddress[] a = new InetSocketAddress[1];
        int n = Net.accept(fd, newfd, a);
        addrs[0] = a[0];
        return n;
    }

    @Override
    protected SocketChannel implFinishAccept(FileDescriptor newfd, SocketAddress sa)
        throws IOException
    {
        InetSocketAddress isa = (InetSocketAddress)sa;
        // check permitted to accept connections from the remote address
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkAccept(isa.getAddress().getHostAddress(), isa.getPort());
        }
        return new InetSocketChannelImpl(provider(), family, newfd, isa);
    }
}
