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
 * @test VirtualMBeanNotifTest.java
 * @bug 5108776
 * @build VirtualMBeanNotifTest Wombat WombatMBean
 * @run main VirtualMBeanNotifTest
 * @summary Test that Virtual MBeans can be implemented and emit notifs.
 * @author  Daniel Fuchs
 */
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.management.Attribute;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;
import javax.management.StandardMBean;
import javax.management.event.EventSubscriber;
import javax.management.namespace.VirtualEventManager;
import javax.management.namespace.MBeanServerSupport;

public class VirtualMBeanNotifTest {

    /**
     * An invocation handler that can implement DynamicMBean,
     * NotificationBroadcaster, NotificationEmitter.
     * The invocation handler works by forwarding all calls received from
     * the implemented interfaces through a wrapped MBeanServer object.
     */
    public static class DynamicWrapper
            implements InvocationHandler {

        /**
         * Inserts an additional class at the head of a signature array.
         * @param first The first class in the signature
         * @param rest  The other classes in the signature
         * @return A signature array, of length rest.length+1.
         */
        static Class[] concat(Class first, Class... rest) {
            if (rest == null || rest.length == 0) {
                return new Class[] { first };
            }
            final Class[] sig = new Class[rest.length+1];
            sig[0] = first;
            System.arraycopy(rest, 0, sig, 1, rest.length);
            return sig;
        }

        /**
         * Inserts an additional object at the head of a parameters array.
         * @param first The first object in the parameter array.
         * @param rest  The other objects in the parameter array.
         * @return A parameter array, of length rest.length+1.
         */
        static Object[] concat(Object first, Object... rest) {
            if (rest == null || rest.length == 0) {
                return new Object[] { first };
            }
            final Object[] params = new Object[rest.length+1];
            params[0] = first;
            System.arraycopy(rest, 0, params, 1, rest.length);
            return params;
        }

        /**
         * These two sets are used to check that all methods from
         * implemented interfaces are mapped.
         * unmapped is the set of methods that couldn't be mapped.
         * mapped is the set of methods that could be mapped.
         */
        final static Set<Method> unmapped = new HashSet<Method>();
        final static Set<Method> mapped = new HashSet<Method>();

        /**
         * For each method define in one of the interfaces intf, tries
         * to find a corresponding method in the reference class ref, where
         * the method in ref has the same name, and takes an additional
         * ObjectName as first parameter.
         *
         * So for instance, if ref is MBeanServer and intf is {DynamicMBean}
         * the result map is:
         *     DynamicMBean.getAttribute -> MBeanServer.getAttribute
         *     DynamicMBean.setAttribute -> MBeanServer.setAttribute
         *     etc...
         * If a method was mapped, it is additionally added to 'mapped'
         * If a method couldn't be mapped, it is added to 'unmmapped'.
         * In our example above, DynamicMBean.getNotificationInfo will end
         * up in 'unmapped'.
         *
         * @param ref   The reference class - to which calls will be forwarded
         *              with an additional ObjectName parameter inserted.
         * @param intf  The proxy interface classes - for which we must find an
         *              equivalent in 'ref'
         * @return A map mapping the methods from intfs to the method of ref.
         */
        static Map<Method,Method> makeMapFor(Class<?> ref, Class<?>... intf) {
            final Map<Method,Method> map = new HashMap<Method,Method>();
            for (Class<?> clazz : intf) {
                for (Method m : clazz.getMethods()) {
                    try {
                        final Method m2 =
                            ref.getMethod(m.getName(),
                            concat(ObjectName.class,m.getParameterTypes()));
                        map.put(m,m2);
                        mapped.add(m);
                    } catch (Exception x) {
                        unmapped.add(m);
                    }
                }
            }
            return map;
        }

        /**
         * Tries to map all methods from DynamicMBean.class and
         * NotificationEmitter.class to their equivalent in MBeanServer.
         * This should be all the methods except
         * DynamicMBean.getNotificationInfo.
         */
        static final Map<Method,Method> mbeanmap =
                makeMapFor(MBeanServer.class,DynamicMBean.class,
                NotificationEmitter.class);
        /**
         * Tries to map all methods from DynamicMBean.class and
         * NotificationEmitter.class to an equivalent in DynamicWrapper.
         * This time only DynamicMBean.getNotificationInfo will be mapped.
         */
        static final Map<Method,Method> selfmap =
                makeMapFor(DynamicWrapper.class,DynamicMBean.class,
                NotificationEmitter.class);

        /**
         * Now check that we have mapped all methods.
         */
        static {
            unmapped.removeAll(mapped);
            if (unmapped.size() > 0)
                throw new ExceptionInInitializerError("Couldn't map "+ unmapped);
        }

        /**
         * The wrapped MBeanServer to which everything is delegated.
         */
        private final MBeanServer server;

        /**
         * The name of the MBean we're proxying.
         */
        private final ObjectName name;
        DynamicWrapper(MBeanServer server, ObjectName name) {
            this.server=server;
            this.name=name;
        }

        /**
         * Creates a new proxy for the given MBean. Implements
         * NotificationEmitter/NotificationBroadcaster if the proxied
         * MBean also does.
         * @param name    the name of the proxied MBean
         * @param server  the wrapped server
         * @return a DynamicMBean proxy
         * @throws javax.management.InstanceNotFoundException
         */
        public static DynamicMBean newProxy(ObjectName name, MBeanServer server)
            throws InstanceNotFoundException {
            if (server.isInstanceOf(name,
                    NotificationEmitter.class.getName())) {
                // implements NotificationEmitter
                return (DynamicMBean)
                        Proxy.newProxyInstance(
                        DynamicWrapper.class.getClassLoader(),
                        new Class[] {NotificationEmitter.class,
                        DynamicMBean.class},
                        new DynamicWrapper(server, name));
            }
            if (server.isInstanceOf(name,
                    NotificationBroadcaster.class.getName())) {
                // implements NotificationBroadcaster
                return (DynamicMBean)
                        Proxy.newProxyInstance(
                        DynamicWrapper.class.getClassLoader(),
                        new Class[] {NotificationBroadcaster.class,
                        DynamicMBean.class},
                        new DynamicWrapper(server, name));
            }
            // Only implements DynamicMBean.
            return (DynamicMBean)
                        Proxy.newProxyInstance(
                        DynamicWrapper.class.getClassLoader(),
                        new Class[] {DynamicMBean.class},
                        new DynamicWrapper(server, name));
        }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            // Look for a method on this class (takes precedence)
            final Method self = selfmap.get(method);
            if (self != null)
                return call(this,self,concat(name,args));

            // no method found on this class, look for the same method
            // on the wrapped MBeanServer
            final Method mbean = mbeanmap.get(method);
            if (mbean != null)
                return call(server,mbean,concat(name,args));

            // This isn't a method that can be forwarded to MBeanServer.
            // If it's a method from Object, call it on this.
            if (method.getDeclaringClass().equals(Object.class))
                return call(this,method,args);
            throw new NoSuchMethodException(method.getName());
        }

