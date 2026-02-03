/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm/timeout=480 LdapPoolTimeoutTest
 */

import org.testng.annotations.Test;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static jdk.test.lib.Utils.adjustTimeout;

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
            // launch a few concurrent connection attempts
            for (int i = 0; i < 8; i++) {
                futures.add(executorService.submit(() -> { attemptConnect(env); return null; }));
            }
        } finally {
            executorService.shutdown();
        }
        int failedCount = 0;
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get();
            } catch (ExecutionException e) {
                failedCount++;
                System.err.println("task " + (i + 1) + " failed:");
                e.getCause().printStackTrace();
            }
        }
        if (failedCount > 0)
            throw new RuntimeException(failedCount + " (sub)tests failed");
    }

    private static void attemptConnect(Hashtable<Object, Object> env) throws Exception {
        try {
            final InitialDirContext unexpectedCtx =
                    LdapTimeoutTest.assertCompletion(CONNECT_MILLIS - 1000,
                            2 * CONNECT_MILLIS + TOLERANCE,
                            () -> new InitialDirContext(env));
            throw new RuntimeException("InitialDirContext construction was expected to fail," +
                    " but returned " + unexpectedCtx);
        } catch (Throwable t) {
            final NamingException namingEx = findNamingException(t);
            if (namingEx != null) {
                // found the NamingException, verify it's the right reason
                if (namingEx.getCause() instanceof SocketTimeoutException ste) {
                    // got the expected exception
                    System.out.println("Received expected SocketTimeoutException: " + ste);
                    return;
                }
                // rely on the exception message to verify the expected exception
                final String msg = namingEx.getCause() == null
                        ? namingEx.getMessage()
                        : namingEx.getCause().getMessage();
                if (msg != null &&
                        (msg.contains("Network is unreachable")
                                || msg.contains("No route to host")
                                || msg.contains("Timed out waiting for lock")
                                || msg.contains("Connect timed out")
                                || msg.contains("Connection timed out")
                                || msg.contains("Timeout exceeded while waiting for a connection"))) {
                    // got the expected exception
                    System.out.println("Received expected NamingException with message: " + msg);
                    return;
                }
            }
            // unexpected exception, propagate it
            if (t instanceof Exception e) {
                throw e;
            } else {
                throw new Exception(t);
            }
        }
    }

    // Find and return the NamingException from the given Throwable. Returns null if none found.
    private static NamingException findNamingException(final Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof NamingException ne) {
                return ne;
            }
            cause = cause.getCause();
        }
        return null;
    }

}
