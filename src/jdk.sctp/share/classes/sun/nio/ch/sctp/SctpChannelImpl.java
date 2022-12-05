/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package sun.nio.ch.sctp;

import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.NotificationHandler;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpSocketOption;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

/**
 * Unimplemented.
 */
public class SctpChannelImpl
        extends SctpChannel {

    public SctpChannelImpl(SelectorProvider provider) {
        super(provider);
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public Association association() {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public SctpChannel bind(SocketAddress local) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public SctpChannel bindAddress(InetAddress address) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public SctpChannel unbindAddress(InetAddress address) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public boolean connect(SocketAddress remote,
                           int maxOutStreams,
                           int maxInStreams) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public boolean isConnectionPending() {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public boolean finishConnect() throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public Set<SocketAddress> getAllLocalAddresses() throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public Set<SocketAddress> getRemoteAddresses() throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public SctpChannel shutdown() throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public <T> T getOption(SctpSocketOption<T> name) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public <T> SctpChannel setOption(SctpSocketOption<T> name,
                                     T value) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public Set<SctpSocketOption<?>> supportedOptions() {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public <T> MessageInfo receive(ByteBuffer dst,
                                   T attachment,
                                   NotificationHandler<T> handler) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public int send(ByteBuffer src,
                    MessageInfo messageInfo) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }

    @Override
    public void implCloseSelectableChannel() throws IOException {
        throw UnsupportedUtil.sctpUnsupported();
    }
}
