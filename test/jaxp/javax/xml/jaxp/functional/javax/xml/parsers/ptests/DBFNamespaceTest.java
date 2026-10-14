/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.parsers.ptests;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static javax.xml.parsers.ptests.ParserTestConst.GOLDEN_DIR;
import static javax.xml.parsers.ptests.ParserTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

/**
 * This tests DocumentBuilderFactory for namespace processing and no-namespace
 * processing.
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.parsers.ptests.DBFNamespaceTest
 */
public class DBFNamespaceTest {

    /**
     * Provide input for the cases that supporting namespace or not.
     * @return a two-dimensional array contains factory, output file name and
     *         golden validate file name.
     */
    public static Object[][] getInput() {
        DocumentBuilderFactory dbf1 = DocumentBuilderFactory.newInstance();
        String outputfile1 = "dbfnstest01.out";
        String goldfile1 = GOLDEN_DIR + "dbfnstest01GF.out";

        DocumentBuilderFactory dbf2 = DocumentBuilderFactory.newInstance();
        dbf2.setNamespaceAware(true);
        String outputfile2 = "dbfnstest02.out";
        String goldfile2 = GOLDEN_DIR + "dbfnstest02GF.out";
        return new Object[][] { { dbf1, outputfile1, goldfile1 }, { dbf2, outputfile2, goldfile2 } };
    }

    /**
     * Test to parse and transform a document without supporting namespace and
     * with supporting namespace.
     * @param dbf a Document Builder factory for creating document object.
     * @param outputfile output file name.
     * @param goldfile golden validate file name.
     * @throws Exception If any errors occur.
     */
    @ParameterizedTest
    @MethodSource("getInput")
    public void testNamespaceTest(DocumentBuilderFactory dbf, String outputfile,
            String goldfile) throws Exception {
        Document doc = dbf.newDocumentBuilder().parse(new File(XML_DIR, "namespace1.xml"));
        dummyTransform(doc, outputfile);
        assertLinesMatch(
                Files.readAllLines(Path.of(goldfile)),
                Files.readAllLines(Path.of(outputfile)));
    }

    /**
     * This method transforms a Node without any xsl file and uses SAXResult to
     * invoke the callbacks through a ContentHandler. If namespace processing is
     * not chosen, namespaceURI in callbacks should be an empty string otherwise
     * it should be namespaceURI.
     */
    private void dummyTransform(Document document, String fileName)
            throws Exception {
        DOMSource domSource = new DOMSource(document);
        try(MyCHandler chandler = MyCHandler.newInstance(new File(fileName))) {
            TransformerFactory.newInstance().newTransformer().
                transform(domSource, new SAXResult(chandler));
        }
    }
}
