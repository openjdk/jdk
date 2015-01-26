/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.JarUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * @test
 * @bug 8024302 8026037
 * @summary Test for badNetscapeCertType warning
 * @library /lib/testlibrary ../
 * @run main BadNetscapeCertTypeTest
 */
public class BadNetscapeCertTypeTest extends Test {

    private static final String NETSCAPE_KEYSTORE_BASE64 = TEST_SOURCES + FS
            + "bad_netscape_cert_type.jks.base64";

    private static final String NETSCAPE_KEYSTORE
            = "bad_netscape_cert_type.jks";

    /**
     * The test signs and verifies a jar that contains entries
     * whose signer certificate's NetscapeCertType extension
     * doesn't allow code signing (badNetscapeCertType).
     * Warning message is expected.
     * Run bad_netscape_cert_type.sh script to create bad_netscape_cert_type.jks
     */
    public static void main(String[] args) throws Throwable {

        Files.write(Paths.get(NETSCAPE_KEYSTORE),
                Base64.getMimeDecoder().decode(
                    Files.readAllBytes(Paths.get(NETSCAPE_KEYSTORE_BASE64))));

        BadNetscapeCertTypeTest test = new BadNetscapeCertTypeTest();
        test.start();
    }

    private void start() throws Throwable {
        // create a jar file that contains one class file
        Utils.createFiles(FIRST_FILE);
        JarUtils.createJar(UNSIGNED_JARFILE, FIRST_FILE);

        // sign jar
        OutputAnalyzer analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-verbose",
                "-keystore", NETSCAPE_KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-signedjar", SIGNED_JARFILE,
                UNSIGNED_JARFILE,
                KEY_ALIAS);

        checkSigning(analyzer, BAD_NETSCAPE_CERT_TYPE_SIGNING_WARNING);

        // verify signed jar
        analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-verify",
                "-verbose",
                "-keystore", NETSCAPE_KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                SIGNED_JARFILE);

        checkVerifying(analyzer, 0, BAD_NETSCAPE_CERT_TYPE_VERIFYING_WARNING);

        // verify signed jar in strict mode
        analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-verify",
                "-verbose",
                "-strict",
                "-keystore", NETSCAPE_KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                SIGNED_JARFILE);

        checkVerifying(analyzer, BAD_NETSCAPE_CERT_TYPE_EXIT_CODE,
                BAD_NETSCAPE_CERT_TYPE_VERIFYING_WARNING);

        System.out.println("Test passed");
    }

}
