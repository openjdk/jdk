/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8005280 8004371
 * @summary Compatibility test
 * @run main CompatibilityTest
 * @run main/othervm -Dsun.util.spi.XmlPropertiesProvider=jdk.internal.util.xml.BasicXmlPropertiesProvider CompatibilityTest
 */

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This is a behavior compatibility test.
 * Although not defined by the properties.dtd, the constructs
 * in Compatibility.xml are supported by the regular JDK XML
 * Provider.
 *
 * @author: Joe Wang
 */
public class CompatibilityTest {

    public static void main(String[] args) {
        testInternalDTD();
    }

    /*
     * Not in the spec, but the constructs work with the current JDK
     */
    static void testInternalDTD() {
        String src = System.getProperty("test.src");
        if (src == null) {
            src = ".";
        }
        loadPropertyFile(src + "/Compatibility.xml");
    }

    /*
     * 'Store' the populated 'Property' with the specified 'Encoding Type' as an
     * XML file. Retrieve the same XML file and 'load' onto a new 'Property' object.
     */
    static void loadPropertyFile(String filename) {
        try (InputStream in = new FileInputStream(filename)) {
            Properties prop = new Properties();
            prop.loadFromXML(in);
            verifyProperites(prop);
        } catch (IOException ex) {
            fail(ex.getMessage());
        }
    }

    /*
     * This method verifies the first key-value with the original string.
     */
    static void verifyProperites(Properties prop) {
        try {
            for (String key : prop.stringPropertyNames()) {
                String val = prop.getProperty(key);
                if (key.equals("Key1")) {
                    if (!val.equals("value1")) {
                        fail("Key:" + key + "'s value: \nExpected: value1\nFound: " + val);
                    }
                } else if (key.equals("Key2")) {
                    if (!val.equals("<value2>")) {
                        fail("Key:" + key + "'s value: \nExpected: <value2>\nFound: " + val);
                    }
                } else if (key.equals("Key3")) {
                    if (!val.equals("value3")) {
                        fail("Key:" + key + "'s value: \nExpected: value3\nFound: " + val);
                    }
                }
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }

    }

    static void fail(String err) {
        throw new RuntimeException(err);
    }

}
