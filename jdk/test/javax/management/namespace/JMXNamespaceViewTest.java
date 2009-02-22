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
 * @test JMXNamespaceViewTest.java
 * @summary Test the JMXNamespaceView class.
 * @bug 5072476
 * @author Daniel Fuchs
 * @run clean JMXNamespaceViewTest Wombat WombatMBean
 * @run build JMXNamespaceViewTest Wombat WombatMBean
 * @run main JMXNamespaceViewTest
 */


import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.ClientContext;
import javax.management.JMException;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaceView;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXRemoteNamespace;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 * A simple test to test the JMXNamespaceViewTest...
 * @author dfuchs
 */
public class JMXNamespaceViewTest {

    /**
     * Describe the configuration of a namespace
     */
    public static class NamespaceConfig {
        /** name of the namespace - no // allowed **/
        public String name;
        /**
         * JMXServiceURL through which the namespace is exported, if it
         * is a remote namespace. {@code null} if the namespace is local.
         * This is an inpur URL - eg: new JMXServiceURL("rmi",null,0).toString()
         * is acceptable here.
         */
        public String jmxurl;
        /**
         * Values of the name= key for each WombatMBean contained in the
         * namespace.
         */
        public String[] wombats;
        /** list of child namespace **/
        public NamespaceConfig[] children;
    }

    /**
     * Creates a NamespaceConfig record for a local namespace.
     * @param name     name  of the namespace
     * @param wombats  names of WombatMBean it should contain.
     * @return a NamespaceConfig.
     */
    public static NamespaceConfig config(String name, String[] wombats) {
        return config(name,null,wombats);
    }

    /**
     * Creates a NamespaceConfig record for a remote namespace.
     * @param name    name  of the namespace
     * @param jmxurl  input JMXServiceURL for creating the JMXConnectorServer
     * @param wombats names of WombatMBean it should contain.
     * @return a NamespaceConfig.
     */
    public static NamespaceConfig config(String name, String jmxurl,
            String[] wombats) {
        return config(name,jmxurl,wombats,(NamespaceConfig[])null);
    }

    /**
     * Creates a NamespaceConfig record for a local namespace.
     * @param name     name  of the namespace
     * @param wombats  names of WombatMBean it should contain.
     * @param children list  of sub namespaces.
     * @return a NamespaceConfig.
     */
    public static NamespaceConfig config(String name, String[] wombats,
            NamespaceConfig... children) {
        return config(name,null,wombats,children);
    }

    /**
     * Creates a NamespaceConfig record for a remote namespace.
     * @param name    name  of the namespace
     * @param jmxurl  input JMXServiceURL for creating the JMXConnectorServer
     * @param wombats names of WombatMBean it should contain.
     * @param children list  of sub namespaces.
     * @return a NamespaceConfig.
     */
    static NamespaceConfig config(String name, String jmxurl, String[] wombats,
            NamespaceConfig... children) {
         final NamespaceConfig cfg = new NamespaceConfig();
         cfg.name=name; cfg.jmxurl=jmxurl; cfg.wombats=wombats;
         cfg.children=children;
         return cfg;
    }

    /**
     * Returns the given names. This is a utility method to ease code
     * reading.
     * @param names names of Wombat MBeans.
     * @return the given names.
     */
    static String[] wombats(String... names) {
        return names;
    }

    /**
     * Creates a JMXServiceURL string for the given protocol.
     * This is also a utility method to ease code reading.
     * @param protocol The protocol name (e.g. "rmi")
     * @return A JMXServiceURL string.
     * @throws Exception if creation of the JMXServiceURL fails.
     */
    static String url(String protocol) throws Exception {
        return new JMXServiceURL(protocol,null,0).toString();
    }

