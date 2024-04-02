/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @test
 * @bug 8202598
 * @summary PEM outputs should have consistent line endings
 * @library /test/lib
 */

public class LineEndings {

    public static void main(String[] args) throws Exception {
        keytool("-genkeypair -dname CN=A -keyalg ec");

        keytool("-certreq -file a.csr -rfc");
        checkFile("a.csr");

        keytool("-exportcert -file a.crt -rfc");
        checkFile("a.crt");

        keytool("-gencrl -id 1 -rfc -file a.crl");
        checkFile("a.crl");

        // `keytool -printcrl` shows "Verified by ..." at the end. Remove it.
        String print = keytool("-printcrl -file a.crl -rfc").getStdout();
        print = print.substring(0, print.indexOf("Verified by"));
        Files.writeString(Path.of("print"), print);
        checkFile("print");
    }

    private static OutputAnalyzer keytool(String cmd) throws Exception {
        return SecurityTools.keytool(
                "-keystore ks -storepass changeit -alias a " + cmd)
                .shouldHaveExitValue(0);
    }

    // Make sure only CRLF is used inside the file.
    private static void checkFile(String name) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        for (byte b : Files.readAllBytes(Path.of(name))) {
            // Collect all non-printable bytes in an array
            if (b < 32) bout.write(b);
        }
        // There should only be a series of CRLFs left
        byte[] endings = bout.toByteArray();
        Asserts.assertTrue(endings.length > 4, "Too empty");
        Asserts.assertTrue(endings.length % 2 == 0,
                "Length is " + endings.length);
        for (int i = 0; i < endings.length; i += 2) {
            Asserts.assertEquals(endings[i], (byte)'\r',
                    "Byte at " + i + " is not CR");
            Asserts.assertEquals(endings[i + 1], (byte)'\n',
                    "Byte at " + (i + 1) + " is not LF");
        }
    }
}
