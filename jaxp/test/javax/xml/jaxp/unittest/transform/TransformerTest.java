/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static jaxp.library.JAXPTestUtilities.getSystemProperty;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

/*
 * @test
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm -DrunSecMngr=true transform.TransformerTest
 * @run testng/othervm transform.TransformerTest
 * @summary Transformer Tests
 * @bug 6272879 6305029 6505031 8150704 8162598 8169112 8169772
 */
@Listeners({jaxp.library.FilePolicy.class})
public class TransformerTest {

    /**
     * Reads the contents of the given file into a string.
     * WARNING: this method adds a final line feed even if the last line of the file doesn't contain one.
     *
     * @param f
     * The file to read
     * @return The content of the file as a string, with line terminators as \"n"
     * for all platforms
     * @throws IOException
     * If there was an error reading
     */
    private String getFileContentAsString(File f) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Utility method for testBug8162598().
     * Provides a convenient way to check/assert the expected namespaces
     * of a Node and its siblings.
     *
     * @param test
     * The node to check
     * @param nstest
     * Expected namespace of the node
     * @param nsb
     * Expected namespace of the first sibling
     * @param nsc
     * Expected namespace of the first sibling of the first sibling
     */
    private void checkNodeNS8162598(Node test, String nstest, String nsb, String nsc) {
        String testNodeName = test.getNodeName();
        if (nstest == null) {
            Assert.assertNull(test.getNamespaceURI(), "unexpected namespace for " + testNodeName);
        } else {
            Assert.assertEquals(test.getNamespaceURI(), nstest, "unexpected namespace for " + testNodeName);
        }
        Node b = test.getChildNodes().item(0);
        if (nsb == null) {
            Assert.assertNull(b.getNamespaceURI(), "unexpected namespace for " + testNodeName + "->b");
        } else {
            Assert.assertEquals(b.getNamespaceURI(), nsb, "unexpected namespace for " + testNodeName + "->b");
        }
        Node c = b.getChildNodes().item(0);
        if (nsc == null) {
            Assert.assertNull(c.getNamespaceURI(), "unexpected namespace for " + testNodeName + "->b->c");
        } else {
            Assert.assertEquals(c.getNamespaceURI(), nsc, "unexpected namespace for " + testNodeName + "->b->c");
        }
    }

    private class XMLReaderFor6305029 implements XMLReader {
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

    /*
     * @bug 6272879
     * @summary Test for JDK-6272879
     */
    @Test
    public final void testBug6272879() throws IOException, TransformerException {
        final String LINE_SEPARATOR = getSystemProperty("line.separator");

        final String xsl =
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + LINE_SEPARATOR +
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">" + LINE_SEPARATOR +
                "<xsl:output method=\"xml\" indent=\"no\" encoding=\"ISO-8859-1\"/>" + LINE_SEPARATOR +
                "<xsl:template match=\"/\">" + LINE_SEPARATOR +
                "<xsl:element name=\"TransformateurXML\">" + LINE_SEPARATOR +
                "  <xsl:for-each select=\"XMLUtils/test\">" + LINE_SEPARATOR +
                "  <xsl:element name=\"test2\">" + LINE_SEPARATOR +
                "    <xsl:element name=\"valeur2\">" + LINE_SEPARATOR +
                "      <xsl:attribute name=\"attribut2\">" + LINE_SEPARATOR +
                "        <xsl:value-of select=\"valeur/@attribut\"/>" + LINE_SEPARATOR +
                "      </xsl:attribute>" + LINE_SEPARATOR +
                "      <xsl:value-of select=\"valeur\"/>" + LINE_SEPARATOR +
                "    </xsl:element>" + LINE_SEPARATOR +
                "  </xsl:element>" + LINE_SEPARATOR +
                "  </xsl:for-each>" + LINE_SEPARATOR +
                "</xsl:element>" + LINE_SEPARATOR +
                "</xsl:template>" + LINE_SEPARATOR +
                "</xsl:stylesheet>";

        final String sourceXml =
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + LINE_SEPARATOR +
                // "<!DOCTYPE XMLUtils [" + LINE_SEPARATOR +
                // "<!ELEMENT XMLUtils (test*)>" + LINE_SEPARATOR +
                // "<!ELEMENT test (valeur*)>" + LINE_SEPARATOR +
                // "<!ELEMENT valeur (#PCDATA)>" + LINE_SEPARATOR +
                // "<!ATTLIST valeur attribut CDATA #REQUIRED>]>" +
                // LINE_SEPARATOR +
                "<XMLUtils>" + LINE_SEPARATOR +
                "  <test>" + LINE_SEPARATOR +
                "    <valeur attribut=\"Attribut 1\">Valeur 1</valeur>" + LINE_SEPARATOR +
                "  </test>" + LINE_SEPARATOR +
                "  <test>" + LINE_SEPARATOR +
                "    <valeur attribut=\"Attribut 2\">Valeur 2</valeur>" + LINE_SEPARATOR +
                "  </test>" + LINE_SEPARATOR +
                "</XMLUtils>";

        System.out.println("Stylesheet:");
        System.out.println("=============================");
        System.out.println(xsl);
        System.out.println();

        System.out.println("Source before transformation:");
        System.out.println("=============================");
        System.out.println(sourceXml);
        System.out.println();

        // transform to DOM result
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer(new StreamSource(new ByteArrayInputStream(xsl.getBytes())));
        DOMResult result = new DOMResult();
        t.transform(new StreamSource(new ByteArrayInputStream(sourceXml.getBytes())), result);
        Document document = (Document)result.getNode();

        System.out.println("Result after transformation:");
        System.out.println("============================");
        OutputFormat format = new OutputFormat();
        format.setIndenting(true);
        new XMLSerializer(System.out, format).serialize(document);
        System.out.println();

        System.out.println("Node content for element valeur2:");
        System.out.println("=================================");
        NodeList nodes = document.getElementsByTagName("valeur2");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            System.out.println("  Node value: " + node.getFirstChild().getNodeValue());
            System.out.println("  Node attribute: " + node.getAttributes().item(0).getNodeValue());

            AssertJUnit.assertEquals("Node value mismatch", "Valeur " + (i + 1), node.getFirstChild().getNodeValue());
            AssertJUnit.assertEquals("Node attribute mismatch", "Attribut " + (i + 1), node.getAttributes().item(0).getNodeValue());
        }
    }

