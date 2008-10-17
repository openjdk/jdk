/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test
 * @summary Test named MBeanServers.
 * @author Daniel Fuchs
 * @bug 6299231
 * @run clean NamedMBeanServerTest
 * @run build NamedMBeanServerTest
 * @run main NamedMBeanServerTest
 */

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MBeanServerBuilder;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;

/**
 * This test can probably be leveraged in the JCK to test compatibilty
 * of MBeanServerFactory *Name* method implementation.
 * @author dfuchs
 */
public class NamedMBeanServerTest {

    /**
     * One enum value for each way of creating an MBeanServer through the
     * MBeanServerFactory
     */
    public static enum Creator {
        newMBeanServer() {
            public MBeanServer create(String domain) {
                return MBeanServerFactory.newMBeanServer(domain);
            }
            public String test(MBeanServer server, String domain) {
                System.out.println(toString()+"("+domain+")");
                return test(server,
                        MBeanServerFactory.DEFAULT_MBEANSERVER_NAME,
                        domain);
            }
            public MBeanServer[] servers(Config config) {
                return config.ndServers;
            }
            public String[] strings(Config config) {
                return domains(config);
            }
            public String[] domains(Config config) {
                return config.newDomains;
            }
            public String[] names(Config config) {
                return null;
            }
        },
        createMBeanServer() {
            public MBeanServer create(String domain) {
                return MBeanServerFactory.createMBeanServer(domain);
            }
            public String test(MBeanServer server, String domain) {
                System.out.println(toString()+"("+domain+")");
                return test(server,MBeanServerFactory.DEFAULT_MBEANSERVER_NAME,
                        domain);
            }
            public MBeanServer[] servers(Config config) {
                return config.cdServers;
            }
            public String[] strings(Config config) {
                return domains(config);
            }
            public String[] domains(Config config) {
                return config.createDomains;
            }
            public String[] names(Config config) {
                return null;
            }
        },
        newNamedMBeanServer() {
            public MBeanServer create(String name) {
                return MBeanServerFactory.newNamedMBeanServer(name,null);
            }
            public String test(MBeanServer server, String name) {
                System.out.println(toString()+"("+name+",null)");
                return test(server,name,"DefaultDomain");
            }
            public MBeanServer[] servers(Config config) {
                return config.nnServers;
            }
            public String[] strings(Config config) {
                return names(config);
            }
            public String[] domains(Config config) {
                return null;
            }
            public String[] names(Config config) {
                return config.newNames;
            }
        },
        createNamedMBeanServer() {
            public MBeanServer create(String name) {
                return MBeanServerFactory.createNamedMBeanServer(name,null);
            }
            public String test(MBeanServer server, String name) {
                System.out.println(toString()+"("+name+",null)");
                return test(server,name,"DefaultDomain");
            }
            public MBeanServer[] servers(Config config) {
                return config.cnServers;
            }
            public String[] strings(Config config) {
                return names(config);
            }
            public String[] domains(Config config) {
                return null;
            }
            public String[] names(Config config) {
                return config.createNames;
            }
        };

        // creates an MBeanServer using the specified input string.
        // either a domain, (for UNNAMED) or a mbeanServerName (for NAMED)
        public abstract MBeanServer create(String string);

        // test the created server against the string used as input to create
        // it.
        public abstract String test(MBeanServer server, String ref);

        public abstract MBeanServer[] servers(Config config);
        public abstract String[] strings(Config config);
        public abstract String[] names(Config config);
        public abstract String[] domains(Config config);

        public MBeanServer[] servers(Config config, String... refs) {
            final MBeanServer[] servers = servers(config);
            final String[] strings = strings(config);
            final MBeanServer[] res = new MBeanServer[refs.length];
            for (int i=0;i<refs.length;i++) {
                for (int j=0;j<strings.length;j++) {
                    if (strings[j].equals(refs[i]))
                        res[i]=servers[j];
                }
                if (res[i] == null)
                    throw new IllegalArgumentException(refs[i]);
            }
            return res;
        }

