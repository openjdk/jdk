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
 * @test ReconnectableJMXConnector
 * @bug 5108776
 * @summary Check that the Event Service can be used to build a
 * ReconnectableJMXConnector.
 * @author Eamonn McManus
 */

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

/*
 * This test checks that it is possible to use the Event Service to create
 * a "reconnectable connector".
 *
 * In the JMX Remote API, we deliberately specified that a connector client
 * (JMXConnector) that encounters a network failure is then permanently broken.
 * The idea being that adding recovery logic to the basic connector client
 * would make it much more complicated and less reliable, and the logic would
 * in any case never correspond to what a given situation needs. Some of
 * the tough questions are: Should the connector try to mask the failure by
 * blocking operations until the failure is resolved? How long should the
 * connector try to reestablish the connection before giving up? Rather than
 * try to solve this problem in the connector, we suggested that people who
 * wanted to recover from network failures could implement the JMXConnector
 * interface themselves so that it forwards to a wrapped JMXConnector that can
 * be replaced in case of network failure.
 *
 * This works fine except that the connector client has state,
 * in the form of listeners added by the user through the
 * MBeanServerConnection.addNotificationListener method. It's possible
 * for the wrapper to keep track of these listeners as well as forwarding
 * them to the wrapped JMXConnector, so that it can reapply them to
 * a replacement JMXConnector after failure recover. But it's quite
 * tricky, particularly because of the two- and four-argument versions of
 * removeNotificationListener.
 *
 * The Event Service can take care of this for you through the EventClient
 * class. Listeners added through that class are implemented in a way that
 * doesn't require the connector client to maintain any state, so they should
 * continue to work transparently after replacing the wrapped JMXConnector.
 * This test is a proof of concept that shows it works.  Quite a number of
 * details would need to be changed to build a reliable reconnectable
 * connector.
 *
 * The test simulates network failure by rewrapping the wrapped JMXConnector's
 * MBeanServerConnection (MBSC) in a "breakable" MBSC which we can cause
 * to stop working.  We do this in two phases.  The first phase suspends
 * any MBSC calls just at the point where they would return to the caller.
 * The goal here is to block an EventClientDelegateMBean.fetchNotifications
 * operation when it has received notifications but not yet delivered them
 * to the EventClient.  This is the most delicate point where a breakage
 * can occur, because the EventClientDelegate must not drop those notifs
 * from its buffer until another fetchNotifs call arrives with a later
 * sequence number (which is an implicit ack of the previous set of
 * notifs).  Once the fetchNotifs call is suspended, we "kill" the MBSC,
 * causing it to throw IOException from this and any other calls.  That
 * triggers the reconnect logic, which will make a new MBSC and issue
 * the same fetchNotifs call to it.
 *
 * The test could be improved by synchronizing explicitly between the
 * breakable MBSC and the mainline, so we only proceed to kill the MBSC
 * when we are sure that the fetchNotifs call is blocked.  As it is,
 * we have a small delay which both ensures that no notifs are delivered
 * while the connection is suspended, and if the machine is fast enough
 * allows the fetchNotifs call to reach the blocking point.
 */
public class ReconnectableConnectorTest {
    private static class ReconnectableJMXConnector implements JMXConnector {
        private final JMXServiceURL url;
        private AtomicReference<JMXConnector> wrappedJMXC =
                new AtomicReference<JMXConnector>();
        private AtomicReference<MBeanServerConnection> wrappedMBSC =
                new AtomicReference<MBeanServerConnection>();
        private final NotificationBroadcasterSupport broadcaster =
                new NotificationBroadcasterSupport();
        private final Lock connectLock = new ReentrantLock();

        ReconnectableJMXConnector(JMXServiceURL url) {
            this.url = url;
        }

        private class ReconnectIH implements InvocationHandler {
            public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                try {
                    return method.invoke(wrappedMBSC.get(), args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        connect();
                        try {
                            return method.invoke(wrappedMBSC.get(),args);
                        } catch (InvocationTargetException ee) {
                            throw ee.getCause();
                        }
                    }
                    throw e.getCause();
                }
            }
        }

        private class FailureListener implements NotificationListener {
            public void handleNotification(Notification n, Object h) {
                String type = n.getType();
                if (type.equals(JMXConnectionNotification.FAILED)) {
                    try {
                        connect();
                    } catch (IOException e) {
                        broadcaster.sendNotification(n);
                    }
                } else if (type.equals(JMXConnectionNotification.NOTIFS_LOST))
                    broadcaster.sendNotification(n);
            }
        }

