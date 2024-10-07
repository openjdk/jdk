/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package sbd.test;

import java.io.File;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * @test
 * @bug 8326915
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm sbd.test.ExternalRefTest
 * @summary Part of the Secure-By-Default (SBD) project. This test verifies issues
 * and error message improvements related to external references.
 */
public class ExternalRefTest {
    /**
     * @bug 8326915
     * Verifies that SAXParseException rather than NPE is thrown when a validating
     * parser is restricted from processing external references.
     * @throws Exception if the test fails
     */
    @Test
    public void testValidatingParser() throws Exception {
        Assert.assertThrows(SAXParseException.class, () -> validateWithParser());
    }

    private void validateWithParser() throws Exception {
            SAXParserFactory spf = SAXParserFactory.newInstance();

            spf.setNamespaceAware(true);
            spf.setValidating(true);

            SAXParser parser = spf.newSAXParser();
            parser.setProperty("http://java.sun.com/xml/jaxp/properties/schemaLanguage",
                    "http://www.w3.org/2001/XMLSchema");

            parser.setProperty("jdk.xml.jdkcatalog.resolve", "strict");
            File xmlFile = new File(getClass().getResource("ExternalRefTest.xml").getPath());

            parser.parse(xmlFile, new DefaultHandler());
    }
}
