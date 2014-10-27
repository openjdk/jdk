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
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import static javax.xml.transform.ptests.TransformerTestConst.XML_DIR;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Class containing the test cases for SAXParserFactory API
 */
public class TfClearParamTest {
    /**
     * Test xslt file.
     */
    private final String XSL_FILE = XML_DIR + "cities.xsl";

    /**
     * Long parameter name embedded with a URI.
     */
    private final String LONG_PARAM_NAME = "{http://xyz.foo.com/yada/baz.html}foo";

    /**
     * Short parameter name.
     */
    private final String SHORT_PARAM_NAME = "foo";

    /**
     * Parameter value.
     */
    private final String PARAM_VALUE = "xyz";

    /**
     * Obtains transformer's parameter with the same name that set before. Value
     * should be same as set one.
     */
    @Test
    public void clear01() {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setParameter(LONG_PARAM_NAME, PARAM_VALUE);
            assertEquals(transformer.getParameter(LONG_PARAM_NAME).toString(), PARAM_VALUE);
        } catch (TransformerConfigurationException ex) {
            failUnexpected(ex);
        }

    }

    /**
     * Obtains transformer's parameter with the a name that wasn't set before.
     * Null is expected.
     */
    @Test
    public void clear02() {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setParameter(LONG_PARAM_NAME, PARAM_VALUE);
            transformer.clearParameters();
            assertNull(transformer.getParameter(LONG_PARAM_NAME));
        } catch (TransformerConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * Obtains transformer's parameter whose initiated with a stream source with
     * the a name that set before. Value should be same as set one.
     */
    @Test
    public void clear03() {
        try {
            Transformer transformer = TransformerFactory.newInstance().
                    newTransformer(new StreamSource(new File(XSL_FILE)));

            transformer.setParameter(LONG_PARAM_NAME, PARAM_VALUE);
            assertEquals(transformer.getParameter(LONG_PARAM_NAME), PARAM_VALUE);
        } catch (TransformerConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * Obtains transformer's parameter whose initiated with a stream source with
     * the a name that wasn't set before. Null is expected.
     */
    @Test
    public void clear04() {
        try {
            Transformer transformer = TransformerFactory.newInstance().
                    newTransformer(new StreamSource(new File(XSL_FILE)));
            transformer.setParameter(LONG_PARAM_NAME, PARAM_VALUE);
            transformer.clearParameters();
            assertNull(transformer.getParameter(LONG_PARAM_NAME));
        } catch (TransformerConfigurationException ex){
            failUnexpected(ex);
        }

    }

    /**
     * Obtains transformer's parameter whose initiated with a sax source with
     * the a name that set before. Value should be same as set one.
     */
    @Test
    public void clear05() {
        try {
            InputSource is = new InputSource(new FileInputStream(XSL_FILE));
            SAXSource saxSource = new SAXSource();
            saxSource.setInputSource(is);

            Transformer transformer = TransformerFactory.newInstance().newTransformer(saxSource);

            transformer.setParameter(LONG_PARAM_NAME, PARAM_VALUE);
            assertEquals(transformer.getParameter(LONG_PARAM_NAME), PARAM_VALUE);
        } catch (FileNotFoundException | TransformerConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * Obtains transformer's parameter whose initiated with a sax source with
     * the a name that wasn't set before. Null is expected.
     */
    @Test
    public void clear06() {
        try {
            InputSource is = new InputSource(new FileInputStream(XSL_FILE));
            SAXSource saxSource = new SAXSource();
            saxSource.setInputSource(is);

            Transformer transformer = TransformerFactory.newInstance().newTransformer(saxSource);

            transformer.setParameter(LONG_PARAM_NAME, PARAM_VALUE);
            transformer.clearParameters();
            assertNull(transformer.getParameter(LONG_PARAM_NAME));
        } catch (FileNotFoundException | TransformerConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * Obtains transformer's parameter whose initiated with a dom source with
     * the a name that set before. Value should be same as set one.
     */
    @Test
    public void clear07() {
        try {
            TransformerFactory tfactory = TransformerFactory.newInstance();

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(new File(XSL_FILE));
            DOMSource domSource = new DOMSource((Node)document);

            Transformer transformer = tfactory.newTransformer(domSource);

            transformer.setParameter(LONG_PARAM_NAME, PARAM_VALUE);
            assertEquals(transformer.getParameter(LONG_PARAM_NAME), PARAM_VALUE);
        } catch (IOException | ParserConfigurationException
                | TransformerConfigurationException | SAXException ex){
            failUnexpected(ex);
        }
    }

    /**
     * Obtains transformer's parameter whose initiated with a dom source with
     * the a name that wasn't set before. Null is expected.
     */
    @Test
    public void clear08() {
        try {
            TransformerFactory tfactory = TransformerFactory.newInstance();

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(new File(XSL_FILE));
            DOMSource domSource = new DOMSource((Node)document);

            Transformer transformer = tfactory.newTransformer(domSource);
            transformer.setParameter(LONG_PARAM_NAME, PARAM_VALUE);
            transformer.clearParameters();
            assertNull(transformer.getParameter(LONG_PARAM_NAME));
        } catch (IOException | ParserConfigurationException
                | TransformerConfigurationException | SAXException ex){
            failUnexpected(ex);
        }
    }

    /**
     * Obtains transformer's parameter with a short name that set before. Value
     * should be same as set one.
     */
    @Test
    public void clear09() {
        try {
            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformer = tfactory.newTransformer();

            transformer.setParameter(SHORT_PARAM_NAME, PARAM_VALUE);
            assertEquals(transformer.getParameter(SHORT_PARAM_NAME).toString(), PARAM_VALUE);
        } catch (TransformerConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * Obtains transformer's parameter with a short name that set with an integer
     * object before. Value should be same as the set integer object.
     */
    @Test
    public void clear10() {
        try {
            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformer = tfactory.newTransformer();

            int intObject = 5;
            transformer.setParameter(SHORT_PARAM_NAME, intObject);
            assertEquals(transformer.getParameter(SHORT_PARAM_NAME), intObject);
        } catch (TransformerConfigurationException ex){
            failUnexpected(ex);
        }
    }
}
