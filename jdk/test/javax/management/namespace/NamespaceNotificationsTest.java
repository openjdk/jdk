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
 * @test NamespaceNotificationsTest.java 1.12
 * @summary General Namespace & Notifications test.
 * @bug 5072476
 * @author Daniel Fuchs
 * @run clean NamespaceNotificationsTest
 *            Wombat WombatMBean JMXRemoteTargetNamespace
 *            NamespaceController NamespaceControllerMBean
 * @compile -XDignore.symbol.file=true  NamespaceNotificationsTest.java
 *            Wombat.java WombatMBean.java JMXRemoteTargetNamespace.java
 *            NamespaceController.java NamespaceControllerMBean.java
 * @run main NamespaceNotificationsTest
 */
import com.sun.jmx.remote.util.EventClientConnection;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.loading.MLet;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author Sun Microsystems, Inc.
 */
public class NamespaceNotificationsTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(NamespaceNotificationsTest.class.getName());

    /** Creates a new instance of NamespaceNotificationsTest */
    public NamespaceNotificationsTest() {
    }


    public static JMXServiceURL export(MBeanServer server)
    throws Exception {
        final JMXServiceURL in = new JMXServiceURL("rmi",null,0);
        final JMXConnectorServer cs =
                JMXConnectorServerFactory.newJMXConnectorServer(in,null,null);
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

    public static void simpleTest(String[] args) {
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

            final Map<String,Object> options = new HashMap<String,Object>();
            options.put(JMXRemoteTargetNamespace.CREATE_EVENT_CLIENT,"true");

            final String mount1 =
                    nc.mount(url1,"server1",options);
            final String mount2 = nc.mount(url2,"server1//server2",
                    options);
            final String mount3 = nc.mount(url3,
                    "server1//server2//server3",
                    options);
            final String mount13 = nc.mount(
                    url1,
                    "server3",
                    "server2//server3",
                    options);
            final String mount21 = nc.mount(url1,"server2//server1",
                    options);
            final String mount31 = nc.mount(
                    url1,
                    "server3//server1",
                    "server1",
                    options);
            final String mount32 = nc.mount(
                    url1,
                    "server3//server2",
                    "server2",
                    options);


            final ObjectName deep =
                    new ObjectName("server1//server2//server3//bush:type=Wombat,name=kanga");
            server1.createMBean(Wombat.class.getName(),deep);

            System.err.println("There's a wombat in the bush!");

            final Counter counter = new Counter();

            final NotificationListener listener =
                    new CounterListener(counter);

            final JMXConnector jc = JMXConnectorFactory.connect(url1);
            final MBeanServerConnection aconn =
                    EventClientConnection.getEventConnectionFor(
                        jc.getMBeanServerConnection(),null);
            aconn.addNotificationListener(deep,listener,null,deep);


            final JMXServiceURL urlx = new JMXServiceURL(url1.toString());
            System.out.println("conn: "+urlx);
            final JMXConnector jc2 = JMXNamespaces.narrowToNamespace(
                    JMXConnectorFactory.connect(urlx),"server1//server1");
            final JMXConnector jc3 = JMXNamespaces.narrowToNamespace(jc2,"server3");
            jc3.connect();
            System.out.println("JC#3: " +
                    ((jc3 instanceof JMXAddressable)?
                        ((JMXAddressable)jc3).getAddress():
                        jc3.toString()));
            final MBeanServerConnection bconn =
                    jc3.getMBeanServerConnection();
            final ObjectName shallow =
                    new ObjectName("bush:"+
                    deep.getKeyPropertyListString());
            final WombatMBean proxy =
                    JMX.newMBeanProxy(EventClientConnection.getEventConnectionFor(
                        bconn,null),shallow,WombatMBean.class,true);

            ((NotificationEmitter)proxy).
                    addNotificationListener(listener,null,shallow);
            proxy.setCaption("I am a new Wombat!");
            System.err.println("New caption: "+proxy.getCaption());
            final int rcvcount = counter.waitfor(2,3000);
            if (rcvcount != 2)
                throw new RuntimeException("simpleTest failed: "+
                        "received count is " +rcvcount);

            System.err.println("simpleTest passed: got "+rcvcount+
                    " notifs");

        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new RuntimeException("simpleTest failed: " + x,x);
        }
    }

    public static class LocalNamespace extends
            JMXNamespace {
        LocalNamespace() {
            super(MBeanServerFactory.newMBeanServer());
        }

    }

    public static class ContextObject<K,V> {
        public final K name;
        public final V object;
        public ContextObject(K name, V object) {
            this.name = name;
            this.object = object;
        }
        private Object[] data() {
            return new Object[] {name,object};
        }

        @Override
        public boolean equals(Object x) {
            if (x instanceof ContextObject)
                return Arrays.deepEquals(data(),((ContextObject<?,?>)x).data());
            return false;
        }
        @Override
        public int hashCode() {
            return Arrays.deepHashCode(data());
        }
    }

    private static <K,V> ContextObject<K,V> context(K k, V v) {
        return new ContextObject<K,V>(k,v);
    }

    private static ObjectName name(String name) {
        try {
            return new ObjectName(name);
        } catch(MalformedObjectNameException x) {
            throw new IllegalArgumentException(name,x);
        }
    }

    public static void simpleTest2() {
        try {
            System.out.println("\nsimpleTest2: STARTING\n");
            final LocalNamespace foo = new LocalNamespace();
            final LocalNamespace joe = new LocalNamespace();
            final LocalNamespace bar = new LocalNamespace();
            final MBeanServer server = MBeanServerFactory.newMBeanServer();

            server.registerMBean(foo,JMXNamespaces.getNamespaceObjectName("foo"));
            server.registerMBean(joe,JMXNamespaces.getNamespaceObjectName("foo//joe"));
            server.registerMBean(bar,JMXNamespaces.getNamespaceObjectName("foo//bar"));
            final BlockingQueue<ContextObject<String,MBeanServerNotification>> queue =
                    new ArrayBlockingQueue<ContextObject<String,MBeanServerNotification>>(20);

            final NotificationListener listener = new NotificationListener() {
                public void handleNotification(Notification n, Object handback) {
                    if (!(n instanceof MBeanServerNotification)) {
                        System.err.println("Error: expected MBeanServerNotification");
                        return;
                    }
                    final MBeanServerNotification mbsn =
                            (MBeanServerNotification) n;

                    // We will pass the namespace name in the handback.
                    //
                    final String namespace = (String) handback;
                    System.out.println("Received " + mbsn.getType() +
                            " for MBean " + mbsn.getMBeanName() +
                            " from name space " + namespace);
                    try {
                        queue.offer(context(namespace,mbsn),500,TimeUnit.MILLISECONDS);
                    } catch (Exception x) {
                        System.err.println("Failed to enqueue received notif: "+mbsn);
                        x.printStackTrace();
                    }
                }
            };

            server.addNotificationListener(JMXNamespaces.insertPath("foo//joe",
                    MBeanServerDelegate.DELEGATE_NAME),listener,null,"foo//joe");
            server.addNotificationListener(JMXNamespaces.insertPath("foo//bar",
                    MBeanServerDelegate.DELEGATE_NAME),listener,null,"foo//bar");
            server.createMBean(MLet.class.getName(),
                    name("foo//joe//domain:type=MLet"));
            checkQueue(queue,"foo//joe",
                    MBeanServerNotification.REGISTRATION_NOTIFICATION);
            server.createMBean(MLet.class.getName(),
                    name("foo//bar//domain:type=MLet"));
            checkQueue(queue,"foo//bar",
                    MBeanServerNotification.REGISTRATION_NOTIFICATION);
            server.unregisterMBean(
                    name("foo//joe//domain:type=MLet"));
            checkQueue(queue,"foo//joe",
                    MBeanServerNotification.UNREGISTRATION_NOTIFICATION);
            server.unregisterMBean(
                    name("foo//bar//domain:type=MLet"));
            checkQueue(queue,"foo//bar",
                    MBeanServerNotification.UNREGISTRATION_NOTIFICATION);
        } catch (RuntimeException x) {
            System.err.println("FAILED: "+x);
            throw x;
        } catch(Exception x) {
            System.err.println("FAILED: "+x);
            throw new RuntimeException("Unexpected exception: "+x,x);
        }
    }


    private static void checkQueue(
            BlockingQueue<ContextObject<String,MBeanServerNotification>> q,
                              String path, String type) {
        try {
          final ContextObject<String,MBeanServerNotification> ctxt =
                    q.poll(500,TimeUnit.MILLISECONDS);
          if (ctxt == null)
            throw new RuntimeException("Timeout expired: expected notif from "+
                    path +", type="+type);
          if (!ctxt.name.equals(path))
            throw new RuntimeException("expected notif from "+
                    path +", got "+ctxt.name);
          if (!ctxt.object.getType().equals(type))
            throw new RuntimeException(ctxt.name+": expected type="+
                    type +", got "+ctxt.object.getType());
          if (!ctxt.object.getType().equals(type))
            throw new RuntimeException(ctxt.name+": expected type="+
                    type +", got "+ctxt.object.getType());
          if (!ctxt.object.getMBeanName().equals(name("domain:type=MLet")))
            throw new RuntimeException(ctxt.name+": expected MBean=domain:type=MLet"+
                    ", got "+ctxt.object.getMBeanName());
        } catch(InterruptedException x) {
            throw new RuntimeException("unexpected interruption: "+x,x);
        }
    }

    public static void main(String[] args) {
        simpleTest(args);
        simpleTest2();
    }

}
