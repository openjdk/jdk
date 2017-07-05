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
package javax.xml.transform.ptests;

import java.io.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import static javax.xml.transform.ptests.TransformerTestConst.CLASS_DIR;
import static javax.xml.transform.ptests.TransformerTestConst.GOLDEN_DIR;
import static javax.xml.transform.ptests.TransformerTestConst.XML_DIR;
import javax.xml.transform.stream.*;
import static jaxp.library.JAXPTestUtilities.compareWithGold;
import static jaxp.library.JAXPTestUtilities.failCleanup;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * Class containing the test cases for TransformerFactory API's
 * getAssociatedStyleSheet method.
 */
public class TransformerFactoryTest {
    /**
     * This test case checks for the getAssociatedStylesheet method
     * of TransformerFactory.
     * The style sheet returned is then copied to an tfactory01.out
     * It will then be verified to see if it matches the golden files
     */
    @Test
    public void tfactory01() {
        String outputFile = CLASS_DIR + "tfactory01.out";
        String goldFile = GOLDEN_DIR + "tfactory01GF.out";
        String xmlFile = XML_DIR + "TransformerFactoryTest.xml";
        String xmlURI = "file:///" + XML_DIR;

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new FileInputStream(xmlFile), xmlURI);
            DOMSource domSource = new DOMSource(doc);
            domSource.setSystemId(xmlURI);
            StreamResult streamResult =new StreamResult(
                new FileOutputStream(outputFile));
            TransformerFactory tFactory = TransformerFactory.newInstance();

            Source s = tFactory.getAssociatedStylesheet(domSource,"screen",
                                           "Modern",null);
            Transformer t = tFactory.newTransformer();
            t.transform(s,streamResult);
            assertTrue(compareWithGold(goldFile, outputFile));
        }catch (IOException | ParserConfigurationException
                | TransformerException | SAXException ex) {
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
