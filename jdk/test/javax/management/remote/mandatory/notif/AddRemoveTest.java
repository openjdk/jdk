/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4838640 4917194
 * @summary test on add/remove NotificationListener
 * @author Shanliang JIANG
 * @run clean AddRemoveTest
 * @run build AddRemoveTest
 * @run main AddRemoveTest
 */

import java.net.MalformedURLException;

import java.util.Collections;
import java.util.Map;
import javax.management.*;
import javax.management.remote.*;
import javax.management.remote.rmi.RMIConnectorServer;

public class AddRemoveTest {
    private static final String[] protocols = {"rmi", "iiop", "jmxmp"};
    private static final MBeanServer mbs = MBeanServerFactory.createMBeanServer();

    public static void main(String[] args) {
        System.out.println(">>> test on add/remove NotificationListener.");

        boolean ok = true;
        for (int i = 0; i < protocols.length; i++) {
            try {
                if (!test(protocols[i])) {
                    System.out.println(">>> Test failed for " + protocols[i]);
                    ok = false;
                } else {
                    System.out.println(">>> Test successed for " + protocols[i]);
                }
            } catch (Exception e) {
                System.out.println(">>> Test failed for " + protocols[i]);
                e.printStackTrace(System.out);
                ok = false;
            }
        }

        if (ok) {
            System.out.println(">>> Test passed");
        } else {
            System.out.println(">>> TEST FAILED");
            System.exit(1);
        }
    }

    private static boolean test(String proto) throws Exception {
        boolean ok = test(proto, false);
        ok &= test(proto, true);
        return ok;
    }

    private static boolean test(String proto, boolean eventService)
            throws Exception {
        System.out.println(">>> Test for protocol " + proto + " with" +
                (eventService ? "" : "out") + " event service");
        JMXServiceURL u = new JMXServiceURL(proto, null, 0);
        JMXConnectorServer server;
        JMXServiceURL addr;
        JMXConnector client;
        MBeanServerConnection mserver;

        final ObjectName delegateName =
                    new ObjectName("JMImplementation:type=MBeanServerDelegate");
        final NotificationListener dummyListener = new NotificationListener() {
                public void handleNotification(Notification n, Object o) {
                    // do nothing
                    return;
                }
            };

        try {
            // with a client listener, but close the server first
            Map<String, String> env = Collections.singletonMap(
                    RMIConnectorServer.DELEGATE_TO_EVENT_SERVICE,
                    Boolean.toString(eventService));
            server = JMXConnectorServerFactory.newJMXConnectorServer(u, env, mbs);
            server.start();

            addr = server.getAddress();
            client = JMXConnectorFactory.newJMXConnector(addr, null);
            client.connect(null);

            mserver = client.getMBeanServerConnection();
            String s1 = "1";
            String s2 = "2";
            String s3 = "3";

            for (int i=0; i<3; i++) {
                mserver.addNotificationListener(delegateName, dummyListener, null, s1);
                mserver.addNotificationListener(delegateName, dummyListener, null, s2);
                mserver.addNotificationListener(delegateName, dummyListener, null, s3);

                mserver.removeNotificationListener(delegateName, dummyListener, null, s3);
                mserver.removeNotificationListener(delegateName, dummyListener, null, s2);
                mserver.removeNotificationListener(delegateName, dummyListener, null, s1);
            }

            for (int i=0; i<3; i++) {
                mserver.addNotificationListener(delegateName, dummyListener, null, s1);
                mserver.addNotificationListener(delegateName, dummyListener, null, s2);
                mserver.addNotificationListener(delegateName, dummyListener, null, s3);

                mserver.removeNotificationListener(delegateName, dummyListener);

                try {
                    mserver.removeNotificationListener(delegateName, dummyListener, null, s1);
                    System.out.println("Failed to remove a listener.");

                    // no expected exception
                    return false;
                } catch (ListenerNotFoundException lne) {
                    // As expected.
                }
            }
            client.close();

            server.stop();

        } catch (MalformedURLException e) {
            System.out.println(">>> Skipping unsupported URL " + u);
            return true;
        }

        return true;
    }
}
