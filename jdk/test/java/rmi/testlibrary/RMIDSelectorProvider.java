/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import static java.net.StandardSocketOptions.SO_REUSEADDR;
import static java.net.StandardSocketOptions.SO_REUSEPORT;

/**
 * A SelectorProvider, that can be loaded by the child rmid process, whose
 * inheritedChannel method will create a new server socket channel and report
 * it back to the parent process, over stdout.
 */
public class RMIDSelectorProvider extends SelectorProvider {

    private final SelectorProvider provider;
    private ServerSocketChannel channel;

    public RMIDSelectorProvider() {
        provider = sun.nio.ch.DefaultSelectorProvider.create();
    }

    public DatagramChannel openDatagramChannel()
        throws IOException
    {
        return provider.openDatagramChannel();
    }

    public DatagramChannel openDatagramChannel(ProtocolFamily family)
        throws IOException
    {
        return provider.openDatagramChannel(family);
    }

    public Pipe openPipe()
        throws IOException
    {
        return provider.openPipe();
    }

    public AbstractSelector openSelector()
        throws IOException
    {
        return provider.openSelector();
    }

    public ServerSocketChannel openServerSocketChannel()
        throws IOException
    {
        return provider.openServerSocketChannel();
    }

    public SocketChannel openSocketChannel()
        throws IOException
    {
        return provider.openSocketChannel();
    }

    public synchronized Channel inheritedChannel() throws IOException {
        System.out.println("RMIDSelectorProvider.inheritedChannel");
        if (channel == null) {
            // Create and bind a new server socket channel
            channel = ServerSocketChannel.open();

            // Enable SO_REUSEADDR before binding
            channel.setOption(SO_REUSEADDR, true);

            // Enable SO_REUSEPORT, if supported, before binding
            if (channel.supportedOptions().contains(SO_REUSEPORT)) {
                channel.setOption(SO_REUSEPORT, true);
            }

            channel.bind(new InetSocketAddress(0));

            System.out.println(RMID.EPHEMERAL_MSG + channel.socket().getLocalPort());
        }
        return channel;
    }
}
