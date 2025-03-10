/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test id=passive
 * @summary Check that MX notifications are reported for all cycles
 * @library /test/lib /
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive
 *      -XX:+ShenandoahDegeneratedGC
 *      TestPauseNotifications
 *
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive
 *      -XX:-ShenandoahDegeneratedGC
 *      TestPauseNotifications
 */

/*
 * @test id=aggressive
 * @summary Check that MX notifications are reported for all cycles
 * @library /test/lib /
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *      TestPauseNotifications
 */

/*
 * @test id=adaptive
 * @summary Check that MX notifications are reported for all cycles
 * @library /test/lib /
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive
 *      TestPauseNotifications
 */

/*
 * @test id=static
 * @summary Check that MX notifications are reported for all cycles
 * @library /test/lib /
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=static
 *      TestPauseNotifications
 */

/*
 * @test id=compact
 * @summary Check that MX notifications are reported for all cycles
 * @library /test/lib /
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=compact
 *      TestPauseNotifications
 */

/*
 * @test id=generational
 * @summary Check that MX notifications are reported for all cycles
 * @library /test/lib /
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      TestPauseNotifications
 */

import java.util.*;
import java.util.concurrent.atomic.*;
import javax.management.*;
import java.lang.management.*;
import javax.management.openmbean.*;

import jdk.test.lib.Utils;

import com.sun.management.GarbageCollectionNotificationInfo;

public class TestPauseNotifications {

    static final long HEAP_MB = 128;                           // adjust for test configuration above
    static final long TARGET_MB = Long.getLong("target", 2_000); // 2 Gb allocation

    static volatile Object sink;

    private static boolean isExpectedPauseAction(String action) {
        return "Init Mark".equals(action) || "Final Mark".equals(action) || "Full GC".equals(action)
            || "Degenerated GC".equals(action) || "Init Update Refs".equals(action)
            || "Final Update Refs".equals(action) || "Final Roots".equals(action);
    }

    public static void main(String[] args) throws Exception {
        final long startTimeNanos = System.nanoTime();

        final AtomicLong pausesDuration = new AtomicLong();
        final AtomicLong cyclesDuration = new AtomicLong();
        final AtomicLong pausesCount = new AtomicLong();
        final AtomicLong cyclesCount = new AtomicLong();

        NotificationListener listener = new NotificationListener() {
            @Override
            public void handleNotification(Notification n, Object o) {
                if (n.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());

                    System.out.println("Received: " + info.getGcName() + "/" + info.getGcAction());


                    long d = info.getGcInfo().getDuration();

                    String name = info.getGcName();
                    if (name.contains("Shenandoah")) {
                        if (name.equals("Shenandoah Pauses")) {
                            pausesCount.incrementAndGet();
                            pausesDuration.addAndGet(d);
                            if (!isExpectedPauseAction(info.getGcAction())) {
                                throw new IllegalStateException("Unknown action: " + info.getGcAction());
                            }
                        } else if (name.equals("Shenandoah Cycles")) {
                            cyclesCount.incrementAndGet();
                            cyclesDuration.addAndGet(d);
                        } else {
                            throw new IllegalStateException("Unknown name: " + name);
                        }
                    }
                }
            }
        };

        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            ((NotificationEmitter) bean).addNotificationListener(listener, null, null);
        }

        final int size = 100_000;
        long count = TARGET_MB * 1024 * 1024 / (16 + 4 * size);

        for (int c = 0; c < count; c++) {
            sink = new int[size];
        }

        // Look at test timeout to figure out how long we can wait without breaking into timeout.
        // Default to 1/4 of the remaining time in 1s steps.
        final long STEP_MS = 1000;
        long spentTimeNanos = System.nanoTime() - startTimeNanos;
        long maxTries = (Utils.adjustTimeout(Utils.DEFAULT_TEST_TIMEOUT) - (spentTimeNanos / 1_000_000L)) / STEP_MS / 4;

        long actualPauses = 0;
        long actualCycles = 0;

        // Wait until enough notifications are accrued to match minimum boundary.
        long minExpected = 10;

        long tries = 0;
        while (tries++ < maxTries) {
            actualPauses = pausesCount.get();
            actualCycles = cyclesCount.get();
            if (minExpected <= actualPauses && minExpected <= actualCycles) {
                // Wait a little bit to catch the lingering notifications.
                Thread.sleep(5000);
                actualPauses = pausesCount.get();
                actualCycles = cyclesCount.get();
                break;
            }
            Thread.sleep(STEP_MS);
        }

        {
            String msg = "Pauses expected = [" + minExpected + "; +inf], actual = " + actualPauses;
            if (minExpected <= actualPauses) {
                System.out.println(msg);
            } else {
                throw new IllegalStateException(msg);
            }
        }

        {
            String msg = "Cycles expected = [" + minExpected + "; +inf], actual = " + actualCycles;
            if (minExpected <= actualCycles) {
                System.out.println(msg);
            } else {
                throw new IllegalStateException(msg);
            }
        }

        {
            long actualPauseDuration = pausesDuration.get();
            long actualCycleDuration = cyclesDuration.get();

            String msg = "Pauses duration (" + actualPauseDuration + ") is expected to be not larger than cycles duration (" + actualCycleDuration + ")";

            if (actualPauseDuration <= actualCycleDuration) {
                System.out.println(msg);
            } else {
                throw new IllegalStateException(msg);
            }
        }
    }
}
