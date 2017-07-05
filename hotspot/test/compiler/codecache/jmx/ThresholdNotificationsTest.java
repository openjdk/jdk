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
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import sun.hotspot.code.BlobType;

/*
 * @test ThresholdNotificationsTest
 * @library /testlibrary /test/lib
 * @modules java.base/sun.misc
 *          java.management
 * @build ThresholdNotificationsTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *     sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:-UseCodeCacheFlushing
 *     -XX:-MethodFlushing -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:+SegmentedCodeCache -XX:CompileCommand=compileonly,null::*
 *     ThresholdNotificationsTest
 * @summary testing of getUsageThreshold()
 */
public class ThresholdNotificationsTest implements NotificationListener {

    private final static long WAIT_TIME = 10000L;
    private volatile long counter;
    private final BlobType btype;

    public static void main(String[] args) {
        for (BlobType bt : BlobType.getAvailable()) {
            new ThresholdNotificationsTest(bt).runTest();
        }
    }

    public ThresholdNotificationsTest(BlobType btype) {
        this.btype = btype;
        counter = 0L;
        CodeCacheUtils.disableCollectionUsageThresholds();
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
            if (poolName.equals(btype.getMemoryPool().getName())) {
                counter++;
            }
        }
    }

    protected void runTest() {
        int iterationsCount =
            Integer.getInteger("jdk.test.lib.iterations", 1);
        MemoryPoolMXBean bean = btype.getMemoryPool();
        ((NotificationEmitter) ManagementFactory.getMemoryMXBean()).
                addNotificationListener(this, null, null);
        for (int i = 0; i < iterationsCount; i++) {
            CodeCacheUtils.hitUsageThreshold(bean, btype);
        }
        Asserts.assertTrue(
                Utils.waitForCondition(
                        () -> (CodeCacheUtils.isCodeHeapPredictable(btype) ?
                                (counter == iterationsCount) : (counter >= iterationsCount)),
                        WAIT_TIME),
                "Couldn't receive expected notifications count");
        try {
            ((NotificationEmitter) ManagementFactory.getMemoryMXBean()).
                    removeNotificationListener(this);
        } catch (ListenerNotFoundException ex) {
            throw new AssertionError("Can't remove notification listener", ex);
        }
        System.out.printf("INFO: Scenario finished successfully for %s%n",
                bean.getName());
    }
}
