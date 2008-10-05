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
 * @test QueryNamesTest.java 1.4
 * @summary Test how queryNames works with Namespaces.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean QueryNamesTest Wombat WombatMBean
 * @run build QueryNamesTest Wombat WombatMBean
 * @run main QueryNamesTest
 */


import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;

/**
 * Class QueryNamesTest
 * @author Sun Microsystems, 2005 - All rights reserved.
 */
public class QueryNamesTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(QueryNamesTest.class.getName());

    public static class LocalNamespace
            extends JMXNamespace {

        private static MBeanServer check(MBeanServer server) {
            if (server == null)
                throw new IllegalArgumentException("MBeanServer can't be null");
            return server;
        }

        public LocalNamespace() {
            this(MBeanServerFactory.createMBeanServer());
        }

        public LocalNamespace(MBeanServer server) {
            super(check(server));
        }


        public static String add(MBeanServerConnection server,
                String nspath)
                throws IOException, JMException {
            server.createMBean(LocalNamespace.class.getName(),
                    JMXNamespaces.getNamespaceObjectName(nspath));
            return nspath;
        }
    }

    /** Creates a new instance of QueryNamesTest */
    public QueryNamesTest() {
    }

    private static String[] namespaces = {
        "greg", "greg//chichille", "greg//chichille//petard",
        "greg//alambic", "greg//alambic//canette",
        "greg//chichille/virgule", "greg//chichille/funeste",
        "greg//chichille/virgule//bidouble",
        "greg//chichille/virgule//bi/double",
        "fran", "fran//gast", "fran//gast//gaf",
        "fran//longtar", "fran//longtar//parcmetre"
    };

    private static void createNamespaces(MBeanServer server) throws Exception {
        final LinkedList<String> all = new LinkedList<String>();
        try {
            for (String ns : namespaces)
                all.addFirst(LocalNamespace.add(server,ns));
        } catch (Exception e) {
            removeNamespaces(server,all.toArray(new String[all.size()]));
            throw e;
        }
    }

    // Dummy test that checks that all JMXNamespaces are registered,
    // but are not returned by queryNames("*:*");
    //
    private static void checkRegistration(MBeanServer server)
        throws Exception {
        final Set<ObjectName> handlerNames = new HashSet<ObjectName>(namespaces.length);
        for (String ns : namespaces)
            handlerNames.add(JMXNamespaces.getNamespaceObjectName(ns));
        for (ObjectName nh : handlerNames) // check handler registration
            if (!server.isRegistered(nh))
                throw new InstanceNotFoundException("handler "+nh+
                        " is not registered");

        // global: queryNames("*:*") from top level
        final Set<ObjectName> all1 = server.queryNames(null,null);
        final Set<ObjectName> all2 = server.queryNames(ObjectName.WILDCARD,null);
        if (!all1.equals(all2))
            throw new Exception("queryNames(*:*) != queryNames(null)");
        final Set<ObjectName> common = new HashSet<ObjectName>(all1);
        common.retainAll(handlerNames);

        final Set<ObjectName> ref = new HashSet<ObjectName>();
        for (String ns : namespaces) {
            if (!ns.contains(JMXNamespaces.NAMESPACE_SEPARATOR))
                ref.add(JMXNamespaces.getNamespaceObjectName(ns));
        }
        if (!common.equals(ref)) {
            throw new Exception("some handler names were not returned by " +
                    "wildcard query - only returned: "+common+
                    ", expected: "+ref);
        }

        // for each namespace: queryNames("<namespace>//*:*");
        for (String ns : namespaces) {
            final ObjectName pattern = new ObjectName(ns+
                    JMXNamespaces.NAMESPACE_SEPARATOR+"*:*");
            final Set<ObjectName> all4 =
                    server.queryNames(pattern,null);
            final Set<ObjectName> common4 = new HashSet<ObjectName>(all4);
            common4.retainAll(handlerNames);

            final Set<ObjectName> ref4 = new HashSet<ObjectName>();
            for (String ns2 : namespaces) {
                if (! ns2.startsWith(ns+JMXNamespaces.NAMESPACE_SEPARATOR))
                    continue;
                if (!ns2.substring(ns.length()+
                        JMXNamespaces.NAMESPACE_SEPARATOR.length()).
                        contains(JMXNamespaces.NAMESPACE_SEPARATOR))
                    ref4.add(JMXNamespaces.getNamespaceObjectName(ns2));
            }
            if (!common4.equals(ref4)) {
                throw new Exception("some handler names were not returned by " +
                    "wildcard query on "+pattern+" - only returned: "+common4+
                    ", expected: "+ref4);
            }
        }
    }

    // Make a Map<parent, direct children>
    private static Map<String,Set<String>> makeNsTree(String[] nslist) {
        final Map<String,Set<String>> nsTree =
                new LinkedHashMap<String,Set<String>>(nslist.length);
        for (String ns : nslist) {
            if (nsTree.get(ns) == null)
                nsTree.put(ns,new LinkedHashSet<String>());
            final String[] elts = ns.split(JMXNamespaces.NAMESPACE_SEPARATOR);
            int last = ns.lastIndexOf(JMXNamespaces.NAMESPACE_SEPARATOR);
            if (last<0) continue;
            while (last > 0 && ns.charAt(last-1) == '/') last--;
            final String parent = ns.substring(0,last);
            if (nsTree.get(parent) == null)
                nsTree.put(parent,new LinkedHashSet<String>());
            nsTree.get(parent).add(ns);
        }
        return nsTree;
    }

    private static class Rigolo {
        final static String[] ones = { "a", "e", "i", "o", "u", "y", "ai", "oo",
        "ae", "ey", "ay", "oy", "au", "ou", "eu", "oi", "ei", "ea"};
        final static String[] twos = { "b", "bz", "c", "cz", "ch",
        "ct", "ck", "cs", "d", "ds", "f",  "g", "gh", "h", "j", "k", "l", "m",
        "n", "p", "ps", "q", "r", "s", "sh", "t", "v", "w", "x",
        "z"};
        final static String[] threes = {"rr","tt","pp","ss","dd","ff","ll", "mm", "nn",
        "zz", "cc", "bb"};
        final static String[] fours = {"x", "s", "ght", "cks", "rt", "rts", "ghts", "bs",
          "ts", "gg" };
        final static String[] fives = { "br", "bl", "cr", "cn", "cth", "dr",
        "fr", "fl", "cl", "chr",  "gr", "gl", "kr", "kh", "pr", "pl", "ph",
        "rh", "sr", "tr", "vr"};

        private Random rg = new Random();

        private String next(String[] table) {
            return table[rg.nextInt(table.length)];
        }

        public String nextName(int max) {
            final Random rg = new Random();
            final int nl = 3 + rg.nextInt(max);
            boolean begin = rg.nextBoolean();
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < nl ; j++) {
                if (begin) {
                    sb.append(next(ones));
                } else if (j > 0 && j < nl-1 && rg.nextInt(4)==0) {
                    sb.append(next(threes));
                } else if (j < nl-1 && rg.nextInt(3)==0) {
                    sb.append(next(fives));
                } else {
                    sb.append(next(twos));
                }
                begin = !begin;
            }
            if (!begin && rg.nextInt(2)==0)
                sb.append(next(fours));
            return sb.toString();
        }

        private ObjectName getWombatName(String ns, String domain, String name)
            throws MalformedObjectNameException {
            String d = domain;
            if (ns != null && !ns.equals(""))
                d = ns + JMXNamespaces.NAMESPACE_SEPARATOR + domain;
            return new ObjectName(d+":type=Wombat,name="+name);
        }

        public Set<ObjectName> nextWombats(String ns)
            throws MalformedObjectNameException  {
            final int dcount = 1 + rg.nextInt(5);
            final Set<ObjectName> wombats = new HashSet<ObjectName>();
            for (int i = 0; i < dcount ; i++) {
                final String d = nextName(7);
                final int ncount = 5 + rg.nextInt(20);
                for (int j = 0 ; j<ncount; j++) {
                    final String n = nextName(5);
                    wombats.add(getWombatName(ns,d,n));
                }
            }
            return wombats;
        }
    }

    public static void checkNsQuery(MBeanServer server)
        throws Exception {
        final Map<String,Set<String>> nsTree = makeNsTree(namespaces);
        final Random rg = new Random();
        final Rigolo rigolo = new Rigolo();
        for (String ns : namespaces) {
            final ObjectName name = JMXNamespaces.getNamespaceObjectName(ns);
            final String[] doms =
                    (String[])server.getAttribute(name,"Domains");
            final Set<String> subs = new HashSet<String>();
            for (String d : doms) {
                if (d.endsWith(JMXNamespaces.NAMESPACE_SEPARATOR)) {
                    subs.add(ns+JMXNamespaces.NAMESPACE_SEPARATOR+d.substring(0,
                            d.length()-JMXNamespaces.NAMESPACE_SEPARATOR.length()));
                }
            }

            final Set<String> expectNs = new HashSet<String>(nsTree.get(ns));

            if (! subs.containsAll(expectNs))
                throw new Exception("getDomains didn't return all namespaces: "+
                        "returned="+subs+", expected="+expectNs);
            if (! expectNs.containsAll(subs))
                throw new Exception("getDomains returned additional namespaces: "+
                        "returned="+subs+", expected="+expectNs);

            final Set<ObjectName> nsNames = server.queryNames(
                    new ObjectName(ns+
                    JMXNamespaces.NAMESPACE_SEPARATOR+"*"+
                    JMXNamespaces.NAMESPACE_SEPARATOR+":*"),null);

            final Set<ObjectName> expect =
                    new HashSet<ObjectName>(expectNs.size());
            for (String sub : expectNs) {
                expect.add(JMXNamespaces.getNamespaceObjectName(sub));
            }

            if (! nsNames.containsAll(expect))
                throw new Exception("queryNames didn't return all namespaces: "+
                        "returned="+nsNames+", expected="+expect);
            if (! expect.containsAll(nsNames))
                throw new Exception("getDomains returned additional namespaces: "+
                        "returned="+nsNames+", expected="+expect);

        }
    }

    private static void addWombats(MBeanServer server, Set<ObjectName> names)
        throws Exception {
        for (ObjectName on : names) {
            if (! server.isRegistered(on)) {
                server.createMBean(Wombat.class.getName(),on);
                System.out.println("A new wombat is born: "+on);
            }
        }
    }

    private static void addWombats(MBeanServer server,
             Map<String,Set<ObjectName>> wombats)
        throws Exception {
        for (String ns : wombats.keySet()) {
            addWombats(server,wombats.get(ns));
        }
    }

    private static Map<String,Set<ObjectName>> nameWombats()
        throws Exception {
        final Rigolo rigolo = new Rigolo();
        final Map<String,Set<ObjectName>> wombats =
                new HashMap<String,Set<ObjectName>>(namespaces.length);

        for (String ns : namespaces) {
            wombats.put(ns,rigolo.nextWombats(ns));
        }
        wombats.put("",rigolo.nextWombats(""));
        return wombats;
    }

    private static boolean removeWombats(MBeanServer server,
            Map<String,Set<ObjectName>> wombats) {
        boolean res = true;
        for (String ns : wombats.keySet()) {
            res = res && removeWombats(server,wombats.get(ns));
        }
        return res;
    }

    private static boolean removeWombats(MBeanServer server,
            Set<ObjectName> wombats) {
        boolean res = true;
        for (ObjectName on : wombats) {
            try {
                if (server.isRegistered(on))
                    server.unregisterMBean(on);
            } catch (Exception x) {
                res = false;
                System.out.println("Failed to remove "+on+": "+x);
            }
        }
        return res;
    }

    public static void main(String[] args)
        throws Exception {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Map<String,Set<ObjectName>>  wombats = nameWombats();
        createNamespaces(server);
        try {
            addWombats(server,wombats);
            System.out.println("MBeans: " +server.getMBeanCount());
            System.out.println("Visible: " +server.queryNames(null,null).size());
            System.out.println("Domains: " +Arrays.asList(server.getDomains()));
            checkRegistration(server);
            checkNsQuery(server);
        } finally {
            boolean res = true;
            res = res && removeWombats(server, wombats);
            if (!res)
                throw new RuntimeException("failed to cleanup some namespaces");
        }

    }

    private static boolean removeNamespaces(MBeanServer server) {
        final List<String> l = Arrays.asList(namespaces);
        Collections.reverse(l);
        return removeNamespaces(server, l.toArray(new String[namespaces.length]));
    }

    private static boolean removeNamespaces(MBeanServer server, String[] t) {
        boolean success = true;
        for (String ns : t) {
            try {
                server.unregisterMBean(JMXNamespaces.getNamespaceObjectName(ns));
            } catch (Exception x) {
                System.out.println("failed to remove namespace: "+ ns);
                success = false;
            }
        }
        return success;
    }

}
