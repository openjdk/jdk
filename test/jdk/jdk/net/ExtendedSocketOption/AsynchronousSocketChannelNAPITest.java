/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @modules jdk.net
 * @summary Check ExtendedSocketOption NAPI_ID support for AsynchronousSocketChannel and
 *          AsynchronousServerSocketChannel
 * @run junit ${test.main.class}
 * @run junit/othervm -Djava.net.preferIPv4Stack=true ${test.main.class}
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static jdk.net.ExtendedSocketOptions.SO_INCOMING_NAPI_ID;
import static jdk.test.lib.net.IPSupport.diagnoseConfigurationIssue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsynchronousSocketChannelNAPITest {
    private static InetAddress hostAddr;
    private static final Class<SocketException> SE = SocketException.class;
    private static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    private static final Class<UnsupportedOperationException> UOE = UnsupportedOperationException.class;

    @BeforeAll
    public static void setup() throws IOException {
        diagnoseConfigurationIssue().ifPresent(Assumptions::abort);
        try (var sc = AsynchronousSocketChannel.open();
             var ssc = AsynchronousServerSocketChannel.open()) {
            if (!sc.supportedOptions().contains(SO_INCOMING_NAPI_ID)) {
                assertThrows(UOE, () -> sc.getOption(SO_INCOMING_NAPI_ID));
                assertThrows(UOE, () -> sc.setOption(SO_INCOMING_NAPI_ID, 42));
                assertThrows(UOE, () -> sc.setOption(SO_INCOMING_NAPI_ID, null));
                assertThrows(UOE, () -> ssc.getOption(SO_INCOMING_NAPI_ID));
                assertThrows(UOE, () -> ssc.setOption(SO_INCOMING_NAPI_ID, 42));
                assertThrows(UOE, () -> ssc.setOption(SO_INCOMING_NAPI_ID, null));
                Assumptions.abort("NAPI ID not supported on this system");
            }
        }
        hostAddr = InetAddress.getLocalHost();
    }

    @Test
    public void testSetGetOptionSocketChannel() throws IOException {
        try (var sc = AsynchronousSocketChannel.open()) {
            assertEquals(0, (int) sc.getOption(SO_INCOMING_NAPI_ID));
            assertThrows(SE, () -> sc.setOption(SO_INCOMING_NAPI_ID, 42));
            assertThrows(IAE, () -> sc.setOption(SO_INCOMING_NAPI_ID, null));
        }
    }

    @Test
    public void testSetGetOptionServerSocketChannel() throws IOException {
        try (var ssc = AsynchronousServerSocketChannel.open()) {
            assertEquals(0, (int) ssc.getOption(SO_INCOMING_NAPI_ID));
            assertThrows(SE, () -> ssc.setOption(SO_INCOMING_NAPI_ID, 42));
            assertThrows(IAE, () -> ssc.setOption(SO_INCOMING_NAPI_ID, null));
        }
    }

    @Test
    public void testSocketChannel() throws Exception {
        int socketID, clientID, originalClientID = 0;
        boolean initialRun = true;
        try (var ss = AsynchronousServerSocketChannel.open()) {
            ss.bind(new InetSocketAddress(hostAddr, 0));

            try (var c = AsynchronousSocketChannel.open()) {
                c.connect(ss.getLocalAddress()).get();

                try (var s = ss.accept().get()) {
                    assertEquals(0, (int) s.getOption(SO_INCOMING_NAPI_ID));

                    for (int i = 0; i < 10; i++) {
                        s.write(ByteBuffer.wrap("test".getBytes()));

                        socketID = s.getOption(SO_INCOMING_NAPI_ID);
                        assertEquals(0, socketID, "AsynchronousSocketChannel: Sender");

                        c.read(ByteBuffer.allocate(128)).get();
                        clientID = ss.getOption(SO_INCOMING_NAPI_ID);

                        // check ID remains consistent
                        if (initialRun) {
                            assertTrue(clientID >= 0, "AsynchronousSocketChannel: Receiver");
                            initialRun = false;
                            originalClientID = clientID;
                        } else {
                            assertEquals(originalClientID, clientID);
                        }
                    }
                }
            }
        }
    }
}
