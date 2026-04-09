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

package stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.FactoryFindTest
 * @summary Test SaTX factory using factory property and using ContextClassLoader.
 */
public class FactoryFindTest {
    final static String FACTORY_KEY = "javax.xml.stream.XMLInputFactory";

    @Test
    @Disabled // due to 8156508
    public void testFactoryFindUsingStaxProperties() throws Exception {
        // If property is defined, will take precendence so this test
        // is ignored :(
        Assumptions.assumeTrue(System.getProperty(FACTORY_KEY) == null);

        Properties props = new Properties();
        String configFile = System.getProperty("java.home") + File.separator + "lib" + File.separator + "stax.properties";

        File f = new File(configFile);
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                props.load(fis);
            }
        } else {
            props.setProperty(FACTORY_KEY, "com.sun.xml.internal.stream.XMLInputFactoryImpl");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                props.store(fos, null);
            }
            f.deleteOnExit();
        }

        XMLInputFactory factory = XMLInputFactory.newInstance();
        assertEquals(factory.getClass().getName(), props.getProperty(FACTORY_KEY));
    }

    @Test
    public void testFactoryFind() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        assertNull(factory.getClass().getClassLoader());

        Thread.currentThread().setContextClassLoader(null);
        factory = XMLInputFactory.newInstance();
        assertNull(factory.getClass().getClassLoader());

        MyClassLoader clInput = new MyClassLoader();
        Thread.currentThread().setContextClassLoader(clInput);
        XMLInputFactory.newInstance();
        // because it's decided by having sm or not in FactoryFind code
        assertTrue(clInput.wasUsed);

        XMLOutputFactory ofactory = XMLOutputFactory.newInstance();
        assertNull(ofactory.getClass().getClassLoader());

        Thread.currentThread().setContextClassLoader(null);
        ofactory = XMLOutputFactory.newInstance();
        assertNull(ofactory.getClass().getClassLoader());

        MyClassLoader clOutput = new MyClassLoader();
        Thread.currentThread().setContextClassLoader(clOutput);
        XMLOutputFactory.newInstance();
        assertTrue(clOutput.wasUsed);
    }

    private static class MyClassLoader extends URLClassLoader {
        boolean wasUsed = false;

        public MyClassLoader() {
            super(new URL[0]);
        }

        public Class<?> loadClass(String name) throws ClassNotFoundException {
            wasUsed = true;
            return super.loadClass(name);
        }
    }
}
