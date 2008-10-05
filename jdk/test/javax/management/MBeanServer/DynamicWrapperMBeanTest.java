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
 * @bug 6624232
 * @summary Test the DynamicWrapperMBean interface
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.modelmbean.RequiredModelMBean;
import static javax.management.StandardMBean.Options;

public class DynamicWrapperMBeanTest {
    public static interface WrappedMBean {
        public void sayHello();
    }
    public static class Wrapped implements WrappedMBean {
        public void sayHello() {
            System.out.println("Hello");
        }
    }

    private static String failure;

    public static void main(String[] args) throws Exception {
        if (Wrapped.class.getClassLoader() ==
                StandardMBean.class.getClassLoader()) {
            throw new Exception(
                    "TEST ERROR: Resource and StandardMBean have same ClassLoader");
        }

        Options wrappedVisOpts = new Options();
        wrappedVisOpts.setWrappedObjectVisible(true);
        Options wrappedInvisOpts = new Options();
        wrappedInvisOpts.setWrappedObjectVisible(false);
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

        if (failure != null)
            throw new Exception("TEST FAILED: " + failure);
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
