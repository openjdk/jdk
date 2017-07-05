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
 * @test CustomForwarderTest
 * @bug 5108776 6759619
 * @summary Test that a custom EventForwarder can be added
 * @author Eamonn McManus
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.event.EventClientDelegate;
import javax.management.event.EventClientDelegateMBean;
import javax.management.event.EventForwarder;
import javax.management.event.EventReceiver;
import javax.management.event.EventRelay;
import javax.management.remote.MBeanServerForwarder;
import javax.management.remote.NotificationResult;
import javax.management.remote.TargetedNotification;

public class CustomForwarderTest {
    public static class UdpEventRelay implements EventRelay {
        private final EventClientDelegateMBean delegate;
        private final DatagramSocket socket;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final String clientId;
        private EventReceiver receiver;

        public UdpEventRelay(EventClientDelegateMBean delegate)
        throws IOException {
            this.delegate = delegate;
            this.socket = new DatagramSocket();
            try {
                clientId = delegate.addClient(
                        UdpEventForwarder.class.getName(),
                        new Object[] {socket.getLocalSocketAddress()},
                        new String[] {SocketAddress.class.getName()});
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                final IOException ioe =
                        new IOException("Exception creating EventForwarder");
                ioe.initCause(e);
                throw ioe;
            }
            Thread t = new Thread(new Receiver());
            t.setDaemon(true);
            t.start();
        }

        public String getClientId() throws IOException {
            return clientId;
        }

        public void setEventReceiver(EventReceiver eventReceiver) {
            this.receiver = eventReceiver;
        }

        public void stop() throws IOException {
            closed.set(true);
            socket.close();
        }

        void simulateNonFatal() {
            receiver.nonFatal(new Exception("NonFatal"));
        }

        void simulateFailed() {
            receiver.failed(new Error("Failed"));
        }

