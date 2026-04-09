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

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.URL;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamWriterTest.WriterTest
 * @summary Test XMLStreamWriter functionality.
 */
public class WriterTest {

    private static final String ENCODING = "UTF-8";
    private XMLOutputFactory outputFactory = null;
    private XMLStreamWriter xtw = null;
    private static final String[] files = new String[] { "testOne.xml", "testTwo.xml", "testThree.xml", "testFour.xml", "testFive.xml", "testSix.xml", "testSeven.xml",
            "testEight.xml", "testNine.xml", "testTen.xml", "testEleven.xml", "testTwelve.xml", "testDefaultNS.xml", null, "testFixAttr.xml" };

    @BeforeEach
    public void setUp() {
        outputFactory = XMLOutputFactory.newInstance();
    }

    // Test StreamWriter with out any namespace functionality.
    @Test
    public void testOne() throws Exception {
        String outputFile = files[0] + ".out";
        System.out.println("Writing output to " + outputFile);

        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeStartDocument("utf-8", "1.0");
        xtw.writeStartElement("elmeOne");
        xtw.writeStartElement("elemTwo");
        xtw.writeStartElement("elemThree");
        xtw.writeStartElement("elemFour");
        xtw.writeStartElement("elemFive");
        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();

        assertTrue(checkResults(outputFile, files[0] + ".org"));
    }

    // Test StreamWriter's Namespace Context.
    @Test
    public void testTwo() throws Exception {
        String outputFile = files[1] + ".out";
        System.out.println("Writing output to " + outputFile);

        xtw = outputFactory.createXMLStreamWriter(System.out);
        xtw.writeStartDocument();
        xtw.writeStartElement("elemTwo");
        xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeEndDocument();
        NamespaceContext nc = xtw.getNamespaceContext();
        // Got a Namespace Context.class

        XMLStreamWriter xtw1 = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);

        xtw1.writeComment("all elements here are explicitly in the HTML namespace");
        xtw1.setNamespaceContext(nc);
        xtw1.writeStartDocument("utf-8", "1.0");
        xtw1.setPrefix("htmlOne", "http://www.w3.org/TR/REC-html40");
        NamespaceContext nc1 = xtw1.getNamespaceContext();
        xtw1.close();
        Iterator<String> it = nc1.getPrefixes("http://www.w3.org/TR/REC-html40");

