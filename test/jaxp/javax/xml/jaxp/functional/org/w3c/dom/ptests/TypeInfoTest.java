/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
import org.w3c.dom.TypeInfo;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm org.w3c.dom.ptests.TypeInfoTest
 * @summary Test getTypeName and getTypeNamespace methods of TypeInfo interface
 */
public class TypeInfoTest {
    /*
     * Get the TypeInfo of the root element, and verify it.
     */
    @Test
    public void test() throws Exception {
        TypeInfo typeInfo = getTypeOfRoot(SCHEMA_INSTANCE,
                """
                <?xml version='1.0'?>
                <test1 xmlns="testNS"><code/></test1>
                """);

        assertEquals("Test", typeInfo.getTypeName());
        assertEquals("testNS", typeInfo.getTypeNamespace());
    }

    private TypeInfo getTypeOfRoot(String schemaText, String docText) throws Exception {
        Element root = getRoot(schemaText, docText);
        return root.getSchemaTypeInfo();
    }

    private Element getRoot(String schemaText, String docText) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        InputSource inSchema = new InputSource(new StringReader(schemaText));
        inSchema.setSystemId("schema.xsd");
        dbf.setNamespaceAware(true);
        dbf.setValidating(true);
        dbf.setAttribute(SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);
        dbf.setAttribute(SCHEMA_SOURCE, inSchema);

        DocumentBuilder parser = dbf.newDocumentBuilder();

        InputSource inSource = new InputSource(new StringReader(docText));
        inSource.setSystemId("doc.xml");
        Document document = parser.parse(inSource);

        return document.getDocumentElement();
    }

    private static final String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

    private static final String SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";

    /*
     * Schema instance
     */
    private static final String SCHEMA_INSTANCE =
            """
            <?xml version="1.0"?>
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                        xmlns:testNS="testNS"
                        targetNamespace="testNS" elementFormDefault="qualified">
                <xsd:element name="test1" type="testNS:Test"/>

                <xsd:complexType name="Test">
                    <xsd:sequence>
                        <xsd:element name="description" minOccurs="0"/>
                        <xsd:element name="code"/>
                    </xsd:sequence>
                </xsd:complexType>

                <xsd:element name="test2">
                    <xsd:complexType>
                        <xsd:sequence>
                            <xsd:element name="description" minOccurs="0"/>
                            <xsd:element name="code"/>
                        </xsd:sequence>
                    </xsd:complexType>
                </xsd:element>

                <xsd:element name="test3" type="xsd:string"/>

                <xsd:element name="test4" type="testNS:Test1"/>

                <xsd:simpleType name="Test1">
                    <xsd:restriction base="xsd:string"/>
                </xsd:simpleType>

                <xsd:element name="test5">
                    <xsd:simpleType>
                        <xsd:restriction base="xsd:string"/>
                    </xsd:simpleType>
                </xsd:element>

                <xsd:element name="test6">
                    <xsd:complexType>
                        <xsd:complexContent>
                            <xsd:extension base="testNS:Test">
                                <xsd:attribute name="attr" type="xsd:string"/>
                            </xsd:extension>
                        </xsd:complexContent>
                    </xsd:complexType>
                </xsd:element>

            </xsd:schema>
            """;


}
