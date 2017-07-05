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
 * @bug 6332907
 * @summary test the ability for connector server to close individual connections
 * @author Shanliang JIANG
 * @run clean CloseConnectionTest
 * @run build CloseConnectionTest
 * @run main CloseConnectionTest
 */
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.management.MBeanServerFactory;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;

public class CloseConnectionTest {

    public static void main(String[] args) throws Exception {
        System.out.println(">>> Test the ability for connector server to close " +
                "individual connections.");

        final String[] protos = new String[]{"rmi", "iiop", "jmxmp"};
        for (String p : protos) {
            System.out.println("\n>>> Testing the protocol " + p);
            JMXServiceURL addr = new JMXServiceURL(p, null, 0);
            System.out.println(">>> Creating a JMXConnectorServer on " + addr);
            JMXConnectorServer server = null;
            try {
                server = JMXConnectorServerFactory.newJMXConnectorServer(addr,
                        null,
                        MBeanServerFactory.createMBeanServer());
            } catch (Exception e) {
                System.out.println(">>> Skip the protocol: " + p);
                continue;
            }

            test1(server);
            test2(server);

            server.stop();
        }

        System.out.println(">>> Bye bye!");
    }

    private static void test1(JMXConnectorServer server) throws Exception {
        try {
            server.closeConnection("toto");
            // not started, known id
            throw new RuntimeException("An IllegalArgumentException is not thrown.");
        } catch (IllegalStateException e) {
            System.out.println(">>> Test1: Got expected IllegalStateException: " + e);
        }

        server.start();
        System.out.println(">>>Test1 Started the server on " + server.getAddress());

        try {
            server.closeConnection("toto");
            throw new RuntimeException("An IllegalArgumentException is not thrown.");
        } catch (IllegalArgumentException e) {
            System.out.println(">> Test1: Got expected IllegalArgumentException: " + e);
        }

        MyListener listener = new MyListener();
        server.addNotificationListener(listener, null, null);

        System.out.println(">>> Test1: Connecting a client to the server ...");
        final JMXConnector conn = JMXConnectorFactory.connect(server.getAddress());
        conn.getMBeanServerConnection().getDefaultDomain();
        final String id1 = conn.getConnectionId();

        listener.wait(JMXConnectionNotification.OPENED, timeout);

        System.out.println(">>> Test1: Closing the connection: " + conn.getConnectionId());
        server.closeConnection(id1);
        listener.wait(JMXConnectionNotification.CLOSED, timeout);

        System.out.println(">>> Test1: Using again the connector whose connection " +
                "should be closed by the server, it should reconnect " +
                "automatically to the server and get a new connection id.");
        conn.getMBeanServerConnection().getDefaultDomain();
        final String id2 = conn.getConnectionId();
        listener.wait(JMXConnectionNotification.OPENED, timeout);

        if (id1.equals(id2)) {
            throw new RuntimeException("Failed, the first client connection is not closed.");
        }

        System.out.println(">>> Test1: Greate, we get a new connection id " + id2 +
                ", the first one is closed as expected.");

        System.out.println(">>> Test1: Closing the client.");
        conn.close();
        System.out.println(">>> Test1: Stopping the server.");
        server.removeNotificationListener(listener);
    }

    private static void test2(JMXConnectorServer server) throws Exception {
        System.out.println(">>> Test2 close a connection before " +
                "the client can use it...");
        final Killer killer = new Killer(server);
        server.addNotificationListener(killer, null, null);

        System.out.println(">>> Test2 Connecting a client to the server ...");
        final JMXConnector conn;
        try {
            conn = JMXConnectorFactory.connect(server.getAddress());
            throw new RuntimeException(">>> Failed, do not receive an " +
                    "IOException telling the connection is refused.");
        } catch (IOException ioe) {
            System.out.println(">>> Test2 got expected IOException: "+ioe);
        }
    }

    private static class MyListener implements NotificationListener {
        public void handleNotification(Notification n, Object hb) {
            if (n instanceof JMXConnectionNotification) {
                synchronized (received) {
                    received.add((JMXConnectionNotification) n);
                    received.notify();
                }
            }
        }

        public JMXConnectionNotification wait(String type, long timeout)
                throws Exception {
            JMXConnectionNotification waited = null;
            long toWait = timeout;
            long deadline = System.currentTimeMillis() + timeout;
            synchronized (received) {
                while (waited == null && toWait > 0) {
                    received.wait(toWait);
                    for (JMXConnectionNotification n : received) {
                        if (type.equals(n.getType())) {
                            waited = n;
                            break;
                        }
                    }
                    received.clear();
                    toWait = deadline - System.currentTimeMillis();
                }
            }

            if (waited == null) {
                throw new RuntimeException("Do not receive expected notification " + type);
            } else {
                System.out.println(">>> Received expected notif: "+type+
                        " "+waited.getConnectionId());
            }

            return waited;
        }

        final List<JMXConnectionNotification> received =
                new ArrayList<JMXConnectionNotification>();
    }

    private static class Killer implements NotificationListener {
        public Killer(JMXConnectorServer server) {
            this.server = server;
        }
        public void handleNotification(Notification n, Object hb) {
            if (n instanceof JMXConnectionNotification) {
                if (JMXConnectionNotification.OPENED.equals(n.getType())) {
                    final JMXConnectionNotification cn =
                            (JMXConnectionNotification)n;
                    try {
                        System.out.println(">>> Killer: close the connection "+
                                cn.getConnectionId());
                        server.closeConnection(cn.getConnectionId());
                    } catch (Exception e) {
                        // impossible?
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            }
        }

        private final JMXConnectorServer server;
    }

    private static final long timeout = 6000;
}
