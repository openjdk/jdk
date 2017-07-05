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
 * @test SubUnsubTest
 * @bug 6736611
 * @summary Test not to remove other listeners when calling unsubscribe
 * @author Shanliang JIANG
 * @run clean SubUnsubTest
 * @run build SubUnsubTest
 * @run main SubUnsubTest
 */

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventSubscriber;
import javax.management.event.EventClient;
public class SubUnsubTest {
    private static class CountListener implements NotificationListener {
        volatile int count;

        public void handleNotification(Notification n, Object h) {
            count++;
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
        System.out.println("Testing EventSubscriber-unsubscribe method.");

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
        CountListener listener = new CountListener();

        System.out.println("Subscribing and adding listeners ...");
        sub.subscribe(pattern, listener, null, null);
        sub.subscribe(name2, listener, null, null);
        mbs.addNotificationListener(name2, listener, null, null);

        sender1.send();
        sender2.send();
        if (listener.count != 4) {
            throw new RuntimeException("Do not receive all notifications: "+
                    "Expect 4, got "+listener.count);
        }

        System.out.println("Unsubscribe the listener with the pattern.");
        sub.unsubscribe(pattern, listener);
        listener.count = 0;
        sender1.send();
        sender2.send();
        if (listener.count != 2) {
            throw new RuntimeException("The method unsubscribe removes wrong listeners.");
        }

        System.out.println("Unsubscribe the listener with the ObjectName.");
        sub.unsubscribe(name2, listener);
        listener.count = 0;
        sender1.send();
        sender2.send();
        if (listener.count != 1) {
            throw new RuntimeException("The method unsubscribe removes wrong listeners.");
        }

        System.out.println("Subscribe twice to same MBean with same listener " +
                "but different handback.");
        sub.subscribe(name1, listener, null, new Object());
        sub.subscribe(name1, listener, null, new Object());
        listener.count = 0;

        sub.unsubscribe(name1, listener);
        sender1.send();
        if (listener.count > 0) {
            throw new RuntimeException("EventSubscriber: the method unsubscribe" +
                    " does not remove a listener which was subscribed 2 times.");
        }

        System.out.println("Bye bye!");
        return;
    }
}