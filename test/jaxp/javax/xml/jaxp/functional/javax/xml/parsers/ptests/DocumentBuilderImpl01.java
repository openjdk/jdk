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
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;

import static javax.xml.parsers.ptests.ParserTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This checks for the methods of DocumentBuilder
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.parsers.ptests.DocumentBuilderImpl01
 */
public class DocumentBuilderImpl01 implements EntityResolver {
    /**
     * Provide DocumentBuilder.
     *
     * @return data provider has single DocumentBuilder.
     * @throws ParserConfigurationException if a DocumentBuilder cannot be
     *         created which satisfies the configuration requested.
     */
    public static Object[][] getBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        return new Object[][] { { docBuilder } };
    }

    /**
     * Test the default functionality of isValidation method. Expect
     * to return false because not setting the validation.
     * @param docBuilder document builder instance.
     */
    @ParameterizedTest
    @MethodSource("getBuilder")
    public void testCheckDocumentBuilderImpl01(DocumentBuilder docBuilder) {
        assertFalse(docBuilder.isValidating());
    }

    /**
     * Test the default functionality of isNamespaceAware method.
     * @param docBuilder document builder instance.
     */
    @ParameterizedTest
    @MethodSource("getBuilder")
    public void testCheckDocumentBuilderImpl02(DocumentBuilder docBuilder) {
        assertFalse(docBuilder.isNamespaceAware());
    }

    /**
     * Test the parse(InputStream).
     * @param docBuilder document builder instance.
     * @throws Exception If any errors occur.
     */
    @ParameterizedTest
    @MethodSource("getBuilder")
    public void testCheckDocumentBuilderImpl04(DocumentBuilder docBuilder)
            throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(XML_DIR,
                "DocumentBuilderImpl01.xml"))) {
            assertNotNull(docBuilder.parse(fis));
        }
    }

    /**
     * Test the parse(File).
     *
     * @param docBuilder document builder instance.
     * @throws Exception If any errors occur.
     */
    @ParameterizedTest
    @MethodSource("getBuilder")
    public void testCheckDocumentBuilderImpl05(DocumentBuilder docBuilder)
            throws Exception {
        assertNotNull(docBuilder.parse(new File(XML_DIR,
                "DocumentBuilderImpl01.xml")));
    }

    /**
     * Test the parse(InputStream,systemId).
     * @param docBuilder document builder instance.
     * @throws Exception If any errors occur.
     */
    @ParameterizedTest
    @MethodSource("getBuilder")
    public void testCheckDocumentBuilderImpl06(DocumentBuilder docBuilder)
            throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(XML_DIR,
                "DocumentBuilderImpl02.xml"))) {
            assertNotNull(docBuilder.parse(fis, new File(XML_DIR).toURI()
                    .toASCIIString() + '/'));
        }
    }

    /**
     * Test the setEntityResolver.
     * @param docBuilder document builder instance.
     */
    @ParameterizedTest
    @MethodSource("getBuilder")
    public void testCheckDocumentBuilderImpl07(DocumentBuilder docBuilder) {
        docBuilder.setEntityResolver(this);
        assertNotNull(resolveEntity("publicId", "http://www.myhost.com/today"));
    }

    /**
     * Allow the application to resolve external entities.
     *
     * @param publicId The public identifier of the external entity
     *        being referenced, or null if none was supplied.
     * @param systemId The system identifier of the external entity
     *        being referenced.
     * @return An InputSource object describing the new input source,
     *         or null to request that the parser open a regular
     *         URI connection to the system identifier.
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        if (systemId.equals("http://www.myhost.com/today"))
            return new InputSource(systemId);
        else
            return null;
    }
}
