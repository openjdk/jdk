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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * This tests the setIgnoringElementWhitespace and setIgnoringComments of
 * DocumentBuilderFactory
 */
public class DocumentBuilderFactory02 {

    /**
     * This testcase tests for the isIgnoringElementContentWhitespace and the
     * setIgnoringElementContentWhitespace. The xml file has all kinds of
     * whitespace,tab and newline characters, it uses the MyNSContentHandler
     * which does not invoke the characters callback when this
     * setIgnoringElementContentWhitespace is set to true.
     */
    @Test
    public void testCheckElementContentWhitespace() {
        try {
            String goldFile = TestUtils.GOLDEN_DIR + FILE_SEP + "dbfactory02GF.out";
            String outputFile = USER_DIR + FILE_SEP + "dbfactory02.out";
            MyErrorHandler eh = MyErrorHandler.newInstance();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(true);
            assertFalse(dbf.isIgnoringElementContentWhitespace());
            dbf.setIgnoringElementContentWhitespace(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(eh);
            Document doc = db.parse(new File(TestUtils.XML_DIR, "DocumentBuilderFactory06.xml"));
            assertFalse(eh.errorOccured);
            DOMSource domSource = new DOMSource(doc);
            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformer = tfactory.newTransformer();
            SAXResult saxResult = new SAXResult();
            saxResult.setHandler(MyCHandler.newInstance(new File(outputFile)));
            transformer.transform(domSource, saxResult);
            assertTrue(compareWithGold(goldFile, outputFile));
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
            failUnexpected(e);
        }
    }
}
