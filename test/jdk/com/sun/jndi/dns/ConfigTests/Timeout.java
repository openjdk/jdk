/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.directory.InitialDirContext;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;

import jdk.test.lib.net.URIBuilder;

/*
 * @test
 * @bug 8200151 8265309
 * @summary Tests that we can set the initial UDP timeout interval and the
 *          number of retries.
 * @library ../lib/ /test/lib
 * @modules java.base/sun.security.util
 * @run main/othervm Timeout
 */

public class Timeout extends DNSTestBase {
    // initial timeout = 1/4 sec
    private static final int TIMEOUT = 250;
    // try 5 times per server
    private static final int RETRIES = 5;
    // DnsClient retries again with increased timeout if left
    // timeout is less than this value, and max retry attempts
    // is not reached
    private static final int DNS_CLIENT_MIN_TIMEOUT = 0;

    private long startTime;

    public Timeout() {
        setLocalServer(false);
    }

    public static void main(String[] args) throws Exception {
        new Timeout().run(args);
    }

    /*
     * Tests that we can set the initial UDP timeout interval and the
     * number of retries.
     */
    @Override
    public void runTest() throws Exception {
        // Create a DatagramSocket and bind it to the loopback address to simulate
        // UDP DNS server that doesn't respond
        try (DatagramSocket ds = new DatagramSocket(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            String allQuietUrl = URIBuilder.newBuilder()
                    .scheme("dns")
                    .loopback()
                    .port(ds.getLocalPort())
                    .build()
                    .toString();
            env().put(Context.PROVIDER_URL, allQuietUrl);
            env().put("com.sun.jndi.dns.timeout.initial", String.valueOf(TIMEOUT));
            env().put("com.sun.jndi.dns.timeout.retries", String.valueOf(RETRIES));
            setContext(new InitialDirContext(env()));

            // Any request should fail after timeouts have expired.
            startTime = System.nanoTime();
            context().getAttributes("");

            throw new RuntimeException(
                    "Failed: getAttributes succeeded unexpectedly");
        }
    }

    @Override
    public boolean handleException(Exception e) {
        if (e instanceof CommunicationException) {
            Duration elapsedTime = Duration.ofNanos(System.nanoTime() - startTime);
            if (!(((CommunicationException) e)
                    .getRootCause() instanceof SocketTimeoutException)) {
                return false;
            }

            Duration minAllowedTime = Duration.ofMillis(TIMEOUT)
                    .multipliedBy((1 << RETRIES) - 1)
                    .minus(Duration.ofMillis(DNS_CLIENT_MIN_TIMEOUT * RETRIES));
            Duration maxAllowedTime = Duration.ofMillis(TIMEOUT)
                    .multipliedBy((1 << RETRIES) - 1)
                    // max allowed timeout value is set to 2 * expected timeout
                    .multipliedBy(2);

            DNSTestUtils.debug("Elapsed (ms):  " + elapsedTime.toMillis());
            String expectedRangeMsg = "%s - %s"
                    .formatted(minAllowedTime.toMillis(), maxAllowedTime.toMillis());
            DNSTestUtils.debug("Expected range (ms): " + expectedRangeMsg);

            // Check that elapsed time is as long as expected, and
            // not more than 2 times greater.
            if (elapsedTime.compareTo(minAllowedTime) >= 0 &&
                elapsedTime.compareTo(maxAllowedTime) <= 0) {
                System.out.println("elapsed time is as long as expected.");
                return true;
            }
            throw new RuntimeException(
                    "Failed: timeout in " + elapsedTime.toMillis() +
                    " ms, expected to be in a range (ms): " + expectedRangeMsg);
        }

        return super.handleException(e);
    }
}
