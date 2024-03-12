/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8286779
 * @summary Test limited/default_local.policy containing inconsistent entries
 * @library /test/lib
 * @run testng/othervm InconsistentEntries
 */

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.crypto.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;

public class InconsistentEntries {

    private static final String JDK_HOME = System.getProperty("test.jdk");
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final Path POLICY_DIR = Paths.get(JDK_HOME, "conf", "security",
            "policy", "testlimited");
    private static final Path POLICY_FILE = Paths.get(TEST_SRC, "default_local.policy");

    Path targetFile = null;

    @BeforeTest
    public void setUp() throws IOException {
        if (!POLICY_DIR.toFile().exists()) {
            Files.createDirectory(POLICY_DIR);
        }

        targetFile = POLICY_DIR.resolve(POLICY_FILE.getFileName());
        Files.copy(POLICY_FILE, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterTest
    public void cleanUp() throws IOException {
        Files.delete(targetFile);
    }

    @Test
    public void test() throws Exception {
        String JAVA_HOME = System.getProperty("java.home");
        String FS = System.getProperty("file.separator");
        Path testlimited = Path.of(JAVA_HOME + FS + "conf" + FS + "security" +
                FS + "policy" + FS + "testlimited");
        if (!Files.exists(testlimited)) {
            throw new RuntimeException(
                    "custom policy subdirectory: testlimited does not exist");
        }

        File testpolicy = new File(JAVA_HOME + FS + "conf" + FS + "security" +
                FS + "policy" + FS + "testlimited" + FS + "default_local.policy");
        if (testpolicy.length() == 0) {
            throw new RuntimeException(
                    "policy: default_local.policy does not exist or is empty");
        }

        Security.setProperty("crypto.policy", "testlimited");

        Assert.assertThrows(ExceptionInInitializerError.class,
                () -> Cipher.getMaxAllowedKeyLength("AES"));
    }
}
