/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8158633
 * @summary BASE64 encoded cert not correctly parsed with UTF-16
 * @library /test/lib
 * @run main/othervm PemEncoding
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.util.regex.Pattern;

public class PemEncoding {
    public static void main(String[] args) throws Exception {

        final var certPath = String.format("%s%s..%sHostnameChecker%scert5.crt",
                System.getProperty("test.src", "."),
                File.separator,
                File.separator,
                File.separator);

        final var testCommand = new String[]{"-Dfile.encoding=UTF-16",
                PemEncoding.PemEncodingTest.class.getName(),
                certPath};

        final var result = ProcessTools.executeTestJava(testCommand);

        // printing and saving all output from the subprocess
        final var subProcessOutput = result.getStdout();
        final var subProcessErr = result.getStderr();
        System.out.printf("Output:\n%s%n", subProcessOutput);
        System.err.printf("Error output:\n%s%n", subProcessErr);

        result.shouldHaveExitValue(0);

        // checking if there are any errors in the result
        Asserts.assertEquals(subProcessErr, "", "Errors found in subprocess");

        // checking if there are non ASCII characters, as UTF-16 characters should be present in signature
        final var pattern = Pattern.compile("[^\\x00-\\x7F]");// detects all non-ASCII characters
        final var matcher = pattern.matcher(subProcessOutput);

        // Some systems may automatically convert non-ASCII to ?
        // so this will detect the signature in all possible scenarios
        final var isUTF16Used = matcher.find() || subProcessOutput.contains("??: ?");
        Asserts.assertTrue(isUTF16Used, "UTF-16 was used");
    }

    static class PemEncodingTest {
        public static void main(String[] args) throws Exception {

            try (FileInputStream fis = new FileInputStream(args[0])) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                System.out.println(cf.generateCertificate(fis));
            }
        }
    }
}
