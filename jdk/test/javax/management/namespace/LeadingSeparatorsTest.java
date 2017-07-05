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
 * @test LeadingSeparatorsTest.java
 * @summary Test that the semantics of a leading // in ObjectName is respected.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean LeadingSeparatorsTest Wombat WombatMBean
 * @compile -XDignore.symbol.file=true  LeadingSeparatorsTest.java
 * @run build LeadingSeparatorsTest Wombat WombatMBean
 * @run main LeadingSeparatorsTest
 */

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXRemoteNamespace;
import javax.management.namespace.JMXNamespace;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Class LeadingSeparatorsTest
 * @author Sun Microsystems, 2005 - All rights reserved.
 */
public class LeadingSeparatorsTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(LeadingSeparatorsTest.class.getName());

    /** Creates a new instance of NullObjectNameTest */
    public LeadingSeparatorsTest() {
    }

    public static interface MyWombatMBean extends WombatMBean {
        public Set<ObjectName> untrue(ObjectName pat) throws Exception;
    }
    public static class MyWombat
            extends Wombat implements MyWombatMBean {
        public MyWombat() throws NotCompliantMBeanException {
            super(MyWombatMBean.class);
        }

        public Set<ObjectName> untrue(ObjectName pat) throws Exception {
            final Set<ObjectName> res=listMatching(pat.withDomain("*"));
            final Set<ObjectName> untrue = new HashSet<ObjectName>();
            for (ObjectName a:res) {
                untrue.add(a.withDomain(pat.getDomain()+"//"+a.getDomain()));
            }
            return untrue;
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
            top.registerMBean(rmiHandler,
                    JMXNamespaces.getNamespaceObjectName("rmi"));
            top.invoke(JMXNamespaces.getNamespaceObjectName("rmi"),
                    "connect", null, null);

            // Create a namespace direct// that points to 'sub' and flows
            // through a direct reference to 'sub'.
            // The namespace direct// will accept createMBean, and registerMBean.
            //
            final JMXNamespace directHandler = new JMXNamespace(sub);
            top.registerMBean(directHandler,
                    JMXNamespaces.getNamespaceObjectName("direct"));

            final ObjectName n1 = new ObjectName("//direct//w:type=Wombat");
            final ObjectName n2 = new ObjectName("direct//w:type=Wombat");
            final ObjectName n3 = new ObjectName("//rmi//w:type=Wombat");
            final ObjectName n4 = new ObjectName("rmi//w:type=Wombat");

            // register wombat using an object name with a leading //
            final Object     obj = new MyWombat();
            // check that returned object name doesn't have the leading //
            assertEquals(n2,top.registerMBean(obj, n1).getObjectName());
            System.out.println(n1+" registered");

            // check that the registered Wombat can be accessed with all its
            // names.
            System.out.println(n2+" mood is: "+top.getAttribute(n2, "Mood"));
            System.out.println(n1+" mood is: "+top.getAttribute(n1, "Mood"));
            System.out.println(n4+" mood is: "+top.getAttribute(n4, "Mood"));
            System.out.println(n3+" mood is: "+top.getAttribute(n3, "Mood"));

            // call listMatching. The result should not contain any prefix.
            final Set<ObjectName> res = (Set<ObjectName>)
                    top.invoke(n3, "listMatching",
                    // remove rmi// from rmi//*:*
                    JMXNamespaces.deepReplaceHeadNamespace(
                    new Object[] {ObjectName.WILDCARD.withDomain("rmi//*")},
                    "rmi", ""), new String[] {ObjectName.class.getName()});

            // add rmi// prefix to all names in res.
            final Set<ObjectName> res1 =
                   JMXNamespaces.deepReplaceHeadNamespace(res, "", "rmi");
            System.out.println("got: "+res1);

            // compute expected result
            final Set<ObjectName> res2 = sub.queryNames(null,null);
            final Set<ObjectName> res3 = new HashSet<ObjectName>();
            for (ObjectName o:res2) {
               res3.add(o.withDomain("rmi//"+o.getDomain()));
            }
            System.out.println("expected: "+res3);
            assertEquals(res1, res3);

            // invoke "untrue(//niark//niark:*)"
            // should return a set were all ObjectNames begin with
            // //niark//niark//
            //
            final Set<ObjectName> res4 = (Set<ObjectName>)
                    top.invoke(n3, "untrue",
                    // remove niark//niark : should remove nothing since
                    // our ObjectName begins with a leading //
                    JMXNamespaces.deepReplaceHeadNamespace(
                    new Object[] {
                       ObjectName.WILDCARD.withDomain("//niark//niark")},
                    "niark//niark", ""),
                    new String[] {ObjectName.class.getName()});
            System.out.println("got: "+res4);

            // add rmi// should add nothing since the returned names have a
            // leading //
            //
            final Set<ObjectName> res5 =
                   JMXNamespaces.deepReplaceHeadNamespace(res4, "", "rmi");
            System.out.println("got#2: "+res5);

            // compute expected result
            final Set<ObjectName> res6 = new HashSet<ObjectName>();
            for (ObjectName o:res2) {
               res6.add(o.withDomain("//niark//niark//"+o.getDomain()));
            }
            System.out.println("expected: "+res6);

            // both res4 and res5 should be equals to the expected result.
            assertEquals(res4, res6);
            assertEquals(res5, res6);

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
