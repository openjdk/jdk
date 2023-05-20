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
 * @summary Basic test com.sun.management.Threads.currentThreadEnclosingScopes
 * @enablePreview
 * @run junit CurrentThreadEnclosingScopes
 */

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope.ShutdownOnFailure;
import java.util.concurrent.ThreadFactory;
import com.sun.management.Threads;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrentThreadEnclosingScopes {

    /**
     * Test thread in "root" container.
     */
    @Test
    void testRootContainer() {
        String s = Threads.currentThreadEnclosingScopes();
        assertTrue(s.isEmpty());
    }

    /**
     * Test thread started in executor service.
     */
    @Test
    void testExecutorService() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                String s = Threads.currentThreadEnclosingScopes();
                assertTrue(s.isEmpty());
            }).get();
        }
    }

    /**
     * Test thread running in StructuredTaskScope, scope owned by main thread.
     */
    @Test
    void testEnclosingScope() throws Exception {
        Thread mainThread = Thread.currentThread();
        try (var scope = new ShutdownOnFailure("duke", Thread.ofVirtual().factory())) {
            scope.fork(() -> {
                // wait for main thread to block so its stack trace is stable
                while (mainThread.getState() != Thread.State.WAITING) {
                    Thread.sleep(10);
                }

                // enclosing scope should include the main thread (as owner)
                String s = Threads.currentThreadEnclosingScopes();

                // scope name and owner expected on the first line
                String line1 = s.lines().findFirst().orElseThrow();
                assertTrue(line1.contains("duke"));
                assertTrue(line1.contains(mainThread.toString()));

                // stack trace of owner should be in the string
                for (StackTraceElement e : mainThread.getStackTrace()) {
                    assertTrue(s.contains(e.toString()));
                }

                return null;
            });
            scope.join();
            scope.throwIfFailed();
        }
    }

    /**
     * Test thread running in StructuredTaskScope, outer and inner scopes owned by main
     * thread.
     */
    @Test
    void testNestedEnclosingScopes1() throws Exception {
        Thread mainThread = Thread.currentThread();
        ThreadFactory factory = Thread.ofVirtual().factory();
        try (var scope1 = new ShutdownOnFailure("duke-outer", factory)) {
            try (var scope2 = new ShutdownOnFailure("duke-inner", factory)) {
                scope2.fork(() -> {
                    // wait for main thread to block so its stack trace is stable
                    while (mainThread.getState() != Thread.State.WAITING) {
                        Thread.sleep(10);
                    }

                    // enclosing scopes
                    String s = Threads.currentThreadEnclosingScopes();

                    // scope name and name on first line
                    String line1 = s.lines().findFirst().orElseThrow();
                    assertTrue(line1.contains("duke-inner"));
                    assertTrue(line1.contains(mainThread.toString()));

                    // string should contain name of outer scope
                    assertTrue(s.contains("duke-outer"));

                    // stack trace of owner should be in the string
                    for (StackTraceElement e : mainThread.getStackTrace()) {
                        assertTrue(s.contains(e.toString()));
                    }
                    return null;
                });
                scope2.join();
                scope2.throwIfFailed();
            }
            scope1.join();
        }
    }

    /**
     * Test thread running in StructuredTaskScope, outer scope owned by main thread,
     * inner scope owned by child thread.
     */
    @Test
    void testNestedEnclosingScopes2() throws Exception {
        Thread mainThread = Thread.currentThread();
        ThreadFactory factory = Thread.ofVirtual().factory();
        try (var scope1 = new ShutdownOnFailure("duke-outer", factory)) {
            scope1.fork(() -> {
                Thread childThread = Thread.currentThread();
                try (var scope2 = new ShutdownOnFailure("duke-inner", factory)) {
                    scope2.fork(() -> {
                        // wait for threads to blocks so stack traces are stable
                        while (mainThread.getState() != Thread.State.WAITING
                                || childThread.getState() != Thread.State.WAITING) {
                            Thread.sleep(10);
                        }

                        // enclosing scopes
                        String s = Threads.currentThreadEnclosingScopes();

                        // scope name and name on first line
                        String line1 = s.lines().findFirst().orElseThrow();
                        assertTrue(line1.contains("duke-inner"));
                        assertTrue(line1.contains(childThread.toString()));

                        // string should contain name of outer scope
                        assertTrue(s.contains("duke-outer"));

                        // stack trace of owners should be in the string
                        for (StackTraceElement e : mainThread.getStackTrace()) {
                            assertTrue(s.contains(e.toString()));
                        }
                        for (StackTraceElement e : childThread.getStackTrace()) {
                            assertTrue(s.contains(e.toString()));
                        }

                        return null;
                    });
                    scope2.join();
                    scope2.throwIfFailed();
                }
                return null;
            });
            scope1.join();
            scope1.throwIfFailed();
        }
    }
}
