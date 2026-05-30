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

package stream.XMLStreamFilterTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamFilterTest.HasNextTest
 * @summary Test Filtered XMLStreamReader hasNext() always return the correct value if repeat to call it.
 */
public class HasNextTest {

    private static final String INPUT_FILE = "HasNextTest.xml";

    private HasNextTypeFilter createFilter() {

        HasNextTypeFilter f = new HasNextTypeFilter();

        f.addType(XMLEvent.START_ELEMENT);
        f.addType(XMLEvent.END_ELEMENT);
        f.addType(XMLEvent.PROCESSING_INSTRUCTION);
        f.addType(XMLEvent.CHARACTERS);
        f.addType(XMLEvent.COMMENT);
        f.addType(XMLEvent.SPACE);
        f.addType(XMLEvent.START_DOCUMENT);
        f.addType(XMLEvent.END_DOCUMENT);
        return f;
    }

    private XMLStreamReader createStreamReader(HasNextTypeFilter f) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        return factory.createFilteredReader(factory.createXMLStreamReader(this.getClass().getResourceAsStream(INPUT_FILE)), (StreamFilter) f);
    }

    private boolean checkHasNext(XMLStreamReader r1) {
        // try asking 3 times, insure all results are the same
        boolean hasNext = assertDoesNotThrow(r1::hasNext);
        assertEquals(hasNext, assertDoesNotThrow(r1::hasNext));
        assertEquals(hasNext, assertDoesNotThrow(r1::hasNext));
        return hasNext;
    }

    @Test
    public void testFilterUsingNextTag() throws Exception {
        HasNextTypeFilter f = createFilter();
        XMLStreamReader r1 = createStreamReader(f);
        XMLStreamException expected = assertThrows(XMLStreamException.class, () -> {
            while (checkHasNext(r1)) {
                r1.nextTag();
            }
        });
        assertTrue(expected.toString().contains("END_ELEMENT"));
    }

    @Test
    public void testFilterUsingNext() throws Exception {
        HasNextTypeFilter f = createFilter();
        XMLStreamReader r1 = createStreamReader(f);
        while (checkHasNext(r1)) {
            r1.next();
        }
    }
}
