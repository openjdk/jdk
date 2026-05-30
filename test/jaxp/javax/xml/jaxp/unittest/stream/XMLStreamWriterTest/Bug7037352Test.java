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

package stream.XMLStreamWriterTest;

import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamResult;

import static org.junit.jupiter.api.Assertions.assertSame;

/*
 * @test
 * @bug 7037352
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamWriterTest.Bug7037352Test
 * @summary Test XMLStreamWriter.getNamespaceContext().getPrefix with XML_NS_URI and XMLNS_ATTRIBUTE_NS_URI.
 */
public class Bug7037352Test {

    @Test
    public void test() throws Exception {
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        StreamResult sr = new StreamResult();
        XMLStreamWriter xsw = xof.createXMLStreamWriter(sr);
        NamespaceContext nc = xsw.getNamespaceContext();

        assertSame(XMLConstants.XML_NS_PREFIX, nc.getPrefix(XMLConstants.XML_NS_URI));
        assertSame(XMLConstants.XMLNS_ATTRIBUTE, nc.getPrefix(XMLConstants.XMLNS_ATTRIBUTE_NS_URI));
    }

}
