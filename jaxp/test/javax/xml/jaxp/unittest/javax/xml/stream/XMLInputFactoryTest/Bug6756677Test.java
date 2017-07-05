/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.stream.XMLInputFactoryTest;

import javax.xml.stream.XMLInputFactory;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @bug 6756677
 * @summary Test XMLInputFactory.newFactory(String factoryId, ClassLoader classLoader).
 */
public class Bug6756677Test {

    @Test
    public void testNewInstance() {
        String myFactory = "javax.xml.stream.XMLInputFactoryTest.MyInputFactory";
        try {
            System.setProperty("MyInputFactory", myFactory);
            XMLInputFactory xif = XMLInputFactory.newInstance("MyInputFactory", null);
            System.out.println(xif.getClass().getName());
            Assert.assertTrue(xif.getClass().getName().equals(myFactory));

        } catch (UnsupportedOperationException oe) {
            Assert.fail(oe.getMessage());
        }

    }

    // newFactory was added in StAX 1.2
    @Test
    public void testNewFactory() {
        String myFactory = "javax.xml.stream.XMLInputFactoryTest.MyInputFactory";
        ClassLoader cl = null;
        try {
            System.setProperty("MyInputFactory", myFactory);
            XMLInputFactory xif = XMLInputFactory.newFactory("MyInputFactory", cl);
            System.out.println(xif.getClass().getName());
            Assert.assertTrue(xif.getClass().getName().equals(myFactory));

        } catch (UnsupportedOperationException oe) {
            Assert.fail(oe.getMessage());
        }

    }

    String Temp_Result = "";
    boolean PASSED = true;
    boolean FAILED = false;

    String XMLInputFactoryClassName = "com.sun.xml.internal.stream.XMLInputFactoryImpl";
    String XMLInputFactoryID = "javax.xml.stream.XMLInputFactory";
    ClassLoader CL = null;

    // jaxp-test jaxp-product-tests javax.xml.jaxp14.ptests.FactoryTest
    @Test
    public void test() {
        if (!test29()) {
            Assert.fail(Temp_Result);
        }
        if (!test31()) {
            Assert.fail(Temp_Result);
        }
    }

    /*
     * test for XMLInputFactory.newInstance(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) classloader is null and
     * factoryClassName points to correct implementation of
     * javax.xml.stream.XMLInputFactory , should return newInstance of
     * XMLInputFactory
     */
    @Test
    public boolean test29() {
        try {
            System.setProperty(XMLInputFactoryID, XMLInputFactoryClassName);
            XMLInputFactory xif = XMLInputFactory.newInstance(XMLInputFactoryID, CL);
            if (xif instanceof XMLInputFactory) {
                System.out.println(" test29() passed");
                return PASSED;
            } else {
                System.out.println(" test29() failed");
                Temp_Result = "test29() failed: xif not an instance of XMLInputFactory ";
                return FAILED;
            }
        } catch (javax.xml.stream.FactoryConfigurationError fce) {
            System.out.println("Failed : FactoryConfigurationError in test29 " + fce);
            Temp_Result = "test29() failed ";
            return FAILED;
        } catch (Exception e) {
            System.out.println("Failed : Exception in test29 " + e);
            Temp_Result = "test29() failed ";
            return FAILED;
        }
    }

    /*
     * test for XMLInputFactory.newInstance(java.lang.String factoryClassName,
     * java.lang.ClassLoader classLoader) classloader is
     * default(Class.getClassLoader()) and factoryClassName points to correct
     * implementation of javax.xml.stream.XMLInputFactory , should return
     * newInstance of XMLInputFactory
     */
    @Test
    public boolean test31() {
        try {
            Bug6756677Test test3 = new Bug6756677Test();
            ClassLoader cl = (test3.getClass()).getClassLoader();
            System.setProperty(XMLInputFactoryID, XMLInputFactoryClassName);
            XMLInputFactory xif = XMLInputFactory.newInstance(XMLInputFactoryID, cl);
            if (xif instanceof XMLInputFactory) {
                System.out.println(" test31() passed");
                return PASSED;
            } else {
                System.out.println(" test31() failed");
                Temp_Result = "test31() failed: xif not an instance of XMLInputFactory ";
                return FAILED;
            }
        } catch (javax.xml.stream.FactoryConfigurationError fce) {
            System.out.println("Failed : FactoryConfigurationError in test31 " + fce);
            Temp_Result = "test31() failed ";
            return FAILED;
        } catch (Exception e) {
            System.out.println("Failed : Exception in test31 " + e);
            Temp_Result = "test31() failed ";
            return FAILED;
        }
    }
}
