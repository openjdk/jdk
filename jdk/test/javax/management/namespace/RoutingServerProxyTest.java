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
 * @test RoutingServerProxyTest.java 1.6
 * @summary General RoutingServerProxyTest test.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean RoutingServerProxyTest Wombat WombatMBean
 * @compile -XDignore.symbol.file=true RoutingServerProxyTest.java
 * @run build RoutingServerProxyTest Wombat WombatMBean
 * @run main RoutingServerProxyTest
 */

import com.sun.jmx.namespace.RoutingServerProxy;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.JMX;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationEmitter;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.MBeanServerSupport;

/**
 * Class RoutingServerProxyTest
 *
 * @author Sun Microsystems, Inc.
 */
public class RoutingServerProxyTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(RoutingServerProxyTest.class.getName());

    /**
     * Creates a new instance of RoutingServerProxyTest
     */
    public RoutingServerProxyTest() {
    }

    public static class DynamicWombat extends StandardEmitterMBean {
        DynamicWombat(Wombat w) throws NotCompliantMBeanException {
            super(w,WombatMBean.class,w);
        }

        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
            final ObjectName myname = ((Wombat)getImplementation()).
                    preRegister(server,name);
            return super.preRegister(server,myname);
        }

        @Override
        public void postRegister(Boolean registrationDone) {
            try {
                ((Wombat)getImplementation()).
                    postRegister(registrationDone);
            } finally {
                super.postRegister(registrationDone);
            }
        }

        @Override
        public void preDeregister() throws Exception {
                ((Wombat)getImplementation()).
                    preDeregister();
                super.preDeregister();

        }

        @Override
        public void postDeregister() {
            try {
                ((Wombat)getImplementation()).
                    postDeregister();
            } finally {
                super.postDeregister();
            }
        }
    }

    public static class VirtualWombatHandler
            extends JMXNamespace {

        public static class VirtualWombatRepository
                extends MBeanServerSupport {

            final Map<ObjectName, DynamicMBean> bush;

            VirtualWombatRepository(Map<ObjectName, DynamicMBean> bush) {
                this.bush = bush;
            }

            @Override
            protected Set<ObjectName> getNames() {
                return bush.keySet();
            }

            @Override
            public DynamicMBean getDynamicMBeanFor(ObjectName name)
                    throws InstanceNotFoundException {
                final DynamicMBean mb = bush.get(name);
                if (mb == null) {
                    throw new InstanceNotFoundException(String.valueOf(name));
                }
                return mb;
            }

            @Override
            public NotificationEmitter getNotificationEmitterFor(
                    ObjectName name) throws InstanceNotFoundException {
                DynamicMBean mbean = getDynamicMBeanFor(name);
                if (mbean instanceof NotificationEmitter) {
                    return (NotificationEmitter) mbean;
                }
                return null;
            }
        }
        VirtualWombatRepository bush;

        VirtualWombatHandler(Map<ObjectName, DynamicMBean> bush) {
            this(new VirtualWombatRepository(Collections.synchronizedMap(bush)));
        }

        private VirtualWombatHandler(VirtualWombatRepository repository) {
            super(repository);
            bush = repository;
        }

        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name)
                throws Exception {
            final ObjectName myname = super.preRegister(server, name);
            return myname;
        }

        @Override
        public void postRegister(Boolean registrationDone) {
            if (!registrationDone.booleanValue()) {
                return;
            }
            final MBeanServer me = JMXNamespaces.narrowToNamespace(getMBeanServer(),
                    getObjectName().getDomain());
            for (Map.Entry<ObjectName, DynamicMBean> e : bush.bush.entrySet()) {
                final DynamicMBean obj = e.getValue();
                try {
                    if (obj instanceof MBeanRegistration) {
                        ((MBeanRegistration) obj).preRegister(me, e.getKey());
                    }
                } catch (Exception x) {
                    System.err.println("preRegister failed for " +
                            e.getKey() + ": " + x);
                    bush.bush.remove(e.getKey());
                }
            }
            for (Map.Entry<ObjectName, DynamicMBean> e : bush.bush.entrySet()) {
                final Object obj = e.getValue();
                if (obj instanceof MBeanRegistration) {
                    ((MBeanRegistration) obj).postRegister(registrationDone);
                }
            }
        }

        @Override
        public void preDeregister() throws Exception {
            for (Map.Entry<ObjectName, DynamicMBean> e : bush.bush.entrySet()) {
                final Object obj = e.getValue();
                if (obj instanceof MBeanRegistration) {
                    ((MBeanRegistration) obj).preDeregister();
                }
            }
        }

        @Override
        public void postDeregister() {
            for (Map.Entry<ObjectName, DynamicMBean> e : bush.bush.entrySet()) {
                final Object obj = e.getValue();
                if (obj instanceof MBeanRegistration) {
                    ((MBeanRegistration) obj).postDeregister();
                }
            }
        }
    }

    public static ObjectName getWombatName(String name)
        throws MalformedObjectNameException {
        return ObjectName.getInstance("australian.bush:type=Wombat,name="+name);
    }

    public static ObjectName addDir(String dir, ObjectName name)
        throws MalformedObjectNameException {
        return name.withDomain(
                dir+JMXNamespaces.NAMESPACE_SEPARATOR+ name.getDomain());
    }

    public static void simpleTest()
        throws JMException, IOException {
        final MBeanServer master = MBeanServerFactory.createMBeanServer();
        final MBeanServer agent1 = MBeanServerFactory.createMBeanServer();
        final Wombat w1 = new Wombat();
        final Wombat w2 = new Wombat();
        final Wombat w3 = new Wombat();
        final Map<ObjectName,DynamicMBean> wombats =
                new ConcurrentHashMap<ObjectName,DynamicMBean>();
        wombats.put(getWombatName("LittleWombat"),
                new DynamicWombat(w2));
        wombats.put(getWombatName("BigWombat"),
                new DynamicWombat(w3));
        final Wombat w4 = new Wombat();
        final Wombat w5 = new Wombat();

        final JMXNamespace agent2 =
                new VirtualWombatHandler(wombats);
        agent1.registerMBean(w4,getWombatName("LittleWombat"));
        master.registerMBean(w1,getWombatName("LittleWombat"));
        master.registerMBean(new JMXNamespace(agent1),
                JMXNamespaces.getNamespaceObjectName("south.east"));
        master.registerMBean(agent2,
                JMXNamespaces.getNamespaceObjectName("north"));
        master.registerMBean(w5,addDir("south.east",
                getWombatName("GrandWombat")));

        MBeanServer se = null;

        try {
            se = JMXNamespaces.narrowToNamespace(master,"south.easht");
        } catch (Exception x) {
            System.out.println("Caught expected exception: "+x);
        }
        if (se != null)
            throw new RuntimeException("Expected exception for "+
                    "cd(south.easht)");
        se = JMXNamespaces.narrowToNamespace(master,"south.east");

        MBeanServer nth = JMXNamespaces.narrowToNamespace(master,"north");

        final ObjectName ln = getWombatName("LittleWombat");
        MBeanInfo mb1 = master.getMBeanInfo(ln);
        MBeanInfo mb2 = se.getMBeanInfo(ln);
        MBeanInfo mb3 = nth.getMBeanInfo(ln);

        final WombatMBean grand = JMX.newMBeanProxy(se,
                getWombatName("GrandWombat"),WombatMBean.class);
        final WombatMBean big = JMX.newMBeanProxy(nth,
                getWombatName("BigWombat"),WombatMBean.class);
        grand.getCaption();
        big.getCaption();
        grand.setCaption("I am GrandWombat");
        big.setCaption("I am BigWombat");

        final WombatMBean grand2 =
                JMX.newMBeanProxy(master,addDir("south.east",
                getWombatName("GrandWombat")),WombatMBean.class);
        final WombatMBean big2 =
                JMX.newMBeanProxy(master,addDir("north",
                getWombatName("BigWombat")),WombatMBean.class);
        if (!"I am GrandWombat".equals(grand2.getCaption()))
            throw new RuntimeException("bad caption for GrandWombat"+
                    grand2.getCaption());
        if (!"I am BigWombat".equals(big2.getCaption()))
            throw new RuntimeException("bad caption for BigWombat"+
                    big2.getCaption());


        final Set<ObjectInstance> northWombats =
                nth.queryMBeans(ObjectName.WILDCARD,null);
        final Set<ObjectInstance> seWombats =
                se.queryMBeans(ObjectName.WILDCARD,null);
        if (!northWombats.equals(
                agent2.getSourceServer().queryMBeans(ObjectName.WILDCARD,null))) {
            throw new RuntimeException("Bad Wombat census in northern territory: got "
                    +northWombats+", expected "+
                    agent2.getSourceServer().
                    queryMBeans(ObjectName.WILDCARD,null));
        }
        if (!seWombats.equals(
                agent1.queryMBeans(ObjectName.WILDCARD,null))) {
            throw new RuntimeException("Bad Wombat census in south east: got "
                    +seWombats+", expected "+
                    agent1.
                    queryMBeans(ObjectName.WILDCARD,null));
        }

        final MBeanServer supermaster = MBeanServerFactory.createMBeanServer();
        supermaster.registerMBean(new JMXNamespace(master),
            JMXNamespaces.getNamespaceObjectName("australia"));
        final MBeanServer proxymaster =
                JMXNamespaces.narrowToNamespace(supermaster,"australia");
        final MBeanServer sem =
                JMXNamespaces.narrowToNamespace(proxymaster,"south.east");
        final MBeanServer nthm =
                JMXNamespaces.narrowToNamespace(proxymaster,"north");
        final Set<ObjectInstance> northWombats2 =
                nthm.queryMBeans(ObjectName.WILDCARD,null);
        final Set<ObjectInstance> seWombats2 =
                sem.queryMBeans(ObjectName.WILDCARD,null);
        if (!northWombats2.equals(
                agent2.getSourceServer().queryMBeans(ObjectName.WILDCARD,null))) {
            throw new RuntimeException("Bad Wombat census in " +
                    "Australia // North");
        }
        if (!seWombats2.equals(
                agent1.queryMBeans(ObjectName.WILDCARD,null))) {
            throw new RuntimeException("Bad Wombat census in " +
                    "Australia // South East");
        }
        final WombatMBean grand3 =
                JMX.newMBeanProxy(supermaster,
                addDir("australia//south.east",
                getWombatName("GrandWombat")),WombatMBean.class);
        final WombatMBean big3 =
                JMX.newMBeanProxy(supermaster,addDir("australia//north",
                getWombatName("BigWombat")),WombatMBean.class);
        if (!"I am GrandWombat".equals(grand3.getCaption()))
            throw new RuntimeException("bad caption for " +
                    "australia//south.east//GrandWombat"+
                    grand3.getCaption());
        if (!"I am BigWombat".equals(big3.getCaption()))
            throw new RuntimeException("bad caption for " +
                    "australia//north//BigWombat"+
                    big3.getCaption());
        final WombatMBean grand4 =
                JMX.newMBeanProxy(sem,
                getWombatName("GrandWombat"),WombatMBean.class);
        final WombatMBean big4 =
                JMX.newMBeanProxy(nthm,
                getWombatName("BigWombat"),WombatMBean.class);
        if (!"I am GrandWombat".equals(grand4.getCaption()))
            throw new RuntimeException("bad caption for " +
                    "[australia//south.east//] GrandWombat"+
                    grand4.getCaption());
        if (!"I am BigWombat".equals(big4.getCaption()))
            throw new RuntimeException("bad caption for " +
                    "[australia//north//] BigWombat"+
                    big4.getCaption());

        if (!(nthm instanceof RoutingServerProxy))
            throw new AssertionError("expected RoutingServerProxy for nthm");
        if (!(sem instanceof RoutingServerProxy))
            throw new AssertionError("expected RoutingServerProxy for sem");

        if (!"australia//north".equals((
                (RoutingServerProxy)nthm).getSourceNamespace()))
            throw new RuntimeException("north territory should be in australia");
        if (!"australia//south.east".equals((
                (RoutingServerProxy)sem).getSourceNamespace()))
            throw new RuntimeException("south east territory should be in australia");

    }

    public static  void main(String[] args) {
        try {
            simpleTest();
        } catch (Exception x) {
            System.err.println("SimpleTest failed: "+x);
            throw new RuntimeException(x);
        }
    }

}
