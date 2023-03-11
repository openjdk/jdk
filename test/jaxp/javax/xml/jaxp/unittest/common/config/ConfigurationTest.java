/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package common.config;

import java.lang.reflect.Method;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 *
 * @author huizwang
 */
public class ConfigurationTest {
    static final String SP_CONFIG = "jdk.xml.config.file";
    static final String SP_ENTITY_EXPANSION = "jdk.xml.entityExpansionLimit";


    static final boolean isWindows = System.getProperty("os.name").contains("Windows");
    static String SRC_DIR = System.getProperty("test.src", ".");
    static String TEST_SOURCE_DIR;
    static {
        System.out.println(SRC_DIR);
        String testroot;
        if (isWindows) {
            SRC_DIR = SRC_DIR.replace('\\', '/');
            testroot = SRC_DIR.substring(1, SRC_DIR.lastIndexOf("/") + 1);
        } else {
            testroot = SRC_DIR.substring(0, SRC_DIR.lastIndexOf("/") + 1);
        }
        TEST_SOURCE_DIR = SRC_DIR + "/files/";
        System.out.println(testroot);
    }

   /*
     * DataProvider for testing the configuration file and system property.
     *
     * Fields:
     *     configuration file, property name, property value
     */
    @DataProvider(name = "getProperty")
    public Object[][] getProperty() {

        return new Object[][]{
            {null, SP_ENTITY_EXPANSION, "64000"},
            {"jaxp.properties", SP_ENTITY_EXPANSION, "1000"},
        };
    }

    static String getPath(String file) {
        String temp = TEST_SOURCE_DIR + file;
        if (isWindows) {
            temp = "/" + temp;
        }
        return temp;
    }
}
