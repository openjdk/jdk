/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @build jdk.test.lib.OSVersion jdk.test.lib.Platform
 * @run main/othervm -Xcheck:jni Test success
 * @run main/othervm/policy=policy.fail -Xcheck:jni Test fail
 * @run main/othervm/policy=policy.success -Xcheck:jni Test success
 */

import jdk.net.SocketFlow;
import jdk.net.Sockets;
import jdk.test.lib.Platform;
import jdk.test.lib.OSVersion;

import java.io.IOException;
import java.net.*;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Future;

import static java.lang.System.out;
import static jdk.net.ExtendedSocketOptions.SO_FLOW_SLA;

public class Test {

    interface Runner { void run() throws Exception; }

    static boolean expectSuccess;
    private static final boolean expectSupport = checkExpectedOptionSupport();

    public static void main(String[] args) throws Exception {

        // quick check to see if supportedOptions() working before
        // creating any sockets and libnet loaded

        Sockets.supportedOptions(Socket.class);

        expectSuccess = args[0].equals("success");

        // Main thing is to check for JNI problems
        // Doesn't matter if currently setting the option with the loopback
        // interface doesn't work

        boolean sm = System.getSecurityManager() != null;
        out.println("Security Manager enabled: " + sm);
        out.println("Success expected: " + expectSuccess);

        SocketFlow flowIn = SocketFlow.create()
                                      .bandwidth(1000)
                                      .priority(SocketFlow.HIGH_PRIORITY);

        try (ServerSocket ss = new ServerSocket(0);
             DatagramSocket dg = new DatagramSocket(0)) {

            int tcp_port = ss.getLocalPort();
            final InetAddress loop = InetAddress.getLoopbackAddress();
            final InetSocketAddress loopad = new InetSocketAddress(loop, tcp_port);

            final int udp_port = dg.getLocalPort();

            final Socket s = new Socket(loop, tcp_port);
            final SocketChannel sc = SocketChannel.open();
            sc.connect(new InetSocketAddress(loop, tcp_port));

            doTest("Sockets.setOption Socket", () -> {
                out.println(flowIn);
                if (s.supportedOptions().contains(SO_FLOW_SLA) != expectSupport) {
                    throw new RuntimeException("Unexpected supportedOptions()");
                }
                Sockets.setOption(s, SO_FLOW_SLA, flowIn);
                out.println(flowIn);
            });

            doTest("Sockets.getOption Socket", () -> {
                Sockets.getOption(s, SO_FLOW_SLA);
                out.println(flowIn);
            });

            doTest("Sockets.setOption SocketChannel", () -> {
                if (sc.supportedOptions().contains(SO_FLOW_SLA) != expectSupport) {
                    throw new RuntimeException("Unexpected supportedOptions()");
                }
                sc.setOption(SO_FLOW_SLA, flowIn);
            });
            doTest("Sockets.getOption SocketChannel", () ->
                    sc.getOption(SO_FLOW_SLA)
            );
            doTest("Sockets.setOption DatagramSocket", () -> {
                try (DatagramSocket dg1 = new DatagramSocket(0)) {
                    if (dg1.supportedOptions().contains(SO_FLOW_SLA) != expectSupport) {
                        throw new RuntimeException("Unexpected supportedOptions()");
                    }

                    dg1.connect(loop, udp_port);
                    Sockets.setOption(dg1, SO_FLOW_SLA, flowIn);
                }
            });
            doTest("Sockets.setOption DatagramSocket 2", () -> {
                try (DatagramChannel dg2 = DatagramChannel.open()) {
                    if (dg2.supportedOptions().contains(SO_FLOW_SLA) != expectSupport) {
                        throw new RuntimeException("Unexpected supportedOptions()");
                    }
                    dg2.bind(new InetSocketAddress(loop, 0));
                    dg2.connect(new InetSocketAddress(loop, udp_port));
                    dg2.setOption(SO_FLOW_SLA, flowIn);
                }
            });
            doTest("Sockets.setOption MulticastSocket", () -> {
                try (MulticastSocket mc1 = new MulticastSocket(0)) {
                    if (mc1.supportedOptions().contains(SO_FLOW_SLA) != expectSupport) {
                        throw new RuntimeException("Unexpected supportedOptions()");
                    }
                    mc1.connect(loop, udp_port);
                    Sockets.setOption(mc1, SO_FLOW_SLA, flowIn);
                }
            });
            doTest("Sockets.setOption AsynchronousSocketChannel", () -> {
                try (AsynchronousSocketChannel asc = AsynchronousSocketChannel.open()) {
                    if (asc.supportedOptions().contains(SO_FLOW_SLA) != expectSupport) {
                        throw new RuntimeException("Unexpected supportedOptions()");
                    }
                    Future<Void> f = asc.connect(loopad);
                    f.get();
                    asc.setOption(SO_FLOW_SLA, flowIn);
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
                return;
            }
        } catch (UnsupportedOperationException e) {
            if (expectSupport) {
                throw new RuntimeException("Test failed: " +
                        "unexpected UnsupportedOperationException");
            }
            out.println("UnsupportedOperationException as expected");
            return;
        } catch (IOException e) {
            // Probably a permission error, but we're not
            // going to check unless a specific permission exception
            // is defined.
            System.out.println(e);
        }
        if (!expectSupport) {
            throw new RuntimeException("Test failed: " +
                    "UnsupportedOperationException was not thrown");
        }
    }

    private static boolean checkExpectedOptionSupport() {
        if (Platform.isSolaris()) {
            OSVersion solarisVersion = OSVersion.current();
            OSVersion solarisVersionToCheck = new OSVersion(11, 2);
            if (solarisVersion.compareTo(solarisVersionToCheck) >= 0) {
                System.out.println("This Solaris version (" + solarisVersion
                        + ") should support SO_FLOW_SLA option");
                return true;
            } else {
                System.out.println("This Solaris version (" + solarisVersion
                        + ") should not support SO_FLOW_SLA option");
            }
        } else {
            System.out.println("Not Solaris, SO_FLOW_SLA should not be " +
                    "supported");
        }
        return false;
    }

}
