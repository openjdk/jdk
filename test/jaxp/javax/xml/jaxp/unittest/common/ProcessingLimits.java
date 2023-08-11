/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8294858
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm common.ProcessingLimits
 * @summary Verifies the support of processing limits. Use this test to cover
 * tests related to processing limits.
 */
public class ProcessingLimits {
    private static final String XML_NAME_LIMIT = "jdk.xml.maxXMLNameLimit";

    /*
     * Data for tests:
     * xml, name limit
     */
    @DataProvider(name = "xml-data")
    public Object[][] xmlData() throws Exception {
        return new Object[][]{
            {"<foo xmlns='bar'/>", null},
            {"<foo xmlns='bar'/>", "0"},
            {"<?xml version=\"1.1\"?><foo xmlns='bar'/>", null},
            {"<?xml version=\"1.1\"?><foo xmlns='bar'/>", "0"},
        };
    }
    /**
     * bug 8294858
     * Verifies that 0 (no limit) is honored by the parser. According to the bug
     * report, the parser treated 0 literally for namespace names.
     *
     * @param xml the XML content
     * @param limit the limit to be set. "null" means not set.
     *
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "xml-data")
    public void testNameLimit(String xml, String limit)throws Exception
    {
        boolean success = true;
        try {
            if (limit != null) {
                System.setProperty(XML_NAME_LIMIT, limit);
            }
            parse(xml);
        } catch (Exception e) {
            // catch instead of throw so that we can clear the System Property
            success = false;
            System.err.println("Limit is set to " + limit + " failed: " + e.getMessage());
        }
        if (limit != null) {
            System.clearProperty(XML_NAME_LIMIT);
        }
        Assert.assertTrue(success);
    }

    private static void parse(String xml)
        throws Exception
    {
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
        while (reader.hasNext())
            reader.next();
        System.err.println("Parsed successfully");
    }
}
