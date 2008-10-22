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
 * @test EventManagerTest.java 1.8 08/01/22
 * @bug 5108776
 * @summary Basic test for EventManager.
 * @author Shanliang JIANG
 * @run clean EventManagerTest
 * @run build EventManagerTest
 * @run main EventManagerTest
 */

import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 */
public class EventManagerTest {
    private static MBeanServer mbeanServer;
    private static ObjectName emitter;
    private static JMXServiceURL url;
    private static JMXConnectorServer server;
    private static JMXConnector conn;
    private static MBeanServerConnection client;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        System.out.println(">>> EventManagerTest-main basic tests ...");
        mbeanServer = MBeanServerFactory.createMBeanServer();

        // for 1.5
        if (System.getProperty("java.version").startsWith("1.5") &&
                !mbeanServer.isRegistered(EventClientDelegateMBean.OBJECT_NAME)) {
            System.out.print("Working on "+System.getProperty("java.version")+
                    " register "+EventClientDelegateMBean.OBJECT_NAME);

            mbeanServer.registerMBean(EventClientDelegate.
                    getEventClientDelegate(mbeanServer),
                    EventClientDelegateMBean.OBJECT_NAME);
        }

        emitter = new ObjectName("Default:name=NotificationEmitter");

        url = new JMXServiceURL("rmi", null, 0) ;
        server =
                JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbeanServer);
        server.start();

        url = server.getAddress();
        conn = JMXConnectorFactory.connect(url, null);
        client = conn.getMBeanServerConnection();

        mbeanServer.registerMBean(new NotificationEmitter(), emitter);

        boolean succeed;

        System.out.println(">>> EventManagerTest-main: using the fetching EventRelay...");
        succeed = test(new EventClient(client));

        System.out.println(">>> EventManagerTest-main: using the pushing EventRelay...");
        EventClientDelegateMBean ecd = EventClientDelegate.getProxy(client);
        succeed &= test(new EventClient(ecd,
                new RMIPushEventRelay(ecd),
                null, null,
                EventClient.DEFAULT_LEASE_TIMEOUT));

        conn.close();
        server.stop();

        if (succeed) {
            System.out.println(">>> EventManagerTest-main: PASSE!");
        } else {
            System.out.println("\n>>> EventManagerTest-main: FAILED!");
            System.exit(1);
        }
    }

    public static boolean test(EventClient efClient) throws Exception {
        // add listener from the client side
        Listener listener = new Listener();
        efClient.subscribe(emitter, listener, null, null);

        // ask to send notifs
        Object[] params = new Object[] {new Integer(sendNB)};
        String[] signatures = new String[] {"java.lang.Integer"};
        client.invoke(emitter, "sendNotifications", params, signatures);

        // waiting
        long toWait = 6000;
        long stopTime = System.currentTimeMillis() + toWait;

        synchronized(listener) {
            while(listener.received < sendNB && toWait > 0) {
                listener.wait(toWait);
                toWait = stopTime - System.currentTimeMillis();
            }
        }

        // clean
        System.out.println(">>> EventManagerTest-test: cleaning...");
        efClient.unsubscribe(emitter, listener);
        efClient.close();

        if (listener.received != sendNB) {
            System.out.println(">>> EventManagerTest-test: FAILED! Expected to receive "+sendNB+", but got "+listener.received);

            return false;
        } else if (listener.seqErr > 0) {
            System.out.println(">>> EventManagerTest-test: FAILED! The receiving sequence is not correct.");

            return false;
        } else {
            System.out.println(">>> EventManagerTest-test: got all expected "+listener.received);
            return true;
        }
    }

    private static class Listener implements NotificationListener {
        public int received = 0;
        public int seqErr = 0;

        private long lastSeq = -1;

        public void handleNotification(Notification notif, Object handback) {
            if (!myType.equals(notif.getType())) {
                System.out.println(">>> EventManagerTest-Listener: got unexpected notif: "+notif);
                System.exit(1);
            }

            if (lastSeq == -1) {
                lastSeq = notif.getSequenceNumber();
            } else if (notif.getSequenceNumber() - lastSeq++ != 1) {
                seqErr++;
            }

            //System.out.println(">>> EventManagerTest-Listener: got notif "+notif.getSequenceNumber());

            synchronized(this) {
                if (++received >= sendNB) {
                    this.notify();
                }
            }
        }
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
         * Send Notification objects.
         *
         * @param nb The number of notifications to send
         */
        public void sendNotifications(Integer nb) {
            Notification notif;
            for (int i=1; i<=nb.intValue(); i++) {
                notif = new Notification(myType, this, count++);
                notif.setUserData("jsl");
                //System.out.println(">>> EventManagerService-NotificationEmitter-sendNotifications: "+i);

                sendNotification(notif);
            }
        }
    }

    public interface NotificationEmitterMBean {
        public void sendNotifications(Integer nb);
    }

    private static int sendNB = 120;
    private static long count = 0;

    private static final String myType = "notification.my_notification";
}
