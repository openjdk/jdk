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
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamReaderTest.Jsr173MR1Req5Test
 * @summary Test XMLStreamReader parses namespace declaration within element when NamespaceAware turns off and on.
 */
public class Jsr173MR1Req5Test {

    private static final String INPUT_FILE1 = "Jsr173MR1Req5.xml";

    @Test
    public void testAttributeCountNoNS() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newInstance();

        // Turn off NS awareness to count xmlns as attributes
        ifac.setProperty("javax.xml.stream.isNamespaceAware", Boolean.FALSE);

        XMLStreamReader re = ifac.createXMLStreamReader(getClass().getResource(INPUT_FILE1).toExternalForm(),
                this.getClass().getResourceAsStream(INPUT_FILE1));
        while (re.hasNext()) {
            int event = re.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                // System.out.println("#attrs = " + re.getAttributeCount());
                assertEquals(3, re.getAttributeCount());
            }
        }
        re.close();
    }

    @Test
    public void testAttributeCountNS() throws Exception {
        XMLInputFactory ifac = XMLInputFactory.newInstance();

        // Turn on NS awareness to not count xmlns as attributes
        ifac.setProperty("javax.xml.stream.isNamespaceAware", Boolean.TRUE);

        XMLStreamReader re = ifac.createXMLStreamReader(getClass().getResource(INPUT_FILE1).toExternalForm(),
                this.getClass().getResourceAsStream(INPUT_FILE1));
        while (re.hasNext()) {
            int event = re.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                // System.out.println("#attrs = " + re.getAttributeCount());
                assertEquals(1, re.getAttributeCount());
            }
        }
        re.close();
    }
}
