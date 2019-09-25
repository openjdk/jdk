/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

/*
 * Make sure that System.inheritedChannel returns null when given a UNIX domain socket
 */

public class UnixDomainChannelTest {

    public static class Child {
        public static void main(String[] args) throws Exception {
            // we just want to make sure that System.inheritedChannel either
            // returns a connected channel, or null if it is given a listener
            Channel channel = System.inheritedChannel();
            String result = channel == null ? "N" : "Y";
            if (args[0].equals("test1") || args[0].equals("test2")) {
                // socket is writeable
                ByteChannel bc = (ByteChannel)channel;
                ByteBuffer buf = ByteBuffer.wrap(result.getBytes(ISO_8859_1));
                bc.write(buf);
            } else { // test3
                // in this case the socket is a listener
                // we can't write to it. So, use UnixDatagramSocket
                // to accept a writeable socket
                UnixDomainSocket listener = new UnixDomainSocket(0); // fd 0
                UnixDomainSocket sock = listener.accept();
                sock.write((int)result.charAt(0));
            }
        }
    }

    static boolean passed = true;

    public static void main(String args[]) throws Exception {
        test1();
        test2();
        test3();
        if (!passed)
            throw new RuntimeException();
    }

    private static void closeAll(UnixDomainSocket... sockets) {
        for (UnixDomainSocket sock : sockets) {
            sock.close();
        }
    }

    // Test with a named connected socket
    private static void test1() throws Exception {
        UnixDomainSocket listener = new UnixDomainSocket();
        listener.bind("foo.socket");
        UnixDomainSocket sock1 = new UnixDomainSocket();
        sock1.connect("foo.socket");
        UnixDomainSocket sock2 = listener.accept();

        Launcher.launchWithUnixDomainSocket("UnixDomainChannelTest$Child", sock2, "test1");
        int c = sock1.read();
        if (c != 'Y') {
            System.err.printf("test1: failed %d d\n", c );
            passed = false;
        }
        closeAll(listener, sock1, sock2);
    }

    // Test with unnamed socketpair
    private static void test2() throws Exception {
        UnixDomainSocket[] pair = UnixDomainSocket.socketpair();
        System.out.println("test2: launching child");
        Launcher.launchWithUnixDomainSocket("UnixDomainChannelTest$Child", pair[0], "test2");
        if (pair[1].read() != 'Y') {
            System.err.println("test2: failed");
            passed = false;
        }
        closeAll(pair[0], pair[1]);
    }

    // Test with a named listener
    private static void test3() throws Exception {
        UnixDomainSocket listener = new UnixDomainSocket();
        listener.bind("foo.socket");
        UnixDomainSocket sock1 = new UnixDomainSocket();
        System.out.println("test3: launching child");
        Launcher.launchWithUnixDomainSocket("UnixDomainChannelTest$Child", listener, "test3");
        sock1.connect("foo.socket");
        if (sock1.read() != 'N') {
            System.err.println("test3: failed");
            passed = false;
        }
        closeAll(listener, sock1);
    }

}
