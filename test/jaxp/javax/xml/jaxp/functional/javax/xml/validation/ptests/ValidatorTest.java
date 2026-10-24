/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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
package javax.xml.validation.ptests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static javax.xml.validation.ptests.ValidationTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.validation.ptests.ValidatorTest
 * @summary Class containing the test cases for Validator API
 */
@TestInstance(Lifecycle.PER_CLASS)
public class ValidatorTest {

    @BeforeAll
    public void setup() throws SAXException, IOException, ParserConfigurationException {
        schema = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI).newSchema(new File(XML_DIR + "test.xsd"));

        assertNotNull(schema);

        xmlFileUri = Paths.get(XML_DIR).resolve("test.xml").toUri().toASCIIString();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        xmlDoc = dbf.newDocumentBuilder().parse(xmlFileUri);
    }

    @Test
    public void testValidateStreamSource() throws SAXException, IOException {
        Validator validator = getValidator();
        validator.setErrorHandler(new MyErrorHandler());
        validator.validate(getStreamSource());
    }

    @Test
    public void testValidateNullSource() {
        Validator validator = getValidator();
        assertNotNull(validator);
        assertThrows(NullPointerException.class, () -> validator.validate(null));
    }

    @Test
    public void testErrorHandler() {
        Validator validator = getValidator();
        assertNull(validator.getErrorHandler(), "When Validator is created, initially ErrorHandler should not be set.");

        ErrorHandler mh = new MyErrorHandler();
        validator.setErrorHandler(mh);
        assertSame(mh, validator.getErrorHandler());

    }

    public Object[][] getSourceAndResult() {
        return new Object[][] {
                { getStreamSource(), null },
                { getSAXSource(), getSAXResult() },
                { getDOMSource(), getDOMResult() },
                { getSAXSource(), null },
                { getDOMSource(), null } };
    }

    @ParameterizedTest
    @MethodSource("getSourceAndResult")
    public void testValidateWithResult(Source source, Result result) throws SAXException, IOException {
        Validator validator = getValidator();
        validator.validate(source, result);
    }

    @Test
    public void testGetUnrecognizedProperty() {
        Validator validator = getValidator();
        assertThrows(SAXNotRecognizedException.class, () -> validator.getProperty(UNRECOGNIZED_NAME));

    }

    @Test
    public void testSetUnrecognizedProperty() {
        Validator validator = getValidator();
        assertThrows(SAXNotRecognizedException.class, () -> validator.setProperty(UNRECOGNIZED_NAME, "test"));
    }

    @Test
    public void testGetNullProperty() {
        Validator validator = getValidator();
        assertNotNull(validator);
        assertThrows(NullPointerException.class, () -> validator.getProperty(null));
    }

    @Test
    public void testSetNullProperty() {
        Validator validator = getValidator();
        assertNotNull(validator);
        assertThrows(NullPointerException.class, () -> validator.setProperty(null, "test"));
    }

    @Test
    public void testGetUnrecognizedFeature() {
        Validator validator = getValidator();
        assertThrows(SAXNotRecognizedException.class, () -> validator.getFeature(UNRECOGNIZED_NAME));

    }

    @Test
    public void testSetUnrecognizedFeature() {
        Validator validator = getValidator();
        assertThrows(SAXNotRecognizedException.class, () -> validator.setFeature(UNRECOGNIZED_NAME, true));
    }

    @Test
    public void testGetNullFeature() {
        Validator validator = getValidator();
        assertNotNull(validator);
        assertThrows(NullPointerException.class, () -> validator.getFeature(null));
    }

    @Test
    public void testSetNullFeature() {
        Validator validator = getValidator();
        assertNotNull(validator);
        assertThrows(NullPointerException.class, () -> validator.setFeature(null, true));
    }

    private Validator getValidator() {
        return schema.newValidator();
    }

    private Source getStreamSource() {
        return new StreamSource(xmlFileUri);
    }

    private Source getSAXSource() {
        return new SAXSource(new InputSource(xmlFileUri));
    }

    private Result getSAXResult() {
        SAXResult saxResult = new SAXResult();
        saxResult.setHandler(new DefaultHandler());
        return saxResult;
    }

    private Source getDOMSource() {
        return new DOMSource(xmlDoc);
    }

    private Result getDOMResult() {
        return new DOMResult();
    }

    private static final String UNRECOGNIZED_NAME = "http://xml.org/sax/features/namespace-prefixes";
    private String xmlFileUri;
    private Schema schema;
    private Document xmlDoc;
}
