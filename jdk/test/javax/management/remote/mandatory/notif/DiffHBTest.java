/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4911721
 * @summary test on add/remove NotificationListener
 * @author Shanliang JIANG
 * @run clean DiffHBTest
 * @run build DiffHBTest
 * @run main DiffHBTest
 */

import java.net.MalformedURLException;
import java.io.IOException;

import javax.management.*;
import javax.management.remote.*;

/**
 * This test registeres an unique listener with two different handbacks,
 * it expects to receive a same notification two times.
 */
public class DiffHBTest {
    private static final String[] protocols = {"rmi", "iiop", "jmxmp"};

    private static final MBeanServer mbs = MBeanServerFactory.createMBeanServer();
    private static ObjectName delegateName;
    private static ObjectName timerName;

    public static int received = 0;
    public static final int[] receivedLock = new int[0];
    public static Notification receivedNotif = null;

    public static Object receivedHB = null;
    public static final String[] hbs = new String[] {"0", "1"};

    public static void main(String[] args) throws Exception {
        System.out.println(">>> test on one listener with two different handbacks.");

        delegateName = new ObjectName("JMImplementation:type=MBeanServerDelegate");
        timerName = new ObjectName("MBean:name=Timer");

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
        System.out.println(">>> Test for protocol " + proto);
        JMXServiceURL u = new JMXServiceURL(proto, null, 0);
        JMXConnectorServer server;
        JMXServiceURL addr;
        JMXConnector client;
        MBeanServerConnection mserver;

        final NotificationListener dummyListener = new NotificationListener() {
                public void handleNotification(Notification n, Object o) {
                    synchronized(receivedLock) {
                        if (n == null) {
                            System.out.println(">>> Got a null notification.");
                            System.exit(1);
                        }

                        // check number
                        if (received > 2) {
                            System.out.println(">>> Expect to receive 2 notifs,  but get "+received);
                            System.exit(1);
                        }

                        if (received == 0) { // first time
                            receivedNotif = n;
                            receivedHB = o;

                            if (!hbs[0].equals(o) && !hbs[1].equals(o)) {
                                System.out.println(">>> Unkown handback: "+o);
                                System.exit(1);
                            }
                        } else { // second time
                            if (!receivedNotif.equals(n)) {
                                System.out.println(">>> Not get same notif twice.");
                                System.exit(1);
                            } else if (!hbs[0].equals(o) && !hbs[1].equals(o)) { // validate handback
                                System.out.println(">>> Unkown handback: "+o);
                                System.exit(1);
                            } else if (receivedHB.equals(o)) {
                                System.out.println(">>> Got same handback twice: "+o);
                                System.exit(1);
                            }
                        }

                        ++received;

                        if (received == 2) {
                            receivedLock.notify();
                        }
                    }
                }
            };

        try {
            server = JMXConnectorServerFactory.newJMXConnectorServer(u, null, mbs);
            server.start();

            addr = server.getAddress();
            client = JMXConnectorFactory.newJMXConnector(addr, null);
            client.connect(null);

            mserver = client.getMBeanServerConnection();

            mserver.addNotificationListener(delegateName, dummyListener, null, hbs[0]);
            mserver.addNotificationListener(delegateName, dummyListener, null, hbs[1]);

            for (int i=0; i<20; i++) {
                synchronized(receivedLock) {
                    received = 0;
                }

                mserver.createMBean("javax.management.timer.Timer", timerName);

                synchronized(receivedLock) {
                    if (received != 2) {
                        long remainingTime = waitingTime;
                        final long startTime = System.currentTimeMillis();

                        while (received != 2 && remainingTime > 0) {
                            receivedLock.wait(remainingTime);
                            remainingTime = waitingTime -
                                (System.currentTimeMillis() - startTime);
                        }
                    }

                    if (received != 2) {
                        System.out.println(">>> Expected 2 notifis, but received "+received);

                        return false;
                    }
                }


                synchronized(receivedLock) {
                    received = 0;
                }

                mserver.unregisterMBean(timerName);

                synchronized(receivedLock) {
                    if (received != 2) {

                        long remainingTime = waitingTime;
                        final long startTime = System.currentTimeMillis();

                        while (received != 2 && remainingTime >0) {
                            receivedLock.wait(remainingTime);
                            remainingTime = waitingTime -
                                (System.currentTimeMillis() - startTime);
                        }
                    }

                    if (received != 2) {
                        System.out.println(">>> Expected 2 notifis, but received "+received);

                        return false;
                    }
                }
            }

            mserver.removeNotificationListener(delegateName, dummyListener);

            client.close();

            server.stop();

        } catch (MalformedURLException e) {
            System.out.println(">>> Skipping unsupported URL " + u);
            return true;
        }

        return true;
    }

    private final static long waitingTime = 10000;
}
