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
 * @run main/othervm -Djava.security.manager=allow MacToRealPathWithSM MacToRealPath.policy
 */
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class MacToRealPathWithSM {
    public static void main(String[] args) throws Exception {
        String policyFile = args[0];
        String testSrc = System.getProperty("test.src");
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (testSrc == null || tmpDir == null)
            throw new RuntimeException("This test must be run by jtreg");
        System.out.println("testSrc: " + testSrc);
        System.out.println("tmpDir: " + tmpDir);

        Path src = Path.of(testSrc);
        Path tmp = Path.of(tmpDir);

        Path path = Files.createTempFile(tmp, "bonjour", ".txt");
        path.toFile().deleteOnExit();

        // Write to the path
        Files.writeString(path, "\nBonjour, tout le monde!\n");
        System.out.println(Files.readString(path));

        // Install security manager with the given policy file
        System.setProperty("java.security.policy",
            src.resolve(policyFile).toString());
        System.setSecurityManager(new SecurityManager());

        // Derive real path
        System.out.printf("real path: %s%n", path.toRealPath());
        System.out.printf("real path no follow: %s%n",
                          path.toRealPath(LinkOption.NOFOLLOW_LINKS));
    }
}
