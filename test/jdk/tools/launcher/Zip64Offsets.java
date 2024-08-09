/*
 * Copyright (c) 2024 Alphabet LLC. All rights reserved.
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

/**
 * @test
 * @requires vm.bits == 64
 * @bug 8328995
 * @summary Test java.util.zip behavior with >4GB offsets
 * @library /test/lib
 * @run main/othervm -Xmx6g Zip64Offsets
 */
import static java.nio.charset.StandardCharsets.UTF_8;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zip64Offsets {
    public static class Main {
        public static void main(String[] args) {
            System.out.print("Main");
        }
    }

    public static void main(String[] args) throws Throwable {
        test();
    }

    static void test() throws Throwable {
        Path zipPath = Path.of("Zip64Offsets-tmp.zip");
        Files.deleteIfExists(zipPath);

        try (OutputStream os = Files.newOutputStream(zipPath);
                BufferedOutputStream bos = new BufferedOutputStream(os);
                ZipOutputStream zos = new ZipOutputStream(bos)) {

            // Write >4GB of zip data
            byte[] gb = new byte[1 << 30];
            for (int i = 0; i < 6; i++) {
                writeEntry(zos, Integer.toString(i), gb);
            }

            for (Class<?> clazz : List.of(Zip64Offsets.class, Main.class)) {
                String baseName = clazz.getName() + ".class";
                byte[] bytes = clazz.getResourceAsStream(baseName).readAllBytes();
                writeEntry(zos, baseName, bytes);
            }

            // Put the manifest at the end, to ensure that "java -jar" supports >4GB offsets
            writeEntry(
                    zos,
                    "META-INF/MANIFEST.MF",
                    ("Manifest-Version: 1.0\nMain-Class: " + Main.class.getName() + "\n")
                            .getBytes(UTF_8));
        }

        checkCanRead(zipPath);
    }

    private static void writeEntry(ZipOutputStream zos, String name, byte[] bytes)
            throws IOException {
        ZipEntry ze = new ZipEntry(name);
        ze.setMethod(ZipEntry.STORED);
        ze.setSize(bytes.length);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        ze.setCrc(crc.getValue());
        zos.putNextEntry(ze);
        zos.write(bytes);
        zos.closeEntry();
    }

    static void checkCanRead(Path zipPath) throws Exception {
        // Check java -jar
        OutputAnalyzer a = ProcessTools.executeTestJava("-jar", zipPath.getFileName().toString());
        a.shouldHaveExitValue(0);
        a.stdoutShouldMatch("\\AMain\\Z");
        a.stderrShouldMatch("\\A\\Z");
    }
}
