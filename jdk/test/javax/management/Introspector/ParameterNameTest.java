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
 * @test %M% %I%
 * @bug 6323980
 * @summary Test that parameter names can be specified with &#64;Name.
 * @author Eamonn McManus
 */

import javax.management.MBean;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MXBean;
import javax.management.ObjectName;

import annot.Name;
import javax.management.ManagedOperation;

public class ParameterNameTest {
    public static interface NoddyMBean {
        public int add(int x, @Name("y") int y);
    }

    public static class Noddy implements NoddyMBean {
        public int add(int x, int y) {
            return x + y;
        }
    }

    public static interface NoddyMXBean {
        public int add(int x, @Name("y") int y);
    }

    public static class NoddyImpl implements NoddyMXBean {
        public int add(int x, int y) {
            return x + y;
        }
    }

    @MBean
    public static class NoddyAnnot {
        @ManagedOperation
        public int add(int x, @Name("y") int y) {
            return x + y;
        }
    }

    @MXBean
    public static class NoddyAnnotMX {
        @ManagedOperation
        public int add(int x, @Name("y") int y) {
            return x + y;
        }
    }

    private static final Object[] mbeans = {
        new Noddy(), new NoddyImpl(), new NoddyAnnot(), new NoddyAnnotMX(),
    };

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("a:b=c");
        for (Object mbean : mbeans) {
            System.out.println("Testing " + mbean.getClass().getName());
            mbs.registerMBean(mbean, name);
            MBeanInfo mbi = mbs.getMBeanInfo(name);
            MBeanOperationInfo[] mbois = mbi.getOperations();
            assertEquals(1, mbois.length);
            MBeanParameterInfo[] mbpis = mbois[0].getSignature();
            assertEquals(2, mbpis.length);
            boolean mx = Boolean.parseBoolean(
                    (String) mbi.getDescriptor().getFieldValue("mxbean"));
            assertEquals(mx ? "p0" : "p1", mbpis[0].getName());
            assertEquals("y", mbpis[1].getName());
            mbs.unregisterMBean(name);
        }
        System.out.println("TEST PASSED");
    }

    private static void assertEquals(Object expect, Object actual)
    throws Exception {
        boolean eq;
        if (expect == null)
            eq = (actual == null);
        else
            eq = expect.equals(actual);
        if (!eq) {
            throw new Exception(
                    "TEST FAILED: expected " + expect + ", found " + actual);
        }
    }
}
