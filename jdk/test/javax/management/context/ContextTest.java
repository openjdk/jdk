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
 * @test ContextTest
 * @bug 5072267
 * @summary Test client contexts.
 * @author Eamonn McManus
 * TODO: Try registering with a null name replaced by preRegister (for example
 * from the MLet class) and see if it now works.
 */

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
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
import javax.management.MBeanServerDelegate;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.loading.MLet;
import javax.management.namespace.JMXNamespace;

import javax.management.remote.MBeanServerForwarder;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

public class ContextTest {
    private static String failure;
    private static final Map<String, String> emptyContext = emptyMap();

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
            assertEquals("preRegister context", creationContext, getContext());
            calledOps.add("preRegister");
            return name;
        }

        public void postRegister(Boolean registrationDone) {
            assertEquals("postRegister context", creationContext, getContext());
            calledOps.add("postRegister");
        }

        // The condition checked here is not guaranteed universally true,
        // but is true every time we unregister an instance of this MBean
        // in this test.
        public void preDeregister() throws Exception {
            assertEquals("preDeregister context", creationContext, getContext());
        }

        public void postDeregister() {
            assertEquals("postDeregister context", creationContext, getContext());
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

    /*
     * InvocationHandler that forwards all methods to a contained object
     * but also records each forwarded method.  This allows us to check
     * that the appropriate methods were called with the appropriate
     * parameters.  It's similar to what's typically available in
     * Mock Object frameworks.
     */
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
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        System.out.println(mbs.queryNames(null, null));
        ObjectName name = new ObjectName("a:b=c");
        mbs.registerMBean(new ShowContext(), name);
        final ShowContextMBean show =
                JMX.newMBeanProxy(mbs, name, ShowContextMBean.class);

        // Test local setting and getting within the MBeanServer

        assertEquals("initial context", emptyContext, show.getContext());
        ClientContext.doWithContext(singletonMap("foo", "bar"), new Callable<Void>() {
            public Void call() {
                assertEquals("context in doWithContext",
                        singletonMap("foo", "bar"), show.getContext());
                return null;
            }
        });
        assertEquals("initial context after doWithContext",
                emptyContext, show.getContext());
        String got = ClientContext.doWithContext(
                singletonMap("foo", "baz"), new Callable<String>() {
            public String call() {
                return ClientContext.getContext().get("foo");
            }
        });
        assertEquals("value extracted from context", "baz", got);

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
        assertEquals("nested doWithContext context",
                singletonMap("fred", "jim"), combined);

        final String ugh = "a!\u00c9//*=:\"% ";
        ClientContext.doWithContext(singletonMap(ugh, ugh), new Callable<Void>() {
            public Void call() {
                assertEquals("context with tricky encoding",
                        singletonMap(ugh, ugh), show.getContext());
                return null;
            }
        });
        Map<String, String> ughMap = new TreeMap<String, String>();
        ughMap.put(ugh, ugh);
        ughMap.put("fred", "jim");
        // Since this is a TreeMap and "fred" is greater than ugh (which begins
        // with "a"), we will see the encoding of ugh first in the output string.
        String encoded = ClientContext.encode(ughMap);
        String expectedUghCoding = "a%21%C3%89%2F%2F%2A%3D%3A%22%25+";
        String expectedUghMapCoding =
                ClientContext.NAMESPACE + "//" + expectedUghCoding + "=" +
                expectedUghCoding + ";fred=jim";
        assertEquals("hairy context encoded as string",
                expectedUghMapCoding, encoded);

        // Wrap the MBeanServer with a context MBSF so we can test withContext.
        // Also check the simulated namespace directly.

        LogIH mbsIH = new LogIH(mbs);
        MBeanServer snoopMBS = newSnoop(MBeanServer.class, mbsIH);
        MBeanServerForwarder ctxMBS =
                ClientContext.newContextForwarder(snoopMBS, null);

        // The MBSF returned by ClientContext is actually a compound of two
        // forwarders, but that is supposed to be hidden by changing the
        // behaviour of get/setMBeanServer.  Check that it is indeed so.
        assertEquals("next MBS of context forwarder",
                snoopMBS, ctxMBS.getMBeanServer());
        // If the above assertion fails you may get a confusing message
        // because the toString() of the two objects is likely to be the same
        // so it will look as if they should be equal.
        ctxMBS.setMBeanServer(null);
        assertEquals("next MBS of context forwarder after setting it null",
                null, ctxMBS.getMBeanServer());
        ctxMBS.setMBeanServer(snoopMBS);

        // The MBSF should look the same as the original MBeanServer except
        // that it has the JMXNamespace for the simulated namespace.

        Set<ObjectName> origNames = mbs.queryNames(null, null);
        Set<ObjectName> mbsfNames = ctxMBS.queryNames(null, null);
        assertEquals("number of MBeans returned by queryNames within forwarder",
                origNames.size() + 1, mbsfNames.size());
        assertEquals("MBeanCount within forwarder",
                mbsfNames.size(), ctxMBS.getMBeanCount());
        assertCalled(mbsIH, "queryNames", emptyContext);
        assertCalled(mbsIH, "getMBeanCount", emptyContext);

        ObjectName ctxNamespaceName = new ObjectName(
                ClientContext.NAMESPACE + "//:" + JMXNamespace.TYPE_ASSIGNMENT);
        origNames.add(ctxNamespaceName);
        assertEquals("MBeans within forwarder", origNames, mbsfNames);
        Set<String> domains = new HashSet<String>(Arrays.asList(ctxMBS.getDomains()));
        assertEquals("domains include context namespace MBean",
                true, domains.contains(ClientContext.NAMESPACE + "//"));
        assertCalled(mbsIH, "getDomains", emptyContext);

        // Now test ClientContext.withContext.

        MBeanServer ughMBS = ClientContext.withContext(ctxMBS, ugh, ugh);

        ShowContextMBean ughshow =
                JMX.newMBeanProxy(ughMBS, name, ShowContextMBean.class);
        Map<String, String> ughCtx = ughshow.getContext();
        Map<String, String> ughExpect = singletonMap(ugh, ugh);
        assertEquals("context seen by MBean accessed within namespace",
                ughExpect, ughCtx);
        assertCalled(mbsIH, "getAttribute", ughExpect, name, "Context");

        MBeanServer cmbs = ClientContext.withContext(
                ctxMBS, "mickey", "mouse");
        ShowContextMBean cshow =
                JMX.newMBeanProxy(cmbs, name, ShowContextMBean.class);
        assertEquals("context seen by MBean accessed within namespace",
                singletonMap("mickey", "mouse"), cshow.getContext());

        MBeanServer ccmbs = ClientContext.withContext(
                cmbs, "donald", "duck");
        ShowContextMBean ccshow =
                JMX.newMBeanProxy(ccmbs, name, ShowContextMBean.class);
        Map<String, String> disney = new HashMap<String, String>();
        disney.put("mickey", "mouse");
        disney.put("donald", "duck");
        assertEquals("context seen by MBean in nested namespace",
                disney, ccshow.getContext());

        // Test that all MBS ops produce reasonable results

        ObjectName logger = new ObjectName("a:type=Logger");
        DynamicMBean showMBean =
                new StandardMBean(new ShowContext(), ShowContextMBean.class);
        LogIH mbeanLogIH = new LogIH(showMBean);
        DynamicMBean logMBean = newSnoop(DynamicMBean.class, mbeanLogIH);
        ObjectInstance loggerOI = ccmbs.registerMBean(logMBean, logger);
        assertEquals("ObjectName returned by createMBean",
                logger, loggerOI.getObjectName());

        // We get an getMBeanInfo call to determine the className in the
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
        assertEquals("context namespace query includes expected names",
                true, names.containsAll(expectedNames));

        Set<ObjectName> nsNames = ccmbs.queryNames(new ObjectName("*//:*"), null);
        Set<ObjectName> expectedNsNames = new HashSet<ObjectName>(
                Arrays.asList(
                new ObjectName(ClientContext.NAMESPACE +
                ObjectName.NAMESPACE_SEPARATOR + ":" +
                JMXNamespace.TYPE_ASSIGNMENT)));
        assertEquals("context namespace query includes namespace MBean",
                true, nsNames.containsAll(expectedNsNames));



        Set<ObjectInstance> insts = ccmbs.queryMBeans(
                MBeanServerDelegate.DELEGATE_NAME, null);
        assertEquals("size of set from MBeanServerDelegate query", 1, insts.size());
        assertEquals("ObjectName from MBeanServerDelegate query",
                MBeanServerDelegate.DELEGATE_NAME,
                insts.iterator().next().getObjectName());

        ObjectName createdName = new ObjectName("a:type=Created");
        ObjectInstance createdOI =
                ccmbs.createMBean(ShowContext.class.getName(), createdName);
        assertEquals("class name from createMBean",
                ShowContext.class.getName(), createdOI.getClassName());
        assertEquals("ObjectName from createMBean",
                createdName, createdOI.getObjectName());
        assertEquals("context within createMBean",
                disney, ccmbs.getAttribute(createdName, "CreationContext"));

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
        assertEquals("operations called on MBean",
                expectedOps, ccmbs.getAttribute(createdName, "CalledOps"));

        assertEquals("ClassLoader for MBean",
                ShowContext.class.getClassLoader(),
                ccmbs.getClassLoaderFor(createdName));

        assertEquals("isRegistered", true, ccmbs.isRegistered(createdName));
        assertEquals("isInstanceOf", true, ccmbs.isInstanceOf(createdName,
                ShowContext.class.getName()));
        assertEquals("isInstanceOf", false, ccmbs.isInstanceOf(createdName,
                DynamicMBean.class.getName()));
        ccmbs.unregisterMBean(createdName);
        assertEquals("isRegistered after unregister",
                false, ccmbs.isRegistered(createdName));

        MLet mlet = new MLet();
        ObjectName defaultMLetName = new ObjectName("DefaultDomain:type=MLet");

        ccmbs.registerMBean(mlet, defaultMLetName);

        assertEquals("getClassLoader", mlet, ccmbs.getClassLoader(defaultMLetName));

        assertEquals("number of MBean operations", 0, mbeanLogIH.log.size());

        // Test that contexts still work when we can't combine two encoded contexts.
        // Here, we wrap cmbs (mickey=mouse) so that ccmbs2 (donald=duck) cannot
        // see that it already contains a context and therefore cannot combine
        // into mickey=mouse;donald=duck.  We don't actually use the snoop
        // capabilities of the returned object -- we just want an opaque
        // MBeanServer wrapper
        MBeanServer cmbs2 = newSnoop(MBeanServer.class, new LogIH(cmbs));
        MBeanServer ccmbs2 = ClientContext.withContext(cmbs2, "donald", "duck");
        assertEquals("context when combination is impossible",
                disney, ccmbs2.getAttribute(name, "Context"));

        // Test failure cases of ClientContext.encode
        final List<Map<String, String>> badEncodeArgs =
                Arrays.asList(
                    null,
                    Collections.<String,String>singletonMap(null, "foo"),
                    Collections.<String,String>singletonMap("foo", null));
        for (Map<String, String> bad : badEncodeArgs) {
            try {
                String oops = ClientContext.encode(bad);
                failed("ClientContext.encode(" + bad + ") should have failed: "
                        + oops);
            } catch (Exception e) {
                assertEquals("Exception for ClientContext.encode(" + bad + ")",
                        IllegalArgumentException.class, e.getClass());
            }
        }

        // ADD NEW TESTS HERE ^^^

        if (failure != null)
            throw new Exception(failure);
    }

    private static void assertEquals(String what, Object x, Object y) {
        if (!equal(x, y))
            failed(what + ": expected " + string(x) + "; got " + string(y));
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
        assertCalled(logIH, op, expectedContext, (Object[]) null);
    }

    private static void assertCalled(
            LogIH logIH, String op, Map<String, String> expectedContext,
            Object... params) {
        LogRecord lr = logIH.log.remove();
        assertEquals("called operation", op, lr.op);
        if (params != null)
            assertEquals("operation parameters", params, lr.params);
        assertEquals("operation context", expectedContext, lr.context);
    }

    private static void failed(String why) {
        failure = why;
        new Throwable("FAILED: " + why).printStackTrace(System.out);
    }
}
