/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4675045 8198753
 * @summary Test DatagramChannel send exceptions
 * @library ..
 * @run junit SendExceptions
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SendExceptions {
    private DatagramChannel sndChannel;
    private DatagramChannel rcvChannel;
    private InetSocketAddress sender;
    private InetSocketAddress receiver;
    static final ByteBuffer buf = ByteBuffer.allocate(17);

    @BeforeEach
    public void setup() throws Exception {
        buf.rewind();
        sndChannel = DatagramChannel.open();
        sndChannel.bind(null);
        InetAddress address = InetAddress.getLocalHost();
        if (address.isLoopbackAddress()) {
            address = InetAddress.getLoopbackAddress();
        }
        sender = new InetSocketAddress(address,
            sndChannel.socket().getLocalPort());

        rcvChannel = DatagramChannel.open();
        rcvChannel.bind(null);
        receiver = new InetSocketAddress(address,
            rcvChannel.socket().getLocalPort());
    }

    @AfterEach
    public void cleanup() throws Exception {
        rcvChannel.close();
        sndChannel.close();
    }

    @Test
    public void unsupportedAddressTypeException() throws IOException {
        assertThrows(UnsupportedAddressTypeException.class, () -> {
            sndChannel.send(buf, new SocketAddress() {});
        });
        rcvChannel.connect(sender);
        assertThrows(UnsupportedAddressTypeException.class, () -> {
            sndChannel.send(buf, new SocketAddress() {});
        });
        sndChannel.connect(receiver);
        assertThrows(UnsupportedAddressTypeException.class, () -> {
            sndChannel.send(buf, new SocketAddress() {});
        });
    }

    @Test
    public void unresolvedAddressException() {
        String host = TestUtil.UNRESOLVABLE_HOST;
        InetSocketAddress unresolvable = new InetSocketAddress (host, 37);
        assertThrows(UnresolvedAddressException.class, () -> {
            sndChannel.send(buf, unresolvable);
        });
    }

    @Test
    public void alreadyConnectedException() throws IOException {
        sndChannel.connect(receiver);
        InetSocketAddress random = new InetSocketAddress(0);
        assertThrows(AlreadyConnectedException.class, () -> {
            sndChannel.send(buf, random);
        });
    }

}
