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

package javax.xml.datatype.ptests;

import jaxp.library.JAXPDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8169778
 * @library /javax/xml/jaxp/libs
 * @build jaxp.library.JAXPDataProvider
 * @run junit/othervm javax.xml.datatype.ptests.FactoryNewInstanceTest
 * @summary Tests for DatatypeFactory.newInstance(factoryClassName , classLoader)
 */
public class FactoryNewInstanceTest {

    private static final String DEFAULT_IMPL_CLASS =
        "com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl";
    private static final String DATATYPE_FACTORY_CLASSNAME = DEFAULT_IMPL_CLASS;

    public static Object[][] getValidateParameters() {
        return new Object[][] {
                { DATATYPE_FACTORY_CLASSNAME, null },
                { DATATYPE_FACTORY_CLASSNAME, FactoryNewInstanceTest.class.getClassLoader() },
        };
    }

    /**
     * Test if newDefaultInstance() method returns an instance
     * of the expected factory.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testDefaultInstance() throws Exception {
        DatatypeFactory dtf1 = DatatypeFactory.newDefaultInstance();
        DatatypeFactory dtf2 = DatatypeFactory.newInstance();
        assertNotSame(dtf1, dtf2, "same instance returned:");
        assertSame(dtf1.getClass(), dtf2.getClass(),
                "unexpected class mismatch for newDefaultInstance():");
        assertEquals(DEFAULT_IMPL_CLASS, dtf1.getClass().getName());
    }

    /*
     * test for DatatypeFactory.newInstance(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) factoryClassName points to correct
     * implementation of javax.xml.datatype.DatatypeFactory , should return
     * newInstance of DatatypeFactory
     */
    @ParameterizedTest
    @MethodSource("getValidateParameters")
    public void testNewInstance(String factoryClassName, ClassLoader classLoader) throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance(DATATYPE_FACTORY_CLASSNAME, null);
        Duration duration = dtf.newDuration(true, 1, 1, 1, 1, 1, 1);
        assertNotNull(duration);
    }


    /*
     * test for DatatypeFactory.newInstance(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) factoryClassName is null , should
     * throw DatatypeConfigurationException
     */
    @ParameterizedTest
    @MethodSource("jaxp.library.JAXPDataProvider#newInstanceNeg")
    public void testNewInstanceNeg(String factoryClassName, ClassLoader classLoader) throws DatatypeConfigurationException {
        assertThrows(
                DatatypeConfigurationException.class,
                () -> DatatypeFactory.newInstance(factoryClassName, classLoader));
    }
}
