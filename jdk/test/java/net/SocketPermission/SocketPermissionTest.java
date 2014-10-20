/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8047031
 * @summary SocketPermission tests for legacy socket types
 * @library ../../../lib/testlibrary
 * @run testng/othervm/policy=policy SocketPermissionTest
 */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketPermission;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntConsumer;
import static jdk.testlibrary.Utils.getFreePort;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SocketPermissionTest {
    private int freePort = -1;

    //positive tests
    @Test(dataProvider = "positiveProvider")
    public void testPositive(Function<String, AccessControlContext> genAcc, IntConsumer func) {
        String addr = "localhost:" + freePort;
        AccessControlContext acc = genAcc.apply(addr);
        AccessController.doPrivileged((PrivilegedAction) () -> {
            func.accept(freePort);
            return null;
        }, acc);
    }

    //negative tests
    @Test(dataProvider = "negativeProvider", expectedExceptions = SecurityException.class)
    public void testNegative(AccessControlContext acc, IntConsumer func) {
        AccessController.doPrivileged((PrivilegedAction) () -> {
            func.accept(freePort);
            return null;
        }, acc);
    }

    @BeforeMethod
    public void setFreePort() throws Exception {
        freePort = getFreePort();
    }

    @DataProvider
    public Object[][] positiveProvider() {
        //test for SocketPermission "host:port","connect,resolve";
        Function<String, AccessControlContext> generateAcc1 = (addr) -> getAccessControlContext(
                new SocketPermission(addr, "listen, connect,resolve"));
        IntConsumer func1 = (i) -> connectSocketTest(i);
        IntConsumer func2 = (i) -> connectDatagramSocketTest(i);

        //test for SocketPermission "localhost:1024-","accept";
        Function<String, AccessControlContext> generateAcc2 = (addr) -> getAccessControlContext(
                new SocketPermission(addr, "listen,connect,resolve"),
                new SocketPermission("localhost:1024-", "accept"));
        IntConsumer func3 = (i) -> acceptServerSocketTest(i);

        //test for SocketPermission "229.227.226.221", "connect,accept"
        Function<String, AccessControlContext> generateAcc3 = (addr) -> getAccessControlContext(
                new SocketPermission(addr, "listen,resolve"),
                new SocketPermission("229.227.226.221", "connect,accept"));
        IntConsumer func4 = (i) -> sendDatagramPacketTest(i);
        IntConsumer func5 = (i) -> joinGroupMulticastTest(i);

        //test for SocketPermission "host:port", "listen"
        Function<String, AccessControlContext> generateAcc4 = (addr) -> getAccessControlContext(
                new SocketPermission(addr, "listen"));
        IntConsumer func6 = (i) -> listenDatagramSocketTest(i);
        IntConsumer func7 = (i) -> listenMulticastSocketTest(i);
        IntConsumer func8 = (i) -> listenServerSocketTest(i);

        return new Object[][]{
            {generateAcc1, func1},
            {generateAcc1, func2},
            {generateAcc2, func3},
            {generateAcc3, func4},
            {generateAcc3, func5},
            {generateAcc4, func6},
            {generateAcc4, func7},
            {generateAcc4, func8}
        };
    }

    @DataProvider
    public Object[][] negativeProvider() {
        IntConsumer[] funcs = {i -> connectSocketTest(i),
            i -> connectDatagramSocketTest(i), i -> acceptServerSocketTest(i),
            i -> sendDatagramPacketTest(i), i -> joinGroupMulticastTest(i),
            i -> listenDatagramSocketTest(i), i -> listenMulticastSocketTest(i),
            i -> listenServerSocketTest(i)};
        return Arrays.stream(funcs).map(f -> {
            //Construct an AccessControlContext without SocketPermission
            AccessControlContext acc = getAccessControlContext(
                    new java.io.FilePermission("<<ALL FILES>>", "read,write,execute,delete"),
                    new java.net.NetPermission("*"),
                    new java.util.PropertyPermission("*", "read,write"),
                    new java.lang.reflect.ReflectPermission("*"),
                    new java.lang.RuntimePermission("*"),
                    new java.security.SecurityPermission("*"),
                    new java.io.SerializablePermission("*"));
            return new Object[]{acc, f};
        }).toArray(Object[][]::new);
    }

    public void connectSocketTest(int port) {
        try (ServerSocket server = new ServerSocket(port);
                Socket client = new Socket(InetAddress.getLocalHost(), port);) {
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void connectDatagramSocketTest(int port) {
        String msg = "Hello";
        try {
            InetAddress me = InetAddress.getLocalHost();
            try (DatagramSocket ds = new DatagramSocket(port, me)) {
                DatagramPacket dp = new DatagramPacket(msg.getBytes(),
                        msg.length(), me, port);
                ds.send(dp);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void acceptServerSocketTest(int port) {
        try {
            InetAddress me = InetAddress.getLocalHost();
            try (ServerSocket server = new ServerSocket(port)) {
                Socket client = new Socket(me, port);
                server.accept();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void sendDatagramPacketTest(int port) {
        String msg = "Hello";
        try {
            InetAddress group = InetAddress.getByName("229.227.226.221");
            try (DatagramSocket s = new DatagramSocket(port)) {
                DatagramPacket hi = new DatagramPacket(msg.getBytes(),
                        msg.length(), group, port);
                s.send(hi);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void joinGroupMulticastTest(int port) {
        try {
            InetAddress group = InetAddress.getByName("229.227.226.221");
            try (MulticastSocket s = new MulticastSocket(port)) {
                s.joinGroup(group);
                s.leaveGroup(group);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void listenDatagramSocketTest(int port) {
        try (DatagramSocket ds = new DatagramSocket(port)) {
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void listenMulticastSocketTest(int port) {
        try (MulticastSocket ms = new MulticastSocket(port)) {
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void listenServerSocketTest(int port) {
        try (ServerSocket ms = new ServerSocket(port)) {
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static AccessControlContext getAccessControlContext(Permission... ps) {
        Permissions perms = new Permissions();
        for (Permission p : ps) {
            perms.add(p);
        }
        /*
         *Create an AccessControlContext that consist a single protection domain
         * with only the permissions calculated above
         */
        ProtectionDomain pd = new ProtectionDomain(null, perms);
        return new AccessControlContext(new ProtectionDomain[]{pd});
    }

}
