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
 * @test JMXNamespaceSecurityTest.java
 * @summary General JMXNamespaceSecurityTest test.
 * @author Daniel Fuchs
 * @bug 5072476 6299231
 * @run clean JMXNamespaceViewTest JMXNamespaceSecurityTest Wombat WombatMBean
 *            LazyDomainTest
 * @run build JMXNamespaceSecurityTest JMXNamespaceViewTest Wombat WombatMBean
 *            LazyDomainTest
 * @run main/othervm JMXNamespaceSecurityTest namespace.policy
 */
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.namespace.JMXDomain;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.remote.JMXConnectorServer;

/**
 *
 * @author Sun Microsystems, Inc.
 */
public class JMXNamespaceSecurityTest extends JMXNamespaceViewTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(JMXNamespaceSecurityTest.class.getName());

    public static class NamedMBeanServerCreator
            extends JMXNamespaceViewTest.MBeanServerConfigCreator {
      public MBeanServer createMBeanServerFor(NamespaceConfig config) {
            return MBeanServerFactory.
                    createNamedMBeanServer(config.name,config.name);
        }
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

    public static void test(MBeanServer server, NamespaceConfig[] namespaces)
        throws Exception {
        System.out.println("Launching test...");
        List<JMXConnectorServer> cslist = load(server,
                new NamedMBeanServerCreator(), namespaces);
        Map<String,NamespaceConfig> inputMap =
                new HashMap<String,NamespaceConfig>();

        for (NamespaceConfig cfg : namespaces) {
            fillMap(inputMap,"",cfg);
        }
        final MBeanServer platform = ManagementFactory.getPlatformMBeanServer();
        //if (System.getProperty("jmx.wait")!=null) {
            /*
            // if we wanted to lazy load the platform MBeanServer:
            final LazyDomainTest.MBeanServerLoader loader =
                    new LazyDomainTest.MBeanServerLoader() {
                public MBeanServer loadMBeanServer() {
                    return ManagementFactory.getPlatformMBeanServer();
                }
            };
            final LazyDomainTest.MBeanServerProxy proxy =
                    new LazyDomainTest.MBeanServerProxy(loader);
            final LazyDomainTest.LazyDomain domain =
                    new LazyDomainTest.LazyDomain(proxy);
            server.registerMBean(domain,
                    JMXDomain.getDomainObjectName("java.lang"));
            */
            // Mount java.lang MBeans into our private server so that
            // visualvm can connect.
            server.registerMBean(
                    new JMXDomain(platform),
                    JMXDomain.getDomainObjectName("java.lang"));
        //}
        if (System.getProperty("jmx.wait")!=null) {
            platform.registerMBean(new JMXNamespace(server),
                    JMXNamespaces.getNamespaceObjectName("test"));
        }

        System.setSecurityManager(new SecurityManager());

        // Some sanity checks... The policy file should allow access
        // to java.lang MBeans.
        final ObjectName platnames = new ObjectName("java.lang:*");
        for (ObjectName o : platform.queryNames(platnames,null)) {
            server.getMBeanInfo(o);
        }
        final Set<ObjectName> lang =
                new HashSet<ObjectName>(server.queryNames(platnames, null));
        lang.remove(JMXDomain.getDomainObjectName("java.lang"));
        if (!lang.equals(platform.
                queryNames(platnames, null)))
            throw new Exception("Wrong list of platform names: "+lang);
        System.out.println("Got all java.lang MBeans: "+lang);

        // The policy file should allow to see all namespaces.
        // check this...
        final List<ObjectName> patterns = new ArrayList<ObjectName>();
        final Set<String> paths = new TreeSet<String>();
        final Set<String> uuids = new HashSet<String>();
        patterns.add(new ObjectName("*//:*"));
        while (patterns.size()>0) {
            System.out.println("server.queryNames("+patterns.get(0)+",null)");
            Set<ObjectName> names = server.queryNames(patterns.remove(0),null);
            System.out.println("found: "+names);

            for (ObjectName no : names) {
                final String uuid = (String) server.getAttribute(no, "UUID");
                if (uuids.contains(uuid)) {
                    System.out.print("namespace "+no+", uuid="+uuid+
                            " already parsed. Skipping");
                    continue;
                }
                uuids.add(uuid);
                patterns.add(new ObjectName(no.getDomain()+"*//:*"));
                System.out.println("added pattern: "+
                        new ObjectName(no.getDomain()+"*//:*"));
                if (no.getDomain().endsWith(ClientContext.NAMESPACE+
                        JMXNamespaces.NAMESPACE_SEPARATOR)) continue;
                paths.add(no.getDomain().substring(0,
                        no.getDomain().length()-
                        JMXNamespaces.NAMESPACE_SEPARATOR.length()));
            }
        }
        final TreeSet<String> expected = new TreeSet<String>(inputMap.keySet());
        if (!expected.equals(paths)) {
            throw new Exception("wrong set of namespaces, expected "+
                    expected+", got "+paths);
        }

        System.out.println("Got all namespaces: "+paths);

        // Check that we can see all wombats.
        //
        ObjectName wchief =
                new ObjectName("top1//rmi2//wombat:name=wchief,type=Wombat");
        String caption = (String) server.getAttribute(wchief,"Caption");
        System.out.println("wchief says "+caption);
        Object mood = server.getAttribute(wchief,"Mood");
        System.out.println("wchief's mood on a scale of 100 is "+mood);

        ObjectName wchief2 =
                new ObjectName("top1//wombat:name=wchief,type=Wombat");
        String caption2 = (String) server.getAttribute(wchief2,"Caption");
        System.out.println("wchief2 says "+caption2);
        try {
            Object mood2 = server.getAttribute(wchief2,"Mood");
            System.out.println("wchief2's mood on a scale of 100 is "+mood2);
            throw new Exception("Expected security exception for "+
                    "getAttribute("+wchief2+", \"Mood\"");
        } catch (SecurityException x) {
            System.out.println("wchief2's mood is unavailable: "+x);
        }
        try {
            exportAndWaitIfNeeded(server);
        } finally {
            closeAll(cslist);
        }

    }
    /** Creates a new instance of JMXNamespaceTest */
    public JMXNamespaceSecurityTest() {
    }

    public static void main(String[] args) throws Exception {
        String osName = System.getProperty("os.name");
        System.out.println("os.name = " + osName);
        if (!osName.equals("SunOS")) {
            System.out.println("This test runs on Solaris only.");
            System.out.println("Bye! Bye!");
            return;
        }
        final String policy = System.getProperty("test.src") +
                File.separator + args[0];
        System.out.println("PolicyFile = " + policy);
        System.setProperty("java.security.policy", policy);
        if (!new File(System.getProperty("java.security.policy")).canRead())
            throw new IOException("no such file: "+
                    System.getProperty("java.security.policy"));
        test(MBeanServerFactory.createNamedMBeanServer("root","root"),
                makeConfig("rmi"));
    }

}
