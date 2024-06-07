/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309841
 * @summary Jarsigner should print a warning if an entry is removed
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.util.JarUtils;

import java.nio.file.Files;
import java.nio.file.Path;

public class RemovedFiles {
    public static void main(String[] args) throws Exception {
        JarUtils.createJarFile(
                Path.of("a.jar"),
                Path.of("."),
                Files.writeString(Path.of("a"), "a"),
                Files.writeString(Path.of("b"), "b"));
        SecurityTools.keytool("-genkeypair -storepass changeit -keystore ks -alias x -dname CN=x -keyalg ed25519");
        SecurityTools.jarsigner("-storepass changeit -keystore ks a.jar x");

        // Remove an entry after signing. There will be a warning.
        JarUtils.deleteEntries(Path.of("a.jar"), "a");
        SecurityTools.jarsigner("-verify -verbose a.jar")
                .shouldContain("Warning: nonexistent signed entries detected: [a]");

        // Re-sign will clean up the SF file.
        SecurityTools.jarsigner("-storepass changeit -keystore ks a.jar x");
        SecurityTools.jarsigner("-verify -verbose a.jar")
                .shouldNotContain("Warning: nonexistent signed entries detected: [a]");
    }
}
