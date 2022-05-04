/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8277795
 * @summary Multi-threaded client timeout tests for ldap pool
 * @library /test/lib
 *          lib/
 * @run testng/othervm LdapPoolTimeoutTest
 */

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import static jdk.test.lib.Utils.adjustTimeout;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class LdapPoolTimeoutTest {
    /*
     * Practical representation of an infinite timeout.
     */
    private static final long INFINITY_MILLIS = adjustTimeout(20_000);
    /*
     * The acceptable variation in timeout measurements.
     */
    private static final long TOLERANCE       = adjustTimeout( 3_500);

    private static final long CONNECT_MILLIS  = adjustTimeout( 3_000);
    private static final long READ_MILLIS     = adjustTimeout(10_000);

    static {
        // a series of checks to make sure this timeouts configuration is
        // consistent and the timeouts do not overlap

        assert (TOLERANCE >= 0);
        // context creation
        assert (2 * CONNECT_MILLIS + TOLERANCE < READ_MILLIS);
        // context creation immediately followed by search
        assert (2 * CONNECT_MILLIS + READ_MILLIS + TOLERANCE < INFINITY_MILLIS);
    }

    @Test
    public void test() throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newCachedThreadPool();

        Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(READ_MILLIS));
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(CONNECT_MILLIS));
        env.put("com.sun.jndi.ldap.connect.pool", "true");
        env.put(Context.PROVIDER_URL, "ldap://example.com:1234");

        try {
            futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
            futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
            futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
            futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
            futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
            futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
            futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
            futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
        } finally {
            executorService.shutdown();
        }
        int failedCount = 0;
        for (var f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                failedCount++;
                e.getCause().printStackTrace(System.out);
            }
        }
        if (failedCount > 0)
            throw new RuntimeException(failedCount + " (sub)tests failed");
    }

    private static void attemptConnect(Hashtable<Object, Object> env) throws Exception {
        try {
            LdapTimeoutTest.assertCompletion(CONNECT_MILLIS - 1000,
                   2 * CONNECT_MILLIS + TOLERANCE,
                   () -> new InitialDirContext(env));
        } catch (RuntimeException e) {
            String msg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            System.err.println("MSG RTE: " + msg);
            // assertCompletion may wrap a CommunicationException in an RTE
            assertTrue(msg != null && msg.contains("Network is unreachable"));
        } catch (NamingException ex) {
            String msg = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            System.err.println("MSG: " + msg);
            assertTrue(msg != null &&
                    (msg.contains("Network is unreachable")
                        || msg.contains("Timed out waiting for lock")
                        || msg.contains("Connect timed out")
                        || msg.contains("Timeout exceeded while waiting for a connection")));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}

