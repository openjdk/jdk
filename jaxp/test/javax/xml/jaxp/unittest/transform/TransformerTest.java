/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
import static jaxp.library.JAXPTestUtilities.tryRunWithTmpPermission;

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
import javax.xml.parsers.SAXParserFactory;
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
 * @bug 6272879 6305029 6505031 8150704 8162598 8169112 8169631 8169772
 */
@Listeners({jaxp.library.FilePolicy.class})
public class TransformerTest {

    // some global constants
    private static final String LINE_SEPARATOR =
        getSystemProperty("line.separator");

    private static final String NAMESPACES =
        "http://xml.org/sax/features/namespaces";

    private static final String NAMESPACE_PREFIXES =
        "http://xml.org/sax/features/namespace-prefixes";

    private static abstract class TestTemplate {
        protected void printSnippet(String title, String snippet) {
            StringBuilder div = new StringBuilder();
            for (int i = 0; i < title.length(); i++)
                div.append("=");
            System.out.println(title + "\n" + div + "\n" + snippet + "\n");
        }
    }

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

    private class XMLReaderFor6305029 implements XMLReader {
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
    public final void testBug6272879() throws Exception {
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
        tryRunWithTmpPermission(() -> {
            OutputFormat format = new OutputFormat();
            format.setIndenting(true);
            new XMLSerializer(System.out, format).serialize(document);
        }, new RuntimePermission("accessClassInPackage.com.sun.org.apache.xml.internal.serialize"));
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

    private static class Test8169631 extends TestTemplate {
        private final static String xsl =
            "<?xml version=\"1.0\"?>" + LINE_SEPARATOR +
            "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\">" + LINE_SEPARATOR +
            "  <xsl:template match=\"/\">" + LINE_SEPARATOR +
            "    <xsl:variable name=\"Counter\" select=\"count(//row)\"/>" + LINE_SEPARATOR +
            "    <xsl:variable name=\"AttribCounter\" select=\"count(//@attrib)\"/>" + LINE_SEPARATOR +
            "    <Counter><xsl:value-of select=\"$Counter\"/></Counter>" + LINE_SEPARATOR +
            "    <AttribCounter><xsl:value-of select=\"$AttribCounter\"/></AttribCounter>" + LINE_SEPARATOR +
            "  </xsl:template>" + LINE_SEPARATOR +
            "</xsl:stylesheet>" + LINE_SEPARATOR;

        private final static String sourceXml =
            "<?xml version=\"1.0\"?>" + LINE_SEPARATOR +
            "<envelope xmlns=\"http://www.sap.com/myns\" xmlns:sap=\"http://www.sap.com/myns\">" + LINE_SEPARATOR +
            "  <sap:row sap:attrib=\"a\">1</sap:row>" + LINE_SEPARATOR +
            "  <row attrib=\"b\">2</row>" + LINE_SEPARATOR +
            "  <row sap:attrib=\"c\">3</row>" + LINE_SEPARATOR +
            "</envelope>" + LINE_SEPARATOR;

        /**
         * Utility method to print out transformation result and check values.
         *
         * @param type
         * Text describing type of transformation
         * @param result
         * Resulting output of transformation
         * @param elementCount
         * Counter of elements to check
         * @param attribCount
         * Counter of attributes to check
         */
        private void verifyResult(String type, String result, int elementCount,
                                  int attribCount)
        {
            printSnippet("Result of transformation from " + type + ":",
                         result);
            Assert.assertEquals(
                result.contains("<Counter>" + elementCount + "</Counter>"),
                true, "Result of transformation from " + type +
                " should have count of " + elementCount + " elements.");
            Assert.assertEquals(
                result.contains("<AttribCounter>" + attribCount +
                "</AttribCounter>"), true, "Result of transformation from " +
                type + " should have count of "+ attribCount + " attributes.");
        }

        public void run() throws IOException, TransformerException,
            SAXException, ParserConfigurationException
        {
            printSnippet("Source:", sourceXml);

            printSnippet("Stylesheet:", xsl);

            // create default transformer (namespace aware)
            TransformerFactory tf1 = TransformerFactory.newInstance();
            ByteArrayInputStream bais = new ByteArrayInputStream(xsl.getBytes());
            Transformer t1 = tf1.newTransformer(new StreamSource(bais));

            // test transformation from stream source with namespace support
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bais = new ByteArrayInputStream(sourceXml.getBytes());
            t1.transform(new StreamSource(bais), new StreamResult(baos));
            verifyResult("StreamSource with namespace support", baos.toString(), 0, 1);

            // test transformation from DOM source with namespace support
            bais.reset();
            baos.reset();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(bais));
            t1.transform(new DOMSource(doc), new StreamResult(baos));
            verifyResult("DOMSource with namespace support", baos.toString(), 0, 1);

            // test transformation from DOM source without namespace support
            bais.reset();
            baos.reset();
            dbf.setNamespaceAware(false);
            doc = dbf.newDocumentBuilder().parse(new InputSource(bais));
            t1.transform(new DOMSource(doc), new StreamResult(baos));
            verifyResult("DOMSource without namespace support", baos.toString(), 3, 3);

            // test transformation from SAX source with namespace support
            bais.reset();
            baos.reset();
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlr = spf.newSAXParser().getXMLReader();
            SAXSource saxS = new SAXSource(xmlr, new InputSource(bais));
            t1.transform(saxS, new StreamResult(baos));
            verifyResult("SAXSource with namespace support", baos.toString(), 0, 1);

            // test transformation from SAX source without namespace support
            bais.reset();
            baos.reset();
            spf.setNamespaceAware(false);
            xmlr = spf.newSAXParser().getXMLReader();
            saxS = new SAXSource(xmlr, new InputSource(bais));
            t1.transform(saxS, new StreamResult(baos));
            verifyResult("SAXSource without namespace support", baos.toString(), 3, 3);
        }
    }

