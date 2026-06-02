/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.transform.ptests;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static javax.xml.transform.ptests.TransformerTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * Unit test for SAXSource sourceToInputSource API.
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.transform.ptests.SAXSourceTest
 */
public class SAXSourceTest {
    /**
     * Test style-sheet file name
     */
    private final String TEST_FILE = XML_DIR + "cities.xsl";

    /**
     * Test obtaining a SAX InputSource object from a Source object.
     */
    @Test
    public void source2inputsource01() throws IOException {
        try (FileInputStream fis = new FileInputStream(TEST_FILE)) {
            StreamSource streamSource = new StreamSource(fis);
            assertNotNull(SAXSource.sourceToInputSource(streamSource));
        }
    }

    /**
     * This test case tries to get InputSource from DOMSource using
     * sourceToInputSource method. It is not possible and hence null is
     * expected. This is a negative test case,
     */
    @Test
    public void source2inputsource02() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.newDocumentBuilder().parse(new File(TEST_FILE));
        assertNull(SAXSource.sourceToInputSource(new DOMSource(null)));
    }

    /**
     * This test case tries to get InputSource from SAXSource using
     * sourceToInputSource method. This will also check if the systemId
     * remained the same. This is a positive test case.
     */
    @Test
    public void source2inputsource03() throws IOException {
        String SYSTEM_ID = "file:///" + XML_DIR;
        try (FileInputStream fis = new FileInputStream(TEST_FILE)) {
            SAXSource saxSource =
                    new SAXSource(new InputSource(fis));
            saxSource.setSystemId(SYSTEM_ID);
            assertEquals(SYSTEM_ID, SAXSource.sourceToInputSource(saxSource).getSystemId());
        }
    }
}
