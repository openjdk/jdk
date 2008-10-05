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
 * @test
 * @bug 5108776
 * @summary Basic test for EventClient.
 * @author Shanliang JIANG
 * @run clean FetchingTest MyFetchingEventForwarder
 * @run build FetchingTest MyFetchingEventForwarder
 * @run main FetchingTest MyFetchingEventForwarder
 */

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.event.EventClientDelegate;
import javax.management.event.EventClientDelegateMBean;
import javax.management.event.FetchingEventRelay;
import javax.management.event.RMIPushEventForwarder;
import javax.management.event.RMIPushServer;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class FetchingTest {
    private static MBeanServer mbeanServer;
    private static ObjectName emitter;
    private static JMXServiceURL url;
    private static JMXConnectorServer server;
    private static JMXConnector conn;
    private static MBeanServerConnection client;
    private static long WAITING_TIME = 6000;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {

        System.out.println(">>> FetchingTest-main basic tests ...");
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
        mbeanServer.registerMBean(new NotificationEmitter(), emitter);
        boolean succeed = true;

        final String[] protos = new String[] {"rmi", "iiop", "jmxmp"};
        for (String proto : protos) {
            System.out.println(">>> FetchingTest-main: testing on "+proto);

            try {
                url = new JMXServiceURL(proto, null, 0) ;
                server = JMXConnectorServerFactory.
                        newJMXConnectorServer(url, null, mbeanServer);
                server.start();
            } catch (Exception e) {
                // OK
                System.out.println(">>> FetchingTest-main: skip the proto "+proto);
                continue;
            }

            url = server.getAddress();
            conn = JMXConnectorFactory.connect(url, null);
            client = conn.getMBeanServerConnection();

            succeed &= test();

            conn.close();
            server.stop();

            System.out.println(
                    ">>> FetchingTest-main: testing on "+proto+" done.");
        }

        if (succeed) {
            System.out.println(">>> FetchingTest-main: PASSED!");
        } else {
            System.out.println("\n>>> FetchingTest-main: FAILED!");
            System.exit(1);
        }
    }

    public static boolean test() throws Exception {
        System.out.println(">>> FetchingTest-test: " +
                "using the default fetching forwarder ...");
        EventClient eventClient =
                new EventClient(client);

        Listener listener = new Listener();
        eventClient.addNotificationListener(emitter, listener, null, null);

        // ask to send notifs
        Object[] params = new Object[] {new Integer(sendNB)};
        String[] signatures = new String[] {"java.lang.Integer"};
        conn.getMBeanServerConnection().invoke(emitter,
                "sendNotifications", params, signatures);

        if (listener.waitNotif(WAITING_TIME) != sendNB) {
            System.out.println(
                    ">>> FetchingTest-test: FAILED! Expected to receive "+
                    sendNB+", but got "+listener.received);

            return false;
        }

        System.out.println(
                ">>> ListenerTest-test: got all expected "+listener.received);
        //eventClient.removeNotificationListener(emitter, listener);
        eventClient.close();

        System.out.println(">>> FetchingTest-test: " +
                "using a user specific List ...");

        FetchingEventRelay fer = new FetchingEventRelay(
                EventClientDelegate.getProxy(client),
                1000, 1000L, 1000, null,
                MyFetchingEventForwarder.class.getName(),
                null, null);

        eventClient = new EventClient(
                EventClientDelegate.getProxy(client), fer, null, null, 10000);

        eventClient.addNotificationListener(emitter, listener, null, null);
        listener.received = 0;

        conn.getMBeanServerConnection().invoke(emitter,
                "sendNotifications", params, signatures);

        if (listener.waitNotif(WAITING_TIME) != sendNB) {
            System.out.println(
                    ">>> FetchingTest-test: FAILED! Expected to receive "+
                    sendNB+", but got "+listener.received);

            return false;
        }

        System.out.println(
                ">>> FetchingTest-test: got all expected "+listener.received);

        if (!MyFetchingEventForwarder.shared.isUsed()) {
            System.out.println(
                    ">>> FetchingTest-test: FAILED! The user specific list" +
                        "is not used!");

            return false;
        }

        System.out.println(">>> Negative test to add an EventClient" +
                " with a non EventForwarder object.");
        try {
            MyFetchingEventForwarder.shared.setAgain();

            System.out.println(
                    ">>> FetchingTest-test: FAILED! No expected exception" +
                    "when setting the list after the forwarder started.");

            return false;
        } catch (IllegalStateException ise) {
            // OK
            System.out.println(
                    ">>> FetchingTest-test: Got expected exception: " + ise);
        }

        eventClient.close();

        try {
            fer = new FetchingEventRelay(
                EventClientDelegate.getProxy(client),
                1000, 1000L, 1000, null,
                Object.class.getName(),
                null, null);

                eventClient = new EventClient(
                        EventClientDelegate.getProxy(client), fer, null, null, 10000);

                System.out.println(
                    ">>> FetchingTest-test: FAILED! No expected exception" +
                    "when creating an illegal EventForwarder");
        } catch (IllegalArgumentException iae) {
            // OK
            // iae.printStackTrace();
        }

        return true;
    }

    private static class Listener implements NotificationListener {
        public void handleNotification(Notification notif, Object handback) {
            synchronized(this) {
                if (++received >= sendNB) {
                    this.notify();
                }
            }

            //System.out.println(">>> FetchingTest-Listener: received = "+received);
        }

        public int waitNotif(long timeout) throws Exception {
            synchronized(this) {
                long stopTime = System.currentTimeMillis() + timeout;
                long toWait = timeout;
                while (toWait > 0 && received < sendNB) {
                        this.wait(toWait);
                    toWait = stopTime - System.currentTimeMillis();
                }
            }

            return received;
        }

        public static int received = 0;
    }

    public static class NotificationEmitter extends NotificationBroadcasterSupport
            implements NotificationEmitterMBean {

        public void sendNotifications(Integer nb) {
            System.out.println(
                    ">>> FetchingTest-NotificationEmitter-sendNotifications: "+nb);
            Notification notif;
            for (int i=1; i<=nb.intValue(); i++) {
                notif = new Notification(myType, this, count++);
                sendNotification(notif);
            }
        }
    }

    public interface NotificationEmitterMBean {
        public void sendNotifications(Integer nb);
    }



    private static int sendNB = 20;
    private static int count = 0;

    private static final String myType = "notification.my_notification";
}
