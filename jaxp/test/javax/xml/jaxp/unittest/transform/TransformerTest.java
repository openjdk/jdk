/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package transform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

/*
 * @summary Test Transformer.
 */
public class TransformerTest {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private static final String XML_DOCUMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<prefix:localName xmlns:prefix=\"namespaceUri\"/>";

    //Test for JDK-6305029
    @Test
    public final void testTransform() throws TransformerException {

        // test SAXSource
        SAXSource saxSource = new SAXSource(new MyXMLReader(), new InputSource());

        StringWriter builder = new StringWriter();
        TransformerFactory.newInstance().newTransformer().transform(saxSource, new StreamResult(builder));

        AssertJUnit.assertEquals("Identity transform of SAXSource", XML_DOCUMENT, builder.toString());

        // test StreamSource
        StreamSource streamSource = new StreamSource(new StringReader(XML_DOCUMENT));

        StringWriter streamResult = new StringWriter();

        TransformerFactory.newInstance().newTransformer().transform(streamSource, new StreamResult(streamResult));

        AssertJUnit.assertEquals("Identity transform of StreamSource", XML_DOCUMENT, streamResult.toString());
    }

    private static class MyXMLReader implements XMLReader {

        private static final String NAMESPACES = "http://xml.org/sax/features/namespaces";

        private static final String NAMESPACE_PREFIXES = "http://xml.org/sax/features/namespace-prefixes";

        private boolean namespaces = true;

        private boolean namespacePrefixes = false;

        private EntityResolver resolver;

        private DTDHandler dtdHandler;

        private ContentHandler contentHandler;

        private ErrorHandler errorHandler;

        public boolean getFeature(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {

            if (name.equals(NAMESPACES)) {
                return namespaces;
            } else if (name.equals(NAMESPACE_PREFIXES)) {
                return namespacePrefixes;
            } else {
                throw new SAXNotRecognizedException();
            }
        }

        public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {

            if (name.equals(NAMESPACES)) {
                namespaces = value;
            } else if (name.equals(NAMESPACE_PREFIXES)) {
                namespacePrefixes = value;
            } else {
                throw new SAXNotRecognizedException();
            }
        }

        public Object getProperty(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return null;
        }

        public void setProperty(final String name, final Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        }

        public void setEntityResolver(final EntityResolver theResolver) {
            this.resolver = theResolver;
        }

        public EntityResolver getEntityResolver() {
            return resolver;
        }

        public void setDTDHandler(final DTDHandler theHandler) {
            dtdHandler = theHandler;
        }

        public DTDHandler getDTDHandler() {
            return dtdHandler;
        }

        public void setContentHandler(final ContentHandler handler) {
            contentHandler = handler;
        }

        public ContentHandler getContentHandler() {
            return contentHandler;
        }

        public void setErrorHandler(final ErrorHandler handler) {
            errorHandler = handler;
        }

        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        public void parse(final InputSource input) throws IOException, SAXException {

            parse();
        }

        public void parse(final String systemId) throws IOException, SAXException {

            parse();
        }

        private void parse() throws SAXException {
            contentHandler.startDocument();
            contentHandler.startPrefixMapping("prefix", "namespaceUri");

            AttributesImpl atts = new AttributesImpl();
            if (namespacePrefixes) {
                atts.addAttribute("", "xmlns:prefix", "xmlns:prefix", "CDATA", "namespaceUri");
            }

            contentHandler.startElement("namespaceUri", "localName", namespacePrefixes ? "prefix:localName" : "", atts);
            contentHandler.endElement("namespaceUri", "localName", namespacePrefixes ? "prefix:localName" : "");
            contentHandler.endPrefixMapping("prefix");
            contentHandler.endDocument();
        }
    }

