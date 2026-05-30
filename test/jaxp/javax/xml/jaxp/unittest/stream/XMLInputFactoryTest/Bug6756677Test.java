/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

package stream.XMLInputFactoryTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/*
 * @test
 * @bug 6756677
 * @library /javax/xml/jaxp/unittest
 * @compile MyInputFactory.java
 * @run junit/othervm stream.XMLInputFactoryTest.Bug6756677Test
 * @summary Test XMLInputFactory.newFactory(String factoryId, ClassLoader classLoader).
 */
public class Bug6756677Test {

    @Test
    public void testNewInstance() {
        String myFactory = "stream.XMLInputFactoryTest.MyInputFactory";
        System.setProperty("MyInputFactory", myFactory);
        XMLInputFactory xif = XMLInputFactory.newInstance("MyInputFactory", null);
        System.out.println(xif.getClass().getName());
        assertEquals(myFactory, xif.getClass().getName());
    }

    // newFactory was added in StAX 1.2
    @Test
    public void testNewFactory() {
        String myFactory = "stream.XMLInputFactoryTest.MyInputFactory";
        System.setProperty("MyInputFactory", myFactory);
        XMLInputFactory xif = XMLInputFactory.newFactory("MyInputFactory", null);
        System.out.println(xif.getClass().getName());
        assertEquals(myFactory, xif.getClass().getName());
    }


    String XMLInputFactoryClassName = "com.sun.xml.internal.stream.XMLInputFactoryImpl";
    String XMLInputFactoryID = "javax.xml.stream.XMLInputFactory";
    ClassLoader CL = null;

    /*
     * test for XMLInputFactory.newInstance(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) classloader is null and
     * factoryClassName points to correct implementation of
     * javax.xml.stream.XMLInputFactory , should return newInstance of
     * XMLInputFactory
     */
    @Test
    public void test29() {
        System.setProperty(XMLInputFactoryID, XMLInputFactoryClassName);
        XMLInputFactory xif = XMLInputFactory.newInstance(XMLInputFactoryID, CL);
        assertInstanceOf(XMLInputFactory.class, xif, "xif should be an instance of XMLInputFactory");
    }

    /*
     * test for XMLInputFactory.newInstance(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) classloader is
     * default(Class.getClassLoader()) and factoryClassName points to correct
     * implementation of javax.xml.stream.XMLInputFactory , should return
     * newInstance of XMLInputFactory
     */
    @Test
    public void test31() {
        Bug6756677Test test3 = new Bug6756677Test();
        ClassLoader cl = (test3.getClass()).getClassLoader();
        System.setProperty(XMLInputFactoryID, XMLInputFactoryClassName);
        XMLInputFactory xif = XMLInputFactory.newInstance(XMLInputFactoryID, cl);
        assertInstanceOf(XMLInputFactory.class, xif, "xif should be an instance of XMLInputFactory");
    }
}
