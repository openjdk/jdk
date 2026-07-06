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

import jaxp.library.JAXPDataProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @build jaxp.library.JAXPDataProvider
 * @run junit/othervm javax.xml.parsers.ptests.SAXFactoryNewInstanceTest
 * @summary Tests for SAXParserFactory.newInstance(factoryClassName , classLoader)
 */
public class SAXFactoryNewInstanceTest {

    private static final String SAXPARSER_FACTORY_CLASSNAME = "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl";

    public static Object[][] getValidateParameters() {
        return new Object[][] {
                { SAXPARSER_FACTORY_CLASSNAME, null },
                { SAXPARSER_FACTORY_CLASSNAME, SAXFactoryNewInstanceTest.class.getClassLoader() },
        };
    }

    /*
     * test for SAXParserFactory.newInstance(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) factoryClassName points to correct
     * implementation of javax.xml.parsers.SAXParserFactory , should return
     * newInstance of SAXParserFactory
     */
    @ParameterizedTest
    @MethodSource("getValidateParameters")
    public void testNewInstance(String factoryClassName, ClassLoader classLoader) throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance(factoryClassName, classLoader);
        SAXParser sp = spf.newSAXParser();
        assertNotNull(sp);
    }

    /*
     * test for SAXParserFactory.newInstance(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) factoryClassName is null , should
     * throw FactoryConfigurationError
     */
    @ParameterizedTest
    @MethodSource("jaxp.library.JAXPDataProvider#newInstanceNeg")
    public void testNewInstanceNeg(String factoryClassName, ClassLoader classLoader) {
        assertThrows(
                FactoryConfigurationError.class,
                () -> SAXParserFactory.newInstance(factoryClassName, classLoader));
    }

}
