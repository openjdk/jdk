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
 * @test NullObjectNameTest.java
 * @summary Test that null ObjectName are correctly handled in namespaces.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean NullObjectNameTest Wombat WombatMBean
 * @compile -XDignore.symbol.file=true  NullObjectNameTest.java
 * @run build NullObjectNameTest Wombat WombatMBean
 * @run main NullObjectNameTest
 */

import com.sun.jmx.namespace.RoutingServerProxy;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXRemoteNamespace;
import javax.management.namespace.JMXNamespace;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Class NullObjectNameTest
 * @author Sun Microsystems, 2005 - All rights reserved.
 */
public class NullObjectNameTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(NullObjectNameTest.class.getName());

    /** Creates a new instance of NullObjectNameTest */
    public NullObjectNameTest() {
    }

    public static class MyWombat
            extends Wombat {
        public MyWombat() throws NotCompliantMBeanException {
            super();
        }
        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name)
        throws Exception {

            if (name == null)
                name = new ObjectName(":type=Wombat");

            return super.preRegister(server, name);
        }

    }

    static String failure=null;

    public static void testRegister() throws Exception {
        final MBeanServer top = ManagementFactory.getPlatformMBeanServer();
        final MBeanServer sub = MBeanServerFactory.createMBeanServer();
        final JMXServiceURL url = new JMXServiceURL("rmi",null,0);
        final JMXConnectorServer srv =
                JMXConnectorServerFactory.newJMXConnectorServer(url,null,sub);
        srv.start();

        try {

            // Create a namespace rmi// that points to 'sub' and flows through
            // a JMXRemoteNamespace connected to 'srv'
            // The namespace rmi// will accept createMBean, but not registerMBean.
            //
            final JMXRemoteNamespace rmiHandler = JMXRemoteNamespace.
                    newJMXRemoteNamespace(srv.getAddress(),null);
            top.registerMBean(rmiHandler,JMXNamespaces.getNamespaceObjectName("rmi"));
            top.invoke(JMXNamespaces.getNamespaceObjectName("rmi"),
                    "connect", null, null);

            // Create a namespace direct// that points to 'sub' and flows
            // through a direct reference to 'sub'.
            // The namespace direct// will accept createMBean, and registerMBean.
            //
            final JMXNamespace directHandler = new JMXNamespace(sub);
            top.registerMBean(directHandler,
                    JMXNamespaces.getNamespaceObjectName("direct"));

            // Now cd to each of the created namespace.
            //
            MBeanServer cdrmi = JMXNamespaces.narrowToNamespace(top,"rmi");
            MBeanServer cddirect = JMXNamespaces.narrowToNamespace(top,"direct");
            boolean ok = false;

            // Check that calling createMBean with a null ObjectName fails
            // gracefully for namespace rmi// (we can't add rmi// to a null
            // ObjectName.
            //
            // TODO: do this test for all createMBean flavors!
            try {
                final ObjectInstance moi1 =
                        cdrmi.createMBean(MyWombat.class.getName(),null);
                System.out.println(moi1.getObjectName().toString()+
                        ": created through rmi//");
                cddirect.unregisterMBean(moi1.getObjectName());
                failed("expected MBeanRegistrationException");
            } catch (MBeanRegistrationException x) {
                System.out.println("Received expected exception: " + x);
                if (!(x.getCause() instanceof IllegalArgumentException)) {
                    System.err.println("Bad wrapped exception: "+ x.getCause());
                    failed("expected IllegalArgumentException");
                }
            }

            // Check that calling refgisterMBean with a null ObjectName fails
            // gracefully for namespace direct// (we can't add direct// to a null
            // ObjectName.
            //
            try {
                final ObjectInstance moi2 =
                        cddirect.registerMBean(new MyWombat(), (ObjectName)null);
                System.out.println(moi2.getObjectName().toString()+
                        ": created through direct//");
                cdrmi.unregisterMBean(moi2.getObjectName());
                failed("expected MBeanRegistrationException");
            } catch (MBeanRegistrationException x) {
                System.out.println("Received expected exception: " + x);
                if (!(x.getCause() instanceof IllegalArgumentException)) {
                    System.err.println("Bad wrapped exception: "+ x.getCause());
                    failed("expected IllegalArgumentException");
                }
            }

            // Now artificially pretend that 'sub' is contained in a faked//
            // namespace.
            // We should be able to use 'null' in registerMBean/createMBean in
            // this case.
            //
            RoutingServerProxy proxy =
                    new RoutingServerProxy(sub,"","faked",false);
            final ObjectInstance moi3 =
                    proxy.registerMBean(new MyWombat(),null);
            System.out.println(moi3.getObjectName().toString()+
                    ": created through faked//");

            // Now check that null is correctly handled (accepted or rejected)
            // in queries for each of the above configs.
            //
            ObjectName wombat = moi3.getObjectName().withDomain(
                    moi3.getObjectName().getDomain().substring("faked//".length()));
            ObjectInstance moi = new ObjectInstance(wombat,moi3.getClassName());

            System.out.println("Checking queryNames(null,null) with rmi//");
            assertEquals(cdrmi.queryNames(null,null).contains(wombat),true);
            System.out.println("Checking queryNames(null,null) with direct//");
            assertEquals(cddirect.queryNames(null,null).contains(wombat),true);
            System.out.println("Checking queryMBeans(null,null) with rmi//");
            assertEquals(cdrmi.queryMBeans(null,null).contains(moi),true);
            System.out.println("Checking queryMBeans(null,null) with direct//");
            assertEquals(cddirect.queryMBeans(null,null).contains(moi),true);

            try {
                System.out.println("Checking queryNames(null,null) with faked//");
                assertEquals(proxy.queryNames(null,null).
                        contains(moi3.getObjectName()),true);
                failed("queryNames(null,null) should have failed for faked//");
            } catch (IllegalArgumentException x) {
                System.out.println("Received expected exception for faked//: "+x);
            }
            try {
                System.out.println("Checking queryMBeans(null,null) with faked//");
                assertEquals(proxy.queryMBeans(null,null).contains(moi3),true);
                failed("queryMBeans(null,null) should have failed for faked//");
            } catch (IllegalArgumentException x) {
                System.out.println("Received expected exception for faked//: "+x);
            }
            System.out.println("Checking queryNames(faked//*:*,null)");
            assertEquals(proxy.queryNames(new ObjectName("faked//*:*"),null).
                    contains(moi3.getObjectName()),true);

            System.out.println("Checking queryMBeans(faked//*:*,null)");
            assertEquals(proxy.queryMBeans(new ObjectName("faked//*:*"),null).
                    contains(moi3),true);

            proxy.unregisterMBean(moi3.getObjectName());

            // ADD NEW TESTS HERE ^^^

        } finally {
            srv.stop();
        }

        if (failure != null)
            throw new Exception(failure);


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


    private static void failed(String why) {
        failure = why;
        new Throwable("FAILED: " + why).printStackTrace(System.out);
    }

    public static void main(String[] args) throws Exception {
        testRegister();
    }
}