    @Test
    public final void testCR6272879() {

        final String xsl = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + LINE_SEPARATOR
                + "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">" + LINE_SEPARATOR
                + "<xsl:output method=\"xml\" indent=\"no\" encoding=\"ISO-8859-1\"/>" + LINE_SEPARATOR + "<xsl:template match=\"/\">" + LINE_SEPARATOR
                + "<xsl:element name=\"TransformateurXML\">" + LINE_SEPARATOR + "  <xsl:for-each select=\"XMLUtils/test\">" + LINE_SEPARATOR
                + "  <xsl:element name=\"test2\">" + LINE_SEPARATOR + "    <xsl:element name=\"valeur2\">" + LINE_SEPARATOR
                + "      <xsl:attribute name=\"attribut2\">" + LINE_SEPARATOR + "        <xsl:value-of select=\"valeur/@attribut\"/>" + LINE_SEPARATOR
                + "      </xsl:attribute>" + LINE_SEPARATOR + "      <xsl:value-of select=\"valeur\"/>" + LINE_SEPARATOR + "    </xsl:element>"
                + LINE_SEPARATOR + "  </xsl:element>" + LINE_SEPARATOR + "  </xsl:for-each>" + LINE_SEPARATOR + "</xsl:element>" + LINE_SEPARATOR
                + "</xsl:template>" + LINE_SEPARATOR + "</xsl:stylesheet>";

        final String sourceXml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                + LINE_SEPARATOR
                // "<!DOCTYPE XMLUtils [" + LINE_SEPARATOR +
                // "<!ELEMENT XMLUtils (test*)>" + LINE_SEPARATOR +
                // "<!ELEMENT test (valeur*)>" + LINE_SEPARATOR +
                // "<!ELEMENT valeur (#PCDATA)>" + LINE_SEPARATOR +
                // "<!ATTLIST valeur attribut CDATA #REQUIRED>]>" +
                // LINE_SEPARATOR +
                + "<XMLUtils>" + LINE_SEPARATOR + "  <test>" + LINE_SEPARATOR + "    <valeur attribut=\"Attribut 1\">Valeur 1</valeur>" + LINE_SEPARATOR
                + "  </test>" + LINE_SEPARATOR + "  <test>" + LINE_SEPARATOR + "    <valeur attribut=\"Attribut 2\">Valeur 2</valeur>" + LINE_SEPARATOR
                + "  </test>" + LINE_SEPARATOR + "</XMLUtils>";

        Document document;
        Node node;

        System.out.println("Stylesheet:");
        System.out.println("==================================");
        System.out.println(xsl);
        System.out.println();

        System.out.println("Source file before transformation:");
        System.out.println("==================================");
        System.out.println(sourceXml);
        System.out.println();

        System.out.println("Source file after transformation:");
        System.out.println("=================================");
        document = getTransformation(xsl, sourceXml);

        System.out.println(document);

        System.out.println("Node content for element valeur2:");
        System.out.println("=================================");
        NodeList nodes = document.getElementsByTagName("valeur2");
        nodes = document.getElementsByTagName("valeur2");
        for (int i = 0; i < nodes.getLength(); i++) {
            node = nodes.item(i);
            System.out.println("  Node value: " + node.getFirstChild().getNodeValue());
            System.out.println("  Node attribute: " + node.getAttributes().item(0).getNodeValue());

            AssertJUnit.assertEquals("Node value mismatch", "Valeur " + (i + 1), node.getFirstChild().getNodeValue());
            AssertJUnit.assertEquals("Node attribute mismatch", "Attribut " + (i + 1), node.getAttributes().item(0).getNodeValue());
        }
    }

    private static Document getTransformation(final String xsl, final String sourceXml) {

        Transformer transformer;
        DOMResult reponse;
        Document document = null;

        try {
            InputStream in = new ByteArrayInputStream(xsl.getBytes());
            transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(in));

            reponse = new DOMResult();
            transformer.transform(new StreamSource(new ByteArrayInputStream(sourceXml.getBytes())), reponse);
            document = (Document) reponse.getNode();
        } catch (Exception e) {
            String msg = "Exception in getTransformation: " + e;
            System.err.println(msg);
            Assert.fail(msg);
        }

        return (document);
    }
}