    /**
     * Creates a config for a hierarchy of namespaces, mixing local namespaces
     * and remote namespaces using the given protocol.
     * @param protocol The protocol that should be used for remote namespaces.
     * @return A namespace config hierarchy.
     * @throws java.lang.Exception
     */
    public static NamespaceConfig[] makeConfig(String protocol)
        throws Exception {
        final NamespaceConfig[] config = {
        // Top level namespace "top1" (local)
        config("top1",wombats("wchief","w1","w2","w3"),
                // top1//local1
                config("local1",wombats("wchief","ww1","ww2")),
                // top1//local2
                config("local2",wombats("wchief","ww4","ww5","ww6"),
                    // top1//local2//local3
                    config("local3",wombats("wchief","www1","www2")),
                    // top1//local2//rmi1
                    config("rmi1",url(protocol),wombats("wchief","www3","www4","www5"))),
                // top1//rmi2
                config("rmi2",url(protocol),wombats("wchief","ww7","ww8","ww9"),
                    // top1//rmi2//local4
                    config("local4",wombats("wchief","www6","www7")),
                    // top1//rmi2//rmi3
                    config("rmi3",url(protocol),wombats("wchief","www3","www4","www5"),
                        // top1//rmi2//rmi3//local5
                        config("local5",wombats("wchief","wwww1"))))),
        // Top level namespace "top2" (local)
        config("top2",wombats("wchief","w21","w22","w23"),
                // top2//local21
                config("local21",wombats("wchief","ww21","ww22")),
                // top2//rmi22
                config("rmi22",url(protocol),wombats("wchief","ww27","ww28","ww29"),
                    // top2//rmi22//local24
                    config("local24",wombats("wchief","www26","www27")),
                    // top2//rmi22//rmi23
                    config("rmi23",url(protocol),wombats("wchief","www23","www24","www25"),
                        // top2//rmi22//rmi23//local25
                        config("local25",wombats("wchief","wwww21"))))),
        // Top level namespace "top3" (remote)
        config("top3",url(protocol),wombats("wchief","w31","w32","w33"),
                // top3//local31
                config("local31",wombats("wchief","ww31","ww32")),
                // top3//rmi32
                config("rmi32",url(protocol),wombats("wchief","ww37","ww38","ww39"),
                    // top3//rmi32//local34
                    config("local34",wombats("wchief","www36","www37")),
                    // top3//rmi32//rmi33
                    config("rmi33",url(protocol),wombats("wchief","www33","www34","www35"),
                        // top3//rmi32//local35
                        config("local35",wombats("wchief","wwww31"))))),
        };
        return config;
    }

    /**
     * Close all connector servers in the list.
     * @param cslist List of connector servers to close.
     */
    public static void closeAll(List<JMXConnectorServer> cslist) {
            for (JMXConnectorServer cs : cslist) {
                try {
                    cs.stop();
                } catch (Exception xx) {
                    System.err.println("Failed to stop connector: " + xx);
                }
            }
    }

    public static class MBeanServerConfigCreator {
        public MBeanServer createMBeanServerFor(NamespaceConfig config) {
            return MBeanServerFactory.newMBeanServer();
        }
    }

