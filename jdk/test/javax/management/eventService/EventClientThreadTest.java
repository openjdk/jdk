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
 * @bug 6747411
 * @summary Check that EventClient instances don't leak threads.
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.TreeSet;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class EventClientThreadTest {
    private static final int MAX_TIME_SECONDS = 20;

    private static final BlockingQueue<Notification> queue =
            new ArrayBlockingQueue(100);

    private static final NotificationListener queueListener =
            new NotificationListener() {
        public void handleNotification(Notification notification,
                                       Object handback) {
            queue.add(notification);
        }
    };

    private static final NotificationFilter dummyFilter =
            new NotificationFilter() {
        public boolean isNotificationEnabled(Notification notification) {
            return true;
        }
    };

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        long deadline = start + MAX_TIME_SECONDS * 1000;

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://");
        JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(
                url, null, mbs);
        cs.start();
        JMXServiceURL addr = cs.getAddress();
        JMXConnector cc = JMXConnectorFactory.connect(addr);
        MBeanServerConnection mbsc = cc.getMBeanServerConnection();

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        System.out.println("Opening and closing some EventClients...");
        // If we create a connection, then create and destroy EventClients
        // over it, then close it, there should be no "JMX *" threads left.
        for (int i = 0; i < 5; i++)
            test(mbsc);

        cc.close();

        showTime("opening and closing initial EventClients", start);

        Set<String> jmxThreads = threadsMatching("JMX .*");
        while (!jmxThreads.isEmpty() && System.currentTimeMillis() < deadline) {
            Set<String> jmxThreadsNow = threadsMatching("JMX .*");
            Set<String> gone = new TreeSet<String>(jmxThreads);
            gone.removeAll(jmxThreadsNow);
            for (String s : gone)
                showTime("expiry of \"" + s + "\"", start);
            jmxThreads = jmxThreadsNow;
            Thread.sleep(10);
        }
        if (System.currentTimeMillis() >= deadline) {
            showThreads(threads);
            throw new Exception("Timed out waiting for JMX threads to expire");
        }

        showTime("waiting for JMX threads to expire", start);

        System.out.println("TEST PASSED");
    }

    static void showThreads(ThreadMXBean threads) throws Exception {
        long[] ids = threads.getAllThreadIds();
        for (long id : ids) {
            ThreadInfo ti = threads.getThreadInfo(id);
            String name = (ti == null) ? "(defunct)" : ti.getThreadName();
            System.out.printf("%4d %s\n", id, name);
        }
    }

    static void showTime(String what, long start) {
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Time after %s: %.3f s\n", what, elapsed / 1000.0);
    }

    static Set<String> threadsMatching(String pattern) {
        Set<String> matching = new TreeSet<String>();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] ids = threads.getAllThreadIds();
        for (long id : ids) {
            ThreadInfo ti = threads.getThreadInfo(id);
            String name = (ti == null) ? "(defunct)" : ti.getThreadName();
            if (name.matches(pattern))
                matching.add(name);
        }
        return matching;
    }

    static void test(MBeanServerConnection mbsc) throws Exception {
        final ObjectName delegateName = MBeanServerDelegate.DELEGATE_NAME;
        final ObjectName testName = new ObjectName("test:type=Test");
        EventClient ec = new EventClient(mbsc);
        ec.addNotificationListener(delegateName, queueListener, null, null);
        mbsc.createMBean(MBeanServerDelegate.class.getName(), testName);
        mbsc.unregisterMBean(testName);
        final String[] expectedTypes = {
            MBeanServerNotification.REGISTRATION_NOTIFICATION,
            MBeanServerNotification.UNREGISTRATION_NOTIFICATION,
        };
        for (String s : expectedTypes) {
            Notification n = queue.poll(3, TimeUnit.SECONDS);
            if (n == null)
                throw new Exception("Timed out waiting for notif: " + s);
            if (!(n instanceof MBeanServerNotification))
                throw new Exception("Got notif of wrong class: " + n.getClass());
            if (!n.getType().equals(s)) {
                throw new Exception("Got notif of wrong type: " + n.getType() +
                        " (expecting " + s + ")");
            }
        }
        ec.removeNotificationListener(delegateName, queueListener);

        ec.addNotificationListener(delegateName, queueListener, dummyFilter, "foo");
        ec.removeNotificationListener(delegateName, queueListener, dummyFilter, "foo");

        ec.close();
    }
}