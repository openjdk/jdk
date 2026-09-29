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

package sax;

import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 6809409
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm sax.IssueTracker56Test
 * @summary Test SAXException has Cause.
 */
public class IssueTracker56Test {

    @Test
    public void testException() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser parser = spf.newSAXParser();
        String xmlToParse = "<root>Issue 56: SAXException does not do the exception chaining properly</root>";
        InputSource source = new InputSource(new StringReader(xmlToParse));

        SAXException ex = assertThrows(SAXException.class, () -> parser.parse(source, new MyHandler()));
        assertNotNull(ex.getCause(), "failed chaining exception properly.");
    }

    @Test
    public void testWorkAround() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser parser = spf.newSAXParser();
        String xmlToParse = "<root>Issue 56: SAXException does not do the exception chaining properly</root>";
        InputSource source = new InputSource(new StringReader(xmlToParse));
        assertThrows(SAXException.class, () -> parser.parse(source, new MyHandler1()));
    }

    public static class MyHandler extends DefaultHandler implements ErrorHandler {
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            try {
                System.out.println(uri);
                System.out.println(uri.charAt(56));
            } catch (Exception e) {
                throw new SAXException(e);
            }

        }
    }

    public static class MyHandler1 extends DefaultHandler implements ErrorHandler {
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXExceptionExt {
            try {
                System.out.println(uri);
                System.out.println(uri.charAt(56));
            } catch (Exception e) {
                throw new SAXExceptionExt(e);
            }

        }
    }
}
