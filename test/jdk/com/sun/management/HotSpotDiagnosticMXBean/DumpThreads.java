/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284161 8287008 8309406 8356870
 * @summary Basic test for com.sun.management.HotSpotDiagnosticMXBean.dumpThreads
 * @requires vm.continuations
 * @modules java.base/jdk.internal.vm jdk.management
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run junit/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *         --enable-native-access=ALL-UNNAMED DumpThreads
 * @run junit/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *         --enable-native-access=ALL-UNNAMED -Djdk.trackAllThreads DumpThreads
 * @run junit/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *        --enable-native-access=ALL-UNNAMED -Djdk.trackAllThreads=true DumpThreads
 * @run junit/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *        --enable-native-access=ALL-UNNAMED -Djdk.trackAllThreads=false DumpThreads
 */

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.HotSpotDiagnosticMXBean.ThreadDumpFormat;
import jdk.test.lib.threaddump.ThreadDump;
import jdk.test.lib.thread.VThreadPinner;
import jdk.test.lib.thread.VThreadRunner;
import jdk.test.whitebox.WhiteBox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class DumpThreads {
    private static boolean trackAllThreads;

    @BeforeAll
    static void setup() throws Exception {
        String s = System.getProperty("jdk.trackAllThreads");
        trackAllThreads = (s == null) || s.isEmpty() || Boolean.parseBoolean(s);

        // need >=2 carriers for testing pinning
        VThreadRunner.ensureParallelism(2);
    }

    /**
     * Test thread dump in plain text format.
     */
    @Test
    void testPlainText() throws Exception {
        List<String> lines = dumpThreadsToPlainText();

        // pid should be on the first line
        String pid = Long.toString(ProcessHandle.current().pid());
        assertEquals(pid, lines.get(0));

        // timestamp should be on the second line
        String secondLine = lines.get(1);
        ZonedDateTime.parse(secondLine);

        // runtime version should be on third line
        String vs = Runtime.version().toString();
        assertEquals(vs, lines.get(2));

        // dump should include current thread
        Thread currentThread = Thread.currentThread();
        if (trackAllThreads || !currentThread.isVirtual()) {
            ThreadFields fields = findThread(currentThread.threadId(), lines);
            assertNotNull(fields, "current thread not found");
            assertEquals(currentThread.getName(), fields.name());
            assertEquals(currentThread.isVirtual(), fields.isVirtual());
        }
    }

    /**
     * Test thread dump in JSON format.
     */
    @Test
    void testJsonFormat() throws Exception {
        ThreadDump threadDump = dumpThreadsToJson();

        // dump should include current thread in the root container
        Thread currentThread = Thread.currentThread();
        if (trackAllThreads || !currentThread.isVirtual()) {
            ThreadDump.ThreadInfo ti = threadDump.rootThreadContainer()
                    .findThread(currentThread.threadId())
                    .orElse(null);
            assertNotNull(ti, "current thread not found");
            assertEquals(currentThread.isVirtual(), ti.isVirtual());
        }
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
     * Test that a thread container for an executor service is in the JSON format thread dump.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testThreadContainer(ExecutorService executor) throws Exception {
        try (executor) {
            testThreadContainer(executor, Objects.toIdentityString(executor));
        }
    }

    /**
     * Test that a thread container for the common pool is in the JSON format thread dump.
     */
    @Test
    void testCommonPool() throws Exception {
        testThreadContainer(ForkJoinPool.commonPool(), "ForkJoinPool.commonPool");
    }

    /**
     * Test that the JSON thread dump has a thread container for the given executor.
     */
    private void testThreadContainer(ExecutorService executor, String name) throws Exception {
        var threadRef = new AtomicReference<Thread>();

        executor.submit(() -> {
            threadRef.set(Thread.currentThread());
            LockSupport.park();
        });

        // capture Thread
        Thread thread;
        while ((thread = threadRef.get()) == null) {
            Thread.sleep(20);
        }

        try {
            // dump threads to file and parse as JSON object
            ThreadDump threadDump = dumpThreadsToJson();

            // find the thread container corresponding to the executor
            var container = threadDump.findThreadContainer(name).orElse(null);
            assertNotNull(container, name + " not found");
            assertFalse(container.owner().isPresent());
            var parent = container.parent().orElse(null);
            assertEquals(threadDump.rootThreadContainer(), parent);

            // find the thread in the thread container
            ThreadDump.ThreadInfo ti = container.findThread(thread.threadId()).orElse(null);
            assertNotNull(ti, "thread not found");

        } finally {
            LockSupport.unpark(thread);
        }
    }

    /**
     * ThreadFactory implementations for tests.
     */
    static Stream<ThreadFactory> threadFactories() {
        Stream<ThreadFactory> s = Stream.of(Thread.ofPlatform().factory());
        if (trackAllThreads) {
            return Stream.concat(s, Stream.of(Thread.ofVirtual().factory()));
        } else {
            return s;
        }
    }

    /**
     * Test thread dump with a thread blocked on monitor enter.
     */
    @ParameterizedTest
    @MethodSource("threadFactories")
    void testBlockedThread(ThreadFactory factory) throws Exception {
        testBlockedThread(factory, false);
    }

    /**
     * Test thread dump with a thread blocked on monitor enter when pinned.
     */
    @Test
    void testBlockedThreadWhenPinned() throws Exception {
        assumeTrue(trackAllThreads, "This test requires all threads to be tracked");
        testBlockedThread(Thread.ofVirtual().factory(), true);
    }

    void testBlockedThread(ThreadFactory factory, boolean pinned) throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);

        Thread thread = factory.newThread(() -> {
            if (pinned) {
                VThreadPinner.runPinned(() -> {
                    started.countDown();
                    synchronized (lock) { }  // blocks
                });
            } else {
                started.countDown();
                synchronized (lock) { }  // blocks
            }
        });

        try {
            synchronized (lock) {
                // start thread and wait for it to block
                thread.start();
                started.await();
                await(thread, Thread.State.BLOCKED);

                long tid = thread.threadId();
                String lockAsString = Objects.toIdentityString(lock);

                // thread dump in plain text should include thread
                List<String> lines = dumpThreadsToPlainText();
                ThreadFields fields = findThread(tid, lines);
                assertNotNull(fields, "thread not found");
                assertEquals("BLOCKED", fields.state());
                assertTrue(contains(lines, "- waiting to lock <" + lockAsString));

                // thread dump in JSON format should include thread in root container
                ThreadDump threadDump = dumpThreadsToJson();
                ThreadDump.ThreadInfo ti = threadDump.rootThreadContainer()
                        .findThread(tid)
                        .orElse(null);
                assertNotNull(ti, "thread not found");
                assertEquals("BLOCKED", ti.state());
                assertEquals(lockAsString, ti.blockedOn());
                if (pinned) {
                    long carrierTid = ti.carrier().orElse(-1L);
                    assertNotEquals(-1L, carrierTid, "carrier not found");
                    assertForkJoinWorkerThread(carrierTid);
                }
            }
        } finally {
            thread.join();
        }
    }

    /**
     * Test thread dump with a thread waiting in Object.wait.
     */
    @ParameterizedTest
    @MethodSource("threadFactories")
    void testWaitingThread(ThreadFactory factory) throws Exception {
        testWaitingThread(factory, false);
    }

    /**
     * Test thread dump with a thread waiting in Object.wait when pinned.
     */
    @Test
    void testWaitingThreadWhenPinned() throws Exception {
        assumeTrue(trackAllThreads, "This test requires all threads to be tracked");
        testWaitingThread(Thread.ofVirtual().factory(), true);
    }

    void testWaitingThread(ThreadFactory factory, boolean pinned) throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);

        Thread thread = factory.newThread(() -> {
            try {
                synchronized (lock) {
                    if (pinned) {
                        VThreadPinner.runPinned(() -> {
                            started.countDown();
                            lock.wait();
                        });
                    } else {
                        started.countDown();
                        lock.wait();
                    }
                }
            } catch (InterruptedException e) { }
        });

        try {
            // start thread and wait for it to wait in Object.wait
            thread.start();
            started.await();
            await(thread, Thread.State.WAITING);

            long tid = thread.threadId();
            String lockAsString = Objects.toIdentityString(lock);

            // thread dump in plain text should include thread
            List<String> lines = dumpThreadsToPlainText();
            ThreadFields fields = findThread(tid, lines);
            assertNotNull(fields, "thread not found");
            assertEquals("WAITING", fields.state());

            // thread dump in JSON format should include thread in root container
            ThreadDump threadDump = dumpThreadsToJson();
            ThreadDump.ThreadInfo ti = threadDump.rootThreadContainer()
                    .findThread(thread.threadId())
                    .orElse(null);
            assertNotNull(ti, "thread not found");
            assertEquals(ti.isVirtual(), thread.isVirtual());
            assertEquals("WAITING", ti.state());
            if (pinned) {
                long carrierTid = ti.carrier().orElse(-1L);
                assertNotEquals(-1L, carrierTid, "carrier not found");
                assertForkJoinWorkerThread(carrierTid);
            }

            // Compiled native frames have no locals. If Object.wait0 has been compiled
            // then we don't have the object that the thread is waiting on
            Method wait0 = Object.class.getDeclaredMethod("wait0", long.class);
            boolean expectWaitingOn = !WhiteBox.getWhiteBox().isMethodCompiled(wait0);
            if (expectWaitingOn) {
                // plain text dump should have "waiting on" line
                assertTrue(contains(lines, "- waiting on <" + lockAsString));

                // JSON thread dump should have waitingOn property
                assertEquals(lockAsString, ti.waitingOn());
            }

        } finally {
            synchronized (lock) {
                lock.notifyAll();
            }
            thread.join();
        }
    }

    /**
     * Test thread dump with a thread parked on a j.u.c. lock.
     */
    @ParameterizedTest
    @MethodSource("threadFactories")
    void testParkedThread(ThreadFactory factory) throws Exception {
        testParkedThread(factory, false);
    }

    /**
     * Test thread dump with a thread parked on a j.u.c. lock and pinned.
     */
    @Test
    void testParkedThreadWhenPinned() throws Exception {
        assumeTrue(trackAllThreads, "This test requires all threads to be tracked");
        testParkedThread(Thread.ofVirtual().factory(), true);
    }

    void testParkedThread(ThreadFactory factory, boolean pinned) throws Exception {
        var lock = new ReentrantLock();
        var started = new CountDownLatch(1);

        Thread thread = factory.newThread(() -> {
            if (pinned) {
                VThreadPinner.runPinned(() -> {
                    started.countDown();
                    lock.lock();
                    lock.unlock();
                });
            } else {
                started.countDown();
                lock.lock();
                lock.unlock();
            }
        });

        lock.lock();
        try {
            // start thread and wait for it to park
            thread.start();
            started.await();
            await(thread, Thread.State.WAITING);

            long tid = thread.threadId();

            // thread dump in plain text should include thread
            List<String> lines = dumpThreadsToPlainText();
            ThreadFields fields = findThread(tid, lines);
            assertNotNull(fields, "thread not found");
            assertEquals("WAITING", fields.state());
            assertTrue(contains(lines, "- parking to wait for <java.util.concurrent.locks.ReentrantLock"));

            // thread dump in JSON format should include thread in root container
            ThreadDump threadDump = dumpThreadsToJson();
            ThreadDump.ThreadInfo ti = threadDump.rootThreadContainer()
                    .findThread(thread.threadId())
                    .orElse(null);
            assertNotNull(ti, "thread not found");
            assertEquals(ti.isVirtual(), thread.isVirtual());

            // thread should be waiting on the ReentrantLock
            assertEquals("WAITING", ti.state());
            String parkBlocker = ti.parkBlocker();
            assertNotNull(parkBlocker);
            assertTrue(parkBlocker.contains("java.util.concurrent.locks.ReentrantLock"));
            if (pinned) {
                long carrierTid = ti.carrier().orElse(-1L);
                assertNotEquals(-1L, carrierTid, "carrier not found");
                assertForkJoinWorkerThread(carrierTid);
            }
        } finally {
            lock.unlock();
            thread.join();
        }
    }

    /**
     * Test thread dump with a thread owning a monitor.
     */
    @ParameterizedTest
    @MethodSource("threadFactories")
    void testThreadOwnsMonitor(ThreadFactory factory) throws Exception {
        testThreadOwnsMonitor(factory, false);
    }

    @Test
    void testThreadOwnsMonitorWhenPinned() throws Exception {
        assumeTrue(trackAllThreads, "This test requires all threads to be tracked");
        testThreadOwnsMonitor(Thread.ofVirtual().factory(), true);
    }

    void testThreadOwnsMonitor(ThreadFactory factory, boolean pinned) throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);

        Thread thread = factory.newThread(() -> {
            synchronized (lock) {
                if (pinned) {
                    VThreadPinner.runPinned(() -> {
                        started.countDown();
                        LockSupport.park();
                    });
                } else {
                    started.countDown();
                    LockSupport.park();
                }
            }
        });

        try {
            // start thread and wait for it to park
            thread.start();
            started.await();
            await(thread, Thread.State.WAITING);

            long tid = thread.threadId();
            String lockAsString = Objects.toIdentityString(lock);

            // thread dump in plain text should include thread
            List<String> lines = dumpThreadsToPlainText();
            ThreadFields fields = findThread(tid, lines);
            assertNotNull(fields, "thread not found");
            assertEquals("WAITING", fields.state());
            assertTrue(contains(lines, "- locked <" + lockAsString));

            // thread dump in JSON format should include thread in root container
            ThreadDump threadDump = dumpThreadsToJson();
            ThreadDump.ThreadInfo ti = threadDump.rootThreadContainer()
                    .findThread(tid)
                    .orElse(null);
            assertNotNull(ti, "thread not found");
            assertEquals(ti.isVirtual(), thread.isVirtual());

            // the lock should be in the ownedMonitors array
            Set<String> ownedMonitors = ti.ownedMonitors().values()
                    .stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
            assertTrue(ownedMonitors.contains(lockAsString), lockAsString + " not found");
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test mounted virtual thread.
     */
    @Test
    void testMountedVirtualThread() throws Exception {
        assumeTrue(trackAllThreads, "This test requires all threads to be tracked");

        // start virtual thread that spins until done
        var started = new AtomicBoolean();
        var done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            started.set(true);
            while (!done.get()) {
                Thread.onSpinWait();
            }
        });

        try {
            // wait for thread to start
            awaitTrue(started);
            long tid = thread.threadId();

            // thread dump in plain text should include thread
            List<String> lines = dumpThreadsToPlainText();
            ThreadFields fields = findThread(tid, lines);
            assertNotNull(fields, "thread not found");
            assertTrue(fields.isVirtual());

            // thread dump in JSON format should include thread in root container
            ThreadDump threadDump = dumpThreadsToJson();
            ThreadDump.ThreadInfo ti = threadDump.rootThreadContainer()
                    .findThread(tid)
                    .orElse(null);
            assertNotNull(ti, "thread not found");
            assertTrue(ti.isVirtual());
            long carrierTid = ti.carrier().orElse(-1L);
            assertNotEquals(-1L, carrierTid, "carrier not found");
            assertForkJoinWorkerThread(carrierTid);
        } finally {
            done.set(true);
            thread.join();
        }
    }

    /**
     * Asserts that the given thread identifier is a ForkJoinWorkerThread.
     */
    private void assertForkJoinWorkerThread(long tid) {
        Thread thread = Thread.getAllStackTraces()
                .keySet()
                .stream()
                .filter(t -> t.threadId() == tid)
                .findAny()
                .orElse(null);
        assertNotNull(thread, "thread " + tid + " not found");
        assertTrue(thread instanceof ForkJoinWorkerThread, "not a ForkJoinWorkerThread");
    }

    /**
     * Test that dumpThreads throws if the output file already exists.
     */
    @Test
    void testFileAlreadyExists() throws Exception {
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        String file = Files.createFile(genOutputPath("txt")).toString();
        assertThrows(FileAlreadyExistsException.class,
                () -> mbean.dumpThreads(file, ThreadDumpFormat.TEXT_PLAIN));
        assertThrows(FileAlreadyExistsException.class,
                () -> mbean.dumpThreads(file, ThreadDumpFormat.JSON));
    }

    /**
     * Test that dumpThreads throws IOException when the output file cannot be created.
     */
    @Test
    void testFileCreateFails() {
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        String badFile = Path.of(".").toAbsolutePath()
                .resolve("does-not-exist")
                .resolve("does-not-exist")
                .resolve("threads.bad")
                .toString();
        assertThrows(IOException.class,
                () -> mbean.dumpThreads(badFile, ThreadDumpFormat.TEXT_PLAIN));
        assertThrows(IOException.class,
                () -> mbean.dumpThreads(badFile, ThreadDumpFormat.JSON));
    }

    /**
     * Test that dumpThreads throws IOException if writing to output file fails.
     */
    @Test
    void testFileWriteFails() {
        var out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("There is not enough space on the disk");
            }
        };
        // need to invoke internal API directly to test this
        assertThrows(IOException.class, () -> jdk.internal.vm.ThreadDumper.dumpThreads(out));
        assertThrows(IOException.class, () -> jdk.internal.vm.ThreadDumper.dumpThreadsToJson(out));
    }

    /**
     * Test that dumpThreads throws if the file path is relative.
     */
    @Test
    void testRelativePath() {
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
    void testNull() {
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        assertThrows(NullPointerException.class,
                () -> mbean.dumpThreads(null, ThreadDumpFormat.TEXT_PLAIN));
        assertThrows(NullPointerException.class,
                () -> mbean.dumpThreads(genOutputPath("txt").toString(), null));
    }

    /**
     * Represents the data for a thread found in a plain text thread dump.
     */
    private record ThreadFields(long tid, String name, boolean isVirtual, String state) { }

    /**
     * Find a thread in the lines of a plain text thread dump.
     */
    private ThreadFields findThread(long tid, List<String> lines) {
        String line = lines.stream()
                .filter(l -> l.startsWith("#" + tid + " "))
                .findFirst()
                .orElse(null);
        if (line == null) {
            return null;
        }

        // #3 "main" RUNNABLE 2025-04-18T15:22:12.012450Z
        // #36 "" virtual WAITING 2025-04-18T15:22:12.012450Z
        Pattern pattern = Pattern.compile("#(\\d+)\\s+\"([^\"]*)\"\\s+(virtual\\s+)?(\\w+)\\s+(.*)");
        Matcher matcher = pattern.matcher(line);
        assertTrue(matcher.matches());
        String name = matcher.group(2);
        boolean isVirtual = "virtual ".equals(matcher.group(3));
        String state = matcher.group(4);
        return new ThreadFields(tid, name, isVirtual, state);
    }

    /**
     * Returns true if lines of a plain text thread dump contain the given text.
     */
    private boolean contains(List<String> lines, String text) {
        return lines.stream().map(String::trim)
                .anyMatch(l -> l.contains(text));
    }

    /**
     * Dump threads to a file in plain text format, return the lines in the file.
     */
    private List<String> dumpThreadsToPlainText() throws Exception {
        Path file = genOutputPath(".txt");
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        mbean.dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN);
        System.err.format("Dumped to %s%n", file.getFileName());
        List<String> lines = Files.readAllLines(file);
        return lines;
    }

    /**
     * Dump threads to a file in JSON format, parse and return as JSON object.
     */
    private static ThreadDump dumpThreadsToJson() throws Exception {
        Path file = genOutputPath(".json");
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        mbean.dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.JSON);
        System.err.format("Dumped to %s%n", file.getFileName());
        String jsonText = Files.readString(file);
        return ThreadDump.parse(jsonText);
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
     * Waits for the given thread to get to a given state.
     */
    private void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assertTrue(state != Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }

    /**
     * Waits for the boolean value to become true.
     */
    private static void awaitTrue(AtomicBoolean ref) throws Exception {
        while (!ref.get()) {
            Thread.sleep(20);
        }
    }
}
