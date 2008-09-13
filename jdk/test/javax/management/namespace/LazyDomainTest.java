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
 * @test LazyDomainTest.java
 * @bug 5072476
 * @summary Basic test for Lazy Domains.
 * @author Daniel Fuchs
 * @run clean LazyDomainTest Wombat WombatMBean
 * @run build LazyDomainTest Wombat WombatMBean
 * @run main LazyDomainTest
 */


import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Set;
import javax.management.JMX;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerBuilder;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.namespace.JMXDomain;
import javax.management.remote.MBeanServerForwarder;

/**
 * Test simple creation/registration of namespace.
 *
 */
public class LazyDomainTest {
    private static Map<String,Object> emptyEnvMap() {
        return Collections.emptyMap();
    }


    public static interface MBeanServerLoader {
        public MBeanServer loadMBeanServer();
    }


    public static class MBeanServerProxy implements InvocationHandler {

        private final static Map<Method,Method> localMap;
        static {
            localMap = new HashMap<Method, Method>();
            for (Method m : MBeanServerForwarder.class.getDeclaredMethods()) {
                try {
                    final Method loc = MBeanServerProxy.class.
                            getMethod(m.getName(), m.getParameterTypes());
                    localMap.put(m, loc);
                } catch (Exception x) {
                    // not defined...
                }
            }
            try {
                localMap.put(MBeanServer.class.
                        getMethod("getMBeanCount", (Class[]) null),
                        MBeanServerProxy.class.
                        getMethod("getMBeanCount", (Class[]) null));
            } catch (NoSuchMethodException x) {
                // OK.
            }
        }

        private final MBeanServerLoader loader;
        private MBeanServer server;
        private final Set<LazyDomain> domains;

        public MBeanServerProxy(MBeanServerLoader loader) {
            if (loader == null)
                throw new IllegalArgumentException("null loader");
            this.loader = loader;
            this.server = null;
            domains = new HashSet<LazyDomain>();
        }


        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if (method.getDeclaringClass().equals(Object.class)) {
                return invokeMethod(this,method,args);
            }
            final Method local = localMap.get(method);
            if (local != null) {
                return invokeMethod(this,local,args);
            }
            if (method.getDeclaringClass().equals(MBeanServer.class)) {
                return invokeMethod(getMBeanServer(),method,args);
            }
            throw new NoSuchMethodException(method.getName());
        }

        private Object invokeMethod(Object on, Method method, Object[] args)
            throws Throwable {
            try {
                return method.invoke(on, args);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }

        public synchronized MBeanServer getMBeanServer() {
            if (server == null) setMBeanServer(loader.loadMBeanServer());
            return server;
        }

        public synchronized void setMBeanServer(MBeanServer mbs) {
            this.server = mbs;
            if (mbs != null) {
                for (LazyDomain dom : domains) dom.loaded();
                domains.clear();
            }
        }

        public synchronized boolean isLoaded() {
            return server != null;
        }

        public synchronized void add(LazyDomain dom) {
            if (isLoaded()) dom.loaded();
            else domains.add(dom);
        }

        public synchronized boolean remove(LazyDomain dom) {
            return domains.remove(dom);
        }

        public Integer getMBeanCount() {
            if (isLoaded()) return server.getMBeanCount();
            else return Integer.valueOf(0);
        }
    }

    public static class LazyDomain extends JMXDomain {
        public static MBeanServer makeProxyFor(MBeanServerProxy proxy) {
            return (MBeanServer)
                    Proxy.newProxyInstance(LazyDomain.class.getClassLoader(),
                    new Class[] {MBeanServer.class, MBeanServerForwarder.class},
                    proxy);
        }

        private final MBeanServerProxy proxy;
        private volatile NotificationListener listener;
        private volatile NotificationFilter   filter;

        public LazyDomain(MBeanServerProxy proxy) {
            super(makeProxyFor(proxy));
            this.proxy = proxy;
        }

        @Override
        public Integer getMBeanCount() {
            if (proxy.isLoaded())
                return super.getMBeanCount();
            return 0;
        }


        @Override
        public synchronized void addMBeanServerNotificationListener(
                NotificationListener listener,
                NotificationFilter filter) {
            if (proxy.isLoaded()) {
                super.addMBeanServerNotificationListener(listener, filter);
            } else {
                this.listener = listener;
                this.filter   = filter;
                proxy.add(this);
            }
        }

