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
 * @test DomainCreationTest.java
 * @bug 5072476
 * @summary Test the creation and registration of JMXDomain instances.
 * @author Daniel Fuchs
 * @run clean DomainCreationTest Wombat WombatMBean
 * @run build DomainCreationTest Wombat WombatMBean
 * @run main DomainCreationTest
 */


import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotificationEmitter;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.namespace.JMXDomain;
import javax.management.namespace.MBeanServerSupport;

/**
 * Test simple creation/registration of namespace.
 *
 */
public class DomainCreationTest {
    private static Map<String,Object> emptyEnvMap() {
        return Collections.emptyMap();
    }


    public static class LocalDomainRepository
            extends MBeanServerSupport {
        private final MBeanServer server;
        private final String      domain;


        public class DynamicMBeanProxy implements DynamicMBean {

            private final MBeanServer server;
            private final ObjectName name;

            public DynamicMBeanProxy(MBeanServer s, ObjectName n) {
                this.server = s;
                this.name = n;
            }

            public Object getAttribute(String attribute)
                    throws AttributeNotFoundException,
                    MBeanException, ReflectionException {
                try {
                    return server.getAttribute(name, attribute);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public void setAttribute(Attribute attribute)
                    throws AttributeNotFoundException,
                    InvalidAttributeValueException, MBeanException,
                    ReflectionException {
                try {
                    server.setAttribute(name, attribute);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public AttributeList getAttributes(String[] attributes) {
                try {
                    return server.getAttributes(name, attributes);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public AttributeList setAttributes(AttributeList attributes) {
                try {
                    return server.setAttributes(name, attributes);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public Object invoke(String actionName, Object[] params,
                    String[] signature) throws MBeanException,
                    ReflectionException {
                try {
                    return server.invoke(name, actionName, params, signature);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }

            public MBeanInfo getMBeanInfo() {
                try {
                    return server.getMBeanInfo(name);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }
        }

        public LocalDomainRepository(String domain) {
            this.server = MBeanServerFactory.newMBeanServer();
            this.domain = domain;
        }

        @Override
        protected Set<ObjectName> getNames() {
            try {
            final ObjectName name =
                    ObjectName.getInstance(domain+":*");
            return server.queryNames(name, null);
            } catch (RuntimeException x) {
                throw x;
            } catch (Exception x) {
                throw new RuntimeException(x);
            }
        }

        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
                throws InstanceNotFoundException {
            return new DynamicMBeanProxy(server, name);
        }

        @Override
        public NotificationEmitter
                getNotificationEmitterFor(ObjectName name)
                throws InstanceNotFoundException {
            DynamicMBean mbean = getDynamicMBeanFor(name);
            if (mbean instanceof NotificationEmitter)
                return (NotificationEmitter) mbean;
            return null;
        }

    }

    private static MBeanServer newMBeanServer() {
        return MBeanServerFactory.newMBeanServer();
    }

    public static interface ThingMBean {}
    public static class Thing implements ThingMBean, MBeanRegistration {
        public ObjectName preRegister(MBeanServer server, ObjectName name)
                throws Exception {
            if (name == null) return new ObjectName(":type=Thing");
            else return name;
        }
        public void postRegister(Boolean registrationDone) {
        }

        public void preDeregister() throws Exception {
        }
        public void postDeregister() {
        }
    }

    /**
     * Test that it is possible to create a dummy MBean with a null
     * ObjectName - this is just a sanity check - as there are already
     * other JMX tests that check that.
     *
     * @throws java.lang.Exception
     */
    public static void testCreateWithNull() throws Exception {
        final MBeanServer server = newMBeanServer();
        final ObjectInstance oi = server.registerMBean(new Thing(),null);
        server.unregisterMBean(oi.getObjectName());
        System.out.println("testCreateWithNull PASSED");
    }

    /**
     * Check that we can register a JMXNamespace MBean, using its standard
     * ObjectName.
     * @throws java.lang.Exception
     */
    public static void testGoodObjectName() throws Exception {
        MBeanServer server = newMBeanServer();
        final ObjectName name =
                JMXDomain.getDomainObjectName("gloups");
        final ObjectInstance oi =
                server.registerMBean(new JMXDomain(
                new LocalDomainRepository("gloups")),name);
        System.out.println("Succesfully registered namespace: "+name);
        try {
            if (! name.equals(oi.getObjectName()))
                throw new RuntimeException("testGoodObjectName: TEST failed: " +
                        "namespace registered as: "+
                    oi.getObjectName()+" expected: "+name);
        } finally {
            server.unregisterMBean(oi.getObjectName());
        }
        System.out.println("Succesfully unregistered namespace: "+name);
        System.out.println("testGoodObjectName PASSED");
    }

    /**
     * Check that we cannot register a JMXNamespace MBean, if we don't use
     * its standard ObjectName.
     * @throws java.lang.Exception
     */
    public static void testBadObjectName() throws Exception {
        MBeanServer server = newMBeanServer();
        Throwable exp = null;
        final ObjectName name = new ObjectName("d:k=v");
        try {
            server.registerMBean(new JMXDomain(
                new LocalDomainRepository("d")),name);
            System.out.println("testBadObjectName: " +
                    "Error: MBean registered, no exception thrown.");
        } catch(RuntimeMBeanException x) {
            exp = x.getCause();
        } catch(Exception x) {
            throw new RuntimeException("testBadObjectName: TEST failed: " +
                    "expected RuntimeMBeanException - got "+
                    x);
        }
        if (exp == null)  server.unregisterMBean(name);
        if (exp == null)
            throw new RuntimeException("testBadObjectName: TEST failed: " +
                    "expected IllegalArgumentException - got none");
        if (!(exp instanceof IllegalArgumentException))
            throw new RuntimeException("testBadObjectName: TEST failed: " +
                    "expected IllegalArgumentException - got "+
                    exp.toString(),exp);
        System.out.println("Got expected exception: "+exp);
        System.out.println("testBadObjectName PASSED");
    }

    /**
     * Check that we cannot register a Domain MBean in a domain that already
     * exists.
     *
     * @throws java.lang.Exception
     */
    public static void testBadDomain() throws Exception {
        MBeanServer server = newMBeanServer();
        Throwable exp = null;
        final ObjectName name = new ObjectName("glips:k=v");
        server.registerMBean(new Wombat(),name);

        final ObjectName dname =
                JMXDomain.getDomainObjectName("glips");

        try {
            server.registerMBean(new JMXDomain(
                new LocalDomainRepository("glips")),dname);
            System.out.println("testBadDomain: " +
                    "Error: MBean registered, no exception thrown.");
        } catch(RuntimeOperationsException x) {
            exp = x.getCause();
        } catch(Exception x) {
            throw new RuntimeException("testBadDomain: TEST failed: " +
                    "expected RuntimeOperationsException - got "+
                    x);
        } finally {
            server.unregisterMBean(name);
        }
        if (exp == null)  {
            server.unregisterMBean(dname);
        }
        if (exp == null)
            throw new RuntimeException("testBadDomain: TEST failed: " +
                    "expected IllegalArgumentException - got none");
        if (!(exp instanceof IllegalArgumentException))
            throw new RuntimeException("testBadDomain: TEST failed: " +
                    "expected IllegalArgumentException - got "+
                    exp.toString(),exp);
        System.out.println("Got expected exception: "+exp);
        System.out.println("testBadDomain PASSED");
    }


    public static void main(String... args) throws Exception {
        testCreateWithNull();
        testGoodObjectName();
        testBadObjectName();
        testBadDomain();
    }
}
