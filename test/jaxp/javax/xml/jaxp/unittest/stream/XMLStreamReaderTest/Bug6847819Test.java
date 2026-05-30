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
import javax.xml.stream.XMLStreamException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 6847819
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamReaderTest.Bug6847819Test
 * @summary Test StAX parser shall throw XMLStreamException for illegal xml declaration.
 */
public class Bug6847819Test {

    @Test
    public void testIllegalDecl() {
        String xml = "<?xml ?><root>abc]]>xyz</root>";
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        assertThrows(
                XMLStreamException.class,
                () -> inputFactory.createXMLStreamReader(new StringReader(xml)));
    }
}
