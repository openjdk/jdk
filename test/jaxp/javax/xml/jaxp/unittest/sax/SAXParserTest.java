/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8213734
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit sax.SAXParserTest
 * @summary Tests that failed parsing closes the file correctly.
 */
public class SAXParserTest {

    /*
     * @bug 8213734
     * Verifies that files opened by the SAXParser is closed when Exception
     * occurs.
     */
    @Test
    public void testCloseReaders() throws Exception {
        Path testFile = createTestFile("Test");
        System.out.println("Test file: " + testFile.toString());
        SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
        SAXParser parser = factory.newSAXParser();
        DefaultHandler explodingHandler = new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                throw new SAXException("Stop the parser.");
            }
        };
        assertThrows(SAXException.class, () -> parser.parse(testFile.toFile(), explodingHandler));
        // Deletion would fail on Windows if the file was not closed.
        Files.delete(testFile);
    }

    private static Path createTestFile(String name) throws IOException {
        Path path = Files.createTempFile(name, ".xml");
        Files.writeString(path, "<?xml version=\"1.0\"?><test a1=\"x\" a2=\"y\"/>");
        return path;
    }
}
