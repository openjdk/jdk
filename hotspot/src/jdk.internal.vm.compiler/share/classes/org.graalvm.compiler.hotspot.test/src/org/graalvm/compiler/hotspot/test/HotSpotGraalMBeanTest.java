/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.hotspot.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

import java.util.Arrays;
import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.hotspot.HotSpotGraalMBean;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.test.GraalTest;
import org.graalvm.util.EconomicMap;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotGraalMBeanTest {

    public HotSpotGraalMBeanTest() {
        // No support for registering Graal MBean yet on JDK9 (GR-4025). We cannot
        // rely on an exception being thrown when accessing ManagementFactory.platformMBeanServer
        // via reflection as recent JDK9 changes now allow this and issue a warning instead.
        Assume.assumeTrue(GraalTest.Java8OrEarlier);
    }

    @Test
    public void registration() throws Exception {
        ObjectName name;

        Field field = null;
        try {
            field = stopMBeanServer();
        } catch (Exception ex) {
            if (ex.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
                // skip on JDK9
                return;
            }
        }
        assertNull("The platformMBeanServer isn't initialized now", field.get(null));

        HotSpotGraalMBean bean = HotSpotGraalMBean.create(null);
        assertNotNull("Bean created", bean);

        assertNull("It is not registered yet", bean.ensureRegistered(true));

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        assertNotNull("Now the bean thinks it is registered", name = bean.ensureRegistered(true));

        assertNotNull("And the bean is found", server.getObjectInstance(name));
    }

    private static Field stopMBeanServer() throws NoSuchFieldException, SecurityException, IllegalAccessException, IllegalArgumentException {
        final Field field = ManagementFactory.class.getDeclaredField("platformMBeanServer");
        field.setAccessible(true);
        field.set(null, null);
        return field;
    }

    @Test
    public void readBeanInfo() throws Exception {
        ObjectName name;

        assertNotNull("Server is started", ManagementFactory.getPlatformMBeanServer());

        HotSpotGraalMBean realBean = HotSpotGraalMBean.create(null);
        assertNotNull("Bean is registered", name = realBean.ensureRegistered(false));
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        ObjectInstance bean = server.getObjectInstance(name);
        assertNotNull("Bean is registered", bean);
        MBeanInfo info = server.getMBeanInfo(name);
        assertNotNull("Info is found", info);

        MBeanAttributeInfo printCompilation = (MBeanAttributeInfo) findAttributeInfo("PrintCompilation", info);
        assertNotNull("PrintCompilation found", printCompilation);
        assertEquals("true/false", Boolean.class.getName(), printCompilation.getType());

        Attribute printOn = new Attribute(printCompilation.getName(), Boolean.TRUE);

        Object before = server.getAttribute(name, printCompilation.getName());
        server.setAttribute(name, printOn);
        Object after = server.getAttribute(name, printCompilation.getName());

        assertNull("Default value was not set", before);
        assertEquals("Changed to on", Boolean.TRUE, after);
    }

    private static Object findAttributeInfo(String attrName, Object info) {
        MBeanAttributeInfo printCompilation = null;
        for (MBeanAttributeInfo attr : ((MBeanInfo) info).getAttributes()) {
            if (attr.getName().equals(attrName)) {
                assertTrue("Readable", attr.isReadable());
                assertTrue("Writable", attr.isWritable());
                printCompilation = attr;
                break;
            }
        }
        return printCompilation;
    }

    @Test
    public void optionsAreCached() throws Exception {
        ObjectName name;

        assertNotNull("Server is started", ManagementFactory.getPlatformMBeanServer());

        HotSpotGraalMBean realBean = HotSpotGraalMBean.create(null);

        OptionValues original = new OptionValues(EconomicMap.create());

        assertSame(original, realBean.optionsFor(original, null));

        assertNotNull("Bean is registered", name = realBean.ensureRegistered(false));
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        ObjectInstance bean = server.getObjectInstance(name);
        assertNotNull("Bean is registered", bean);
        MBeanInfo info = server.getMBeanInfo(name);
        assertNotNull("Info is found", info);

        MBeanAttributeInfo dump = (MBeanAttributeInfo) findAttributeInfo("Dump", info);

        Attribute dumpTo1 = new Attribute(dump.getName(), 1);

        server.setAttribute(name, dumpTo1);
        Object after = server.getAttribute(name, dump.getName());
        assertEquals(1, after);

        final OptionValues modified1 = realBean.optionsFor(original, null);
        assertNotSame(original, modified1);
        final OptionValues modified2 = realBean.optionsFor(original, null);
        assertSame("Options are cached", modified1, modified2);

    }

    @Test
    public void dumpOperation() throws Exception {
        Field field = null;
        try {
            field = stopMBeanServer();
        } catch (Exception ex) {
            if (ex.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
                // skip on JDK9
                return;
            }
        }
        assertNull("The platformMBeanServer isn't initialized now", field.get(null));

        ObjectName name;

        assertNotNull("Server is started", ManagementFactory.getPlatformMBeanServer());

        HotSpotGraalMBean realBean = HotSpotGraalMBean.create(null);

        assertNotNull("Bean is registered", name = realBean.ensureRegistered(false));
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        ObjectInstance bean = server.getObjectInstance(name);
        assertNotNull("Bean is registered", bean);

        MBeanInfo info = server.getMBeanInfo(name);
        assertNotNull("Info is found", info);

        final MBeanOperationInfo[] arr = info.getOperations();
        assertEquals("Currently three overloads", 3, arr.length);
        MBeanOperationInfo dumpOp = null;
        for (int i = 0; i < arr.length; i++) {
            assertEquals("dumpMethod", arr[i].getName());
            if (arr[i].getSignature().length == 3) {
                dumpOp = arr[i];
            }
        }
        assertNotNull("three args variant found", dumpOp);

        server.invoke(name, "dumpMethod", new Object[]{
                        "java.util.Arrays", "asList", ":3"
        }, null);

        MBeanAttributeInfo dump = (MBeanAttributeInfo) findAttributeInfo("Dump", info);
        Attribute dumpTo1 = new Attribute(dump.getName(), "");
        server.setAttribute(name, dumpTo1);
        Object after = server.getAttribute(name, dump.getName());
        assertEquals("", after);

        OptionValues empty = new OptionValues(EconomicMap.create());
        OptionValues unsetDump = realBean.optionsFor(empty, null);
        final MetaAccessProvider metaAccess = jdk.vm.ci.runtime.JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(Arrays.class.getMethod("asList", Object[].class));
        final OptionValues forMethod = realBean.optionsFor(unsetDump, method);
        assertNotSame(unsetDump, forMethod);
        Object nothing = unsetDump.getMap().get(DebugOptions.Dump);
        assertEquals("Empty string", "", nothing);

        Object specialValue = forMethod.getMap().get(DebugOptions.Dump);
        assertEquals(":3", specialValue);

        OptionValues normalMethod = realBean.optionsFor(unsetDump, null);
        Object noSpecialValue = normalMethod.getMap().get(DebugOptions.Dump);
        assertEquals("Empty string", "", noSpecialValue);
    }

}