        // FileWriter fw = new FileWriter(outputFile);
        while (it.hasNext()) {
            System.out.println("Prefixes :" + it.next());
            // fw.write((String)it.next());
            // fw.write(";");
        }
        // fw.close();
        // assertTrue(checkResults(testTwo+".out", testTwo+".org"));
    }

    // Test StreamWriter for proper element sequence.
    @Test
    public void testThree() throws Exception {
        String outputFile = files[2] + ".out";
        System.out.println("Writing output to " + outputFile);

        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeStartDocument("utf-8", "1.0");
        xtw.writeStartElement("elmeOne");
        xtw.writeStartElement("elemTwo");
        xtw.writeEmptyElement("emptyElem");
        xtw.writeStartElement("elemThree");
        xtw.writeStartElement("elemFour");
        xtw.writeStartElement("elemFive");
        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();

        assertTrue(checkResults(outputFile, files[2] + ".org"));
    }

    // Test StreamWriter with elements,attribute and element content.
    @Test
    public void testFour() throws Exception {
        String outputFile = files[3] + ".out";
        System.out.println("Writing output to " + outputFile);

        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeStartDocument("utf-8", "1.0");
        xtw.writeStartElement("elmeOne");
        xtw.writeStartElement("elemTwo");
        xtw.writeEmptyElement("emptyElem");
        xtw.writeAttribute("testAttr", "testValue");
        xtw.writeStartElement("elemThree");
        xtw.writeStartElement("elemFour");
        xtw.writeCharacters("TestCharacterData");
        xtw.writeStartElement("elemFive");
        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();

        assertTrue(checkResults(outputFile, files[3] + ".org"));
    }

    // Test StreamWriter's Namespace Context.
    @Test
    public void testFive() throws Exception {
        String outputFile = files[4] + ".out";
        System.out.println("Writing output to " + outputFile);

        xtw = outputFactory.createXMLStreamWriter(System.out);
        xtw.writeStartDocument();
        xtw.writeStartElement("elemTwo");
        xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");
        // xtw.writeEndDocument();
        NamespaceContext nc = xtw.getNamespaceContext();
        // Got a Namespace Context.class

        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);

        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.setNamespaceContext(nc);
        xtw.writeStartDocument("utf-8", "1.0");
        // xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "html");
        // xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        assertTrue(checkResults(outputFile, files[4] + ".org"));
        System.out.println("Done");
    }

    // Test StreamWriter, uses the Namespace Context set by the user to resolve namespaces.
    @Test
    public void testSix() throws Exception {
        String outputFile = files[5] + ".out";
        System.out.println("Writing output to " + outputFile);

        xtw = outputFactory.createXMLStreamWriter(System.out);
        xtw.writeStartDocument();
        xtw.writeStartElement("elemTwo");
        xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeEndDocument();
        NamespaceContext nc = xtw.getNamespaceContext();
        // Got a Namespace Context information.

        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);

        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.setNamespaceContext(nc);
        xtw.writeStartDocument("utf-8", "1.0");
        xtw.setPrefix("htmlNewPrefix", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "html");
        // xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        assertTrue(checkResults(outputFile, files[5] + ".org"));
        System.out.println("Done");
    }

    // Test StreamWriter supplied with correct namespace information.
    @Test
    public void testSeven() throws Exception {
        String outputFile = files[6] + ".out";
        System.out.println("Writing output to " + outputFile);

        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.writeStartDocument("utf-8", "1.0");
        xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "html");
        xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        assertTrue(checkResults(outputFile, files[6] + ".org"));
        System.out.println("Done");
    }

    // Test StreamWriter supplied with correct namespace information and" + "isRepairingNamespace is set to true.
    @Test
    public void testEight() throws Exception {
        String outputFile = files[7] + ".out";
        System.out.println("Writing output to " + outputFile);
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.writeStartDocument("utf-8", "1.0");
        xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "html");
        xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        // check against testSeven.xml.org
        assertTrue(checkResults(outputFile, files[7] + ".org"));
        System.out.println("Done");
    }

    // Test StreamWriter supplied with correct namespace information and isRepairingNamespace
    // is set to true. Pass namespace information using writenamespace function.
    @Test
    public void testNine() throws Exception {
        String outputFile = files[8] + ".out";
        System.out.println("Writing output to " + outputFile);
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.writeStartDocument("utf-8", "1.0");
        // xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "html");
        xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        // check against testSeven.xml.org
        assertTrue(checkResults(outputFile, files[7] + ".org"));
        System.out.println("Done");
    }

    // Test StreamWriter supplied with no namespace information and isRepairingNamespace is set to true.
    @Test
    public void testTen() throws Exception {
        String outputFile = files[9] + ".out";
        System.out.println("Writing output to " + outputFile);
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.writeStartDocument("utf-8", "1.0");
        // xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "html");
        // xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        // check against testSeven.xml.org
        // prefix is generated while it was defined in the 'org' file, the
        // following comparison method needs a rewrite.
        // assertTrue(checkResults(files[9]+".out",files[7]+".org"));
        System.out.println("Done");
    }

    // Test StreamWriter supplied with namespace information passed through startElement and
    // isRepairingNamespace is set to true.
    @Test
    public void testEleven() throws Exception {
        String outputFile = files[10] + ".out";
        System.out.println("Writing output to " + outputFile);
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.writeStartDocument("utf-8", "1.0");
        // xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("html", "html", "http://www.w3.org/TR/REC-html40");
        // xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        // check against testSeven.xml.org
        assertTrue(checkResults(outputFile, files[7] + ".org"));
        System.out.println("Done");
    }

    @Test
    public void testTwelve() throws Exception {
        String outputFile = files[11] + ".out";
        System.out.println("Writing output to " + outputFile);
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.writeStartDocument("utf-8", "1.0");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "html");
        // xtw.writeNamespace("html", "http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        // check against testSeven.xml.org
        // assertTrue(checkResults(files[10]+".out",files[7]+".org"));
        System.out.println("Done");
    }

    @Test
    public void testDefaultNamespace() throws Exception {
        String outputFile = files[12] + ".out";
        System.out.println("Writing output to " + outputFile);
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.writeStartDocument("utf-8", "1.0");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "html");
        xtw.writeDefaultNamespace("http://www.w3.org/TR/REC-html40");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        // xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        // check against testSeven.xml.org
        // assertTrue(checkResults(files[10]+".out",files[7]+".org"));
        System.out.println("Done");
    }

    @Test
    public void testRepairNamespace() throws Exception {
        String outputFile = files[14] + ".out";
        System.out.println("Writing output to " + outputFile);
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
        xtw = outputFactory.createXMLStreamWriter(new FileOutputStream(outputFile), ENCODING);
        xtw.writeComment("all elements here are explicitly in the HTML namespace");
        xtw.writeStartDocument("utf-8", "1.0");
        xtw.writeStartElement("html", "html", "http://www.w3.org/TR/REC-html40");
        // xtw.writeStartElement("http://www.w3.org/TR/REC-html40","html");
        // xtw.writeDefaultNamespace("http://www.w3.org/TR/REC-html40");
        xtw.writeAttribute("html", "testPrefix", "attr1", "http://frob.com");
        xtw.writeAttribute("html", "testPrefix", "attr2", "http://frob2.com");
        xtw.writeAttribute("html", "http://www.w3.org/TR/REC-html40", "attr4", "http://frob4.com");

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "head");
        xtw.setPrefix("html", "http://www.w3.org/TR/REC-html40");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "title");

        xtw.writeCharacters("Frobnostication");
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "body");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "p");
        xtw.writeCharacters("Moved to");
        xtw.writeStartElement("http://www.w3.org/TR/REC-html40", "a");
        xtw.writeAttribute("href", "http://frob.com");

        xtw.writeCharacters("here");
        xtw.writeEndElement();
        xtw.writeEndElement();
        xtw.writeEndElement();

        xtw.writeEndElement();

        xtw.writeEndDocument();
        xtw.flush();
        xtw.close();
        // check against testSeven.xml.org
        // assertTrue(checkResults(files[10]+".out",files[7]+".org"));
        System.out.println("Done");
    }

    protected boolean checkResults(String checkFile, String orgFile) throws Exception {
        URL fileName = WriterTest.class.getResource(orgFile);
        // URL outputFileName = WriterTest.class.getResource(checkFile);
        return compareOutput(new InputStreamReader(fileName.openStream()), new InputStreamReader(new FileInputStream(checkFile)));
    }

    protected boolean compareOutput(Reader expected, Reader actual) throws IOException {
        LineNumberReader expectedOutput = null;
        LineNumberReader actualOutput = null;
        try {
            expectedOutput = new LineNumberReader(expected);
            actualOutput = new LineNumberReader(actual);

            while (expectedOutput.ready() && actualOutput.ready()) {
                String expectedLine = expectedOutput.readLine();
                String actualLine = actualOutput.readLine();
                if (!expectedLine.equals(actualLine)) {
                    System.out.println("Entityreference expansion failed, line no: " + expectedOutput.getLineNumber());
                    System.out.println("Expected: " + expectedLine);
                    System.out.println("Actual  : " + actualLine);
                    return false;
                }
            }
            return true;
        } finally {
            expectedOutput.close();
            actualOutput.close();
        }
    }
}
