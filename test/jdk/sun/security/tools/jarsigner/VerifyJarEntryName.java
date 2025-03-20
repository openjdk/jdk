/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8339280
 * @summary Test that jarsigner -verify emits a warning when the filename of
 *     an entry in the LOC is changed
 * @library /test/lib
 */

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jdk.test.lib.SecurityTools;

public class VerifyJarEntryName {

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
            zos.write("Manifest-Version: 1.0\nCreated-By: Test\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        var template = out.toByteArray();
        Path jarPath = Path.of("test.jar");
        Files.write(jarPath, template);

        SecurityTools.keytool("-genkeypair -keystore ks -storepass changeit "
                + "-alias mykey -keyalg rsa -dname CN=me ");

        SecurityTools.jarsigner("-keystore ks -storepass changeit "
                        + "test.jar mykey")
                .shouldHaveExitValue(0);

        Path jarPath1 = Path.of("test1.jar");
        Files.copy(jarPath, jarPath1, StandardCopyOption.REPLACE_EXISTING);

        // Modify a single byte in "MANIFEST.MF" filename in LOC
        byte[] signedJar = Files.readAllBytes(jarPath);
        var jarS = new String(signedJar, StandardCharsets.ISO_8859_1);

        var manifestPos = jarS.indexOf("MANIFEST.MF");
        if (manifestPos != -1) {
            signedJar[manifestPos] = 'X';
        }
        Files.write(jarPath, signedJar);

        SecurityTools.jarsigner("-verify -verbose test.jar")
                .shouldContain("Manifest is missing when reading via JarInputStream")
                .shouldHaveExitValue(0);

        // Modify a single byte in "MYKEY.SF" filename in LOC
        byte[] signedJar1 = Files.readAllBytes(jarPath1);
        var jarS1 = new String(signedJar1, StandardCharsets.ISO_8859_1);

        var sfPos = jarS1.indexOf("MYKEY.SF");
        if (sfPos != -1) {
            signedJar1[sfPos] = 'X';
        }
        Files.write(jarPath1, signedJar1);

        SecurityTools.jarsigner("-verify -verbose test1.jar")
                .shouldContain("Entries mismatch when comparing JarFile and JarInputStream")
                .shouldHaveExitValue(0);
    }
}