        @Override
        public synchronized void removeMBeanServerNotificationListener(
                NotificationListener listener)
                throws ListenerNotFoundException {
            if (this.listener != listener)
                throw new ListenerNotFoundException();
            this.listener = null;
            this.filter   = null;
            if (proxy.isLoaded())
                super.removeMBeanServerNotificationListener(listener);
            proxy.remove(this);
        }

        public synchronized void loaded() {
            if (listener != null)
                addMBeanServerNotificationListener(listener, filter);
        }

    }

    /**
     * This is a use case for e.g GlassFish: the LazyStarterDomain MBean
     * is a place holder that will unregister itself and autoload a set
     * of MBeans in place of its own domain when that domain is
     * accessed.
     * This is an abstract class, where the only abstract method
     * is loadMBeans(MBeanServer).
     * Subclasses should implement that method to register whatever MBeans
     * in the domain previously held by that LazyStarterDomain object.
     * In other words: the LazyStarterDomain MBean is 'replaced' by the
     * MBeans loaded by loadMBeans();
     */
    public static abstract class LazyStarterDomain extends LazyDomain {

        /**
         * This is a loader that will unregister the JMXDomain that
         * created it, and register a bunch of MBeans in its place
         * by calling LazyStarterDomain.loadMBeans
         *
         * That one gave me "la migraine".
         */
        private static class HalfGrainLoader implements MBeanServerLoader {
            private volatile LazyStarterDomain domain;
            public MBeanServer loadMBeanServer() {
                if (domain == null)
                    throw new IllegalStateException(
                            "JMXDomain MBean not registered!");
                final MBeanServer server     = domain.getMBeanServer();
                final ObjectName  domainName = domain.getObjectName();
                try {
                    server.unregisterMBean(domainName);
                } catch (Exception x) {
                    throw new IllegalStateException("Can't unregister " +
                            "JMXDomain: "+x,x);
                }
                domain.loadMBeans(server,domainName.getDomain());
                return server;
            }
            public void setDomain(LazyStarterDomain domain) {
                this.domain = domain;
            }
        }

        /**
         * This is an MBeanServerProxy which create a loader for the
         * LazyStarterDomain MBean.
         */
        private static class DomainStarter extends MBeanServerProxy {

            public DomainStarter() {
                this(new HalfGrainLoader());
            }

            private final HalfGrainLoader loader;
            private DomainStarter(HalfGrainLoader loader) {
                super(loader);
                this.loader = loader;
            }

            public void setDomain(LazyStarterDomain domain) {
                loader.setDomain(domain);
            }
        }

        /**
         * A new LazyStarterDomain. When the domain monitored by this
         * MBean is accessed, this MBean will unregister itself and call
         * the abstract loadMBeans(MBeanServer) method.
         * Subclasses need only to implement loadMBeans().
         */
        public LazyStarterDomain() {
            this(new DomainStarter());
        }

        private LazyStarterDomain(DomainStarter starter) {
            super(starter);
            starter.setDomain(this);
        }

        // Contrarily to its LazyDomain superclass, this LazyDomain
        // doesn't wrapp another MBeanServer: it simply registers a bunch
        // of MBeans in its own MBeanServer.
        // Thus, there's no notifications to forward.
        //
        @Override
        public void addMBeanServerNotificationListener(
                NotificationListener listener, NotificationFilter filter) {
            // nothing to do.
        }

        // Contrarily to its LazyDomain superclass, this LazyDomain
        // doesn't wrapp another MBeanServer: it simply registers a bunch
        // of MBeans in its own MBeanServer.
        // Thus, there's no notifications to forward.
        //
        @Override
        public void removeMBeanServerNotificationListener(
                NotificationListener listener) throws ListenerNotFoundException {
            // nothing to do
        }

        // If this domain is registered, it contains no MBean.
        // If it is not registered, then it no longer contain any MBean.
        // The MBeanCount is thus always 0.
        @Override
        public Integer getMBeanCount() {
            return 0;
        }

        /**
         * Called when the domain is first accessed.
         * {@code server} is the server in which this MBean was registered.
         * A subclass must override this method in order to register
         * the MBeans that should be contained in domain.
         *
         * @param server the server in which to load the MBeans.
         * @param domain the domain in which the MBeans should be registered.
         */
        protected abstract void loadMBeans(MBeanServer server, String domain);


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

    // ZZZ need to add a test case with several LazyDomains, and
    // need to test that nothing is loaded until the lazy domains
    // are accessed...
    //

