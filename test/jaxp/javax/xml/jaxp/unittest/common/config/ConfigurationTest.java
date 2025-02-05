/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.DataProvider;

/**
 * Verifies the configuration file and precedence:
 *     settings in the configuration file are used as the default values of properties;
 *     any settings in a custom configuration file override those in the default
 * configuration.
 */
public class ConfigurationTest extends ImplProperties {
    // system property for custom configuration file
    static final String SP_CONFIG = "java.xml.config.file";
    // Impl-Specific Property: entity expansion
    static final String ISP_ENTITY_EXPANSION = "jdk.xml.entityExpansionLimit";
    // Impl-Specific Property: parameter entity limit
    static final String ISP_PARAMETER_ENTITY = "jdk.xml.maxParameterEntitySizeLimit";
    // Impl-Specific Property: element attribute limit
    static final String ISP_ELEMENT_ATTRIBUTE = "jdk.xml.elementAttributeLimit";
    // Impl-Specific Property: XML name limit
    static final String ISP_NAME_LIMIT = "jdk.xml.maxXMLNameLimit";

    // Impl-Specific Feature: extension functions
    static final String ISF_EXTENSION_FUNCTIONS = "jdk.xml.enableExtensionFunctions";
    // Catalog feature: resolve
    static final String CATALOG_RESOLVE = "javax.xml.catalog.resolve";
    // The USE_CATALOG property indicates whether Catalog is enabled for a processor
    static final String USE_CATALOG = "http://javax.xml.XMLConstants/feature/useCatalog";
    static final String SP_USE_CATALOG = "javax.xml.useCatalog";


    static final boolean IS_WINDOWS = System.getProperty("os.name").contains("Windows");
    static final String SRC_DIR;
    static final String TEST_SOURCE_DIR;
    static {
        String srcDir = System.getProperty("test.src", ".");
        if (IS_WINDOWS) {
            srcDir = srcDir.replace('\\', '/');
        }
        SRC_DIR = srcDir;
        TEST_SOURCE_DIR = srcDir + "/files/";
    }

    static enum PropertyType { FEATURE, PROPERTY };

   /*
     * DataProvider for testing the configuration file and system property.
     *
     * Fields:
     *     configuration file, property name, property type, property value
     */
    @DataProvider(name = "getProperty")
    public Object[][] getProperty() {
        /**
         * Test cases for verifying the configuration file
         */
        return new Object[][]{
            // default value is expected for property (PARAMETER_ENTITY) not
            // set in the default and custom configuration files
            {null, ISP_PARAMETER_ENTITY, PROPERTY_VALUE[PROPERTY_VALUE_JDK24][INDEX_PE]},
            // this property is set in the default (jaxp.properties),
            // but not the custom configuration file. Expects readings from the
            // default config
            {null, ISP_NAME_LIMIT, PROPERTY_VALUE[PROPERTY_VALUE_JDK24][INDEX_NAME]},
            // the property in the default configuration file (jaxp.properties)
            // will be read and used as the default value of the property
            {null, ISP_ENTITY_EXPANSION, PROPERTY_VALUE[PROPERTY_VALUE_JDK24][INDEX_EE]},
        };
    }

    @DataProvider(name = "getProperty0")
    public Object[][] getProperty0() {
        /**
         * Duplicate of getProperty to include the case that uses the system
         * property to set up a custom configuration file. This is to avoid
         * interfering with other test cases.
         */
        return new Object[][]{
            // the setting in the custom configuration file will override that
            // in the default one
            {"customJaxp.properties", ISP_ENTITY_EXPANSION, "1000"},
        };
    }


    static String getPath(String file) {
        String temp = TEST_SOURCE_DIR + file;
        if (IS_WINDOWS) {
            temp = "/" + temp;
        }
        return temp;
    }
}