        public void connect() throws IOException {
            connectLock.lock();
            try {
                connectWithLock();
            } finally {
                connectLock.unlock();
            }
        }

        private void connectWithLock() throws IOException {
            MBeanServerConnection mbsc = wrappedMBSC.get();
            if (mbsc != null) {
                try {
                    mbsc.getDefaultDomain();
                    return;  // the connection works
                } catch (IOException e) {
                    // OK: the connection doesn't work, so make a new one
                }
            }
            // This is where we would need to add the fancy logic that
            // allows the connection to keep failing for a while
            // before giving up.
            JMXConnector jmxc = JMXConnectorFactory.connect(url);
            jmxc.addConnectionNotificationListener(
                    new FailureListener(), null, null);
            wrappedJMXC.set(jmxc);
            if (false)
                wrappedMBSC.set(jmxc.getMBeanServerConnection());
            else {
                mbsc = jmxc.getMBeanServerConnection();
                InvocationHandler ih = new BreakableIH(mbsc);
                mbsc = (MBeanServerConnection) Proxy.newProxyInstance(
                        MBeanServerConnection.class.getClassLoader(),
                        new Class<?>[] {MBeanServerConnection.class},
                        ih);
                wrappedMBSC.set(mbsc);
            }
        }

        private BreakableIH breakableIH() {
            MBeanServerConnection mbsc = wrappedMBSC.get();
            return (BreakableIH) Proxy.getInvocationHandler(mbsc);
        }

        void suspend() {
            BreakableIH ih = breakableIH();
            ih.suspend();
        }

        void kill() throws IOException {
            BreakableIH ih = breakableIH();
            wrappedJMXC.get().close();
            ih.kill();
        }

        public void connect(Map<String, ?> env) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        private final AtomicReference<MBeanServerConnection> mbscRef =
                new AtomicReference<MBeanServerConnection>();

        public MBeanServerConnection getMBeanServerConnection()
                throws IOException {
            connect();
            // Synchro here is not strictly correct: two threads could make
            // an MBSC at the same time.  OK for a test but beware for real
            // code.
            MBeanServerConnection mbsc = mbscRef.get();
            if (mbsc != null)
                return mbsc;
            mbsc = (MBeanServerConnection) Proxy.newProxyInstance(
                    MBeanServerConnection.class.getClassLoader(),
                    new Class<?>[] {MBeanServerConnection.class},
                    new ReconnectIH());
            mbsc = EventClient.getEventClientConnection(mbsc);
            mbscRef.set(mbsc);
            return mbsc;
        }

        public MBeanServerConnection getMBeanServerConnection(
                Subject delegationSubject) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void close() throws IOException {
            wrappedJMXC.get().close();
        }

        public void addConnectionNotificationListener(
                NotificationListener l, NotificationFilter f, Object h) {
            broadcaster.addNotificationListener(l, f, h);
        }

        public void removeConnectionNotificationListener(NotificationListener l)
                throws ListenerNotFoundException {
            broadcaster.removeNotificationListener(l);
        }

        public void removeConnectionNotificationListener(
                NotificationListener l, NotificationFilter f, Object h)
                throws ListenerNotFoundException {
            broadcaster.removeNotificationListener(l, f, h);
        }

