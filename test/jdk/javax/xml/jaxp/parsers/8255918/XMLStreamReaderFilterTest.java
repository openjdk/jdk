/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.util.Objects;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.testng.annotations.Test;

/**
 * JDK-8255918
 */
public class XMLStreamReaderFilterTest {

    static final String XMLSOURCE1 = "<root>\n"
            + "  <element1>\n"
            + "    <element2>\n" // Unclosed element2
            + "  </element1>\n"
            + "  <element3>\n"
            + "  </element3>\n"
            + "</root>";

    static final String EXPECTED_MESSAGE1 = ""
            + "ParseError at [row,col]:[4,5]\n"
            + "Message: The element type \"element2\" must be terminated by the matching end-tag \"</element2>\".";

    /*
     * @test
     * @modules java.xml
     * @run testng/othervm XMLStreamReaderFilterTest
     *
     */
    @Test
    public void testXMLStreamReaderExceptionThrownByConstructor() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        Throwable thrown = null;

        try (Reader source = new StringReader(XMLSOURCE1)) {
            XMLStreamReader reader = factory.createXMLStreamReader(source);
            factory.createFilteredReader(reader, r ->
                r.getEventType() == XMLStreamConstants.START_ELEMENT
                        && r.getLocalName().equals("element3"));
        } catch (Exception e) {
            thrown = e;
        }

        assertTrue(thrown instanceof XMLStreamException, "Missing or unexpected exception type: " + String.valueOf(thrown));
        assertEquals(EXPECTED_MESSAGE1, thrown.getMessage(), "Unexpected exception message: " + thrown.getMessage());
    }

}
