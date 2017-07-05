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
 * @bug 5108776
 * @summary Test that the various Executor parameters in an EventClient do
 * what they are supposed to.
 * @author Eamonn McManus
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.event.EventClientDelegate;
import javax.management.event.EventClientDelegateMBean;
import javax.management.event.FetchingEventRelay;
import javax.management.remote.MBeanServerForwarder;

public class EventClientExecutorTest {
    private static volatile String failure;
    private static final Set testedPrefixes = new HashSet();

    public static void main(String[] args) throws Exception {
        Executor fetchExecutor = Executors.newSingleThreadExecutor(
                new NamedThreadFactory("FETCH"));
        Executor listenerExecutor = Executors.newSingleThreadExecutor(
                new NamedThreadFactory("LISTENER"));
        ScheduledExecutorService leaseScheduler =
            Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("LEASE"));

        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        MBeanServerForwarder mbsf = EventClientDelegate.newForwarder(mbs, null);
        mbs = mbsf;

        EventClientDelegateMBean ecd = EventClientDelegate.getProxy(mbs);
        ecd = (EventClientDelegateMBean) Proxy.newProxyInstance(
                EventClientDelegateMBean.class.getClassLoader(),
                new Class<?>[] {EventClientDelegateMBean.class},
                new DelegateCheckIH(ecd));

        ObjectName mbeanName = new ObjectName("d:type=Notifier");
        Notifier notifier = new Notifier();
        mbs.registerMBean(notifier, mbeanName);

        FetchingEventRelay eventRelay = new FetchingEventRelay(
                ecd, fetchExecutor);
        EventClient ec = new EventClient(
                ecd, eventRelay, listenerExecutor, leaseScheduler, 1000L);
        NotificationListener checkListener = new NotificationListener() {
            public void handleNotification(Notification notification,
                                           Object handback) {
                assertThreadName("listener dispatch", "LISTENER");
            }
        };
        ec.addNotificationListener(mbeanName, checkListener, null, null);

        mbs.invoke(mbeanName, "send", null, null);

        // Now wait until we have seen all three thread types.
        long deadline = System.currentTimeMillis() + 5000;
        synchronized (testedPrefixes) {
            while (testedPrefixes.size() < 3 && failure == null) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    fail("Timed out waiting for all three thread types to show, " +
                            "saw only " + testedPrefixes);
                    break;
                }
                try {
                    testedPrefixes.wait(remain);
                } catch (InterruptedException e) {
                    fail("Unexpected InterruptedException");
                    break;
                }
            }
        }

        // We deliberately don't close the EventClient to check that it has
        // not created any non-daemon threads.

        if (failure != null)
            throw new Exception("TEST FAILED: " + failure);
        else
            System.out.println("TEST PASSED");
    }

    public static interface NotifierMBean {
        public void send();
    }

    public static class Notifier extends NotificationBroadcasterSupport
            implements NotifierMBean {
        public void send() {
            Notification n = new Notification("a.b.c", this, 0L);
            sendNotification(n);
        }
    }

    static void fail(String why) {
        System.out.println("FAIL: " + why);
        failure = why;
    }

    static void assertThreadName(String what, String prefix) {
        String name = Thread.currentThread().getName();
        if (!name.startsWith(prefix)) {
            fail("Wrong thread for " + what + ": " + name);
            return;
        }

        synchronized (testedPrefixes) {
            if (testedPrefixes.add(prefix))
                testedPrefixes.notify();
        }
    }

    private static class DelegateCheckIH implements InvocationHandler {
        private final EventClientDelegateMBean ecd;

        public DelegateCheckIH(EventClientDelegateMBean ecd) {
            this.ecd = ecd;
        }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String methodName = method.getName();
            if (methodName.equals("fetchNotifications"))
                assertThreadName("fetchNotifications", "FETCH");
            else if (methodName.equals("lease"))
                assertThreadName("lease renewal", "LEASE");
            try {
                return method.invoke(ecd, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private int count;

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(namePrefix + " " + ++count);
            t.setDaemon(true);
            return t;
        }
    }
}
