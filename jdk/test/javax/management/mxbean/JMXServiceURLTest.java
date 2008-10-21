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
 * @bug 6607114 6670375 6731410
 * @summary Test that JMXServiceURL works correctly in MXBeans
 * @author Eamonn McManus
 */

import java.io.InvalidObjectException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.Attribute;
import javax.management.JMX;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
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

    private static enum Part {
        PROTOCOL("protocol", SimpleType.STRING, "rmi", 25, "", "a:b", "/", "?", "#"),
        HOST("host", SimpleType.STRING, "a.b.c", 25, "a..b", ".a.b", "a.b."),
        PORT("port", SimpleType.INTEGER, 25, "25", -25),
        PATH("URLPath", SimpleType.STRING, "/tiddly", 25, "tiddly");

        Part(String name, OpenType openType, Object validValue, Object... bogusValues) {
            this.name = name;
            this.openType = openType;
            this.validValue = validValue;
            this.bogusValues = bogusValues;
        }

        final String name;
        final OpenType openType;
        final Object validValue;
        final Object[] bogusValues;
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

        // Now make sure we reject any bogus-looking CompositeData items.
        // We first try every combination of omitted items (items can be
        // null but cannot be omitted), then we try every combination of
        // valid and bogus items.
        final Part[] parts = Part.values();
        final int nParts = parts.length;
        final int maxPartMask = (1 << nParts) - 1;
        // Iterate over all possibilities of included and omitted, except
        // 0, because a CompositeDataSupport must have at least one element,
        // and maxPartMask, where all items are included and the result is valid.
        for (int mask = 1; mask < maxPartMask; mask++) {
            Map<String, Object> cdMap = new HashMap<String, Object>();
            List<String> names = new ArrayList<String>();
            List<OpenType> types = new ArrayList<OpenType>();
            for (int i = 0; i < nParts; i++) {
                if ((mask & (1 << i)) != 0) {
                    Part part = parts[i];
                    cdMap.put(part.name, part.validValue);
                    names.add(part.name);
                    types.add(openTypeForValue(part.validValue));
                }
            }
            String[] nameArray = names.toArray(new String[0]);
            OpenType[] typeArray = types.toArray(new OpenType[0]);
            CompositeType badct = new CompositeType(
                    "bad", "descr", nameArray, nameArray, typeArray);
            CompositeData badcd = new CompositeDataSupport(badct, cdMap);
            checkBad(mbs, name, badcd);
        }

        int nBogus = 1;
        for (Part part : parts)
            nBogus *= (part.bogusValues.length + 1);
        // Iterate over all combinations of bogus values.  We are basically
        // treating each Part as a digit while counting up from 1.  A digit
        // value of 0 stands for the valid value of that Part, and 1 on
        // stand for the bogus values.  Hence an integer where all the digits
        // are 0 would represent a valid CompositeData, which is why we
        // start from 1.
        for (int bogusCount = 1; bogusCount < nBogus; bogusCount++) {
            List<String> names = new ArrayList<String>();
            List<OpenType> types = new ArrayList<OpenType>();
            int x = bogusCount;
            Map<String, Object> cdMap = new HashMap<String, Object>();
            for (Part part : parts) {
                int digitMax = part.bogusValues.length + 1;
                int digit = x % digitMax;
                Object value = (digit == 0) ?
                    part.validValue : part.bogusValues[digit - 1];
                cdMap.put(part.name, value);
                names.add(part.name);
                types.add(openTypeForValue(value));
                x /= digitMax;
            }
            String[] nameArray = names.toArray(new String[0]);
            OpenType[] typeArray = types.toArray(new OpenType[0]);
            CompositeType badct = new CompositeType(
                    "bad", "descr", nameArray, nameArray, typeArray);
            CompositeData badcd = new CompositeDataSupport(badct, cdMap);
            checkBad(mbs, name, badcd);
        }
    }

    private static OpenType openTypeForValue(Object value) {
        if (value instanceof String)
            return SimpleType.STRING;
        else if (value instanceof Integer)
            return SimpleType.INTEGER;
        else
            throw new AssertionError("Value has invalid type: " + value);
    }

    private static void checkBad(
            MBeanServer mbs, ObjectName name, CompositeData badcd)
            throws Exception {
        try {
            mbs.setAttribute(name, new Attribute("Url", badcd));
            throw new Exception("Expected exception for: " + badcd);
        } catch (MBeanException e) {
            if (!(e.getCause() instanceof InvalidObjectException)) {
                throw new Exception(
                        "Wrapped exception should be InvalidObjectException", e);
            }
            System.out.println("OK: rejected " + badcd);
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
