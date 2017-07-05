/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.xml.sax.ptests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import static jaxp.library.JAXPTestUtilities.compareWithGold;
import static jaxp.library.JAXPTestUtilities.failCleanup;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;
import static org.xml.sax.ptests.SAXTestConst.CLASS_DIR;
import static org.xml.sax.ptests.SAXTestConst.GOLDEN_DIR;
import static org.xml.sax.ptests.SAXTestConst.XML_DIR;

/**
 * This tests the Attributes interface. Here the startElement() callback of
 * ContentHandler has Attributes as one of its arguments. Attributes
 * pertaining to an element are taken into this argument and various methods
 * of Attributes interfaces are tested. This program uses Namespace processing
 * with namespaces in xml file. This program does not use Validation
 */
public class AttributesNSTest {
    /**
     * Test for Attribute Interface's setter/getter.
     */
    @Test
    public void testcase01() {
        String outputFile = CLASS_DIR + "AttributesNS.out";
        String goldFile = GOLDEN_DIR + "AttributesNSGF.out";
        String xmlFile = XML_DIR + "namespace1.xml";
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            // http://www.saxproject.com/?selected=namespaces namespace-prefixes
            //set to false to supress xmlns attributes
            spf.setFeature("http://xml.org/sax/features/namespace-prefixes",
                                        false);
            SAXParser saxParser = spf.newSAXParser();
            MyAttrCHandler myAttrCHandler = new MyAttrCHandler(outputFile);
            saxParser.parse(new File(xmlFile), myAttrCHandler);
            myAttrCHandler.flushAndClose();
            assertTrue(compareWithGold(goldFile, outputFile));
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        } finally {
            try {
                Path outputPath = Paths.get(outputFile);
                if(Files.exists(outputPath))
                    Files.delete(outputPath);
            } catch (IOException ex) {
                failCleanup(ex, outputFile);
            }
        }
    }
}
