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
 * @test DynamicWrapperMBeanTest
 * @bug 6624232 6776225
 * @summary Test the DynamicWrapperMBean interface
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import javax.annotation.Resource;
import javax.management.JMX;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationInfo;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.SendNotification;
import javax.management.StandardEmitterMBean;
import javax.management.StandardMBean;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.modelmbean.RequiredModelMBean;
import static javax.management.StandardMBean.Options;

public class DynamicWrapperMBeanTest {
    private static String failure;

    public static void main(String[] args) throws Exception {
        wrapTest();
        notifTest();

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    private static final Options wrappedVisOpts = new Options();
    private static final Options wrappedInvisOpts = new Options();
    static {
        wrappedVisOpts.setWrappedObjectVisible(true);
        wrappedInvisOpts.setWrappedObjectVisible(false);
    }

    public static interface WrappedMBean {
        public void sayHello();
    }
    public static class Wrapped implements WrappedMBean {
        public void sayHello() {
            System.out.println("Hello");
        }
    }

    private static void wrapTest() throws Exception {
        if (Wrapped.class.getClassLoader() ==
                StandardMBean.class.getClassLoader()) {
            throw new Exception(
                    "TEST ERROR: Resource and StandardMBean have same ClassLoader");
        }

        assertEquals("Options withWrappedObjectVisible(false)",
                     new Options(), wrappedInvisOpts);

        Wrapped resource = new Wrapped();

        StandardMBean visible =
                new StandardMBean(resource, WrappedMBean.class, wrappedVisOpts);
        StandardMBean invisible =
                new StandardMBean(resource, WrappedMBean.class, wrappedInvisOpts);

        assertEquals("getResource withWrappedObjectVisible(true)",
                resource, visible.getWrappedObject());
        assertEquals("getResource withWrappedObjectVisible(false)",
                invisible, invisible.getWrappedObject());

        System.out.println("===Testing StandardMBean===");

        ObjectName visibleName = new ObjectName("a:type=visible");
        ObjectName invisibleName = new ObjectName("a:type=invisible");
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        mbs.registerMBean(visible, visibleName);
        mbs.registerMBean(invisible, invisibleName);

        assertEquals("ClassLoader for visible resource",
                Wrapped.class.getClassLoader(),
                mbs.getClassLoaderFor(visibleName));
        assertEquals("ClassLoader for invisible resource",
                StandardMBean.class.getClassLoader(),
                mbs.getClassLoaderFor(invisibleName));

        assertEquals("isInstanceOf(WrappedMBean) for visible wrapped",
                true, mbs.isInstanceOf(visibleName, WrappedMBean.class.getName()));
        assertEquals("isInstanceOf(WrappedMBean) for invisible wrapped",
                false, mbs.isInstanceOf(invisibleName, WrappedMBean.class.getName()));
        assertEquals("isInstanceOf(StandardMBean) for visible wrapped",
                false, mbs.isInstanceOf(visibleName, StandardMBean.class.getName()));
        assertEquals("isInstanceOf(StandardMBean) for invisible wrapped",
                true, mbs.isInstanceOf(invisibleName, StandardMBean.class.getName()));

        mbs.unregisterMBean(visibleName);
        mbs.unregisterMBean(invisibleName);

        System.out.println("===Testing RequiredModelMBean===");

        // Godawful Model MBeans...
        ModelMBeanOperationInfo mmboi = new ModelMBeanOperationInfo(
                "say hello to the nice man", Wrapped.class.getMethod("sayHello"));
        ModelMBeanInfo visibleMmbi = new ModelMBeanInfoSupport(
                Wrapped.class.getName(), "Visible wrapped", null, null,
                new ModelMBeanOperationInfo[] {mmboi}, null);
        ModelMBeanInfo invisibleMmbi = new ModelMBeanInfoSupport(
                Wrapped.class.getName(), "Invisible wrapped", null, null,
                new ModelMBeanOperationInfo[] {mmboi}, null);
        RequiredModelMBean visibleRmmb = new RequiredModelMBean(visibleMmbi);
        RequiredModelMBean invisibleRmmb = new RequiredModelMBean(invisibleMmbi);
        visibleRmmb.setManagedResource(resource, "VisibleObjectReference");
        invisibleRmmb.setManagedResource(resource, "ObjectReference");

        mbs.registerMBean(visibleRmmb, visibleName);
        mbs.registerMBean(invisibleRmmb, invisibleName);

        assertEquals("ClassLoader for visible wrapped",
                Wrapped.class.getClassLoader(),
                mbs.getClassLoaderFor(visibleName));
        assertEquals("ClassLoader for invisible wrapped",
                StandardMBean.class.getClassLoader(),
                mbs.getClassLoaderFor(invisibleName));

        assertEquals("isInstanceOf(WrappedMBean) for visible resource",
                true, mbs.isInstanceOf(visibleName, WrappedMBean.class.getName()));
        assertEquals("isInstanceOf(WrappedMBean) for invisible resource",
                false, mbs.isInstanceOf(invisibleName, WrappedMBean.class.getName()));
        assertEquals("isInstanceOf(RequiredModelMBean) for visible resource",
                false, mbs.isInstanceOf(visibleName, RequiredModelMBean.class.getName()));
        assertEquals("isInstanceOf(RequiredModelMBean) for invisible resource",
                true, mbs.isInstanceOf(invisibleName, RequiredModelMBean.class.getName()));

        mbs.unregisterMBean(visibleName);
        mbs.unregisterMBean(invisibleName);
    }

    private static enum WrapType {
        NBS("NotificationBroadcasterSupport"),
        INJ("@Resource SendNotification"),
        STD_MBEAN_NBS("StandardMBean delegating to NotificationBroadcasterSupport"),
        STD_MBEAN_INJ("StandardMBean delegating to @Resource SendNotification"),
        STD_MBEAN_SUB_NBS("StandardMBean subclass implementing NotificationBroadcaster"),
        STD_MBEAN_SUB_INJ("StandardMBean subclass with @Resource SendNotification"),
        STD_EMIT_MBEAN_NBS("StandardEmitterMBean delegating to NotificationBroadcasterSupport"),
        STD_EMIT_MBEAN_INJ("StandardEmitterMBean delegating to @Resource SendNotification"),
        STD_EMIT_MBEAN_SUB("StandardEmitterMBean subclass"),
        STD_EMIT_MBEAN_SUB_INJ("StandardEmitterMBean subclass with @Resource SendNotification");

        WrapType(String s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return s;
        }

        private final String s;
    }

    @NotificationInfo(
        types = {"foo", "bar"}
    )
    public static interface BroadcasterMBean {
        public void send(Notification n);
    }

    public static class Broadcaster
            extends NotificationBroadcasterSupport implements BroadcasterMBean {
        public void send(Notification n) {
            super.sendNotification(n);
        }
    }

    public static interface SendNotifMBean extends BroadcasterMBean {
    }

    public static class SendNotif implements SendNotifMBean {
        @Resource
        private volatile SendNotification sendNotif;

        public void send(Notification n) {
            sendNotif.sendNotification(n);
        }
    }

    public static class StdBroadcaster
            extends StandardMBean
            implements BroadcasterMBean, NotificationBroadcaster {
        private final NotificationBroadcasterSupport nbs =
                new NotificationBroadcasterSupport();

        public StdBroadcaster() throws Exception {
            super(BroadcasterMBean.class);
        }

        public void send(Notification n) {
            nbs.sendNotification(n);
        }

        public void addNotificationListener(NotificationListener listener,
                NotificationFilter filter, Object handback) {
            nbs.addNotificationListener(listener, filter, handback);
        }

        public MBeanNotificationInfo[] getNotificationInfo() {
            return null;
        }

        public void removeNotificationListener(NotificationListener listener)
                throws ListenerNotFoundException {
            nbs.removeNotificationListener(listener);
        }
    }

    public static class StdSendNotif
            extends StandardMBean implements SendNotifMBean {
        @Resource
        private volatile SendNotification sendNotif;

        public StdSendNotif() throws Exception {
            super(SendNotifMBean.class);
        }

        public void send(Notification n) {
            sendNotif.sendNotification(n);
        }
    }

    public static class StdEmitterBroadcaster // :-)
            extends StandardEmitterMBean
            implements BroadcasterMBean {

        public StdEmitterBroadcaster() throws Exception {
            super(BroadcasterMBean.class, null);
        }

        public void send(Notification n) {
            super.sendNotification(n);
        }
    }

    // This case is unlikely - if you're using @Resource SendNotification
    // then there's no point in using StandardEmitterMBean, since
    // StandardMBean would suffice.
    public static class StdEmitterSendNotif
            extends StandardEmitterMBean implements SendNotifMBean {
        @Resource
        private volatile SendNotification sendNotif;

        public StdEmitterSendNotif() {
            super(SendNotifMBean.class, null);
        }

        public void send(Notification n) {
            sendNotif.sendNotification(n);
        }
    }

    // Test that JMX.isNotificationSource and
    // mbs.isInstanceOf("NotificationBroadcaster") work correctly even when
    // the MBean is a broadcaster by virtue of its wrapped resource.
    // Test that we find the MBeanNotificationInfo[] from the @NotificationInfo
    // annotation on BroadcasterMBean.  We cover a large number of different
    // MBean types, but all ultimately implement that interface.
    private static void notifTest() throws Exception {
        System.out.println("===Testing notification senders===");

        for (WrapType wrapType : WrapType.values()) {
            System.out.println("---" + wrapType);

            final Object mbean;

            switch (wrapType) {
            case NBS:
                // An MBean that extends NotificationBroadcasterSupport
                mbean = new Broadcaster();
                break;
            case INJ:
                // An MBean that injects SendNotification
                mbean = new SendNotif();
                break;
            case STD_MBEAN_NBS:
                // A StandardMBean that delegates to a NotificationBroadcasterSupport
                mbean = new StandardMBean(
                        new Broadcaster(), BroadcasterMBean.class, wrappedVisOpts);
                break;
            case STD_MBEAN_INJ:
                // A StandardMBean that delegates to an object that injects
                // SendNotification
                mbean = new StandardMBean(
                        new SendNotif(), BroadcasterMBean.class, wrappedVisOpts);
                break;
            case STD_EMIT_MBEAN_NBS: {
                // A StandardEmitterMBean that delegates to a NotificationBroadcasterSupport
                Broadcaster broadcaster = new Broadcaster();
                mbean = new StandardEmitterMBean(
                        broadcaster, BroadcasterMBean.class, wrappedVisOpts,
                        broadcaster);
                break;
            }
            case STD_EMIT_MBEAN_INJ: {
                // A StandardEmitterMBean that delegates to an object that injects
                // SendNotification
                SendNotif sendNotif = new SendNotif();
                mbean = new StandardEmitterMBean(
                        sendNotif, BroadcasterMBean.class, wrappedVisOpts,
                        null);
                break;
            }
            case STD_MBEAN_SUB_NBS:
                // A subclass of StandardMBean that implements NotificationBroadcaster
                mbean = new StdBroadcaster();
                break;
            case STD_MBEAN_SUB_INJ:
                // A subclass of StandardMBean that injects SendNotification
                mbean = new StdSendNotif();
                break;
            case STD_EMIT_MBEAN_SUB:
                // A subclass of StandardEmitterMBean
                mbean = new StdEmitterBroadcaster();
                break;
            case STD_EMIT_MBEAN_SUB_INJ:
                // A subclass of StandardEmitterMBean that injects SendNotification
                // (which is a rather strange thing to do and probably a user
                // misunderstanding but we should do the right thing anyway).
                mbean = new StdEmitterSendNotif();
                break;
            default:
                throw new AssertionError();
            }

            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

            final ObjectName name = new ObjectName("a:type=Sender");
            mbs.registerMBean(mbean, name);
            boolean isBroadcaster = mbs.isInstanceOf(
                    name, NotificationBroadcaster.class.getName());
            assertEquals("JMX.isNotificationSource(mbean)",
                    true, JMX.isNotificationSource(mbean));
            assertEquals("isInstanceOf(NotificationBroadcaster)",
                    true, isBroadcaster);
            MBeanNotificationInfo[] mbnis =
                    mbs.getMBeanInfo(name).getNotifications();
            assertEquals("MBeanNotificationInfo not empty",
                    true, (mbnis.length > 0));

            mbs.unregisterMBean(name);
        }
    }

    private static void assertEquals(String what, Object expect, Object actual) {
        if (equal(expect, actual))
            System.out.println("OK: " + what + " = " + expect);
        else
            fail(what + " should be " + expect + ", is " + actual);
    }

    private static boolean equal(Object x, Object y) {
        if (x == y)
            return true;
        if (x == null || y == null)
            return false;
        return x.equals(y);
    }

    private static void fail(String why) {
        failure = why;
        System.out.println("FAIL: " + why);
    }
}
