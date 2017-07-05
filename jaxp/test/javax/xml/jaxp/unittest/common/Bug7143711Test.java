/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package common;

import java.security.AllPermission;
import java.security.Permission;
import java.security.Permissions;

import javax.xml.XMLConstants;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @bug 7143711
 * @summary Test set use-service-mechanism shall not override what's set by the constructor in secure mode.
 */
public class Bug7143711Test {
    static final String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    static final String SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";

    private static final String DOM_FACTORY_ID = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String SAX_FACTORY_ID = "javax.xml.parsers.SAXParserFactory";

    // impl specific feature
    final String ORACLE_FEATURE_SERVICE_MECHANISM = "http://www.oracle.com/feature/use-service-mechanism";

    @Test
    public void testValidation_SAX_withSM() {
        System.out.println("Validation using SAX Source with security manager:");
        System.setProperty(SAX_FACTORY_ID, "MySAXFactoryImpl");
        Permissions granted = new java.security.Permissions();
        granted.add(new AllPermission());
        System.setSecurityManager(new MySM(granted));

        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // should not allow
            factory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, true);
            if ((boolean) factory.getFeature(ORACLE_FEATURE_SERVICE_MECHANISM)) {
                Assert.fail("should not override in secure mode");
            }
        } catch (Exception e) {
            Assert.fail(e.getMessage());

        } finally {
            System.clearProperty(SAX_FACTORY_ID);
            System.setSecurityManager(null);
        }

        System.setSecurityManager(null);

    }

    @Test(enabled=false) //skipped due to bug JDK-8080097
    public void testTransform_DOM_withSM() {
        System.out.println("Transform using DOM Source;  Security Manager is set:");

        Permissions granted = new java.security.Permissions();
        granted.add(new AllPermission());
        System.setSecurityManager(new MySM(granted));
        System.setProperty(DOM_FACTORY_ID, "MyDOMFactoryImpl");

        try {
            TransformerFactory factory = TransformerFactory.newInstance("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl",
                    TransformerFactory.class.getClassLoader());
            factory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, true);
            if ((boolean) factory.getFeature(ORACLE_FEATURE_SERVICE_MECHANISM)) {
                Assert.fail("should not override in secure mode");
            }

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } finally {
            System.clearProperty(DOM_FACTORY_ID);
            System.setSecurityManager(null);
        }

        System.clearProperty(DOM_FACTORY_ID);
    }

    @Test
    public void testXPath_DOM_withSM() {
        System.out.println("Evaluate DOM Source;  Security Manager is set:");
        Permissions granted = new java.security.Permissions();
        granted.add(new AllPermission());
        System.setSecurityManager(new MySM(granted));
        System.setProperty(DOM_FACTORY_ID, "MyDOMFactoryImpl");

        try {
            XPathFactory xPathFactory = XPathFactory.newInstance("http://java.sun.com/jaxp/xpath/dom",
                    "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl", null);
            xPathFactory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, true);
            if ((boolean) xPathFactory.getFeature(ORACLE_FEATURE_SERVICE_MECHANISM)) {
                Assert.fail("should not override in secure mode");
            }

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } finally {
            System.clearProperty(DOM_FACTORY_ID);
            System.setSecurityManager(null);
        }

        System.clearProperty(DOM_FACTORY_ID);
    }

    @Test
    public void testSM() {
        SecurityManager sm = System.getSecurityManager();
        if (System.getSecurityManager() != null) {
            System.out.println("Security manager not cleared: " + sm.toString());
        } else {
            System.out.println("Security manager cleared: ");
        }
    }

    class MySM extends SecurityManager {
        Permissions granted;

        public MySM(Permissions perms) {
            granted = perms;
        }

        @Override
        public void checkPermission(Permission perm) {
            if (granted.implies(perm)) {
                return;
            }
            super.checkPermission(perm);
        }

    }

}
