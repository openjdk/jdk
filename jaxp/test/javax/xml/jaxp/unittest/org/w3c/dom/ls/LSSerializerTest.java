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

package org.w3c.dom.ls;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMError;
import org.w3c.dom.DOMErrorHandler;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


/*
 * @bug 6439439 8080906
 * @summary Test LSSerializer.
 */
public class LSSerializerTest {
    private static final String DOM_FORMAT_PRETTY_PRINT = "format-pretty-print";

    class DOMErrorHandlerImpl implements DOMErrorHandler {

        boolean NoOutputSpecifiedErrorReceived = false;

        public boolean handleError(final DOMError error) {
            // consume "no-output-specified" errors
            if ("no-output-specified".equalsIgnoreCase(error.getType())) {
                NoOutputSpecifiedErrorReceived = true;
                return true;
            }

            // unexpected error
            Assert.fail("Unexpected Error Type: " + error.getType() + " @ (" + error.getLocation().getLineNumber() + ", "
                    + error.getLocation().getColumnNumber() + ")" + ", " + error.getMessage());

            return false;
        }
    }

    class Output implements LSOutput {
        public OutputStream getByteStream() {
            return null;
        }

        public void setByteStream(final OutputStream byteStream) {
        }

        public Writer getCharacterStream() {
            return null;
        }

        public void setCharacterStream(final Writer characterStream) {
        }

        public String getSystemId() {
            return null;
        }

        public void setSystemId(final String systemId) {
        }

        public String getEncoding() {
            return "UTF8";
        }

        public void setEncoding(final String encoding) {
        }
    }

    /*
     * @bug 8080906
     * It will fail in a Jigsaw build until JDK-8080266 is fixed.
     */
    @Test
    public void testDefaultLSSerializer() throws Exception {
        DOMImplementationLS domImpl = (DOMImplementationLS) DocumentBuilderFactory.newInstance().newDocumentBuilder().getDOMImplementation();
        LSSerializer lsSerializer = domImpl.createLSSerializer();
        Assert.assertTrue(lsSerializer.getClass().getName().endsWith("dom3.LSSerializerImpl"));
    }

    @Test
    public void testDOMErrorHandler() {

        final String XML_DOCUMENT = "<?xml version=\"1.0\"?>" + "<hello>" + "world" + "</hello>";

        StringReader stringReader = new StringReader(XML_DOCUMENT);
        InputSource inputSource = new InputSource(stringReader);
        Document doc = null;
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            // LSSerializer defaults to Namespace processing
            // so parsing must also
            documentBuilderFactory.setNamespaceAware(true);
            DocumentBuilder parser = documentBuilderFactory.newDocumentBuilder();
            doc = parser.parse(inputSource);

        } catch (Throwable e) {
            e.printStackTrace();
            Assert.fail(e.toString());
        }

        DOMImplementation impl = doc.getImplementation();
        DOMImplementationLS implLS = (DOMImplementationLS) impl.getFeature("LS", "3.0");
        LSSerializer writer = implLS.createLSSerializer();
        DOMErrorHandlerImpl eh = new DOMErrorHandlerImpl();
        writer.getDomConfig().setParameter("error-handler", eh);

        boolean serialized = false;
        try {
            serialized = writer.write(doc, new Output());

            // unexpected success
            Assert.fail("Serialized without raising an LSException due to " + "'no-output-specified'.");
        } catch (LSException lsException) {
            // expected exception
            System.out.println("Expected LSException: " + lsException.toString());
            // continue processing
        }

        Assert.assertFalse(serialized, "Expected writer.write(doc, new Output()) == false");

