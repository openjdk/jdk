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

/**
 * @test
 * @bug 8024302 8026037
 * @summary The test signs and verifies a jar file with -tsacert option
 * @library /lib/testlibrary
 * @modules java.base/sun.misc
 *          java.base/sun.security.pkcs
 *          java.base/sun.security.timestamp
 *          java.base/sun.security.util
 *          java.base/sun.security.x509
 *          java.management
 * @run main TsacertOptionTest
 */
public class TsacertOptionTest {

    private static final String FS = System.getProperty("file.separator");
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String KEYTOOL = JAVA_HOME + FS + "bin" + FS
            + "keytool";
    private static final String JARSIGNER = JAVA_HOME + FS + "bin" + FS
            + "jarsigner";
    private static final String UNSIGNED_JARFILE = "unsigned.jar";
    private static final String SIGNED_JARFILE = "signed.jar";
    private static final String FILENAME = TsacertOptionTest.class.getName()
            + ".txt";
    private static final String PASSWORD = "changeit";
    private static final String KEYSTORE = "ks.jks";
    private static final String CA_KEY_ALIAS = "ca";
    private static final String SIGNING_KEY_ALIAS = "sign_alias";
    private static final String TSA_KEY_ALIAS = "ts";
    private static final String KEY_ALG = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final int VALIDITY = 365;
    private static final String WARNING = "Warning:";
    private static final String JAR_SIGNED = "jar signed.";
    private static final String JAR_VERIFIED = "jar verified.";

    /**
     * The test signs and verifies a jar file with -tsacert option,
     * and checks that no warning was shown.
     * A certificate that is addressed in -tsacert option contains URL to TSA
     * in Subject Information Access extension.
     */
    public static void main(String[] args) throws Throwable {
        TsacertOptionTest test = new TsacertOptionTest();
        test.start();
    }

    void start() throws Throwable {
        // create a jar file that contains one file
        Utils.createFiles(FILENAME);
        JarUtils.createJar(UNSIGNED_JARFILE, FILENAME);

        // look for free network port for TSA service
        int port = jdk.testlibrary.Utils.getFreePort();
        String host = "127.0.0.1";
        String tsaUrl = "http://" + host + ":" + port;

        // create key pair for jar signing
        ProcessTools.executeCommand(KEYTOOL,
                "-genkey",
                "-alias", CA_KEY_ALIAS,
                "-keyalg", KEY_ALG,
                "-keysize", Integer.toString(KEY_SIZE),
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=CA",
                "-validity", Integer.toString(VALIDITY)).shouldHaveExitValue(0);
        ProcessTools.executeCommand(KEYTOOL,
                "-genkey",
                "-alias", SIGNING_KEY_ALIAS,
                "-keyalg", KEY_ALG,
                "-keysize", Integer.toString(KEY_SIZE),
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=Test").shouldHaveExitValue(0);
        ProcessTools.executeCommand(KEYTOOL,
                "-certreq",
                "-alias", SIGNING_KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-file", "certreq").shouldHaveExitValue(0);
        ProcessTools.executeCommand(KEYTOOL,
                "-gencert",
                "-alias", CA_KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-validity", Integer.toString(VALIDITY),
                "-infile", "certreq",
                "-outfile", "cert").shouldHaveExitValue(0);
        ProcessTools.executeCommand(KEYTOOL,
                "-importcert",
                "-alias", SIGNING_KEY_ALIAS,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-file", "cert").shouldHaveExitValue(0);

        // create key pair for TSA service
        // SubjectInfoAccess extension contains URL to TSA service
        ProcessTools.executeCommand(KEYTOOL,
                "-genkey",
                "-v",
                "-alias", TSA_KEY_ALIAS,
                "-keyalg", KEY_ALG,
                "-keysize", Integer.toString(KEY_SIZE),
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=TSA",
                "-ext", "ExtendedkeyUsage:critical=timeStamping",
                "-ext", "SubjectInfoAccess=timeStamping:URI:" + tsaUrl,
                "-validity", Integer.toString(VALIDITY)).shouldHaveExitValue(0);

        try (TimestampCheck.Handler tsa = TimestampCheck.Handler.init(port,
                KEYSTORE);) {

            // start TSA
            tsa.start();

            // sign jar file
            // specify -tsadigestalg option because
            // TSA server uses SHA-1 digest algorithm
             OutputAnalyzer analyzer = ProcessTools.executeCommand(JARSIGNER,
                    "-J-Dhttp.proxyHost=",
                    "-J-Dhttp.proxyPort=",
                    "-J-Djava.net.useSystemProxies=",
                    "-verbose",
                    "-keystore", KEYSTORE,
                    "-storepass", PASSWORD,
                    "-keypass", PASSWORD,
                    "-signedjar", SIGNED_JARFILE,
                    "-tsacert", TSA_KEY_ALIAS,
                    "-tsadigestalg", "SHA-1",
                    UNSIGNED_JARFILE,
                    SIGNING_KEY_ALIAS);

            analyzer.shouldHaveExitValue(0);
            analyzer.stdoutShouldNotContain(WARNING);
            analyzer.shouldContain(JAR_SIGNED);

            // verify signed jar
            analyzer = ProcessTools.executeCommand(JARSIGNER,
                    "-verbose",
                    "-verify",
                    "-keystore", KEYSTORE,
                    "-storepass", PASSWORD,
                    SIGNED_JARFILE);

            analyzer.shouldHaveExitValue(0);
            analyzer.stdoutShouldNotContain(WARNING);
            analyzer.shouldContain(JAR_VERIFIED);
        }

        System.out.println("Test passed");
    }

}
