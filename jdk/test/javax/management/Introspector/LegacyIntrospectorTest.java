/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6316460
 * @summary Test that the legacy com.sun.management.jmx.Introspector
 * methods work.
 * @author Eamonn McManus
 * @run clean LegacyIntrospectorTest
 * @run build LegacyIntrospectorTest
 * @run main LegacyIntrospectorTest
 */

import javax.management.*;
import com.sun.management.jmx.*;

public class LegacyIntrospectorTest {
    public static interface TestMBean {
        public int getWhatever();
    }
    public static class Test implements TestMBean {
        public int getWhatever() {return 0;}
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        MBeanInfo mbi = Introspector.testCompliance(Test.class);
        MBeanAttributeInfo mbai = mbi.getAttributes()[0];
        if (!mbai.getName().equals("Whatever"))
            throw new Exception("Wrong attribute name: " + mbai.getName());
        Class c = Introspector.getMBeanInterface(Test.class);
        if (c != TestMBean.class)
            throw new Exception("Wrong interface: " + c);

        MBeanServer mbs1 = new MBeanServerImpl();
        if (!mbs1.getDefaultDomain().equals("DefaultDomain"))
            throw new Exception("Wrong default domain: " + mbs1.getDefaultDomain());

        MBeanServer mbs2 = new MBeanServerImpl("Foo");
        if (!mbs2.getDefaultDomain().equals("Foo"))
            throw new Exception("Wrong default domain: " + mbs2.getDefaultDomain());

        ObjectName delegateName =
            new ObjectName("JMImplementation:type=MBeanServerDelegate");
        MBeanInfo delegateInfo = mbs2.getMBeanInfo(delegateName);
        MBeanInfo refDelegateInfo =
            MBeanServerFactory.newMBeanServer().getMBeanInfo(delegateName);
        if (!delegateInfo.equals(refDelegateInfo))
            throw new Exception("Wrong delegate info from MBeanServerImpl: " +
                                delegateInfo);

        System.out.println("TEST PASSED");
    }
}