    /*
     * @bug 8169631
     * @summary Test combinations of namespace awareness settings on
     *          XSL transformations
     */
    @Test
    public final void testBug8169631() throws IOException, SAXException,
        TransformerException, ParserConfigurationException
    {
        new Test8169631().run();
    }

    /*
     * @bug 8150704
     * @summary Test that XSL transformation with lots of temporary result
     *          trees will not run out of DTM IDs.
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

    private static class Test8162598 extends TestTemplate {
        private static final String xsl =
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

        private static final String sourceXml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><aaa></aaa>" + LINE_SEPARATOR;
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

        private void checkNodeNS(Node test, String nstest, String nsb, String nsc) {
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

        public void run()  throws Exception {
            printSnippet("Source:", sourceXml);

            printSnippet("Stylesheet:", xsl);

            // transform to DOM result
            TransformerFactory tf = TransformerFactory.newInstance();
            ByteArrayInputStream bais = new ByteArrayInputStream(xsl.getBytes());
            Transformer t = tf.newTransformer(new StreamSource(bais));
            DOMResult result = new DOMResult();
            bais = new ByteArrayInputStream(sourceXml.getBytes());
            t.transform(new StreamSource(bais), result);
            Document document = (Document)result.getNode();

            System.out.println("Result after transformation:");
            System.out.println("============================");
            tryRunWithTmpPermission(() -> {
                OutputFormat format = new OutputFormat();
                format.setIndenting(true);
                new XMLSerializer(System.out, format).serialize(document);
            }, new RuntimePermission("accessClassInPackage.com.sun.org.apache.xml.internal.serialize"));
            System.out.println();

            checkNodeNS(document.getElementsByTagName("test1").item(0), "ns2", "ns2", null);
            checkNodeNS(document.getElementsByTagName("test2").item(0), "ns1", "ns2", null);
            checkNodeNS(document.getElementsByTagName("test3").item(0), null, null, null);
            checkNodeNS(document.getElementsByTagName("test4").item(0), null, null, null);
            checkNodeNS(document.getElementsByTagName("test5").item(0), "ns1", "ns1", null);
            Assert.assertNull(document.getElementsByTagName("test6").item(0).getNamespaceURI(),
                "unexpected namespace for test6");
        }
    }

    /*
     * @bug 8162598
     * @summary Test XSLTC handling of namespaces, especially empty namespace
     *          definitions to reset the default namespace
     */
    @Test
    public final void testBug8162598() throws Exception {
        new Test8162598().run();
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
