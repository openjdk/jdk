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
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import static javax.xml.transform.ptests.TransformerTestConst.XML_DIR;
import javax.xml.transform.sax.SAXTransformerFactory;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/*
 * TransformerConfigurationException expected when there is relative URI is used
 * in citiesinclude.xsl file
 */
public class SAXTFactoryTest004 {
    /**
     * Negative test for newTransformerHandler when relative URI is in XML file.
     * @throws TransformerConfigurationException If for some reason the
     * TransformerHandler can not be created.
     */
    @Test(expectedExceptions = TransformerConfigurationException.class)
    public void transformerHandlerTest01() throws TransformerConfigurationException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document document = docBuilder.parse(new File(XML_DIR + "citiesinclude.xsl"));
            DOMSource domSource= new DOMSource(document);
            SAXTransformerFactory saxTFactory
                    = (SAXTransformerFactory)TransformerFactory.newInstance();
            saxTFactory.newTransformerHandler(domSource);
        } catch (ParserConfigurationException | IOException | SAXException ex) {
            failUnexpected(ex);
        }
    }
}
