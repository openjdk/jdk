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
 * @test JMXNamespaceTest.java
 * @summary General JMXNamespace test.
 * @bug 5072476
 * @author Daniel Fuchs
 * @run clean JMXNamespaceTest
 *            Wombat WombatMBean JMXRemoteTargetNamespace
 *            NamespaceController NamespaceControllerMBean
 * @compile -XDignore.symbol.file=true JMXNamespaceTest.java
 *            Wombat.java WombatMBean.java JMXRemoteTargetNamespace.java
 *            NamespaceController.java NamespaceControllerMBean.java
 * @run main/othervm JMXNamespaceTest
 */
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.NotificationEmitter;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaceMBean;
import javax.management.namespace.JMXRemoteNamespaceMBean;
import javax.management.namespace.MBeanServerConnectionWrapper;
import javax.management.namespace.MBeanServerSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author Sun Microsystems, Inc.
 */
public class JMXNamespaceTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(JMXNamespaceTest.class.getName());

    /** Creates a new instance of JMXNamespaceTest */
    public JMXNamespaceTest() {
    }

    public static class WombatRepository extends MBeanServerSupport {
        final Wombat wombat;
        final StandardMBean mbean;
        final ObjectName wombatName;

        public WombatRepository(ObjectName wombatName) {
            try {
                wombat = new Wombat();
                mbean  = wombat;
                this.wombatName = wombatName;
                wombat.preRegister(null,wombatName);
            } catch (Exception x) {
                throw new IllegalArgumentException(x);
            }
        }

        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
            throws InstanceNotFoundException {
            if (wombatName.equals(name)) return mbean;
            else throw new InstanceNotFoundException(String.valueOf(name));
        }

        @Override
        protected Set<ObjectName> getNames() {
            final Set<ObjectName> res = Collections.singleton(wombatName);
            return res;
        }

        @Override
        public NotificationEmitter
                getNotificationEmitterFor(ObjectName name)
            throws InstanceNotFoundException {
            final DynamicMBean mb = getDynamicMBeanFor(name);
            if (mb instanceof NotificationEmitter)
                return (NotificationEmitter)mb;
            return null;
        }
    }

    public static class SimpleTest {
            public final String   descr;
            private final Class<?> testClass;
            private final Method method;
            public SimpleTest(String descr) {
                this.descr = descr;
                this.testClass = JMXNamespaceTest.class;
                try {
                    method = testClass.
                        getDeclaredMethod(descr,SimpleTestConf.class,
                            Object[].class);
                } catch (NoSuchMethodException x) {
                    throw new IllegalArgumentException(descr+": test not found",
                            x);
                }
            }

            public void run(SimpleTestConf conf, Object... args)
                throws Exception {
                try {
                    method.invoke(null,conf,args);
                } catch (InvocationTargetException x) {
                    final Throwable cause = x.getCause();
                    if (cause instanceof Exception) throw (Exception)cause;
                    if (cause instanceof Error) throw (Error)cause;
                    throw x;
                }
            }
    }

    public static class SimpleTestConf {
        public final  Wombat wombat;
        public final  StandardMBean mbean;
        public final  String dirname;
        public final  ObjectName handlerName;
        public final  ObjectName wombatNickName;
        public final  ObjectName wombatName;
        public final  JMXNamespace wombatNamespace;
        public final  MBeanServer server;
        public final  WombatMBean proxy;
        public SimpleTestConf(String[] args) throws Exception {
            wombat = new Wombat();
            mbean = wombat;
            dirname = "wombat";
            handlerName =
                    new ObjectName(dirname+"//:type=JMXNamespace");

            wombatNickName =
                    new ObjectName("burrow:type=Wombat");

            wombatName =
                    new ObjectName(dirname+"//"+wombatNickName);

            wombatNamespace =
                    new JMXNamespace(
                    new WombatRepository(wombatNickName));

            server = ManagementFactory.getPlatformMBeanServer();
            System.out.println(handlerName+" registered="+
                    server.isRegistered(handlerName));
            server.registerMBean(wombatNamespace,handlerName);

            try {
                proxy = JMX.newMBeanProxy(server,wombatName,
                                WombatMBean.class);
            } catch (Exception x) {
                server.unregisterMBean(handlerName);
                throw x;
            }
        }

        public void close() {
            try {
                server.unregisterMBean(handlerName);
            } catch (Exception x) {
                System.out.println("Failed to close: " + x);
                x.printStackTrace();
            }
        }

        public void test(SimpleTest test,Object... args)
            throws Exception {
            try {
                test.run(this,args);
                passed++;
            } catch (Exception x) {
                failed++;
                System.err.println(test.descr+" failed: " + x);
                x.printStackTrace();
            }
        }

        public volatile int failed = 0;
        public volatile int passed = 0;
    }

    static void checkValue(String name,Object expected, Object returned)
        throws InvalidAttributeValueException {
        if (Collections.singletonList(expected).
                equals(Collections.singletonList(returned))) return;

        throw new InvalidAttributeValueException("Bad value for "+
                name+": ["+returned+"] - was expecting ["+expected+"]");
    }

    // ---------------------------------------------------------------
    // SIMPLE TESTS BEGIN HERE
    // ---------------------------------------------------------------

    static void getCaptionTest(SimpleTestConf env, Object... args)
        throws Exception {
        System.out.println(env.proxy.getCaption());
    }

    static void setCaptionTest(SimpleTestConf env, Object... args)
        throws Exception {
        env.proxy.setCaption((String)args[0]);
        final String result = env.proxy.getCaption();
        System.out.println(result);
        checkValue("Caption",args[0],result);
    }

    static void queryNamesTest1(SimpleTestConf env, Object... args)
        throws Exception {
        final ObjectName pat =
                new ObjectName(env.handlerName.getDomain()+"*:*");
        final Set<ObjectName> res =
                env.server.queryNames(pat,null);
        System.out.println("queryNamesTest1: "+res);
        checkValue("names",Collections.singleton(env.wombatName),res);
    }

    static void queryNamesTest2(SimpleTestConf env, Object... args)
        throws Exception {
        final ObjectName pat =
                new ObjectName("*:"+
                env.wombatName.getKeyPropertyListString());
        final Set<ObjectName> res =
                env.server.queryNames(pat,null);
        System.out.println("queryNamesTest2: "+res);
        checkValue("names",Collections.emptySet(),res);
    }

    static void getDomainsTest(SimpleTestConf env, Object... args)
        throws Exception {
        final List<String> domains =
                Arrays.asList(env.server.getDomains());
        System.out.println("getDomainsTest: "+domains);
        if (domains.contains(env.wombatName.getDomain()))
            throw new InvalidAttributeValueException("domain: "+
                    env.wombatName.getDomain());
        if (!domains.contains(env.handlerName.getDomain()))
            throw new InvalidAttributeValueException("domain not found: "+
                    env.handlerName.getDomain());
    }

    // ---------------------------------------------------------------
    // SIMPLE TESTS END HERE
    // ---------------------------------------------------------------

    private static void simpleTest(String[] args) {
        final SimpleTestConf conf;
        try {
            conf = new SimpleTestConf(args);
            try {
                conf.test(new SimpleTest("getCaptionTest"));
                conf.test(new SimpleTest("setCaptionTest"),
                        "I am a new Wombat!");
                conf.test(new SimpleTest("queryNamesTest1"));
                conf.test(new SimpleTest("queryNamesTest2"));
                conf.test(new SimpleTest("getDomainsTest"));
            } finally {
                conf.close();
            }
        } catch (Exception x) {
            System.err.println("simpleTest FAILED: " +x);
            x.printStackTrace();
            throw new RuntimeException(x);
        }
        System.out.println("simpleTest: "+conf.passed+
                " PASSED, " + conf.failed + " FAILED.");
        if (conf.failed>0) {
            System.err.println("simpleTest FAILED ["+conf.failed+"]");
            throw new RuntimeException("simpleTest FAILED ["+conf.failed+"]");
        } else {
            System.err.println("simpleTest PASSED ["+conf.passed+"]");
        }
    }

    public static void recursiveTest(String[] args) {
        final SimpleTestConf conf;
        try {
            conf = new SimpleTestConf(args);
            try {
                final JMXServiceURL url =
                        new JMXServiceURL("rmi","localHost",0);
                final Map<String,Object> empty = Collections.emptyMap();
                final JMXConnectorServer server =
                        JMXConnectorServerFactory.newJMXConnectorServer(url,
                        empty,conf.server);
                server.start();
                final JMXServiceURL address = server.getAddress();
                final JMXConnector client =
                        JMXConnectorFactory.connect(address,
                        empty);
                final String[] signature = {
                    JMXServiceURL.class.getName(),
                    Map.class.getName(),
                };
                final String[] signature2 = {
                    JMXServiceURL.class.getName(),
                    Map.class.getName(),
                    String.class.getName(),
                };
                final Object[] params = {
                    address,
                    null,
                };
                final MBeanServerConnection c =
                        client.getMBeanServerConnection();
                final ObjectName dirName1 =
                        new ObjectName("kanga//:type=JMXNamespace");
                c.createMBean(JMXRemoteTargetNamespace.class.getName(),
                              dirName1, params,signature);
                c.invoke(dirName1, "connect", null, null);
                try {
                    final MemoryMXBean memory =
                            JMX.newMXBeanProxy(c,
                            new ObjectName("kanga//"+
                            ManagementFactory.MEMORY_MXBEAN_NAME),
                            MemoryMXBean.class);
                    System.out.println("HeapMemory #1: "+
                            memory.getHeapMemoryUsage().toString());
                    final MemoryMXBean memory2 =
                            JMX.newMXBeanProxy(c,
                            new ObjectName("kanga//kanga//"+
                            ManagementFactory.MEMORY_MXBEAN_NAME),
                            MemoryMXBean.class);
                    System.out.println("HeapMemory #2: "+
                            memory2.getHeapMemoryUsage().toString());
                    final Object[] params2 = {
                        address,
                        null,
                        "kanga//kanga"
                        // "kanga//kanga//roo//kanga", <= cycle
                    };
                    final ObjectName dirName2 =
                            new ObjectName("kanga//roo//:type=JMXNamespace");
                    c.createMBean(JMXRemoteTargetNamespace.class.getName(),
                              dirName2, params2, signature2);
                    System.out.println(dirName2 + " created!");
                    JMX.newMBeanProxy(c,dirName2,
                            JMXRemoteNamespaceMBean.class).connect();
                    try {
                        final ObjectName wombatName1 =
                                new ObjectName("kanga//roo//"+conf.wombatName);
                        final ObjectName wombatName2 =
                                new ObjectName("kanga//roo//"+wombatName1);
                        final WombatMBean wombat1 =
                                JMX.newMBeanProxy(c,wombatName1,WombatMBean.class);
                        final WombatMBean wombat2 =
                                JMX.newMBeanProxy(c,wombatName2,WombatMBean.class);
                        final String newCaption="I am still the same old wombat";
                        wombat1.setCaption(newCaption);
                        final String caps = conf.proxy.getCaption();
                        System.out.println("Caption: "+caps);
                        checkValue("Caption",newCaption,caps);
                        final String caps1 = wombat1.getCaption();
                        System.out.println("Caption #1: "+caps1);
                        checkValue("Caption #1",newCaption,caps1);
                        final String caps2 = wombat2.getCaption();
                        System.out.println("Caption #2: "+caps2);
                        checkValue("Caption #2",newCaption,caps2);
                        final ObjectInstance instance =
                                NamespaceController.createInstance(conf.server);
                        final NamespaceControllerMBean controller =
                                JMX.newMBeanProxy(conf.server,instance.getObjectName(),
                                                  NamespaceControllerMBean.class);
                        final String[] dirs = controller.findNamespaces();
                        System.out.println("directories: " +
                                Arrays.asList(dirs));
                        final int depth = 4;
                        final String[] dirs2 = controller.findNamespaces(null,null,depth);
                        System.out.println("directories[depth="+depth+"]: " +
                                Arrays.asList(dirs2));
                        for (String dir : dirs2) {
                            if (dir.endsWith(JMXNamespaces.NAMESPACE_SEPARATOR))
                                dir = dir.substring(0,dir.length()-
                                        JMXNamespaces.NAMESPACE_SEPARATOR.length());
                            if (dir.split(JMXNamespaces.NAMESPACE_SEPARATOR).length
                                        > (depth+1)) {
                                throw new RuntimeException(dir+": depth exceeds "+depth);
                            }
                            final ObjectName handlerName =
                                    JMXNamespaces.getNamespaceObjectName(dir);
                            final JMXNamespaceMBean handler =
                                    JMX.newMBeanProxy(conf.server,handlerName,
                                    JMXNamespaceMBean.class);
                            try {
                            System.err.println("Directory "+dir+" domains: "+
                                    Arrays.asList(handler.getDomains()));
                            System.err.println("Directory "+dir+" default domain: "+
                                    handler.getDefaultDomain());
                            System.err.println("Directory "+dir+" MBean count: "+
                                    handler.getMBeanCount());
                            } catch(Exception x) {
                                System.err.println("get info failed for " +
                                        dir +", "+handlerName+": "+x);
                                x.getCause().printStackTrace();
                                throw x;
                            }
                        }

                    } finally {
                        c.unregisterMBean(dirName2);
                    }
                } finally {
                    c.unregisterMBean(dirName1);
                    client.close();
                    server.stop();
                }
            } finally {
                conf.close();
            }
            System.err.println("recursiveTest PASSED");
        } catch (Exception x) {
            System.err.println("recursiveTest FAILED: " +x);
            x.printStackTrace();
            throw new RuntimeException(x);
        }
    }

    public static void verySimpleTest(String[] args) {
        System.err.println("verySimpleTest: starting");
        try {
            final MBeanServer srv = MBeanServerFactory.createMBeanServer();
            srv.registerMBean(new JMXNamespace(
                    JMXNamespaces.narrowToNamespace(srv, "foo")),
                    JMXNamespaces.getNamespaceObjectName("foo"));
            throw new Exception("Excpected IllegalArgumentException not raised.");
        } catch (IllegalArgumentException x) {
            System.err.println("verySimpleTest: got expected exception: "+x);
        } catch (Exception x) {
            System.err.println("verySimpleTest FAILED: " +x);
            x.printStackTrace();
            throw new RuntimeException(x);
        }
        System.err.println("verySimpleTest: PASSED");
    }

    public static void verySimpleTest2(String[] args) {
        System.err.println("verySimpleTest2: starting");
        try {
            final MBeanServer srv = MBeanServerFactory.createMBeanServer();
            final JMXConnectorServer cs = JMXConnectorServerFactory.
                    newJMXConnectorServer(new JMXServiceURL("rmi",null,0),
                    null, srv);
            cs.start();
            final JMXConnector cc = JMXConnectorFactory.connect(cs.getAddress());

            srv.registerMBean(new JMXNamespace(
                    new MBeanServerConnectionWrapper(
                            JMXNamespaces.narrowToNamespace(
                                cc.getMBeanServerConnection(),
                                "foo"))),
                    JMXNamespaces.getNamespaceObjectName("foo"));
            throw new Exception("Excpected IllegalArgumentException not raised.");
        } catch (IllegalArgumentException x) {
            System.err.println("verySimpleTest2: got expected exception: "+x);
        } catch (Exception x) {
            System.err.println("verySimpleTest2 FAILED: " +x);
            x.printStackTrace();
            throw new RuntimeException(x);
        }
        System.err.println("verySimpleTest2: PASSED");
    }

    public static void main(String[] args) {
        simpleTest(args);
        recursiveTest(args);
        verySimpleTest(args);
        verySimpleTest2(args);
    }

}
