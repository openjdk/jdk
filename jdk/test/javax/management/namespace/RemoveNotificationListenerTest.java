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
 * @test RemoveNotificationListenerTest.java 1.8
 * @summary General RemoveNotificationListenerTest test.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean RemoveNotificationListenerTest JMXRemoteTargetNamespace
 * @compile -XDignore.symbol.file=true  JMXRemoteTargetNamespace.java
 * @run build RemoveNotificationListenerTest JMXRemoteTargetNamespace
 * @run main/othervm RemoveNotificationListenerTest
 */

import com.sun.jmx.remote.util.EventClientConnection;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.management.JMException;
import javax.management.JMX;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespaces;
import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXConnectorServerMBean;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

/**
 * Class RemoveNotificationListenerTest
 */
public class RemoveNotificationListenerTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(RemoveNotificationListenerTest.class.getName());

    /** Creates a new instance of RemoveNotificationListenerTest */
    public RemoveNotificationListenerTest() {
    }

    public static class SubjectAuthenticator implements JMXAuthenticator {
        final Set<Subject> authorized;
        public SubjectAuthenticator(Subject[] authorized) {
            this.authorized = new HashSet<Subject>(Arrays.asList(authorized));
        }

        public Subject authenticate(Object credentials) {
            if (authorized.contains(credentials))
                return (Subject)credentials;
            else
                throw new SecurityException("Subject not authorized: "+credentials);
        }

    }

    public static interface LongtarMBean {
        public void sendNotification(Object userData)
            throws IOException, JMException;
    }
    public static class Longtar extends NotificationBroadcasterSupport
            implements LongtarMBean {
        public Longtar() {
            super(new MBeanNotificationInfo[] {
                new MBeanNotificationInfo(new String[] {"papillon"},
                        "pv","M'enfin???")
            });
        }

        public void sendNotification(Object userData)
        throws IOException, JMException {
            final Notification n =
                    new Notification("papillon",this,nextseq(),"M'enfin???");
            n.setUserData(userData);
            System.out.println("Sending notification: "+userData);
            sendNotification(n);
        }

        private static synchronized long nextseq() {return ++seqnb;}
        private static volatile long seqnb=0;
    }

    private static final String NS = JMXNamespaces.NAMESPACE_SEPARATOR;
    private static final String CS = "jmx.rmi:type=JMXConnectorServer";
    private static final String BD = "longtar:type=Longtar";

    private static void createNamespace(MBeanServerConnection server,
            String namespace, Subject creator, boolean forwarding)
            throws Exception {
        final MBeanServer sub = MBeanServerFactory.createMBeanServer();
        final JMXServiceURL url = new JMXServiceURL("rmi",null,0);
        final Map<String,Object> smap = new HashMap<String,Object>();
        smap.put(JMXConnectorServer.AUTHENTICATOR,
                new SubjectAuthenticator(new Subject[] {creator}));
        final JMXConnectorServer rmi =
                JMXConnectorServerFactory.newJMXConnectorServer(url,smap,null);
        final ObjectName name = new ObjectName(CS);
        sub.registerMBean(rmi,name);
        rmi.start();
        final Map<String,Object> cmap = new HashMap<String,Object>();
        cmap.put(JMXConnector.CREDENTIALS,creator);
        final Map<String,Object> options = new HashMap<String,Object>(cmap);
        options.put(JMXRemoteTargetNamespace.CREATE_EVENT_CLIENT,"true");
        JMXRemoteTargetNamespace.createNamespace(server,
                namespace,
                rmi.getAddress(),
                options
                );
        server.invoke(JMXNamespaces.getNamespaceObjectName(namespace),
                "connect", null,null);
    }
    private static void closeNamespace(MBeanServerConnection server,
            String namespace) {
        try {
            final ObjectName hname =
                    JMXNamespaces.getNamespaceObjectName(namespace);
            if (!server.isRegistered(hname))
                return;
            final ObjectName sname =
                    new ObjectName(namespace+NS+CS);
            if (!server.isRegistered(sname))
                return;
            final JMXConnectorServerMBean cs =
                    JMX.newMBeanProxy(server,sname,
                    JMXConnectorServerMBean.class,true);
            try {
                cs.stop();
            } finally {
                server.unregisterMBean(hname);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static Subject newSubject(String[] principals) {
        final Set<Principal> ps = new HashSet<Principal>();
        for (String p:principals) ps.add(new JMXPrincipal(p));
        return new Subject(true,ps,Collections.emptySet(),Collections.emptySet());
    }


    public static void testSubject() throws Exception {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        final String a = "a";
        final String b = a + NS + "b";

        final Subject s1 = newSubject(new String[] {"chichille"});
        final Subject s2 = newSubject(new String[] {"alambic"});
        final Subject s3 = newSubject(new String[] {"virgule"});
        final Subject s4 = newSubject(new String[] {"funeste"});

        final JMXServiceURL url = new JMXServiceURL("rmi",null,0);
        final Map<String,Object> smap = new HashMap<String,Object>();
        smap.put(JMXConnectorServer.AUTHENTICATOR,
                new SubjectAuthenticator(new Subject[] {s1}));
        final JMXConnectorServer rmi =
                JMXConnectorServerFactory.newJMXConnectorServer(url,smap,null);
        final ObjectName name = new ObjectName(CS);
        server.registerMBean(rmi,name);
        rmi.start();

        try {

            final Map<String,Object> map = new HashMap<String,Object>();
            map.put(JMXConnector.CREDENTIALS,s1);
            final JMXConnector c =
                    JMXConnectorFactory.connect(rmi.getAddress(),map);
            final MBeanServerConnection mbsorig = c.getMBeanServerConnection();

            final MBeanServerConnection mbs =
                    EventClientConnection.getEventConnectionFor(mbsorig,null);

            createNamespace(mbs,a,s2,true);
            createNamespace(mbs,b,s3,true);

            final ObjectName longtar = new ObjectName(b+NS+BD);

            mbs.createMBean(Longtar.class.getName(),longtar);
            final LongtarMBean proxy =
                    JMX.newMBeanProxy(mbs,longtar,LongtarMBean.class,true);


            final BlockingQueue<Notification> bbq =
                    new ArrayBlockingQueue<Notification>(10);
            final NotificationListener listener1 = new NotificationListener() {
                public void handleNotification(Notification notification,
                        Object handback) {
                    System.out.println(notification.getSequenceNumber()+": "+
                            notification.getMessage());
                    bbq.add(notification);
                }
            };
            final NotificationListener listener2 = new NotificationListener() {
                public void handleNotification(Notification notification,
                        Object handback) {
                    System.out.println(notification.getSequenceNumber()+": "+
                            notification.getMessage());
                    bbq.add(notification);
                }
            };

            final NotificationEmitter ubpdalfdla = (NotificationEmitter)proxy;
            try {

                // Add 1 NL, send 1 notif (1)
                ubpdalfdla.addNotificationListener(listener1,null,listener1);
                proxy.sendNotification(new Integer(1));
                // Thread.sleep(180000);

                // We should have 1 notif with userdata = 1
                final Notification n1 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n1.getUserData()).intValue() != 1)
                    throw new Exception("Expected 1, got"+n1.getUserData());

                // remove NL, send 1 notif (2) => we shouldn't receive it
                ubpdalfdla.removeNotificationListener(listener1,null,listener1);
                proxy.sendNotification(new Integer(2));

                // add NL, send 1 notif (3)
                ubpdalfdla.addNotificationListener(listener1,null,listener1);
                proxy.sendNotification(new Integer(3));

                // we should receive only 1 notif (3)
                final Notification n3 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n3.getUserData()).intValue() != 3)
                    throw new Exception("Expected 3, got"+n3.getUserData());

                // remove NL, send 1 notif (4) => we shouldn't receive it.
                ubpdalfdla.removeNotificationListener(listener1);
                proxy.sendNotification(new Integer(4));

                // add NL, send 1 notif (5).
                ubpdalfdla.addNotificationListener(listener1,null,listener1);
                proxy.sendNotification(new Integer(5));

                // next notif in queue should be (5)
                final Notification n5 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n5.getUserData()).intValue() != 5)
                    throw new Exception("Expected 5, got"+n5.getUserData());

                // add 2 NL, send 1 notif (6)
                ubpdalfdla.addNotificationListener(listener2,null,listener2);
                ubpdalfdla.addNotificationListener(listener2,null,null);
                proxy.sendNotification(new Integer(6));

                // We have 3 NL, we should receive (6) 3 times....
                final Notification n61 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n61.getUserData()).intValue() != 6)
                    throw new Exception("Expected 6 (#1), got"+n61.getUserData());
                final Notification n62 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n62.getUserData()).intValue() != 6)
                    throw new Exception("Expected 6 (#2), got"+n62.getUserData());
                final Notification n63 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n63.getUserData()).intValue() != 6)
                    throw new Exception("Expected 6 (#3), got"+n63.getUserData());

                // Remove 1 NL, send 1 notif (7)
                ubpdalfdla.removeNotificationListener(listener2,null,null);
                proxy.sendNotification(new Integer(7));

                // next notifs in queue should be (7), twice...
                final Notification n71 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n71.getUserData()).intValue() != 7)
                    throw new Exception("Expected 7 (#1), got"+n71.getUserData());
                final Notification n72 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n72.getUserData()).intValue() != 7)
                    throw new Exception("Expected 7 (#2), got"+n72.getUserData());

                // Add 1 NL, send 1 notif (8)
                ubpdalfdla.addNotificationListener(listener2,null,null);
                proxy.sendNotification(new Integer(8));

                // Next notifs in queue should be (8), 3 times.
                final Notification n81 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n81.getUserData()).intValue() != 8)
                    throw new Exception("Expected 8 (#1), got"+n81.getUserData());
                final Notification n82 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n82.getUserData()).intValue() != 8)
                    throw new Exception("Expected 8 (#2), got"+n82.getUserData());
                final Notification n83 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n83.getUserData()).intValue() != 8)
                    throw new Exception("Expected 8 (#3), got"+n83.getUserData());

                // Remove 2 NL, send 1 notif (9)
                ubpdalfdla.removeNotificationListener(listener2);
                proxy.sendNotification(new Integer(9));

                // Next notifs in queue should be (9), 1 time only.
                final Notification n9 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n9.getUserData()).intValue() != 9)
                    throw new Exception("Expected 9, got"+n9.getUserData());

                // send 1 notif (10)
                proxy.sendNotification(new Integer(10));

                // Next notifs in queue should be (10), 1 time only.
                final Notification n10 = bbq.poll(3,TimeUnit.SECONDS);
                // may throw NPE => would indicate a bug.
                if (((Integer)n10.getUserData()).intValue() != 10)
                    throw new Exception("Expected 10, got"+n10.getUserData());

                ubpdalfdla.removeNotificationListener(listener1);
                mbs.unregisterMBean(longtar);

            } finally {
                c.close();
            }
        } finally {
            closeNamespace(server,b);
            closeNamespace(server,a);
            rmi.stop();
        }

    }

    public static void main(String[] args) throws Exception {
        testSubject();
    }

}
