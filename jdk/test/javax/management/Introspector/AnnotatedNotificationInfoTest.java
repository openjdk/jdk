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
 * @bug 6323980 6772779
 * @summary Test &#64;NotificationInfo annotation
 * @author Eamonn McManus
 * @run main/othervm -ea AnnotatedNotificationInfoTest
 */

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import javax.annotation.Resource;
import javax.management.AttributeChangeNotification;
import javax.management.Description;
import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.ListenerNotFoundException;
import javax.management.MBean;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.MXBean;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationInfo;
import javax.management.NotificationInfos;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.SendNotification;

public class AnnotatedNotificationInfoTest {

    static final Descriptor expectedDescriptor = new ImmutableDescriptor(
            "foo=bar", "descriptionResourceBundleBaseName=bundle",
            "descriptionResourceKey=key");
    static final MBeanNotificationInfo expected = new MBeanNotificationInfo(
            new String[] {"foo", "bar"},
            AttributeChangeNotification.class.getName(),
            "description",
            expectedDescriptor);

    // Data for the first kind of test.  This tests that MBeanNotificationInfo
    // is correctly derived from @NotificationInfo.
    // Every static field called mbean* is expected to be an MBean
    // with a single MBeanNotificationInfo that has the same value
    // in each case.

    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static interface Intf1MBean {}

    public static class Intf1
            extends NotificationBroadcasterSupport implements Intf1MBean {}

    private static Object mbeanIntf1 = new Intf1();

    @NotificationInfos(
        @NotificationInfo(
                types = {"foo", "bar"},
                notificationClass = AttributeChangeNotification.class,
                description = @Description(
                    value = "description",
                    bundleBaseName = "bundle",
                    key = "key"),
                descriptorFields = {"foo=bar"}))
    public static interface Intf2MBean {}

    public static class Intf2
            extends NotificationBroadcasterSupport implements Intf2MBean {}

    private static Object mbeanIntf2 = new Intf2();

    @NotificationInfos({})
    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static interface Intf3MBean {}

    public static class Intf3
            extends NotificationBroadcasterSupport implements Intf3MBean {}

    private static Object mbeanIntf3 = new Intf3();

    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static interface ParentIntf {}

    public static interface Intf4MBean extends Serializable, ParentIntf, Cloneable {}

    public static class Intf4
            extends NotificationBroadcasterSupport implements Intf4MBean {}

    private static Object mbeanIntf4 = new Intf4();

    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static interface Intf5MXBean {}

    public static class Intf5Impl
            extends NotificationBroadcasterSupport implements Intf5MXBean {}

    private static Object mbeanIntf5 = new Intf5Impl();

    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static interface Intf6MBean {}

    public static class Intf6 implements Intf6MBean {
        @Resource
        private volatile SendNotification send;
    }

    private static Object mbeanIntf6 = new Intf6();

    public static interface Impl1MBean {}

    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static class Impl1
            extends NotificationBroadcasterSupport implements Impl1MBean {}

    private static Object mbeanImpl1 = new Impl1();

    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static class ParentImpl extends NotificationBroadcasterSupport {}

    public static interface Impl2MBean {}

    public static class Impl2 extends ParentImpl implements Impl2MBean {}

    private static Object mbeanImpl2 = new Impl2();

    public static interface Impl3MXBean {}

    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static class Impl3
            extends NotificationBroadcasterSupport implements Impl3MXBean {}

    private static Object mbeanImpl3 = new Impl3();

    public static class Impl4 extends ParentImpl implements Impl3MXBean {}

    private static Object mbeanImpl4 = new Impl4();

    @MBean
    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static class MBean1 extends NotificationBroadcasterSupport {}

    private static Object mbeanMBean1 = new MBean1();

    @MBean
    public static class MBean2 extends ParentImpl {}

    private static Object mbeanMBean2 = new MBean2();

    @MBean
    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static class MBean3 {
        @Resource
        private volatile SendNotification send;
    }

    private static Object mbeanMBean3 = new MBean3();

    @MXBean
    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static class MXBean1 extends NotificationBroadcasterSupport {}

    private static Object mbeanMXBean1 = new MXBean1();

    @MXBean
    public static class MXBean2 extends ParentImpl {}

    private static Object mbeanMXBean2 = new MXBean2();

    // Test that @NotificationInfo and @NotificationInfos are ignored if
    // the MBean returns a non-empty MBeanNotificationInfo[] from its
    // NotificationBroadcaster.getNotifications() implementation.

    @NotificationInfo(types={"blim", "blam"})
    public static interface Explicit1MBean {}

    public static class Explicit1
            extends NotificationBroadcasterSupport implements Explicit1MBean {
        public Explicit1() {
            super(expected);
        }
    }

    private static Object mbeanExplicit1 = new Explicit1();

    @NotificationInfos(
        {
            @NotificationInfo(types="blim"), @NotificationInfo(types="blam")
        }
    )
    public static interface Explicit2MXBean {}

