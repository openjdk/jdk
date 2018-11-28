/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package dom;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/*
 * @test
 * @bug 8213117
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng dom.DocumentTest
 * @summary Tests functionalities for Document.
 */
@Listeners({jaxp.library.BasePolicy.class})
public class DocumentTest {

    private static final String XML1 = "<root><oldNode oldAttrib1=\"old value 1\" oldAttrib2=\"old value 2\"></oldNode></root>";
    private static final String XML2 = "<root><newNode newAttrib=\"new value\"></newNode></root>";

    /**
     * Verifies the adoptNode method. Before a node from a deferred DOM can be
     * adopted, it needs to be fully expanded.
     */
    @Test
    public void testAdoptNode() throws Exception {

        DocumentBuilder builder = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder();

        Document doc1 = getDocument(builder, XML1);
        Document doc2 = getDocument(builder, XML2);

        Element newNode = (Element) doc2.getFirstChild().getFirstChild();
        Element replacementNode = (Element) doc1.adoptNode(newNode);

        Node oldNode = doc1.getFirstChild().getFirstChild();
        doc1.getDocumentElement().replaceChild(replacementNode, oldNode);

        String attrValue = doc1.getFirstChild().getFirstChild().getAttributes()
                .getNamedItem("newAttrib").getNodeValue();
        Assert.assertEquals(attrValue, "new value");
    }

    private static Document getDocument(DocumentBuilder builder, String xml) throws SAXException, IOException {
        InputStream a = new ByteArrayInputStream(xml.getBytes());
        Document out = builder.parse(a);
        return out;
    }

}
