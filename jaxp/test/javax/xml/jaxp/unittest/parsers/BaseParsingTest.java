/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package parsers;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;

/**
 * @test
 * @bug 8169450
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm -DrunSecMngr=true parsers.BaseParsingTest
 * @run testng/othervm parsers.BaseParsingTest
 * @summary Tests that verify base parsing
 */
@Listeners({jaxp.library.BasePolicy.class})
public class BaseParsingTest {

    @DataProvider(name = "xmlDeclarations")
    public static Object[][] xmlDeclarations() {
        return new Object[][]{
            {"<?xml version=\"1.0\"?><root><test>t</test></root>"},
            {"<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><test>t</test></root>"},
            {"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone='yes'?><root><test>t</test></root>"},
            {"<?xml\n"
                + " version=\"1.0\"?>\n"
                + "<root>\n"
                + " <test>t</test>\n"
                + "</root>"},
            {"<?xml\n"
                + " version=\"1.0\"\n"
                + " encoding=\"UTF-8\"?>\n"
                + "<root>\n"
                + " <test>t</test>\n"
                + "</root>"},
            {"<?xml\n"
                + " version=\"1.0\"\n"
                + " encoding=\"UTF-8\"\n"
                + " standalone=\"yes\"?>\n"
                + "<root>\n"
                + " <test>t</test>\n"
                + "</root>"},
            {"<?xml\n"
                + " version\n"
                + "=\n"
                + "\"1.0\"\n"
                + " encoding\n"
                + "=\n"
                + "\"UTF-8\"\n"
                + " standalone\n"
                + "=\n"
                + "\"yes\"?>\n"
                + "<root>\n"
                + " <test>t</test>\n"
                + "</root>"},
            {"<?xml version=\"1.1\"?><root><test>t</test></root>"},
            {"<?xml version=\"1.1\" encoding=\"UTF-8\"?><root><test>t</test></root>"},
            {"<?xml version=\"1.1\" encoding=\"UTF-8\" standalone='yes'?><root><test>t</test></root>"},
            {"<?xml\n"
                + " version=\"1.1\"?>\n"
                + "<root>\n"
                + " <test>t</test>\n"
                + "</root>"},
            {"<?xml\n"
                + " version=\"1.1\"\n"
                + " encoding=\"UTF-8\"?>\n"
                + "<root>\n"
                + " <test>t</test>\n"
                + "</root>"},
            {"<?xml\n"
                + " version=\"1.1\"\n"
                + " encoding=\"UTF-8\"\n"
                + " standalone=\"yes\"?>\n"
                + "<root>\n"
                + " <test>t</test>\n"
                + "</root>"},
            {"<?xml\n"
                + " version\n"
                + "=\n"
                + "\"1.1\"\n"
                + " encoding\n"
                + "=\n"
                + "\"UTF-8\"\n"
                + " standalone\n"
                + "=\n"
                + "\"yes\"?>\n"
                + "<root>\n"
                + " <test>t</test>\n"
                + "</root>"}
        };
    }

    /**
     * @bug 8169450
     * Verifies that the parser successfully parses the declarations provided in
     * xmlDeclarations. Exception would otherwise be thrown as reported in 8169450.
     *
     * XML Declaration according to https://www.w3.org/TR/REC-xml/#NT-XMLDecl
     * [23] XMLDecl     ::= '<?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'
     * [24] VersionInfo ::= S 'version' Eq ("'" VersionNum "'" | '"' VersionNum '"')
     * [25] Eq          ::= S? '=' S? [26] VersionNum ::= '1.' [0-9]+
     *
     * @param xml the test xml
     * @throws Exception if the parser fails to parse the xml
     */
    @Test(dataProvider = "xmlDeclarations")
    public void test(String xml) throws Exception {
        XMLInputFactory xif = XMLInputFactory.newDefaultFactory();
        XMLStreamReader xsr = xif.createXMLStreamReader(new StringReader(xml));
        while (xsr.hasNext()) {
            xsr.next();
        }
    }

    /**
     * @bug 8169450
     * This particular issue does not appear in DOM parsing since the spaces are
     * normalized during version detection. This test case then serves as a guard
     * against such an issue from occuring in the version detection.
     *
     * @param xml the test xml
     * @throws Exception if the parser fails to parse the xml
     */
    @Test(dataProvider = "xmlDeclarations")
    public void testWithDOM(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.parse(new InputSource(new StringReader(xml)));
    }
}
