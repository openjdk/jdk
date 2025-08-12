/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test dumping the heap while a virtual thread is unmounted with a native method frame at top.
 * @requires vm.continuations
 * @modules jdk.management
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run junit/othervm/native -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI --enable-native-access=ALL-UNNAMED UnmountedVThreadNativeMethodAtTop
 */

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import com.sun.management.HotSpotDiagnosticMXBean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import jdk.test.lib.hprof.model.Snapshot;
import jdk.test.lib.hprof.model.ThreadObject;
import jdk.test.lib.hprof.parser.Reader;
import jdk.test.whitebox.WhiteBox;

public class UnmountedVThreadNativeMethodAtTop {

    static WhiteBox wb = WhiteBox.getWhiteBox();

    boolean done;

    /**
     * The tests accumulate previous heap dumps. Trigger GC before each test to get rid of them.
     * This makes dumps smaller, processing faster, and avoids OOMs
     */
    @BeforeEach
    void doGC() {
        wb.fullGC();
    }

    /**
     * Test dumping the heap while a virtual thread is blocked entering a synchronized native method.
     */
    @Test
    void VThreadBlockedAtSynchronizedNative() throws Exception {
        var lock = this;
        var started = new CountDownLatch(1);
        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            runWithSynchronizedNative();
        });
        try {
            synchronized (lock) {
                vthread.start();

                // wait for thread to start and block
                started.await();
                await(vthread, Thread.State.BLOCKED);

                Path dumpFile = dumpHeap();
                verifyHeapDump(dumpFile);
            }
        } finally {
            vthread.join();
        }
    }

    /**
     * Run native method while holding the monitor for "this".
     */
    private synchronized native void runWithSynchronizedNative();

    /**
     * Called from the native method.
     */
    private void run() {
    }

    /**
     * Test dumping the heap while a virtual thread is waiting in Object.wait().
     */
    @Test
    void VThreadBlockedAtOjectWait() throws Exception {
        var lock = this;
        var started = new CountDownLatch(1);
        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            try {
                synchronized (lock) {
                    while (!done) {
                        lock.wait();
                    }
                }
            } catch (InterruptedException e) { }
        });
        try {
            vthread.start();

            // wait for thread to start and wait
            started.await();
            await(vthread, Thread.State.WAITING);

            Path dumpFile = dumpHeap();
            verifyHeapDump(dumpFile);
        } finally {
            synchronized (lock) {
                done = true;
                lock.notify();
            }
            vthread.join();
        }
    }

    private Path dumpHeap() throws Exception {
        Path df = Files.createTempFile(Path.of("."), "dump", ".hprof");
        Files.delete(df);
        var bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        bean.dumpHeap(df.toString(), false);
        return df;
    }

    private void verifyHeapDump(Path dumpFile) throws Exception {
        // Make sure that heap dump can be parsed
        System.out.println("Parse " + dumpFile.toAbsolutePath() + " ...");
        try (Snapshot snapshot = Reader.readFile(dumpFile.toString(), false, 0)) {
            snapshot.resolve(true);

            // find virtual threads
            List<ThreadObject> vthreads = snapshot.getThreads()
                    .stream()
                    .filter(t -> snapshot.findThing(t.getId())
                            .getClazz()
                            .getName().equals("java.lang.VirtualThread"))
                    .toList();

            assertFalse(vthreads.isEmpty(), "No virtual threads found!!");
            System.out.format("%s virtual thread(s) found%n", vthreads.size());
        }
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            Thread.sleep(10);
            state = thread.getState();
        }
    }

    static {
        System.loadLibrary("UnmountedVThreadNativeMethodAtTop");
    }
}
