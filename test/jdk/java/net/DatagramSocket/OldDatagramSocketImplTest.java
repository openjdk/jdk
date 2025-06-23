/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8260428
 * @summary Drop support for pre JDK 1.4 DatagramSocketImpl implementations
 * @run testng/othervm OldDatagramSocketImplTest
 */

import org.testng.annotations.Test;

import java.net.*;
import java.io.*;

import static org.testng.Assert.assertEquals;

public class OldDatagramSocketImplTest {
    InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    @Test
    public void testOldImplConnect() {
        try (var ds = new DatagramSocket(new OldDatagramSocketImpl()) {}) {
            ds.connect(new InetSocketAddress(LOOPBACK, 6667));
            throw new RuntimeException("ERROR: test failed");
        } catch (SocketException ex) {
            assertEquals(ex.getMessage(), "connect not implemented");
            System.out.println("PASSED: default implementation of connect has thrown as expected");
        }
    }

    @Test
    public void testOldImplConnectTwoArgs() {
        try (var ds = new DatagramSocket(new OldDatagramSocketImpl()) {}) {
            ds.connect(LOOPBACK, 6667);
            throw new RuntimeException("ERROR: test failed");
        } catch (UncheckedIOException ex) {
            assertEquals(ex.getMessage(), "connect failed");
            System.out.println("PASSED: default implementation of connect has thrown as expected");
        }
    }

    @Test
    public void testOldImplDisconnect() {
        try (var ds = new DatagramSocket(new OldDatagramSocketImplWithValidConnect()) { }){
            ds.connect(LOOPBACK, 6667);
            ds.disconnect();
            throw new RuntimeException("ERROR: test failed");
        } catch (UncheckedIOException ex) {
            var innerException = ex.getCause();
            assertEquals(innerException.getClass(), SocketException.class);
            assertEquals(innerException.getMessage(), "disconnect not implemented");
            System.out.println("PASSED: default implementation of disconnect has thrown as expected");
        }
    }

    @Test
    public void testOldImplPublic() {
        try (var ds = new PublicOldDatagramSocketImpl()) {
            ds.connect(LOOPBACK, 0);
            throw new RuntimeException("ERROR: test failed");
        } catch (SocketException ex) {
            assertEquals(ex.getMessage(), "connect not implemented");
            System.out.println("PASSED: default implementation of disconnect has thrown as expected");
        }
    }
    @Test
    public void testOldImplPublicDisconnect() {
        try (var ds = new PublicOldDatagramSocketImplWithValidConnect()) {
            ds.disconnect();
            throw new RuntimeException("ERROR: test failed");
        } catch (UncheckedIOException ex) {
            var innerException = ex.getCause();
            assertEquals(innerException.getClass(), SocketException.class);
            assertEquals(innerException.getMessage(), "disconnect not implemented");
            System.out.println("PASSED: default implementation of disconnect has thrown as expected");
        }
    }

    private class OldDatagramSocketImpl extends DatagramSocketImpl implements AutoCloseable {

        @Override
        protected void create() throws SocketException { }

        @Override
        protected void bind(int lport, InetAddress laddr) throws SocketException { }

        @Override
        protected void send(DatagramPacket p) throws IOException { }

        @Override
        protected int peek(InetAddress i) throws IOException {
            return 0;
        }

        @Override
        protected int peekData(DatagramPacket p) throws IOException {
            return 0;
        }

        @Override
        protected void receive(DatagramPacket p) throws IOException { }

        @Override
        protected void setTimeToLive(int ttl) throws IOException { }

        @Override
        protected int getTimeToLive() throws IOException {
            return 0;
        }

        @Override
        protected void join(InetAddress inetaddr) throws IOException { }

        @Override
        protected void leave(InetAddress inetaddr) throws IOException { }

        @Override
        protected void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException { }

        @Override
        protected void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException { }

        @Override
        public void close() { }

        @Override
        public void setOption(int optID, Object value) throws SocketException { }

        @Override
        public Object getOption(int optID) throws SocketException {
            return null;
        }
    }

    private class OldDatagramSocketImplWithValidConnect extends OldDatagramSocketImpl implements AutoCloseable {
        @Override
        protected void connect(InetAddress address, int port) throws SocketException { }
    }
    // Overriding connect() to make it public so that it can be called
    // directly from the test code
    private class PublicOldDatagramSocketImpl extends OldDatagramSocketImpl {
        public void connect(InetAddress addr, int port) throws SocketException { super.connect(addr, port); }
    }
    // Overriding disconnect() to make it public so that it can be called
    // directly from the test code
    private class PublicOldDatagramSocketImplWithValidConnect extends OldDatagramSocketImplWithValidConnect {
        public void disconnect() { super.disconnect(); }
    }
}