        String test(MBeanServer server, String name, String domain) {
            // whether the MBeanServer was created throug a "create" method
            boolean registered = REFERENCED.contains(this);
            if (!server.getDefaultDomain().equals(domain)) {
                return "Unexpected default domain: " +
                        server.getDefaultDomain() + ", should be: " + domain;
            }
            if (!MBeanServerFactory.getMBeanServerName(server).
                    equals(name)) {
                return " Unexpected name: " +
                        MBeanServerFactory.getMBeanServerName(server) +
                        ", should be: " + name;
            }
            List<MBeanServer> found =
                    MBeanServerFactory.findMBeanServerByName(name);
            if (!registered && found.contains(server))
                return " Server "+name+" found by name - " +
                        "but should not be registered";
            if (!registered &&
                    !name.equals(MBeanServerFactory.DEFAULT_MBEANSERVER_NAME) &&
                    found.size()>0)
                return " Server "+name+" had too many matches: " + found.size();
            if (registered && !found.contains(server))
                return " Server "+name+" not found by name - " +
                        "but is registered!";
            if (registered &&
                    !name.equals(MBeanServerFactory.DEFAULT_MBEANSERVER_NAME) &&
                    !(found.size()==1))
                return " Server "+name+" had too many matches: " + found.size();
            return null;
        }

        public static final EnumSet<Creator> NAMED =
                EnumSet.of(createNamedMBeanServer, newNamedMBeanServer);
        public static final EnumSet<Creator> UNNAMED =
                EnumSet.complementOf(NAMED);
        public static final EnumSet<Creator> REFERENCED =
                EnumSet.of(createMBeanServer, createNamedMBeanServer);
        public static final EnumSet<Creator> UNREFERENCED =
                EnumSet.complementOf(REFERENCED);

    }

    public static class Config {
        final String[] newDomains;
        final String[] createDomains;
        final String[] newNames;
        final String[] createNames;
        final MBeanServer[] ndServers;
        final MBeanServer[] cdServers;
        final MBeanServer[] nnServers;
        final MBeanServer[] cnServers;
        final Map<String,Set<MBeanServer>> queries;
        Config(String[][] data) {
            this(data[0],data[1],data[2],data[3]);
        }
        Config(String[] nd, String[] cd, String[] nn, String[] cn) {
            this.newDomains=nd.clone();
            this.createDomains=cd.clone();
            this.newNames=nn.clone();
            this.createNames=cn.clone();
            ndServers = new MBeanServer[nd.length];
            cdServers = new MBeanServer[cd.length];
            nnServers = new MBeanServer[nn.length];
            cnServers = new MBeanServer[cn.length];
            queries = new HashMap<String,Set<MBeanServer>>();
            init();
        }
        private void init() {
            for (Creator c : Creator.values()) fill(c);
            addQuery(null,Creator.createMBeanServer.servers(this));
            addQuery(null,Creator.createNamedMBeanServer.servers(this));
            addQuery("?*",Creator.createMBeanServer.servers(this));
            addQuery("?*",Creator.createNamedMBeanServer.servers(this));
            addQuery("*",Creator.createMBeanServer.servers(this));
            addQuery("*",Creator.createNamedMBeanServer.servers(this));
            addQuery(MBeanServerFactory.DEFAULT_MBEANSERVER_NAME,
                    Creator.createMBeanServer.servers(this));
        }
        private void addQuery(String pattern, MBeanServer... servers) {
            final Set<MBeanServer> s = getQuery(pattern);
            s.addAll(Arrays.asList(servers));
        }
        public Set<MBeanServer> getQuery(String pattern) {
            final Set<MBeanServer> s = queries.get(pattern);
            if (s != null) return s;
            queries.put(pattern,new HashSet<MBeanServer>());
            return queries.get(pattern);
        }
        public Set<String> getPatterns() {
            return queries.keySet();
        }
        private void fill(Creator creator) {
            fill(creator.servers(this),creator.strings(this),creator);
        }
        private void fill(MBeanServer[] dest, String[] src, Creator creator) {
            for(int i=0;i<src.length;i++) dest[i]=creator.create(src[i]);
        }

    }

    static String[] domains(String... str) {
        return str;
    }
    static String[] names(String... str) {
        return str;
    }
    final static Config test1  = new Config(domains("foo1","foo2","foo3"),
            domains("foobar1","foobar2","foobar3","foobar4"),
            names("bar1","bar2"),
            names("barfoo1","barfoo2","barfoo3","batfox1","catfog2","foofoo3"));
    static {
        test1.addQuery("b*",Creator.createNamedMBeanServer.servers(test1,
                "barfoo1","barfoo2","barfoo3","batfox1"));
        test1.addQuery("*arf*",Creator.createNamedMBeanServer.servers(test1,
                "barfoo1","barfoo2","barfoo3"));
        test1.addQuery("*a?f*",Creator.createNamedMBeanServer.servers(test1,
                "barfoo1","barfoo2","barfoo3","batfox1","catfog2"));
        test1.addQuery("",new MBeanServer[0]);
        test1.addQuery("-",new MBeanServer[0]);
        test1.addQuery("def*",Creator.createMBeanServer.servers(test1));
    }

