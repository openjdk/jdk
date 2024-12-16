/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=monitorenter
 * @bug 8345266
 * @summary Test OOM while trying to unmount vthread on monitorenter
 * @requires vm.continuations & vm.gc.G1 & vm.opt.DisableExplicitGC != "true"
 * @library /test/lib
 * @run main/othervm -XX:+UseG1GC -Xmx48M MonitorOOM false
 */

/*
 * @test id=wait
 * @bug 8345266
 * @summary Test OOM while trying to unmount vthread on Object.wait
 * @requires vm.continuations & vm.gc.G1 & vm.opt.DisableExplicitGC != "true"
 * @library /test/lib
 * @run main/othervm -XX:+UseG1GC -Xmx48M MonitorOOM true
 */

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MonitorOOM {
    static volatile Object data;

    public static void main(String[] args) throws Throwable {
        final boolean testWait = args.length == 1 ? Boolean.parseBoolean(args[0]) : false;

        Thread vthread;
        var lock = new Object();
        var canFillHeap = new AtomicBoolean();
        var heapFilled = new AtomicBoolean();
        var heapCollected = new AtomicBoolean();
        var exRef = new AtomicReference<Throwable>();
        synchronized (lock) {
            vthread = Thread.ofVirtual().start(() -> {
                try {
                    awaitTrue(canFillHeap);
                    data = fillHeap();
                    heapFilled.set(true);
                    synchronized (lock) {
                        if (testWait) {
                            lock.wait(5);
                        }
                    }
                    data = null;
                    System.gc();
                    heapCollected.set(true);
                } catch (Throwable e) {
                    data = null;
                    System.gc(); // avoid nested OOME
                    exRef.set(e);
                }
            });
            canFillHeap.set(true);
            awaitTrue(heapFilled);
            loopMillis(100); // allow vthread to reach synchronized
        }
        joinVThread(vthread, heapCollected, exRef);
        assert exRef.get() == null;
    }

    private static Object[] fillHeap() {
        Object[] first = null, last = null;
        int size = 1 << 20;
        while (size > 0) {
            try {
                Object[] array = new Object[size];
                if (first == null) {
                    first = array;
                } else {
                    last[0] = array;
                }
                last = array;
            } catch (OutOfMemoryError oome) {
                size = size >>> 1;
            }
        }
        return first;
    }

    private static void awaitTrue(AtomicBoolean ready) {
        // Don't call anything that might allocate from the Java heap.
        while (!ready.get()) {}
    }

    private static void loopMillis(long millis) {
        // Don't call anything that might allocate from the Java heap.
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < millis) {}
    }

    private static void joinVThread(Thread vthread, AtomicBoolean ready, AtomicReference<Throwable> exRef) throws Throwable {
        // Don't call anything that might allocate from the Java heap until ready is set.
        while (!ready.get()) {
            Throwable ex = exRef.get();
            if (ex != null) {
                throw ex;
            }
        }
        vthread.join();
    }
}
