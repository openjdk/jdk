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

package stream.XMLResolverTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLResolverTest.XMLResolverTest
 * @summary Test XMLResolver.
 */
public class XMLResolverTest {

    @Test
    public void testXMLResolver() throws Exception {
        XMLInputFactory xifactory = XMLInputFactory.newInstance();
        xifactory.setProperty(XMLInputFactory.RESOLVER, new MyStaxResolver());
        File file = new File(getClass().getResource("XMLResolverTest.xml").getFile());
        String systemId = file.toURI().toString();
        InputStream entityxml = new FileInputStream(file);
        XMLStreamReader streamReader = xifactory.createXMLStreamReader(systemId, entityxml);
        while (streamReader.hasNext()) {
            int eventType = streamReader.next();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                eventType = streamReader.next();
                if (eventType == XMLStreamConstants.CHARACTERS) {
                    String text = streamReader.getText();
                    assertTrue(text.contains("replace2"));
                }
            }
        }
    }

    private static class MyStaxResolver implements XMLResolver {

        public Object resolveEntity(String publicId, String systemId, String baseURI, String namespace) {
            try {
                return new FileInputStream(getClass().getResource("replace2.txt").getFile());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
