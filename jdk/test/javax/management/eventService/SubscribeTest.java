/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 5108776
 * @summary Test that EventSubscriber.subscribe works
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventSubscriber;

public class SubscribeTest {
    private static class CountListener implements NotificationListener {
        volatile int count;

        public void handleNotification(Notification n, Object h) {
            count++;
        }
    }

    private static class SwitchFilter implements NotificationFilter {
        volatile boolean enabled;

        public boolean isNotificationEnabled(Notification n) {
            return enabled;
        }
    }

    public static interface SenderMBean {}

    public static class Sender extends NotificationBroadcasterSupport
            implements SenderMBean {
        void send() {
            Notification n = new Notification("type", this, 1L);
            sendNotification(n);
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name1 = new ObjectName("d:type=Sender,id=1");
        ObjectName name2 = new ObjectName("d:type=Sender,id=2");
        ObjectName pattern = new ObjectName("d:type=Sender,*");
        Sender sender1 = new Sender();
        Sender sender2 = new Sender();
        mbs.registerMBean(sender1, name1);
        mbs.registerMBean(sender2, name2);

        EventSubscriber sub = EventSubscriber.getEventSubscriber(mbs);

        System.out.println("Single subscribe covering both MBeans");
        CountListener listen1 = new CountListener();
        sub.subscribe(pattern, listen1, null, null);
        sender1.send();
        assertEquals("Notifs after sender1 send", 1, listen1.count);
        sender2.send();
        assertEquals("Notifs after sender2 send", 2, listen1.count);

        System.out.println("Unsubscribe");
        sub.unsubscribe(pattern, listen1);
        sender1.send();
        assertEquals("Notifs after sender1 send", 2, listen1.count);

        System.out.println("Subscribe twice to same MBean with same listener " +
                "but different filters");
        SwitchFilter filter1 = new SwitchFilter();
        sub.subscribe(name1, listen1, null, null);
        sub.subscribe(name1, listen1, filter1, null);
        listen1.count = 0;
        sender1.send();
        // switch is off, so only one notif expected
        assertEquals("Notifs after sender1 send", 1, listen1.count);
        filter1.enabled = true;
        sender1.send();
        // switch is on, so two more notifs expected
        assertEquals("Notifs after sender1 send", 3, listen1.count);

        System.out.println("Remove those subscriptions");
        sub.unsubscribe(name1, listen1);
        sender1.send();
        assertEquals("Notifs after sender1 send", 3, listen1.count);
    }

    private static void assertEquals(String what, Object expected, Object actual)
    throws Exception {
        if (!equal(expected, actual)) {
            String msg = "Expected " + expected + "; got " + actual;
            throw new Exception("TEST FAILED: " + what + ": " + msg);
        }
    }

    private static boolean equal(Object x, Object y) {
        if (x == y)
            return true;
        if (x == null)
            return false;
        return (x.equals(y));
    }
}
