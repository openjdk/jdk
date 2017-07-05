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
 * @test VirtualMBeanTest.java
 * @bug 5108776 5072476
 * @summary Test that Virtual MBeans can be implemented and emit notifs.
 * @author Eamonn McManus
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;
import javax.management.SendNotification;
import javax.management.StandardEmitterMBean;
import javax.management.StandardMBean;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.VirtualEventManager;
import javax.management.namespace.MBeanServerSupport;
import javax.management.timer.TimerMBean;

// In this test, we check that the two main use case types for
// MBeanServerSupport work correctly:
// (1) as a special-purpose implementation of MBeanServer for a fixed number
//     of MBeans (e.g. for QueryNotificationFilter)
// (2) as an MBeanServer supporting Virtual MBeans.
// In each case we are particularly interested in the notification behaviour.
// We check that the behaviour is correct when calling addNotificationListener
// (a) for an MBean that does not exist; (b) for an MBean that does exist but
// is not a NotificationEmitter; and (c) for an MBean that exists and is
// a NotificationEmitter.  We also check the degenerate and usual case
// where the MBeanServerSupport subclass does not support notifications
// at all.
//
// Each subclass will have an MBean called test:type=NotEmitter that
// does not support addNotificationListener. If it also has MBeans called
// test:type=Emitter,* then they are expected to support addNL. No subclass
// will have any other MBeans, so in particular no subclass will have
// test:type=Nonexistent.
//
public class VirtualMBeanTest {
    static final ObjectName
            nonExistentName, notEmitterName, emitterName1, emitterName2;
    static {
        try {
            nonExistentName = new ObjectName("test:type=NonExistent");
            notEmitterName = new ObjectName("test:type=NotEmitter");
            emitterName1 = new ObjectName("test:type=Emitter,id=1");
            emitterName2 = new ObjectName("test:type=Emitter,id=2");
        } catch (MalformedObjectNameException e) {
            throw new AssertionError(e);
        }
    }

    static final StandardMBean.Options wrappedVisible = new StandardMBean.Options();
    static {
        wrappedVisible.setWrappedObjectVisible(true);
    }

    public static interface NothingMBean {}
    public static class Nothing implements NothingMBean {}
    public static class NothingNBS extends NotificationBroadcasterSupport
            implements NothingMBean {}

    // Class that has hardwired MBeans test:type=NotEmitter,
    // test:type=Broadcaster, and test:type=Emitter.
    private static class HardwiredMBS extends MBeanServerSupport
            implements SendNotification {
        private final DynamicMBean notEmitter =
                new StandardMBean(new Nothing(), NothingMBean.class, wrappedVisible);
        private final StandardEmitterMBean emitter1, emitter2;
        {
            NothingNBS nnbs1 = new NothingNBS();
            emitter1 = new StandardEmitterMBean(
                    nnbs1, NothingMBean.class, wrappedVisible, nnbs1);
            NothingNBS nnbs2 = new NothingNBS();
            emitter2 = new StandardEmitterMBean(
                    nnbs2, NothingMBean.class, wrappedVisible, nnbs2);
        }

        private final Map<ObjectName, DynamicMBean> map =
                new TreeMap<ObjectName, DynamicMBean>();
        {
            map.put(notEmitterName, notEmitter);
            map.put(emitterName1, emitter1);
            map.put(emitterName2, emitter2);
        }


        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
                throws InstanceNotFoundException {
            DynamicMBean mbean = map.get(name);
            if (mbean != null)
                return mbean;
            else
                throw new InstanceNotFoundException(name);
        }

        @Override
        protected Set<ObjectName> getNames() {
            return map.keySet();
        }

        @Override
        public String toString() {
            return "Hardwired MBeanServerSupport";
        }

        public void sendNotification(Notification notification) {
            emitter1.sendNotification(notification);
            emitter2.sendNotification(notification);
        }
    }

