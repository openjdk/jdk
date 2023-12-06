/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8284161 8287008 8309406
 * @summary Basic test for com.sun.management.HotSpotDiagnosticMXBean.dumpThreads
 * @requires vm.continuations
 * @modules jdk.management
 * @library /test/lib
 * @run junit/othervm DumpThreads
 * @run junit/othervm -Djdk.trackAllThreads DumpThreads
 * @run junit/othervm -Djdk.trackAllThreads=true DumpThreads
 * @run junit/othervm -Djdk.trackAllThreads=false DumpThreads
 */

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.HotSpotDiagnosticMXBean.ThreadDumpFormat;
import jdk.test.lib.threaddump.ThreadDump;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class DumpThreads {
    private static boolean trackAllThreads;

    @BeforeAll
    static void setup() throws Exception {
        String s = System.getProperty("jdk.trackAllThreads");
        trackAllThreads = (s == null) || s.isEmpty() || Boolean.parseBoolean(s);
    }

    /**
     * ExecutorService implementations that have their object identity in the container
     * name so they can be found in the JSON format.
     */
    static Stream<ExecutorService> executors() {
        return Stream.of(
                Executors.newFixedThreadPool(1),
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    /**
     * Test thread dump in plain text format contains information about the current
     * thread and a virtual thread created directly with the Thread API.
     */
    @Test
    void testRootContainerPlainTextFormat() throws Exception {
        Thread vthread = Thread.ofVirtual().start(LockSupport::park);
        try {
            testDumpThreadsPlainText(vthread, trackAllThreads);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test thread dump in JSON format contains information about the current
     * thread and a virtual thread created directly with the Thread API.
     */
    @Test
    void testRootContainerJsonFormat() throws Exception {
        Thread vthread = Thread.ofVirtual().start(LockSupport::park);
        try {
            testDumpThreadsJson(null, vthread, trackAllThreads);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test thread dump in plain text format includes a thread executing a task in the
     * given ExecutorService.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testExecutorServicePlainTextFormat(ExecutorService executor) throws Exception {
        try (executor) {
            Thread thread = forkParker(executor);
            try {
                testDumpThreadsPlainText(thread, true);
            } finally {
                LockSupport.unpark(thread);
            }
        }
    }

    /**
     * Test thread dump in JSON format includes a thread executing a task in the
     * given ExecutorService.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testExecutorServiceJsonFormat(ExecutorService executor) throws Exception {
        try (executor) {
            Thread thread = forkParker(executor);
            try {
                testDumpThreadsJson(Objects.toIdentityString(executor), thread, true);
            } finally {
                LockSupport.unpark(thread);
            }
        }
    }

    /**
     * Test thread dump in JSON format includes a thread executing a task in the
     * fork-join common pool.
     */
    @Test
    void testForkJoinPool() throws Exception {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        Thread thread = forkParker(pool);
        try {
            testDumpThreadsJson("ForkJoinPool.commonPool", thread, true);
        } finally {
            LockSupport.unpark(thread);
        }
    }

    /**
     * Invoke HotSpotDiagnosticMXBean.dumpThreads to create a thread dump in plain text
     * format, then sanity check that the thread dump includes expected strings, the
     * current thread, and maybe the given thread.
     * @param thread the thread to test if included
     * @param expectInDump true if the thread is expected to be included
     */
    private void testDumpThreadsPlainText(Thread thread, boolean expectInDump) throws Exception {
        Path file = genOutputPath(".txt");
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        mbean.dumpThreads(file.toString(), ThreadDumpFormat.TEXT_PLAIN);
        System.err.format("Dumped to %s%n", file);

        // pid should be on the first line
        String line1 = line(file, 0);
        String pid = Long.toString(ProcessHandle.current().pid());
        assertTrue(line1.contains(pid));

        // timestamp should be on the second line
        String line2 = line(file, 1);
        ZonedDateTime.parse(line2);

        // runtime version should be on third line
        String line3 = line(file, 2);
        String vs = Runtime.version().toString();
        assertTrue(line3.contains(vs));

        // test if thread is included in thread dump
        assertEquals(expectInDump, isPresent(file, thread));

        // current thread should be included if platform thread or tracking all threads
        Thread currentThread = Thread.currentThread();
        boolean currentThreadExpected = trackAllThreads || !currentThread.isVirtual();
        assertEquals(currentThreadExpected, isPresent(file, currentThread));
    }

    /**
     * Invoke HotSpotDiagnosticMXBean.dumpThreads to create a thread dump in JSON format.
     * The thread dump is parsed as a JSON object and checked to ensure that it contains
     * expected data, the current thread, and maybe the given thread.
     * @param containerName the name of the container or null for the root container
     * @param thread the thread to test if included
     * @param expect true if the thread is expected to be included
     */
    private void testDumpThreadsJson(String containerName,
                                     Thread thread,
                                     boolean expectInDump) throws Exception {
        Path file = genOutputPath(".json");
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        mbean.dumpThreads(file.toString(), ThreadDumpFormat.JSON);
        System.err.format("Dumped to %s%n", file);

        // parse the JSON text
        String jsonText = Files.readString(file);
        ThreadDump threadDump = ThreadDump.parse(jsonText);

        // test threadDump/processId
        assertTrue(threadDump.processId() == ProcessHandle.current().pid());

        // test threadDump/time can be parsed
        ZonedDateTime.parse(threadDump.time());

        // test threadDump/runtimeVersion
        assertEquals(Runtime.version().toString(), threadDump.runtimeVersion());

        // test root container, has no parent and no owner
        var rootContainer = threadDump.rootThreadContainer();
        assertFalse(rootContainer.owner().isPresent());
        assertFalse(rootContainer.parent().isPresent());

        // test that the container contains the given thread
        ThreadDump.ThreadContainer container;
        if (containerName == null) {
            // root container, the thread should be found if trackAllThreads is true
            container = rootContainer;
        } else {
            // find the container
            container = threadDump.findThreadContainer(containerName).orElse(null);
            assertNotNull(container, containerName + " not found");
            assertFalse(container.owner().isPresent());
            assertTrue(container.parent().get() == rootContainer);

        }
        boolean found = container.findThread(thread.threadId()).isPresent();
        assertEquals(expectInDump, found);

        // current thread should be in root container if platform thread or tracking all threads
        Thread currentThread = Thread.currentThread();
        boolean currentThreadExpected = trackAllThreads || !currentThread.isVirtual();
        found = rootContainer.findThread(currentThread.threadId()).isPresent();
        assertEquals(currentThreadExpected, found);
    }

    /**
     * Test that dumpThreads throws if the output file already exists.
     */
    @Test
    void testFileAlreadyExsists() throws Exception {
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        String file = Files.createFile(genOutputPath("txt")).toString();
        assertThrows(FileAlreadyExistsException.class,
                () -> mbean.dumpThreads(file, ThreadDumpFormat.TEXT_PLAIN));
        assertThrows(FileAlreadyExistsException.class,
                () -> mbean.dumpThreads(file, ThreadDumpFormat.JSON));
    }

    /**
     * Test that dumpThreads throws if the file path is relative.
     */
    @Test
    void testRelativePath() throws Exception {
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        assertThrows(IllegalArgumentException.class,
                () -> mbean.dumpThreads("threads.txt", ThreadDumpFormat.TEXT_PLAIN));
        assertThrows(IllegalArgumentException.class,
                () -> mbean.dumpThreads("threads.json", ThreadDumpFormat.JSON));
    }

    /**
     * Test that dumpThreads throws with null parameters.
     */
    @Test
    void testNull() throws Exception {
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        assertThrows(NullPointerException.class,
                () -> mbean.dumpThreads(null, ThreadDumpFormat.TEXT_PLAIN));
        assertThrows(NullPointerException.class,
                () -> mbean.dumpThreads(genOutputPath("txt").toString(), null));
    }

    /**
     * Submits a parking task to the given executor, returns the Thread object of
     * the parked thread.
     */
    private static Thread forkParker(ExecutorService executor) {
        class Box { static volatile Thread thread;}
        var latch = new CountDownLatch(1);
        executor.submit(() -> {
            Box.thread = Thread.currentThread();
            latch.countDown();
            LockSupport.park();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Box.thread;
    }

    /**
     * Returns true if a Thread is present in a plain text thread dump.
     */
    private static boolean isPresent(Path file, Thread thread) throws Exception {
        String expect = "#" + thread.threadId();
        return count(file, expect) > 0;
    }

    /**
     * Generate a file path with the given suffix to use as an output file.
     */
    private static Path genOutputPath(String suffix) throws Exception {
        Path dir = Path.of(".").toAbsolutePath();
        Path file = Files.createTempFile(dir, "dump", suffix);
        Files.delete(file);
        return file;
    }

    /**
     * Return the count of the number of files in the given file that contain
     * the given character sequence.
     */
    static long count(Path file, CharSequence cs) throws Exception {
        try (Stream<String> stream = Files.lines(file)) {
            return stream.filter(line -> line.contains(cs)).count();
        }
    }

    /**
     * Return line $n of the given file.
     */
    private String line(Path file, long n) throws Exception {
        try (Stream<String> stream = Files.lines(file)) {
            return stream.skip(n).findFirst().orElseThrow();
        }
    }
}
