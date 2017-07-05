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

import java.util.Date;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.JarUtils;

/**
 * @test
 * @bug 8024302 8026037
 * @summary Checks warnings if -tsa and -tsacert options are not specified
 * @library /lib/testlibrary ../
 * @run main NoTimestampTest
 */
public class NoTimestampTest extends Test {

    /**
     * The test signs and verifies a jar file without -tsa and -tsacert options,
     * and checks that proper warnings are shown.
     */
    public static void main(String[] args) throws Throwable {
        NoTimestampTest test = new NoTimestampTest();
        test.start();
    }

    private void start() throws Throwable {
        String timezone = System.getProperty("user.timezone");
        System.out.println(String.format("Timezone = %s", timezone));

        // create a jar file that contains one class file
        Utils.createFiles(FIRST_FILE);
        JarUtils.createJar(UNSIGNED_JARFILE, FIRST_FILE);

        // calculate certificate expiration date
        Date expirationDate = new Date(System.currentTimeMillis() + VALIDITY
                * 24 * 60 * 60 * 1000L);

        // create key pair
        ProcessTools.executeCommand(KEYTOOL,
                "-genkey",
                "-alias", KEY_ALIAS,
                "-keyalg", KEY_ALG,
                "-keysize", Integer.toString(KEY_SIZE),
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=Test",
                "-validity", Integer.toString(VALIDITY));

        // sign jar file
        OutputAnalyzer analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-J-Duser.timezone=" + timezone,
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-signedjar", SIGNED_JARFILE,
                UNSIGNED_JARFILE,
                KEY_ALIAS);

        String warning = String.format(NO_TIMESTAMP_SIGNING_WARN_TEMPLATE,
                expirationDate);
        checkSigning(analyzer, warning);

        // verify signed jar
        analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-J-Duser.timezone=" + timezone,
                "-verify",
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                SIGNED_JARFILE,
                KEY_ALIAS);

        warning = String.format(NO_TIMESTAMP_VERIFYING_WARN_TEMPLATE, expirationDate);
        checkVerifying(analyzer, 0, warning);

        // verify signed jar in strict mode
        analyzer = ProcessTools.executeCommand(JARSIGNER,
                "-J-Duser.timezone=" + timezone,
                "-verify",
                "-strict",
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                SIGNED_JARFILE,
                KEY_ALIAS);

        checkVerifying(analyzer, 0, warning);

        System.out.println("Test passed");
    }

}
