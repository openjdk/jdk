/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package gc.stress.gclocker;

// Stress the GC locker by calling GetPrimitiveArrayCritical while
// concurrently filling up old gen.

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

final class ThreadUtils {
    public static void sleep(long durationMS) {
        try {
            Thread.sleep(durationMS);
        } catch (Exception e) {
        }
    }
}

class Filler {
    private static final int SIZE = 250000;

    private int[] i1 = new int[SIZE];
    private int[] i2 = new int[SIZE];
    private short[] s1 = new short[SIZE];
    private short[] s2 = new short[SIZE];

    private Map<Object, Object> map = new HashMap<>();

    public Filler() {
        for (int i = 0; i < 10000; i++) {
            map.put(new Object(), new Object());
        }
    }
}

class Exitable {
    private volatile boolean shouldExit = false;

    protected boolean shouldExit() {
        return shouldExit;
    }

    public void exit() {
        shouldExit = true;
    }
}

class MemoryWatcher {
    private MemoryPoolMXBean bean;
    private final int thresholdPromille = 750;
    private final int criticalThresholdPromille = 800;
    private final long minGCWaitNanos = 1_000_000_000L;
    private final long minFreeWaitElapsedNanos = 30L * 1_000_000_000L;
    private final long minFreeCriticalWaitNanos;

    private int lastUsage = 0;
    private long lastGCDetectedNanos = System.nanoTime();
    private long lastFreeNanos = System.nanoTime();

    public MemoryWatcher(String mxBeanName, long minFreeCriticalWaitNanos) {
        this.minFreeCriticalWaitNanos = minFreeCriticalWaitNanos;
        List<MemoryPoolMXBean> memoryBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean bean : memoryBeans) {
            if (bean.getName().equals(mxBeanName)) {
                this.bean = bean;
                break;
            }
        }
    }

    private int getMemoryUsage() {
        if (bean == null) {
            Runtime r = Runtime.getRuntime();
            float free = (float) r.freeMemory() / r.maxMemory();
            return Math.round((1 - free) * 1000);
        } else {
            MemoryUsage usage = bean.getUsage();
            float used = (float) usage.getUsed() / usage.getCommitted();
            return Math.round(used * 1000);
        }
    }

    public synchronized boolean shouldFreeUpSpace() {
        int usage = getMemoryUsage();
        long nowNanos = System.nanoTime();

        boolean detectedGC = false;
        if (usage < lastUsage) {
            lastGCDetectedNanos = nowNanos;
            detectedGC = true;
        }

        lastUsage = usage;

        long elapsedNanos = nowNanos - lastFreeNanos;
        long timeSinceLastGCNanos = nowNanos - lastGCDetectedNanos;

        if (usage > criticalThresholdPromille && elapsedNanos > minFreeCriticalWaitNanos) {
            lastFreeNanos = nowNanos;
            return true;
        } else if (usage > thresholdPromille && !detectedGC) {
            if (elapsedNanos > minFreeWaitElapsedNanos || timeSinceLastGCNanos > minGCWaitNanos) {
                lastFreeNanos = nowNanos;
                return true;
            }
        }

        return false;
    }
}

class MemoryUser extends Exitable implements Runnable {
    private final Queue<Filler> cache = new ArrayDeque<Filler>();
    private final MemoryWatcher watcher;

    private void load() {
        if (watcher.shouldFreeUpSpace()) {
            int toRemove = cache.size() / 5;
            for (int i = 0; i < toRemove; i++) {
                cache.remove();
            }
        }
        cache.add(new Filler());
    }

    public MemoryUser(String mxBeanName, long minFreeCriticalWaitNanos) {
        watcher = new MemoryWatcher(mxBeanName, minFreeCriticalWaitNanos);
    }

    @Override
    public void run() {
        for (int i = 0; i < 200; i++) {
            load();
        }

        while (!shouldExit()) {
            load();
        }
    }
}

class GCLockerStresser extends Exitable implements Runnable {
    static native void fillWithRandomValues(byte[] array);

    @Override
    public void run() {
        byte[] array = new byte[1024 * 1024];
        while (!shouldExit()) {
            fillWithRandomValues(array);
        }
    }
}

public class TestGCLocker {
    private static Exitable startGCLockerStresser(String name) {
        GCLockerStresser task = new GCLockerStresser();

        Thread thread = new Thread(task);
        thread.setName(name);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();

        return task;
    }

    private static Exitable startMemoryUser(String mxBeanName, long minFreeCriticalWaitNanos) {
        MemoryUser task = new MemoryUser(mxBeanName, minFreeCriticalWaitNanos);

        Thread thread = new Thread(task);
        thread.setName("Memory User");
        thread.start();

        return task;
    }

    public static void main(String[] args) {
        System.loadLibrary("TestGCLocker");

        long durationMinutes = args.length > 0 ? Long.parseLong(args[0]) : 5;
        String mxBeanName = args.length > 1 ? args[1] : null;
        long minFreeCriticalWaitNanos = args.length > 2
            ? Integer.parseInt(args[2]) * 1_000_000L
            : 500_000_000L;

        Exitable stresser1 = startGCLockerStresser("GCLockerStresser1");
        Exitable stresser2 = startGCLockerStresser("GCLockerStresser2");
        Exitable memoryUser = startMemoryUser(mxBeanName, minFreeCriticalWaitNanos);

        try {
            Thread.sleep(durationMinutes * 60_000L);
        } catch (InterruptedException e) {
            throw new RuntimeException("Test Failure, did not except an InterruptedException", e);
        }

        stresser1.exit();
        stresser2.exit();
        memoryUser.exit();
    }
}
