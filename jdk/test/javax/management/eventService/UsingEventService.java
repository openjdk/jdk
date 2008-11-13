/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test UsingEventService.java 1.10 08/01/22
 * @bug 5108776
 * @summary Basic test for EventManager.
 * @author Shanliang JIANG
 * @run clean UsingEventService
 * @run build UsingEventService
 * @run main UsingEventService
 */

import java.util.HashMap;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventConsumer;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class UsingEventService {
    private static JMXServiceURL url;
    private static JMXConnectorServer server;
    private static JMXConnector conn;
    private static MBeanServerConnection client;

    public static void main(String[] args) throws Exception {
        if (System.getProperty("java.version").startsWith("1.5")) {
            System.out.println(">>> UsingEventService-main not available for JDK1.5, bye");
            return;
        }

        ObjectName oname = new ObjectName("test:t=t");
        Notification n = new Notification("", oname, 0);

        System.out.println(">>> UsingEventService-main basic tests ...");
        MBeanServer mbeanServer = MBeanServerFactory.createMBeanServer();

        url = new JMXServiceURL("rmi", null, 0) ;
        JMXConnectorServer server =
                JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbeanServer);
        server.start();
        url = server.getAddress();

        System.out.println(">>> UsingEventService-main test to not use the event service...");
        conn = JMXConnectorFactory.connect(url, null);
        client = conn.getMBeanServerConnection();

        System.out.println(">>> UsingEventService-main test to use the event service...");
        Map env = new HashMap(1);
        env.put("jmx.remote.use.event.service", "true");
        conn = JMXConnectorFactory.connect(url, env);
        client = conn.getMBeanServerConnection();

        ((EventConsumer)client).subscribe(oname, listener, null, null);

        System.out.println(">>> UsingEventService-main using event service as expected!");

        System.out.println(">>> UsingEventService-main test to use" +
                " the event service with system property...");

        System.setProperty("jmx.remote.use.event.service", "true");
        conn = JMXConnectorFactory.connect(url, null);
        client = conn.getMBeanServerConnection();

        ((EventConsumer)client).subscribe(oname, listener, null, null);

        System.out.println("" +
                ">>> UsingEventService-main using event service as expected!");

        System.out.println(">>> Happy bye bye!");
    }

    private final static NotificationListener listener = new NotificationListener() {
        public void handleNotification(Notification n, Object hk) {
            //
        }
    };
}
