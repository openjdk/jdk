/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8243099
 * @modules jdk.net
 * @run main/othervm DontFragmentTest ipv4
 * @run main/othervm DontFragmentTest ipv6
 */

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.stream.*;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.net.ExtendedSocketOptions.IP_DONTFRAGMENT;

public class DontFragmentTest {

   static int getMTU(NetworkInterface nif) {
        try {
            return nif.getMTU();
        } catch (SocketException e) {
            return -1;
        }
    }

    static ByteBuffer read(Selector sel, DatagramChannel chan, boolean succeed) throws Exception {
        int n = sel.select(1000);
        ByteBuffer b = ByteBuffer.allocate(32 * 1024);
        System.out.println("n = " + n);
        if (n > 0)
            sel.selectedKeys().clear();
        if (succeed && n == 0)
            throw new RuntimeException("select timedout");
        if (!succeed && n != 0) {
            var a = chan.receive(b);
            if (a != null) {
                b.flip();
                System.out.printf("ERROR: read %d bytes\n", b.remaining());
            }
            throw new RuntimeException("select should have timedout");
        }
        if (!succeed)
            return null;
        try {
            var a = chan.receive(b);
            if (a == null)
                throw new RuntimeException("expected to read data");
            b.flip();
            System.out.printf("Read %d bytes\n", b.remaining());
            return b;
        } catch (IOException e) {
            if (succeed)
                throw e;
            return null;
        }
    }

    static void write(ByteBuffer buf, DatagramChannel chan, boolean succeed) throws IOException {
        System.out.printf("write %d bytes\n", buf.remaining());
        try {
            chan.write(buf);
        } catch (IOException e) {
            if (succeed)
                throw e;
            System.out.println("WRITE exception: " + e.toString());
        } finally {
            buf.clear(); // reset pointers so can be written again
        }
    }

    public static void main(String[] args) throws Exception {
        Selector sel = Selector.open();
        StandardProtocolFamily fam = args[0].equals("ipv4") ? INET : INET6;
        System.out.println("Family = " + fam);
        DatagramChannel c1 = DatagramChannel.open(fam);
        c1.bind(new InetSocketAddress(5555));
        c1.configureBlocking(false);
        c1.register(sel, SelectionKey.OP_READ, null);

        int port = ((InetSocketAddress)(c1.getLocalAddress())).getPort();
        //InetAddress iaddr = getLocalAddress(fam);
        InetAddress iaddr = fam == INET ? InetAddress.getByName("127.0.0.1")
                                        : InetAddress.getByName("::1");
        System.out.println("Local address: " + iaddr);
        InetSocketAddress addr = new InetSocketAddress(iaddr, port);
        NetworkInterface nif = NetworkInterface.getByInetAddress(iaddr);
        int mtu = nif.getMTU();
        System.out.println("MTU is " + mtu);
        assert mtu > 0;
        int large_size = mtu + 20;
        int small_size = mtu - 80;
        ByteBuffer small = ByteBuffer.allocate(small_size);
        ByteBuffer large = ByteBuffer.allocate(large_size);

        DatagramChannel c2 = DatagramChannel.open(fam);
        c2.bind(new InetSocketAddress(iaddr, 0));
        c2.connect(addr);
        System.out.println("Connected to " + addr);

        // large buffer should succeed, before option set
        write(large, c2, true);
        read(sel, c1, true);

        if (c2.getOption(IP_DONTFRAGMENT)) {
            throw new RuntimeException("IP_DONTFRAGMENT should not be set");
        }
        c2.setOption(IP_DONTFRAGMENT, true);
        if (!c2.getOption(IP_DONTFRAGMENT)) {
            throw new RuntimeException("IP_DONTFRAGMENT should be set");
        }
        // small buffer should succeed, after option set
        //write(ByteBuffer.allocate(16000), c2);
        write(small, c2, true);
        read(sel, c1, true);

        // large buffer should fail, after option set
        write(large, c2, false);
        read(sel, c1, false);
    }
}