    public static void test(Config config) throws Exception {
        for (Creator c : Creator.values()) {
            final MBeanServer[] s = c.servers(config);
            final String[] ref = c.strings(config);
            for (int i=0;i<s.length;i++) {
                final String msg = c.test(s[i], ref[i]);
                if (msg != null)
                    throw new Exception(String.valueOf(c)+"["+i+"]: "+msg);
            }
        }
        for (String pat : config.getPatterns()) {
            System.out.print("findMBeanServerByName(\""+pat+"\"): [");
            final List<MBeanServer> found =
                    MBeanServerFactory.findMBeanServerByName(pat);
            String sep=" ";
            for (MBeanServer m : found) {
                System.out.print(sep+MBeanServerFactory.getMBeanServerName(m));
                sep=", ";
            }
            System.out.println(" ]");
            final Set<MBeanServer> founds = new HashSet<MBeanServer>();
            founds.addAll(found);
            if (!founds.equals(config.getQuery(pat))) {
                final String msg =
                        "bad result for findMBeanServerByName(\""+
                        pat+"\"): expected "+config.getQuery(pat).size()+", "+
                        "got "+founds.size();
                throw new Exception(msg);
            }
        }
    }

    public static void testexception(Creator c, String name,
            Class<? extends Exception> error) throws Exception {
        Exception failed = null;
        MBeanServer server = null;
        try {
            server = c.create(name);
        } catch (Exception x) {
            failed = x;
        } finally {
            if (Creator.REFERENCED.contains(c) && server!=null) {
                MBeanServerFactory.releaseMBeanServer(server);
            }
        }
        if (failed == null && error != null) {
            throw new Exception("Expected "+error.getName()+
                    " for "+c+"("+name+")");
        }
        if (error != null && !error.isInstance(failed))
            throw new Exception("Expected "+error.getName()+
                    " for "+c+"("+name+"), caught "+failed);
        System.out.println(""+c+"("+name+") PASSED: "+
                (failed==null?"no exception":String.valueOf(failed)));
    }

    private static final Map<String,Class<? extends Exception>> failures =
            new LinkedHashMap<String,Class<? extends Exception>>();
    private static final Map<String,Class<? extends Exception>> legacy =
            new LinkedHashMap<String,Class<? extends Exception>>();
    private static final String[] illegalnames = {
        "", "-", ":", ";", "?", "*", "wom?bat", "ran:tan.plan",
        "rin;tin.tin", "tab*mow"

    };
    private static final String[] legalnames = {
        "wombat", "top.tip", "ran.tan.plan", "rin.tin.tin!"
    };
    private static final String[] nofailures = {
       MBeanServerFactory.DEFAULT_MBEANSERVER_NAME, "default", null
    };
    static {
        for (String s:illegalnames)
            failures.put(s, IllegalArgumentException.class);
        for (String s:nofailures)
            failures.put(s, null);
        legacy.putAll(failures);
        for (String s:legalnames)
            legacy.put(s, UnsupportedOperationException.class);

    }

    public static void test2(Map<String,Class<? extends Exception>> config)
        throws Exception {
        for (Creator c:Creator.NAMED) {
            for (String s:config.keySet()) testexception(c, s, config.get(s));
        }
    }

    public static class LegacyBuilder extends MBeanServerBuilder {

        @Override
        public MBeanServerDelegate newMBeanServerDelegate() {
            return new MBeanServerDelegate() {
                @Override
                public synchronized String getMBeanServerId() {
                    return "gloups";
                }
            };
        }

    }
    public static class LegacyBuilder2 extends MBeanServerBuilder {

        @Override
        public MBeanServerDelegate newMBeanServerDelegate() {
            return new MBeanServerDelegate() {
                @Override
                public synchronized String getMBeanServerId() {
                    return "c'est la vie...";
                }
                @Override
                public synchronized void setMBeanServerName(String name) {
                }

            };
        }

    }

    public static void test3(Map<String,Class<? extends Exception>> config,
            String builderClassName)
        throws Exception {
        final String builder =
                System.getProperty("javax.management.builder.initial");
        System.setProperty("javax.management.builder.initial",
                builderClassName);
        try {
            test2(config);
        } finally {
            if (builder != null)
                System.setProperty("javax.management.builder.initial", builder);
            else
                System.clearProperty("javax.management.builder.initial");
        }
    }

    public static void main(String[] args) throws Exception {
        test(test1);
        test2(failures);
        test3(legacy,LegacyBuilder.class.getName());
        test3(legacy,LegacyBuilder2.class.getName());
    }
}
