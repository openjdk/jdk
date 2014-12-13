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

import static jaxp.library.JAXPTestUtilities.ERROR_MSG_HEADER;
import static jaxp.library.JAXPTestUtilities.FILE_SEP;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.LocatorImpl;

/**
 * Utility interface which includes final variables of xml, golden file
 * directories.
 */
interface TestUtils {
    final String XML_DIR = System.getProperty("test.src", ".") + FILE_SEP + "javax/xml/parsers/xmlfiles";
    final String GOLDEN_DIR = XML_DIR + FILE_SEP + "out";
}

/**
 * Customized DefaultHandler which writes output document when methods are
 * called by Transformer. Test may use output document to compare with golden
 * file for verification.
 */
class MyCHandler extends DefaultHandler {

    private final BufferedWriter bWriter;
    private final Locator locator = new LocatorImpl();

    private MyCHandler(File file) throws IOException {
        bWriter = new BufferedWriter(new FileWriter(file));
    }

    public static MyCHandler newInstance(File file) throws IOException {
        MyCHandler handler = new MyCHandler(file);
        return handler;
    }

    public void characters(char[] ch, int start, int length) {
        String s = new String(ch, start, length);
        String str = String.format("characters...length is:%d\n<%s>", s.length(), s);
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void endDocument() {
        String str = "endDocument...";
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
            bWriter.flush();
            bWriter.close();

        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void endElement(String namespaceURI, String localName, String qName) {
        String str = String.format("endElement...\nnamespaceURI: <%s> localName: <%s> qName: <%s>", namespaceURI, localName, qName);
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void endPrefixMapping(String prefix) {
        String str = String.format("endPrefixMapping...\nprefix: <%s>", prefix);
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void ignorableWhitespace(char[] ch, int start, int length) {
        String s = new String(ch, start, length);
        String str = String.format("ignorableWhitespace...\n%s ignorable white space string length: %d", s, s.length());
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void processingInstruction(String target, String data) {
        String str = String.format("processingInstruction...target:<%s> data: <%s>", target, data);
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void skippedEntity(String name) {
        String str = String.format("skippedEntity...\nname: <%s>", name);
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void startDocument() {
        String str = "startDocument...";
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
        String str = String.format("startElement...\nnamespaceURI: <%s> localName: <%s> qName: <%s> Number of Attributes: <%d> Line# <%d>", namespaceURI,
                localName, qName, atts.getLength(), locator.getLineNumber());
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }

    public void startPrefixMapping(String prefix, String uri) {
        String str = String.format("startPrefixMapping...\nprefix: <%s> uri: <%s>", prefix, uri);
        try {
            bWriter.write(str, 0, str.length());
            bWriter.newLine();
        } catch (IOException e) {
            throw new RuntimeException(ERROR_MSG_HEADER, e);
        }
    }
}

/**
 * Customized DefaultHandler used for SAXParseException testing.
 */
class MyErrorHandler extends DefaultHandler {
    boolean errorOccured = false;

    private MyErrorHandler() {
    }

    public static MyErrorHandler newInstance() {
        return new MyErrorHandler();
    }

    public void error(SAXParseException e) {
        errorOccured = true;
    }

    public void warning(SAXParseException e) {
        errorOccured = true;
    }

    public void fatalError(SAXParseException e) {
        errorOccured = true;
    }
}
