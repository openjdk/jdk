/*
 * Copyright (c) 2025, Red Hat, Inc.
 *
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

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.List;

/*
 * @test
 * @summary Ensures java.security is loadable in Windows even when the user
 *          doesn't have permissions on the parent directory.
 * @bug 8352728
 * @requires os.family == "windows"
 * @library /test/lib
 * @run main ConfigFileTestDirPermissions
 */

public class ConfigFileTestDirPermissions {
    private static final Path JDK =
            Path.of("jdk_parent_dir", "jdk").toAbsolutePath();

    public static void main(String[] args) throws Exception {
        // Copy the JDK to a different directory
        Files.createDirectories(JDK);
        FileUtils.copyDirectory(Path.of(System.getProperty("test.jdk")), JDK);

        // Remove permissions from the parent directory
        Path parent = JDK.getParent();
        AclFileAttributeView view =
                Files.getFileAttributeView(parent, AclFileAttributeView.class);
        List<AclEntry> originalAcl = List.copyOf(view.getAcl());
        view.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.DENY)
                .setPrincipal(Files.getOwner(parent)).build()));

        try {
            // Check the permissions are affecting the current user
            try {
                JDK.toRealPath();
                throw new jtreg.SkippedException("Must run non-elevated!");
            } catch (IOException ignored) {}

            // Execute the copied JDK ensuring java.security.Security is loaded
            ProcessTools.executeProcess(new ProcessBuilder(List.of(
                    JDK.resolve("bin", "java.exe").toString(),
                    "-Djava.security.debug=properties",
                    "-XshowSettings:security:properties",
                    "-version"
            ))).shouldHaveExitValue(0);
        } finally {
            view.setAcl(originalAcl);
        }

        System.out.println("TEST PASS - OK");
    }
}
