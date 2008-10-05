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
 * @test AddRemoveListenerTest.java
 * @bug 5108776
 * @summary Basic test for EventClient to see internal thread management.
 * @author Shanliang JIANG
 * @run clean AddRemoveListenerTest
 * @run build AddRemoveListenerTest
 * @run main AddRemoveListenerTest
 */

import java.io.IOException;
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
import javax.management.event.FetchingEventRelay;
import javax.management.event.RMIPushEventRelay;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;


// This thread creates a single MBean that emits a number of parallel
// sequences of notifications.  Each sequence is distinguished by an id
// and each id corresponds to a thread that is filtering the notifications
// so it only sees its own ones.  The notifications for a given id have
// contiguous sequence numbers and each thread checks that the notifications
// it receives do indeed have these numbers.  If notifications are lost or
// if the different sequences interfere with each other then the test will
// fail.  As an added tweak, a "noise" thread periodically causes notifications
// to be emitted that do not correspond to any sequence and do not have any id.
public class AddRemoveListenerTest {

    private static MBeanServer mbeanServer = MBeanServerFactory.createMBeanServer();
    private static ObjectName emitter;
    private static NotificationSender emitterImpl;
    private static JMXServiceURL url;
    private static JMXConnectorServer server;

    private static int toSend = 100;
    private static final long bigWaiting = 10000;
    private static int counter = 0;
    private static int jobs = 10;
    private static int endedJobs = 0;

    private static volatile String failure;

