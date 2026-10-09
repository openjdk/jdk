/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

/*
 * @test
 * @summary Make sure that jimages are consistent when created by jlink. Copies test jdk and runs against original.
 * @bug 8252730
 * @modules jdk.jlink
 *          jdk.management
 *          jdk.unsupported
 *          jdk.charsets
 * @library /test/lib
 * @build jdk.test.lib.util.FileUtils
 * @run main JLinkReproducible3Test
 */
public class JLinkReproducible3Test {

    public static void main(String[] args) throws Exception {
        Path image1 = Paths.get("./image1");
        Path image2 = Paths.get("./image2");

        Path jdkTestDir = Path.of(
                Optional.of(
                        System.getProperty("test.jdk"))
                        .orElseThrow(() -> new RuntimeException("Couldn't load JDK Test Dir"))
        );

        // Link each image from its own copy of the JDK placed at a distinct
        // location. Copy, link, then delete the copy before creating the next
        // one so that at most one JDK copy exists on disk at a time.
        linkFromCopy(jdkTestDir, Path.of("./copy-jdk1-tmpdir"), image1);
        linkFromCopy(jdkTestDir, Path.of("./copy-jdk2-tmpdir"), image2);

        long mismatch = Files.mismatch(image1.resolve("lib").resolve("modules"), image2.resolve("lib").resolve("modules"));
        if (mismatch != -1L) {
            throw new RuntimeException("jlink producing inconsistent result in modules. Mismatch in modules file occurred at byte position " + mismatch);
        }
    }

    private static void linkFromCopy(Path jdkTestDir, Path copyJdkDir, Path image) throws Exception {
        Files.createDirectory(copyJdkDir);
        copyJDK(jdkTestDir, copyJdkDir);

        Path copiedJlink = Paths.get(copyJdkDir.toString(), "bin", "jlink");
        runCopiedJlink(copiedJlink.toString(), "--add-modules",
                "java.base,jdk.management,jdk.unsupported,jdk.charsets",
                "--output", image.toString());

        // The copied JDK was only needed to run jlink; free the disk space
        // before creating the next copy.
        FileUtils.deleteFileTreeWithRetry(copyJdkDir);
    }

    private static void runCopiedJlink(String... args) throws Exception {
        var process = new ProcessBuilder(args);
        var res = ProcessTools.executeProcess(process);
        res.shouldHaveExitValue(0);
    }

    private static void copyJDK(Path src, Path dst) throws Exception {
        Files.walk(src).skip(1).forEach(file -> {
            try {
                Files.copy(file, dst.resolve(src.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
    }
}

