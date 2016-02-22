/*
 * Copyright (c) 2015 SAP SE. All rights reserved.
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

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import static java.nio.file.attribute.PosixFilePermission.*;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/*
 * @test
 * @bug 8132475
 * @summary Check that the executables in the current JDK image
 *          are executable by all users.
 * @run main ExecutableTest
 * @author Volker Simonis
 */

public class ExecutableTest {

    // The bin/ directory may contain non-executable files (see 8132704)
    private static final String[] exclude = { "jmc.ini" };
    private static final Set<String> excludeSet =
        new HashSet<String>(Arrays.asList(exclude));

    public static void main(String args[]) throws Throwable {
        String JAVA_HOME = System.getProperty("java.home");
        Path binPath = Paths.get(JAVA_HOME, "bin");
        DirectoryStream<Path> stream = Files.newDirectoryStream(binPath);
        EnumSet<PosixFilePermission> execPerms =
            EnumSet.of(GROUP_EXECUTE, OTHERS_EXECUTE, OWNER_EXECUTE);
        for (Path entry : stream) {
            if (excludeSet.contains(entry.getFileName().toString())) continue;
            if (Files.isRegularFile(entry)) {
                if (!Files.isExecutable(entry)) {
                    throw new Error(entry + " is not executable!");
                }
                try {
                    Set<PosixFilePermission> perm = Files.getPosixFilePermissions(entry);
                    if (!perm.containsAll(execPerms)) {
                        throw new Error(entry + " has not all executable permissions!\n" +
                                        "Should have: " + execPerms + "\nbut has: " + perm);
                    }
                } catch (UnsupportedOperationException e) {}
            }
        }
    }
}
