/*
 * Copyright (c) 2026, Red Hat, Inc.
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
 * @summary Ensures java.security is loadable in Windows, even when the user
 * does not have permissions on one of the parent directories.
 * @bug 8352728
 * @requires os.family == "windows"
 * @library /test/lib
 * @run main WindowsParentDirPermissions
 */

public class WindowsParentDirPermissions {
    private static AutoCloseable restrictedAcl(Path path) throws IOException {
        AclFileAttributeView view =
                Files.getFileAttributeView(path, AclFileAttributeView.class);
        List<AclEntry> originalAcl = List.copyOf(view.getAcl());
        view.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.DENY)
                .setPrincipal(Files.getOwner(path)).build()));
        return () -> view.setAcl(originalAcl);
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("JDK-8352728-tmp-");
        try (AutoCloseable a1 = () -> FileUtils.deleteFileTreeUnchecked(temp)) {
            // Copy the jdk to a different directory
            Path originalJdk = Path.of(System.getProperty("test.jdk"));
            Path jdk = temp.resolve("jdk-parent-dir", "jdk");
            Files.createDirectories(jdk);
            FileUtils.copyDirectory(originalJdk, jdk);

            // Remove current user permissions from jdk-parent-dir
            try (AutoCloseable a2 = restrictedAcl(jdk.getParent())) {
                // Make sure the permissions are affecting the current user
                try {
                    jdk.toRealPath();
                    throw new jtreg.SkippedException("Must run non-elevated!");
                } catch (IOException expected) { }

                // Execute the copied jdk, ensuring java.security.Security is
                // loaded (i.e. use -XshowSettings:security:properties)
                ProcessTools.executeProcess(new ProcessBuilder(
                        List.of(jdk.resolve("bin", "java.exe").toString(),
                                "-Djava.security.debug=properties",
                                "-XshowSettings:security:properties",
                                "-version"))).shouldHaveExitValue(0);
            }
        }
        System.out.println("TEST PASS - OK");
    }
}
