/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8370344
 * @requires os.family != "windows"
 * @requires vm.flavor != "zero"
 * @requires vm.hasJFR
 * @summary Test closing a shared scope during faulting access
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main jdk.test.lib.FileInstaller sharedCloseJfr.jfc sharedCloseJfr.jfc
 * @run main/othervm
 *   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *   -XX:CompileCommand=exclude,*TestSharedCloseJFR.main
 *   -XX:StartFlightRecording:filename=recording.jfr,dumponexit=true,settings=sharedCloseJfr.jfc
 *   TestSharedCloseJFR
 */

import jdk.test.whitebox.WhiteBox;

import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

// We are interested in the following scenario:
// When accessing a memory-mapped file that is truncated
// a segmentation fault will occur (see also test/hotspot/jtreg/runtime/Unsafe/InternalErrorTest.java)
//
// This segmentation fault will be caught in the VM's signal handler
// and get turned into an InternalError by a VM handshake operation.
// This handshake operation calls back into Java to the constructor
// of InternalError. This constructor calls super constructors until
// it ends up in the constructor of Throwable, where JFR starts logging
// the Throwable being created. This logging code adds a bunch
// of extra Java frames to the stack.
//
// All of this occurs during the original memory access, i.e.
// while we are inside a @Scoped method call (jdk.internal.misc.ScopedMemoryAccess).
// If at this point a shared arena is closed in another thread,
// the shared scope closure handshake (src/hotspot/share/prims/scopedMemoryAccess.cpp)
// will see all the extra frames added by JFR and the InternalError constructor,
// while walking the stack of the thread doing the faulting access.
//
// This test is here to make sure that the shared scope closure handshake can
// deal with that situation.
public class TestSharedCloseJFR {

    private static final int PAGE_SIZE = WhiteBox.getWhiteBox().getVMPageSize();

    public static void main(String[] args) throws Throwable {
        String fileName = "tmp.txt";
        Path path = Path.of(fileName);
        AtomicBoolean stop = new AtomicBoolean();

        Files.write(path, "1".repeat(PAGE_SIZE + 1000).getBytes());
        try (RandomAccessFile file = new RandomAccessFile(fileName, "rw")) {
            FileChannel fileChannel = file.getChannel();
            MemorySegment segment =
                    fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileChannel.size(), Arena.ofAuto());
            // truncate file
            // this will make the access fault
            Files.write(path, "2".getBytes());

            // start worker thread
            CountDownLatch latch = new CountDownLatch(1);
            Thread.ofPlatform().start(() -> {
                latch.countDown();
                while (!stop.get()) {
                    Arena.ofShared().close(); // hammer VM with handshakes
                }
            });

            // wait util the worker thread has started
            latch.await();

            // access (should fault)
            // try it a few times until we get a handshake during JFR reporting
            for (int i = 0; i < 50_000; i++) {
                try {
                    segment.get(ValueLayout.JAVA_INT, PAGE_SIZE);
                    throw new RuntimeException("InternalError was expected");
                } catch (InternalError e) {
                    // InternalError as expected
                    if (!e.getMessage().contains("a fault occurred in an unsafe memory access")) {
                        throw new RuntimeException("Unexpected exception", e);
                    }
                }
            }
        } finally {
            // stop worker
            stop.set(true);
        }
    }
}
