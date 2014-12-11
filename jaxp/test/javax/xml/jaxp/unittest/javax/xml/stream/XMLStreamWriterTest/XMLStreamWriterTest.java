/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.stream.XMLStreamWriterTest;

import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/*
 * @bug 6347190
 * @summary Test StAX Writer won't insert comment into element inside.
 */
public class XMLStreamWriterTest {

    @BeforeMethod
    protected void setUp() throws Exception {
    }

    @AfterMethod
    protected void tearDown() throws Exception {
    }

    /**
     * Test of main method, of class TestXMLStreamWriter.
     */
    @Test
    public void testWriteComment() {
        try {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a:html href=\"http://java.sun.com\"><!--This is comment-->java.sun.com</a:html>";
            XMLOutputFactory f = XMLOutputFactory.newInstance();
            // f.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES,
            // Boolean.TRUE);
            StringWriter sw = new StringWriter();
            XMLStreamWriter writer = f.createXMLStreamWriter(sw);
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("a", "html", "http://www.w3.org/TR/REC-html40");
            writer.writeAttribute("href", "http://java.sun.com");
            writer.writeComment("This is comment");
            writer.writeCharacters("java.sun.com");
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            sw.flush();
            StringBuffer sb = sw.getBuffer();
            System.out.println("sb:" + sb.toString());
            Assert.assertTrue(sb.toString().equals(xml));
        } catch (Exception ex) {
            Assert.fail("Exception: " + ex.getMessage());
        }
    }

}