    /**
     * Load the given namespace configuration inside the given MBeanServer.
     * Return a list of connector servers created in the process.
     * @param server      The MBeanServer in which the namespaces must
     *                    be created.
     * @param namespaces  The list of namespaces to create.
     * @return a list of started connector servers.
     * @throws java.lang.Exception failed to create the specified namespaces.
     */
    public static List<JMXConnectorServer> load(MBeanServer server,
           MBeanServerConfigCreator factory,
           NamespaceConfig... namespaces) throws Exception {
        final List<JMXConnectorServer> cslist =
                new ArrayList<JMXConnectorServer>();
        try {
            final ObjectName creator =
                    new ObjectName("jmx.creator:type=JMXNamespaceCreator");
            if (System.getProperty("jmx.wait")!=null
                    && !server.isRegistered(creator)) {
                server.registerMBean(new JMXNamespaceCreator(),creator);
            }
            for (NamespaceConfig cfg : namespaces) {
                final MBeanServer srv = factory.createMBeanServerFor(cfg);
                if (System.getProperty("jmx.wait")!=null
                    && !srv.isRegistered(creator)) {
                    srv.registerMBean(new JMXNamespaceCreator(),creator);
                }
                if (cfg.wombats != null) {
                    for (String w : cfg.wombats) {
                        final ObjectName n =
                                new ObjectName("wombat:type=Wombat,name=" + w);
                        final WombatMBean ww = new Wombat();
                        srv.registerMBean(ww, n);
                    }
                }
                if (cfg.children != null) {
                    cslist.addAll(load(srv, factory, cfg.children));
                }
                JMXNamespace nm;
                if (cfg.jmxurl == null) {
                    nm = new JMXNamespace(srv);
                } else {
                    JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL(cfg.jmxurl),
                            null, srv);
                    srv.registerMBean(cs,
                            new ObjectName("jmx.remote:type=JMXConnectorServer"));
                    cs.start();
                    cslist.add(cs);
                    nm = JMXRemoteNamespace.
                            newJMXRemoteNamespace(cs.getAddress(),
                            null);
                }
                server.registerMBean(nm,
                        JMXNamespaces.getNamespaceObjectName(cfg.name));
                if (nm instanceof JMXRemoteNamespace) {
                    server.invoke(
                            JMXNamespaces.getNamespaceObjectName(cfg.name),
                            "connect", null, null);
                }
            }
        } catch (Exception x) {
            closeAll(cslist);
            throw x;
        }
        return cslist;
    }

    /**
     * Add an entry {@code <path,NamespaceConfig>} in the map for the given
     * namespace and its subnamespaces.
     * @param map    A {@code Map<path,NamespaceConfig>}.
     * @param parent The path of the parent workspace.
     * @param cfg    The NamespaceConfig hierarchy to index in the map.
     */
    public static void fillMap(Map<String,NamespaceConfig> map, String parent,
            NamespaceConfig cfg) {

        final String where;
        if (parent == null || parent.equals(""))
            where=cfg.name;
        else
            where=parent+JMXNamespaces.NAMESPACE_SEPARATOR+cfg.name;
        map.put(where,cfg);
        if (cfg.children==null) return;
        for(NamespaceConfig child:cfg.children) {
            fillMap(map,where,child);
        }
    }

    /**
     * Compare a list of namespace names obtained from JMXNamespaceView.list()
     * with the expected clildren list of the corresponding NamespaceConfig.
     * @param list      A list of namespace names
     * @param children  A list of NamespaceConfig correspondng to expected
     *                  namespace.
     * @param fail      If true and the comparison yields false, throws an
     *                  exception instead of simply returning false.
     * @return true if OK, false if NOK.
     */
    private static boolean compare(String[] list, NamespaceConfig[] children,
            boolean fail) {
        final List<String> found = new ArrayList<String>(Arrays.asList(list));
        if (found.contains(ClientContext.NAMESPACE))
            found.remove(ClientContext.NAMESPACE);

        if (children == null && found.size()==0) return  true;
        if (children == null && fail == false) return false;
        if (children == null) throw new RuntimeException(
                "No child expected. Found "+Arrays.toString(list));
        final Set<String> names = new HashSet<String>();
        for (NamespaceConfig cfg : children) {
            names.add(cfg.name);
            if (found.contains(cfg.name)) continue;
            if (!fail) return false;
            throw new RuntimeException(cfg.name+" not found in "+
                    found);
        }
        found.removeAll(names);
        if (found.size()==0) return true;
        if (fail==false) return false;
        throw new RuntimeException("found additional namespaces: "+
                found);
    }

    /**
     * Compares the result of queryNames(null,null) with a set of expected
     * wombats.
     * @param where    The path of the namespace that was queried.
     * @param list     The set of ObjectNames found.
     * @param wombats  The expected list of wombats.
     * @param fail      If true and the comparison yields false, throws an
     *                  exception instead of simply returning false.
     * @return true if OK, false if NOK.
     * @throws java.lang.Exception something went wrong.
     */
    private static boolean compare(String where,
            Set<ObjectName>list, String[] wombats,
            boolean fail) throws Exception {
        final Set<ObjectName> found = new HashSet<ObjectName>();
        final Set<ObjectName> expected = new HashSet<ObjectName>();
        for (ObjectName n : list) {
            if ("Wombat".equals(n.getKeyProperty("type")))
               found.add(n);
        }
        for(String w : wombats) {
            final ObjectName n =
                    new ObjectName("wombat:type=Wombat,name=" + w);
            expected.add(n);
            if (found.contains(n)) continue;
            if (fail == false) return false;
            throw new RuntimeException(where+
                    ": Wombat "+w+" not found in "+found);
        }
        found.removeAll(expected);
        if (found.size()==0) {
            System.out.println(where+": found all expected: "+expected);
            return true;
        }
        if (fail==false) return false;
        throw new RuntimeException(where+": found additional MBeans: "+
                found);
    }

    /**
     * A generic test to test JMXNamespaceView over a namespace configuration.
     * @param server      The MBeanServer in which to load the namespace
     *                    config.
     * @param namespaces  The namespace config to run the test over...
     * @throws java.lang.Exception
     */
    public static void doTest(MBeanServer server, NamespaceConfig... namespaces)
            throws Exception {
        List<JMXConnectorServer> cslist = load(server,
                new MBeanServerConfigCreator(), namespaces);
        Map<String,NamespaceConfig> inputMap =
                new HashMap<String,NamespaceConfig>();

        for (NamespaceConfig cfg : namespaces) {
            fillMap(inputMap,"",cfg);
        }
        try {
            final JMXNamespaceView root = new JMXNamespaceView(server);
            List<JMXNamespaceView> vlist = new ArrayList<JMXNamespaceView>();
            vlist.add(root);

            while (!vlist.isEmpty()) {
                JMXNamespaceView v = vlist.remove(0);
                final String where = v.isRoot()?"root":v.where();
                System.out.println(where+": "+
                   v.getMBeanServerConnection().queryNames(null,null));
                for (String ns : v.list()) {
                    final JMXNamespaceView down = v.down(ns);
                    vlist.add(down);
                    if (!down.where().equals(v.isRoot()?ns:where+
                            JMXNamespaces.NAMESPACE_SEPARATOR+ns)) {
                        throw new RuntimeException("path of "+down.where()+
                            " should be "+(v.isRoot()?ns:where+
                            JMXNamespaces.NAMESPACE_SEPARATOR+ns));
                    }
                    if (down.up().equals(v)) continue;
                    throw new RuntimeException("parent of "+down.where()+
                            " should be "+where);
                }
                final NamespaceConfig[] children;
                final NamespaceConfig   cfg;
                if (v.isRoot()) {
                    children=namespaces;
                    cfg = null;
                } else {
                    cfg = inputMap.get(where);
                    children = cfg==null?null:cfg.children;
                }
                compare(v.list(),children,true);
                if (!v.isRoot()) {
                    if (where.endsWith(ClientContext.NAMESPACE)) {
                        System.out.println(where+": skipping queryNames analysis");
                        continue;
                    }
                    //System.out.println(where+": cfg is: "+cfg);
                    compare(where,v.getMBeanServerConnection().
                        queryNames(null, null),cfg.wombats,true);
                }
            }

            exportAndWaitIfNeeded(server);
        } finally {
            closeAll(cslist);
        }
    }

    public static interface JMXNamespaceCreatorMBean {
        public ObjectInstance createLocalNamespace(String namespace)
                throws JMException ;
        public void removeLocalNamespace(String namespace)
                throws JMException;
    }

    public static class JMXNamespaceCreator
            implements MBeanRegistration,
                      JMXNamespaceCreatorMBean {

        private volatile MBeanServer mbeanServer;

        public ObjectInstance createLocalNamespace(String namespace)
            throws JMException {
            return mbeanServer.registerMBean(
                    new JMXNamespace(MBeanServerFactory.newMBeanServer()),
                    JMXNamespaces.getNamespaceObjectName(namespace));
        }

        public void removeLocalNamespace(String namespace)
            throws JMException {
            mbeanServer.unregisterMBean(
                    JMXNamespaces.getNamespaceObjectName(namespace));
        }

        public ObjectName preRegister(MBeanServer server, ObjectName name)
                throws Exception {
            mbeanServer = server;
            return name;
        }

        public void postRegister(Boolean registrationDone) {
        }

        public void preDeregister() throws Exception {
         }

        public void postDeregister() {
        }

    }

    public static void exportAndWaitIfNeeded(MBeanServer server)
        throws Exception {
                if (System.getProperty("jmx.wait")!=null) {
                final int port = getPortFor("rmi");
                LocateRegistry.createRegistry(port);
                final JMXServiceURL url =
                        new JMXServiceURL("rmi",null,port,
                        "/jndi/rmi://localhost:"+port+"/jmxrmi");
                final JMXConnectorServer cs =
                        JMXConnectorServerFactory.
                        newJMXConnectorServer(url, null, server);
                cs.start();
                try {
                    System.out.println("RMI Server waiting at: "+cs.getAddress());
                    System.in.read();
                } finally {
                    cs.stop();
                }
            }
    }

    public static int getPortFor(String protocol) throws Exception {
        final int aport =
              Integer.valueOf(System.getProperty("jmx."+protocol+".port","0"));
        if (aport > 0) return aport;
        final ServerSocket s = new ServerSocket(0);
        try {
            final int port = s.getLocalPort();
            return port;
        } finally {
            s.close();
        }
    }

    public static void main(String[] args) throws Exception {
        doTest(ManagementFactory.getPlatformMBeanServer(),makeConfig("rmi"));
    }

}
