/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4909536
 * @summary Ensure that the Introspector does not retain refs to classes
 * @requires vm.opt.final.ClassUnloading
 * @author Eamonn McManus
 *
 * @run clean ClassLeakTest
 * @run build ClassLeakTest
 * @run main/othervm ClassLeakTest
 */

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.io.File;
import java.nio.file.Paths;
import java.net.*;
import java.util.*;

import javax.management.*;
import javax.management.loading.*;

public class ClassLeakTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing that registering and unregistering a " +
                           "Standard MBean does not retain a reference to " +
                           "the MBean's class");

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName testName = new ObjectName(":name=Test");
        Test mbean = new Test();
        mbs.registerMBean(mbean, testName);

        MBeanInfo info = mbs.getMBeanInfo(testName);
        MBeanAttributeInfo[] attrs = info.getAttributes();
        if (attrs.length != 1 || !attrs[0].getName().equals("A")
            || !attrs[0].isReadable() || !attrs[0].isWritable()
            || attrs[0].isIs() || !attrs[0].getType().equals("int")) {
            System.out.println("TEST FAILED: unexpected MBeanInfo attrs");
            System.exit(1);
        }
        MBeanOperationInfo[] ops = info.getOperations();
        if (ops.length != 1 || !ops[0].getName().equals("bogus")
            || ops[0].getSignature().length > 0
            || ops[0].getImpact() != MBeanOperationInfo.UNKNOWN
            || !ops[0].getReturnType().equals("void")) {
            System.out.println("TEST FAILED: unexpected MBeanInfo ops");
            System.exit(1);
        }
        if (info.getConstructors().length != 2) {
            System.out.println("TEST FAILED: wrong number of constructors " +
                               "in introspected bean: " +
                               Arrays.asList(info.getConstructors()));
            System.exit(1);
        }
        if (!info.getClassName().endsWith("Test")) {
            System.out.println("TEST FAILED: wrong info class name: " +
                               info.getClassName());
            System.exit(1);
        }

        WeakReference mbeanRef = new WeakReference(mbean);
        mbs.unregisterMBean(testName);
        mbean = null;

        System.out.println("MBean registered and unregistered, waiting for " +
                           "garbage collector to collect class loader");
        for (int i = 0; i < 10000 && mbeanRef.get() != null; i++) {
            System.gc();
            Thread.sleep(1);
        }

        if (mbeanRef.get() == null)
            System.out.println("Test passed: class loader was GC'd");
        else {
            System.out.println("TEST FAILED: class loader was not GC'd");
            System.exit(1);
        }
    }

    public static interface TestMBean {
        public void bogus();
        public int getA();
        public void setA(int a);
    }

    public static class Test implements TestMBean {
        public Test() {}
        public Test(int x) {}

        public void bogus() {}
        public int getA() {return 0;}
        public void setA(int a) {}
    }
}
