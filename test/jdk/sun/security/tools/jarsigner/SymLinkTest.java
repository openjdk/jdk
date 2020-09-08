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

/**
 * @test
 * @bug 8250968
 * @summary Symlinks attributes not preserved when using jarsigner on zip files
 * @modules jdk.jartool/sun.security.tools.jarsigner
 *          java.base/sun.security.tools.keytool
 * @library /test/lib
 * @run main/othervm SymLinkTest
 */

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.Formatter;

import jdk.test.lib.SecurityTools;

public class SymLinkTest {
    private final static String ZIPFILENAME = "8250968-test.zip";
    private static final String WARNING_MSG = "POSIX file permission and/or symlink " +
            "attributes detected. These attributes are ignored when signing and are not " +
            "protected by the signature.";

    public static void main(String[] args) throws Exception {
        Files.deleteIfExists(Paths.get(ZIPFILENAME));
        try (FileOutputStream fos = new FileOutputStream(ZIPFILENAME)) {
            fos.write(ZIPBYTES);
        }

        // check permissions before signing
        verifyExtraAttrs(ZIPFILENAME);

        SecurityTools.keytool(
                "-genkey",
                "-keyalg", "RSA",
                "-dname", "CN=Coffey, OU=JPG, O=Oracle, L=Santa Clara, ST=California, C=US",
                "-alias", "examplekey",
                "-storepass", "password",
                "-keypass", "password",
                "-keystore", "examplekeystore",
                "-validity", "365")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner(
                "-keystore", "examplekeystore",
                "-verbose", ZIPFILENAME,
                "-storepass", "password",
                "-keypass", "password",
                "examplekey")
                .shouldHaveExitValue(0)
                .shouldContain(WARNING_MSG);

        // zip file now signed. Recheck attributes
        verifyExtraAttrs(ZIPFILENAME);

        SecurityTools.jarsigner("-keystore", "examplekeystore",
                "-storepass", "password",
                "-keypass", "password",
                "-verbose",
                "-verify", ZIPFILENAME)
                .shouldHaveExitValue(0)
                .shouldContain(WARNING_MSG);
    }

    private static void verifyExtraAttrs(String zipFileName) throws IOException {
        // the 16 bit extra attributes value should equal 0xa1ff - look for that pattern.
        // Such values can be read from zip file via 'unzip -Z -l -v <zipfile>'
        try (FileInputStream fis = new FileInputStream(ZIPFILENAME)) {
            byte[] b = fis.readAllBytes();
            boolean patternFound;
            for (int i = 0; i < b.length -1; i++) {
                patternFound = ((b[i] & 0xFF) == 0xFF) &&  ((b[i + 1] & 0xFF) == 0xA1);
                if (patternFound) {
                    return;
                }
            }
            throw new RuntimeException("extra attribute value not detected");
        }
    }

    /**
     * Utility method which takes an byte array and converts to byte array
     * declaration.  For example:
     * <pre>
     *     {@code
     *        var fooJar = Files.readAllBytes(Path.of("foo.jar"));
     *        var result = createByteArray(fooJar, "FOOBYTES");
     *      }
     * </pre>
     * @param bytes A byte array used to create a byte array declaration
     * @param name Name to be used in the byte array declaration
     * @return The formatted byte array declaration
     */
    public static String createByteArray(byte[] bytes, String name) {
        StringBuilder sb = new StringBuilder(bytes.length * 5);
        Formatter fmt = new Formatter(sb);
        fmt.format("    public static byte[] %s = {", name);
        final int linelen = 8;
        for (int i = 0; i < bytes.length; i++) {
            if (i % linelen == 0) {
                fmt.format("%n        ");
            }
            fmt.format(" (byte) 0x%x,", bytes[i] & 0xff);
        }
        fmt.format("%n    };%n");
        return sb.toString();
    }

    /*
     * Created using the createByteArray utility method.
     * The zipfile itself was created via this example:
     * $ ls -l z
     * lrwxrwxrwx 1 test test 4 Aug 27 18:33 z -> ../z
     * $ zip -ry test.zip z
     */
    public final static byte[] ZIPBYTES = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0xa, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x2e, (byte) 0x94, (byte) 0x1b, (byte) 0x51, (byte) 0xb4, (byte) 0xcc,
            (byte) 0xb6, (byte) 0xf1, (byte) 0x4, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x4, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x1c, (byte) 0x0, (byte) 0x7a, (byte) 0x55,
            (byte) 0x54, (byte) 0x9, (byte) 0x0, (byte) 0x3, (byte) 0x77, (byte) 0xfc, (byte) 0x47, (byte) 0x5f,
            (byte) 0x78, (byte) 0xfc, (byte) 0x47, (byte) 0x5f, (byte) 0x75, (byte) 0x78, (byte) 0xb, (byte) 0x0,
            (byte) 0x1, (byte) 0x4, (byte) 0xec, (byte) 0x3, (byte) 0x0, (byte) 0x0, (byte) 0x4, (byte) 0xec,
            (byte) 0x3, (byte) 0x0, (byte) 0x0, (byte) 0x2e, (byte) 0x2e, (byte) 0x2f, (byte) 0x7a, (byte) 0x50,
            (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x1e, (byte) 0x3, (byte) 0xa, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x2e, (byte) 0x94, (byte) 0x1b, (byte) 0x51, (byte) 0xb4,
            (byte) 0xcc, (byte) 0xb6, (byte) 0xf1, (byte) 0x4, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x4,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x18, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xff,
            (byte) 0xa1, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x7a, (byte) 0x55, (byte) 0x54,
            (byte) 0x5, (byte) 0x0, (byte) 0x3, (byte) 0x77, (byte) 0xfc, (byte) 0x47, (byte) 0x5f, (byte) 0x75,
            (byte) 0x78, (byte) 0xb, (byte) 0x0, (byte) 0x1, (byte) 0x4, (byte) 0xec, (byte) 0x3, (byte) 0x0,
            (byte) 0x0, (byte) 0x4, (byte) 0xec, (byte) 0x3, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b,
            (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0,
            (byte) 0x1, (byte) 0x0, (byte) 0x47, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x3f, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };
}
