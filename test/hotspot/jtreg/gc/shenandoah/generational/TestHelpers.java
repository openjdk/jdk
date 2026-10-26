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

import java.io.Closeable;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;
import jdk.test.whitebox.WhiteBox;

class TestHelpers {
    interface GcNotificationListener {
        void handleNotification(GarbageCollectionNotificationInfo info);
    }

    public static void logEvent(GarbageCollectionNotificationInfo event) {
        System.out.println(event.getGcAction() + ", " + event.getGcCause() + ", " + event.getGcName());
    }

    static class ScopedListener implements Closeable {
        private final NotificationListener handle;
        private final CountDownLatch sawSystemGC;

        ScopedListener() {
            this(TestHelpers::logEvent);
        }

        ScopedListener(final GcNotificationListener gcListener) {
            sawSystemGC = new CountDownLatch(1);
            handle = (notification, handback) -> {
                if (isCollectorNotification(notification)) {
                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                    if (isSystemGC(info)) {
                        sawSystemGC.countDown();
                    }

                    gcListener.handleNotification(info);
                }
            };

            TestHelpers.subscribeToCollectorNotifications(handle);
        }

        @Override
        public void close() throws IOException {
            try {
                System.gc();

                if (!sawSystemGC.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Timed out waiting for end of gc event stream");
                }

                TestHelpers.unsubscribeToCollectorNotifications(handle);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    static void promote(List<?> objects) {
        final int maxTries = 5;
        int tries = 0;
        while (!allAreInOld(objects) && tries < maxTries) {
            WB.fullGC();
            ++tries;
        }

        assert allAreInOld(objects) : "Not all objects were promoted";
    }

    static boolean allAreInOld(List<?> objects) {
        for (Object obj : objects) {
            if (!WB.isObjectInOldGen(obj)) {
                return false;
            }
        }
        return true;
    }

    static boolean isCollectorNotification(Notification n) {
        return n.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION);
    }

    static void subscribeToCollectorNotifications(NotificationListener listener) {
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            ((NotificationEmitter) b).addNotificationListener(listener, null, null);
        }
    }

    static void unsubscribeToCollectorNotifications(NotificationListener listener) throws Exception {
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            ((NotificationEmitter) b).removeNotificationListener(listener, null, null);
        }
    }

    static boolean isSystemGC(GarbageCollectionNotificationInfo info) {
        return info.getGcCause().contains("System.gc");
    }
}
