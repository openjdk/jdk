/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @run main JLinkReproducible3Test
 */
public class JLinkReproducible3Test {

    public static void main(String[] args) throws Exception {
        Path image1 = Paths.get("./image1");
        Path image2 = Paths.get("./image2");

        Path copy_jdk1_dir = Path.of("./copy-jdk1-tmpdir");
        Path copy_jdk2_dir = Path.of("./copy-jdk2-tmpdir");
        Path jdk_test_dir = Path.of(
                Optional.of(
                        System.getProperty("test.jdk"))
                        .orElseThrow(() -> new RuntimeException("Couldn't load JDK Test Dir"))
        );

        copyJDKs(jdk_test_dir, copy_jdk1_dir, copy_jdk2_dir);

        Path copied_jlink1 = Optional.of(
                Paths.get(copy_jdk1_dir.toString(), "bin", "jlink"))
                .orElseThrow(() -> new RuntimeException("Unable to load copied jlink")
                );

        Path copied_jlink2 = Optional.of(
                Paths.get(copy_jdk2_dir.toString(), "bin", "jlink"))
                .orElseThrow(() -> new RuntimeException("Unable to load copied jlink")
                );

        runCopiedJlink(copied_jlink1.toString(), "--add-modules", "java.base,jdk.management,jdk.unsupported,jdk.charsets", "--output", image1.toString());
        runCopiedJlink(copied_jlink2.toString(), "--add-modules", "java.base,jdk.management,jdk.unsupported,jdk.charsets", "--output", image2.toString());

        long mismatch = Files.mismatch(image1.resolve("lib").resolve("modules"), image2.resolve("lib").resolve("modules"));
        if (mismatch != -1L) {
            throw new RuntimeException("jlink producing inconsistent result in modules. Mismatch in modules file occurred at byte position " + mismatch);
        }
    }

    private static void runCopiedJlink(String... args) throws Exception {
        var pb = new ProcessBuilder(args);
        var res = ProcessTools.executeProcess(pb);
        res.shouldHaveExitValue(0);
    }

    private static String runJavaVersion(Path jdk_test_base_dir) throws Exception {
        var java_exec = Paths.get(jdk_test_base_dir.toString(), "bin", "java");
        var pb = new ProcessBuilder(java_exec.toString(), "--version");
        var res = ProcessTools.executeProcess(pb);
        res.shouldHaveExitValue(0);
        return res.getStdout();


    }

    private static void copyJDKs(Path src, Path dst1, Path dst2) throws Exception {
        Files.walk(src).skip(1).forEach(file -> {
            try {
                System.out.println(file);
                if(Files.isDirectory(file)) {
                    Files.createDirectories(dst1.resolve(src.relativize(file)));
                    Files.createDirectories(dst2.resolve(src.relativize(file)));
                } else {
                    Files.copy(file, dst1.resolve(src.relativize(file)));
                    Files.copy(file, dst2.resolve(src.relativize(file)));
                }
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
    }
}

