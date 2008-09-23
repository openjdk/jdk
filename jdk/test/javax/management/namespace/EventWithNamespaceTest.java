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
 *
 * @test EventWithNamespaceTest.java 1.8
 * @bug 6539857 5072476 5108776
 * @summary General Namespace & Notifications test.
 * @author Daniel Fuchs
 * @run clean EventWithNamespaceTest Wombat WombatMBean
 *            JMXRemoteTargetNamespace
 *            NamespaceController NamespaceControllerMBean
 * @compile -XDignore.symbol.file=true EventWithNamespaceTest.java
 *          Wombat.java WombatMBean.java JMXRemoteTargetNamespace.java
 *          NamespaceController.java NamespaceControllerMBean.java
 * @run main EventWithNamespaceTest
 */

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespaces;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author Sun Microsystems, Inc.
 */
public class EventWithNamespaceTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(EventWithNamespaceTest.class.getName());

    /** Creates a new instance of EventWithNamespaceTest */
    public EventWithNamespaceTest() {
    }

    private static Map<String,?> singletonMap(String key, Object value) {
        final Map<String,Object> map = new HashMap<String,Object>();
        map.put(key,value);
        return map;
    }

    public  Map<String,?> getServerMap() {
        return singletonMap(JMXConnectorServer.DELEGATE_TO_EVENT_SERVICE,"true");
    }

    public JMXServiceURL export(MBeanServer server)
    throws Exception {
        final JMXServiceURL in = new JMXServiceURL("rmi",null,0);
        final Map<String,?> env = getServerMap();

        final JMXConnectorServer cs =
                JMXConnectorServerFactory.newJMXConnectorServer(in,env,null);
        final ObjectName csname = ObjectName.
                getInstance(cs.getClass().getPackage().getName()+
                ":type="+cs.getClass().getSimpleName());
        server.registerMBean(cs,csname);
        cs.start();
        return cs.getAddress();
    }

    public static class Counter {
        int count;
        public synchronized int count() {
            count++;
            notifyAll();
            return count;
        }
        public synchronized int peek() {
            return count;
        }
        public synchronized int waitfor(int max, long timeout)
        throws InterruptedException {
            final long start = System.currentTimeMillis();
            while (count < max && timeout > 0) {
                final long rest = timeout -
                        (System.currentTimeMillis() - start);
                if (rest <= 0) break;
                wait(rest);
            }
            return count;
        }
    }

    public static class CounterListener
            implements NotificationListener {
        final private Counter counter;
        public CounterListener(Counter counter) {
            this.counter = counter;
        }
        public void handleNotification(Notification notification,
                Object handback) {
            System.out.println("Received notif from " + handback +
                    ":\n\t" + notification);
            if (!notification.getSource().equals(handback)) {
                System.err.println("OhOh... Unexpected source: \n\t"+
                        notification.getSource()+"\n\twas expecting:\n\t"+
                        handback);
            }
            counter.count();
        }
    }

    public void simpleTest(String[] args) {
        try {
            final MBeanServer server1 =
                    ManagementFactory.getPlatformMBeanServer();
            final JMXServiceURL url1 = export(server1);

            final MBeanServer server2 =
                    MBeanServerFactory.createMBeanServer("server2");
            final JMXServiceURL url2 = export(server2);

            final MBeanServer server3 =
                    MBeanServerFactory.createMBeanServer("server3");
            final JMXServiceURL url3 = export(server3);

            final ObjectInstance ncinst =
                    NamespaceController.createInstance(server1);

            final NamespaceControllerMBean nc =
                    JMX.newMBeanProxy(server1,ncinst.getObjectName(),
                    NamespaceControllerMBean.class);

            final String mount2 = nc.mount(url2,"server2",null);
            final String mount3 = nc.mount(url3,"server2//server3",
                    null);

            final ObjectName deep =
                    new ObjectName("server2//server3//bush:type=Wombat,name=kanga");
            server1.createMBean(Wombat.class.getName(),deep);

            System.err.println("There's a wombat in the bush!");

            final Counter counter = new Counter();

            final NotificationListener listener =
                    new CounterListener(counter);

            final JMXConnector jc = JMXConnectorFactory.connect(url1);
            final MBeanServerConnection conn1 =
                    jc.getMBeanServerConnection();
            final ObjectName shallow =
                    new ObjectName("bush:"+
                    deep.getKeyPropertyListString());
            final MBeanServerConnection conn2 =
                    JMXNamespaces.narrowToNamespace(conn1,"server2//server3");

            final WombatMBean proxy1 =
                    JMX.newMBeanProxy(conn1,deep,WombatMBean.class,true);
            final WombatMBean proxy2 =
                    JMX.newMBeanProxy(conn2,shallow,WombatMBean.class,true);


            System.err.println("Adding first Notification Listener");
            conn1.addNotificationListener(deep,listener,null,deep);
            System.err.println("Adding second Notification Listener");
            ((NotificationEmitter)proxy2).
                    addNotificationListener(listener,null,shallow);
            final JMXConnector c3 = JMXConnectorFactory.connect(url3,
                    singletonMap(JMXConnector.USE_EVENT_SERVICE,"false"));
            System.err.println("Adding third Notification Listener");
            c3.getMBeanServerConnection().
                    addNotificationListener(shallow,listener,null,shallow);
            System.err.println("Set attribute to trigger notif");
            proxy1.setCaption("I am a new Wombat!");
            System.err.println("Get attribute");
            System.err.println("New caption: "+proxy2.getCaption());
            System.err.println("Wait for Notifs...");
            final int rcvcount = counter.waitfor(3,3000);
            if (rcvcount != 3)
                throw new RuntimeException("simpleTest failed: "+
                        "received count is " +rcvcount);
            System.err.println("simpleTest: got expected "+rcvcount+
                    " notifs");

            System.err.println("removing all listeners");
            conn1.removeNotificationListener(deep,listener,null,deep);
            ((NotificationEmitter)proxy2)
                .removeNotificationListener(listener,null,shallow);
            c3.getMBeanServerConnection().
                    removeNotificationListener(shallow,listener,null,shallow);

            System.err.println("simpleTest passed: got "+rcvcount+
                    " notifs");

        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new RuntimeException("simpleTest failed: " + x,x);
        }
    }

    public void run(String[] args) {
                simpleTest(args);
    }

    public static void main(String[] args) {
        new EventWithNamespaceTest().run(args);
    }

}
