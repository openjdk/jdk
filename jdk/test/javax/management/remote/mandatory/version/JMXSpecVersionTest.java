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
 * @test
 * @bug 6750008
 * @summary Test JMX.getSpecificationVersion
 * @author Eamonn McManus
 */

import java.io.IOException;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.JMX;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerDelegateMBean;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.StandardMBean;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.MBeanServerSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class JMXSpecVersionTest {
    private static String failure;
    private static final Object POISON_PILL = new Object();

    private static class FakeDelegate implements DynamicMBean {
        private final Object specVersion;
        private final DynamicMBean delegate = new StandardMBean(
                new MBeanServerDelegate(), MBeanServerDelegateMBean.class, false);

        FakeDelegate(Object specVersion) {
            this.specVersion = specVersion;
        }

        public Object getAttribute(String attribute)
                throws AttributeNotFoundException, MBeanException,
                ReflectionException {
            if ("SpecificationVersion".equals(attribute)) {
                if (specVersion == POISON_PILL)
                    throw new AttributeNotFoundException(attribute);
                else
                    return specVersion;
            } else
                return delegate.getAttribute(attribute);
        }

        public void setAttribute(Attribute attribute)
                throws AttributeNotFoundException, InvalidAttributeValueException,
                MBeanException, ReflectionException {
            delegate.setAttribute(attribute);
        }

        public AttributeList getAttributes(String[] attributes) {
            AttributeList list = delegate.getAttributes(attributes);
            for (ListIterator<Attribute> it = list.asList().listIterator();
                 it.hasNext(); ) {
                Attribute attr = it.next();
                if (attr.getName().equals("SpecificationVersion")) {
                    it.remove();
                    if (specVersion != POISON_PILL) {
                        attr = new Attribute(attr.getName(), specVersion);
                        it.add(attr);
                    }
                }
            }
            return list;
        }

        public AttributeList setAttributes(AttributeList attributes) {
            return delegate.setAttributes(attributes);
        }

        public Object invoke(String actionName, Object[] params,
                             String[] signature) throws MBeanException,
                                                        ReflectionException {
            return delegate.invoke(actionName, params, signature);
        }

        public MBeanInfo getMBeanInfo() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static class MBeanServerWithVersion extends MBeanServerSupport {
        private final DynamicMBean delegate;

        public MBeanServerWithVersion(Object specVersion) {
            this.delegate = new FakeDelegate(specVersion);
        }

        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
                throws InstanceNotFoundException {
            if (MBeanServerDelegate.DELEGATE_NAME.equals(name))
                return delegate;
            else
                throw new InstanceNotFoundException(name);
        }

        @Override
        protected Set<ObjectName> getNames() {
            return Collections.singleton(MBeanServerDelegate.DELEGATE_NAME);
        }
    }

    private static class EmptyMBeanServer extends MBeanServerSupport {
        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name) throws InstanceNotFoundException {
            throw new InstanceNotFoundException(name);
        }

        @Override
        protected Set<ObjectName> getNames() {
            return Collections.emptySet();
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///");
        JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(
                url, null, mbs);
        cs.start();

        String realVersion = (String) mbs.getAttribute(
                MBeanServerDelegate.DELEGATE_NAME, "SpecificationVersion");
        assertEquals("Reported local version",
                realVersion, JMX.getSpecificationVersion(mbs, null));
        assertEquals("Reported local version >= \"2.0\"",
                true, (realVersion.compareTo("2.0") >= 0));

        JMXConnector cc = JMXConnectorFactory.connect(cs.getAddress());
        MBeanServerConnection mbsc = cc.getMBeanServerConnection();
        assertEquals("Reported remote version",
                realVersion, JMX.getSpecificationVersion(mbsc, null));

        cc.close();
        try {
            String brokenVersion = JMX.getSpecificationVersion(mbsc, null);
            fail("JMX.getSpecificationVersion succeded over closed connection" +
                    " (returned " + brokenVersion + ")");
        } catch (Exception e) {
            assertEquals("Exception for closed connection",
                    IOException.class, e.getClass());
        }

        try {
            String brokenVersion = JMX.getSpecificationVersion(
                    new EmptyMBeanServer(), null);
            fail("JMX.getSpecificationVersion succeded with empty MBean Server" +
                    " (returned " + brokenVersion + ")");
        } catch (Exception e) {
            assertEquals("Exception for empty MBean Server",
                    IOException.class, e.getClass());
        }

        try {
            String brokenVersion = JMX.getSpecificationVersion(null, null);
            fail("JMX.getSpecificationVersion succeded with null MBean Server" +
                    " (returned " + brokenVersion + ")");
        } catch (Exception e) {
            assertEquals("Exception for null MBean Server",
                    IllegalArgumentException.class, e.getClass());
        }

        MBeanServer mbs1_2 = new MBeanServerWithVersion("1.2");
        String version1_2 = JMX.getSpecificationVersion(mbs1_2, null);
        assertEquals("Version for 1.2 MBean Server", "1.2", version1_2);

        // It's completely nutty for an MBean Server to return null as the
        // value of its spec version, and we don't actually say what happens
        // in that case, but in fact we return the null to the caller.
        MBeanServer mbs_null = new MBeanServerWithVersion(null);
        String version_null = JMX.getSpecificationVersion(mbs_null, null);
        assertEquals("Version for MBean Server that declares null spec version",
                null, version_null);

        try {
            MBeanServer mbs1_2_float = new MBeanServerWithVersion(1.2f);
            String version1_2_float =
                    JMX.getSpecificationVersion(mbs1_2_float, null);
            fail("JMX.getSpecificationVersion succeeded with version 1.2f" +
                    " (returned " + version1_2_float + ")");
        } catch (Exception e) {
            assertEquals("Exception for non-string version (1.2f)",
                    IOException.class, e.getClass());
        }

        try {
            MBeanServer mbs_missing = new MBeanServerWithVersion(POISON_PILL);
            String version_missing =
                    JMX.getSpecificationVersion(mbs_missing, null);
            fail("JMX.getSpecificationVersion succeeded with null version" +
                    " (returned " + version_missing + ")");
        } catch (Exception e) {
            assertEquals("Exception for missing version",
                    IOException.class, e.getClass());
        }

        ObjectName wildcardNamespaceName = new ObjectName("foo//*//bar//baz:k=v");
        try {
            String brokenVersion =
                    JMX.getSpecificationVersion(mbsc, wildcardNamespaceName);
            fail("JMX.getSpecificationVersion succeeded with wildcard namespace" +
                    " (returned " + brokenVersion + ")");
        } catch (Exception e) {
            assertEquals("Exception for wildcard namespace",
                    IllegalArgumentException.class, e.getClass());
        }

        String sub1_2namespace = "blibby";
        JMXNamespace sub1_2 = new JMXNamespace(mbs1_2);
        ObjectName sub1_2name =
                JMXNamespaces.getNamespaceObjectName(sub1_2namespace);
        mbs.registerMBean(sub1_2, sub1_2name);
        String sub1_2namespaceHandlerVersion =
                JMX.getSpecificationVersion(mbs, sub1_2name);
        assertEquals("Spec version of namespace handler",
                realVersion, sub1_2namespaceHandlerVersion);
        // The namespace handler is in the top-level namespace so its
        // version should not be 1.2.

        for (String nameInSub : new String[] {"*:*", "d:k=v"}) {
            ObjectName subName = new ObjectName(sub1_2namespace + "//" + nameInSub);
            String subVersion = JMX.getSpecificationVersion(mbs, subName);
            assertEquals("Spec version in 1.2 namespace (" + nameInSub + ")",
                    "1.2", subVersion);
        }

        mbs.unregisterMBean(sub1_2name);
        for (String noSuchNamespace : new String[] {
            sub1_2namespace + "//*:*", sub1_2namespace + "//d:k=v",
        }) {
            try {
                String brokenVersion = JMX.getSpecificationVersion(
                        mbs, new ObjectName(noSuchNamespace));
                fail("JMX.getSpecificationVersion succeeded with missing " +
                        "namespace (" + noSuchNamespace + " -> " +
                        brokenVersion);
            } catch (Exception e) {
                assertEquals("Exception for missing namespace",
                        IOException.class, e.getClass());
            }
        }

        if (failure != null)
            throw new Exception("TEST FAILED: " + failure);
        System.out.println("TEST PASSED");
    }

    private static void assertEquals(String what, Object expect, Object actual) {
        if (equal(expect, actual))
            System.out.println("OK: " + what + ": " + expect);
        else
            fail(what + ": expected " + expect + ", got " + actual);
    }

    private static  boolean equal(Object x, Object y) {
        if (x == null)
            return (y == null);
        else
            return x.equals(y);
    }

    private static void fail(String why) {
        System.out.println("FAILED: " + why);
        failure = why;
    }
}
