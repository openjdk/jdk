/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import sun.hotspot.code.BlobType;

/*
 * @test PoolsIndependenceTest
 * @library /testlibrary /test/lib
 * @build PoolsIndependenceTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *     sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:-UseCodeCacheFlushing
 *     -XX:-MethodFlushing -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:+SegmentedCodeCache PoolsIndependenceTest
 * @summary testing of getUsageThreshold()
 */
public class PoolsIndependenceTest implements NotificationListener {

    private final Map<String, AtomicInteger> counters;
    private final BlobType btype;
    private volatile long lastEventTimestamp;

    public PoolsIndependenceTest(BlobType btype) {
        counters = new HashMap<>();
        for (BlobType bt : BlobType.getAvailable()) {
            counters.put(bt.getMemoryPool().getName(), new AtomicInteger(0));
        }
        this.btype = btype;
        lastEventTimestamp = 0;
        CodeCacheUtils.disableCollectionUsageThresholds();
    }

    public static void main(String[] args) {
        for (BlobType bt : BlobType.getAvailable()) {
            new PoolsIndependenceTest(bt).runTest();
        }
    }

    protected void runTest() {
        MemoryPoolMXBean bean = btype.getMemoryPool();
        ((NotificationEmitter) ManagementFactory.getMemoryMXBean()).
                addNotificationListener(this, null, null);
        bean.setUsageThreshold(bean.getUsage().getUsed() + 1);
        long beginTimestamp = System.currentTimeMillis();
        CodeCacheUtils.WB.allocateCodeBlob(
                CodeCacheUtils.ALLOCATION_SIZE, btype.id);
        CodeCacheUtils.WB.fullGC();
        /* waiting for expected event to be received plus double the time took
         to receive expected event(for possible unexpected) and
         plus 1 second in case expected event received (almost)immediately */
        Utils.waitForCondition(() -> {
            long currentTimestamp = System.currentTimeMillis();
            int eventsCount
                    = counters.get(btype.getMemoryPool().getName()).get();
            if (eventsCount > 0) {
                if (eventsCount > 1) {
                    return true;
                }
                long timeLastEventTook
                        = beginTimestamp - lastEventTimestamp;
                long timeoutValue
                        = 1000L + beginTimestamp + 3L * timeLastEventTook;
                return currentTimestamp > timeoutValue;
            }
            return false;
        });
        for (BlobType bt : BlobType.getAvailable()) {
            int expectedNotificationsAmount = bt.equals(btype) ? 1 : 0;
            CodeCacheUtils.assertEQorGTE(btype, counters.get(bt.getMemoryPool().getName()).get(),
                    expectedNotificationsAmount, String.format("Unexpected "
                            + "amount of notifications for pool: %s",
                            bt.getMemoryPool().getName()));
        }
        try {
            ((NotificationEmitter) ManagementFactory.getMemoryMXBean()).
                    removeNotificationListener(this);
        } catch (ListenerNotFoundException ex) {
            throw new AssertionError("Can't remove notification listener", ex);
        }
        System.out.printf("INFO: Scenario with %s finished%n", bean.getName());
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        String nType = notification.getType();
        String poolName
                = CodeCacheUtils.getPoolNameFromNotification(notification);
        // consider code cache events only
        if (CodeCacheUtils.isAvailableCodeHeapPoolName(poolName)) {
            Asserts.assertEQ(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED,
                    nType, "Unexpected event received: " + nType);
            // receiving events from available CodeCache-related beans only
            if (counters.get(poolName) != null) {
                counters.get(poolName).incrementAndGet();
                lastEventTimestamp = System.currentTimeMillis();
            }
        }
    }
}
