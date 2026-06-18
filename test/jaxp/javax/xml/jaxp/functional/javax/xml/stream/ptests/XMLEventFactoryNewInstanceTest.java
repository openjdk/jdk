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

package javax.xml.stream.ptests;

import jaxp.library.JAXPDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.stream.XMLEventFactory;

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
 * @run junit/othervm javax.xml.stream.ptests.XMLEventFactoryNewInstanceTest
 * @summary Tests for XMLEventFactory.newFactory(factoryId , classLoader)
 */
public class XMLEventFactoryNewInstanceTest {

    private static final String DEFAULT_IMPL_CLASS =
        "com.sun.xml.internal.stream.events.XMLEventFactoryImpl";
    private static final String XMLEVENT_FACTORY_CLASSNAME = DEFAULT_IMPL_CLASS;
    private static final String XMLEVENT_FACTORY_ID = "javax.xml.stream.XMLEventFactory";

    public static Object[][] getValidateParameters() {
        return new Object[][] {
                { XMLEVENT_FACTORY_ID, null },
                { XMLEVENT_FACTORY_ID, XMLEventFactoryNewInstanceTest.class.getClassLoader() },
        };
    }

    /**
     * Test if newDefaultFactory() method returns an instance
     * of the expected factory.
     */
    @Test
    public void testDefaultInstance() {
        XMLEventFactory ef1 = XMLEventFactory.newDefaultFactory();
        XMLEventFactory ef2 = XMLEventFactory.newFactory();
        assertNotSame(ef1, ef2, "same instance returned:");
        assertSame(ef1.getClass(), ef2.getClass(),
                "unexpected class mismatch for newDefaultFactory():");
        assertEquals(DEFAULT_IMPL_CLASS, ef1.getClass().getName());
    }

    /*
     * test for XMLEventFactory.newFactory(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) factoryClassName points to correct
     * implementation of javax.xml.stream.XMLEventFactory , should return
     * newInstance of XMLEventFactory
     */
    @ParameterizedTest
    @MethodSource("getValidateParameters")
    public void testNewFactory(String factoryId, ClassLoader classLoader) {
        System.setProperty(XMLEVENT_FACTORY_ID, XMLEVENT_FACTORY_CLASSNAME);
        try {
            XMLEventFactory xef = XMLEventFactory.newFactory(factoryId, classLoader);
            assertNotNull(xef);
        } finally {
            System.clearProperty(XMLEVENT_FACTORY_ID);
        }
    }

    /*
     * test for XMLEventFactory.newFactory(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) factoryClassName is null , should
     * throw NullPointerException
     */
    @ParameterizedTest
    @MethodSource("jaxp.library.JAXPDataProvider#newInstanceNeg")
    public void testNewFactoryNeg(String factoryId, ClassLoader classLoader) {
        assertThrows(NullPointerException.class, () -> XMLEventFactory.newFactory(factoryId, classLoader));
    }

}
