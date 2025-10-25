/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8364227
 * @summary Test various null parameters and verify Exceptions thrown
 * @modules java.management.rmi
 * @run main ExceptionTestNulls
 */

import java.lang.management.ManagementFactory;
import javax.management.AttributeNotFoundException;
import javax.management.ObjectName;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationListener;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;

public class ExceptionTestNulls {

    public interface MyMBean {
    }

    public class My implements MyMBean {
    }

    private int count;

    public static void main(String args[]) throws Exception {
        ExceptionTestNulls test = new ExceptionTestNulls();
        test.run();
    }

    public ExceptionTestNulls() {
        count = 0; // Simple index for printing tests, for readability.
    }

    public void run() {

        try {
        ObjectName name = new ObjectName("a:b=c");
        ObjectName namePattern = new ObjectName("*:type=Foo");
        My myMy = new My();
            MBeanServer mbs = MBeanServerFactory.newMBeanServer();

            try {
                // createMBean with null className
                mbs.createMBean((String) null, name, name, new Object[0], new String[0]);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

            try {
                // createMBean with ObjectName as a pattern
                mbs.createMBean("myMy", namePattern, name, new Object[0], new String[0]);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

            try {
                // registerMBean with null Object
                mbs.registerMBean(null, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

            try {
                // registerMBean with no name available
                mbs.registerMBean(myMy, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

            try {
                // unregisterMBean with null ObjectName
                mbs.unregisterMBean(null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

            try {
                mbs.isRegistered(null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

            try {
                mbs.getAttribute(null, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

            try {
                mbs.getAttribute(name, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

           try {
                mbs.getAttributes(null, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

           try {
                mbs.getAttributes(name, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

           try {
                mbs.setAttribute(null, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

           try {
                mbs.setAttribute(name, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

           try {
                mbs.setAttributes(null, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

           try {
                mbs.setAttributes(name, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

           try {
                mbs.addNotificationListener(null, (NotificationListener) null, null, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

           try {
                mbs.registerMBean(myMy, name);
                mbs.addNotificationListener(null, name, null, null);
            } catch (RuntimeOperationsException e) {
                checkROEContainsIAE(e);
            }

        } catch (MBeanException | MalformedObjectNameException | InstanceAlreadyExistsException
                 | NotCompliantMBeanException | InstanceNotFoundException | ReflectionException
                 | AttributeNotFoundException | InvalidAttributeValueException e) {
            // Should not reach here.  Known Exceptions thrown by methods above.
            // These would be a failure, as would other exceptions not caught (e.g. NullPointerException).
            throw new RuntimeException(e);
        }
    }

    public void checkROEContainsIAE(RuntimeOperationsException e) {
        System.out.println(++count);
        System.out.println("Checking: " + e);
        if (e.getCause() instanceof IllegalArgumentException) {
            System.out.println("Got expected cause: " + e.getCause());
            System.out.println();
        } else {
            throw new RuntimeException("Not the expected cause: " + e);
        }
    }
}
