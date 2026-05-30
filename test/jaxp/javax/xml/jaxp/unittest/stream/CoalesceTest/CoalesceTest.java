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
package stream.CoalesceTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.CoalesceTest.CoalesceTest
 * @summary Test Coalesce property works.
 */
public class CoalesceTest {

    private static final String countryElementContent = "START India  CS}}}}}} India END";
    private static final String descriptionElementContent = "a&b";
    private static final String fooElementContent = "&< cdatastart<><>>><>><<<<cdataend entitystart insert entityend";

    @Test
    public void testCoalesceProperty() throws Exception {
        XMLInputFactory xifactory = XMLInputFactory.newInstance();
        xifactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        InputStream xml = this.getClass().getResourceAsStream("coalesce.xml");
        XMLStreamReader streamReader = xifactory.createXMLStreamReader(xml);
        while (streamReader.hasNext()) {
            int eventType = streamReader.next();
            if (eventType == XMLStreamConstants.START_ELEMENT && streamReader.getLocalName().equals("country")) {
                eventType = streamReader.next();
                if (eventType == XMLStreamConstants.CHARACTERS) {
                    assertEquals(countryElementContent, streamReader.getText());
                }
            }
            if (eventType == XMLStreamConstants.START_ELEMENT && streamReader.getLocalName().equals("description")) {
                eventType = streamReader.next();
                if (eventType == XMLStreamConstants.CHARACTERS) {
                    assertEquals(descriptionElementContent, streamReader.getText());
                }
            }
            if (eventType == XMLStreamConstants.START_ELEMENT && streamReader.getLocalName().equals("foo")) {
                eventType = streamReader.next();
                if (eventType == XMLStreamConstants.CHARACTERS) {
                    assertEquals(fooElementContent, streamReader.getText());
                }
            }
        }
    }

}