    // Class that has the notEmitter MBean but not either of the others, so does
    // not support listeners.
    private static class VirtualMBSWithoutListeners
            extends MBeanServerSupport {
        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
                throws InstanceNotFoundException {
            if (name.equals(notEmitterName)) {
                return new StandardMBean(
                        new Nothing(), NothingMBean.class, wrappedVisible);
            } else
                throw new InstanceNotFoundException(name);
        }

        @Override
        protected Set<ObjectName> getNames() {
            return Collections.singleton(notEmitterName);
        }

        @Override
        public String toString() {
            return "Virtual MBeanServerSupport without listener support";
        }
    }

    // Class that has the notEmitter and emitter MBeans as Virtual MBeans, using
    // VirtualEventManager to handle listeners for the emitter MBean.  We
    // implement the broadcaster MBean (which is a NotificationBroadcaster but
    // not a NotificationEmitter) even though it's very hard to imagine a real
    // use case where that would happen.
    private static class VirtualMBSWithListeners
            extends MBeanServerSupport implements SendNotification {
        private final VirtualEventManager vem = new VirtualEventManager();

        private static final List<ObjectName> names =
                Arrays.asList(notEmitterName, emitterName1, emitterName2);

        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
                throws InstanceNotFoundException {
            if (names.contains(name)) {
                return new StandardMBean(
                        new Nothing(), NothingMBean.class, wrappedVisible);
            } else
                throw new InstanceNotFoundException(name);
        }

        @Override
        public NotificationEmitter getNotificationEmitterFor(
                ObjectName name) throws InstanceNotFoundException {
            if (name.equals(emitterName1) || name.equals(emitterName2))
                return vem.getNotificationEmitterFor(name);
            else if (name.equals(notEmitterName))
                return null;
            else
                throw new InstanceNotFoundException(name);
        }

        @Override
        protected Set<ObjectName> getNames() {
            return new TreeSet<ObjectName>(Arrays.asList(notEmitterName, emitterName2));
        }

        @Override
        public String toString() {
            return "Virtual MBeanServerSupport with listener support";
        }

        public void sendNotification(Notification notification) {
            vem.publish(emitterName1, notification);
            vem.publish(emitterName2, notification);
        }
    }

    private static final MBeanServer[] vmbsss = {
        new HardwiredMBS(),
        new VirtualMBSWithoutListeners(),
        new VirtualMBSWithListeners(),
    };

    public static void main(String[] args) throws Exception {
        Exception lastEx = null;
        for (MBeanServer vmbs : vmbsss) {
            String testName = "\"" + vmbs + "\"";
            System.out.println("===Test " + testName + "===");
            try {
                test(vmbs);
            } catch (Exception e) {
                System.out.println(
                        "===Test " + testName + " failed with exception " + e);
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                pw.flush();
                String es = sw.toString();
                System.out.println("......" + es.replace("\n", "\n......"));
                lastEx = e;
            }
        }
        if (lastEx != null)
            throw lastEx;
        System.out.println("TEST PASSED");
    }

    private static class NothingListener implements NotificationListener {
        public void handleNotification(Notification notification,
                                       Object handback) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static class QueueListener implements NotificationListener {
        final BlockingQueue<Notification> queue =
                new ArrayBlockingQueue<Notification>(10);

        public void handleNotification(Notification notification,
                                       Object handback) {
            queue.add(notification);
        }
    }

