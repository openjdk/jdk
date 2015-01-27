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

import java.io.File;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.JarUtils;

/**
 * @test
 * @bug 8024302 8026037
 * @summary Test for chainNotValidated warning
 * @library /lib/testlibrary ../
 * @run main ChainNotValidatedTest
 */
public class ChainNotValidatedTest extends Test {

    private static final String CHAIN = "chain";

    /**
     * The test signs and verifies a jar that contains entries
     * whose cert chain can't be correctly validated (chainNotValidated).
     * Warning message is expected.
     */
    public static void main(String[] args) throws Throwable {
        ChainNotValidatedTest test = new ChainNotValidatedTest();
        test.start();
    }

    private void start() throws Throwable {
        // create a jar file that contains one class file
        Utils.createFiles(FIRST_FILE);
        JarUtils.createJar(UNSIGNED_JARFILE, FIRST_FILE);

        // create self-signed certificate whose BasicConstraints extension
        // is set to false, so the certificate may not be used
        // as a parent certificate (certpath validation should fail)
        ProcessTools.executeCommand(KEYTOOL,
                "-genkeypair",
                "-alias", CA_KEY_ALIAS,
                "-keyalg", KEY_ALG,
                "-keysize", Integer.toString(KEY_SIZE),
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=CA",
                "-ext", "BasicConstraints:critical=ca:false",
                "-validity", Integer.toString(VALIDITY)).shouldHaveExitValue(0);

        // create a certificate that is signed by self-signed certificate
        // despite of it may not be used as a parent certificate
        // (certpath validation should fail)
        ProcessTools.executeCommand(KEYTOOL,
                "-genkeypair",
                "-alias", KEY_ALIAS,
                "-keyalg", KEY_ALG,
                "-keysize", Integer.toString(KEY_SIZE),
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=Test",
                "-ext", "BasicConstraints:critical=ca:false",
                "-validity", Integer.toString(VALIDITY)).shouldHaveExitValue(0);

        ProcessTools.executeCommand(KEYTOOL,
                "-certreq",
                "-alias", KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-file", CERT_REQUEST_FILENAME).shouldHaveExitValue(0);

        ProcessTools.executeCommand(KEYTOOL,
                "-gencert",
                "-alias", CA_KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-infile", CERT_REQUEST_FILENAME,
                "-validity", Integer.toString(VALIDITY),
                "-outfile", CERT_FILENAME).shouldHaveExitValue(0);

        ProcessTools.executeCommand(KEYTOOL,
                "-importcert",
                "-alias", KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-file", CERT_FILENAME).shouldHaveExitValue(0);

        ProcessBuilder pb = new ProcessBuilder(KEYTOOL,
                "-export",
                "-rfc",
                "-alias", KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(CHAIN)));
        ProcessTools.executeCommand(pb).shouldHaveExitValue(0);

        pb = new ProcessBuilder(KEYTOOL,
                "-export",
                "-rfc",
                "-alias", CA_KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(CHAIN)));
        ProcessTools.executeCommand(pb).shouldHaveExitValue(0);

        // remove CA certificate
        ProcessTools.executeCommand(KEYTOOL,
                "-delete",
                "-alias", CA_KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD).shouldHaveExitValue(0);

        // sign jar
        OutputAnalyzer analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-certchain", CHAIN,
                "-signedjar", SIGNED_JARFILE,
                UNSIGNED_JARFILE,
                KEY_ALIAS);

        checkSigning(analyzer, CHAIN_NOT_VALIDATED_SIGNING_WARNING);

        // verify signed jar
        analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-verify",
                "-verbose",
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-certchain", CHAIN,
                SIGNED_JARFILE);

        checkVerifying(analyzer, 0, CHAIN_NOT_VALIDATED_VERIFYING_WARNING);

        // verify signed jar in strict mode
        analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-verify",
                "-verbose",
                "-strict",
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-certchain", CHAIN,
                SIGNED_JARFILE);

        checkVerifying(analyzer, CHAIN_NOT_VALIDATED_EXIT_CODE,
                CHAIN_NOT_VALIDATED_VERIFYING_WARNING);

        System.out.println("Test passed");
    }

}
