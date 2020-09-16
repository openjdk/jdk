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
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.spi.ToolProvider;

/*
 * @test
 * @summary Make sure that jimages are consistent when created by jlink. Copies test jdk and runs against original.
 * @bug 8252730
 * @modules jdk.jlink
 *          jdk.management
 *          jdk.unsupported
 *          jdk.charsets
 * @library /test/lib
 * @run main JLinkReproducible4Test
 */
public class JLinkReproducible3Test {
    static final Path COPIED_JLINK;
    static final ToolProvider JLINK_TOOL;

    static {
        try {
            Path jdk2_dir = Files.createTempDirectory("JLinkReproducible4Test-jdk2");
            Path jdk_test_dir = Path.of(
                    Optional.of(
                            System.getProperty("test.jdk"))
                            .orElseThrow(() -> new RuntimeException("Couldn't load JDK Test Dir"))
            );

            Files.walkFileTree(jdk_test_dir, new CopyFileVisitor(jdk_test_dir, jdk2_dir));

            COPIED_JLINK = Optional.of(
                    Paths.get(jdk2_dir.toString(), "bin", "jlink"))
                    .orElseThrow(() -> new RuntimeException("Unable to load copied jlink")
                    );

            JLINK_TOOL = ToolProvider.findFirst("jlink")
                    .orElseThrow(() ->
                            new RuntimeException("jlink tool not found")
                    );

        } catch (IOException e) {
            throw new RuntimeException("Couldn't intialize JDKs");
        }

    }

    public static void main(String[] args) throws Exception {
        Path image1 = Paths.get("./image1");
        Path image2 = Paths.get("./image2");

        JLINK_TOOL.run(System.out, System.err, "--add-modules", "java.base,jdk.management,jdk.unsupported,jdk.charsets", "--output", image1.toString());
        runCopiedJlink(COPIED_JLINK.toString(), "--add-modules", "java.base,jdk.management,jdk.unsupported,jdk.charsets", "--output", image2.toString());

        if (Files.mismatch(image1.resolve("lib").resolve("modules"), image2.resolve("lib").resolve("modules")) != -1L) {
            throw new RuntimeException("jlink producing inconsistent result in modules");
        }
    }

    private static void runCopiedJlink(String...args) throws Exception {
        var pb = new ProcessBuilder(args);
        var res = ProcessTools.executeProcess(pb);
        res.shouldHaveExitValue(0);
    }

    private static class CopyFileVisitor extends SimpleFileVisitor<Path> {
        private final Path src;
        private final Path dst;

        public CopyFileVisitor(Path src, Path dst) {
            this.src = src;
            this.dst = dst;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path file,
                                                 BasicFileAttributes attrs) throws IOException {
            Path dstDir = dst.resolve(src.relativize(file));
            if (!dstDir.toFile().exists()) {
                Files.createDirectories(dstDir);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file,
                                         BasicFileAttributes attrs) throws IOException {
            if (!file.toFile().isFile()) {
                return FileVisitResult.CONTINUE;
            }
            Path dstFile = dst.resolve(src.relativize(file));
            Files.copy(file, dstFile, StandardCopyOption.COPY_ATTRIBUTES);
            return FileVisitResult.CONTINUE;
        }
    }
}