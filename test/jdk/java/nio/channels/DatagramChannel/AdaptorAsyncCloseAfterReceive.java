/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8280113
 * @summary Test async close of a DatagramSocket obtained from a DatagramChannel where
 *     the DatagramChannel's internal socket address caches are already populated
 * @enablePreview
 * @library /test/lib
 * @run junit AdaptorAsyncCloseAfterReceive
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

class AdaptorAsyncCloseAfterReceive {

    // used for scheduling socket close
    private static ScheduledExecutorService scheduler;

    @BeforeAll
    static void setup() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    static void finish() {
        scheduler.shutdown();
    }

    /**
     * Test closing a DatagramSocket, obtained from a DatagramChannel, while the main
     * thread is blocked in receive. The receive method should throw rather than
     * completing with the sender address of a previous datagram.
     */
    @ParameterizedTest
    @CsvSource({"0,0", "100,0", "0,60000", "100,60000"})
    void testReceive(int maxLength, int timeout) throws Exception {
        try (DatagramChannel dc = DatagramChannel.open()) {
            dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            populateSocketAddressCaches(dc);

            DatagramSocket s = dc.socket();
            s.setSoTimeout(timeout);

            // schedule socket to be closed while main thread blocked in receive
            Future<?> future = scheduler.schedule(() -> s.close(), 1, TimeUnit.SECONDS);
            try {
                byte[] ba = new byte[maxLength];
                DatagramPacket p = new DatagramPacket(ba, maxLength);
                assertThrows(SocketException.class, () -> s.receive(p));
            } finally {
                future.cancel(true);
            }
        }
    }

    /**
     * Send and receive a few messages to ensure that the DatagramChannel internal
     * socket address cache is populated. This setup is also done in a virtual
     * thread to ensure that the underlying socket is non-blocking.
     */
    private void populateSocketAddressCaches(DatagramChannel dc) throws Exception {
        VThreadRunner.run(() -> {
            InetSocketAddress remote = (InetSocketAddress) dc.getLocalAddress();
            if (remote.getAddress().isAnyLocalAddress()) {
                InetAddress lb = InetAddress.getLoopbackAddress();
                remote = new InetSocketAddress(lb, dc.socket().getLocalPort());
            }
            for (int i = 0; i < 2; i++) {
                ByteBuffer bb = ByteBuffer.allocate(32);
                dc.send(bb, remote);
                bb.rewind();
                dc.receive(bb);
            }
        });
    }
}
