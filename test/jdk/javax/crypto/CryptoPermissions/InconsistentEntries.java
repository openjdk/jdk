/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.crypto.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;

public class InconsistentEntries {

    private static final String JDK_HOME = System.getProperty("test.jdk", ".");
    private static final String TEST_SRC = System.getProperty("test.src", ".");
    private static final Path TEMP_JDK_HOME = Path.of("java");
    private static final Path POLICY_DIR = TEMP_JDK_HOME.resolve(Path.of("conf", "security",
            "policy", "testlimited"));
    private static final Path POLICY_FILE_SRC = Paths.get(TEST_SRC, "default_local.policy");
    private static final Path POLICY_FILE_TARGET = POLICY_DIR
        .resolve(POLICY_FILE_SRC.getFileName());

    @BeforeTest
    public void setUp() throws Exception {
        // Clone the tested JDK to the scratch directory
        FileUtils.copyDirectory(Path.of(JDK_HOME), TEMP_JDK_HOME);

        // create policy directory in the cloned JDK
        if (!POLICY_DIR.toFile().exists()) {
            Files.createDirectory(POLICY_DIR);
        }

        // copy policy file into policy directory
        Files.copy(POLICY_FILE_SRC, POLICY_FILE_TARGET, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void main(String[] args) throws Throwable {
        if (!Files.exists(POLICY_DIR)) {
            throw new RuntimeException(
                    "custom policy subdirectory: testlimited does not exist");
        }
        File testpolicy = new File(POLICY_FILE_TARGET.toString());
        if (testpolicy.length() == 0) {
            throw new RuntimeException(
                    "policy: default_local.policy does not exist or is empty");
        }

        Security.setProperty("crypto.policy", "testlimited");

        Assert.assertThrows(ExceptionInInitializerError.class,
                () -> Cipher.getMaxAllowedKeyLength("AES"));
    }

    @Test
    public void test() throws Exception {
        String tmpJava = TEMP_JDK_HOME.resolve("bin").resolve("java").toString();
        String[] args = Utils.prependTestJavaOpts(InconsistentEntries.class.getName());
        ProcessBuilder pb = new ProcessBuilder(tmpJava);
        pb.command().addAll(List.of(args));

        ProcessTools
            .executeProcess(pb)
            .shouldHaveExitValue(0);
    }
}
