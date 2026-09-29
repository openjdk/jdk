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
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static javax.xml.validation.ptests.ValidationTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8080907 8169778
 * @library /javax/xml/jaxp/libs
 * @build jaxp.library.JAXPDataProvider
 * @run junit/othervm javax.xml.validation.ptests.SchemaFactoryTest
 * @summary Class containing the test cases for SchemaFactory
 */
@TestInstance(Lifecycle.PER_CLASS)
public class SchemaFactoryTest {

    @BeforeAll
    public void setup() throws SAXException, IOException, ParserConfigurationException {
        sf = newSchemaFactory();
        assertNotNull(sf);

        ifac = XMLInputFactory.newInstance();

        xsd1 = Files.readAllBytes(Paths.get(XML_DIR + "test.xsd"));
        xsd2 = Files.readAllBytes(Paths.get(XML_DIR + "test1.xsd"));

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        xsdDoc1 = db.parse(newInputStream(xsd1));
        xsdDoc2 = db.parse(newInputStream(xsd2));

        xml = Files.readAllBytes(Paths.get(XML_DIR + "test.xml"));
    }


    public Object[][] getValidateParameters() {
        return new Object[][] { { W3C_XML_SCHEMA_NS_URI, SCHEMA_FACTORY_CLASSNAME, null },
                { W3C_XML_SCHEMA_NS_URI, SCHEMA_FACTORY_CLASSNAME, this.getClass().getClassLoader() } };
    }

    /**
     * Test if newDefaultInstance() method returns an instance
     * of the expected factory.
     */
    @Test
    public void testDefaultInstance() {
        SchemaFactory sf1 = SchemaFactory.newDefaultInstance();
        SchemaFactory sf2 = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
        assertNotSame(sf1, sf2, "same instance returned:");
        assertSame(sf1.getClass(), sf2.getClass(),
                "unexpected class mismatch for newDefaultInstance():");
        assertEquals(DEFAULT_IMPL_CLASS, sf1.getClass().getName());
        assertTrue(sf1.isSchemaLanguageSupported(W3C_XML_SCHEMA_NS_URI),
                   "isSchemaLanguageSupported(W3C_XML_SCHEMA_NS_URI):");
        assertFalse(sf1.isSchemaLanguageSupported(UNRECOGNIZED_NAME),
                   "isSchemaLanguageSupported(UNRECOGNIZED_NAME):");
    }

    /*
     * test for SchemaFactory.newInstance(java.lang.String schemaLanguage,
     * java.lang.String factoryClassName, java.lang.ClassLoader classLoader)
     * factoryClassName points to correct implementation of
     * javax.xml.validation.SchemaFactory , should return newInstance of
     * SchemaFactory
     */
    @ParameterizedTest
    @MethodSource("getValidateParameters")
    public void testNewInstance(String schemaLanguage, String factoryClassName, ClassLoader classLoader) throws SAXException {
        SchemaFactory sf = SchemaFactory.newInstance(schemaLanguage, factoryClassName, classLoader);
        Schema schema = sf.newSchema();
        assertNotNull(schema);
    }

