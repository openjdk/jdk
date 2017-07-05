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

package javax.xml.parsers.ptests;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.SAXParserFactory;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Class containing the test cases for SAXParserFactory/DocumentBuilderFactory
 * newInstance methods.
 */
public class FactoryConfErrorTest {

    /**
     * Set properties DocumentBuilderFactory and SAXParserFactory to invalid
     * value before any test run.
     */
    @BeforeTest
    public void setup() {
        System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "xx");
        System.setProperty("javax.xml.parsers.SAXParserFactory", "xx");
    }

    /**
     * Restore properties DocumentBuilderFactory and SAXParserFactory to default
     * value after all tests run.
     */
    @AfterTest
    public void cleanup() {
        System.clearProperty("javax.xml.parsers.DocumentBuilderFactory");
        System.clearProperty("javax.xml.parsers.SAXParserFactory");
    }

    /**
     * To test exception thrown if javax.xml.parsers.SAXParserFactory property
     * is invalid.
     */
    @Test(expectedExceptions = FactoryConfigurationError.class)
    public void testNewInstance01() {
        SAXParserFactory.newInstance();
    }

    /**
     * To test exeception thrown if javax.xml.parsers.DocumentBuilderFactory is
     * invalid.
     */
    @Test(expectedExceptions = FactoryConfigurationError.class)
    public void testNewInstance02() {
        DocumentBuilderFactory.newInstance();
    }
}
