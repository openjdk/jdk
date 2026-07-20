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
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 7057778
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm sax.Bug7057778Test
 * @summary Test the file can be deleted after SAXParser.parse(File, DefaultHandler).
 */
public class Bug7057778Test {
    @Test
    public void testParse() throws Exception {
        Path badXml = Path.of("bad.xml");
        Files.writeString(badXml, "\n\n\n\n");

        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser parser = spf.newSAXParser();
        XMLReader xmlReader = parser.getXMLReader();
        DefaultHandler2 noopHandler = new DefaultHandler2();
        xmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", noopHandler);

        // Test file is empty and fails parsing.
        File dst = badXml.toFile();
        assertThrows(SAXParseException.class, () -> parser.parse(dst, noopHandler));
        // But parse failure should not keep the destination file open.
        assertTrue(dst.delete(), "could not delete the file");
    }
}
