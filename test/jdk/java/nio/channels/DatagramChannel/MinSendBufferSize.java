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
 * @bug 8239355
 * @summary Check that new SO_SNDBUF limit on macOS is adhered to
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @requires os.family == "mac"
 * @run testng/othervm MinSendBufferSize
 * @run testng/othervm -Djava.net.preferIPv4Stack=true MinSendBufferSize
 */

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;

import static java.net.StandardSocketOptions.SO_SNDBUF;
import static jdk.test.lib.net.IPSupport.hasIPv4;
import static jdk.test.lib.net.IPSupport.hasIPv6;
import static jdk.test.lib.net.IPSupport.preferIPv4Stack;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class MinSendBufferSize {
    private int EXPECTED_SNDBUF;
    private DatagramChannel datagramChannel, datagramChannelIPv4,
            datagramChannelIPv6;

    private final static int IPV4_SNDBUF = 65507;
    private final static int IPV6_SNDBUF = 65527;
    private final static Class<IOException> IOE = IOException.class;

    @BeforeTest
    public void setUp() throws IOException {
        datagramChannel = DatagramChannel.open();
        if (hasIPv4())
            datagramChannelIPv4 = DatagramChannel.open(INET);
        if (hasIPv6())
            datagramChannelIPv6 = DatagramChannel.open(INET6);

        EXPECTED_SNDBUF = hasIPv6() && !preferIPv4Stack()
                ? IPV6_SNDBUF : IPV4_SNDBUF;
    }

    private void populateDataProvider(List<Object[]> testcases,
                                      DatagramChannel datagramChannel,
                                      int payloadSize,
                                      ProtocolFamily family) {

        testcases.add(new Object[]{datagramChannel, payloadSize - 1,
                family, null});
        testcases.add(new Object[]{datagramChannel, payloadSize,
                family, null});
        testcases.add(new Object[]{datagramChannel, payloadSize + 1,
                family, IOE});
    }

    @DataProvider(name = "testGetOptionProvider")
    public Object[][] providerIO() {
        var testcases = new ArrayList<Object[]>();

        testcases.add(new Object[]{datagramChannel, EXPECTED_SNDBUF});
        if (hasIPv4())
            testcases.add(new Object[]{datagramChannelIPv4, IPV4_SNDBUF});
        if (hasIPv6())
            testcases.add(new Object[]{datagramChannelIPv6, IPV6_SNDBUF});

        return testcases.toArray(Object[][]::new);
    }

    @DataProvider(name = "testSendPayloadProvider")
    public Object[][] providerIO_Payload() {
        var testcases = new ArrayList<Object[]>();

        if (hasIPv4())
            populateDataProvider(testcases, datagramChannel,
                    IPV4_SNDBUF, INET);
        if (hasIPv6() && !preferIPv4Stack())
            populateDataProvider(testcases, datagramChannel,
                    IPV6_SNDBUF, INET6);

        if (hasIPv4())
            populateDataProvider(testcases, datagramChannelIPv4,
                    IPV4_SNDBUF, INET);
        if (hasIPv6())
            populateDataProvider(testcases, datagramChannelIPv6,
                    IPV6_SNDBUF, INET6);

        return testcases.toArray(Object[][]::new);
    }

    @Test(dataProvider = "testGetOptionProvider")
    public void testGetOption(DatagramChannel channel, int sendBuf)
            throws IOException {

        assertTrue(channel.getOption(SO_SNDBUF) >= sendBuf);
    }

    @Test(dataProvider = "testSendPayloadProvider")
    public void testSend(DatagramChannel channel, int sendBuf,
                            ProtocolFamily family,
                            Class<? extends Throwable> exception)
            throws IOException {

        InetAddress targetAddress;
        assert family != null;
        if (family == INET) {
            targetAddress = InetAddress.getByName("127.0.0.1");
        } else {
            targetAddress = InetAddress.getByName("::1");
        }

        try (var receiver = new DatagramSocket(0, targetAddress)) {
            var buf = ByteBuffer.allocate(sendBuf);
            var addr = receiver.getLocalSocketAddress();
            if (exception != null) {
                expectThrows(exception, () -> channel.send(buf, addr));
            } else {
                channel.send(buf, addr);
            }
        }
    }

    @AfterTest
    public void tearDown() throws IOException {
        datagramChannel.close();
        if (hasIPv4())
            datagramChannelIPv4.close();
        if (hasIPv6())
            datagramChannelIPv6.close();
    }
}