        private class Receiver implements Runnable {
            public void run() {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                while (true) {
                    try {
                        socket.receive(packet);
                    } catch (IOException e) {
                        if (closed.get()) {
                            System.out.println("Receiver got exception: " + e);
                            System.out.println("Normal because it has been closed");
                            return;
                        } else {
                            System.err.println("UNEXPECTED EXCEPTION IN RECEIVER:");
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }
                    try {
                        ByteArrayInputStream bin = new ByteArrayInputStream(buf);
                        ObjectInputStream oin = new ObjectInputStream(bin);
                        NotificationResult nr = (NotificationResult)
                                oin.readObject();
                        receiver.receive(nr);
                    } catch (Exception e) {
                        System.err.println("UNEXPECTED EXCEPTION IN RECEIVER:");
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            }
        }
    }

    public static class UdpEventForwarder implements EventForwarder {
        private final DatagramSocket socket;
        private final AtomicLong seqNo = new AtomicLong(0);
        private static volatile boolean drop;

        public UdpEventForwarder(SocketAddress addr) throws IOException {
            this.socket = new DatagramSocket();
            socket.connect(addr);
        }

        public static void setDrop(boolean drop) {
            UdpEventForwarder.drop = drop;
        }

        public void forward(Notification n, Integer listenerId) throws IOException {
            long nextSeqNo = seqNo.incrementAndGet();
            long thisSeqNo = nextSeqNo - 1;
            TargetedNotification tn = new TargetedNotification(n, listenerId);
            NotificationResult nr = new NotificationResult(
                    thisSeqNo, nextSeqNo, new TargetedNotification[] {tn});
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(bout);
            oout.writeObject(nr);
            oout.close();
            byte[] bytes = bout.toByteArray();
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            if (!drop)
                socket.send(packet);
        }

        public void close() throws IOException {
            socket.close();
        }

        public void setClientId(String clientId) throws IOException {
            // Nothing to do.
        }
    }

    public static interface EmptyMBean {}

    public static class Empty
            extends NotificationBroadcasterSupport implements EmptyMBean {
        public void send(Notification n) {
            super.sendNotification(n);
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        MBeanServerForwarder mbsf = EventClientDelegate.newForwarder();
        mbsf.setMBeanServer(mbs);
        mbs = mbsf;

        // for 1.5
        if (System.getProperty("java.version").startsWith("1.5") &&
                !mbs.isRegistered(EventClientDelegateMBean.OBJECT_NAME)) {
            System.out.print("Working on "+System.getProperty("java.version")+
                    " register "+EventClientDelegateMBean.OBJECT_NAME);

            mbs.registerMBean(EventClientDelegate.
                    getEventClientDelegate(mbs),
                    EventClientDelegateMBean.OBJECT_NAME);
        }

        ObjectName name = new ObjectName("a:b=c");
        Empty mbean = new Empty();
        mbs.registerMBean(mbean, name);

        EventClientDelegateMBean delegate = (EventClientDelegateMBean)
            MBeanServerInvocationHandler.newProxyInstance(
                mbs,
                EventClientDelegateMBean.OBJECT_NAME,
                EventClientDelegateMBean.class,
                false);
        UdpEventRelay relay = new UdpEventRelay(delegate);
        EventClient client = new EventClient(delegate, relay, null, null, 0L);

        final Semaphore lostCountSema = new Semaphore(0);
        final BlockingQueue<Notification> nonFatalNotifs =
                new ArrayBlockingQueue<Notification>(1);
        final BlockingQueue<Notification> failedNotifs =
                new ArrayBlockingQueue<Notification>(1);
        NotificationListener lostListener = new NotificationListener() {
            public void handleNotification(Notification notification, Object handback) {
                if (notification.getType().equals(EventClient.NOTIFS_LOST)) {
                    System.out.println("Got lost-notifs notif: count=" +
                            notification.getUserData());
                    lostCountSema.release(((Long) notification.getUserData()).intValue());
                } else if (notification.getType().equals(EventClient.NONFATAL)) {
                    System.out.println("Got nonFatal notif");
                    nonFatalNotifs.add(notification);
                } else if (notification.getType().equals(EventClient.FAILED)) {
                    System.out.println("Got failed notif");
                    failedNotifs.add(notification);
                } else
                    System.out.println("Mysterious EventClient notif: " + notification);
            }
        };
        client.addEventClientListener(lostListener, null, null);

        final BlockingQueue<Notification> notifQueue =
                new ArrayBlockingQueue<Notification>(10);
        NotificationListener countListener = new NotificationListener() {
            public void handleNotification(Notification notification, Object handback) {
                System.out.println("Received: " + notification);
                notifQueue.add(notification);
                if (!"tiddly".equals(handback)) {
                    System.err.println("TEST FAILED: bad handback: " + handback);
                    System.exit(1);
                }
            }
        };

        final AtomicInteger filterCount = new AtomicInteger(0);
        NotificationFilter countFilter = new NotificationFilter() {
            private static final long serialVersionUID = 1234L;

            public boolean isNotificationEnabled(Notification notification) {
                System.out.println("Filter called for: " + notification);
                filterCount.incrementAndGet();
                return true;
            }
        };

        client.addNotificationListener(name, countListener, countFilter, "tiddly");

        assertEquals("Initial notif count", 0, notifQueue.size());
        assertEquals("Initial filter count", 0, filterCount.get());

        Notification n = nextNotif(name);
        mbean.send(n);

        System.out.println("Waiting for notification to arrive...");

        Notification n1 = notifQueue.poll(10, TimeUnit.SECONDS);

        assertEquals("Received notif", n, n1);
        assertEquals("Notif queue size after receive", 0, notifQueue.size());
        assertEquals("Filter count after notif", 1, filterCount.get());
        assertEquals("Lost notif count", 0, lostCountSema.availablePermits());

        System.out.println("Dropping notifs");

        UdpEventForwarder.setDrop(true);
        for (int i = 0; i < 3; i++)
            mbean.send(nextNotif(name));
        UdpEventForwarder.setDrop(false);

        Thread.sleep(2);
        assertEquals("Notif queue size after drops", 0, notifQueue.size());

        System.out.println("Turning off dropping and sending a notif");
        n = nextNotif(name);
        mbean.send(n);

        System.out.println("Waiting for dropped notifications to be detected...");
        boolean acquired = lostCountSema.tryAcquire(3, 5, TimeUnit.SECONDS);
        assertEquals("Correct count of lost notifs", true, acquired);

        n1 = notifQueue.poll(10, TimeUnit.SECONDS);
        assertEquals("Received non-dropped notif", n, n1);

        assertEquals("Notif queue size", 0, notifQueue.size());
        assertEquals("Filter count after drops", 5, filterCount.get());

        Thread.sleep(10);
        assertEquals("Further lost-notifs", 0, lostCountSema.availablePermits());

        System.out.println("Testing error notifs");
        relay.simulateNonFatal();
        n = nonFatalNotifs.poll(10, TimeUnit.SECONDS);
        assertEquals("Exception message for non-fatal exception", "NonFatal",
                ((Throwable) n.getSource()).getMessage());
        relay.simulateFailed();
        n = failedNotifs.poll(10, TimeUnit.SECONDS);
        assertEquals("Exception message for failed exception", "Failed",
                ((Throwable) n.getSource()).getMessage());

        // 6759619
        System.out.println("Test EventClient.getEventClientNotificationInfo");
        MBeanNotificationInfo[] mbnis = client.getEventClientNotificationInfo();
        final String[] expectedTypes = {
            EventClient.NOTIFS_LOST, EventClient.NONFATAL, EventClient.FAILED
        };
    check:
        for (String type : expectedTypes) {
            for (MBeanNotificationInfo mbni : mbnis) {
                for (String t : mbni.getNotifTypes()) {
                    if (type.equals(t)) {
                        System.out.println("...found " + type);
                        continue check;
                    }
                }
            }
            throw new Exception("TEST FAILED: Did not find notif type " + type);
        }

        client.close();

        System.out.println("TEST PASSED");
    }

    private static AtomicLong nextSeqNo = new AtomicLong(0);
    private static Notification nextNotif(ObjectName name) {
        long n = nextSeqNo.incrementAndGet();
        return new Notification("type", name, n, "" + n);
    }

    private static void assertEquals(String what, Object expected, Object got) {
        if (equals(expected, got))
            System.out.println(what + " = " + expected + ", as expected");
        else {
            Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
            for (Thread t : traces.keySet()) {
                System.out.println(t.getName());
                for (StackTraceElement elmt : traces.get(t)) {
                    System.out.println("    " + elmt);
                }
            }
            throw new RuntimeException(
                    "TEST FAILED: " + what + " is " + got + "; should be " +
                    expected);
        }
    }

    private static boolean equals(Object expected, Object got) {
        if (!(expected instanceof Notification))
            return expected.equals(got);
        if (expected.getClass() != got.getClass())
            return false;
        // Notification doesn't override Object.equals so two distinct
        // notifs are never equal even if they have the same contents.
        // Although the test doesn't serialize the notifs, if at some
        // stage it did then it would fail because the deserialized notif
        // was not equal to the original one.  Therefore we compare enough
        // notif fields to detect when notifs really are different.
        Notification en = (Notification) expected;
        Notification gn = (Notification) got;
        return (en.getType().equals(gn.getType()) &&
                en.getSource().equals(gn.getSource()) &&
                en.getSequenceNumber() == gn.getSequenceNumber());
    }
}
