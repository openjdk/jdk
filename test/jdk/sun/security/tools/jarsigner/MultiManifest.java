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
 * @bug 8341775
 * @summary Print warning that duplicate manifest files are removed by jarsigner
 *  after signing whether or not -verbose is passed
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MultiManifest {

    private static final String META_INF = "META-INF/";

    public static void main(String[] args) throws Exception {

        writeMultiManifestJar();

        SecurityTools.keytool("-keystore ks -storepass changeit "
                + "-keypass changeit -alias a -dname CN=a -keyalg rsa "
                + "-genkey -validity 300");

        SecurityTools.jarsigner("-verbose -keystore ks -storepass changeit "
                                + "MultiManifest.jar -signedjar MultiManifest.signed.jar a")
                .shouldHaveExitValue(0)
                .shouldContain("Duplicate manifest entries were detected")
                .shouldContain("discarded");

        SecurityTools.jarsigner("-keystore ks -storepass changeit "
                                + "MultiManifest.jar -signedjar MultiManifest.signed.jar a")
                 .shouldHaveExitValue(0)
                 .shouldContain("Duplicate manifest entries were detected")
                 .shouldContain("discarded");

    }

    public static void writeMultiManifestJar() throws IOException {
        int locPosA, locPosB, cenPos;
        var out = new ByteArrayOutputStream(1024);
        try (var zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
            zos.closeEntry();
            locPosA = out.size();
            zos.putNextEntry(new ZipEntry(META_INF + "AANIFEST.MF"));
            zos.closeEntry();
            locPosB = out.size();
            zos.putNextEntry(new ZipEntry(META_INF + "BANIFEST.MF"));
            zos.flush();
            cenPos = out.size();
        }
        var template = out.toByteArray();
        // ISO_8859_1 to keep the 8-bit value
        var s = new String(template, StandardCharsets.ISO_8859_1);
        // change META-INF/AANIFEST.MF to META-INF/MANIFEST.MF
        var loc = s.indexOf("AANIFEST.MF", locPosA);
        var cen = s.indexOf("AANIFEST.MF", cenPos);
        template[loc] = template[cen] = (byte) 'M';
        // change META-INF/BANIFEST.MF to META-INF/MANIFEST.MF
        loc = s.indexOf("BANIFEST.MF", locPosB);
        cen = s.indexOf("BANIFEST.MF", cenPos);
        template[loc] = template[cen] = (byte) 'M';
        Files.write(Path.of("MultiManifest.jar"), template);
    }
}
