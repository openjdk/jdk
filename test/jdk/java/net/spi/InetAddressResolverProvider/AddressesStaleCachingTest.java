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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import impl.SimpleResolverProviderImpl;
import org.testng.Assert;
import org.testng.annotations.Test;


/*
 * @test
 * @summary Test that stale InetAddress caching security properties work as
 *          expected when a custom resolver is installed.
 * @library lib providers/simple
 * @build test.library/testlib.ResolutionRegistry
 *  simple.provider/impl.SimpleResolverProviderImpl AddressesStaleCachingTest
 * @run testng/othervm -Djava.security.properties=${test.src}/props/CacheStale.props AddressesStaleCachingTest
 */
public class AddressesStaleCachingTest {

    private static class Lookup {
        private final byte[] address;
        private final long timestamp;

        private Lookup(byte[] address, long timestamp) {
            this.address = address;
            this.timestamp = timestamp;
        }
    }

    /**
     * Validates successful and unsuccessful lookups when the stale cache is
     * enabled.
     */
    @Test
    public void testRefresh() throws Exception{
        // The first request is to save the data into the cache
        Lookup first = doLookup(false, 0);

        Thread.sleep(10000); // intentionally big delay > x2 stale property
        // The refreshTime is expired, we will do the successful lookup.
        Lookup second = doLookup(false, 0);
        Assert.assertNotEquals(first.timestamp, second.timestamp,
                               "Two lookups are expected");

        Thread.sleep(10000); // intentionally big delay > x2 stale property
        // The refreshTime is expired again, we will do the failed lookup.
        Lookup third = doLookup(true, 0);
        Assert.assertNotEquals(second.timestamp, third.timestamp,
                               "Two lookups are expected");

        // The stale cache is enabled, so we should get valid/same data for
        // all requests(even for the failed request).
        Assert.assertEquals(first.address, second.address,
                            "Same address is expected");
        Assert.assertEquals(second.address, third.address,
                            "Same address is expected");
    }

    /**
     * Validates that only one thread is blocked during "refresh", all others
     * will continue to use the "stale" data.
     */
    @Test
    public void testOnlyOneThreadIsBlockedDuringRefresh() throws Exception {
        long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        doLookup(false, timeout);
        Thread.sleep(9000);

        CountDownLatch blockServer = new CountDownLatch(1);
        SimpleResolverProviderImpl.setBlocker(blockServer);

        Thread ts[] = new Thread[10];
        CountDownLatch wait9 = new CountDownLatch(ts.length - 1);
        CountDownLatch wait10 = new CountDownLatch(ts.length);
        CountDownLatch start = new CountDownLatch(ts.length);
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                start.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                doLookup(true, timeout);
                wait9.countDown();
                wait10.countDown();
            });
        }
        for (Thread t : ts) {
            t.start();
        }
        if (!wait9.await(10, TimeUnit.SECONDS)) {
            blockServer.countDown();
            throw new RuntimeException("Some threads hang");
        }
        blockServer.countDown();
        if (!wait10.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("The last thread hangs");
        }
    }

    private static Lookup doLookup(boolean error, long timeout) {
        SimpleResolverProviderImpl.setUnreachableServer(error);
        try {
            byte[] firstAddress = InetAddress.getByName("javaTest.org").getAddress();
            long firstTimestamp = SimpleResolverProviderImpl.getLastLookupTimestamp();

            byte[] secondAddress = InetAddress.getByName("javaTest.org").getAddress();
            long secondTimestamp = SimpleResolverProviderImpl.getLastLookupTimestamp();

            Assert.assertEquals(firstAddress, secondAddress,
                                "Same address is expected");
            if (timeout == 0 || timeout - System.nanoTime() > 0) {
                Assert.assertEquals(firstTimestamp, secondTimestamp,
                        "Only one positive lookup is expected with caching enabled");
            }
            return new Lookup(firstAddress, firstTimestamp);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
