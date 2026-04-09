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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 6440324
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamReaderTest.IsValidatingTest
 * @summary Test StAX can accept non-existent DTD if IS_VALIDATING if false.
 */
public class IsValidatingTest {

    /**
     * File with non-existent DTD.
     */
    private static final String INPUT_FILE = "IsValidatingTest.xml";
    /**
     * File with internal subset and non-existent DTD.
     */
    private static final String INPUT_FILE_INTERNAL_SUBSET = "IsValidatingTestInternalSubset.xml";

    /**
     * Test StAX with IS_VALIDATING = false and a non-existent DTD.
     * Test should pass.
     *
     * Try to parse an XML file that references a non-existent DTD.
     * Desired behavior:
     *     If IS_VALIDATING == false, then continue processing.
     *
     * Note that an attempt is made to read the DTD even if IS_VALIDATING == false.
     * This is not required for DTD validation, but for entity resolution.
     * The XML specification allows the optional reading of an external DTD
     * even for non-validating processors.
     */
    @Test
    public void testStAXIsValidatingFalse() throws Exception {

        boolean dtdEventOccured = false;

        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);

        XMLStreamReader reader = xif.createXMLStreamReader(
                this.getClass().getResource(INPUT_FILE).toExternalForm(),
                this.getClass().getResourceAsStream(INPUT_FILE));

        assertEquals(Boolean.FALSE, reader.getProperty(XMLInputFactory.IS_VALIDATING));

        while (reader.hasNext()) {
            int e = reader.next();
            if (e == XMLEvent.DTD) {
                dtdEventOccured = true;
            }
        }

        // should have see DTD Event
        assertTrue(dtdEventOccured, "did not see DTD event");
    }

    /**
     * Test StAX with IS_VALIDATING = false, an internal subset and a
     * non-existent DTD.
     *
     * Test should pass.
     */
    @Test
    public void testStAXIsValidatingFalseInternalSubset() throws Exception {
        boolean dtdEventOccured = false;
        boolean entityReferenceEventOccured = false;

        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);

        XMLStreamReader reader = xif.createXMLStreamReader(
                this.getClass().getResource(INPUT_FILE).toExternalForm(),
                this.getClass().getResourceAsStream(INPUT_FILE_INTERNAL_SUBSET));

        assertEquals(Boolean.FALSE, reader.getProperty(XMLInputFactory.IS_VALIDATING));

        while (reader.hasNext()) {
            int e = reader.next();
            if (e == XMLEvent.DTD) {
                dtdEventOccured = true;
                System.out.println("testStAXIsValidatingFalseInternalSubset(): " + "reader.getText() with Event == DTD: " + reader.getText());
            } else if (e == XMLEvent.ENTITY_REFERENCE) {
                // expected ENTITY_REFERENCE values?
                if (reader.getLocalName().equals("foo") && reader.getText().equals("bar")) {
                    entityReferenceEventOccured = true;
                }

                System.out.println("testStAXIsValidatingFalseInternalSubset(): " + "reader.get(LocalName, Text)() with Event " + " == ENTITY_REFERENCE: "
                        + reader.getLocalName() + " = " + reader.getText());
            }
        }

        // should have see DTD Event
        assertTrue(dtdEventOccured, "did not see DTD event");

        // should have seen an ENITY_REFERENCE Event
        assertTrue(entityReferenceEventOccured, "did not see ENTITY_REFERENCE event");
    }
}
