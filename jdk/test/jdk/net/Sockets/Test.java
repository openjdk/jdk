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
 * @bug 8032808 8044773
 * @modules jdk.net
 * @run main/othervm -Xcheck:jni Test success
 * @run main/othervm/policy=policy.fail -Xcheck:jni Test fail
 * @run main/othervm/policy=policy.success -Xcheck:jni Test success
 */

import java.net.*;
import java.io.IOException;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.Set;
import jdk.net.*;
import static java.lang.System.out;

public class Test {

    interface Runner { void run() throws Exception; }

    static boolean expectSuccess;

    public static void main(String[] args) throws Exception {

        // quick check to see if supportedOptions() working before
        // creating any sockets and libnet loaded

        Sockets.supportedOptions(Socket.class);

        expectSuccess = args[0].equals("success");

        // Main thing is to check for JNI problems
        // Doesn't matter if current system does not support the option
        // and currently setting the option with the loopback interface
        // doesn't work either

        boolean sm = System.getSecurityManager() != null;
        out.println("Security Manager enabled: " + sm);
        out.println("Success expected: " + expectSuccess);

        SocketFlow flowIn = SocketFlow.create()
                                      .bandwidth(1000)
                                      .priority(SocketFlow.HIGH_PRIORITY);

        try (ServerSocket ss = new ServerSocket(0);
             DatagramSocket dg = new DatagramSocket(0)) {

            int tcp_port = ss.getLocalPort();
            final InetAddress loop = InetAddress.getByName("127.0.0.1");
            final InetSocketAddress loopad = new InetSocketAddress(loop, tcp_port);

            final int udp_port = dg.getLocalPort();

            // If option not available, end test
            Set<SocketOption<?>> options = dg.supportedOptions();
            if (!options.contains(ExtendedSocketOptions.SO_FLOW_SLA)) {
                System.out.println("SO_FLOW_SLA not supported");
                return;
            }

            final Socket s = new Socket("127.0.0.1", tcp_port);
            final SocketChannel sc = SocketChannel.open();
            sc.connect(new InetSocketAddress("127.0.0.1", tcp_port));

            doTest("Sockets.setOption Socket", () -> {
                out.println(flowIn);
                Sockets.setOption(s, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
                out.println(flowIn);
            });
            doTest("Sockets.getOption Socket",() -> {
                Sockets.getOption(s, ExtendedSocketOptions.SO_FLOW_SLA);
                out.println(flowIn);
            });
            doTest("Sockets.setOption SocketChannel",() ->
                sc.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn)
            );
            doTest("Sockets.getOption SocketChannel",() ->
                sc.getOption(ExtendedSocketOptions.SO_FLOW_SLA)
            );
            doTest("Sockets.setOption DatagramSocket",() -> {
                try (DatagramSocket dg1 = new DatagramSocket(0)) {
                    dg1.connect(loop, udp_port);
                    Sockets.setOption(dg1, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
                }
            });
            doTest("Sockets.setOption DatagramSocket 2", () -> {
                try (DatagramChannel dg2 = DatagramChannel.open()) {
                    dg2.bind(new InetSocketAddress(loop, 0));
                    dg2.connect(new InetSocketAddress(loop, udp_port));
                    dg2.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
                }
            });
            doTest("Sockets.setOption MulticastSocket", () -> {
                try (MulticastSocket mc1 = new MulticastSocket(0)) {
                    mc1.connect(loop, udp_port);
                    Sockets.setOption(mc1, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
                }
            });
            doTest("Sockets.setOption AsynchronousSocketChannel", () -> {
                try (AsynchronousSocketChannel asc = AsynchronousSocketChannel.open()) {
                    Future<Void> f = asc.connect(loopad);
                    f.get();
                    asc.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
                }
            });
        }
    }

    static void doTest(String message, Runner func) throws Exception {
        out.println(message);
        try {
            func.run();
            if (expectSuccess) {
                out.println("Completed as expected");
            } else {
                throw new RuntimeException("Operation succeeded, but expected SecurityException");
            }
        } catch (SecurityException e) {
            if (expectSuccess) {
                throw new RuntimeException("Unexpected SecurityException", e);
            } else {
                out.println("Caught expected: " + e);
            }
        } catch (UnsupportedOperationException e) {
            System.out.println(e);
        } catch (IOException e) {
            // Probably a permission error, but we're not
            // going to check unless a specific permission exception
            // is defined.
            System.out.println(e);
        }
    }
}
