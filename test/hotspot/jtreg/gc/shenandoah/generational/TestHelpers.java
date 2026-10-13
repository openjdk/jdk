package gc.shenandoah.generational;

import java.io.Closeable;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.management.GarbageCollectionNotificationInfo;
import jdk.test.whitebox.WhiteBox;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

class TestHelpers {
    interface GcNotificationListener {
        void handleNotification(GarbageCollectionNotificationInfo info);
    }

    public static class EventLogger implements GcNotificationListener {
        @Override
        public void handleNotification(GarbageCollectionNotificationInfo event) {
            System.out.println(event.getGcAction() + ", " + event.getGcCause() + ", " + event.getGcName());
        }
    }

    static class ScopedListener implements Closeable {
        private final NotificationListener handle;
        private final CountDownLatch sawSystemGC;

        ScopedListener() {
            this(new EventLogger());
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
