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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jtreg.SkippedException;
import jdk.test.lib.Asserts;
import jdk.tools.jlink.internal.LinkableRuntimeImage;
import tests.Helper;

/* @test
 * @bug 8377819
 * @summary Test the --security-properties plugin
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 * @build tests.*
 * @run main/othervm SecurityPropertiesPluginTest
 */

public class SecurityPropertiesPluginTest {

    private static Helper helper;

    private static final String SECPROPS_PATH = "conf/security/java.security";
    private static final String TEST_DIR = System.getProperty("test.dir", ".");
    private static final boolean LINKABLE_RUNTIME =
            LinkableRuntimeImage.isLinkableRuntime();

    public static void main(String[] args) throws Throwable {

        helper = Helper.newHelper(LINKABLE_RUNTIME);
        if (helper == null) {
            throw new SkippedException("Test not run");
        }

        /*
         * Test props option with file containing 5 properties:
         * one that overrides a current property,
         * one that is a user-defined property,
         * two include properties (it should only use the 2nd one)
         * one that overrides a multi-valued property.
         */
        Map<String, String> propMap =
                Map.of("keystore.type", "bogus",
                       "foo", "bar",
                       "include", "file1",
                       "jdk.certpath.disabledAlgorithms", "MD2");
        Path p = writePropsToFile(propMap, "test.security");
        test("modOne", p.toString(), propMap);

        // test illegal/bad options
        testBadOptions();
    }

    private static void test(String module, String option,
        Map<String, String> propMap) throws Exception {

        helper.generateDefaultJModule(module);
        Path image = helper.generateDefaultImage(
                new String[] { "--security-properties", option }, module)
                .assertSuccess();
        helper.checkImage(image, module, null, null,
                new String[] { SECPROPS_PATH });

        Properties props = new Properties();
        try (FileInputStream fis =
                new FileInputStream(image.resolve(SECPROPS_PATH).toFile())) {
            props.load(fis);
        }

        propMap.forEach((k, v) ->
            Asserts.assertEquals(v, props.getProperty(k)));

        // check include is last line
        List<String> lines = Files.readAllLines(image.resolve(SECPROPS_PATH));
        Asserts.assertEquals(lines.getLast(), "include=file1");
    }

    private static void testBadOptions() throws Exception {

        // non-existent props file
        String module = "testBad";
        helper.generateDefaultJModule(module);
        helper.generateDefaultImage(new String[]
                { "--security-properties", "nonexistent-file" }, module)
                .assertFailure("java.io.FileNotFoundException: " +
                               "nonexistent-file (No such file or directory)");
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
