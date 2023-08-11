/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package parse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.testng.Assert.assertEquals;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * JDK-8059327: XML parser returns corrupt attribute value
 * https://bugs.openjdk.org/browse/JDK-8059327
 *
 * Also:
 * JDK-8061550: XMLEntityScanner can corrupt content during parsing
 * https://bugs.openjdk.org/browse/JDK-8061550
 *
 * @Summary: verify that the character cache in XMLEntityScanner is reset properly
 */

public class XMLEntityScannerLoad {

    @Test(dataProvider = "xmls")
    public void test(String xml) throws SAXException, IOException, ParserConfigurationException {
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ChunkInputStream(xml));
        String value = d.getDocumentElement().getAttribute("a1");
        assertEquals(value, "w");
    }

    static class ChunkInputStream extends ByteArrayInputStream {
        ChunkInputStream(String xml) {
            super(xml.getBytes());
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            return super.read(b, off, 7);
        }
    }

    @DataProvider(name = "xmls")
    private Object[][] xmls() {
        return new Object[][] {
            {"<?xml version=\"1.0\"?><element a1=\"w\" a2=\"&quot;&quot;\"/>"},
            {"<?xml version=\"1.1\"?><element a1=\"w\" a2=\"&quot;&quot;\"/>"}
        };
    }
}
