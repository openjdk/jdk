/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4250354
 * @key headful
 * @summary tests that JNI global refs are cleaned up correctly
 * @run main/timeout=600 NoEventsLeakTest
 */

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Frame;

public class NoEventsLeakTest extends Frame {
    static final int nLoopCount = 1000;

    private static void initialize() {
        NoEventsLeakTest app = new NoEventsLeakTest();
        boolean result = app.run();
        if (result) {
            throw new RuntimeException("Memory leak in Component");
        }
        System.out.println("Test Passed");
    }

    public boolean run() {
        setSize(10, 10);
        addNotify();
        for (int i = 0; i < nLoopCount; i++) {
            Canvas panel = new TestCanvas();
            add(panel, 0);
            remove(0);
            panel = null;
            System.gc();
        }
        try {
            Thread.currentThread().sleep(1000);
        } catch (InterruptedException e) {
        }
        System.gc();
        System.out.println("Checking");
        return ((TestCanvas.created - TestCanvas.finalized) > 800);
    }

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(NoEventsLeakTest::initialize);
    }
}

class TestCanvas extends Canvas {
    static int finalized = 0;
    static int created = 0;
    static final int nLoopPrint = 100;

    public TestCanvas() {
        if (created % nLoopPrint == 0) {
            System.out.println("Created " + getClass() + " " + created);
        }
        created++;
    }

    @SuppressWarnings("removal")
    protected void finalize() {
        try {
            super.finalize();
            if (finalized % nLoopPrint == 0) {
                System.out.println("Finalized " + getClass() + " " + finalized);
            }
            finalized++;
        } catch (Throwable t) {
            System.out.println("Exception in " + getClass() + ": " + t);
        }
    }
}
