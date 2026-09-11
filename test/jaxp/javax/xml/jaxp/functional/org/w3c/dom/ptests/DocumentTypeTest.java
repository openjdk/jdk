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
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.w3c.dom.ptests.DOMTestUtil.createDOM;

/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm org.w3c.dom.ptests.DocumentTypeTest
 * @summary Test DocumentType
 */
public class DocumentTypeTest {

    /*
     * Test testGetEntities method, and verify the entity items.
     */
    @Test
    public void testGetEntities() throws Exception {
        DocumentType documentType = createDOM("DocumentType01.xml").getDoctype();
        NamedNodeMap namedNodeMap = documentType.getEntities();
        // should return both external and internal. Parameter entities are not
        // contained. Duplicates are discarded.
        assertEquals(3, namedNodeMap.getLength());
        assertEquals("author", namedNodeMap.item(0).getNodeName());
        assertEquals("test", namedNodeMap.item(1).getNodeName());
        assertEquals("writer", namedNodeMap.item(2).getNodeName());
    }

    /*
     * Test getNotations method, and verify the notation items.
     */
    @Test
    public void testGetNotations() throws Exception {
        DocumentType documentType = createDOM("DocumentType03.xml").getDoctype();
        NamedNodeMap nm = documentType.getNotations();
        // should return 2 because the notation name is repeated,
        // and it considers only the first occurrence
        assertEquals(2, nm.getLength());
        assertEquals("gs", nm.item(0).getNodeName());
        assertEquals("name", nm.item(1).getNodeName());
    }

    /*
     * Test getName method.
     */
    @Test
    public void testGetName() throws Exception {
        DocumentType documentType = createDOM("DocumentType03.xml").getDoctype();
        assertEquals("note", documentType.getName());
    }

    /*
     * Test getSystemId and getPublicId method.
     */
    @Test
    public void testGetSystemId() throws Exception {
        DocumentType documentType = createDOM("DocumentType05.xml").getDoctype();
        assertEquals("DocumentBuilderImpl02.dtd", documentType.getSystemId());
        assertNull(documentType.getPublicId());
    }

}
