/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import jtreg.SkippedException;
import jdk.test.lib.Asserts;
import tests.Helper;

/* @test
 * @bug 8377819
 * @summary Test the --security-properties plugin
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.*
 * @run main SecurityPropertiesPluginTest
 */

public class SecurityPropertiesPluginTest {

    private static Helper helper;

    private static String SECPROPS_PATH = "conf/security/java.security";
    private static String TEST_DIR = System.getProperty("test.dir", ".");

    public static void main(String[] args) throws Throwable {

        helper = Helper.newHelper();
        if (helper == null) {
            throw new SkippedException("Test not run");
        }

        /*
         * Test 3 properties: one that overrides a current property,
         * one that is a user-defined property, and one that
         * overrides a multi-valued property.
         */
        test("testOne", "one", Map.of("keystore.type", "bogus",
                                      "foo", "bar",
                                      "jdk.certpath.disabledAlgorithms", "MD2"));

        // test illegal/bad options
        testBadOptions();
    }

    private static void test(String module, String fileName,
        Map<String, String> propMap) throws Exception {

        Path p = writePropsToFile(propMap, fileName);
        helper.generateDefaultJModule(module);

        Path image = helper.generateDefaultImage(
                new String[] { "--security-properties", p.toString() }, module)
                .assertSuccess();
        helper.checkImage(image, module, null, null,
                new String[] { SECPROPS_PATH });

        Properties props = new Properties();
        try (FileInputStream fis =
                new FileInputStream(image.resolve(SECPROPS_PATH).toFile())) {
            props.load(fis);
        }

        propMap.forEach((k, v) ->
            Asserts.assertEquals(props.getProperty(k), v));
    }

    private static void testBadOptions() throws Exception {

        String module = "testBad";
        helper.generateDefaultJModule(module);
        helper.generateDefaultImage(new String[]
                { "--security-properties", "nonexistent-file" }, module)
                .assertFailure("java.io.FileNotFoundException: " +
                               "nonexistent-file (No such file or directory)");

        Path p = Path.of(TEST_DIR, "bad");
        Files.writeString(p, "include foo");
        helper.generateDefaultImage(new String[]
                { "--security-properties", p.toString() }, module)
                .assertFailure("Error: the include property is not supported");
    }

    private static Path writePropsToFile(Map<String, String> propMap,
            String filename) throws Exception {
        Path p = Path.of(TEST_DIR, filename);
        StringBuilder sb = new StringBuilder();
        propMap.forEach((k, v) -> sb.append(k + "=" + v
                                            + System.lineSeparator()));
        Files.writeString(p, sb);
        return p;
    }
}
