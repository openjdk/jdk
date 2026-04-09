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

package stream.XMLEventReaderTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * @test
 * @bug 6668115
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLEventReaderTest.Bug6668115Test
 * @summary Test XMLEventReader.getElementText() shall update last event even if no peek.
 */
public class Bug6668115Test {

    public java.io.File input;
    public final String filesDir = "./";
    protected XMLInputFactory inputFactory;
    protected XMLOutputFactory outputFactory;

    /**
     * The reason the following call sequence is a problem is that with a
     * peekevent, getElementText calls nextEvent which does properly update the
     * lastEvent
     */
    @Test
    public void testNextTag() throws Exception {
        XMLEventReader er = getReader();
        er.nextTag();
        er.nextTag();

        assertNotNull(er.getElementText());
        er.nextTag();
        assertNotNull(er.getElementText());
    }

    @Test
    public void testNextTagWPeek() throws Exception {
        XMLEventReader er = getReader();
        er.nextTag();
        er.nextTag();

        er.peek();
        assertNotNull(er.getElementText());
        er.nextTag();
        assertNotNull(er.getElementText());
    }

    private XMLEventReader getReader() throws Exception {
        inputFactory = XMLInputFactory.newInstance();
        input = new File(getClass().getResource("play2.xml").getFile());
        // Check if event reader returns the correct event
        return inputFactory.createXMLEventReader(inputFactory.createXMLStreamReader(new java.io.FileInputStream(input), "UTF-8"));
    }

}