    /*
     * test for SchemaFactory.newInstance(java.lang.String schemaLanguage,
     * java.lang.String factoryClassName, java.lang.ClassLoader classLoader)
     * factoryClassName is null , should throw IllegalArgumentException
     */
    @ParameterizedTest
    @MethodSource("jaxp.library.JAXPDataProvider#newInstanceNeg")
    public void testNewInstanceWithNullFactoryClassName(String factoryClassName, ClassLoader classLoader) {
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI, factoryClassName, classLoader));
    }

    /*
     * test for SchemaFactory.newInstance(java.lang.String schemaLanguage,
     * java.lang.String factoryClassName, java.lang.ClassLoader classLoader)
     * schemaLanguage is null , should throw NPE
     */
    @Test
    public void testNewInstanceWithNullSchemaLanguage() {
        assertThrows(
                NullPointerException.class,
                () -> SchemaFactory.newInstance(null, SCHEMA_FACTORY_CLASSNAME, this.getClass().getClassLoader()));
    }

    /*
     * test for SchemaFactory.newInstance(java.lang.String schemaLanguage,
     * java.lang.String factoryClassName, java.lang.ClassLoader classLoader)
     * schemaLanguage is empty , should throw IllegalArgumentException
     */
    @Test
    public void testNewInstanceWithEmptySchemaLanguage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaFactory.newInstance("", SCHEMA_FACTORY_CLASSNAME, this.getClass().getClassLoader()));
    }


    @Test
    public void testNewSchemaDefault() {
        assertThrows(SAXParseException.class, () -> validate(sf.newSchema()));
    }

    @Test
    public void testNewSchemaWithFile() throws SAXException, IOException {
        validate(sf.newSchema(new File(XML_DIR + "test.xsd")));
    }

    @Test
    public void testNewSchemaWithNullFile() {
        assertThrows(NullPointerException.class, () -> sf.newSchema((File) null));
    }

    public Object[][] getValidSource() throws XMLStreamException {
        return new Object[][] {
                { streamSource(xsd1) },
                { saxSource(xsd1) },
                { domSource(xsdDoc1) },
                { staxStreamSource(xsd1) },
                { staxEventSource(xsd1) } };

    }

    @ParameterizedTest
    @MethodSource("getValidSource")
    public void testNewSchemaWithValidSource(Source schema) throws SAXException, IOException {
        validate(sf.newSchema(schema));
    }

    public static Object[][] getInvalidSource() {
        return new Object[][] {
                { nullStreamSource() },
                { nullSaxSource() } };
    }

    @ParameterizedTest
    @MethodSource("getInvalidSource")
    public void testNewSchemaWithInvalidSource(Source schema) {
        assertThrows(SAXParseException.class, () -> sf.newSchema(schema));
    }

    @Test
    public void testNewSchemaWithNullSource() {
        assertThrows(NullPointerException.class, () -> sf.newSchema((Source) null));
    }

    public Object[][] getValidSources() {
        return new Object[][] {
                { streamSource(xsd1), streamSource(xsd2) },
                { saxSource(xsd1), saxSource(xsd2) },
                { domSource(xsdDoc1), domSource(xsdDoc2) } };
    }

    @ParameterizedTest
    @MethodSource("getValidSources")
    public void testNewSchemaWithValidSourceArray(Source schema1, Source schema2) throws SAXException, IOException {
        validate(sf.newSchema(new Source[] { schema1, schema2 }));
    }

    public Object[][] getInvalidSources() {
        return new Object[][] {
                { streamSource(xsd1), nullStreamSource() },
                { nullStreamSource(), nullStreamSource() },
                { saxSource(xsd1), nullSaxSource() },
                { nullSaxSource(), nullSaxSource() } };
    }

    @ParameterizedTest
    @MethodSource("getInvalidSources")
    public void testNewSchemaWithInvalidSourceArray(Source schema1, Source schema2) {
        assertThrows(SAXParseException.class, () -> sf.newSchema(new Source[] { schema1, schema2 }));
    }

    public Object[][] getNullSources() {
        return new Object[][] {
                { new Source[] { domSource(xsdDoc1), null } },
                { new Source[] { null, null } },
                { null } };

    }

    @ParameterizedTest
    @MethodSource("getNullSources")
    public void testNewSchemaWithNullSourceArray(Source[] schemas) {
        assertThrows(NullPointerException.class, () -> sf.newSchema(schemas));
    }

    @Test
    public void testNewSchemaWithNullUrl() {
        assertThrows(NullPointerException.class, () -> sf.newSchema((URL) null));
    }


    @Test
    public void testErrorHandler() {
        SchemaFactory sf = newSchemaFactory();
        assertNull(sf.getErrorHandler(), "When SchemaFactory is created, initially ErrorHandler should not be set.");

        ErrorHandler handler = new MyErrorHandler();
        sf.setErrorHandler(handler);
        assertSame(handler, sf.getErrorHandler());

        sf.setErrorHandler(null);
        assertNull(sf.getErrorHandler());
    }

    @Test
    public void testGetUnrecognizedProperty() {
        SchemaFactory sf = newSchemaFactory();
        assertThrows(SAXNotRecognizedException.class, () -> sf.getProperty(UNRECOGNIZED_NAME));
    }

    @Test
    public void testSetUnrecognizedProperty() {
        SchemaFactory sf = newSchemaFactory();
        assertThrows(SAXNotRecognizedException.class, () -> sf.setProperty(UNRECOGNIZED_NAME, "test"));
    }

    @Test
    public void testGetNullProperty() {
        SchemaFactory sf = newSchemaFactory();
        assertNotNull(sf);
        assertThrows(NullPointerException.class, () -> sf.getProperty(null));
    }

    @Test
    public void testSetNullProperty() {
        SchemaFactory sf = newSchemaFactory();
        assertNotNull(sf);
        assertThrows(NullPointerException.class, () -> sf.setProperty(null, "test"));
    }

    @Test
    public void testGetUnrecognizedFeature() {
        SchemaFactory sf = newSchemaFactory();
        assertThrows(SAXNotRecognizedException.class, () -> sf.getFeature(UNRECOGNIZED_NAME));

    }

    @Test
    public void testSetUnrecognizedFeature() {
        SchemaFactory sf = newSchemaFactory();
        assertThrows(SAXNotRecognizedException.class, () -> sf.setFeature(UNRECOGNIZED_NAME, true));
    }

    @Test
    public void testGetNullFeature() {
        SchemaFactory sf = newSchemaFactory();
        assertNotNull(sf);
        assertThrows(NullPointerException.class, () -> sf.getFeature(null));
    }

    @Test
    public void testSetNullFeature() {
        SchemaFactory sf = newSchemaFactory();
        assertNotNull(sf);
        assertThrows(NullPointerException.class, () -> sf.setFeature(null, true));
    }

    public static Object[][] getSourceFeature() {
        return new Object[][] {
                { StreamSource.FEATURE },
                { SAXSource.FEATURE },
                { DOMSource.FEATURE },
                { DOMSource.FEATURE } };

    }

    /*
     * Return true for each of the JAXP Source features to indicate that this
     * SchemaFactory supports all of the built-in JAXP Source types.
     */
    @ParameterizedTest
    @MethodSource("getSourceFeature")
    public void testSourceFeatureGet(String sourceFeature) throws Exception {
        assertTrue(newSchemaFactory().getFeature(sourceFeature));
    }

    /*
     * JAXP Source features are read-only because this SchemaFactory always
     * supports all JAXP Source types.
     */
    @ParameterizedTest
    @MethodSource("getSourceFeature")
    public void testSourceFeatureSet(String sourceFeature) {
        assertThrows(
                SAXNotSupportedException.class,
                () -> newSchemaFactory().setFeature(sourceFeature, false));
    }

    @Test
    public void testInvalidSchemaLanguage() {
        final String INVALID_SCHEMA_LANGUAGE = "http://relaxng.org/ns/structure/1.0";
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaFactory.newInstance(INVALID_SCHEMA_LANGUAGE));
    }

    @Test
    public void testNullSchemaLanguage() {
        assertThrows(NullPointerException.class, () -> SchemaFactory.newInstance(null));
    }

    private void validate(Schema schema) throws SAXException, IOException {
        schema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml)));
    }

    private static InputStream newInputStream(byte[] xsd) {
        return new ByteArrayInputStream(xsd);
    }

    private static Source streamSource(byte[] xsd) {
        return new StreamSource(newInputStream(xsd));
    }

    private static Source nullStreamSource() {
        return new StreamSource((InputStream) null);
    }

    private static Source saxSource(byte[] xsd) {
        return new SAXSource(new InputSource(newInputStream(xsd)));
    }

    private static Source nullSaxSource() {
        return new SAXSource(new InputSource((InputStream) null));
    }

    private static Source domSource(Document xsdDoc) {
        return new DOMSource(xsdDoc);
    }

    private Source staxStreamSource(byte[] xsd) throws XMLStreamException {
        return new StAXSource(ifac.createXMLStreamReader(newInputStream(xsd)));
    }

    private Source staxEventSource(byte[] xsd) throws XMLStreamException {
        return new StAXSource(ifac.createXMLEventReader(newInputStream(xsd)));
    }


    private SchemaFactory newSchemaFactory() {
        return SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
    }

    private static final String UNRECOGNIZED_NAME = "http://xml.org/sax/features/namespace-prefixes";

    private static final String DEFAULT_IMPL_CLASS =
        "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory";

    private static final String SCHEMA_FACTORY_CLASSNAME = DEFAULT_IMPL_CLASS;

    private SchemaFactory sf;
    private XMLInputFactory ifac;
    private byte[] xsd1;
    private byte[] xsd2;
    private Document xsdDoc1;
    private Document xsdDoc2;
    private byte[] xml;
}
