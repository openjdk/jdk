/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary PipedReader.close() should synchronize state changes and use
 *          volatile for closedByReader, matching PipedInputStream behavior
 * @run main CloseVisibility
 */

import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PipedInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CloseVisibility {

    public static void main(String[] args) throws Exception {
        testFieldIsVolatile();
        testCloseIsSynchronized();
    }

    /**
     * Verify PipedReader.closedByReader is volatile (matching PipedInputStream).
     */
    static void testFieldIsVolatile() throws Exception {
        Field prField = PipedReader.class.getDeclaredField("closedByReader");
        boolean prVolatile = Modifier.isVolatile(prField.getModifiers());

        Field pisField = PipedInputStream.class.getDeclaredField("closedByReader");
        boolean pisVolatile = Modifier.isVolatile(pisField.getModifiers());

        if (!prVolatile) {
            throw new RuntimeException(
                "PipedReader.closedByReader should be volatile "
                + "(PipedInputStream's is: " + pisVolatile + ")");
        }
        System.out.println("PASS: PipedReader.closedByReader is volatile");
    }

    /**
     * Verify PipedReader.close() acquires the monitor lock.
     *
     * Hold the PipedReader lock from another thread, then call close().
     * If close() uses synchronized(this), it should block until the
     * lock is released. If it completes immediately, close() is not
     * synchronized.
     */
    static void testCloseIsSynchronized() throws Exception {
        PipedWriter pw = new PipedWriter();
        PipedReader pr = new PipedReader(pw);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch closeDone = new CountDownLatch(1);

        // Thread 1: hold the PipedReader lock for 2 seconds
        Thread holder = new Thread(() -> {
            synchronized (pr) {
                lockHeld.countDown();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        holder.setDaemon(true);
        holder.start();
        lockHeld.await();

        // Thread 2: call close() — should block on synchronized(this)
        AtomicBoolean closeCompleted = new AtomicBoolean(false);
        Thread closer = new Thread(() -> {
            try {
                pr.close();
                closeCompleted.set(true);
            } catch (IOException e) { /* ignore */ }
            closeDone.countDown();
        });
        closer.setDaemon(true);
        closer.start();

        // close() should NOT complete within 500ms because holder has
        // the lock for 2 seconds. If close() has no synchronized block,
        // it would return immediately.
        boolean doneEarly = closeDone.await(500, TimeUnit.MILLISECONDS);
        if (doneEarly) {
            throw new RuntimeException(
                "close() completed while another thread held the monitor. "
                + "Expected close() to block on synchronized(this).");
        }

        // Release the lock and verify close() completes
        holder.interrupt();
        holder.join(2000);
        closer.join(2000);

        if (!closeCompleted.get()) {
            throw new RuntimeException(
                "close() did not complete after lock was released");
        }
        System.out.println("PASS: PipedReader.close() blocks on monitor lock");
    }
}
