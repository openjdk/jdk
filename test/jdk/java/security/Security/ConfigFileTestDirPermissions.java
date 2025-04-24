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

import static java.nio.file.StandardOpenOption.APPEND;

/*
 * @test
 * @summary Ensures java.security is loadable in Windows and filesystem
 * soft-links are resolved, even when the user does not have permissions
 * on one of the parent directories.
 * @bug 8352728
 * @requires os.family == "windows"
 * @library /test/lib
 * @run main ConfigFileTestDirPermissions
 */

public class ConfigFileTestDirPermissions {
    private static final String LF = System.lineSeparator();
    private static final String TEST_PROPERTY =
            "test.property.name=test_property_value";

    // Unlike symbolic links, directory junctions do not require elevation
    private static void createJunction(Path target, Path link)
            throws IOException, InterruptedException {
        if (!Files.isDirectory(target)) {
            throw new IOException("The target must be a directory: " + target);
        }
        int exitCode =
                new ProcessBuilder("cmd", "/c", "MKLINK", "/J", link.toString(),
                        target.toString()).inheritIO().start().waitFor();
        if (exitCode != 0) {
            throw new IOException("Unexpected exit code: " + exitCode);
        }
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("JDK-8352728-tmp-");
        // We will create the following directories structure:
        //
        // 📁 JDK-8352728-tmp-*/
        // ├─🔒 jdk-parent-dir/         (ACL with REMOVED-PERMISSIONS)
        // │ └─📁 jdk/
        // │   ├─📁 conf/
        // │   │ ├─📁 security/
        // │   │ │ ├─📄 java.security
        // │   │ │ │    📝 include link-to-other-dir/other.properties
        // │   │ │ ├─🔗 link-to-other-dir/ ⟹ 📁 JDK-8352728-tmp-*/other-dir
        // │   │ │ └─...               (JUNCTION)
        // │   │ └─...
        // │   └─...
        // ├─📁 other-dir/
        // │ └─📄 other.properties
        // │      📝 include ../relatively.included.properties
        // └─📄 relatively.included.properties
        //      📝 test.property.name=test_property_value
        try {
            // Copy the jdk to a different directory
            Path originalJdk = Path.of(System.getProperty("test.jdk"));
            Path jdk = temp.resolve("jdk-parent-dir", "jdk");
            Files.createDirectories(jdk);
            FileUtils.copyDirectory(originalJdk, jdk);

            // Create a properties file with a relative include in it
            Path otherDir = temp.resolve("other-dir");
            Files.createDirectories(otherDir);
            Path other = otherDir.resolve("other.properties");
            Path included = temp.resolve("relatively.included.properties");
            Files.writeString(included, TEST_PROPERTY + LF);
            Files.writeString(other,
                    "include ../" + included.getFileName() + LF);

            // Create a junction to the properties file dir, from the jdk dir
            Path javaSec = jdk.resolve("conf", "security", "java.security");
            Path linkDir = javaSec.resolveSibling("link-to-other-dir");
            createJunction(otherDir, linkDir);

            // Include the properties file from java.security (through the link)
            Files.writeString(javaSec,
                    LF + "include " + linkDir.getFileName() + "/" +
                            other.getFileName() + LF, APPEND);

            // Remove current user permissions from jdk-parent-dir
            Path parent = jdk.getParent();
            AclFileAttributeView view = Files.getFileAttributeView(parent,
                    AclFileAttributeView.class);
            List<AclEntry> originalAcl = List.copyOf(view.getAcl());
            view.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.DENY)
                    .setPrincipal(Files.getOwner(parent)).build()));

            try {
                // Make sure the permissions are affecting the current user
                try {
                    jdk.toRealPath();
                    throw new jtreg.SkippedException("Must run non-elevated!");
                } catch (IOException expected) { }

                // Execute the copied jdk, ensuring java.security.Security is
                // loaded (i.e. use -XshowSettings:security:properties)
                ProcessTools.executeProcess(new ProcessBuilder(
                                jdk.resolve("bin", "java.exe").toString(),
                                "-Djava.security.debug=properties",
                                "-XshowSettings:security:properties",
                                "-version"))
                        .shouldHaveExitValue(0)
                        .shouldContain(TEST_PROPERTY);
            } finally {
                view.setAcl(originalAcl);
            }
        } finally {
            FileUtils.deleteFileTreeUnchecked(temp);
        }

        System.out.println("TEST PASS - OK");
    }
}
