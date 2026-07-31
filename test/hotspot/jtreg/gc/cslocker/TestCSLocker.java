/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

package gc.cslocker;

import static gc.testlibrary.Allocation.blackHole;

/*
 * @test TestCSLocker
 * @bug 6186200
 * @library /
 * @summary This short test check RFE 6186200 changes. One thread locked
 * @summary completely in JNI CS, while other is trying to allocate memory
 * @summary provoking GC. OOM means FAIL, deadlock means PASS.
 *
 * @run main/native/othervm -Xmx256m gc.cslocker.TestCSLocker
 */

public class TestCSLocker extends Thread
{
    public static void main(String args[]) throws Exception {
        // start garbage producer thread
        GarbageProducer garbageProducer = new GarbageProducer(1000000, 10);
        garbageProducer.start();

        // start CS locker thread
        CSLocker csLocker = new CSLocker();
        csLocker.start();

        // Wait for the csLocker thread to finish its critical section
        csLocker.join();
        garbageProducer.interrupt();
    }
}

class GarbageProducer extends Thread
{
    private int size;
    private int sleepTime;

    GarbageProducer(int size, int sleepTime) {
        this.size = size;
        this.sleepTime = sleepTime;
    }

    public void run() {
        boolean isRunning = true;

        while (isRunning) {
            try {
                blackHole(new int[size]);
                sleep(sleepTime);
            } catch (InterruptedException e) {
                isRunning = false;
            }
        }
    }
}

class CSLocker extends Thread
{
    static { System.loadLibrary("TestCSLocker"); }

    public void run() {
        int[] a = new int[10];
        a[0] = 1;
        if (!criticalSection(a)) {
            throw new RuntimeException("failed to acquire CSLock");
        }
    }

    native boolean criticalSection(int[] array);
}
