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
package stream.XMLStreamReaderTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8167340 8204329
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamReaderTest.StreamReaderTest
 * @summary Verifies patches for StreamReader bugs
 */
public class StreamReaderTest {
    @Test
    public void testNext() throws Exception {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(
                new StringReader("<?xml version='1.0'?><foo/>"));

        while (xmlStreamReader.hasNext()) {
            int event = xmlStreamReader.next();
        }
        // no more event
        assertThrows(NoSuchElementException.class, xmlStreamReader::next);
    }


    /**
     * Verifies that after switching to a different XML Version (1.1), the parser
     * is initialized properly (the listener was not registered in this case).
     *
     * @param path the path to XML source
     */
    @ParameterizedTest
    @MethodSource("getPaths")
    public void testSwitchXMLVersions(String path) throws Exception {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty("javax.xml.stream.isCoalescing", true);
        XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(
                this.getClass().getResourceAsStream(path));

        while (xmlStreamReader.hasNext()) {
            int event = xmlStreamReader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlStreamReader.getLocalName().equals("body")) {
                    String elementText = xmlStreamReader.getElementText();
                    assertFalse(elementText.contains("</body>"), "Fail: elementText contains </body>");
                }
            }
        }
    }

    /**
     * CR 6631264 / sjsxp Issue 45:
     * https://sjsxp.dev.java.net/issues/show_bug.cgi?id=45
     * XMLStreamReader.hasName() should return false for ENTITY_REFERENCE
     */
    @Test
    public void testHasNameOnEntityEvent() throws Exception {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        XMLStreamReader r = xif.createXMLStreamReader(
                this.getClass().getResourceAsStream("ExternalDTD.xml"));
        while (r.next() != XMLStreamConstants.ENTITY_REFERENCE) {
            System.out.println("event type: " + r.getEventType());
        }
        assertFalse(r.hasName(), "hasName returned true on ENTITY_REFERENCE event.");
    }

    /*
       DataProvider: provides paths to xml sources
       Data: path to xml source
     */
    public static Object[][] getPaths() {
        return new Object[][] {
                { "Bug8167340_1-0.xml" },
                { "Bug8167340_1-1.xml" }
        };
    }
}