    private static void registerWombats(MBeanServer server, String domain,
            int count) {
        try {
            for (int i=0;i<count;i++) {
                final ObjectName name =
                        new ObjectName(domain+":type=Wombat,name=wombat#"+i);
                server.createMBean("Wombat", name);
            }
        } catch (RuntimeException x) {
            throw x;
        } catch(Exception x) {
            throw new RuntimeException(x.toString(),x);
        }
    }

    public static void checkSize(String test, MBeanServer server, int size) {
        System.out.println("We have now "+server.getMBeanCount()+
                " MBeans in "+Arrays.toString(server.getDomains()));
        if (server.getMBeanCount() != size)
            fail(test+"Expected "+size+
                    " MBeans, found " + server.getMBeanCount());
    }

    private static MBeanServer newMBeanServer() {
        return MBeanServerFactory.newMBeanServer();
    }

    public static void lazyTest() throws Exception {
        final String test = "lazyTest: ";
        System.out.println("" +
                "\nThis test checks that it is possible to perform lazy loading" +
                "\nof MBeans in a given domain by using a JMXDomain subclass" +
                "\nfor that domain.");

        System.out.println(test + " START");

        // The "global" MBeanServer...
        final MBeanServer server = newMBeanServer();

        // An MBeanServer proxy which makes it possible to `lazy load'
        // the platform MBeanServer domains inside the global MBeanServer.
        //
        final MBeanServerProxy  platform =
                new MBeanServerProxy(new MBeanServerLoader() {

            public MBeanServer loadMBeanServer() {
                return ManagementFactory.getPlatformMBeanServer();
            }
        });


        // The list of domain from the platform MBeanServer that will be
        // lazily loaded in the global MBeanServer
        //
        final String[] platformDomains = {
              "java.lang", "com.sun.management",
              "java.util.logging", "java.nio"
        };

        // We create a second MBeanServer, in which we will store some
        // custom MBeans. We will use this server to perform lazy loading
        // of two domains: custom.awomb and custom.bwomb.
        //
        // We use an MBeanServerBuilder here so that the MBeans registered
        // in our custom domain see all the MBeans in the global MBeanServer.
        // We do this by saying that the 'outer' MBeanServer is our global
        // servers. This means that the MBeans registered in the global
        // MBeanServer will see the MBeans from custom.awomb and custom.bwomb,
        // and the MBeans from custom.awomb and custom.bwomb will also see
        // the MBeans from the global MBeanServer, including those from
        // the platform domains.
        //
        final MBeanServerBuilder builder = new MBeanServerBuilder();
        final MBeanServerDelegate delegate = builder.newMBeanServerDelegate();
        final MBeanServer custom = builder.newMBeanServer("custom",
                server, delegate);

        // Number of MBean that we will put in each of the custom domain.
        //
        final int customCount = 10;

        // We use one MBeanServer proxy for each of the custom domains.
        // This makes it possible to load custom.awomb independently of
        // custom.bwomb.
        //
        // Here, the logic of the loader is to register MBeans in the loaded
        // domain as soon as the domain is loaded.
        //
        final MBeanServerProxy customa =
                new MBeanServerProxy(new MBeanServerLoader() {
            // A loader to register awomb MBeans in the custom MBeanServer.
            public MBeanServer loadMBeanServer() {
                registerWombats(custom, "custom.awomb", customCount);
                return custom;
            }
        });
        final MBeanServerProxy customb =
                new MBeanServerProxy(new MBeanServerLoader() {
            // A loader to register bwomb MBeans in the custom MBeanServer.
            public MBeanServer loadMBeanServer() {
                registerWombats(custom, "custom.bwomb", customCount);
                return custom;
            }
        });

        // A notification queue.
        final BlockingQueue<Notification> queue =
                new ArrayBlockingQueue<Notification>(100);

        // A listener that puts notifs in the queue.
        final NotificationListener l = new NotificationListener() {

            public void handleNotification(Notification notification,
                    Object handback) {
                try {
                    if (!queue.offer(notification, 5, TimeUnit.SECONDS)) {
                        throw new RuntimeException("timeout exceeded");
                    }
                } catch (Exception x) {
                    fail(test + "failed to handle notif", x);
                }
            }
        };

        // Create a LazyDomain for each of the platform domain.
        // All platform domain share the same MBeanServer proxy, which means
        // that loading one domain will also load all the others.
        //
        Map<String,LazyDomain> domainsMap = new HashMap<String,LazyDomain>();
        for (String dom : platformDomains) {
            domainsMap.put(dom, new LazyDomain(platform));
        }
        domainsMap.put("custom.awomb", new LazyDomain(customa));
        domainsMap.put("custom.bwomb", new LazyDomain(customb));

        for (Map.Entry<String,LazyDomain> e : domainsMap.entrySet()) {
            server.registerMBean(e.getValue(),
                    JMXDomain.getDomainObjectName(e.getKey()));
        }

        // check that lazy MBeans are not there...
        checkSize(test,server,domainsMap.size()+1);

        System.out.println(test+" registering listener with delegate.");
        server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, l,
                null, null);

