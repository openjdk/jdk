/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284199 8296779 8306647
 * @summary Test thread dumps with StructuredTaskScope
 * @enablePreview
 * @library /test/lib
 * @run junit/othervm StructuredThreadDumpTest
 */

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.HotSpotDiagnosticMXBean.ThreadDumpFormat;
import jdk.test.lib.threaddump.ThreadDump;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructuredThreadDumpTest {

    /**
     * Test that a thread dump with a tree of task scopes contains a thread grouping for
     * each task scope.
     */
    @Test
    void testTree() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withName("scope"))) {
            Thread thread1 = fork(scope, "child-scope-A");
            Thread thread2 = fork(scope, "child-scope-B");
            try {
                ThreadDump threadDump = threadDump();

                // thread dump should have a thread container for each scope
                var rootContainer = threadDump.rootThreadContainer();
                var container1 = threadDump.findThreadContainer("scope").orElseThrow();
                var container2 = threadDump.findThreadContainer("child-scope-A").orElseThrow();
                var container3 = threadDump.findThreadContainer("child-scope-B").orElseThrow();

                // check parents
                assertFalse(rootContainer.parent().isPresent());
                assertTrue(container1.parent().get() == rootContainer);
                assertTrue(container2.parent().get() == container1);
                assertTrue(container3.parent().get() == container1);

                // check owners
                assertFalse(rootContainer.owner().isPresent());
                assertTrue(container1.owner().getAsLong() == Thread.currentThread().threadId());
                assertTrue(container2.owner().getAsLong() == thread1.threadId());
                assertTrue(container3.owner().getAsLong() == thread2.threadId());

                // thread1 and threads2 should be in threads array of "scope"
                container1.findThread(thread1.threadId()).orElseThrow();
                container1.findThread(thread2.threadId()).orElseThrow();

            } finally {
                LockSupport.unpark(thread1);
                LockSupport.unpark(thread2);
                scope.join();
            }
        }
    }

    /**
     * Test that a thread dump with nested tasks scopes contains a thread grouping for
     * each task scope.
     */
    @Test
    void testNested() throws Exception {
        try (var scope1 = StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withName("scope-A"))) {
            Thread thread1 = fork(scope1);

            try (var scope2 = StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withName("scope-B"))) {
                Thread thread2 = fork(scope2);
                try {
                    ThreadDump threadDump = threadDump();

                    // thread dump should have a thread container for both scopes
                    var rootContainer = threadDump.rootThreadContainer();
                    var container1 = threadDump.findThreadContainer("scope-A").orElseThrow();
                    var container2 = threadDump.findThreadContainer("scope-B").orElseThrow();

                    // check parents
                    assertFalse(rootContainer.parent().isPresent());
                    assertTrue(container1.parent().get() == rootContainer);
                    assertTrue(container2.parent().get() == container1);

                    // check owners
                    long tid = Thread.currentThread().threadId();
                    assertFalse(rootContainer.owner().isPresent());
                    assertTrue(container1.owner().getAsLong() == tid);
                    assertTrue(container2.owner().getAsLong() == tid);

                    // thread1 should be in threads array of "scope-A"
                    container1.findThread(thread1.threadId()).orElseThrow();

                    // thread2 should be in threads array of "scope-B"
                    container2.findThread(thread2.threadId()).orElseThrow();

                } finally {
                    LockSupport.unpark(thread2);
                    scope2.join();
                }
            } finally {
                LockSupport.unpark(thread1);
                scope1.join();
            }
        }
    }

    /**
     * Generates a JSON formatted thread dump to a temporary file, prints it to standard
     * output, parses the JSON text and returns a ThreadDump object for the thread dump.
     */
    private static ThreadDump threadDump() throws IOException {
        Path dir = Path.of(".").toAbsolutePath();
        Path file = Files.createTempFile(dir, "threadump", "json");
        Files.delete(file);
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                .dumpThreads(file.toString(), ThreadDumpFormat.JSON);

        try (Stream<String> stream = Files.lines(file)) {
            stream.forEach(System.out::println);
        }

        String jsonText = Files.readString(file);
        return ThreadDump.parse(jsonText);
    }

    /**
     * Forks a subtask in the given scope that parks, returning the Thread that executes
     * the subtask.
     */
    private static Thread fork(StructuredTaskScope<Object, Void> scope) throws Exception {
        var ref = new AtomicReference<Thread>();
        scope.fork(() -> {
            ref.set(Thread.currentThread());
            LockSupport.park();
            return null;
        });
        Thread thread;
        while ((thread = ref.get()) == null) {
            Thread.sleep(10);
        }
        return thread;
    }

    /**
     * Forks a subtask in the given scope. The subtask creates a new child scope with
     * the given name, then parks. This method returns Thread that executes the subtask.
     */
    private static Thread fork(StructuredTaskScope<Object, Void> scope,
                               String childScopeName) throws Exception {
        var ref = new AtomicReference<Thread>();
        scope.fork(() -> {
            try (var childScope = StructuredTaskScope.open(Joiner.awaitAll(),
                    cf -> cf.withName(childScopeName))) {
                ref.set(Thread.currentThread());
                LockSupport.park();
            }
        });
        Thread thread;
        while ((thread = ref.get()) == null) {
            Thread.sleep(10);
        }
        return thread;
    }

}
