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

/* @test
 * @bug 8308678
 * @requires (os.family == "mac")
 * @summary Verify UnixPath::toRealPath falls back if no perms on macOS
 * @run junit/othervm -Djava.security.manager=allow MacToRealPathWithSM
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MacToRealPathWithSM {
    private static final String POLICY_FILE = "MacToRealPath.policy";

    private static Path src;
    private static Path path;

    @BeforeAll
    public static void setup() throws IOException {
        String testSrc = System.getProperty("test.src");
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (testSrc == null || tmpDir == null)
            throw new RuntimeException("This test must be run by jtreg");
        System.out.printf("testSrc: %s%ntmpDir:  %s%n", testSrc, tmpDir);

        src = Path.of(testSrc);
        Path tmp = Path.of(tmpDir);

        path = Files.createTempFile(tmp, "bonjour", ".txt");
        path.toFile().deleteOnExit();

        // Write to the path
        Files.writeString(path, "\nBonjour, tout le monde!\n");
        System.out.println(Files.readString(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", POLICY_FILE})
    @SuppressWarnings("removal")
    public void testToRealPath(String policyFile) throws IOException {
        // Install security manager with the given policy file
        if (!policyFile.isEmpty()) {
            System.setProperty("java.security.policy",
                src.resolve(policyFile).toString());
            System.setSecurityManager(new SecurityManager());
        }

        // Derive real path. Without the source change for this issue applied,
        // if a SecurityManager is used which does not grant read permission
        // for traversing "path" down from its root, an AccessContolException
        // is thrown by UnixPath::toRealPath
        assertDoesNotThrow(() ->
            System.out.printf("real path: %s%n", path.toRealPath()),
            "UnixPath::toRealPath() failed");
        assertDoesNotThrow(() ->
            System.out.printf("real path no follow: %s%n",
                              path.toRealPath(LinkOption.NOFOLLOW_LINKS)),
            "UnixPath::toRealPath(LinkOption.NOFOLLOW_LINKS) failed");
    }
}