        // check that lazy MBeans are not there...
        checkSize(test,server,domainsMap.size()+1);

        // force loading of custom.awomb.
        final ObjectName awombat = new ObjectName(
                "custom.awomb:type=Wombat,name=wombat#"+customCount/2);
        if (!server.isRegistered(awombat))
            fail(test+"Expected "+awombat+" to be reggistered!");

        final int oldCount = domainsMap.size()+1+customCount;
        checkSize(test,server,oldCount);

        if (queue.peek() != null)
            fail(test+"Received unexpected notifications: "+queue);


        System.out.println(test+"creating a proxy for ClassLoadingMXBean.");
        final ClassLoadingMXBean cl =
                JMX.newMXBeanProxy(server,
                new ObjectName(ManagementFactory.CLASS_LOADING_MXBEAN_NAME),
                ClassLoadingMXBean.class);

        checkSize(test,server,oldCount);

        System.out.println(test+"Loaded classes: "+cl.getLoadedClassCount());

        final int newCount = server.getMBeanCount();
        if (newCount < oldCount+6)
            fail(test+"Expected at least "+(oldCount+6)+
                    " MBeans. Found "+newCount);

        final ObjectName jwombat = new ObjectName("java.lang:type=Wombat");
        server.createMBean("Wombat", jwombat);
        System.out.println(test+"Created "+jwombat);
        checkSize(test,server,newCount+1);

        popADD(queue, jwombat, test);
        if (queue.peek() != null)
            fail(test+"Received unexpected notifications: "+queue);


        int platcount = 0;
        for (String dom : platformDomains) {
            final Set<ObjectName> found =
                    server.queryNames(new ObjectName(dom+":*"),null);
            final int jcount = found.size();
            System.out.println(test+"Found "+jcount+" MBeans in "+dom+
                    ": "+found);
            checkSize(test,server,newCount+1);
            platcount += (jcount-1);
        }
        checkSize(test,server,oldCount+platcount);

        final ObjectName owombat = new ObjectName("custom:type=Wombat");
        server.createMBean("Wombat", owombat);
        System.out.println(test+"Created "+owombat);
        checkSize(test,server,newCount+2);
        popADD(queue, owombat, test);
        if (queue.peek() != null)
            fail(test+"Received unexpected notifications: "+queue);

        final Set<ObjectName> jwombatView = (Set<ObjectName>)
                server.invoke(jwombat, "listMatching", new Object[] {null},
                new String[] {ObjectName.class.getName()});
        System.out.println(test+jwombat+" sees: "+jwombatView);
        checkSize(test, server, newCount+2);
        if (jwombatView.size() != (platcount+1))
            fail(test+jwombat+" sees "+jwombatView.size()+" MBeans - should" +
                    " have seen "+(platcount+1));

        final Set<ObjectName> platformMBeans =
                ManagementFactory.getPlatformMBeanServer().
                queryNames(null, null);
        if (!platformMBeans.equals(jwombatView))
            fail(test+jwombat+" should have seen "+platformMBeans);

        // check that awombat triggers loading of bwombats
        final Set<ObjectName> awombatView = (Set<ObjectName>)
                server.invoke(awombat, "listMatching", new Object[] {null},
                new String[] {ObjectName.class.getName()});
        System.out.println(test+awombat+" sees: "+awombatView);
        final int totalCount = newCount+2+customCount;
        checkSize(test, server, totalCount);
        if (awombatView.size() != totalCount)
            fail(test+jwombat+" sees "+jwombatView.size()+" MBeans - should" +
                    " have seen "+totalCount);

        final Set<ObjectName> allMBeans = server.
                queryNames(null, null);
        if (!allMBeans.equals(awombatView))
            fail(test+awombat+" should have seen "+allMBeans);

        System.out.println(test + " PASSED");

    }


