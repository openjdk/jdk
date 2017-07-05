/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4943248
 * @summary Tests that NullPointerException is thrown when listener is null.
 * @author Daniel Fuchs
 * @run clean ConnectionListenerNullTest
 * @run build ConnectionListenerNullTest
 * @run main ConnectionListenerNullTest
 */
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnectorFactory;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import java.util.Map;
public class ConnectionListenerNullTest {

    static final boolean optionalFlag;
    static {
        Class genericClass = null;
        try {
            genericClass =
            Class.forName("javax.management.remote.generic.GenericConnector");
        } catch (ClassNotFoundException x) {
            // NO optional package
        }
        optionalFlag = (genericClass != null);
    }

    final static String[] mandatoryList = {
        "service:jmx:rmi://", "service:jmx:iiop://"
    };

    final static String[] optionalList = {
        "service:jmx:jmxmp://"
    };

    public static int test(String[] urls) {
        int errCount = 0;
        for (int i=0;i<urls.length;i++) {
            try {
                final JMXServiceURL url = new JMXServiceURL(urls[i]);
                final JMXConnector c =
                    JMXConnectorFactory.newJMXConnector(url,(Map)null);
                final NotificationListener nl = null;
                final NotificationFilter   nf = null;
                final Object               h  = null;
                System.out.println("Testing " + c.getClass().getName());
                try {
                    System.out.println(
                        "addConnectionNotificationListener(null,null,null)");
                    c.addConnectionNotificationListener(nl,nf,h);
                    throw new AssertionError("No exception raised");
                } catch (NullPointerException npe) {
                    // OK.
                }
                final NotificationListener listener = new
                   NotificationListener() {
                     public void handleNotification(Notification notification,
                                                   Object handback) {
                   }
                };
                c.addConnectionNotificationListener(listener,nf,h);
                try {
                    System.out.println(
                           "removeConnectionNotificationListener(null)");
                    c.removeConnectionNotificationListener(nl);
                    throw new AssertionError("No exception raised");
                } catch (NullPointerException npe) {
                    // OK.
                }
                try {
                    System.out.println(
                      "removeConnectionNotificationListener(null,null,null)");
                    c.removeConnectionNotificationListener(nl,nf,h);
                    throw new AssertionError("No exception raised");
                } catch (NullPointerException npe) {
                    // OK.
                }
                c.removeConnectionNotificationListener(listener);
                System.out.println(c.getClass().getName() +
                                   " successfully tested.");
            } catch (Exception x) {
                System.err.println("Unexpected exception for " +
                                   urls[i] + ": " + x);
                x.printStackTrace();
                errCount++;
            } catch (AssertionError e) {
                System.err.println("Unexpected assertion error for " +
                                   urls[i] + ": " + e);
                e.printStackTrace();
                errCount++;
            }
        }
        return errCount;
    }

    public static void main(String args[]) {
        int errCount = 0;
        errCount += test(mandatoryList);
        if (optionalFlag) errCount += test(optionalList);
        if (errCount > 0) {
            System.err.println("ConnectionListenerNullTest failed: " +
                               errCount + " error(s) reported.");
            System.exit(1);
        }
        System.out.println("ConnectionListenerNullTest passed.");
    }
}
