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
 * @summary Test jarsigner -verify on a signed JAR to detect the difference
 *     between LOC and CEN entries and emit warning.
 * @library /test/lib
 * @run main JarsignerVerify
 */

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;

import jdk.test.lib.SecurityTools;

public class JarsignerVerify {

    public static void main(String[] args) throws Exception {
        File f = null;

        try {
            byte[] bytes = Base64.getDecoder().decode(TEST_JAR);
            f = createJarFile("test.jar", bytes);

            /*
             * jarsigner -verify reports the difference between
             * LOC and CEN entries.
             */
            SecurityTools.jarsigner("-verify -verbose test.jar")
                    .shouldContain("META-INF/MANIFEST.MF")
                    .shouldContain("META-INF/TESTKEY1.SF")
                    .shouldContain("META-INF/TESTKEY1.RSA")
                    .shouldContain("LocalClass1.class (JarInputStream only)")
                    .shouldContain("LocalClass2.class (JarInputStream only)")
                    .shouldContain("Different content observed in JarInputStream and JarFile.");
        } finally {
            if (f != null) {
                f.delete();
            }
        }
    }

    private static File createJarFile(String filename, byte[] bytes)
            throws Exception {
        File f = new File(filename);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bytes);
        }
        System.out.println("Successfully recreated test JAR file");
        return f;
    }

    /*
     * Base64 encoded bytes for a JAR file which contains two extra class files
     * in the local headers but not in the central directory.
     */
    public static String TEST_JAR =
        "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";
}
