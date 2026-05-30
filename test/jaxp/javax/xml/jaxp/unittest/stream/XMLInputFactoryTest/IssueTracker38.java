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

package stream.XMLInputFactoryTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLInputFactoryTest.IssueTracker38
 * @summary Test createXMLEventReader from DOM or SAX source is unsupported.
 */
public class IssueTracker38 {

    @Test
    public void testXMLEventReaderFromDOMSource() {
        assertThrows(UnsupportedOperationException.class, () -> createEventReaderFromSource(new DOMSource()));
    }

    @Test
    public void testXMLStreamReaderFromDOMSource() {
        assertThrows(UnsupportedOperationException.class, () -> createStreamReaderFromSource(new DOMSource()));
    }

    @Test
    public void testXMLEventReaderFromSAXSource() {
        assertThrows(UnsupportedOperationException.class, () -> createEventReaderFromSource(new SAXSource()));
    }

    @Test
    public void testXMLStreamReaderFromSAXSource() {
        assertThrows(UnsupportedOperationException.class, () -> createStreamReaderFromSource(new SAXSource()));
    }

    private void createEventReaderFromSource(Source source) throws Exception {
        XMLInputFactory xIF = XMLInputFactory.newInstance();
        assertNotNull(xIF.createXMLEventReader(source));
    }

    private void createStreamReaderFromSource(Source source) throws Exception {
        XMLInputFactory xIF = XMLInputFactory.newInstance();
        assertNotNull(xIF.createXMLStreamReader(source));
    }


}
