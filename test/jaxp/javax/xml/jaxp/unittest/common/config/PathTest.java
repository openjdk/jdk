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

import static common.config.ConfigurationTest.SP_CONFIG;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test @bug 8303530
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run testng/othervm common.config.PathTest
 * @summary verifies that the system property "java.xml.config.file" may be set
 * with a relative path.
 */
public class PathTest extends ConfigurationTest {
    private static final String FILE_DIR = "files";
    private static final String CUSTOM_CONFIG = "customJaxp.properties";

    /*
     * Sets up the test environment by copying the customJaxp.properties file
     * to a directory under the current working directory of the JVM.
    */
    @BeforeClass
    public static void setup() throws IOException {
        Path userDir = Paths.get(System.getProperty("user.dir", "."));
        Path fileDir = Paths.get(userDir.toString(), FILE_DIR);

        if (Files.notExists(fileDir)) {
            Files.createDirectory(fileDir);
        }

        Path source = Paths.get(TEST_SOURCE_DIR, CUSTOM_CONFIG);
        Path dest = Paths.get(fileDir.toString(), CUSTOM_CONFIG);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    /*
     * Verifies a user-defined configuration file can be set with a relative path.
     * This test is the same as other Property tests, except the java.xml.config.file
     * system property is set with a relative path.
    */
    @Test(dataProvider = "getProperty0")
    public void testProperty(String config, String property, String expected) throws Exception {
        if (config != null) {
            // set with a relative path instead of the absolute path from getPath
            System.setProperty(SP_CONFIG, FILE_DIR + "/" + config);
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Assert.assertEquals(dbf.getAttribute(property), expected);
    }
}
