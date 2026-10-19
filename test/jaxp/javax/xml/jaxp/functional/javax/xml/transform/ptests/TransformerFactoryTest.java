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
package javax.xml.transform.ptests;

import jaxp.library.JAXPDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static javax.xml.transform.ptests.TransformerTestConst.GOLDEN_DIR;
import static javax.xml.transform.ptests.TransformerTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Class containing the test cases for TransformerFactory API's
 * getAssociatedStyleSheet method and TransformerFactory.newInstance(factoryClassName , classLoader).
 */
/*
 * @test
 * @bug 8169778
 * @library /javax/xml/jaxp/libs
 * @build jaxp.library.JAXPDataProvider
 * @run junit/othervm javax.xml.transform.ptests.TransformerFactoryTest
 */
public class TransformerFactoryTest {
    /**
     * TransformerFactory builtin system-default implementation class name.
     */
    private static final String DEFAULT_IMPL_CLASS =
       "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

    /**
     * TransformerFactory implementation class name.
     */
    private static final String TRANSFORMER_FACTORY_CLASSNAME = DEFAULT_IMPL_CLASS;

    /**
     * Provide valid TransformerFactory instantiation parameters.
     *
     * @return a data provider contains TransformerFactory instantiation parameters.
     */
    public static Object[][] getValidateParameters() {
        return new Object[][] {
                { TRANSFORMER_FACTORY_CLASSNAME, null },
                { TRANSFORMER_FACTORY_CLASSNAME, TransformerFactoryTest.class.getClassLoader() },
        };
    }

    /**
     * Test if newDefaultInstance() method returns an instance
     * of the expected factory.
     */
    @Test
    public void testDefaultInstance() {
        TransformerFactory tf1 = TransformerFactory.newDefaultInstance();
        TransformerFactory tf2 = TransformerFactory.newInstance();
        assertNotSame(tf1, tf2, "same instance returned:");
        assertSame(tf1.getClass(), tf2.getClass(),
                "unexpected class mismatch for newDefaultInstance():");
        assertEquals(DEFAULT_IMPL_CLASS, tf1.getClass().getName());
    }

    /**
     * Test for TransformerFactory.newInstance(java.lang.String
     * factoryClassName, java.lang.ClassLoader classLoader) factoryClassName
     * points to correct implementation of
     * javax.xml.transform.TransformerFactory , should return newInstance of
     * TransformerFactory
     */
    @ParameterizedTest
    @MethodSource("getValidateParameters")
    public void testNewInstance(String factoryClassName, ClassLoader classLoader) throws TransformerConfigurationException {
        TransformerFactory tf = TransformerFactory.newInstance(factoryClassName, classLoader);
        Transformer transformer = tf.newTransformer();
        assertNotNull(transformer);
    }

    /**
     * Test for TransformerFactory.newInstance(java.lang.String
     * factoryClassName, java.lang.ClassLoader classLoader) factoryClassName is
     * null , should throw TransformerFactoryConfigurationError
     */
    @ParameterizedTest
    @MethodSource("jaxp.library.JAXPDataProvider#newInstanceNeg")
    public void testNewInstanceNeg(String factoryClassName, ClassLoader classLoader) {
        assertThrows(
                TransformerFactoryConfigurationError.class,
                () -> TransformerFactory.newInstance(factoryClassName, classLoader));
    }

    /**
     * This test case checks for the getAssociatedStylesheet method
     * of TransformerFactory.
     * The style sheet returned is then copied to an tfactory01.out
     * It will then be verified to see if it matches the golden files.
     */
    @Test
    public void tfactory01() throws Exception {
        String outputFile = "tfactory01.out";
        String goldFile = GOLDEN_DIR + "tfactory01GF.out";
        String xmlFile = XML_DIR + "TransformerFactoryTest.xml";
        String xmlURI = "file:///" + XML_DIR;

        try (FileInputStream fis = new FileInputStream(xmlFile);
                FileOutputStream fos = new FileOutputStream(outputFile);) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(fis, xmlURI);
            DOMSource domSource = new DOMSource(doc);
            domSource.setSystemId(xmlURI);
            StreamResult streamResult = new StreamResult(fos);
            TransformerFactory tFactory = TransformerFactory.newInstance();

            Source s = tFactory.getAssociatedStylesheet(domSource, "screen",
                                           "Modern", null);
            Transformer t = tFactory.newTransformer();
            t.transform(s, streamResult);
        }
        assertLinesMatch(
                Files.readAllLines(Path.of(goldFile)),
                Files.readAllLines(Path.of(outputFile)));
    }
}
