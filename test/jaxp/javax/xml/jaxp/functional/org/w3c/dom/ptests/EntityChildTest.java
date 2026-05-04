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
package org.w3c.dom.ptests;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.w3c.dom.ptests.DOMTestUtil.XML_DIR;

/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm org.w3c.dom.ptests.EntityChildTest
 * @summary Test DOM Parser: parsing an xml file that contains external entities.
 */
public class EntityChildTest {

    @Test
    public void test() throws Exception {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(true);
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        Document document = docBuilder.parse(new File(XML_DIR + "entitychild.xml"));

        Element root = document.getDocumentElement();
        NodeList n = root.getElementsByTagName("table");
        NodeList nl = n.item(0).getChildNodes();
        assertEquals(1, n.getLength());
        assertEquals(3, nl.getLength());
    }
}
