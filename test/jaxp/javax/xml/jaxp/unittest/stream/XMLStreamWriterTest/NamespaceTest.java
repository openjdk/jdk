/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

package stream.XMLStreamWriterTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamWriterTest.NamespaceTest
 * @summary Test the writing of Namespaces.
 */
public class NamespaceTest {
    /** Factory to reuse. */
    XMLOutputFactory xmlOutputFactory = null;

    /** Writer to reuse. */
    XMLStreamWriter xmlStreamWriter = null;

    /** OutputStream to reuse. */
    ByteArrayOutputStream byteArrayOutputStream = null;

    @BeforeEach
    public void setUp() throws Exception {

        // want a Factory that repairs Namespaces
        xmlOutputFactory = XMLOutputFactory.newInstance();
        xmlOutputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);

        // new OutputStream
        byteArrayOutputStream = new ByteArrayOutputStream();

        // new Writer
        xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(byteArrayOutputStream, "utf-8");
    }

    /**
     * Reset Writer for reuse.
     */
    private void resetWriter() throws Exception {
        // reset the Writer
        byteArrayOutputStream.reset();
        xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(byteArrayOutputStream, "utf-8");
    }

    @Test
    public void testDoubleXmlNs() throws XMLStreamException {
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("foo");
        xmlStreamWriter.writeNamespace("xml", XMLConstants.XML_NS_URI);
        xmlStreamWriter.writeAttribute("xml", XMLConstants.XML_NS_URI, "lang", "ja_JP");
        xmlStreamWriter.writeCharacters("Hello");
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();

        xmlStreamWriter.flush();
        String actualOutput = byteArrayOutputStream.toString();

        // there should be no xmlns:xml
        assertEquals(1, actualOutput.split("xmlns:xml").length, "Expected 0 xmlns:xml, actual output: " + actualOutput);
    }

    @Test
    public void testDuplicateNamespaceURI() throws Exception {

        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("", "localName", "nsUri");
        xmlStreamWriter.writeNamespace("", "nsUri");
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();

        xmlStreamWriter.flush();
        String actualOutput = byteArrayOutputStream.toString();

        // there must be only 1 xmlns=...
        assertEquals(2, actualOutput.split("xmlns").length, "Expected 1 xmlns=, actual output: " + actualOutput);
    }

    // TODO: test with both "" & null
    // NDW: There's no distinction in XML between a "null" namespace URI and one
    // with a URI of "" (the empty string) so I haven't tried to call out any
    // such distinctions.

    // ---------------- Current default namespace is "" ----------------

    private void startDocumentEmptyDefaultNamespace(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {

        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("root");
        xmlStreamWriter.writeDefaultNamespace("");
    }

    private String endDocumentEmptyDefaultNamespace(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {

        xmlStreamWriter.writeEndDocument();

        xmlStreamWriter.flush();

        return byteArrayOutputStream.toString();
    }

    /**
     * Current default namespace is "".
     * writeStartElement("", "localName"", "")
     * requires no fixup
     */
    @Test
    public void testEmptyDefaultEmptyPrefix() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\">" + "<localName>" + "requires no fixup" + "</localName>" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeStartElement("", "localName", "");
        xmlStreamWriter.writeCharacters("requires no fixup");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "".
     *
     * writeStartElement("prefix", "localName", "http://example.org/myURI")
     *
     * requires no fixup, but should generate a declaration for "prefix":
     * xmlns:prefix="http://example.org/myURI" if necessary
     *
     * necessary to generate a declaration in this test case.
     */
    @Test
    public void testEmptyDefaultSpecifiedPrefix() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\">" + "<prefix:localName xmlns:prefix=\"http://example.org/myURI\">"
                + "generate xmlns:prefix" + "</prefix:localName>" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeStartElement("prefix", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeCharacters("generate xmlns:prefix");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "".
     *
     * writeStartElement("prefix", "localName", "http://example.org/myURI")
     *
     * requires no fixup, but should generate a declaration for "prefix":
     * xmlns:prefix="http://example.org/myURI" if necessary
     *
     * not necessary to generate a declaration in this test case.
     */
    @Test
    public void testEmptyDefaultSpecifiedPrefixNoDeclarationGeneration() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\"" + " xmlns:prefix=\"http://example.org/myURI\">" + "<prefix:localName>"
                + "not necessary to generate a declaration" + "</prefix:localName>" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeNamespace("prefix", "http://example.org/myURI");

        xmlStreamWriter.writeStartElement("prefix", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeCharacters("not necessary to generate a declaration");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "".
     *
     * writeStartElement("", "localName", "http://example.org/myURI")
     *
     * should "fixup" the declaration for the default namespace:
     * xmlns="http://example.org/myURI"
     */
    @Test
    public void testEmptyDefaultSpecifiedDefault() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\">" + "<localName xmlns=\"http://example.org/myURI\">" + "generate xmlns"
                + "</localName>" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeStartElement("", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeCharacters("generate xmlns");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "".
     *
     * writeAttribute("", "", "attrName", "value")
     *
     * requires no fixup
     */
    @Test
    public void testEmptyDefaultEmptyPrefixWriteAttribute() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\" attrName=\"value\">" + "requires no fixup" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("", "", "attrName", "value");
        xmlStreamWriter.writeCharacters("requires no fixup");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "".
     *
     * writeAttribute("p", "http://example.org/myURI", "attrName", "value")
     *
     * requires no fixup, but should generate a declaration for "p":
     * xmlns:p="http://example.org/myURI" if necessary
     *
     * necessary to generate a declaration in this test case.
     */
    @Test
    public void testEmptyDefaultSpecifiedPrefixWriteAttribute() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\" xmlns:p=\"http://example.org/myURI\" p:attrName=\"value\">"
                + "generate xmlns:p=\"http://example.org/myURI\"" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("p", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("generate xmlns:p=\"http://example.org/myURI\"");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "".
     *
     * writeAttribute("p", "http://example.org/myURI", "attrName", "value")
     *
     * requires no fixup, but should generate a declaration for "p":
     * xmlns:p="http://example.org/myURI" if necessary
     *
     * not necessary to generate a declaration in this test case.
     */
    @Test
    public void testEmptyDefaultSpecifiedPrefixWriteAttributeNoDeclarationGeneration() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\" xmlns:p=\"http://example.org/myURI\" p:attrName=\"value\">"
                + "not necessary to generate a declaration" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");

        xmlStreamWriter.writeAttribute("p", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("not necessary to generate a declaration");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "".
     *
     * writeAttribute("", "http://example.org/myURI", "attrName", "value")
     *
     * XMLOutputFactory (Javadoc) : "If a writer isRepairingNamespaces it will
     * create a namespace declaration on the current StartElement for any
     * attribute that does not currently have a namespace declaration in scope.
     * If the StartElement has a uri but no prefix specified a prefix will be
     * assigned, if the prefix has not been declared in a parent of the current
     * StartElement it will be declared on the current StartElement. If the
     * defaultNamespace is bound and in scope and the default namespace matches
     * the URI of the attribute or StartElement QName no prefix will be
     * assigned."
     *
     * prefix needs to be assigned for this test case.
     */
    @Test
    public void testEmptyDefaultEmptyPrefixSpecifiedNamespaceURIWriteAttribute() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>"
                + "<root xmlns=\"\" xmlns:{generated prefix}=\"http://example.org/myURI\" {generated prefix}:attrName=\"value\">"
                + "generate xmlns declaration {generated prefix}=\"http://example.org/myURI\"" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("generate xmlns declaration {generated prefix}=\"http://example.org/myURI\"");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);

        // there must be one xmlns=
        assertEquals(2, actualOutput.split("xmlns=").length, "Expected 1 xmlns=, actual output: " + actualOutput);

        // there must be one xmlns:{generated prefix}="..."
        assertEquals(2, actualOutput.split("xmlns:").length, "Expected 1 xmlns:{generated prefix}=\"\", actual output: " + actualOutput);

        // there must be one {generated prefix}:attrName="value"
        assertEquals(2, actualOutput.split(":attrName=\"value\"").length, "Expected 1 {generated prefix}:attrName=\"value\", actual output: "
                + actualOutput);
    }

    /**
     * Current default namespace is "".
     *
     * writeAttribute("", "http://example.org/myURI", "attrName", "value")
     *
     * XMLOutputFactory (Javadoc) : "If a writer isRepairingNamespaces it will
     * create a namespace declaration on the current StartElement for any
     * attribute that does not currently have a namespace declaration in scope.
     * If the StartElement has a uri but no prefix specified a prefix will be
     * assigned, if the prefix has not been declared in a parent of the current
     * StartElement it will be declared on the current StartElement. If the
     * defaultNamespace is bound and in scope and the default namespace matches
     * the URI of the attribute or StartElement QName no prefix will be
     * assigned."
     *
     * no prefix needs to be assigned for this test case
     */
    @Test
    public void testEmptyDefaultEmptyPrefixSpecifiedNamespaceURIWriteAttributeNoPrefixGeneration() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\" xmlns:p=\"http://example.org/myURI\" p:attrName=\"value\">"
                + "no prefix generation" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");

        xmlStreamWriter.writeAttribute("", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("no prefix generation");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    // ---------------- Current default namespace is
    // "http://example.org/uniqueURI" ----------------

    private void startDocumentSpecifiedDefaultNamespace(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {

        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("root");
        xmlStreamWriter.writeDefaultNamespace("http://example.org/uniqueURI");
    }

    private String endDocumentSpecifiedDefaultNamespace(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {

        xmlStreamWriter.writeEndDocument();

        xmlStreamWriter.flush();

        return byteArrayOutputStream.toString();
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeElement("", "localName", "")
     *
     * should "fixup" the declaration for the default namespace: xmlns=""
     */
    @Test
    public void testSpecifiedDefaultEmptyPrefix() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"http://example.org/uniqueURI\">" + "<localName xmlns=\"\">"
                + "generate xmlns=\"\"" + "</localName>" + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeStartElement("", "localName", "");
        xmlStreamWriter.writeCharacters("generate xmlns=\"\"");

        String actualOutput = endDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeStartElement("p", "localName", "http://example.org/myURI")
     *
     * requires no fixup, but should generate a declaration for "p":
     * xmlns:p="http://example.org/myURI" if necessary
     *
     * test case where it is necessary to generate a declaration.
     */
    @Test
    public void testSpecifiedDefaultSpecifiedPrefix() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"http://example.org/uniqueURI\">"
                + "<p:localName xmlns:p=\"http://example.org/myURI\">" + "generate xmlns:p=\"http://example.org/myURI\"" + "</p:localName>" + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeCharacters("generate xmlns:p=\"http://example.org/myURI\"");

        String actualOutput = endDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeStartElement("p", "localName", "http://example.org/myURI")
     *
     * requires no fixup, but should generate a declaration for "p":
     * xmlns:p="http://example.org/myURI" if necessary
     *
     * test case where it is not necessary to generate a declaration.
     */
    @Test
    public void testSpecifiedDefaultSpecifiedPrefixNoPrefixGeneration() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root" + " xmlns=\"http://example.org/uniqueURI\""
                + " xmlns:p=\"http://example.org/myURI\">" + "<p:localName>" + "not necessary to generate a declaration" + "</p:localName>" + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");

        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeCharacters("not necessary to generate a declaration");

        String actualOutput = endDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeStartElement("", "localName", "http://example.org/myURI")
     *
     * should "fixup" the declaration for the default namespace:
     * xmlns="http://example.org/myURI"
     */
    @Test
    public void testSpecifiedDefaultEmptyPrefixSpecifiedNamespaceURI() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"http://example.org/uniqueURI\">"
                + "<localName xmlns=\"http://example.org/myURI\">" + "generate xmlns=\"http://example.org/myURI\"" + "</localName>" + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeStartElement("", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeCharacters("generate xmlns=\"http://example.org/myURI\"");

        String actualOutput = endDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeAttribute("", "", "attrName", "value")
     *
     * requires no fixup
     */
    @Test
    public void testSpecifiedDefaultEmptyPrefixWriteAttribute() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"http://example.org/uniqueURI\" attrName=\"value\">" + "requires no fixup"
                + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("", "", "attrName", "value");
        xmlStreamWriter.writeCharacters("requires no fixup");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeAttribute("p", "http://example.org/myURI", "attrName", "value")
     *
     * requires no fixup, but should generate a declaration for "p":
     * xmlns:p="http://example.org/myURI" if necessary
     *
     * test case where it is necessary to generate a declaration.
     */
    @Test
    public void testSpecifiedDefaultSpecifiedPrefixWriteAttribute() throws Exception { // want
                                                                                       // to
                                                                                       // test

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>"
                + "<root xmlns=\"http://example.org/uniqueURI\" xmlns:p=\"http://example.org/myURI\" p:attrName=\"value\">"
                + "generate xmlns:p=\"http://example.org/myURI\"" + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("p", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("generate xmlns:p=\"http://example.org/myURI\"");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeAttribute("p", "http://example.org/myURI", "attrName", "value")
     *
     * requires no fixup, but should generate a declaration for "p":
     * xmlns:p="http://example.org/myURI" if necessary
     *
     * test case where it is not necessary to generate a declaration.
     */
    @Test
    public void testSpecifiedDefaultSpecifiedPrefixWriteAttributeNoDeclarationGeneration() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>"
                + "<root xmlns=\"http://example.org/uniqueURI\" xmlns:p=\"http://example.org/myURI\" p:attrName=\"value\">"
                + "not necessary to generate a declaration" + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");

        xmlStreamWriter.writeAttribute("p", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("not necessary to generate a declaration");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeAttribute("p", "http://example.org/uniqueURI", "attrName", "value")
     *
     * requires no fixup, but should generate a declaration for "p":
     * xmlns:p="http://example.org/uniqueURI" if necessary. (Note that this will
     * potentially produce two namespace bindings with the same URI, xmlns="xxx"
     * and xmlns:p="xxx", but that's perfectly legal.)
     */
    @Test
    public void testSpecifiedDefaultSpecifiedPrefixSpecifiedNamespaceURIWriteAttribute() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"http://example.org/uniqueURI\" attrName=\"value\">" + "requires no fixup"
                + "</root>";
        final String EXPECTED_OUTPUT_2 = "<?xml version=\"1.0\" ?>"
                + "<root xmlns=\"http://example.org/uniqueURI\" xmlns:p=\"http://example.org/uniqueURI\" p:attrName=\"value\">" + "requires no fixup"
                + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("p", "http://example.org/uniqueURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("requires no fixup");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertTrue(actualOutput.equals(EXPECTED_OUTPUT) || actualOutput.equals(EXPECTED_OUTPUT_2), "Expected: " + EXPECTED_OUTPUT + "\n" + "Actual: "
                + actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeAttribute("", "http://example.org/myURI", "attrName", "value")
     *
     * XMLOutputFactory (Javadoc) : "If a writer isRepairingNamespaces it will
     * create a namespace declaration on the current StartElement for any
     * attribute that does not currently have a namespace declaration in scope.
     * If the StartElement has a uri but no prefix specified a prefix will be
     * assigned, if the prefix has not been declared in a parent of the current
     * StartElement it will be declared on the current StartElement. If the
     * defaultNamespace is bound and in scope and the default namespace matches
     * the URI of the attribute or StartElement QName no prefix will be
     * assigned."
     *
     * test case where prefix needs to be assigned.
     */
    @Test
    public void testSpecifiedDefaultEmptyPrefixSpecifiedNamespaceURIWriteAttribute() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root" + " xmlns=\"http://example.org/uniqueURI\""
                + " xmlns:{generated prefix}=\"http://example.org/myURI\"" + " {generated prefix}:attrName=\"value\">"
                + "generate xmlns declaration {generated prefix}=\"http://example.org/myURI\"" + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("generate xmlns declaration {generated prefix}=\"http://example.org/myURI\"");

        String actualOutput = endDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        // there must be one xmlns=
        assertEquals(2, actualOutput.split("xmlns=").length, "Expected 1 xmlns=, actual output: " + actualOutput);

        // there must be one xmlns:{generated prefix}="..."
        assertEquals(2, actualOutput.split("xmlns:").length, "Expected 1 xmlns:{generated prefix}=\"\", actual output: " + actualOutput);

        // there must be one {generated prefix}:attrName="value"
        assertEquals(2, actualOutput.split(":attrName=\"value\"").length, "Expected 1 {generated prefix}:attrName=\"value\", actual output: "
                + actualOutput);
    }

    /**
     * Current default namespace is "http://example.org/uniqueURI".
     *
     * writeAttribute("", "http://example.org/myURI", "attrName", "value")
     *
     * XMLOutputFactory (Javadoc) : "If a writer isRepairingNamespaces it will
     * create a namespace declaration on the current StartElement for any
     * attribute that does not currently have a namespace declaration in scope.
     * If the StartElement has a uri but no prefix specified a prefix will be
     * assigned, if the prefix has not been declared in a parent of the current
     * StartElement it will be declared on the current StartElement. If the
     * defaultNamespace is bound and in scope and the default namespace matches
     * the URI of the attribute or StartElement QName no prefix will be
     * assigned."
     *
     * test case where no prefix needs to be assigned.
     */
    @Test
    public void testSpecifiedDefaultEmptyPrefixSpecifiedNamespaceURIWriteAttributeNoPrefixGeneration() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root" + " xmlns=\"http://example.org/uniqueURI\""
                + " xmlns:p=\"http://example.org/myURI\"" + " p:attrName=\"value\">" + "no prefix needs to be assigned" + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");

        xmlStreamWriter.writeAttribute("", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeCharacters("no prefix needs to be assigned");

        String actualOutput = endDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    // --------------- Serializations, sequences ---------------

    // Unfortunately, the nature of the StAX API makes it possible for the
    // programmer to generate events that cannot be serialized in XML.

    /**
     * Current default namespace is "".
     *
     * write*("p", "myuri", ...); write*("p", "otheruri", ...);
     *
     * XMLOutputFactory (Javadoc) (If repairing of namespaces is enabled): "If
     * element and/or attribute names in the same start or empty-element tag are
     * bound to different namespace URIs and are using the same prefix then the
     * element or the first occurring attribute retains the original prefix and
     * the following attributes have their prefixes replaced with a new prefix
     * that is bound to the namespace URIs of those attributes."
     */
    @Test
    public void testSamePrefixDifferentURI() throws Exception {

        /*
         * writeAttribute("p", "http://example.org/URI-ONE", "attr1", "value");
         * writeAttribute("p", "http://example.org/URI-TWO", "attr2", "value");
         */
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root" + " xmlns=\"\"" + " xmlns:p=\"http://example.org/URI-ONE\"" + " p:attr1=\"value\">"
                + " xmlns:{generated prefix}=\"http://example.org/URI-TWO\"" + " {generated prefix}:attr2=\"value\">"
                + "remap xmlns declaration {generated prefix}=\"http://example.org/URI-TWO\"" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("p", "http://example.org/URI-ONE", "attr1", "value");
        xmlStreamWriter.writeAttribute("p", "http://example.org/URI-TWO", "attr2", "value");
        xmlStreamWriter.writeCharacters("remap xmlns declaration {generated prefix}=\"http://example.org/URI-TWO\"");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);

        // there must be 1 xmlns=
        assertEquals(2, actualOutput.split("xmlns=").length, "Expected 1 xmlns=, actual output: " + actualOutput);

        // there must be 2 xmlns:
        assertEquals(3, actualOutput.split("xmlns:").length, "Expected 2 xmlns:, actual output: " + actualOutput);

        // there must be 2 :attr
        assertEquals(3, actualOutput.split(":attr").length, "Expected 2 :attr, actual output: " + actualOutput);

        /*
         * writeStartElement("p", "localName", "http://example.org/URI-ONE");
         * writeAttribute("p", "http://example.org/URI-TWO", "attrName", "value");
         */
        final String EXPECTED_OUTPUT_2 = "<?xml version=\"1.0\" ?>" + "<root" + " xmlns=\"\">" + "<p:localName" + " xmlns:p=\"http://example.org/URI-ONE\""
                + " xmlns:{generated prefix}=\"http://example.org/URI-TWO\"" + " {generated prefix}:attrName=\"value\">" + "</p:localName>" + "</root>";

        // reset to known state
        resetWriter();
        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/URI-ONE");
        xmlStreamWriter.writeAttribute("p", "http://example.org/URI-TWO", "attrName", "value");

        actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);

        // there must be 1 xmlns=
        assertEquals(2, actualOutput.split("xmlns=").length, "Expected 1 xmlns=, actual output: " + actualOutput);

        // there must be 2 xmlns:
        assertEquals(3, actualOutput.split("xmlns:").length, "Expected 2 xmlns:, actual output: " + actualOutput);

        // there must be 2 p:localName
        assertEquals(3, actualOutput.split("p:localName").length, "Expected 2 p:localName, actual output: " + actualOutput);

        // there must be 1 :attrName
        assertEquals(2, actualOutput.split(":attrName").length, "Expected 1 :attrName, actual output: " + actualOutput);

        /*
         * writeNamespace("p", "http://example.org/URI-ONE");
         * writeAttribute("p", "http://example.org/URI-TWO", "attrName", "value");
         */
        final String EXPECTED_OUTPUT_3 = "<?xml version=\"1.0\" ?>" + "<root" + " xmlns=\"\"" + " xmlns:p=\"http://example.org/URI-ONE\""
                + " xmlns:{generated prefix}=\"http://example.org/URI-TWO\"" + " {generated prefix}:attrName=\"value\">" + "</root>";

        // reset to known state
        resetWriter();
        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeNamespace("p", "http://example.org/URI-ONE");
        xmlStreamWriter.writeAttribute("p", "http://example.org/URI-TWO", "attrName", "value");

        actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);

        // there must be 1 xmlns=
        assertEquals(2, actualOutput.split("xmlns=").length, "Expected 1 xmlns=, actual output: " + actualOutput);

        // there must be 2 xmlns:
        assertEquals(3, actualOutput.split("xmlns:").length, "Expected 2 xmlns:, actual output: " + actualOutput);

        // there must be 1 :attrName
        assertEquals(2, actualOutput.split(":attrName").length, "Expected a :attrName, actual output: " + actualOutput);

        /*
         * writeNamespace("xmlns", ""); writeStartElement("", "localName",
         * "http://example.org/URI-TWO");
         */
        final String EXPECTED_OUTPUT_4 = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\">" + "<localName xmlns=\"http://example.org/URI-TWO\">"
                + "xmlns declaration =\"http://example.org/URI-TWO\"" + "</localName" + "</root>";

        // reset to known state
        resetWriter();
        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        // writeNamespace("xmlns", ""); already done by
        // startDocumentEmptyDefaultNamespace above
        xmlStreamWriter.writeStartElement("", "localName", "http://example.org/URI-TWO");
        xmlStreamWriter.writeCharacters("remap xmlns declaration {generated prefix}=\"http://example.org/URI-TWO\"");

        actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);

        // there must be 2 xmlns=
        assertEquals(3, actualOutput.split("xmlns=").length, "Expected 2 xmlns=, actual output: " + actualOutput);

        // there must be 0 xmlns:
        assertEquals(1, actualOutput.split("xmlns:").length, "Expected 0 xmlns:, actual output: " + actualOutput);

        // there must be 0 :localName
        assertEquals(1, actualOutput.split(":localName").length, "Expected 0 :localName, actual output: " + actualOutput);
    }

    // ---------------- Misc ----------------

    /**
     * The one case where you don't have to worry about fixup is on attributes
     * that do not have a prefix. Irrespective of the current namespace
     * bindings,
     *
     * writeAttribute("", "", "attrName", "value")
     *
     * is always correct and never requires fixup.
     */
    @Test
    public void testEmptyDefaultEmptyPrefixEmptyNamespaceURIWriteAttribute() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"\" attrName=\"value\">" + "never requires fixup" + "</root>";

        startDocumentEmptyDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("", "", "attrName", "value");
        xmlStreamWriter.writeCharacters("never requires fixup");

        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testSpecifiedDefaultEmptyPrefixEmptyNamespaceURIWriteAttribute() throws Exception {

        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"http://example.org/uniqueURI\" attrName=\"value\">" + "never requires fixup"
                + "</root>";

        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);

        xmlStreamWriter.writeAttribute("", "", "attrName", "value");
        xmlStreamWriter.writeCharacters("never requires fixup");

        String actualOutput = endDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /*--------------- Negative tests with isRepairingNamespaces as FALSE ---------------------- */

    private void setUpForNoRepair() throws XMLStreamException {
        xmlOutputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.FALSE);
        xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(byteArrayOutputStream);
    }

    /*
     * Tries to assign default namespace to empty URI and again to a different
     * uri in element and attribute. Expects XMLStreamException .
     * writeNamespace("",""); writeAttribute("", "http://example.org/myURI",
     * "attrName", "value");
     */
    @Test
    public void testEmptyDefaultEmptyPrefixSpecifiedURIWriteAttributeNoRepair() throws XMLStreamException {
        setUpForNoRepair();
        startDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertThrows(
                XMLStreamException.class,
                () -> xmlStreamWriter.writeAttribute("", "http://example.org/myURI", "attrName", "value"));
    }

    /*
     * Tries to assign default namespace to different uris in element and
     * attribute and expects XMLStreamException.
     * writeNamespace("","http://example.org/uniqueURI"); writeAttribute("",
     * "http://example.org/myURI", "attrName", "value");
     */
    @Test
    public void testSpecifiedDefaultEmptyPrefixSpecifiedURIWriteAttributeNoRepair() throws XMLStreamException {
        setUpForNoRepair();
        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertThrows(
                XMLStreamException.class,
                () -> xmlStreamWriter.writeAttribute("", "http://example.org/uniqueURI", "attrName", "value"));
    }

    /*
     * Tries to assign default namespace to same uri twice in element and
     * attribute and expects XMLStreamException.
     * writeNamespace("","http://example.org/uniqueURI"); writeAttribute("",
     * "http://example.org/uniqueURI", "attrName", "value");
     */
    @Test
    public void testSpecifiedDefaultEmptyPrefixSpecifiedDifferentURIWriteAttributeNoRepair() throws XMLStreamException {
        setUpForNoRepair();
        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertThrows(
                XMLStreamException.class,
                () -> xmlStreamWriter.writeAttribute("", "http://example.org/myURI", "attrName", "value"));
    }

    /*
     * Tries to assign prefix 'p' to different uris to attributes of the same
     * element and expects XMLStreamException. writeAttribute("p",
     * "http://example.org/URI-ONE", "attr1", "value"); writeAttribute("p",
     * "http://example.org/URI-TWO", "attr2", "value");
     */
    @Test
    public void testSamePrefixDiffrentURIWriteAttributeNoRepair() throws XMLStreamException {
        setUpForNoRepair();
        startDocumentEmptyDefaultNamespace(xmlStreamWriter);
        xmlStreamWriter.writeAttribute("p", "http://example.org/URI-ONE", "attr1", "value");
        assertThrows(
                XMLStreamException.class,
                () -> xmlStreamWriter.writeAttribute("p", "http://example.org/URI-TWO", "attr2", "value"));
    }

    /*
     * Tries to assign prefix 'p' to different uris in element and attribute and
     * expects XMLStreamException.
     * writeStartElement("p","localName","http://example.org/URI-ONE")
     * writeAttribute("p", "http://example.org/URI-TWO", "attrName", "value")
     */
    @Test
    public void testSamePrefixDiffrentURIWriteElemAndWriteAttributeNoRepair() throws XMLStreamException {
        setUpForNoRepair();
        startDocumentEmptyDefaultNamespace(xmlStreamWriter);
        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/URI-ONE");
        assertThrows(
                XMLStreamException.class,
                () -> xmlStreamWriter.writeAttribute("p", "http://example.org/URI-TWO", "attrName", "value"));
    }

    /*
     * Tries to write following and expects a StreamException. <root
     * xmlns=""http://example.org/uniqueURI"" xmlns=""http://example.org/myURI""
     * />
     */
    @Test
    public void testDefaultNamespaceDiffrentURIWriteElementNoRepair() throws XMLStreamException {
        setUpForNoRepair();
        startDocumentSpecifiedDefaultNamespace(xmlStreamWriter);
        assertThrows(
                XMLStreamException.class,
                () -> xmlStreamWriter.writeNamespace("", "http://example.org/myURI"));
    }

    /*--------------------------------------------------------------------------
     Miscelleneous tests for writeStartElement() & writeAttribute() methods
     in case of NOREPAIR
     --------------------------------------------------------------------------*/

    private void startDocument(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("root");
    }

    @Test
    public void testSpecifiedPrefixSpecifiedURIWriteElementNoRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root>" + "<p:localName></p:localName>" + "</root>";

        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testSpecifiedPrefixSpecifiedURIWriteAttributeNoRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root p:attrName=\"value\">" + "</root>";

        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.writeAttribute("p", "http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testSpecifiedPrefixSpecifiedURISpecifiedNamespcaeWriteElementNoRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root>" + "<p:localName xmlns:p=\"http://example.org/myURI\"></p:localName>" + "</root>";

        setUpForNoRepair();
        startDocument(xmlStreamWriter);

        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /*
     * writeStartElement("p","localName", "http://example.org/myURI")
     * writeNamespace("p","http://example.org/uniqueURI") This sequence of calls
     * should generate an error as prefix 'p' is binded to different namespace
     * URIs in same namespace context and repairing is disabled.
     */

    @Test
    public void testSpecifiedPrefixSpecifiedURISpecifiedDifferentNamespcaeWriteElementNoRepair() throws XMLStreamException {
        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/myURI");
        assertThrows(
                XMLStreamException.class,
                () -> xmlStreamWriter.writeNamespace("p", "http://example.org/uniqueURI"));
    }

    @Test
    public void testEmptyPrefixEmptyURIWriteAttributeNoRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root>" + "<localName attrName=\"value\"></localName>" + "</root>";
        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.writeStartElement("localName");
        xmlStreamWriter.writeAttribute("", "", "attrName", "value");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testEmptyPrefixNullURIWriteAttributeNoRepair() throws XMLStreamException {
        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.writeStartElement("localName");
        assertThrows(
                XMLStreamException.class,
                () -> xmlStreamWriter.writeAttribute(null, null, "attrName", "value"));
    }

    @Test
    public void testDoubleXmlNsNoRepair() throws XMLStreamException {
        // reset to known state
        setUpForNoRepair();

        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("foo");
        xmlStreamWriter.writeNamespace("xml", XMLConstants.XML_NS_URI);
        xmlStreamWriter.writeAttribute("xml", XMLConstants.XML_NS_URI, "lang", "ja_JP");
        xmlStreamWriter.writeCharacters("Hello");
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();

        xmlStreamWriter.flush();
        String actualOutput = byteArrayOutputStream.toString();
        // there should be no xmlns:xml
        assertEquals(1, actualOutput.split("xmlns:xml").length, "Expected 0 xmlns:xml, actual output: " + actualOutput);
    }

    @Test
    public void testSpecifiedURIWriteAttributeNoRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root>" + "<p:localName p:attrName=\"value\"></p:localName>" + "</root>";

        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeAttribute("http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testSpecifiedURIWriteAttributeWithRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root>"
                + "<p:localName xmlns:p=\"http://example.org/myURI\" p:attrName=\"value\"></p:localName>" + "</root>";

        startDocument(xmlStreamWriter);
        xmlStreamWriter.writeStartElement("p", "localName", "http://example.org/myURI");
        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");
        xmlStreamWriter.writeAttribute("http://example.org/myURI", "attrName", "value");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testSpecifiedDefaultInDifferentElementsNoRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root>" + "<localName xmlns=\"http://example.org/myURI\">"
                + "<child xmlns=\"http://example.org/uniqueURI\"></child>" + "</localName>" + "</root>";

        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.writeStartElement("localName");
        xmlStreamWriter.writeDefaultNamespace("http://example.org/myURI");
        xmlStreamWriter.writeStartElement("child");
        xmlStreamWriter.writeDefaultNamespace("http://example.org/uniqueURI");
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    /*------------- Tests for setPrefix() and setDefaultNamespace() methods --------------------*/

    @Test
    public void testSetPrefixWriteNamespaceNoRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns:p=\"http://example.org/myURI\">" + "</root>";

        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.setPrefix("p", "http://example.org/myURI");
        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        System.out.println("actualOutput: " + actualOutput);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testSetPrefixWriteNamespaceWithRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns:p=\"http://example.org/myURI\">" + "</root>";

        startDocument(xmlStreamWriter);
        xmlStreamWriter.setPrefix("p", "http://example.org/myURI");
        xmlStreamWriter.writeNamespace("p", "http://example.org/myURI");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testSetDefaultNamespaceWriteNamespaceNoRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"http://example.org/myURI\">" + "</root>";

        setUpForNoRepair();
        startDocument(xmlStreamWriter);
        xmlStreamWriter.setDefaultNamespace("http://example.org/myURI");
        xmlStreamWriter.writeNamespace("", "http://example.org/myURI");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }

    @Test
    public void testSetDefaultNamespaceWriteNamespaceWithRepair() throws XMLStreamException {
        final String EXPECTED_OUTPUT = "<?xml version=\"1.0\" ?>" + "<root xmlns=\"http://example.org/myURI\">" + "</root>";

        startDocument(xmlStreamWriter);
        xmlStreamWriter.setDefaultNamespace("http://example.org/myURI");
        xmlStreamWriter.writeNamespace("", "http://example.org/myURI");
        xmlStreamWriter.writeEndElement();
        String actualOutput = endDocumentEmptyDefaultNamespace(xmlStreamWriter);
        assertEquals(EXPECTED_OUTPUT, actualOutput);
    }
}
