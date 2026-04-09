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
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * @test
 * @bug 6767322
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamReaderTest.Bug6767322Test
 * @summary Test XMLStreamReader.getVersion() returns null if a version isn't declared.
 */
public class Bug6767322Test {
    private static final String INPUT_FILE = "Bug6767322.xml";

    @Test
    public void testVersionSet() throws Exception {
        XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(this.getClass().getResource(INPUT_FILE).toExternalForm(),
                this.getClass().getResourceAsStream(INPUT_FILE));

        String version = r.getVersion();
        System.out.println("Bug6767322.xml: " + version);
    }

    @Test
    public void testVersionNotSet() throws Exception {
        String xmlText = "Version not declared";
        XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(xmlText.getBytes()));
        String version = r.getVersion();
        System.out.println("Version for text \"" + xmlText + "\": " + version);
        assertNull(version, "getVersion should return null");
    }
}