    private static void test(MBeanServer vmbs) throws Exception {
        MBeanServer mmbs = MBeanServerFactory.newMBeanServer();
        ObjectName namespaceName = new ObjectName("test//:type=JMXNamespace");
        JMXNamespace namespace = new JMXNamespace(vmbs);
        mmbs.registerMBean(namespace, namespaceName);
        MBeanServer mbs = JMXNamespaces.narrowToNamespace(mmbs, "test");

        Set<ObjectName> names = mbs.queryNames(null, null);
        //names.remove(new ObjectName(":type=JMXNamespace"));

        // Make sure that notEmitterName exists according to query...
        System.out.println("Checking query");
        if (!names.contains(notEmitterName))
            throw new Exception("Bad query result: " + names);

        // ...and according to getMBeanInfo
        System.out.println("Checking getMBeanInfo(" + notEmitterName + ")");
        MBeanInfo mbi = mbs.getMBeanInfo(notEmitterName);
        if (mbi.getNotifications().length > 0)
            throw new Exception("notEmitter has NotificationInfo");

        // Make sure we get the right exception for getMBeanInfo on a
        // non-existent MBean
        System.out.println("Checking getMBeanInfo on a non-existent MBean");
        try {
            mbi = mbs.getMBeanInfo(nonExistentName);
            throw new Exception("getMBI succeeded but should not have");
        } catch (InstanceNotFoundException e) {
        }

        // Make sure we get the right exception for addNotificationListener on a
        // non-existent MBean
        System.out.println(
                "Checking addNotificationListener on a non-existent MBean");
        try {
            mbs.addNotificationListener(
                    nonExistentName, new NothingListener(), null, null);
            throw new Exception("addNL succeeded but should not have");
        } catch (InstanceNotFoundException e) {
        }

        // Make sure we get the right exception for isInstanceOf on a
        // non-existent MBean
        System.out.println(
                "Checking isInstanceOf on a non-existent MBean");
        for (Class<?> c : new Class<?>[] {
            Object.class, NotificationBroadcaster.class, NotificationEmitter.class,
        }) {
            try {
                boolean is = mbs.isInstanceOf(nonExistentName, c.getName());
                throw new Exception(
                        "isInstanceOf " + c.getName() +
                        " succeeded but should not have");
            } catch (InstanceNotFoundException e) {
            }
        }

        // Make sure isInstanceOf works correctly for classes without special
        // treatment
        System.out.println(
                "Checking isInstanceOf on normal classes");
        for (ObjectName name : names) {
            boolean isNothing = mbs.isInstanceOf(name, NothingMBean.class.getName());
            if (!isNothing) {
                throw new Exception("isInstanceOf " + NothingMBean.class.getName() +
                        " returned false, should be true");
            }
            boolean isTimer = mbs.isInstanceOf(name, TimerMBean.class.getName());
            if (isTimer) {
                throw new Exception("isInstanceOf " + TimerMBean.class.getName() +
                        " returned true, should be false");
            }
        }

        // Make sure that addNL on notEmitterName gets the right exception
        System.out.println("Checking addNL on non-broadcaster");
        try {
            mbs.addNotificationListener(
                    notEmitterName, new NothingListener(), null, null);
            throw new Exception("addNL succeeded but should not have");
        } catch (RuntimeOperationsException e) {
            if (!(e.getCause() instanceof IllegalArgumentException))
                throw new Exception("Wrong exception from addNL", e);
        }

        if (!(vmbs instanceof SendNotification)) {
            System.out.println("Not testing notifications for this implementation");
            return;
        }

        QueueListener qListener = new QueueListener();

        System.out.println("Testing addNL on emitters");
        mbs.addNotificationListener(emitterName1, qListener, null, null);
        mbs.addNotificationListener(emitterName2, qListener, null, null);

        System.out.println("Testing that listeners work");
        Notification notif = new Notification("notif.type", "source", 0L);

        ((SendNotification) vmbs).sendNotification(notif);
        testListeners(qListener, "notif.type", 2);

        System.out.println("Testing 2-arg removeNL on emitter1");
        mbs.removeNotificationListener(emitterName1, qListener);

        ((SendNotification) vmbs).sendNotification(notif);
        testListeners(qListener, "notif.type", 1);

        System.out.println("Testing 4-arg removeNL on emitter2");
        mbs.removeNotificationListener(emitterName2, qListener, null, null);

        ((SendNotification) vmbs).sendNotification(notif);
        testListeners(qListener, "notif.type", 0);
    }

    private static void testListeners(
            QueueListener qListener, String expectedNotifType, int expectedNotifs)
            throws Exception {
        for (int i = 1; i <= expectedNotifs; i++) {
            Notification rNotif = qListener.queue.poll(1, TimeUnit.SECONDS);
            if (rNotif == null)
                throw new Exception("Notification " + i + " never arrived");
            if (!rNotif.getType().equals(expectedNotifType))
                throw new Exception("Wrong type notif: " + rNotif.getType());
        }
        Notification xNotif = qListener.queue.poll(10, TimeUnit.MILLISECONDS);
        if (xNotif != null)
            throw new Exception("Extra notif: " + xNotif);
    }
}
