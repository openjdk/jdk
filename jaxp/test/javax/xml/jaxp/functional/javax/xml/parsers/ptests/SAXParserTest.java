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

package javax.xml.parsers.ptests;

import static jaxp.library.JAXPTestUtilities.failUnexpected;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.xml.sax.HandlerBase;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Class contains the test cases for SAXParser API
 */
public class SAXParserTest {

    /**
     * Provide SAXParser.
     *
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    @DataProvider(name = "parser-provider")
    public Object[][] getParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser saxparser = spf.newSAXParser();
        return new Object[][] { { saxparser } };
    }

    /**
     * Test case with FileInputStream null, parsing should fail and throw
     * IllegalArgumentException.
     *
     * @throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "parser-provider")
    public void testParse01(SAXParser saxparser) throws IllegalArgumentException {
        try {
            FileInputStream instream = null;
            HandlerBase handler = new HandlerBase();
            saxparser.parse(instream, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with an error in xml file, parsing should fail and throw
     * SAXException.
     *
     * @throws SAXException
     */
    @Test(expectedExceptions = SAXException.class, dataProvider = "parser-provider")
    public void testParse02(SAXParser saxparser) throws SAXException {
        try {
            HandlerBase handler = new HandlerBase();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "invalid.xml"));
            saxparser.parse(instream, handler);
        } catch (IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with a valid in xml file, parser should parse the xml document.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse03(SAXParser saxparser) {
        try {
            HandlerBase handler = new HandlerBase();
            saxparser.parse(new File(TestUtils.XML_DIR, "parsertest.xml"), handler);
        } catch (IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with valid input stream, parser should parse the xml document
     * successfully.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse04(SAXParser saxparser) {
        try {
            HandlerBase handler = new HandlerBase();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "correct.xml"));
            saxparser.parse(instream, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with valid input source, parser should parse the xml document
     * successfully.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse05(SAXParser saxparser) {
        try {
            HandlerBase handler = new HandlerBase();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "parsertest.xml"));
            saxparser.parse(instream, handler, new File(TestUtils.XML_DIR).toURI().toASCIIString());
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with uri null, parsing should fail and throw
     * IllegalArgumentException.
     *
     * @throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "parser-provider")
    public void testParse07(SAXParser saxparser) throws IllegalArgumentException {
        try {
            String uri = null;
            HandlerBase handler = new HandlerBase();
            saxparser.parse(uri, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with non-existant uri, parsing should fail and throw
     * IOException.
     *
     * @throws SAXException
     * @throws IOException
     */
    @Test(expectedExceptions = { SAXException.class, IOException.class }, dataProvider = "parser-provider")
    public void testParse08(SAXParser saxparser) throws SAXException, IOException {
        String uri = " ";

        HandlerBase handler = new HandlerBase();
        saxparser.parse(uri, handler);

    }

    /**
     * Testcase with proper uri, parser should parse successfully.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse09(SAXParser saxparser) {
        try {
            File file = new File(TestUtils.XML_DIR, "correct.xml");
            HandlerBase handler = new HandlerBase();
            saxparser.parse(file.toURI().toASCIIString(), handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with File null, parsing should fail and throw
     * IllegalArgumentException.
     *
     * @throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "parser-provider")
    public void testParse10(SAXParser saxparser) throws IllegalArgumentException {
        try {
            File file = null;
            HandlerBase handler = new HandlerBase();
            saxparser.parse(file, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with empty string as File, parsing should fail and throw
     * SAXException.
     *
     * @throws SAXException
     */
    @Test(expectedExceptions = SAXException.class, dataProvider = "parser-provider")
    public void testParse11(SAXParser saxparser) throws SAXException {
        try {
            HandlerBase handler = new HandlerBase();
            File file = new File("");
            saxparser.parse(file, handler);
        } catch (IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with xml file that has errors parsing should fail and throw
     * SAXException.
     *
     * @throws SAXException
     */
    @Test(expectedExceptions = SAXException.class, dataProvider = "parser-provider")
    public void testParse12(SAXParser saxparser) throws SAXException {
        try {
            HandlerBase handler = new HandlerBase();
            File file = new File(TestUtils.XML_DIR, "valid.xml");
            saxparser.parse(file, handler);
        } catch (IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with xml file that has no errors Parser should successfully
     * parse the xml document.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse13(SAXParser saxparser) {
        try {
            HandlerBase handler = new HandlerBase();
            File file = new File(TestUtils.XML_DIR, "correct.xml");
            saxparser.parse(file, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }

    }

    /**
     * Testcase with input source null, parsing should fail and throw
     * IllegalArgumentException.
     *
     * @throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "parser-provider")
    public void testParse14(SAXParser saxparser) throws IllegalArgumentException {
        try {
            InputSource is = null;
            HandlerBase handler = new HandlerBase();
            saxparser.parse(is, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with input source attached an invaild xml, parsing should fail
     * and throw SAXException.
     *
     * @throws SAXException
     */
    @Test(expectedExceptions = SAXException.class, dataProvider = "parser-provider")
    public void testParse15(SAXParser saxparser) throws SAXException {
        try {
            HandlerBase handler = new HandlerBase();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "invalid.xml"));
            InputSource is = new InputSource(instream);
            saxparser.parse(is, handler);
        } catch (IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with input source attached an vaild xml, parser should
     * successfully parse the xml document.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse16(SAXParser saxparser) {
        try {
            HandlerBase handler = new HandlerBase();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "correct.xml"));
            InputSource is = new InputSource(instream);
            saxparser.parse(is, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with FileInputStream null, parsing should fail and throw
     * IllegalArgumentException.
     *
     * @throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "parser-provider")
    public void testParse17(SAXParser saxparser) throws IllegalArgumentException {
        try {
            FileInputStream instream = null;
            DefaultHandler handler = new DefaultHandler();
            saxparser.parse(instream, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with an error in xml file, parsing should fail and throw
     * SAXException.
     *
     * @throws SAXException
     */
    @Test(expectedExceptions = SAXException.class, dataProvider = "parser-provider")
    public void testParse18(SAXParser saxparser) throws SAXException {
        try {
            DefaultHandler handler = new DefaultHandler();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "invalid.xml"));
            saxparser.parse(instream, handler);
        } catch (IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with valid input stream, parser should parse the xml document
     * successfully.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse19(SAXParser saxparser) {
        try {
            DefaultHandler handler = new DefaultHandler();
            saxparser.parse(new File(TestUtils.XML_DIR, "parsertest.xml"), handler);
        } catch (IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with valid input stream, parser should parse the xml document
     * successfully.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse20(SAXParser saxparser) {
        try {
            DefaultHandler handler = new DefaultHandler();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "correct.xml"));
            saxparser.parse(instream, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with valid input source, parser should parse the xml document
     * successfully.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse21(SAXParser saxparser) {
        try {
            DefaultHandler handler = new DefaultHandler();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "parsertest.xml"));
            saxparser.parse(instream, handler, new File(TestUtils.XML_DIR).toURI().toASCIIString());
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }

    }

    /**
     * Testcase with uri null, parsing should fail and throw
     * IllegalArgumentException.
     *
     * @throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "parser-provider")
    public void testParse23(SAXParser saxparser) throws IllegalArgumentException {
        try {
            String uri = null;
            DefaultHandler handler = new DefaultHandler();
            saxparser.parse(uri, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with non-existant uri, parsing should fail and throw
     * SAXException or IOException.
     *
     * @throws SAXException
     * @throws IOException
     */
    @Test(expectedExceptions = { SAXException.class, IOException.class }, dataProvider = "parser-provider")
    public void testParse24(SAXParser saxparser) throws SAXException, IOException {
        String uri = " ";
        DefaultHandler handler = new DefaultHandler();
        saxparser.parse(uri, handler);

    }

    /**
     * Testcase with proper uri, parser should parse successfully.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse25(SAXParser saxparser) {
        try {
            File file = new File(TestUtils.XML_DIR, "correct.xml");

            DefaultHandler handler = new DefaultHandler();
            saxparser.parse(file.toURI().toASCIIString(), handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with File null, parsing should fail and throw
     * IllegalArgumentException.
     *
     * @throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "parser-provider")
    public void testParse26(SAXParser saxparser) throws IllegalArgumentException {
        try {
            DefaultHandler handler = new DefaultHandler();
            saxparser.parse((File) null, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with empty string as File, parsing should fail and throw
     * SAXException.
     *
     * @throws SAXException
     */
    @Test(expectedExceptions = SAXException.class, dataProvider = "parser-provider")
    public void testParse27(SAXParser saxparser) throws SAXException {
        try {
            DefaultHandler handler = new DefaultHandler();
            File file = new File("");
            saxparser.parse(file, handler);
        } catch (IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with xml file that has errors, parsing should fail and throw
     * SAXException.
     *
     * @throws SAXException
     */
    @Test(expectedExceptions = SAXException.class, dataProvider = "parser-provider")
    public void testParse28(SAXParser saxparser) throws SAXException {
        try {
            DefaultHandler handler = new DefaultHandler();
            File file = new File(TestUtils.XML_DIR, "valid.xml");
            saxparser.parse(file, handler);
        } catch (IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with xml file that has no errors, parser should successfully
     * parse the xml document.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse29(SAXParser saxparser) {
        try {
            DefaultHandler handler = new DefaultHandler();
            File file = new File(TestUtils.XML_DIR, "correct.xml");
            saxparser.parse(file, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with input source null, parsing should fail and throw
     * IllegalArgumentException.
     *
     * @throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "parser-provider")
    public void testParse30(SAXParser saxparser) throws IllegalArgumentException {
        try {
            InputSource is = null;
            DefaultHandler handler = new DefaultHandler();
            saxparser.parse(is, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase with an invalid xml file, parser should throw SAXException.
     *
     * @throws SAXException
     */
    @Test(expectedExceptions = SAXException.class, dataProvider = "parser-provider")
    public void testParse31(SAXParser saxparser) throws SAXException {
        try {
            DefaultHandler handler = new DefaultHandler();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "invalid.xml"));
            InputSource is = new InputSource(instream);
            saxparser.parse(is, handler);
        } catch (IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Test case to parse an xml file that not use namespaces.
     */
    @Test(dataProvider = "parser-provider")
    public void testParse32(SAXParser saxparser) {
        try {
            DefaultHandler handler = new DefaultHandler();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "correct.xml"));
            InputSource is = new InputSource(instream);
            saxparser.parse(is, handler);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Test case to parse an xml file that uses namespaces.
     */
    @Test
    public void testParse33() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            SAXParser saxparser = spf.newSAXParser();
            HandlerBase handler = new HandlerBase();
            FileInputStream instream = new FileInputStream(new File(TestUtils.XML_DIR, "ns4.xml"));
            saxparser.parse(instream, handler);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }
}
