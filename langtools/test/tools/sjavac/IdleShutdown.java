/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8044131
 * @summary Tests the hooks used for detecting idleness of the sjavac server.
 * @build Wrapper
 * @run main Wrapper IdleShutdown
 */
import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.tools.sjavac.server.CompilationResult;
import com.sun.tools.sjavac.server.IdleResetSjavac;
import com.sun.tools.sjavac.server.Sjavac;
import com.sun.tools.sjavac.server.SysInfo;
import com.sun.tools.sjavac.server.Terminable;


public class IdleShutdown {

    final static long TEST_START = System.currentTimeMillis();
    final static long TIMEOUT_MS = 3000;

    public static void main(String[] args) throws InterruptedException {

        final AtomicLong timeoutTimestamp = new AtomicLong(-1);

        log("Starting IdleCallbackJavacService with timeout: " + TIMEOUT_MS);
        Sjavac service = new IdleResetSjavac(
                new NoopJavacService(),
                new Terminable() {
                    public void shutdown(String msg) {
                        // Record the idle timeout time
                        log("Timeout detected");
                        timeoutTimestamp.set(System.currentTimeMillis());
                    }
                },
                TIMEOUT_MS);

        // Make sure it didn't timeout immediately
        if (timeoutTimestamp.get() != -1)
            throw new AssertionError("Premature timeout detected.");

        // Call various methods and wait less than TIMEOUT_MS in between
        Thread.sleep(TIMEOUT_MS - 1000);
        log("Getting sys info");
        service.getSysInfo();

        Thread.sleep(TIMEOUT_MS - 1000);
        log("Getting sys info");
        service.getSysInfo();

        if (timeoutTimestamp.get() != -1)
            throw new AssertionError("Premature timeout detected.");

        Thread.sleep(TIMEOUT_MS - 1000);
        log("Compiling");
        service.compile("",
                        "",
                        new String[0],
                        Collections.<File>emptyList(),
                        Collections.<URI>emptySet(),
                        Collections.<URI>emptySet());

        Thread.sleep(TIMEOUT_MS - 1000);
        log("Compiling");
        service.compile("",
                        "",
                        new String[0],
                        Collections.<File>emptyList(),
                        Collections.<URI>emptySet(),
                        Collections.<URI>emptySet());

        if (timeoutTimestamp.get() != -1)
            throw new AssertionError("Premature timeout detected.");

        long expectedTimeout = System.currentTimeMillis() + TIMEOUT_MS;

        // Wait for actual timeout
        log("Awaiting idle timeout");
        Thread.sleep(TIMEOUT_MS + 1000);

        // Check result
        if (timeoutTimestamp.get() == -1)
            throw new AssertionError("Timeout never occurred");

        long error = Math.abs(expectedTimeout - timeoutTimestamp.get());
        log("Timeout error: " + error + " ms");
        if (error > TIMEOUT_MS * .1)
            throw new AssertionError("Error too big");

        log("Shutting down");
        service.shutdown();
    }

    private static void log(String msg) {
        long logTime = System.currentTimeMillis() - TEST_START;
        System.out.printf("After %5d ms: %s%n", logTime, msg);
    }

    private static class NoopJavacService implements Sjavac {
        @Override
        public SysInfo getSysInfo() {
            // Attempt to trigger idle timeout during a call by sleeping
            try {
                Thread.sleep(TIMEOUT_MS + 1000);
            } catch (InterruptedException e) {
            }
            return null;
        }
        @Override
        public void shutdown() {
        }
        @Override
        public CompilationResult compile(String protocolId,
                                         String invocationId,
                                         String[] args,
                                         List<File> explicitSources,
                                         Set<URI> sourcesToCompile,
                                         Set<URI> visibleSources) {
            return null;
        }
        @Override
        public String serverSettings() {
            return "";
        }
    }
}
