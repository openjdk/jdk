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

package gc.shenandoah.generational;

import com.sun.management.GarbageCollectionNotificationInfo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import jdk.test.whitebox.WhiteBox;


/*
 * @test id=generational
 * @bug 8390310
 * @requires vm.gc.Shenandoah
 * @summary Aged regions must be promoted in place during an abbreviated cycle
 *          (one that skips the evacuation and update-refs phases).
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -Xms512m -Xmx512m
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahGenerationalMinPIPUsage=1
 *      -XX:ShenandoahOldGarbageThreshold=100
 *      -XX:ShenandoahRegionSize=1m
 *      -XX:ShenandoahImmediateThreshold=0
 *      -XX:ShenandoahGenerationalMinTenuringAge=1
 *      -XX:ShenandoahGenerationalMaxTenuringAge=1
 *      -XX:-DisableExplicitGC -XX:+ExplicitGCInvokesConcurrent
 *      gc.shenandoah.generational.TestPromoteInPlaceDuringAbbreviatedCycle
 */

 /*
  * @test id=generational-always-tenure
  * @bug 8390310
  * @requires vm.gc.Shenandoah
  * @summary Tests that promotion potential is correct when the always tenure
  *          override is in effect (set by WB.fullGC())
  * @library /testlibrary /test/lib /
  * @build jdk.test.whitebox.WhiteBox
  * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
  * @run main/othervm -Xbootclasspath/a:.
  *      -Xms512m -Xmx512m
  *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
  *      -XX:+UnlockExperimentalVMOptions
  *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *      -XX:ShenandoahGenerationalMinPIPUsage=1
  *      -XX:ShenandoahOldGarbageThreshold=100
  *      -XX:ShenandoahRegionSize=1m
  *      -XX:ShenandoahImmediateThreshold=0
  *      -XX:ShenandoahGenerationalMinTenuringAge=2
  *      -XX:ShenandoahGenerationalMaxTenuringAge=2
  *      -XX:-DisableExplicitGC -XX:+ExplicitGCInvokesConcurrent
  *      gc.shenandoah.generational.TestPromoteInPlaceDuringAbbreviatedCycle always-tenure
  */
public class TestPromoteInPlaceDuringAbbreviatedCycle {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    // Make a humongous array (with 1MB regions, this will be humongous with
    // and without compressed oops).
    private static final int HUMONGOUS_REFS = 512 * 1024;

    // Also make a not humongous array to test regular region promotion path
    private static final int REGULAR_REFS = 256;

    // Used to create pure garbage regions to satisfy immediate garbage
    // threshold
    private static final int GARBAGE_BYTES = 2 * 1024 * 1024;

    // Test will fail if our humongous object isn't promoted in this many cycles
    private static final int MAX_CYCLES = 5;

    // Keep references so the arrays under test stay live and age in young.
    private static Object[] humongous;
    private static Object[] regular;

    // Reference used to publish, then drop, the per-cycle garbage (to keep
    // local var from being eliminated)
    private static Object garbage;

    private static boolean isCollectorNotification(Notification n) {
        return n.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION);
    }

    private static boolean isIllegalPause(GarbageCollectionNotificationInfo info) {
      return info.getGcName().equals("Shenandoah Pauses")
          &&  (info.getGcAction().contains("Update Refs")
            || info.getGcAction().contains("Degen")
            || info.getGcAction().contains("Full"));
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

    private static GarbageCollectorMXBean cycleBean() {
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (b.getName().equals("Shenandoah Cycles")) {
                return b;
            }
        }
        throw new IllegalStateException("No \"Shenandoah Cycles\" bean found");
    }

    public static void main(String[] args) throws Exception {
        boolean alwaysTenure = args.length > 0 && "always-tenure".equals(args[0]);
        humongous = new Object[HUMONGOUS_REFS];
        regular = new Object[REGULAR_REFS];

        // Listen for events to detect if a non-abbreviated cycle runs
        final GarbageCollectorMXBean cycles = cycleBean();
        final AtomicLong illegalPauses = new AtomicLong();
        final AtomicLong maxCycleId = new AtomicLong();
        NotificationListener listener = (Notification n, Object o) -> {
            if (isCollectorNotification(n)) {
                GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
                if (isIllegalPause(info)) {
                    illegalPauses.incrementAndGet();
                } else if (info.getGcName().equals("Shenandoah Cycles")) {
                    long gcId = info.getGcInfo().getId();
                    if (gcId > maxCycleId.get()) {
                        maxCycleId.set(gcId);
                    }
                }
            }
        };

        subscribeToCollectorNotifications(listener);

        if (WB.isObjectInOldGen(humongous)) {
            throw new IllegalStateException("Expected young humongous array");
        }

        if (WB.isObjectInOldGen(regular)) {
            throw new IllegalStateException("Expected young regular array");
        }

        for (int cycle = 1; cycle <= MAX_CYCLES; cycle++) {
            // Produce one whole dead region so the upcoming cycle is abbreviated.
            garbage = new byte[GARBAGE_BYTES];
            garbage = null;

            if (alwaysTenure) {
                // This also runs a global cycle, but with an effective tenuring threshold of zero
                WB.fullGC();
            } else {
                // Runs a concurrent global cycle and blocks until it completes.
                System.gc();
            }

            // Both objects are in old, exit the test loop
            if (WB.isObjectInOldGen(humongous) && WB.isObjectInOldGen(regular)) {
                break;
            }
        }

        // Flush gc notification events
        long targetCycles = cycles.getCollectionCount();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (maxCycleId.get() < targetCycles) {
            if (System.nanoTime() > deadlineNanos) {
                throw new RuntimeException("Timed out flushing GC notifications: delivered "
                                           + maxCycleId.get() + " of "
                                           + targetCycles + " cycle notifications");
            }
            Thread.sleep(10);
        }

        unsubscribeToCollectorNotifications(listener);

        if (illegalPauses.get() != 0) {
            throw new RuntimeException(illegalPauses.get() + " non-abbreviated cycles happened");
        }

        if (!WB.isObjectInOldGen(humongous)) {
            throw new RuntimeException("Humongous region was not promoted in place.");
        }

        if (!WB.isObjectInOldGen(regular)) {
            throw new RuntimeException("Regular region was not promoted in place");
        }
    }
}