    public static void main(String[] args) throws Exception {
        System.out.println(">>> Test on multiple adding/removing listeners.");

        // for 1.5
        if (System.getProperty("java.version").startsWith("1.5") &&
                !mbeanServer.isRegistered(EventClientDelegateMBean.OBJECT_NAME)) {
            System.out.print("Working on "+System.getProperty("java.version")+
                    " register "+EventClientDelegateMBean.OBJECT_NAME);

            mbeanServer.registerMBean(EventClientDelegate.
                    getEventClientDelegate(mbeanServer),
                    EventClientDelegateMBean.OBJECT_NAME);
        }

        emitter = new ObjectName("Default:name=NotificationSender");
        emitterImpl = new NotificationSender();
        mbeanServer.registerMBean(emitterImpl, emitter);

        String[] types = new String[]{"PushEventRelay", "FetchingEventRelay"};
        String[] protos = new String[]{"rmi", "iiop", "jmxmp"};
        for (String prot : protos) {
            url = new JMXServiceURL(prot, null, 0);

            try {
                server =
                        JMXConnectorServerFactory.newJMXConnectorServer(url,
                        null, mbeanServer);
                server.start();
            } catch (Exception e) {
                System.out.println(">>> Skip "+prot+", not supported.");
                continue;
            }

            url = server.getAddress();

            // noise
            Thread noise = new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        emitterImpl.sendNotif(1, null);
                        try {
                            Thread.sleep(10);
                        } catch (Exception e) {
                            // OK
                        }
                    }
                }
            });
            noise.setDaemon(true);
            noise.start();

            try {
                for (String type: types) {
                    System.out.println("\n\n>>> Testing "+type+" on "+url+" ...");
                    JMXConnector conn = newConn();
                    try {
                        testType(type, conn);
                    } finally {
                        conn.close();
                        System.out.println(">>> Testing "+type+" on "+url+" ... done");
                    }
                }
            } finally {
                server.stop();
            }
        }
    }

    private static void testType(String type, JMXConnector conn) throws Exception {
        Thread[] threads = new Thread[jobs];
        for (int i=0; i<jobs; i++) {
            threads[i] = new Thread(new Job(type, conn));
            threads[i].setDaemon(true);
            threads[i].start();
        }

        // to wait
        long toWait = bigWaiting*jobs;
        long stopTime = System.currentTimeMillis() + toWait;

        synchronized(AddRemoveListenerTest.class) {
            while (endedJobs < jobs && toWait > 0 && failure == null) {
                AddRemoveListenerTest.class.wait(toWait);
                toWait = stopTime - System.currentTimeMillis();
            }
        }

        if (endedJobs != jobs && failure == null) {
            throw new RuntimeException("Need to set bigger waiting timeout?");
        }

        endedJobs = 0;
    }

    public static class Job implements Runnable {
        public Job(String type, JMXConnector conn) {
            this.type = type;
            this.conn = conn;
        }
        public void run() {
            try {
                test(type, conn);

                synchronized(AddRemoveListenerTest.class) {
                    endedJobs++;
                    if (endedJobs>=jobs) {
                        AddRemoveListenerTest.class.notify();
                    }
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private final String type;
        private final JMXConnector conn;
    }

    private static void test(String type, JMXConnector conn) throws Exception {
        EventClient ec = newEventClient(type, conn);
        try {
            test(type, conn, ec);
        } finally {
            ec.close();
        }
    }

    private static void test(String type, JMXConnector conn, EventClient ec)
            throws Exception {
        String id = getId();

        Listener listener = new Listener(id);
        Filter filter = new Filter(id);

        System.out.println(">>> ("+id+") To receive notifications "+toSend);
        ec.addNotificationListener(emitter,
                listener, filter, null);

        emitterImpl.sendNotif(toSend, id);
        listener.waitNotifs(bigWaiting, toSend);
        if (listener.received != toSend) {
            throw new RuntimeException(">>> ("+id+") Expected to receive: "
                    +toSend+", but got: "+listener.received);
        }

        listener.clear();
        ec.removeNotificationListener(emitter, listener, filter, null);

        System.out.println(">>> ("+id+") Repeat adding and removing ...");
        for (int j=0; j<10; j++) {
            ec.addNotificationListener(emitter, dummyListener, null, id);
            Thread.yield(); // allow to start listening
            ec.removeNotificationListener(emitter, dummyListener, null, id);
        }

        System.out.println(">>> ("+id+") To receive again notifications "+toSend);
        ec.addNotificationListener(emitter,
                listener, filter, null);

        emitterImpl.sendNotif(toSend, id);
        listener.waitNotifs(bigWaiting, toSend);
        Thread.yield(); //any duplicated?
        if (listener.received != toSend) {
            throw new RuntimeException("("+id+") Expected to receive: "
                    +toSend+", but got: "+listener.received);
        }
    }

//--------------------------
// private classes
//--------------------------

    private static class Listener implements NotificationListener {
        public Listener(String id) {
            this.id = id;
        }
        public void handleNotification(Notification notif, Object handback) {
            if (!id.equals(notif.getUserData())) {
                System.out.println("("+id+") Filter error, my id is: "+id+
                        ", but got "+notif.getUserData());
                System.exit(1);
            }

            synchronized (this) {
                received++;

                if(++sequenceNB != notif.getSequenceNumber()) {
                    fail("(" + id + ") Wrong sequence number, expected: "
                            +sequenceNB+", but got: "+notif.getSequenceNumber());
                }
                if (received >= toSend || failure != null) {
                    this.notify();
                }
            }
        }

        public void waitNotifs(long timeout, int nb) throws Exception {
            long toWait = timeout;
            long stopTime = System.currentTimeMillis() + timeout;
            synchronized(this) {
                while (received < nb && toWait > 0 && failure == null) {
                    this.wait(toWait);
                    toWait = stopTime - System.currentTimeMillis();
                }
            }
        }

        public void clear() {
            synchronized(this) {
                received = 0;
                sequenceNB = -1;
            }
        }

        private String id;
        private int received = 0;

        private long sequenceNB = -1;
    }

    private static class Filter implements NotificationFilter {
        public Filter(String id) {
            this.id = id;
        }

        public boolean isNotificationEnabled(Notification n) {
            return id.equals(n.getUserData());
        }
        private String id;
    }

    private static NotificationListener dummyListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
        }
    };

    public static class NotificationSender extends NotificationBroadcasterSupport
            implements NotificationSenderMBean {

        /**
         * Send Notification objects.
         *
         * @param nb The number of notifications to send
         */
        public void sendNotif(int nb, String userData) {
            long sequenceNumber = 0;
            for (int i = 0; i<nb; i++) {
                Notification notif = new Notification(myType, this, sequenceNumber++);
                notif.setUserData(userData);
                sendNotification(notif);
            }
        }


        private final String myType = "notification.my_notification";
    }

    public interface NotificationSenderMBean {
        public void sendNotif(int nb, String userData);
    }

    private static JMXConnector newConn() throws IOException {
        return JMXConnectorFactory.connect(url);
    }

    private static EventClient newEventClient(String type, JMXConnector conn)
            throws Exception {
        EventClientDelegateMBean proxy =
                EventClientDelegate.getProxy(conn.getMBeanServerConnection());
        if (type.equals("PushEventRelay")) {
            return new EventClient(proxy,
                    new RMIPushEventRelay(proxy), null, null, 60000);
        } else if (type.equals("FetchingEventRelay")) {
            return new EventClient(proxy,
                    new FetchingEventRelay(proxy), null, null, 60000);
        } else {
            throw new RuntimeException("Wrong event client type: "+type);
        }
    }

    private static String getId() {
        synchronized(AddRemoveListenerTest.class) {
            return String.valueOf(counter++);
        }
    }

    private static void fail(String msg) {
        System.out.println("FAIL: " + msg);
        failure = msg;
    }
}
