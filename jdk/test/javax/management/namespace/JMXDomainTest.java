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
 * @test JMXDomainTest.java
 * @bug 5072476
 * @summary Basic test for JMXDomain.
 * @author Daniel Fuchs
 * @run clean JMXDomainTest Wombat WombatMBean
 * @run build JMXDomainTest Wombat WombatMBean
 * @run main JMXDomainTest
 */


import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerNotification;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.namespace.JMXDomain;
import javax.management.namespace.MBeanServerSupport;

/**
 * Test simple creation/registration of namespace.
 *
 */
public class JMXDomainTest {
    private static Map<String,Object> emptyEnvMap() {
        return Collections.emptyMap();
    }


    public static class LocalDomainRepository
            extends MBeanServerSupport {
        private final MBeanServer server;
        private final String      domain;

        public class DynamicMBeanProxy implements DynamicMBean {

            private final MBeanServer server;
            private final ObjectName name;

            public DynamicMBeanProxy(MBeanServer s, ObjectName n) {
                this.server = s;
                this.name = n;
            }

            public Object getAttribute(String attribute)
                    throws AttributeNotFoundException,
                    MBeanException, ReflectionException {
                try {
                    return server.getAttribute(name, attribute);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public void setAttribute(Attribute attribute)
                    throws AttributeNotFoundException,
                    InvalidAttributeValueException, MBeanException,
                    ReflectionException {
                try {
                    server.setAttribute(name, attribute);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public AttributeList getAttributes(String[] attributes) {
                try {
                    return server.getAttributes(name, attributes);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public AttributeList setAttributes(AttributeList attributes) {
                try {
                    return server.setAttributes(name, attributes);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public Object invoke(String actionName, Object[] params,
                    String[] signature) throws MBeanException,
                    ReflectionException {
                try {
                    return server.invoke(name, actionName, params, signature);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public MBeanInfo getMBeanInfo() {
                try {
                    return server.getMBeanInfo(name);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }
        }

        public LocalDomainRepository(String domain) {
            this.server = MBeanServerFactory.newMBeanServer();
            this.domain = domain;
        }

        @Override
        protected Set<ObjectName> getNames() {
            try {
            final ObjectName name =
                    ObjectName.getInstance(domain+":*");
            return server.queryNames(name, null);
            } catch (RuntimeException x) {
                throw x;
            } catch (Exception x) {
                throw new RuntimeException(x);
            }
        }

        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
                throws InstanceNotFoundException {
            if (server.isRegistered(name))
                return new DynamicMBeanProxy(server, name);
            throw new InstanceNotFoundException(name);
        }


        @Override
        public NotificationEmitter
                getNotificationEmitterFor(final ObjectName name)
                throws InstanceNotFoundException {
            if (server.isInstanceOf(name, NotificationEmitter.class.getName())) {
                return new NotificationEmitter() {

                    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
                        try {
                            server.removeNotificationListener(name, listener, filter, handback);
                        } catch (InstanceNotFoundException x) {
                            throw new IllegalArgumentException(String.valueOf(name), x);
                        }
                    }

                    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws IllegalArgumentException {
                        try {
                            server.addNotificationListener(name, listener, filter, handback);
                        } catch (InstanceNotFoundException x) {
                            throw new IllegalArgumentException(String.valueOf(name), x);
                        }
                    }

                    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
                        try {
                            server.removeNotificationListener(name, listener);
                        } catch (InstanceNotFoundException x) {
                            throw new IllegalArgumentException(String.valueOf(name), x);
                        }
                    }

                    public MBeanNotificationInfo[] getNotificationInfo() {
                        try {
                            return server.getMBeanInfo(name).getNotifications();
                        } catch (Exception x) {
                            throw new IllegalArgumentException(String.valueOf(name), x);
                        }
                    }
                };
            }
            return null;
        }

        @Override
        public ObjectInstance registerMBean(Object object, ObjectName name)
                throws InstanceAlreadyExistsException,
                MBeanRegistrationException, NotCompliantMBeanException {
            return server.registerMBean(object, name);
        }

        @Override
        public void unregisterMBean(ObjectName name)
                throws InstanceNotFoundException,
                MBeanRegistrationException {
            server.unregisterMBean(name);
        }

        @Override
        public ObjectInstance createMBean(String className,
                ObjectName name, ObjectName loaderName, Object[] params,
                String[] signature, boolean useCLR)
                throws ReflectionException, InstanceAlreadyExistsException,
                MBeanRegistrationException, MBeanException,
                NotCompliantMBeanException, InstanceNotFoundException {
            if (useCLR && loaderName == null) {
                return server.createMBean(className, name, params, signature);
            }
            return server.createMBean(className, name, loaderName,
                    params, signature);
        }


    }

    private static MBeanServer newMBeanServer() {
        return MBeanServerFactory.newMBeanServer();
    }

    public static interface ThingMBean {}
    public static class Thing implements ThingMBean, MBeanRegistration {
        public ObjectName preRegister(MBeanServer server, ObjectName name)
                throws Exception {
            if (name == null) return new ObjectName(":type=Thing");
            else return name;
        }
        public void postRegister(Boolean registrationDone) {
        }

        public void preDeregister() throws Exception {
        }
        public void postDeregister() {
        }
    }

    /**
     * Test that it is possible to create a dummy MBean with a null
     * ObjectName - this is just a sanity check - as there are already
     * other JMX tests that check that.
     *
     * @throws java.lang.Exception
     */
    public static void testCreateWithNull() throws Exception {
        final MBeanServer server = newMBeanServer();
        final ObjectInstance oi = server.registerMBean(new Thing(),null);
        server.unregisterMBean(oi.getObjectName());
        System.out.println("testCreateWithNull PASSED");
    }

    public static void testRegisterSimple() throws Exception {
        final ObjectName name =
                JMXDomain.getDomainObjectName("gloups");
        final JMXDomain jmxDomain = new JMXDomain(
                MBeanServerFactory.newMBeanServer());
        testRegister("testRegisterSimple: ",name,jmxDomain);
    }

    public static void testRegisterPseudoVirtual()
            throws Exception {
        final ObjectName name =
                JMXDomain.getDomainObjectName("gloups");
        final JMXDomain jmxDomain = new JMXDomain(
                new LocalDomainRepository("gloups"));
        testRegister("testRegisterPseudoVirtual: ",name,jmxDomain);
    }

    public static void testRegister(final String test,
            final ObjectName name,
            final JMXDomain jmxDomain) throws Exception {
        System.out.println(test+" START");
        MBeanServer server = newMBeanServer();
        final ObjectInstance oi =
                server.registerMBean(jmxDomain,name);
        System.out.println(test+"Succesfully registered namespace: "+name);
        if (!server.isRegistered(name))
            fail(test+name+" is not registered!");
        if (!server.queryNames(new ObjectName(name.getDomain()+":*"), null).
                contains(name))
            fail(test+name+" not in queryNames");

        final Thing thing = new Thing();
        final ObjectName thingName = new ObjectName("gloups:type=Thing");
        server.registerMBean(thing,thingName);
        if (!server.isRegistered(thingName))
            fail(test+thingName+" is not registered!");
        if (!jmxDomain.getSourceServer().isRegistered(thingName))
            fail(test+thingName+" is not registered in domain!");
        if (!server.queryNames(new ObjectName(name.getDomain()+":*"), null).
                contains(thingName))
            fail(test+thingName+" not in queryNames");

        server.unregisterMBean(name);
        if (server.isRegistered(thingName))
            fail(test+thingName+" is still registered!");
        if (server.queryNames(new ObjectName(name.getDomain()+":*"), null).
                contains(thingName))
            fail(test+thingName+" still in queryNames");

        server.registerMBean(jmxDomain, name);
        if (!server.isRegistered(thingName))
            fail(test+thingName+" is not registered again!");

        System.out.println(test+" PASSED");
    }

    private static MBeanServerNotification pop(
            BlockingQueue<Notification> queue,
                                    String type,
                                    ObjectName mbean,
                                    String test)
                                    throws InterruptedException {
        final Notification n = queue.poll(1, TimeUnit.SECONDS);
        if (!(n instanceof MBeanServerNotification))
            fail(test+"expected MBeanServerNotification, got "+n);
        final MBeanServerNotification msn = (MBeanServerNotification)n;
        if (!type.equals(msn.getType()))
            fail(test+"expected "+type+", got "+msn.getType());
        if (!mbean.apply(msn.getMBeanName()))
            fail(test+"expected "+mbean+", got "+msn.getMBeanName());
        System.out.println(test+" got: "+msn);
        return msn;
    }
    private static MBeanServerNotification popADD(
            BlockingQueue<Notification> queue,
                                    ObjectName mbean,
                                    String test)
                                    throws InterruptedException {
        return pop(queue, MBeanServerNotification.REGISTRATION_NOTIFICATION,
                mbean, test);
    }

    private static MBeanServerNotification popREM(
            BlockingQueue<Notification> queue,
                                    ObjectName mbean,
                                    String test)
                                    throws InterruptedException {
        return pop(queue, MBeanServerNotification.UNREGISTRATION_NOTIFICATION,
                mbean, test);
    }


    public static void testRegisterNotifSimple() throws Exception {
        final ObjectName name =
                JMXDomain.getDomainObjectName("gloups");
        final JMXDomain jmxDomain = new JMXDomain(
                MBeanServerFactory.newMBeanServer());
        testRegisterNotif("testRegisterNotifSimple: ",name,jmxDomain);
    }

    public static void testRegisterNotifPseudoVirtual()
            throws Exception {
        final ObjectName name =
                JMXDomain.getDomainObjectName("gloups");
        final JMXDomain jmxDomain = new JMXDomain(
                new LocalDomainRepository("gloups"));
        testRegisterNotif("testRegisterNotifPseudoVirtual: ",name,jmxDomain);
    }

    public static void testRegisterNotif(final String test,
            final ObjectName name,
            final JMXDomain jmxDomain) throws Exception {
        System.out.println(test+" START");
        MBeanServer server = newMBeanServer();
        final ObjectInstance oi =
                server.registerMBean(jmxDomain,name);
        System.out.println(test+"Succesfully registered namespace: "+name);
        if (!server.isRegistered(name))
            fail(test+name+" is not registered!");

        final BlockingQueue<Notification> queue =
                new ArrayBlockingQueue<Notification>(10);

        final NotificationListener l = new NotificationListener() {

            public void handleNotification(Notification notification,
                    Object handback) {
                try {
                    if (!queue.offer(notification,5,TimeUnit.SECONDS))
                        throw new RuntimeException("timeout exceeded");
                } catch (Exception x) {
                    fail(test+"failed to handle notif", x);
                }
            }
        };

        server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, l,
                null, null);

        final Thing thing = new Thing();
        final ObjectName thingName = new ObjectName("gloups:type=Thing");

        server.registerMBean(thing,thingName);
        if (!jmxDomain.getSourceServer().isRegistered(thingName))
            fail(test+thingName+" is not registered in domain!");
        popADD(queue, thingName, test);
        server.unregisterMBean(thingName);
        if (jmxDomain.getSourceServer().isRegistered(thingName))
            fail(test+thingName+" is still registered in domain!");
        popREM(queue, thingName, test);
        if (queue.size() != 0)
            fail(test+queue.size()+" notifs remain in queue "+queue);

        server.unregisterMBean(name);
        popREM(queue, name, test);

        jmxDomain.getSourceServer().registerMBean(thing,thingName);
        if (server.isRegistered(thingName))
            fail(test+thingName+" is still registered in domain!");
        jmxDomain.getSourceServer().unregisterMBean(thingName);
        if (queue.size() != 0)
            fail(test+queue.size()+" notifs remain in queue "+queue);

        server.registerMBean(jmxDomain, name);
        if (!server.isRegistered(name))
            fail(test+name+" is not registered again!");
        popADD(queue, name, test);
        if (queue.size() != 0)
            fail(test+queue.size()+" notifs remain in queue "+queue);

        server.registerMBean(thing,thingName);
        if (!jmxDomain.getSourceServer().isRegistered(thingName))
            fail(test+thingName+" is not registered in domain!");
        popADD(queue, thingName, test);
        server.unregisterMBean(thingName);
        if (jmxDomain.getSourceServer().isRegistered(thingName))
            fail(test+thingName+" is still registered in domain!");
        popREM(queue, thingName, test);
        if (queue.size() != 0)
            fail(test+queue.size()+" notifs remain in queue "+queue);

        System.out.println(test+" PASSED");
    }



    private static void fail(String msg) {
        raise(new RuntimeException(msg));
    }

    private static void fail(String msg, Throwable cause) {
        raise(new RuntimeException(msg,cause));
    }

    private static void raise(RuntimeException x) {
        lastException = x;
        exceptionCount++;
        throw x;
    }

    private static volatile Exception lastException = null;
    private static volatile int       exceptionCount = 0;

    public static void main(String... args) throws Exception {
        testCreateWithNull();

        testRegisterSimple();
        testRegisterNotifSimple();

        testRegisterPseudoVirtual();
        testRegisterNotifPseudoVirtual();

        if (lastException != null)
            throw lastException;
    }
}