    public static void lazyStarterTest() throws Exception {
        final String test = "lazyStarterTest: ";
        System.out.println("" +
                "\nThis test checks that it is possible to perform lazy loading" +
                "\nof MBeans in a given domain by using a transient JMXDomain" +
                "\nsubclass for that domain. ");

        System.out.println(test + " START");

        // The "global" MBeanServer...
        final MBeanServer platform =
                ManagementFactory.getPlatformMBeanServer();

        // A notification queue.
        final BlockingQueue<Notification> queue =
                new ArrayBlockingQueue<Notification>(100);

        // A listener that puts notifs in the queue.
        final NotificationListener l = new NotificationListener() {

            public void handleNotification(Notification notification,
                    Object handback) {
                try {
                    if (!queue.offer(notification, 5, TimeUnit.SECONDS)) {
                        throw new RuntimeException("timeout exceeded");
                    }
                } catch (Exception x) {
                    fail(test + "failed to handle notif", x);
                }
            }
        };

        System.out.println(test+" registering listener with delegate.");
        platform.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, l,
                null, null);

        final String ld1 = "lazy1";
        final String ld2 = "lazy2";
        final int wCount = 5;
        final LazyStarterDomain lazy1 = new LazyStarterDomain() {
            @Override
            protected void loadMBeans(MBeanServer server, String domain) {
                registerWombats(server, ld1, wCount);
            }
        };
        final LazyStarterDomain lazy2 = new LazyStarterDomain() {
            @Override
            protected void loadMBeans(MBeanServer server, String domain) {
                registerWombats(server, ld2, wCount);
            }
        };
        final ObjectName lo1 = JMXDomain.getDomainObjectName(ld1);
        final ObjectName lo2 = JMXDomain.getDomainObjectName(ld2);

        final int initial = platform.getMBeanCount();

        platform.registerMBean(lazy1, lo1);
        System.out.println(test+"registered "+lo1);
        checkSize(test, platform, initial+1);
        popADD(queue, lo1, test);

        platform.registerMBean(lazy2, lo2);
        System.out.println(test+"registered "+lo2);
        checkSize(test, platform, initial+2);
        popADD(queue, lo2, test);


        final ObjectName awombat = new ObjectName(
                ld1+":type=Wombat,name=wombat#"+wCount/2);
        if (!platform.isRegistered(awombat))
            fail(test+"Expected "+awombat+" to be reggistered!");
        checkSize(test,platform,initial+wCount+1);
        popREM(queue, lo1, test);
        final ObjectName pat1 =
                new ObjectName(ld1+":type=Wombat,name=wombat#*");
        for (int i=0;i<wCount;i++) {
            popADD(queue,pat1,test);
        }
        System.out.println(test+"Found: "+
                platform.queryNames(pat1,null));
        checkSize(test,platform,initial+wCount+1);

        final Set<ObjectName> all = platform.queryNames(null, null);
        popREM(queue, lo2, test);
        System.out.println(test+"Now found: "+all);
        checkSize(test,platform,initial+wCount+wCount);
        final ObjectName pat2 =
                new ObjectName(ld2+":type=Wombat,name=wombat#*");
        for (int i=0;i<wCount;i++) {
            popADD(queue,pat2,test);
        }

        System.out.println(test+"check concurrent modification " +
                "of the DomainDispatcher.");
        System.out.println(test+"This will fail if the DomainDispatcher" +
                " doesn't allow concurrent modifications.");
        final HashMap<String,LazyStarterDomain> testConcurrent =
                new HashMap<String,LazyStarterDomain>();
        for (int i=0;i<(100/wCount);i++) {
            final String ld = "concurrent.lazy"+i;
            final LazyStarterDomain lazy = new LazyStarterDomain() {
                @Override
                protected void loadMBeans(MBeanServer server, String domain) {
                    registerWombats(server, ld, wCount-1);
                }
            };
            testConcurrent.put(ld, lazy);
            final ObjectName lo = JMXDomain.getDomainObjectName(ld);
            platform.registerMBean(lazy, lo);
            popADD(queue, lo, test);
        }

        System.out.println(test+"Big autoload: "+
                platform.queryNames(null,null));
        System.out.println(test+"Big after load: "+
                platform.queryNames(null,null));
        if (!platform.queryNames(JMXDomain.getDomainObjectName("*"), null).
                isEmpty()) {
            fail(test+" some domains are still here: "+
                    platform.queryNames(
                    JMXDomain.getDomainObjectName("*"), null));
        }
        queue.clear();
        System.out.println(test+"PASSED: The DomainDispatcher appears to be " +
                "resilient to concurrent modifications.");
    }

    public static void main(String... args) throws Exception {

        lazyTest();
        lazyStarterTest();

        if (lastException != null)
            throw lastException;
    }
}
