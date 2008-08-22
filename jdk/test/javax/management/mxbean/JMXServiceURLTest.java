/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test JMXServiceURLTest
 * @bug 6607114 6670375
 * @summary Test that JMXServiceURL works correctly in MXBeans
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.remote.JMXServiceURL;

public class JMXServiceURLTest {
    public static interface UrlMXBean {
        public JMXServiceURL getUrl();
        public void setUrl(JMXServiceURL url);
    }

    public static class UrlImpl implements UrlMXBean {
        volatile JMXServiceURL url;

        public JMXServiceURL getUrl() {
            return url;
        }

        public void setUrl(JMXServiceURL url) {
            this.url = url;
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("a:b=c");
        UrlImpl urlImpl = new UrlImpl();
        mbs.registerMBean(urlImpl, name);

        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://host:8000/noddy");
        UrlMXBean proxy = JMX.newMXBeanProxy(mbs, name, UrlMXBean.class);
        proxy.setUrl(url);
        assertEquals(url, urlImpl.url);
        JMXServiceURL url2 = proxy.getUrl();
        assertEquals(url, url2);

        CompositeData cd = (CompositeData) mbs.getAttribute(name, "Url");
        CompositeType ct = cd.getCompositeType();
        // Make sure it looks like what we expect.  This will have to be
        // changed if ever we add new properties to CompositeType.  In that
        // case this test should also check interoperability between the
        // current version and the new version.
        assertEquals(4, ct.keySet().size());
        Object[][] expectedItems = {
            {"protocol", SimpleType.STRING, "rmi"},
            {"host", SimpleType.STRING, "host"},
            {"port", SimpleType.INTEGER, 8000},
            {"URLPath", SimpleType.STRING, "/noddy"},
        };
        for (Object[] expectedItem : expectedItems) {
            String itemName = (String) expectedItem[0];
            OpenType expectedType = (OpenType) expectedItem[1];
            Object expectedValue = expectedItem[2];
            OpenType actualType = ct.getType(itemName);
            assertEquals(expectedType, actualType);
            Object actualValue = cd.get(itemName);
            assertEquals(expectedValue, actualValue);
        }
    }

    private static void assertEquals(Object expect, Object actual)
            throws Exception {
        if (expect.equals(actual))
            System.out.println("Equal: " + expect);
        else
            throw new Exception("Expected " + expect + ", got " + actual);
    }
}
