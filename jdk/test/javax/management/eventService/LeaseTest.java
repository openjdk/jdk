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
 * @test LeaseTest.java 1.6 08/01/22
 * @bug 5108776
 * @summary Basic test for Event service leasing.
 * @author Shanliang JIANG
 * @run clean LeaseTest
 * @run build LeaseTest
 * @run main LeaseTest
 */


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.event.EventClientDelegate;
import javax.management.event.EventClientDelegateMBean;
import javax.management.event.EventClientNotFoundException;
import javax.management.event.FetchingEventRelay;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class LeaseTest {

    private static MBeanServer mbeanServer = MBeanServerFactory.createMBeanServer();
    private static List<Notification> notifList = new ArrayList<Notification>();
    private static ObjectName emitter;
    private static NotificationEmitter emitterImpl;
    private static JMXServiceURL url;
    private static JMXConnectorServer server;
    private static JMXConnector conn;
    private static Listener listener = new Listener();

    private static long leaseTime = 100;
    private static final int multiple = 5;
    private static final long bigWaiting = 6000;

    public static void main(String[] args) throws Exception {
        System.out.println(">>> Test the event service lease");

        // for 1.5
        if (System.getProperty("java.version").startsWith("1.5") &&
                !mbeanServer.isRegistered(EventClientDelegateMBean.OBJECT_NAME)) {
            System.out.print("Working on "+System.getProperty("java.version")+
                    " register "+EventClientDelegateMBean.OBJECT_NAME);

            mbeanServer.registerMBean(EventClientDelegate.
                    getEventClientDelegate(mbeanServer),
                    EventClientDelegateMBean.OBJECT_NAME);
        }

        System.setProperty("com.sun.event.lease.time",
                String.valueOf(leaseTime));
        emitter = new ObjectName("Default:name=NotificationEmitter");
        emitterImpl = new NotificationEmitter();
        mbeanServer.registerMBean(emitterImpl, emitter);

        String[] types = new String[]{"PushingEventRelay", "FetchingEventRelay"};
        String[] protos = new String[]{"rmi", "iiop", "jmxmp"};
        for (String prot : protos) {
            url = new JMXServiceURL(prot, null, 0);

            try {
                server =
                JMXConnectorServerFactory.newJMXConnectorServer(url,
                null, mbeanServer);
                server.start();
            } catch (Exception e) {
                System.out.println(">>> Skip "+prot+", not support.");
                continue;
            }

            url = server.getAddress();

            try {
                for (String type: types) {
                    test(type);
                }
            } finally {
                server.stop();
            }
        }
    }

    private static void test(String type) throws Exception {
        System.out.println("\n\n>>> Testing "+type+" on "+url+" ...");
        newConn();
        EventClient ec = newEventClient(type);

        ec.addNotificationListener(emitter,
                listener, null, null);

        System.out.println(">>> Send a notification and should receive it.");
        emitterImpl.sendNotif(++counter);

        if (!waitNotif(bigWaiting, counter)) {
            throw new RuntimeException(">>> Failed to receive notif.");
        }

        System.out.println(">>> Sleep 3 times of requested lease time.");
        Thread.sleep(leaseTime*3);
        System.out.println(">>> Send again a notification and should receive it.");
        emitterImpl.sendNotif(++counter);

        if (!waitNotif(bigWaiting, counter)) {
            throw new RuntimeException(">>> Failed to receive notif.");
        }

        System.out.println(">>> Close the client connection: "+
                conn.getConnectionId());
        conn.close();

        System.out.println(">>> Waiting lease timeout to do clean.");

        if (!emitterImpl.waitingClean(leaseTime*multiple)) {
            throw new RuntimeException(
                    ">>> The event lease failed to do clean: "+
                    emitterImpl.listenerSize);
        } else {
            System.out.println(">>> The listener has been removed.");
        }

        // Check that the client id has indeed been removed, by trying to
        // remove it again, which should fail.
        newConn();
        try {
            EventClientDelegateMBean proxy =
                EventClientDelegate.getProxy(conn.getMBeanServerConnection());
            proxy.removeClient(ec.getEventRelay().getClientId());

            throw new RuntimeException(
                    ">>> The client id is not removed.");
        } catch (EventClientNotFoundException ecnfe) {
            // OK
            System.out.println(">>> The client id has been removed.");
        }
        conn.close();

        System.out.println(">>> Reconnect to the server.");
        newConn();

        System.out.println(">>> Create a new EventClient and add the listeners" +
                " in the failed EventClient into new EventClient");
        EventClient newEC = newEventClient(type);
        newEC.addListeners(ec.getListeners());
        // We expect ec.close() to get IOException because we closed the
        // underlying connection.
        try {
            ec.close();
            throw new RuntimeException(">>> EventClient.close did not throw " +
                    "expected IOException");
        } catch (IOException e) {
            System.out.println(">>> EventClient.close threw expected exception: " + e);
        }

        emitterImpl.sendNotif(++counter);

        if (!waitNotif(bigWaiting, counter)) {
            throw new RuntimeException(">>> The event client failed to add " +
                    "all old registered listeners after re-connection.");
        } else {
            System.out.println(">>> Successfully received notification from" +
                    " new EventClient.");
        }

        System.out.println(">>> Clean the failed EventClient.");
        ec.close();
        if (ec.getListeners().size() != 0) {
            throw new RuntimeException(">>> The event client fails to do clean.");
        }

        System.out.println(">>> Clean the new EventClient.");
        newEC.close();
        if (newEC.getListeners().size() != 0) {
            throw new RuntimeException(">>> The event client fails to do clean.");
        }

        conn.close();
        System.out.println(">>> Testing "+type+" on "+url+" ... done");
    }

    private static boolean waitNotif(long time, int sequenceNumber)
    throws Exception {
        synchronized(notifList) {
            if (search(sequenceNumber)) {
                return true;
            }

            long stopTime = System.currentTimeMillis() + time;
            long toWait = time;
            while (toWait > 0) {
                notifList.wait(toWait);

                if (search(sequenceNumber)) {
                    return true;
                }

                toWait = stopTime - System.currentTimeMillis();
            }

            return false;
        }
    }

    private static boolean search(int sequenceNumber) {
        while(notifList.size() > 0) {
            Notification n = notifList.remove(0);
            if (n.getSequenceNumber() == sequenceNumber) {
                return true;
            }
        }

        return false;
    }

//--------------------------
// private classes
//--------------------------

    private static class Listener implements NotificationListener {
        public void handleNotification(Notification notif, Object handback) {
            synchronized (notifList) {
                notifList.add(notif);
                notifList.notify();
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
        public void sendNotif(int sequenceNumber) {
            Notification notif = new Notification(myType, this, sequenceNumber);
            sendNotification(notif);
        }

        public void addNotificationListener(NotificationListener listener,
                NotificationFilter filter, Object handback) {
            super.addNotificationListener(listener, filter, handback);

            listenerSize++;
        }

        public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
            super.removeNotificationListener(listener);
            listenerSize--;

            synchronized(this) {
                if (listenerSize == 0) {
                    this.notifyAll();
                }
            }
        }

        public void removeNotificationListener(NotificationListener listener,
                NotificationFilter filter, Object handback)
                throws ListenerNotFoundException {
            super.removeNotificationListener(listener, filter, handback);
            listenerSize--;

            synchronized(this) {
                if (listenerSize == 0) {
                    this.notifyAll();
                }
            }
        }

        public boolean waitingClean(long timeout) throws Exception {
            synchronized(this) {
                long stopTime = System.currentTimeMillis() + timeout;
                long toWait = timeout;
                while (listenerSize != 0 && toWait > 0) {
                    this.wait(toWait);
                    toWait = stopTime - System.currentTimeMillis();
                }
            }

            return listenerSize == 0;
        }

        public int listenerSize = 0;

        private final String myType = "notification.my_notification";
    }

    public interface NotificationEmitterMBean {
        public void sendNotif(int sequenceNumber);
    }

    private static void newConn() throws IOException {
        conn = JMXConnectorFactory.connect(url);
    }

    private static EventClient newEventClient(String type) throws Exception {
        EventClientDelegateMBean proxy =
                EventClientDelegate.getProxy(conn.getMBeanServerConnection());
        if (type.equals("PushingEventRelay")) {
            return new EventClient(proxy,
                    new FetchingEventRelay(proxy), null, null, leaseTime);
        } else if (type.equals("FetchingEventRelay")) {
            return new EventClient(proxy,
                    new FetchingEventRelay(proxy), null, null, leaseTime);
        } else {
            throw new RuntimeException("Wrong event client type: "+type);
        }
    }

    private static int counter = 0;
}
