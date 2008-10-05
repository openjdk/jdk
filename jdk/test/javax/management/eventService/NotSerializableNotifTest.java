/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test NotSerializableNotifTest.java 1.5 08/01/22
 * @bug 5108776
 * @summary Basic test for EventClient.
 * @author Shanliang JIANG
 * @run clean NotSerializableNotifTest
 * @run build NotSerializableNotifTest
 * @run main NotSerializableNotifTest
 */


// JMX imports
//
import javax.management.* ;
import javax.management.event.EventClient;
import javax.management.event.EventClientDelegate;
import javax.management.event.EventClientDelegateMBean;
import javax.management.event.EventRelay;
import javax.management.event.FetchingEventRelay;

import javax.management.remote.*;
import javax.management.remote.JMXServiceURL;

public class NotSerializableNotifTest {
    private static MBeanServer mbeanServer =
        MBeanServerFactory.createMBeanServer();
    private static ObjectName emitter;
    private static int port = 2468;

    private static String[] protocols;

    private static final int sentNotifs = 50;

    public static void main(String[] args) throws Exception {
        System.out.println(">>> Test to send a not serializable notification");

        // for 1.5
        if (System.getProperty("java.version").startsWith("1.5") &&
                !mbeanServer.isRegistered(EventClientDelegateMBean.OBJECT_NAME)) {
            System.out.print("Working on "+System.getProperty("java.version")+
                    " register "+EventClientDelegateMBean.OBJECT_NAME);

            mbeanServer.registerMBean(EventClientDelegate.
                    getEventClientDelegate(mbeanServer),
                    EventClientDelegateMBean.OBJECT_NAME);
        }

        NotificationEmitter nm = new NotificationEmitter();
        emitter = new ObjectName("Default:name=NotificationEmitter");
        mbeanServer.registerMBean(nm, emitter);
        String proto = "rmi";

        System.out.println(">>> Test for protocol " + proto);

        JMXServiceURL url = new JMXServiceURL(proto, null, 0);

        JMXConnectorServer server =
            JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbeanServer);

        server.start();

        url = server.getAddress();
        JMXConnector conn = JMXConnectorFactory.connect(url, null);
        MBeanServerConnection client = conn.getMBeanServerConnection();

        EventClientDelegateMBean ecd = EventClientDelegate.getProxy(client);
        EventRelay eventRelay = new FetchingEventRelay(
                ecd,
                FetchingEventRelay.DEFAULT_BUFFER_SIZE,
                10,
                FetchingEventRelay.DEFAULT_MAX_NOTIFICATIONS,
                null);
        EventClient ec = new EventClient(ecd, eventRelay, null, null,
                EventClient.DEFAULT_LEASE_TIMEOUT);

        // add listener from the client side
        Listener listener = new Listener();
        ec.addNotificationListener(emitter, listener, null, null);

        LostListener lostListener = new LostListener();
        ec.addEventClientListener(lostListener, null, null);

        // ask to send one not serializable notif
        System.out.println(">>> sending not serializable notifs ...");

        Object[] params = new Object[] {new Integer(sentNotifs)};
        String[] signatures = new String[] {"java.lang.Integer"};
        client.invoke(emitter, "sendNotserializableNotifs", params, signatures);

//      nm.sendNotserializableNotifs(sentNotifs);
//      nm.sendNotifications(1);

        // waiting
        synchronized(lostListener) {
            if (lostListener.lostCount != sentNotifs) {
                lostListener.wait(6000);
            }
        }

        Thread.sleep(100);

        if (lostListener.lostCount != sentNotifs) {
            System.out.println(">>> FAILED. Expected "+sentNotifs+", but got "+lostListener.lostCount);
            System.exit(1);
        }

        System.out.println(">>> Passed.");

        ec.close();
        conn.close();
        server.stop();
    }


//--------------------------
// private classes
//--------------------------
    private static class Listener implements NotificationListener {
        public void handleNotification(Notification n, Object handback) {
            System.out.println(">>> Listener: receive: "+n);
        }
    }


    private static class LostListener implements NotificationListener {
        public void handleNotification(Notification n, Object handback) {
             if (!EventClient.NOTIFS_LOST.equals(n.getType())) {
                return;
            }

            if (!(n.getUserData() instanceof Long)) {
                System.out.println(">>> Listener: JMXConnectionNotification userData " +
                                   "not a Long: " + n.getUserData());
                System.exit(1);
            } else {
                int lost = ((Long) n.getUserData()).intValue();
                lostCount += lost;
                if (lostCount >= sentNotifs) {
                    synchronized(this) {
                        this.notifyAll();
                    }
                }
            }

        }


        private int lostCount = 0;
    }

    public static class NotificationEmitter extends NotificationBroadcasterSupport
        implements NotificationEmitterMBean {

        public MBeanNotificationInfo[] getNotificationInfo() {
            final String[] ntfTypes = {myType};

            final MBeanNotificationInfo[] ntfInfoArray  = {
                new MBeanNotificationInfo(ntfTypes,
                                          "javax.management.Notification",
                                          "Notifications sent by the NotificationEmitter")};

            return ntfInfoArray;
        }

        /**
         * Send not serializable Notifications.
         *
         * @param nb The number of notifications to send
         */
        public void sendNotserializableNotifs(Integer nb) {

            Notification notif;
            for (int i=1; i<=nb.intValue(); i++) {
                notif = new Notification(myType, this, i);

                notif.setUserData(new Object());
                sendNotification(notif);
            }
        }

        /**
         * Send Notification objects.
         *
         * @param nb The number of notifications to send
         */
        public void sendNotifications(Integer nb) {
            Notification notif;
            for (int i=1; i<=nb.intValue(); i++) {
                notif = new Notification(myType, this, i);

                sendNotification(notif);
            }
        }

        private final String myType = "notification.my_notification";
    }

    public interface NotificationEmitterMBean {
        public void sendNotifications(Integer nb);

        public void sendNotserializableNotifs(Integer nb);
    }
}
