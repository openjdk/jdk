/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284161 8287008
 * @summary Basic test for com.sun.management.HotSpotDiagnosticMXBean.dumpThreads
 * @enablePreview
 * @library /test/lib
 * @run testng/othervm DumpThreads
 * @run testng/othervm -Djdk.trackAllThreads DumpThreads
 * @run testng/othervm -Djdk.trackAllThreads=true DumpThreads
 * @run testng/othervm -Djdk.trackAllThreadds=false DumpThreads
 */

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.HotSpotDiagnosticMXBean.ThreadDumpFormat;
import jdk.test.lib.threaddump.ThreadDump;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class DumpThreads {
    private static final boolean TRACK_ALL_THREADS;
    static {
        String s = System.getProperty("jdk.trackAllThreads");
        TRACK_ALL_THREADS = (s != null) && (s.isEmpty() || Boolean.parseBoolean(s));
    }

    /**
     * Thread dump in plain text format.
     */
    @Test
    public void testPlainText() throws Exception {
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        Path file = genOutputPath("txt");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Thread vthread = forkParker(executor);
            try {
                mbean.dumpThreads(file.toString(), ThreadDumpFormat.TEXT_PLAIN);
                cat(file);

                // pid should be on the first line
                String pid = "" + ProcessHandle.current().pid();
                assertTrue(line(file, 0).contains(pid));

                // runtime version should be on third line
                String vs = Runtime.version().toString();
                assertTrue(line(file, 2).contains(vs));

                // virtual thread should be found
                assertTrue(isPresent(file, vthread));

                // if the current thread is a platform thread then it should be included
                Thread currentThread = Thread.currentThread();
                if (!currentThread.isVirtual() || TRACK_ALL_THREADS) {
                    assertTrue(isPresent(file, currentThread));
                }
            } finally {
                LockSupport.unpark(vthread);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Thread dump in JSON format.
     */
    @Test
    public void testJson() throws Exception {
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        Path file = genOutputPath("json");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Thread vthread = forkParker(executor);
            try {
                mbean.dumpThreads(file.toString(), ThreadDumpFormat.JSON);
                cat(file);

                // parse the JSON text
                String jsonText = Files.readString(file);
                ThreadDump threadDump = ThreadDump.parse(jsonText);

                // test threadDump/processId
                assertTrue(threadDump.processId() == ProcessHandle.current().pid());

                // test threadDump/time can be parsed
                ZonedDateTime.parse(threadDump.time());

                // test threadDump/runtimeVersion
                assertEquals(threadDump.runtimeVersion(), Runtime.version().toString());

                // test root container
                var rootContainer = threadDump.rootThreadContainer();
                assertFalse(rootContainer.owner().isPresent());
                assertFalse(rootContainer.parent().isPresent());

                // if the current thread is a platform thread then it will be in root container
                Thread currentThread = Thread.currentThread();
                if (!currentThread.isVirtual() || TRACK_ALL_THREADS) {
                    rootContainer.findThread(currentThread.threadId()).orElseThrow();
                }

                // find the thread container for the executor. The name of this executor
                // is its String representaiton in this case.
                String name = executor.toString();
                var container = threadDump.findThreadContainer(name).orElseThrow();
                assertFalse(container.owner().isPresent());
                assertTrue(container.parent().get() == rootContainer);
                container.findThread(vthread.threadId()).orElseThrow();
            } finally {
                LockSupport.unpark(vthread);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Test that dumpThreads throws if the output file already exists.
     */
    @Test
    public void testFileAlreadyExsists() throws Exception {
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
    public void testRelativePath() throws Exception {
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
    public void testNull() throws Exception {
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
    private static Thread forkParker(ExecutorService executor) throws Exception {
        var ref = new AtomicReference<Thread>();
        executor.submit(() -> {
            ref.set(Thread.currentThread());
            LockSupport.park();
        });
        Thread thread;
        while ((thread = ref.get()) == null) {
            Thread.sleep(10);
        }
        return thread;
    }

    /**
     * Returns true if the file contains #<tid>
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

    /**
     * Print the given file to standard output.
     */
    private static void cat(Path file) throws Exception {
        try (Stream<String> stream = Files.lines(file)) {
            stream.forEach(System.out::println);
        }
    }
}
