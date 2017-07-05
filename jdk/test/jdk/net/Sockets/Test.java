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
 * @bug 8032808
 * @run main/othervm -Xcheck:jni Test
 * @run main/othervm/policy=policy.fail -Xcheck:jni Test fail
 * @run main/othervm/policy=policy.success -Xcheck:jni Test success
 */

import java.net.*;
import java.io.IOException;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.Set;
import jdk.net.*;

public class Test {

    static boolean security;
    static boolean success;

    interface Runner {
        public void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {

        // quick check to see if supportedOptions() working before
        // creating any sockets and libnet loaded

        Sockets.supportedOptions(Socket.class);

        security = System.getSecurityManager() != null;
        success = security && args[0].equals("success");

        // Main thing is to check for JNI problems
        // Doesn't matter if current system does not support the option
        // and currently setting the option with the loopback interface
        // doesn't work either

        System.out.println ("Security Manager enabled: " + security);
        if (security) {
            System.out.println ("Success expected: " + success);
        }

        final SocketFlow flowIn = SocketFlow.create()
            .bandwidth(1000)
            .priority(SocketFlow.HIGH_PRIORITY);

        ServerSocket ss = new ServerSocket(0);
        int tcp_port = ss.getLocalPort();
        final InetAddress loop = InetAddress.getByName("127.0.0.1");
        final InetSocketAddress loopad = new InetSocketAddress(loop, tcp_port);

        DatagramSocket dg = new DatagramSocket(0);
        final int udp_port = dg.getLocalPort();

        // If option not available, end test
        Set<SocketOption<?>> options = dg.supportedOptions();
        if (!options.contains(ExtendedSocketOptions.SO_FLOW_SLA)) {
            System.out.println("SO_FLOW_SLA not supported");
            return;
        }

        final Socket s = new Socket("127.0.0.1", tcp_port);
        final SocketChannel sc = SocketChannel.open();
        sc.connect (new InetSocketAddress("127.0.0.1", tcp_port));

        doTest(()->{
            Sockets.setOption(s, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
        });
        doTest(()->{
            Sockets.getOption(s, ExtendedSocketOptions.SO_FLOW_SLA);
        });
        doTest(()->{
            sc.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
        });
        doTest(()->{
            sc.getOption(ExtendedSocketOptions.SO_FLOW_SLA);
        });
        doTest(()->{
            DatagramSocket dg1 = new DatagramSocket(0);
            dg1.connect(loop, udp_port);
            Sockets.setOption(dg1, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
        });
        doTest(()->{
            DatagramChannel dg2 = DatagramChannel.open();
            dg2.bind(new InetSocketAddress(loop, 0));
            dg2.connect(new InetSocketAddress(loop, udp_port));
            dg2.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
        });
        doTest(()->{
            MulticastSocket mc1 = new MulticastSocket(0);
            mc1.connect(loop, udp_port);
            Sockets.setOption(mc1, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
        });
        doTest(()->{
            AsynchronousSocketChannel asc = AsynchronousSocketChannel.open();
            Future<Void> f = asc.connect(loopad);
            f.get();
            asc.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
        });
    }

    static void doTest(Runner func) throws Exception {
        try {
            func.run();
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {
            System.out.println (e);
        } catch (IOException e) {
            // Probably a permission error, but we're not
            // going to check unless a specific permission exception
            // is defined.
            System.out.println (e);
        }
    }
}
