/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4619757
 * @summary User Policy Setting is not recognized on Netscape 6
 *          when invoked as root.
 * @library /test/lib
 * @requires os.family != "windows"
 * @run testng/othervm/manual Root
 */

/*
* Run test as root user.
* */

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.*;

public class Root {
    private static final String SRC = System.getProperty("test.src");
    private static final String ROOT = System.getProperty("user.home");
    private static final Path SOURCE = Paths.get(SRC, "Root.policy");
    private static final Path TARGET = Paths.get(ROOT, ".java.policy");
    private static final Path BACKUP = Paths.get(ROOT, ".backup.policy");
    private static final String ROOT_USER_ID = "0";

    @BeforeTest
    public void setup() throws IOException {
        // Backup user policy file if it already exists
        if (TARGET.toFile().exists()) {
            Files.copy(TARGET, BACKUP, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(SOURCE, TARGET, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterTest
    public void cleanUp() throws IOException {
        Files.delete(TARGET);
        // Restore original policy file if backup exists
        if (BACKUP.toFile().exists()) {
            Files.copy(BACKUP, TARGET, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(BACKUP);
        }
    }

    @Test
    private void test() throws InterruptedException, IOException {
        System.out.println("Run test as root user.");

        Process process = Runtime.getRuntime().exec("id -u");
        process.waitFor();
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to retrieve user id.");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();

            if (!ROOT_USER_ID.equals(line)) {
                throw new RuntimeException(
                        "This test needs to be run with root privilege.");
            }
        }

        Policy p = Policy.getPolicy();
        Assert.assertTrue(p.implies(Root.class.getProtectionDomain(),
                new AllPermission()));
    }
}
