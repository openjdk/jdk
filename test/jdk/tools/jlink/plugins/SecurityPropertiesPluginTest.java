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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import jtreg.SkippedException;
import jdk.test.lib.Asserts;
import jdk.test.lib.util.FileUtils;
import jdk.tools.jlink.internal.LinkableRuntimeImage;
import tests.Helper;
import tests.JImageGenerator;
import tests.JImageGenerator.JLinkTask;
import tests.JImageGenerator.JModTask;

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
    private static final String DELIMS = "=: ";

    // Replacement for java.security file
    private static final String JAVA_SECURITY_REPLACEMENT =
        """
        # Normal comment
            # Comment with leading spaces
        ! Comment with '!'
        \t# Comment with leading tab
        \f# Comment with leading form-feed
        # Next 3 are blank lines

        \n
        \r\n
        # Normal property
        foo=bar
        # JDK property
        jdk.certpath.disabledAlgorithms=MD4
        # Property with leading spaces
            bar=foo
        # Property with delimiter ("=") in name, must be escaped
        ba\\=z=foo
        # Multi-lined property with space delimiter
        fruits                           apple, banana, pear, \\
                                         cantaloupe, watermelon, \\
                                         kiwi, mango
        # Property with ':' delimiter
        Truth:Beauty
        """;

    /*
     * Test with file containing the following properties:
     * one that overrides a current property,
     * one that is a user-defined property,
     * two include properties (it should only use the 2nd one)
     * one that overrides a multi-lined value property,
     * one that uses a character that is encoded differently in
     * ISO-8859-1 vs. UTF-8,
     * one that contains an empty string as the value.
     * one that is a multi-lined value property.
     *
     * Each entry contains the key, value, and the expected value after
     * loading/parsing.
     */
    private static final String[][] EXTRA_PROPS = new String[][] {
        {"keystore.type", "bogus", "bogus"},
        {"foo", "bar", "bar"},
        {"include", "doNotUse", "use"},
        {"include", "use", "use"},
        {"jdk.certpath.disabledAlgorithms", "MD2", "MD2"},
        {"iso_8859_1_char", "é", "é"},
        {"empty", "", ""},
        {"equalSign", "\\=", "="},
        {"multiLine", "multi-line \\\n value", "multi-line value"}
    };

    public static void main(String[] args) throws Throwable {

        helper = Helper.newHelper(LINKABLE_RUNTIME);
        if (helper == null) {
            throw new SkippedException("Test not run: no linkable runtime");
        }

        writePropsToFile("test.security");

        testWithDefaultJDKImage();
        testWithCustomJDKImage();

        // test illegal/bad options
        testBadOptions();
    }

    private static void testWithDefaultJDKImage() throws Exception {

        helper.generateDefaultJModule("defaultModule");
        Path image = helper.generateDefaultImage(
                new String[] { "--security-properties", "test.security" },
                "defaultModule").assertSuccess();
        helper.checkImage(image, "defaultModule", null, null,
                new String[] { SECPROPS_PATH });

        testImage(image);
    }

    private static void testWithCustomJDKImage() throws Exception {

        // Copy JDK's jmods directory
        Path jdkJmodsPath = Path.of(System.getProperty("test.jdk"), "jmods");
        Path jdkJmodsCopyPath = Path.of(TEST_DIR, "jmodsCopy");
        FileUtils.copyDirectory(jdkJmodsPath, jdkJmodsCopyPath);

        // Extract contents of jmods copy
        JModTask jmodTask = JImageGenerator.getJModTask()
            .jmod(jdkJmodsCopyPath.resolve("java.base.jmod"))
            .option("--dir")
            .option("java.base.jmod.extracted");
        jmodTask.extract().assertSuccess();

        // Replace JDK's java.security file with test version. First, sanity
        // check syntax of replacement by loading it in Properties object.
        Properties props = new Properties();
        props.load(new ByteArrayInputStream(
            JAVA_SECURITY_REPLACEMENT.getBytes(StandardCharsets.ISO_8859_1)));

        // Now replace the extracted JDK's java.security file
        Path extractedPath = Path.of(TEST_DIR, "java.base.jmod.extracted");
        Files.copy(new ByteArrayInputStream(
            JAVA_SECURITY_REPLACEMENT.getBytes(StandardCharsets.ISO_8859_1)),
            extractedPath.resolve(SECPROPS_PATH),
            StandardCopyOption.REPLACE_EXISTING);

        // Create a new java.base.jmod with replacement java.security file
        jmodTask = JImageGenerator.getJModTask()
            .addClassPath(extractedPath.resolve("classes"))
            .addCmds(extractedPath.resolve("bin"))
            .addConfig(extractedPath.resolve("conf"))
            .addNativeLibraries(extractedPath.resolve("lib"))
            .jmod(Path.of(TEST_DIR, "jmods/java.base.jmod"));
        jmodTask.create().assertSuccess();

        // Create default module for testing
        Path customModule =
            helper.generateDefaultJModule("customModule").assertSuccess();

        // Create second image using custom module and new java.base.jmod
        Path customImage = Path.of(TEST_DIR, "images/customModule.image");
        JLinkTask jLinkTask = JImageGenerator.getJLinkTask()
            .modulePath(Path.of(TEST_DIR, "jmods").toString())
            .output(customImage)
            .addMods("customModule")
            .limitMods("customModule")
            .option("--security-properties")
            .option("test.security");
        jLinkTask.call().assertSuccess();

        testImage(customImage);
    }

    private static void testImage(Path image) throws Exception {

        Path secPropsPath = image.resolve(SECPROPS_PATH);
        Properties javasecProps = new Properties();
        try (FileInputStream fis =
                new FileInputStream(secPropsPath.toFile())) {
            javasecProps.load(fis);
        }

        for (String[] prop : EXTRA_PROPS) {
            Asserts.assertEquals(prop[2], javasecProps.getProperty(prop[0]));
        }

        // check include is last line
        List<String> lines = Files.readAllLines(secPropsPath,
            StandardCharsets.ISO_8859_1);
        Asserts.assertEquals(lines.getLast(), "include use");

        // Make sure override of "jdk.certpath.disabledAlgorithms"
        // is in the right place
        Asserts.assertLessThan(
            lines.indexOf("jdk.certpath.disabledAlgorithms=MD2"),
            lines.size() - EXTRA_PROPS.length);
    }

    private static void testBadOptions() throws Exception {

        // non-existent props file
        String module = "testBad";
        helper.generateDefaultJModule(module);
        helper.generateDefaultImage(new String[]
                { "--security-properties", "nonexistent-file" }, module)
                .assertFailure("java.io.FileNotFoundException: " +
                               "nonexistent-file");
    }

    private static void writePropsToFile(String filename) throws Exception {
        Random r = new Random();
        Path p = Path.of(TEST_DIR, filename);
        // write some comments in both formats ('#' and '!') and blank line
        StringBuilder sb = new StringBuilder();
        sb.append("# Test properties file\n")
          .append("    # Test properties file\n")
          .append("! Test properties file\n")
          .append("     ");
        // use random delimiter
        for (String[] prop : EXTRA_PROPS) {
            sb.append(prop[0] + DELIMS.charAt(r.nextInt(DELIMS.length()))
                              + prop[1] + System.lineSeparator());
        }
        Files.writeString(p, sb, StandardCharsets.ISO_8859_1);
    }
}
