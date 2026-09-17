/*
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
 * @test id=generational
 * @bug 8391591
 * @summary Humongous allocation failure should recover without full GC.
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @run main/othervm
 *      -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC
 *      -XX:ShenandoahGCMode=generational -Xmx128m -Xms128m
 *      -XX:ShenandoahRegionSize=1m -XX:+AlwaysPreTouch
 *      -XX:ConcGCThreads=1 -XX:ParallelGCThreads=1
 *      TestHumongousAllocFailureRecovery
 */

import com.sun.management.GarbageCollectionNotificationInfo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

public class TestHumongousAllocFailureRecovery {
    private static final int MB = 1024 * 1024;
    private static final int HUMONGOUS_SIZE_MB = 32;
    private static final int ITERATIONS = 16;
    private static Object sink;

    private static boolean isCollectorNotification(Notification n) {
        return n.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION);
    }

    private static void subscribeToCollectorNotifications(NotificationListener listener) {
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            ((NotificationEmitter) b).addNotificationListener(listener, null, null);
        }
    }

    private static void unsubscribeToCollectorNotifications(NotificationListener listener) throws Exception {
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            ((NotificationEmitter) b).removeNotificationListener(listener, null, null);
        }
    }

    private static boolean isFullGC(GarbageCollectionNotificationInfo info) {
        return info.getGcName().equals("Shenandoah Pauses")
            && info.getGcAction().contains("Full");
    }

    private static boolean isSystemGC(GarbageCollectionNotificationInfo info) {
        return info.getGcCause().contains("System.gc");
    }

    public static void main(String[] args) throws Exception {
        final List<String> unexpectedFullGCs = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch sawSystemGC = new CountDownLatch(1);

        NotificationListener listener = (Notification n, Object o) -> {
            if (isCollectorNotification(n)) {
                GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
                if (isSystemGC(info)) {
                    sawSystemGC.countDown();
                } else if (isFullGC(info)) {
                    unexpectedFullGCs.add(info.getGcCause());
                }
            }
        };

        subscribeToCollectorNotifications(listener);

        for (int i = 0; i < ITERATIONS; i++) {
            // Requests 33 contiguous regions. We're likely to hit humongous allocation failure
            // because the GC can't keep up with the allocations.
            sink = new byte[MB * HUMONGOUS_SIZE_MB];
        }

        // Invoke an explicit GC. When our stream listener hears this, we know the test is complete.
        System.gc();

        if (!sawSystemGC.await(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timed out waiting for System.gc");
        }

        unsubscribeToCollectorNotifications(listener);

        if (!unexpectedFullGCs.isEmpty()) {
            throw new RuntimeException("Unexpected full GCs: " + unexpectedFullGCs);
        }
    }
}