        public String getConnectionId() throws IOException {
            return wrappedJMXC.get().getConnectionId();
        }
    }

    // InvocationHandler that allows us to perform a two-phase "break" of
    // an object.  The first phase suspends the object, so that calls to
    // it are blocked just before they return.  The second phase unblocks
    // suspended threads and causes them to throw IOException.
    private static class BreakableIH implements InvocationHandler {
        private final Object wrapped;
        private final Holder<String> state = new Holder<String>("running");

        BreakableIH(Object wrapped) {
            this.wrapped = wrapped;
        }

        void suspend() {
            state.set("suspended");
        }

        void kill() {
            state.set("killed");
        }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            Object result;
            try {
                result = method.invoke(wrapped, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            String s = state.get();
            if (s.equals("suspended"))
                state.waitUntilEqual("killed", 3, TimeUnit.SECONDS);
            else if (s.equals("killed"))
                throw new IOException("Broken");
            return result;
        }
    }

    private static class Holder<T> {
        private T held;
        private Lock lock = new ReentrantLock();
        private Condition changed = lock.newCondition();

        Holder(T value) {
            lock.lock();
            this.held = value;
            lock.unlock();
        }

        void waitUntilEqual(T value, long timeout, TimeUnit units)
                throws InterruptedException {
            long millis = units.toMillis(timeout);
            long stop = System.currentTimeMillis() + millis;
            Date stopDate = new Date(stop);
            lock.lock();
            try {
                while (!value.equals(held)) {
                    boolean ok = changed.awaitUntil(stopDate);
                    if (!ok)
                        throw new InterruptedException("Timed out");
                }
            } finally {
                lock.unlock();
            }
        }

        void set(T value) {
            lock.lock();
            try {
                held = value;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        T get() {
            lock.lock();
            try {
                return held;
            } finally {
                lock.unlock();
            }
        }
    }

    private static class StoreListener implements NotificationListener {
        final BlockingQueue<Notification> queue =
                new ArrayBlockingQueue<Notification>(100);

        public void handleNotification(Notification n, Object h) {
            queue.add(n);
        }

        Notification nextNotification(long time, TimeUnit units)
                throws InterruptedException {
            Notification n = queue.poll(time, units);
            if (n == null)
                throw new NoSuchElementException("Notification wait timed out");
            return n;
        }

        int notifCount() {
            return queue.size();
        }
    }

    public static interface SenderMBean {}
    public static class Sender
            extends NotificationBroadcasterSupport implements SenderMBean {
        private AtomicLong seqNo = new AtomicLong(0);

        void send() {
            Notification n =
                    new Notification("type", this, seqNo.getAndIncrement());
            sendNotification(n);
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        Sender sender = new Sender();
        ObjectName name = new ObjectName("a:b=c");
        mbs.registerMBean(sender, name);

        System.out.println("Creating connector server");
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///");
        JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(
                url, null, mbs);
        cs.start();

        StoreListener csListener = new StoreListener();
        cs.addNotificationListener(csListener, null, null);

        System.out.println("Creating reconnectable client");
        JMXServiceURL addr = cs.getAddress();
        ReconnectableJMXConnector cc = new ReconnectableJMXConnector(addr);
        MBeanServerConnection mbsc = cc.getMBeanServerConnection();

        System.out.println("Checking server has sent new-client notif");
        Notification csn = csListener.nextNotification(1, TimeUnit.SECONDS);
        assertEquals("CS notif type",
                JMXConnectionNotification.OPENED, csn.getType());

        StoreListener listener = new StoreListener();
        mbsc.addNotificationListener(name, listener, null, null);

        System.out.println("Sending 10 notifs and checking they are received");
        for (int i = 0; i < 10; i++)
            sender.send();
        checkNotifs(listener, 0, 10);

        System.out.println("Suspending the fetchNotifs operation");
        cc.suspend();
        System.out.println("Sending a notif while fetchNotifs is suspended");
        sender.send();
        System.out.println("Brief wait before checking no notif is received");
        Thread.sleep(2);
        // dumpThreads();
        assertEquals("notif queue while connector suspended",
                0, listener.notifCount());
        assertEquals("connector server notif queue while connector suspended",
                0, csListener.notifCount());

        System.out.println("Breaking the connection so fetchNotifs will fail over");
        cc.kill();

        System.out.println("Checking that client has reconnected");
        csn = csListener.nextNotification(1, TimeUnit.SECONDS);
        assertEquals("First CS notif type after kill",
                JMXConnectionNotification.CLOSED, csn.getType());
        csn = csListener.nextNotification(1, TimeUnit.SECONDS);
        assertEquals("Second CS notif type after kill",
                JMXConnectionNotification.OPENED, csn.getType());

        System.out.println("Checking that suspended notif has been received");
        checkNotifs(listener, 10, 11);
    }

    private static void checkNotifs(
             StoreListener sl, long start, long stop)
            throws Exception {
        for (long i = start; i < stop; i++) {
            Notification n = sl.nextNotification(1, TimeUnit.SECONDS);
            assertEquals("received sequence number", i, n.getSequenceNumber());
        }
    }

    private static void assertEquals(String what, Object expect, Object actual)
    throws Exception {
        if (!expect.equals(actual)) {
            fail(what + " should be " + expect + " but is " + actual);
        }
    }

    private static void fail(String why) throws Exception {
        throw new Exception("TEST FAILED: " + why);
    }

    private static void dumpThreads() {
        System.out.println("Thread stack dump");
        Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
            Thread t = entry.getKey();
            System.out.println("===Thread " + t.getName() + "===");
            for (StackTraceElement ste : entry.getValue())
                System.out.println("    " + ste);
        }
    }
}