        // Call a method using reflection, unwraps invocation target exceptions
        public Object call(Object handle, Method m, Object[] args)
                throws Throwable {
            try {
                return m.invoke(handle, args);
            } catch (InvocationTargetException x) {
               throw x.getCause();
            }
        }

        // this method is called when DynamicMBean.getNotificationInfo() is
        // called. This is the method that should be mapped in
        // 'selfmap'
        public MBeanNotificationInfo[] getNotificationInfo(ObjectName name)
            throws JMException {
            return server.getMBeanInfo(name).getNotifications();
        }
    }

    /**
     * Just so that we can call the same test twice but with two
     * different implementations of VirtualMBeanServerSupport.
     */
    public static interface MBeanServerWrapperFactory {
        public MBeanServer wrapMBeanServer(MBeanServer wrapped);
    }

    /**
     * A VirtualMBeanServerSupport that wrapps an MBeanServer and does not
     * use VirtualEventManager.
     */
    public static class VirtualMBeanServerTest
            extends MBeanServerSupport {

        final MBeanServer wrapped;

        public VirtualMBeanServerTest(MBeanServer wrapped) {
            this.wrapped=wrapped;
        }

        @Override
        public DynamicMBean getDynamicMBeanFor(final ObjectName name)
                throws InstanceNotFoundException {
            if (wrapped.isRegistered(name))
                return DynamicWrapper.newProxy(name,wrapped);
            throw new InstanceNotFoundException(String.valueOf(name));
        }

        @Override
        protected Set<ObjectName> getNames() {
            return wrapped.queryNames(null, null);
        }

        public final static MBeanServerWrapperFactory factory =
                new MBeanServerWrapperFactory() {

            public MBeanServer wrapMBeanServer(MBeanServer wrapped) {
                return new VirtualMBeanServerTest(wrapped);
            }
            @Override
            public String toString() {
                return VirtualMBeanServerTest.class.getName();
            }
        };
    }

     /**
     * A VirtualMBeanServerSupport that wrapps an MBeanServer and
     * uses a VirtualEventManager.
     */
    public static class VirtualMBeanServerTest2
            extends VirtualMBeanServerTest {

        final EventSubscriber sub;
        final NotificationListener nl;
        final VirtualEventManager  mgr;

        /**
         * We use an EventSubscriber to subscribe for all notifications from
         * the wrapped MBeanServer, and publish them through a
         * VirtualEventManager. Not a very efficient way of doing things.
         * @param wrapped
         */
        public VirtualMBeanServerTest2(MBeanServer wrapped) {
            super(wrapped);
            this.sub = EventSubscriber.getEventSubscriber(wrapped);
            this.mgr = new VirtualEventManager();
            this.nl = new NotificationListener() {
                public void handleNotification(Notification notification, Object handback) {
                    mgr.publish((ObjectName)notification.getSource(), notification);
                }
            };
            try {
                sub.subscribe(ObjectName.WILDCARD, nl, null, null);
            } catch (RuntimeException x) {
                throw x;
            } catch (Exception x) {
                throw new IllegalStateException("can't subscribe for notifications!");
            }
        }

        @Override
        public NotificationEmitter
                getNotificationEmitterFor(ObjectName name)
                throws InstanceNotFoundException {
            final DynamicMBean mbean = getDynamicMBeanFor(name);
            if (mbean instanceof NotificationEmitter)
                return mgr.getNotificationEmitterFor(name);
            return null;
        }

        public final static MBeanServerWrapperFactory factory =
                new MBeanServerWrapperFactory() {

            public MBeanServer wrapMBeanServer(MBeanServer wrapped) {
                return new VirtualMBeanServerTest2(wrapped);
            }
            @Override
            public String toString() {
                return VirtualMBeanServerTest2.class.getName();
            }
        };
    }


    public static void test(MBeanServerWrapperFactory factory) throws Exception {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        // names[] are NotificationEmitters
        final ObjectName[] emitters = new ObjectName[2];
        // shields[] have been shielded by wrapping them in a StandardMBean,
        // so although the resource is an MBean that implements
        // NotificationEmitter, the registered MBean (the wrapper) doesn't.
        final ObjectName[] shielded = new ObjectName[2];

        final List<ObjectName> registered = new ArrayList<ObjectName>(4);

        try {
            // register two MBeans before wrapping
            server.registerMBean(new Wombat(),
                    emitters[0] = new ObjectName("bush:type=Wombat,name=wom"));
            registered.add(emitters[0]);

            // we shield the second MBean in a StandardMBean so that it does
            // not appear as a NotificationEmitter.
            server.registerMBean(
                    new StandardMBean(new Wombat(), WombatMBean.class),
                    shielded[0] = new ObjectName("bush:type=Wombat,name=womshield"));
            registered.add(shielded[0]);

            final MBeanServer vserver = factory.wrapMBeanServer(server);

            // register two other MBeans after wrapping
            server.registerMBean(new Wombat(),
                    emitters[1] = new ObjectName("bush:type=Wombat,name=bat"));
            registered.add(emitters[1]);

            // we shield the second MBean in a StandardMBean so that it does
            // not appear as a NotificationEmitter.
            server.registerMBean(
                    new StandardMBean(new Wombat(), WombatMBean.class),
                    shielded[1] = new ObjectName("bush:type=Wombat,name=batshield"));
            registered.add(shielded[1]);

            // Call test with this config - we have two wombats who broadcast
            // notifs (emitters) and two wombats who don't (shielded).
            test(vserver, emitters, shielded);

            System.out.println("*** Test passed for: " + factory);
        } finally {
            // Clean up the platform mbean server for the next test...
            for (ObjectName n : registered) {
                try {
                    server.unregisterMBean(n);
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }
        }
    }

    /**
     * Perform the actual test.
     * @param vserver    A virtual MBeanServerSupport implementation
     * @param emitters   Names of NotificationBroadcaster MBeans
     * @param shielded   Names of non NotificationBroadcaster MBeans
     * @throws java.lang.Exception
     */
    public static void test(MBeanServer vserver, ObjectName[] emitters,
            ObjectName[] shielded) throws Exception {

        // To catch exception in NotificationListener
        final List<Exception> fail = new CopyOnWriteArrayList<Exception>();

        // A queue of received notifications
        final BlockingQueue<Notification> notifs =
                new ArrayBlockingQueue<Notification>(50);

        // A notification listener that puts the notification it receives
        // in the queue.
        final NotificationListener handler = new NotificationListener() {

            public void handleNotification(Notification notification,
                    Object handback) {
                try {
                    notifs.put(notification);
                } catch (Exception x) {
                    fail.add(x);
                }
            }
        };

        // A list of attribute names for which we might receive an
        // exception. If an exception is received when getting these
        // attributes - the test will not fail.
        final List<String> exceptions = Arrays.asList( new String[] {
           "UsageThresholdCount","UsageThreshold","UsageThresholdExceeded",
           "CollectionUsageThresholdCount","CollectionUsageThreshold",
           "CollectionUsageThresholdExceeded"
        });

        // This is just a sanity check. Get all attributes of all MBeans.
        for (ObjectName n : vserver.queryNames(null, null)) {
            final MBeanInfo m = vserver.getMBeanInfo(n);
            for (MBeanAttributeInfo mba : m.getAttributes()) {
                // System.out.println(n+":");
                Object val;
                try {
                    val = vserver.getAttribute(n, mba.getName());
                } catch (Exception x) {
                    // only accept exception for those attributes that
                    // have a valid reason to fail...
                    if (exceptions.contains(mba.getName())) val = x;
                    else throw new Exception("Failed to get " +
                            mba.getName() + " from " + n,x);
                }
                // System.out.println("\t "+mba.getName()+": "+ val);
            }
        }

        // The actual tests. Register for notifications with notif emitters
        for (ObjectName n : emitters) {
            vserver.addNotificationListener(n, handler, null, n);
        }

        // Trigger the emission of notifications, check that we received them.
        for (ObjectName n : emitters) {
            vserver.setAttribute(n,
                    new Attribute("Caption","I am a new wombat!"));
            final Notification notif = notifs.poll(4, TimeUnit.SECONDS);
            if (!notif.getSource().equals(n))
                throw new Exception("Bad source for "+ notif);
            if (fail.size() > 0)
                throw new Exception("Failed to handle notif",fail.remove(0));
        }

        // Check that we didn't get more notifs than expected
        if (notifs.size() > 0)
            throw new Exception("Extra notifications in queue: "+notifs);

        // Check that if the MBean doesn't exist, we get InstanceNotFound.
        try {
            vserver.addNotificationListener(new ObjectName("toto:toto=toto"),
                    handler, null, null);
            throw new Exception("toto:toto=toto doesn't throw INFE");
        } catch (InstanceNotFoundException x) {
            System.out.println("Received "+x+" as expected.");
        }

        // For those MBeans that shouldn't be NotificationEmitters, check that
        // we get IllegalArgumentException
        for (ObjectName n : shielded) {
            try {
                vserver.addNotificationListener(n, handler, null, n);
            } catch (RuntimeOperationsException x) {
                System.out.println("Received "+x+" as expected.");
                System.out.println("Cause is: "+x.getCause());
                if (!(x.getCause() instanceof IllegalArgumentException))
                    throw new Exception("was expecting IllegalArgumentException cause. Got "+x.getCause(),x);
            }
        }

        // Sanity check. Remove our listeners.
        for (ObjectName n : emitters) {
            vserver.removeNotificationListener(n, handler, null, n);
        }

        // That's it.
        // Sanity check: we shouldn't have received any new notif.
        if (notifs.size() > 0)
            throw new Exception("Extra notifications in queue: "+notifs);
        // The NotifListener shouldn't have logged any new exception.
        if (fail.size() > 0)
                throw new Exception("Failed to handle notif",fail.remove(0));
    }

    public static void main(String[] args) throws Exception {
        // test with a regular MBeanServer (no VirtualMBeanServerSupport)
        final MBeanServerWrapperFactory identity =
                new MBeanServerWrapperFactory() {
            public MBeanServer wrapMBeanServer(MBeanServer wrapped) {
                return wrapped;
            }
        };
        test(identity);
        // test with no EventManager
        test(VirtualMBeanServerTest.factory);
        // test with VirtualEventManager
        test(VirtualMBeanServerTest2.factory);
    }
}
