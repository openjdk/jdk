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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import static javax.xml.transform.ptests.TransformerTestConst.CLASS_DIR;
import static javax.xml.transform.ptests.TransformerTestConst.GOLDEN_DIR;
import static javax.xml.transform.ptests.TransformerTestConst.XML_DIR;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import static jaxp.library.JAXPTestUtilities.compareWithGold;
import static jaxp.library.JAXPTestUtilities.failCleanup;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Here Properties Object is populated with required properties.A transformer
 * is created using DOMSource. Using setOutputProperties(), Properties are set
 * for transformer. Then transform(StreamSource, StreamResult) is used for
 * transformation. This tests the setOutputProperties() method.
 */
public class TransformerTest03 {
    /**
     * Test for Transformer.setOutputProperties method.
     */
    @Test
    public void testcase01() {
        String outputFile = CLASS_DIR + "transformer03.out";
        String goldFile = GOLDEN_DIR + "transformer03GF.out";
        String xsltFile = XML_DIR + "cities.xsl";
        String xmlFile = XML_DIR + "cities.xml";

        try (FileInputStream fis = new FileInputStream(xmlFile);
                FileOutputStream fos = new FileOutputStream(outputFile)) {
            Properties properties = new Properties();
            properties.put("method", "xml");
            properties.put("encoding", "UTF-8");
            properties.put("omit-xml-declaration", "yes");
            properties.put("{http://xml.apache.org/xslt}indent-amount", "0");
            properties.put("indent", "no");
            properties.put("standalone", "no");
            properties.put("version", "1.0");
            properties.put("media-type", "text/xml");

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(new File(xsltFile));
            DOMSource domSource = new DOMSource(document);

            Transformer transformer = TransformerFactory.newInstance().
                    newTransformer(domSource);
            StreamSource streamSource = new StreamSource(fis);
            StreamResult streamResult = new StreamResult(fos);

            transformer.setOutputProperties(properties);
            transformer.transform( streamSource, streamResult);
            assertTrue(compareWithGold(goldFile, outputFile));
        } catch (ParserConfigurationException | SAXException
                | IOException | TransformerException ex){
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
