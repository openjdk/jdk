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
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import sun.net.NetHooks;
import sun.net.ext.ExtendedSocketOptions;

/**
 * An implementation of SocketChannels for AF_INET/AF_INET6 sockets
 */

class InetSocketChannelImpl extends SocketChannelImpl
{
    // the protocol family of the socket
    private final ProtocolFamily family;

    // set true when exclusive binding is on and SO_REUSEADDR is emulated
    private boolean isReuseAddress;

    // Constructor for normal connecting sockets
    //
    InetSocketChannelImpl(SelectorProvider sp) throws IOException {
        this(sp, Net.isIPv6Available()
                ? StandardProtocolFamily.INET6
                : StandardProtocolFamily.INET);
    }

    InetSocketChannelImpl(SelectorProvider sp, ProtocolFamily family) throws IOException {
        super(sp, Net.socket(family, true), false);
        this.family = family;
    }

    InetSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd, bound);
        this.family = Net.isIPv6Available()
                ? StandardProtocolFamily.INET6
                : StandardProtocolFamily.INET;
    }

    @Override
    SocketAddress implLocalAddress(FileDescriptor fd) throws IOException {
        return Net.localAddress(fd);
    }

    // Constructor for sockets obtained from server sockets
    //
    InetSocketChannelImpl(SelectorProvider sp,
                          ProtocolFamily family,
                          FileDescriptor fd,
                          InetSocketAddress isa)
        throws IOException
    {
        super(sp, fd, isa);
        this.family = family;
    }


    @Override
    SocketAddress getRevealedLocalAddress(SocketAddress address) {
        return Net.getRevealedLocalAddress((InetSocketAddress)address);
    }

    @Override
    <T> void implSetOption(SocketOption<T> name, T value) throws IOException {
        FileDescriptor fd = getFD();

        if (name == StandardSocketOptions.IP_TOS) {
            ProtocolFamily family = Net.isIPv6Available() ?
                StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
            Net.setSocketOption(fd, family, name, value);
        } else if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
            // SO_REUSEADDR emulated when using exclusive bind
            isReuseAddress = (Boolean)value;
        } else {
            Net.setSocketOption(fd, name, value);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    <T> T implGetOption(SocketOption<T> name) throws IOException {
        FileDescriptor fd = getFD();

        if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
            // SO_REUSEADDR emulated when using exclusive bind
            return (T)Boolean.valueOf(isReuseAddress);
        }

        // special handling for IP_TOS
        if (name == StandardSocketOptions.IP_TOS) {
            ProtocolFamily family = Net.isIPv6Available() ?
                StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
            return (T) Net.getSocketOption(fd, family, name);
        }
        return (T) Net.getSocketOption(fd, name);
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_SNDBUF);
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_KEEPALIVE);
            set.add(StandardSocketOptions.SO_REUSEADDR);
            if (Net.isReusePortAvailable()) {
                set.add(StandardSocketOptions.SO_REUSEPORT);
            }
            set.add(StandardSocketOptions.SO_LINGER);
            set.add(StandardSocketOptions.TCP_NODELAY);
            // additional options required by socket adaptor
            set.add(StandardSocketOptions.IP_TOS);
            set.add(ExtendedSocketOption.SO_OOBINLINE);
            set.addAll(ExtendedSocketOptions.clientSocketOptions());
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
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

    /**
     * Returns the local address, or null if not bound
     */
    @Override
    InetSocketAddress localAddress() {
        return (InetSocketAddress)super.localAddress();
    }

    /**
     * Returns the remote address, or null if not connected
     */
    @Override
    InetSocketAddress remoteAddress() {
        return (InetSocketAddress)super.remoteAddress();
    }

    @Override
    SocketAddress implBind(SocketAddress local) throws IOException {
        InetSocketAddress isa;
        if (local == null) {
            isa = new InetSocketAddress(Net.anyLocalAddress(family), 0);
        } else {
            isa = Net.checkAddress(local, family);
        }
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkListen(isa.getPort());
        }
        FileDescriptor fd = getFD();
        NetHooks.beforeTcpBind(fd, isa.getAddress(), isa.getPort());
        Net.bind(family, fd, isa.getAddress(), isa.getPort());
        return Net.localAddress(fd);
    }

    /**
     * Checks the remote address to which this channel is to be connected.
     */
    @Override
    protected InetSocketAddress checkRemote(SocketAddress sa) throws IOException {
        InetSocketAddress isa = Net.checkAddress(sa, family);
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkConnect(isa.getAddress().getHostAddress(), isa.getPort());
        }
        InetAddress address = isa.getAddress();
        if (address.isAnyLocalAddress()) {
            int port = isa.getPort();
            if (address instanceof Inet4Address) {
                return new InetSocketAddress(Net.inet4LoopbackAddress(), port);
            } else {
                assert family == StandardProtocolFamily.INET6;
                return new InetSocketAddress(Net.inet6LoopbackAddress(), port);
            }
        } else {
            return isa;
        }
    }

    @Override
    protected int implConnect(FileDescriptor fd, SocketAddress sa) throws IOException {
        InetSocketAddress isa = (InetSocketAddress)sa;
        return Net.connect(family, fd, isa.getAddress(), isa.getPort());
    }

    @Override
    protected String getRevealedLocalAddressAsString(SocketAddress sa) {
        InetSocketAddress isa = (InetSocketAddress)sa;
        return Net.getRevealedLocalAddressAsString(isa);
    }
}
