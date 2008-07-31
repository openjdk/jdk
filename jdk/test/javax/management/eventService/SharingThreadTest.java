/*/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test SharingThreadTest.java 1.3 08/01/22
 * @bug 5108776
 * @summary Basic test for EventClient to see internal thread management.
 * @author Shanliang JIANG
 * @run clean SharingThreadTest
 * @run build SharingThreadTest
 * @run main SharingThreadTest
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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


public class SharingThreadTest {

    private static MBeanServer mbeanServer = MBeanServerFactory.createMBeanServer();
    private static List<Notification> notifList = new ArrayList<Notification>();
    private static ObjectName emitter;
    private static NotificationEmitter emitterImpl;
    private static JMXServiceURL url;
    private static JMXConnectorServer server;
    private static JMXConnector conn;


    private static int toSend = 10;
    private static long sequenceNumber = 0;
    private static final long bigWaiting = 6000;
    private static int counter = 0;
    private static int jobs = 10;
    private static int endedJobs = 0;

    private static Executor sharedExecutor = new ThreadPoolExecutor(0, 1, 1000,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue(jobs));
            //Executors.newFixedThreadPool(1);

    public static void main(String[] args) throws Exception {
        System.out.println(">>> Test on sharing threads for multiple EventClient.");

        // for 1.5
        if (System.getProperty("java.version").startsWith("1.5") &&
                !mbeanServer.isRegistered(EventClientDelegateMBean.OBJECT_NAME)) {
            System.out.print("Working on "+System.getProperty("java.version")+
                    " register "+EventClientDelegateMBean.OBJECT_NAME);

            mbeanServer.registerMBean(EventClientDelegate.
                    getEventClientDelegate(mbeanServer),
                    EventClientDelegateMBean.OBJECT_NAME);

            sharedExecutor = new ThreadPoolExecutor(1, 1, 1000,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue(jobs));
        }

        emitter = new ObjectName("Default:name=NotificationEmitter");
        emitterImpl = new NotificationEmitter();
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
                System.out.println(">>> Skip "+prot+", not support.");
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

            Thread[] threads = new Thread[jobs];
            try {
                for (String type: types) {
                    System.out.println("\n\n>>> Testing "+type+" on "+url+" ...");
                    newConn();
                    for (int i=0; i<jobs; i++) {
                        threads[i] = new Thread(new Job(type));
                        threads[i].setDaemon(true);
                        threads[i].start();
                    }

                    // to wait
                    long toWait = bigWaiting*jobs;
                    long stopTime = System.currentTimeMillis() + toWait;

                    synchronized(SharingThreadTest.class) {
                        while (endedJobs < jobs && toWait > 0) {
                            SharingThreadTest.class.wait(toWait);
                            toWait = stopTime - System.currentTimeMillis();
                        }
                    }

                    if (endedJobs != jobs) {
                        throw new RuntimeException("Need to set bigger waiting timeout?");
                    }

                    endedJobs = 0;
                    conn.close();
                    System.out.println(">>> Testing "+type+" on "+url+" ... done");
                }
            } finally {
                server.stop();
            }
        }
    }

    public static class Job implements Runnable {
        public Job(String type) {
            this.type = type;
        }
        public void run() {
            try {
                test(type);

                synchronized(SharingThreadTest.class) {
                    endedJobs++;
                    if (endedJobs>=jobs) {
                        SharingThreadTest.class.notify();
                    }
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private final String type;
    }

    private static void test(String type) throws Exception {
        String id = getId();

        Listener listener = new Listener(id);
        Filter filter = new Filter(id);

        //newConn();
        EventClient ec = newEventClient(type);

        System.out.println(">>> ("+id+") To receive notifications "+toSend);
        ec.addNotificationListener(emitter,
                listener, filter, null);

        emitterImpl.sendNotif(toSend, id);
        listener.waitNotifs(bigWaiting, toSend);
        if (listener.received != toSend) {
            throw new RuntimeException(">>> ("+id+") Expected to receive: "
                    +toSend+", but got: "+listener.received);
        }

        // not close the EventClient to keep using thread
        //ec.close();
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
            System.out.println("("+id+") received "+notif.getSequenceNumber());
            synchronized (notifList) {
                received++;

                if (sequenceNB < 0) {
                    sequenceNB = notif.getSequenceNumber();
                } else if(++sequenceNB != notif.getSequenceNumber()) {
                    throw new RuntimeException("Wrong sequence number, expecte: "
                            +sequenceNB+", but got: "+notif.getSequenceNumber());
                }
                if (received >= toSend) {
                    this.notify();
                }
            }
        }

        public void waitNotifs(long timeout, int nb) throws Exception {
            long toWait = timeout;
            long stopTime = System.currentTimeMillis() + timeout;
            synchronized(this) {
                while (received < nb && toWait > 0) {
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

    public static class NotificationEmitter extends NotificationBroadcasterSupport
            implements NotificationEmitterMBean {

        /**
         * Send Notification objects.
         *
         * @param nb The number of notifications to send
         */
        public void sendNotif(int nb, String userData) {
            new Thread(new SendJob(nb, userData)).start();
        }

        private class SendJob implements Runnable {
            public SendJob(int nb, String userData) {
                this.nb = nb;
                this.userData = userData;
            }

            public void run() {
                if (userData != null) {
                    System.out.println(">>> ("+userData+") sending "+nb);
                }
                for (int i = 0; i<nb; i++) {
                    Notification notif = new Notification(myType, emitter,
                            sequenceNumber++);
                    notif.setUserData(userData);
                    sendNotification(notif);
                    Thread.yield();
                    try {
                        Thread.sleep(1);
                    } catch (Exception e) {}
                }
                if (userData != null) {
                    System.out.println(">>> ("+userData+") sending done");
                }
            }
            private int nb;
            private String userData;
        }
        private final String myType = "notification.my_notification";
    }

    public interface NotificationEmitterMBean {
        public void sendNotif(int nb, String userData);
    }

    private static void newConn() throws IOException {
        conn = JMXConnectorFactory.connect(url);
    }

    private static EventClient newEventClient(String type) throws Exception {
        EventClientDelegateMBean proxy =
                EventClientDelegate.getProxy(conn.getMBeanServerConnection());
        if (type.equals("PushEventRelay")) {
            return new EventClient(proxy,
                    new RMIPushEventRelay(proxy), sharedExecutor, null, 600);
        } else if (type.equals("FetchingEventRelay")) {
            return new EventClient(proxy,
                    new FetchingEventRelay(proxy,
                    FetchingEventRelay.DEFAULT_BUFFER_SIZE,
                    10,
                    FetchingEventRelay.DEFAULT_MAX_NOTIFICATIONS,
                    sharedExecutor),
                    null, null, 600);
        } else {
            throw new RuntimeException("Wrong event client type: "+type);
        }
    }

    private static String getId() {
        synchronized(SharingThreadTest.class) {
            return String.valueOf(counter++);
        }
    }
}