        Assert.assertTrue(eh.NoOutputSpecifiedErrorReceived, "'no-output-specified' error was expected");
    }

    @Test
    public void testFormatPrettyPrint() {

        final String XML_DOCUMENT = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n" + "<hello>" + "world" + "<child><children/><children/></child>"
                + "</hello>";
        /**JDK-8035467
         * no newline in default output
         */
        final String XML_DOCUMENT_DEFAULT_PRINT =
                "<?xml version=\"1.0\" encoding=\"UTF-16\"?>"
                + "<hello>"
                + "world"
                + "<child><children/><children/></child>"
                + "</hello>";

        final String XML_DOCUMENT_PRETTY_PRINT = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + "<hello>" + "world" + "<child>" + "\n" + "        "
                + "<children/>" + "\n" + "        " + "<children/>" + "\n" + "    " + "</child>" + "\n" + "</hello>" + "\n";

        // it all begins with a Document
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = null;
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException parserConfigurationException) {
            parserConfigurationException.printStackTrace();
            Assert.fail(parserConfigurationException.toString());
        }
        Document document = null;

        StringReader stringReader = new StringReader(XML_DOCUMENT);
        InputSource inputSource = new InputSource(stringReader);
        try {
            document = documentBuilder.parse(inputSource);
        } catch (SAXException saxException) {
            saxException.printStackTrace();
            Assert.fail(saxException.toString());
        } catch (IOException ioException) {
            ioException.printStackTrace();
            Assert.fail(ioException.toString());
        }

        // query DOM Interfaces to get to a LSSerializer
        DOMImplementation domImplementation = documentBuilder.getDOMImplementation();
        DOMImplementationLS domImplementationLS = (DOMImplementationLS) domImplementation;
        LSSerializer lsSerializer = domImplementationLS.createLSSerializer();

        // get configuration
        DOMConfiguration domConfiguration = lsSerializer.getDomConfig();

        // query current configuration
        Boolean defaultFormatPrettyPrint = (Boolean) domConfiguration.getParameter(DOM_FORMAT_PRETTY_PRINT);
        Boolean canSetFormatPrettyPrintFalse = (Boolean) domConfiguration.canSetParameter(DOM_FORMAT_PRETTY_PRINT, Boolean.FALSE);
        Boolean canSetFormatPrettyPrintTrue = (Boolean) domConfiguration.canSetParameter(DOM_FORMAT_PRETTY_PRINT, Boolean.TRUE);

        System.out.println(DOM_FORMAT_PRETTY_PRINT + " default/can set false/can set true = " + defaultFormatPrettyPrint + "/"
                + canSetFormatPrettyPrintFalse + "/" + canSetFormatPrettyPrintTrue);

        // test values
        Assert.assertEquals(defaultFormatPrettyPrint, Boolean.FALSE, "Default value of " + DOM_FORMAT_PRETTY_PRINT + " should be " + Boolean.FALSE);

        Assert.assertEquals(canSetFormatPrettyPrintFalse, Boolean.TRUE, "Can set " + DOM_FORMAT_PRETTY_PRINT + " to " + Boolean.FALSE + " should be "
                + Boolean.TRUE);

        Assert.assertEquals(canSetFormatPrettyPrintTrue, Boolean.TRUE, "Can set " + DOM_FORMAT_PRETTY_PRINT + " to " + Boolean.TRUE + " should be "
                + Boolean.TRUE);

        // get default serialization
        String prettyPrintDefault = lsSerializer.writeToString(document);
        System.out.println("(default) " + DOM_FORMAT_PRETTY_PRINT + "==" + (Boolean) domConfiguration.getParameter(DOM_FORMAT_PRETTY_PRINT)
                + ": \n\"" + prettyPrintDefault + "\"");

        Assert.assertEquals(XML_DOCUMENT_DEFAULT_PRINT, prettyPrintDefault, "Invalid serialization with default value, " + DOM_FORMAT_PRETTY_PRINT + "=="
                + (Boolean) domConfiguration.getParameter(DOM_FORMAT_PRETTY_PRINT));

        // configure LSSerializer to not format-pretty-print
        domConfiguration.setParameter(DOM_FORMAT_PRETTY_PRINT, Boolean.FALSE);
        String prettyPrintFalse = lsSerializer.writeToString(document);
        System.out.println("(FALSE) " + DOM_FORMAT_PRETTY_PRINT + "==" + (Boolean) domConfiguration.getParameter(DOM_FORMAT_PRETTY_PRINT)
                + ": \n\"" + prettyPrintFalse + "\"");

        Assert.assertEquals(XML_DOCUMENT_DEFAULT_PRINT, prettyPrintFalse, "Invalid serialization with FALSE value, " + DOM_FORMAT_PRETTY_PRINT + "=="
                + (Boolean) domConfiguration.getParameter(DOM_FORMAT_PRETTY_PRINT));

        // configure LSSerializer to format-pretty-print
        domConfiguration.setParameter(DOM_FORMAT_PRETTY_PRINT, Boolean.TRUE);
        String prettyPrintTrue = lsSerializer.writeToString(document);
        System.out.println("(TRUE) " + DOM_FORMAT_PRETTY_PRINT + "==" + (Boolean) domConfiguration.getParameter(DOM_FORMAT_PRETTY_PRINT)
                + ": \n\"" + prettyPrintTrue + "\"");

        Assert.assertEquals(XML_DOCUMENT_PRETTY_PRINT, prettyPrintTrue, "Invalid serialization with TRUE value, " + DOM_FORMAT_PRETTY_PRINT + "=="
                + (Boolean) domConfiguration.getParameter(DOM_FORMAT_PRETTY_PRINT));
    }

    @Test
    public void testXML11() {

        /**
         * XML 1.1 document to parse.
         */
        final String XML11_DOCUMENT = "<?xml version=\"1.1\" encoding=\"UTF-16\"?>\n" + "<hello>" + "world" + "<child><children/><children/></child>"
                + "</hello>";

        /**JDK-8035467
         * no newline in default output
         */
        final String XML11_DOCUMENT_OUTPUT =
                "<?xml version=\"1.1\" encoding=\"UTF-16\"?>"
                + "<hello>"
                + "world"
                + "<child><children/><children/></child>"
                + "</hello>";

        // it all begins with a Document
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = null;
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException parserConfigurationException) {
            parserConfigurationException.printStackTrace();
            Assert.fail(parserConfigurationException.toString());
        }
        Document document = null;

        StringReader stringReader = new StringReader(XML11_DOCUMENT);
        InputSource inputSource = new InputSource(stringReader);
        try {
            document = documentBuilder.parse(inputSource);
        } catch (SAXException saxException) {
            saxException.printStackTrace();
            Assert.fail(saxException.toString());
        } catch (IOException ioException) {
            ioException.printStackTrace();
            Assert.fail(ioException.toString());
        }

        // query DOM Interfaces to get to a LSSerializer
        DOMImplementation domImplementation = documentBuilder.getDOMImplementation();
        DOMImplementationLS domImplementationLS = (DOMImplementationLS) domImplementation;
        LSSerializer lsSerializer = domImplementationLS.createLSSerializer();

        // get default serialization
        String defaultSerialization = lsSerializer.writeToString(document);

        System.out.println("XML 1.1 serialization = \"" + defaultSerialization + "\"");

        // output should == input
        Assert.assertEquals(XML11_DOCUMENT_OUTPUT, defaultSerialization, "Invalid serialization of XML 1.1 document: ");
    }
}