    public static class Explicit2
            implements NotificationBroadcaster, Explicit2MXBean {
        public void addNotificationListener(NotificationListener listener,
                NotificationFilter filter, Object handback) {}

        public void removeNotificationListener(NotificationListener listener)
                throws ListenerNotFoundException {}

        public MBeanNotificationInfo[] getNotificationInfo() {
            return new MBeanNotificationInfo[] {expected};
        }
    }

    // Data for the second kind of test.  This tests that @NotificationInfo is
    // ignored if the MBean is not a notification source.  Every static
    // field called ignoredMBean* is expected to be an MBean on which
    // isInstanceOf(NotificationBroadcaster.class.getName() is false,
    // addNotificationListener produces an exception, and the
    // MBeanNotificationInfo array is empty.
    @NotificationInfo(types={"com.example.notifs.create",
                             "com.example.notifs.destroy"})
    public static interface CacheMBean {
        public int getCachedNum();
    }

    public static class Cache implements CacheMBean {
        public int getCachedNum() {
            return 0;
        }
    }

    private static Object ignoredMBean1 = new Cache();

    @NotificationInfos(
        @NotificationInfo(types={"foo", "bar"})
    )
    public static interface Cache2MBean {
        public int getCachedNum();
    }

    public static class Cache2 implements Cache2MBean {
        public int getCachedNum() {
            return 0;
        }
    }

    private static Object ignoredMBean2 = new Cache2();

    private static final NotificationListener nullListener =
            new NotificationListener() {
                public void handleNotification(
                        Notification notification, Object handback) {}
            };

    // Test that inheriting inconsistent @NotificationInfo annotations is
    // an error, but not if they are overridden by a non-empty getNotifications()

    @NotificationInfo(types={"blim"})
    public static interface Inconsistent1 {}

    @NotificationInfo(types={"blam"})
    public static interface Inconsistent2 {}

    public static interface InconsistentMBean extends Inconsistent1, Inconsistent2 {}

    public static class Inconsistent
            extends NotificationBroadcasterSupport implements InconsistentMBean {}

    public static class Consistent
            extends Inconsistent implements NotificationBroadcaster {
        public void addNotificationListener(NotificationListener listener,
                NotificationFilter filter, Object handback) {}

        public void removeNotificationListener(NotificationListener listener)
                throws ListenerNotFoundException {}

        public MBeanNotificationInfo[] getNotificationInfo() {
            return new MBeanNotificationInfo[] {expected};
        }
    }

    private static Object mbeanConsistent = new Consistent();

    @NotificationInfo(
            types = {"foo", "bar"},
            notificationClass = AttributeChangeNotification.class,
            description = @Description(
                value = "description",
                bundleBaseName = "bundle",
                key = "key"),
            descriptorFields = {"foo=bar"})
    public static interface Consistent2MBean extends Inconsistent1, Inconsistent2 {}

    public static class Consistent2
            extends NotificationBroadcasterSupport implements Consistent2MBean {}

    private static Object mbeanConsistent2 = new Consistent2();

    public static void main(String[] args) throws Exception {
        if (!AnnotatedNotificationInfoTest.class.desiredAssertionStatus())
            throw new Exception("Test must be run with -ea");

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName on = new ObjectName("a:b=c");

        System.out.println("Testing MBeans...");
        for (Field mbeanField :
                AnnotatedNotificationInfoTest.class.getDeclaredFields()) {
            boolean notifier;
            if (mbeanField.getName().startsWith("mbean"))
                notifier = true;
            else if (mbeanField.getName().startsWith("ignoredMBean"))
                notifier = false;
            else
                continue;
            System.out.println("..." + mbeanField.getName());
            Object mbean = mbeanField.get(null);
            mbs.registerMBean(mbean, on);
            MBeanInfo mbi = mbs.getMBeanInfo(on);
            MBeanNotificationInfo[] mbnis = mbi.getNotifications();
            if (notifier) {
                assert mbnis.length == 1 : mbnis.length;
                assert mbnis[0].equals(expected) : mbnis[0];
            } else {
                assert mbnis.length == 0 : mbnis.length;
                assert !mbs.isInstanceOf(on, NotificationBroadcaster.class.getName());
                try {
                    mbs.addNotificationListener(on, nullListener, null, null);
                    assert false : "addNotificationListener works";
                } catch (Exception e) {
                    // OK: addNL correctly refused
                }
            }
            mbs.unregisterMBean(on);
        }

        // Test that inconsistent @NotificationInfo annotations produce an
        // error.
        try {
            mbs.registerMBean(new Inconsistent(), on);
            System.out.println(mbs.getMBeanInfo(on));
            assert false : "Inconsistent @NotificationInfo not detected";
        } catch (Exception e) {
            System.out.println(
                    "Inconsistent @NotificationInfo correctly produced " + e);
        }
    }
}
