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

package javax.xml.parsers.ptests;

import static jaxp.library.JAXPTestUtilities.FILE_SEP;
import static jaxp.library.JAXPTestUtilities.USER_DIR;
import static jaxp.library.JAXPTestUtilities.compareWithGold;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * This tests DocumentBuilderFactory for namespace processing and no-namespace
 * processing.
 */
public class DBFNamespaceTest {

    /**
     * Provide input for the cases that supporting namespace or not.
     */
    @DataProvider(name = "input-provider")
    public Object[][] getInput() {
        DocumentBuilderFactory dbf1 = DocumentBuilderFactory.newInstance();
        String outputfile1 = USER_DIR + FILE_SEP + "dbfnstest01.out";
        String goldfile1 = TestUtils.GOLDEN_DIR + FILE_SEP + "dbfnstest01GF.out";

        DocumentBuilderFactory dbf2 = DocumentBuilderFactory.newInstance();
        dbf2.setNamespaceAware(true);
        String outputfile2 = USER_DIR + FILE_SEP + "dbfnstest02.out";
        String goldfile2 = TestUtils.GOLDEN_DIR + FILE_SEP + "dbfnstest02GF.out";
        return new Object[][] { { dbf1, outputfile1, goldfile1 }, { dbf2, outputfile2, goldfile2 } };
    }

    /**
     * Test to parse and transform a document without supporting namespace and
     * with supporting namespace.
     */
    @Test(dataProvider = "input-provider")
    public void testNamespaceTest(DocumentBuilderFactory dbf, String outputfile, String goldfile) {
        try {
            Document doc = dbf.newDocumentBuilder().parse(new File(TestUtils.XML_DIR, "namespace1.xml"));
            dummyTransform(doc, outputfile);
            assertTrue(compareWithGold(goldfile, outputfile));
        } catch (SAXException | IOException | ParserConfigurationException | TransformerFactoryConfigurationError | TransformerException e) {
            failUnexpected(e);
        }
    }

    /**
     * This method transforms a Node without any xsl file and uses SAXResult to
     * invoke the callbacks through a ContentHandler. If namespace processing is
     * not chosen, namespaceURI in callbacks should be an empty string otherwise
     * it should be namespaceURI.
     *
     * @throws TransformerFactoryConfigurationError
     * @throws TransformerException
     * @throws IOException
     */
    private void dummyTransform(Document document, String fileName) throws TransformerFactoryConfigurationError, TransformerException, IOException {
        DOMSource domSource = new DOMSource(document);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        File file = new File(fileName);
        System.out.println("The fileName is " + file.getAbsolutePath());
        transformer.transform(domSource, new SAXResult(MyCHandler.newInstance(file)));
    }

}
