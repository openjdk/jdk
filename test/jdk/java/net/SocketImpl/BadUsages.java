/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8221481
 * @summary Test the platform SocketImpl when used in unintended ways
 * @compile/module=java.base java/net/PlatformSocketImpl.java
 * @run junit/othervm ${test.main.class}
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.SocketOptions;
import java.net.StandardSocketOptions;

import java.net.PlatformSocketImpl;  // test helper

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SocketImpl does not specify how the SocketImpl behaves when used in ways
 * that are not intended, e.g. invoking socket operations before the socket is
 * created or trying to establish a connection after the socket is connected or
 * closed.
 *
 * This test exercises the platform SocketImpl to test that it is reliable, and
 * throws reasonable exceptions, for these scenarios.
 */

public class BadUsages {

    /**
     * Test create when already created.
     */
    @Test
    public void testCreate1() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            assertThrows(IOException.class, () -> impl.create(true));
        }
    }

    /**
     * Test create when closed.
     */
    @Test
    public void testCreate2() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        assertThrows(IOException.class, () -> impl.create(true));
    }

    /**
     * Test create when not a stream socket.
     */
    @Test
    public void testCreate3() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            assertThrows(IOException.class, () -> impl.create(false));
        }
    }

    /**
     * Test connect when not created.
     */
    @Test
    public void testConnect1() throws IOException {
        try (var ss = new ServerSocket(0)) {
            var impl = new PlatformSocketImpl(false);
            var address = ss.getInetAddress();
            int port = ss.getLocalPort();
            assertThrows(IOException.class, () -> impl.connect(address, port));
        }
    }

    /**
     * Test connect with unsupported address type.
     */
    @Test
    public void testConnect2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var remote = new SocketAddress() { };
            assertThrows(IOException.class, () -> impl.connect(remote, 0));
        }
    }

    /**
     * Test connect with an unresolved address.
     */
    @Test
    public void testConnect3() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var remote = new InetSocketAddress("blah-blah.blah-blah", 80);
            assertThrows(IOException.class, () -> impl.connect(remote, 0));
        }
    }

    /**
     * Test connect when already connected.
     */
    @Test
    public void testConnect4() throws IOException {
        try (var ss = new ServerSocket();
             var impl = new PlatformSocketImpl(false)) {
            var loopback = InetAddress.getLoopbackAddress();
            ss.bind(new InetSocketAddress(loopback, 0));
            impl.create(true);
            int port = ss.getLocalPort();
            impl.connect(loopback, port);
            assertThrows(IOException.class, () -> impl.connect(loopback, port));
        }
    }

    /**
     * Test connect when closed.
     */
    @Test
    public void testConnect5() throws IOException {
        try (var ss = new ServerSocket(0)) {
            var impl = new PlatformSocketImpl(false);
            impl.close();
            String host = ss.getInetAddress().getHostAddress();
            int port = ss.getLocalPort();
            assertThrows(IOException.class, () -> impl.connect(host, port));
        }
    }

    /**
     * Test bind when not created.
     */
    @Test
    public void testBind1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        var loopback = InetAddress.getLoopbackAddress();
        assertThrows(IOException.class, () -> impl.bind(loopback, 0));
    }

    /**
     * Test bind when already bound.
     */
    @Test
    public void testBind2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var loopback = InetAddress.getLoopbackAddress();
            impl.bind(loopback, 0);
            assertThrows(IOException.class, () -> impl.bind(loopback, 0));
        }
    }

    /**
     * Test bind when connected.
     */
    @Test
    public void testBind3() throws IOException {
        try (var ss = new ServerSocket();
             var impl = new PlatformSocketImpl(false)) {
            var loopback = InetAddress.getLoopbackAddress();
            ss.bind(new InetSocketAddress(loopback, 0));
            impl.create(true);
            impl.connect(ss.getLocalSocketAddress(), 0);
            assertThrows(IOException.class, () -> impl.bind(loopback, 0));
        }
    }

    /**
     * Test bind when closed.
     */
    @Test
    public void testBind4() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        var loopback = InetAddress.getLoopbackAddress();
        assertThrows(IOException.class, () -> impl.bind(loopback, 0));
    }


    /**
     * Test listen when not created.
     */
    @Test
    public void testListen1() {
        var impl = new PlatformSocketImpl(false);
        assertThrows(IOException.class, () -> impl.listen(16));
    }

    /**
     * Test listen when not bound.
     */
    @Test
    public void testListen2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            assertThrows(IOException.class, () -> impl.listen(16));
        }
    }

    /**
     * Test listen when closed.
     */
    @Test
    public void testListen3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        assertThrows(IOException.class, () -> impl.listen(16));
    }

    /**
     * Test accept when not created.
     */
    @Test
    public void testAccept1() throws IOException {
        var impl = new PlatformSocketImpl(true);
        var si = new PlatformSocketImpl(false);
        assertThrows(IOException.class, () -> impl.accept(si));
    }

    /**
     * Test accept when not bound.
     */
    @Test
    public void testAccept2() throws IOException {
        try (var impl = new PlatformSocketImpl(true)) {
            impl.create(true);
            var si = new PlatformSocketImpl(false);
            assertThrows(IOException.class, () -> impl.accept(si));
        }
    }

    /**
     * Test accept when closed.
     */
    @Test
    public void testAccept4() throws IOException {
        var impl = new PlatformSocketImpl(true);
        impl.close();
        var si = new PlatformSocketImpl(false);
        assertThrows(IOException.class, () -> impl.accept(si));
    }

    /**
     * Test accept with SocketImpl that is already created.
     */
    @Test
    public void testAccept5() throws IOException {
        try (var impl = new PlatformSocketImpl(true);
             var si = new PlatformSocketImpl(false)) {
            impl.create(true);
            impl.bind(InetAddress.getLoopbackAddress(), 0);
            si.create(true);
            assertThrows(IOException.class, () -> impl.accept(si));
        }
    }

    /**
     * Test accept with SocketImpl that is closed.
     */
    @Test
    public void testAccept6() throws IOException {
        try (var impl = new PlatformSocketImpl(true);
             var si = new PlatformSocketImpl(false)) {
            impl.create(true);
            impl.bind(InetAddress.getLoopbackAddress(), 0);
            si.create(true);
            si.close();
            assertThrows(IOException.class, () -> impl.accept(si));
        }
    }

    /**
     * Test available when not created.
     */
    @Test
    public void testAvailable1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        assertThrows(IOException.class, () -> impl.available());
    }

    /**
     * Test available when created but not connected.
     */
    @Test
    public void testAvailable2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            assertThrows(IOException.class, () -> impl.available());
        }
    }

    /**
     * Test available when closed.
     */
    @Test
    public void testAvailable3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        assertThrows(IOException.class, () -> impl.available());
    }

    /**
     * Test setOption when not created.
     */
    @Test
    public void testSetOption1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        assertThrows(IOException.class,
                     () -> impl.setOption(StandardSocketOptions.SO_REUSEADDR, true));
        // legacy
        assertThrows(SocketException.class,
                     () -> impl.setOption(SocketOptions.SO_REUSEADDR, true));
    }

    /**
     * Test setOption when closed.
     */
    @Test
    public void testSetOption2() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        assertThrows(IOException.class,
                     () -> impl.setOption(StandardSocketOptions.SO_REUSEADDR, true));
        // legacy
        assertThrows(SocketException.class,
                     () -> impl.setOption(SocketOptions.SO_REUSEADDR, true));
    }

    /**
     * Test setOption with unsupported option.
     */
    @Test
    public void testSetOption3() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var opt = new SocketOption<String>() {
                @Override public String name() { return "birthday"; }
                @Override public Class<String> type() { return String.class; }
            };
            assertThrows(UnsupportedOperationException.class, () -> impl.setOption(opt, ""));
            // legacy
            assertThrows(SocketException.class, () -> impl.setOption(-1, ""));
        }
    }

    /**
     * Test setOption(int, Object) with invalid values.
     */
    @Test
    public void testSetOption4() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            assertThrows(SocketException.class,
                         () -> impl.setOption(SocketOptions.SO_REUSEADDR, -1));
            assertThrows(SocketException.class,
                         () -> impl.setOption(SocketOptions.SO_TIMEOUT, -1));
            assertThrows(SocketException.class,
                         () -> impl.setOption(SocketOptions.SO_SNDBUF, -1));
            assertThrows(SocketException.class,
                         () -> impl.setOption(SocketOptions.SO_RCVBUF, -1));
        }
    }

    /**
     * Test getOption when not created.
     */
    @Test
    public void testGetOption1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        assertThrows(IOException.class,
                     () -> impl.getOption(StandardSocketOptions.SO_REUSEADDR));
        assertThrows(SocketException.class,
                     () -> impl.getOption(-1));
    }

    /**
     * Test getOption when closed.
     */
    @Test
    public void testGetOption2() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        assertThrows(IOException.class,
                     () -> impl.getOption(StandardSocketOptions.SO_REUSEADDR));
        assertThrows(SocketException.class,
                     () -> impl.getOption(SocketOptions.SO_REUSEADDR));
    }

    /**
     * Test getOption with unsupported option.
     */
    @Test
    public void testGetOption3() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var opt = new SocketOption<String>() {
                @Override public String name() { return "birthday"; }
                @Override public Class<String> type() { return String.class; }
            };
            assertThrows(UnsupportedOperationException.class, () -> impl.getOption(opt));
            assertThrows(SocketException.class, () -> impl.getOption(-1));
        }
    }

    /**
     * Test shutdownInput when not created.
     */
    @Test
    public void testShutdownInput1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        assertThrows(IOException.class, () -> impl.shutdownInput());
    }

    /**
     * Test shutdownInput when not connected.
     */
    @Test
    public void testShutdownInput2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            assertThrows(IOException.class, () -> impl.shutdownInput());
        }
    }

    /**
     * Test shutdownInput when closed.
     */
    @Test
    public void testShutdownInput3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        assertThrows(IOException.class, () -> impl.shutdownInput());
    }

    /**
     * Test shutdownOutput when not created.
     */
    @Test
    public void testShutdownOutput1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        assertThrows(IOException.class, () -> impl.shutdownOutput());
    }

    /**
     * Test shutdownOutput when not connected.
     */
    @Test
    public void testShutdownOutput2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            assertThrows(IOException.class, () -> impl.shutdownOutput());
        }
    }

    /**
     * Test shutdownOutput when closed.
     */
    @Test
    public void testShutdownOutput3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        assertThrows(IOException.class, () -> impl.shutdownOutput());
    }

    /**
     * Test sendUrgentData when not created.
     */
    @Test
    public void testSendUrgentData1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        assertThrows(IOException.class, () -> impl.sendUrgentData(0));
    }

    /**
     * Test sendUrgentData when not connected.
     */
    @Test
    public void testSendUrgentData2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            assertThrows(IOException.class, () -> impl.sendUrgentData(0));
        }
    }

    /**
     * Test sendUrgentData when closed.
     */
    @Test
    public void testSendUrgentData3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        assertThrows(IOException.class, () -> impl.sendUrgentData(0));
    }
}
