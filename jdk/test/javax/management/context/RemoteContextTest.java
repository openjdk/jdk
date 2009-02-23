/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test RemoteContextTest.java
 * @bug 5072267
 * @summary Test client contexts with namespaces.
 * @author Eamonn McManus, Daniel Fuchs
 */

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.ClientContext;
import javax.management.DynamicMBean;
import javax.management.JMX;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.loading.MLet;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXRemoteNamespace;
import javax.management.namespace.JMXNamespace;

import static java.util.Collections.singletonMap;
import javax.management.MBeanServerFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class RemoteContextTest {
    private static String failure;

    public static interface ShowContextMBean {
        public Map<String, String> getContext();
        public Map<String, String> getCreationContext();
        public Set<String> getCalledOps();
        public String getThing();
        public void setThing(String x);
        public int add(int x, int y);
    }

    public static class ShowContext
            extends NotificationBroadcasterSupport
            implements ShowContextMBean, MBeanRegistration {
        private final Map<String, String> creationContext;
        private final Set<String> calledOps = new HashSet<String>();

        public ShowContext() {
            creationContext = getContext();
        }

        public Map<String, String> getContext() {
            return ClientContext.getContext();
        }

        public Map<String, String> getCreationContext() {
            return creationContext;
        }

        public Set<String> getCalledOps() {
            return calledOps;
        }

        public String getThing() {
            return "x";
        }

        public void setThing(String x) {
        }

        public int add(int x, int y) {
            return x + y;
        }

        public ObjectName preRegister(MBeanServer server, ObjectName name) {
            assertEquals(creationContext, getContext());
            calledOps.add("preRegister");
            return name;
        }

        public void postRegister(Boolean registrationDone) {
            assertEquals(creationContext, getContext());
            calledOps.add("postRegister");
        }

        // The condition checked here is not guaranteed universally true,
        // but is true every time we unregister an instance of this MBean
        // in this test.
        public void preDeregister() throws Exception {
            assertEquals(creationContext, getContext());
        }

        public void postDeregister() {
            assertEquals(creationContext, getContext());
        }

        // Same remark as for preDeregister
        @Override
        public MBeanNotificationInfo[] getNotificationInfo() {
            calledOps.add("getNotificationInfo");
            return super.getNotificationInfo();
        }

        @Override
        public void addNotificationListener(
                NotificationListener listener, NotificationFilter filter, Object handback) {
            calledOps.add("addNotificationListener");
            super.addNotificationListener(listener, filter, handback);
        }

        @Override
        public void removeNotificationListener(
                NotificationListener listener)
        throws ListenerNotFoundException {
            calledOps.add("removeNL1");
            super.removeNotificationListener(listener);
        }

        @Override
        public void removeNotificationListener(
                NotificationListener listener, NotificationFilter filter, Object handback)
        throws ListenerNotFoundException {
            calledOps.add("removeNL3");
            super.removeNotificationListener(listener, filter, handback);
        }
    }

    private static class LogRecord {
        final String op;
        final Object[] params;
        final Map<String, String> context;
        LogRecord(String op, Object[] params, Map<String, String> context) {
            this.op = op;
            this.params = params;
            this.context = context;
        }

        @Override
        public String toString() {
            return op + Arrays.deepToString(params) + " " + context;
        }
    }

    private static class LogIH implements InvocationHandler {
        private final Object wrapped;
        Queue<LogRecord> log = new LinkedList<LogRecord>();

        LogIH(Object wrapped) {
            this.wrapped = wrapped;
        }

        public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable {
            if (method.getDeclaringClass() != Object.class) {
                LogRecord lr =
                    new LogRecord(
                        method.getName(), args, ClientContext.getContext());
                log.add(lr);
            }
            try {
                return method.invoke(wrapped, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static <T> T newSnoop(Class<T> wrappedClass, LogIH logIH) {
        return wrappedClass.cast(Proxy.newProxyInstance(
                wrappedClass.getClassLoader(),
                new Class<?>[] {wrappedClass},
                logIH));
    }

    public static void main(String[] args) throws Exception {
        final String subnamespace = "sub";
        final ObjectName locname = new ObjectName("a:b=c");
        final ObjectName name = JMXNamespaces.insertPath(subnamespace,locname);
        final MBeanServer mbs = ClientContext.newContextForwarder(
                ManagementFactory.getPlatformMBeanServer(), null);
        final MBeanServer sub = ClientContext.newContextForwarder(
                MBeanServerFactory.newMBeanServer(), null);
        final JMXServiceURL anonym = new JMXServiceURL("rmi",null,0);
        final Map<String, Object> env = Collections.emptyMap();
        final Map<String, String> emptyContext = Collections.emptyMap();
        final JMXConnectorServer srv =
                JMXConnectorServerFactory.newJMXConnectorServer(anonym,env,sub);
        sub.registerMBean(new ShowContext(), locname);

        srv.start();

        try {
        JMXRemoteNamespace subns = JMXRemoteNamespace.
            newJMXRemoteNamespace(srv.getAddress(),null);
        mbs.registerMBean(subns, JMXNamespaces.getNamespaceObjectName("sub"));
        mbs.invoke(JMXNamespaces.getNamespaceObjectName("sub"),
               "connect", null,null);
        final ShowContextMBean show =
                JMX.newMBeanProxy(mbs, name, ShowContextMBean.class);

        assertEquals(emptyContext, show.getContext());
        ClientContext.doWithContext(singletonMap("foo", "bar"), new Callable<Void>() {
            public Void call() {
                assertEquals(singletonMap("foo", "bar"), show.getContext());
                return null;
            }
        });
        assertEquals(emptyContext, show.getContext());
        String got = ClientContext.doWithContext(
                singletonMap("foo", "baz"), new Callable<String>() {
            public String call() {
                return ClientContext.getContext().get("foo");
            }
        });
        assertEquals("baz", got);

        Map<String, String> combined = ClientContext.doWithContext(
                singletonMap("foo", "baz"), new Callable<Map<String, String>>() {
            public Map<String, String> call() throws Exception {
                return ClientContext.doWithContext(
                        singletonMap("fred", "jim"),
                        new Callable<Map<String, String>>() {
                    public Map<String, String> call() {
                        return ClientContext.getContext();
                    }
                });
            }
        });
        assertEquals(singletonMap("fred", "jim"), combined);

        final String ugh = "a!?//*=:\"% ";
        ClientContext.doWithContext(singletonMap(ugh, ugh), new Callable<Void>() {
            public Void call() {
                assertEquals(Collections.singletonMap(ugh, ugh),
                        ClientContext.getContext());
                return null;
            }
        });

        // Basic withContext tests

        LogIH mbsIH = new LogIH(mbs);
        MBeanServer snoopMBS = newSnoop(MBeanServer.class, mbsIH);
        MBeanServer ughMBS = ClientContext.withContext(snoopMBS, ugh, ugh);
        // ughMBS is never referenced but we check that the withContext call
        // included a call to snoopMBS.isRegistered.
        String encodedUgh = URLEncoder.encode(ugh, "UTF-8").replace("*", "%2A");
        ObjectName expectedName = new ObjectName(
                ClientContext.NAMESPACE + ObjectName.NAMESPACE_SEPARATOR +
                encodedUgh + "=" + encodedUgh +
                ObjectName.NAMESPACE_SEPARATOR + ":" +
                JMXNamespace.TYPE_ASSIGNMENT);
        assertCalled(mbsIH, "isRegistered", new Object[] {expectedName},
                emptyContext);

        // Test withDynamicContext

        MBeanServerConnection dynamicSnoop =
                ClientContext.withDynamicContext(snoopMBS);
        assertCalled(mbsIH, "isRegistered",
                new Object[] {
                    JMXNamespaces.getNamespaceObjectName(ClientContext.NAMESPACE)
                },
                emptyContext);
        final ShowContextMBean dynamicShow =
                JMX.newMBeanProxy(dynamicSnoop, name, ShowContextMBean.class);
        assertEquals(Collections.emptyMap(), dynamicShow.getContext());
        assertCalled(mbsIH, "getAttribute", new Object[] {name, "Context"},
                emptyContext);

        Map<String, String> expectedDynamic =
                Collections.singletonMap("gladstone", "gander");
        Map<String, String> dynamic = ClientContext.doWithContext(
                expectedDynamic,
                new Callable<Map<String, String>>() {
                    public Map<String, String> call() throws Exception {
                        return dynamicShow.getContext();
                    }
                });
        assertEquals(expectedDynamic, dynamic);
        ObjectName expectedDynamicName = new ObjectName(
                ClientContext.encode(expectedDynamic) +
                ObjectName.NAMESPACE_SEPARATOR + name);
        assertCalled(mbsIH, "getAttribute",
                new Object[] {expectedDynamicName, "Context"}, dynamic);

        MBeanServer cmbs = ClientContext.withContext(
                mbs, "mickey", "mouse");
        ShowContextMBean cshow =
                JMX.newMBeanProxy(cmbs, name, ShowContextMBean.class);
        assertEquals(Collections.singletonMap("mickey", "mouse"), cshow.getContext());

        MBeanServer ccmbs = ClientContext.withContext(
                cmbs, "donald", "duck");
        ShowContextMBean ccshow =
                JMX.newMBeanProxy(ccmbs, name, ShowContextMBean.class);
        Map<String, String> disney = new HashMap<String, String>();
        disney.put("mickey", "mouse");
        disney.put("donald", "duck");
        assertEquals(disney, ccshow.getContext());

        // Test that all MBS ops produce reasonable results

        ObjectName logger = new ObjectName("a:type=Logger");
        DynamicMBean showMBean =
                new StandardMBean(new ShowContext(), ShowContextMBean.class);
        LogIH mbeanLogIH = new LogIH(showMBean);
        DynamicMBean logMBean = newSnoop(DynamicMBean.class, mbeanLogIH);
        ObjectInstance loggerOI = ccmbs.registerMBean(logMBean, logger);
        assertEquals(logger, loggerOI.getObjectName());

        // We get a getMBeanInfo call to determine the className in the
        // ObjectInstance to return from registerMBean.
        assertCalled(mbeanLogIH, "getMBeanInfo", disney);

        ccmbs.getAttribute(logger, "Thing");
        assertCalled(mbeanLogIH, "getAttribute", disney);

        ccmbs.getAttributes(logger, new String[] {"Thing", "Context"});
        assertCalled(mbeanLogIH, "getAttributes", disney);

        ccmbs.setAttribute(logger, new Attribute("Thing", "bar"));
        assertCalled(mbeanLogIH, "setAttribute", disney);

        ccmbs.setAttributes(logger, new AttributeList(
                Arrays.asList(new Attribute("Thing", "baz"))));
        assertCalled(mbeanLogIH, "setAttributes", disney);

        ccmbs.getMBeanInfo(logger);
        assertCalled(mbeanLogIH, "getMBeanInfo", disney);

        Set<ObjectName> names = ccmbs.queryNames(null, null);
        Set<ObjectName> expectedNames = new HashSet<ObjectName>(
                Collections.singleton(MBeanServerDelegate.DELEGATE_NAME));
        expectedNames.removeAll(names);
        assertEquals(0, expectedNames.size());

        Set<ObjectName> nsNames =
                ccmbs.queryNames(new ObjectName("**?*?//:*"), null);
        Set<ObjectName> expectedNsNames = new HashSet<ObjectName>(
                Arrays.asList(
                new ObjectName(ClientContext.NAMESPACE +
                ObjectName.NAMESPACE_SEPARATOR + ":" +
                JMXNamespace.TYPE_ASSIGNMENT)));
        expectedNsNames.removeAll(nsNames);
        assertEquals(0, expectedNsNames.size());

        Set<ObjectInstance> insts = ccmbs.queryMBeans(
                MBeanServerDelegate.DELEGATE_NAME, null);
        assertEquals(1, insts.size());
        assertEquals(MBeanServerDelegate.DELEGATE_NAME,
                insts.iterator().next().getObjectName());

        ObjectName createdName = new ObjectName("a:type=Created");
        ObjectInstance createdOI =
                ccmbs.createMBean(ShowContext.class.getName(), createdName);
        assertEquals(ShowContext.class.getName(), createdOI.getClassName());
        assertEquals(createdName, createdOI.getObjectName());
        assertEquals(disney, ccmbs.getAttribute(createdName, "CreationContext"));

        NotificationListener nothingListener = new NotificationListener() {
            public void handleNotification(Notification n, Object h) {}
        };
        ccmbs.addNotificationListener(createdName, nothingListener, null, null);
        ccmbs.removeNotificationListener(createdName, nothingListener, null, null);
        ccmbs.addNotificationListener(createdName, nothingListener, null, null);
        ccmbs.removeNotificationListener(createdName, nothingListener);
        Set<String> expectedOps = new HashSet<String>(Arrays.asList(
                "preRegister", "postRegister", "addNotificationListener",
                "removeNL1", "removeNL3", "getNotificationInfo"));
        assertEquals(expectedOps, ccmbs.getAttribute(createdName, "CalledOps"));

        assertEquals(ShowContext.class.getClassLoader(),
                ccmbs.getClassLoaderFor(createdName));

        assertEquals(true, ccmbs.isRegistered(createdName));
        assertEquals(true, ccmbs.isInstanceOf(createdName,
                ShowContext.class.getName()));
        assertEquals(false, ccmbs.isInstanceOf(createdName,
                DynamicMBean.class.getName()));
        ccmbs.unregisterMBean(createdName);
        assertEquals(false, ccmbs.isRegistered(createdName));

        MLet mlet = new MLet();
        ObjectName defaultMLetName = new ObjectName("DefaultDomain:type=MLet");

        ccmbs.registerMBean(mlet, defaultMLetName);

        assertEquals(mlet, ccmbs.getClassLoader(defaultMLetName));

        assertEquals(0, mbeanLogIH.log.size());

        // Test that contexts still work when we can't combine two encoded contexts.
        // Here, we wrap cmbs (mickey=mouse) so that ccmbs2 (donald=duck) cannot
        // see that it already contains a context and therefore cannot combine
        // into mickey=mouse;donald=duck.  We don't actually use the snoop
        // capabilities of the returned object -- we just want an opaque
        // MBeanServer wrapper
        MBeanServer cmbs2 = newSnoop(MBeanServer.class, new LogIH(cmbs));
        MBeanServer ccmbs2 = ClientContext.withContext(cmbs2, "donald", "duck");
        assertEquals(disney, ccmbs2.getAttribute(name, "Context"));

        // ADD NEW TESTS HERE ^^^

        if (failure != null)
            throw new Exception(failure);
        } finally {
            srv.stop();
        }
    }

    private static void assertEquals(Object x, Object y) {
        if (!equal(x, y))
            failed("expected " + string(x) + "; got " + string(y));
    }

    private static boolean equal(Object x, Object y) {
        if (x == y)
            return true;
        if (x == null || y == null)
            return false;
        if (x.getClass().isArray())
            return Arrays.deepEquals(new Object[] {x}, new Object[] {y});
        return x.equals(y);
    }

    private static String string(Object x) {
        String s = Arrays.deepToString(new Object[] {x});
        return s.substring(1, s.length() - 1);
    }

    private static void assertCalled(
            LogIH logIH, String op, Map<String, String> expectedContext) {
        assertCalled(logIH, op, null, expectedContext);
    }

    private static void assertCalled(
            LogIH logIH, String op, Object[] params,
            Map<String, String> expectedContext) {
        LogRecord lr = logIH.log.remove();
        assertEquals(op, lr.op);
        if (params != null)
            assertEquals(params, lr.params);
        assertEquals(expectedContext, lr.context);
    }

    private static void failed(String why) {
        failure = why;
        new Throwable("FAILED: " + why).printStackTrace(System.out);
    }
}