    /*
     * @bug 6305029
     * @summary Test for JDK-6305029
     */
    @Test
    public final void testBug6305029() throws TransformerException {
        final String XML_DOCUMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<prefix:localName xmlns:prefix=\"namespaceUri\"/>";

        // test SAXSource
        SAXSource saxSource = new SAXSource(new XMLReaderFor6305029(), new InputSource());
        StringWriter resultWriter = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.newTransformer().transform(saxSource, new StreamResult(resultWriter));
        AssertJUnit.assertEquals("Identity transform of SAXSource", XML_DOCUMENT, resultWriter.toString());

        // test StreamSource
        StreamSource streamSource = new StreamSource(new StringReader(XML_DOCUMENT));
        resultWriter = new StringWriter();
        tf.newTransformer().transform(streamSource, new StreamResult(resultWriter));
        AssertJUnit.assertEquals("Identity transform of StreamSource", XML_DOCUMENT, resultWriter.toString());
    }

    /*
     * @bug 6505031
     * @summary Test transformer parses keys and their values coming from different xml documents.
     */
    @Test
    public final void testBug6505031() throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer(new StreamSource(getClass().getResource("transform.xsl").toString()));
        t.setParameter("config", getClass().getResource("config.xml").toString());
        t.setParameter("mapsFile", getClass().getResource("maps.xml").toString());
        StringWriter sw = new StringWriter();
        t.transform(new StreamSource(getClass().getResource("template.xml").toString()), new StreamResult(sw));
        String s = sw.toString();
        Assert.assertTrue(s.contains("map1key1value") && s.contains("map2key1value"));
    }

    /*
     * @bug 8150704
     * @summary Test that XSL transformation with lots of temporary result trees will not run out of DTM IDs.
     */
    @Test
    public final void testBug8150704() throws TransformerException, IOException {
        System.out.println("Testing transformation of Bug8150704-1.xml...");
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer(new StreamSource(getClass().getResource("Bug8150704-1.xsl").toString()));
        StringWriter sw = new StringWriter();
        t.transform(new StreamSource(getClass().getResource("Bug8150704-1.xml").toString()), new StreamResult(sw));
        String resultstring = sw.toString().replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        String reference = getFileContentAsString(new File(getClass().getResource("Bug8150704-1.ref").getPath()));
        Assert.assertEquals(resultstring, reference, "Output of transformation of Bug8150704-1.xml does not match reference");
        System.out.println("Passed.");

        System.out.println("Testing transformation of Bug8150704-2.xml...");
        t = tf.newTransformer(new StreamSource(getClass().getResource("Bug8150704-2.xsl").toString()));
        sw = new StringWriter();
        t.transform(new StreamSource(getClass().getResource("Bug8150704-2.xml").toString()), new StreamResult(sw));
        resultstring = sw.toString().replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        reference = getFileContentAsString(new File(getClass().getResource("Bug8150704-2.ref").getPath()));
        Assert.assertEquals(resultstring, reference, "Output of transformation of Bug8150704-2.xml does not match reference");
        System.out.println("Passed.");
    }

    /*
     * @bug 8162598
     * @summary Test XSLTC handling of namespaces, especially empty namespace definitions to reset the
     *          default namespace
     */
    @Test
    public final void testBug8162598() throws IOException, TransformerException {
        final String LINE_SEPARATOR = getSystemProperty("line.separator");

        final String xsl =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + LINE_SEPARATOR +
            "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\">" + LINE_SEPARATOR +
            "    <xsl:template match=\"/\">" + LINE_SEPARATOR +
            "        <root xmlns=\"ns1\">" + LINE_SEPARATOR +
            "            <xsl:call-template name=\"transform\"/>" + LINE_SEPARATOR +
            "        </root>" + LINE_SEPARATOR +
            "    </xsl:template>" + LINE_SEPARATOR +
            "    <xsl:template name=\"transform\">" + LINE_SEPARATOR +
            "        <test1 xmlns=\"ns2\"><b xmlns=\"ns2\"><c xmlns=\"\"></c></b></test1>" + LINE_SEPARATOR +
            "        <test2 xmlns=\"ns1\"><b xmlns=\"ns2\"><c xmlns=\"\"></c></b></test2>" + LINE_SEPARATOR +
            "        <test3><b><c xmlns=\"\"></c></b></test3>" + LINE_SEPARATOR +
            "        <test4 xmlns=\"\"><b><c xmlns=\"\"></c></b></test4>" + LINE_SEPARATOR +
            "        <test5 xmlns=\"ns1\"><b><c xmlns=\"\"></c></b></test5>" + LINE_SEPARATOR +
            "        <test6 xmlns=\"\"/>" + LINE_SEPARATOR +
            "    </xsl:template>" + LINE_SEPARATOR +
            "</xsl:stylesheet>";


        final String sourceXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><aaa></aaa>" + LINE_SEPARATOR;

        System.out.println("Stylesheet:");
        System.out.println("=============================");
        System.out.println(xsl);
        System.out.println();

        System.out.println("Source before transformation:");
        System.out.println("=============================");
        System.out.println(sourceXml);
        System.out.println();

        // transform to DOM result
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer(new StreamSource(new ByteArrayInputStream(xsl.getBytes())));
        DOMResult result = new DOMResult();
        t.transform(new StreamSource(new ByteArrayInputStream(sourceXml.getBytes())), result);
        Document document = (Document)result.getNode();

        System.out.println("Result after transformation:");
        System.out.println("============================");
        OutputFormat format = new OutputFormat();
        format.setIndenting(true);
        new XMLSerializer(System.out, format).serialize(document);
        System.out.println();
        checkNodeNS8162598(document.getElementsByTagName("test1").item(0), "ns2", "ns2", null);
        checkNodeNS8162598(document.getElementsByTagName("test2").item(0), "ns1", "ns2", null);
        checkNodeNS8162598(document.getElementsByTagName("test3").item(0), null, null, null);
        checkNodeNS8162598(document.getElementsByTagName("test4").item(0), null, null, null);
        checkNodeNS8162598(document.getElementsByTagName("test5").item(0), "ns1", "ns1", null);
        Assert.assertNull(document.getElementsByTagName("test6").item(0).getNamespaceURI(), "unexpected namespace for test6");
    }

    /**
     * @bug 8169112
     * @summary Test compilation of large xsl file with outlining.
     *
     * This test merely compiles a large xsl file and tests if its bytecode
     * passes verification by invoking the transform() method for
     * dummy content. The test succeeds if no Exception is thrown
     */
    @Test
    public final void testBug8169112() throws FileNotFoundException,
        TransformerException
    {
        TransformerFactory tf = TransformerFactory.newInstance();
        String xslFile = getClass().getResource("Bug8169112.xsl").toString();
        Transformer t = tf.newTransformer(new StreamSource(xslFile));
        String xmlIn = "<?xml version=\"1.0\"?><DOCROOT/>";
        ByteArrayInputStream bis = new ByteArrayInputStream(xmlIn.getBytes());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        t.transform(new StreamSource(bis), new StreamResult(bos));
    }

    /**
     * @bug 8169772
     * @summary Test transformation of DOM with null valued text node
     *
     * This test would throw a NullPointerException during transform when the
     * fix was not present.
     */
    @Test
    public final void testBug8169772() throws ParserConfigurationException,
        SAXException, IOException, TransformerException
    {
        // create a small DOM
        Document doc = DocumentBuilderFactory.newInstance().
            newDocumentBuilder().parse(
                new ByteArrayInputStream(
                    "<?xml version=\"1.0\"?><DOCROOT/>".getBytes()
                )
            );

        // insert a bad element
        Element e = doc.createElement("ERROR");
        e.appendChild(doc.createTextNode(null));
        doc.getDocumentElement().appendChild(e);

        // transform
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TransformerFactory.newInstance().newTransformer().transform(
            new DOMSource(doc.getDocumentElement()), new StreamResult(bos)
        );
        System.out.println("Transformation result (DOM with null text node):");
        System.out.println("================================================");
        System.out.println(bos);
    }
}
