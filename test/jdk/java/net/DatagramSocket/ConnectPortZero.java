/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

/*
 * @test
 * @bug 8240533
 * @summary Check that DatagramSocket, MulticastSocket and DatagramSocketAdaptor
 *          throw expected Exception when connecting to port 0
 * @run testng/othervm ConnectPortZero
 */

public class ConnectPortZero {
    private InetAddress loopbackAddr, wildcardAddr;
    private DatagramSocket datagramSocket, datagramSocketAdaptor;
    private MulticastSocket multicastSocket;

    private static final Class<SocketException> SE = SocketException.class;
    private static final Class<UncheckedIOException> UCIOE = UncheckedIOException.class;

    @BeforeTest
    public void setUp() throws IOException {
        loopbackAddr = InetAddress.getLoopbackAddress();
        wildcardAddr = new InetSocketAddress(0).getAddress();

        datagramSocket = new DatagramSocket();
        multicastSocket = new MulticastSocket();
        datagramSocketAdaptor = DatagramChannel.open().socket();
    }

    @DataProvider(name = "data")
    public Object[][] variants() {
        return new Object[][]{
                { datagramSocket,        loopbackAddr },
                { datagramSocketAdaptor, loopbackAddr },
                { multicastSocket,       loopbackAddr },
                { datagramSocket,        wildcardAddr },
                { datagramSocketAdaptor, wildcardAddr },
                { multicastSocket,       wildcardAddr }
        };
    }

    @Test(dataProvider = "data")
    public void testConnect(DatagramSocket ds, InetAddress addr) {
        Throwable t = expectThrows(UCIOE, () -> ds.connect(addr, 0));
        assertEquals(t.getCause().getClass(), SE);

        assertThrows(SE, () -> ds
                .connect(new InetSocketAddress(addr, 0)));
    }

    @AfterTest
    public void tearDown() {
        datagramSocket.close();
        multicastSocket.close();
        datagramSocketAdaptor.close();
    }
}
