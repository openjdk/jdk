/*
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;

/**
 * @test
 * @bug 8289208
 * @summary Verifies Disposer robustness in a multi-threaded environment.
 * @run main/othervm -mx128m TestDisposerRace
 * @modules java.desktop/sun.java2d
 */
public final class TestDisposerRace {
    private static final AtomicInteger recordsCount = new AtomicInteger();
    private static volatile boolean disposerDone = false;

    public static void main(String[] args) throws Exception {
        TestDisposerRace test = new TestDisposerRace();
        test.run();

        checkRecordsCountIsSane();
        if (recordsCount.get() > 0) {
            throw new RuntimeException("Some records (" + recordsCount + ") have not been disposed");
        }
    }

    TestDisposerRace() {
        addRecordsToDisposer(30_000);
    }

    void run() throws Exception {
        generateOOME();
        for (int i = 0; i < 1000; ++i) {
            SwingUtilities.invokeAndWait(Disposer::pollRemove);
            if (i % 10 == 0) {
                // Adding records will race with the diposer trying to remove them
                addRecordsToDisposer(1000);
            }
        }

        Disposer.addObjectRecord(new Object(), new FinalDisposerRecord());

        while (!disposerDone) {
             generateOOME();
        }
    }

    private static void checkRecordsCountIsSane() {
        if (recordsCount.get() < 0) {
            throw new RuntimeException("Disposed more records than were added");
        }
    }

    private void addRecordsToDisposer(int count) {
        checkRecordsCountIsSane();

        recordsCount.addAndGet(count);

        MyDisposerRecord disposerRecord = new MyDisposerRecord();
        for (int i = 0; i < count; i++) {
            Disposer.addObjectRecord(new Object(), disposerRecord);
        }
    }

    class MyDisposerRecord implements DisposerRecord {
        public void dispose() {
            recordsCount.decrementAndGet();
        }
    }

    class FinalDisposerRecord implements DisposerRecord {
        public void dispose() {
            disposerDone = true;
        }
    }

    private static void giveGCAChance() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {}
    }

    private static void generateOOME() throws Exception {
        final List<Object> leak = new LinkedList<>();
        try {
            while (true) {
                leak.add(new byte[1024 * 1024]);
            }
        } catch (OutOfMemoryError ignored) {}
        giveGCAChance();
    }
}
