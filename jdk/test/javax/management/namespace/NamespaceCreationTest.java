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
 * @test NamespaceCreationTest.java
 * @summary General JMXNamespace test.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean NamespaceCreationTest Wombat WombatMBean
 * @run build NamespaceCreationTest Wombat WombatMBean
 * @run main NamespaceCreationTest
 */


import java.util.Collections;
import java.util.Map;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.RuntimeMBeanException;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;

/**
 * Test simple creation/registration of namespace.
 *
 */
public class NamespaceCreationTest {
    private static Map<String,Object> emptyEnvMap() {
        return Collections.emptyMap();
    }


    public static class LocalNamespace extends JMXNamespace {

        public LocalNamespace() {
            super(MBeanServerFactory.newMBeanServer());
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
                JMXNamespaces.getNamespaceObjectName("gloups");
        final ObjectInstance oi =
                server.registerMBean(new LocalNamespace(),name);
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
            server.registerMBean(new LocalNamespace(),name);
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
     * Check that we cannot register a Wombat MBean in a namespace that does
     * not exists.
     *
     * @throws java.lang.Exception
     */
    public static void testBadNamespace() throws Exception {
        MBeanServer server = newMBeanServer();
        Throwable exp = null;
        final ObjectName name = new ObjectName("glips//d:k=v");

        try {
            server.registerMBean(new Wombat(),name);
            System.out.println("testBadNamespace: " +
                    "Error: MBean registered, no exception thrown.");
        } catch(MBeanRegistrationException x) {
            exp = x.getCause();
        } catch(Exception x) {
            throw new RuntimeException("testBadNamespace: TEST failed: " +
                    "expected MBeanRegistrationException - got "+
                    x);
        }
        if (exp == null)  server.unregisterMBean(name);
        if (exp == null)
            throw new RuntimeException("testBadNamespace: TEST failed: " +
                    "expected IllegalArgumentException - got none");
        if (!(exp instanceof IllegalArgumentException))
            throw new RuntimeException("testBadNamespace: TEST failed: " +
                    "expected IllegalArgumentException - got "+
                    exp.toString(),exp);
        System.out.println("Got expected exception: "+exp);
        System.out.println("testBadNamespace PASSED");
    }

    /**
     * Check that we cannot register a Wombat MBean with a domain name
     * that ends with //. This is reserved for namespaces.
     *
     * @throws java.lang.Exception
     */
    public static void testBadDomain() throws Exception {
        MBeanServer server = newMBeanServer();
        Throwable exp = null;
        final ObjectName name = new ObjectName("glups//:k=v");

        try {
            server.registerMBean(new Wombat(),name);
            System.out.println("testBadDomain: Error: MBean registered, no exception thrown.");
        } catch(RuntimeMBeanException x) {
            exp = x.getCause();
        } catch(Exception x) {
            throw new RuntimeException("testBadDomain: TEST failed: " +
                    "expected RuntimeMBeanException - got "+
                    x);
        }
        if (exp == null)  server.unregisterMBean(name);
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

    /**
     * Check that we cannot register a Wombat MBean as if it were a
     * JMXNamespace. Only JMXNamespace MBeans can have JMX Namespace names.
     * @throws java.lang.Exception
     */
    public static void testBadClassName() throws Exception {
        MBeanServer server = newMBeanServer();
        Throwable exp = null;
        final ObjectName name =
                JMXNamespaces.getNamespaceObjectName("glops");
        try {
            server.registerMBean(new Wombat(),name);
            System.out.println("testBadClassName: " +
                    "Error: MBean registered, no exception thrown.");
        } catch(RuntimeMBeanException x) {
            exp = x.getCause();
        } catch(Exception x) {
            throw new RuntimeException("testBadClassName: TEST failed: " +
                    "expected RuntimeMBeanException - got "+
                    x);
        }
        if (exp == null)  server.unregisterMBean(name);
        if (exp == null)
            throw new RuntimeException("testBadClassName: TEST failed: " +
                    "expected IllegalArgumentException - got none");
        if (!(exp instanceof IllegalArgumentException))
            throw new RuntimeException("testBadClassName: TEST failed: " +
                    "expected IllegalArgumentException - got "+
                    exp.toString(),exp);
        System.out.println("Got expected exception: "+exp);
        System.out.println("testBadClassName PASSED");
    }

    public static void main(String... args) throws Exception {
        testCreateWithNull();
        testGoodObjectName();
        testBadObjectName();
        testBadNamespace();
        testBadDomain();
        testBadClassName();
    }
}
