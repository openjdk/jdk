/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8329251
 * @library /test/lib
 * @summary Validates the customized keystore/ truststore paths
 * in the debug logs
 * @run main/othervm LogKeyStorePathVerifier

 */

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;

public class LogKeyStorePathVerifier {

    static String pathToStores = "../../../javax/net/ssl/etc/";
    static String keyStoreFile = "keystore";
    static String passwd = "passphrase";

    static void initContext() throws Exception {
        String keyFilename =
            System.getProperty("test.src", ".") + "/" + pathToStores +
                "/" + keyStoreFile;
        FileInputStream fis = new FileInputStream(keyFilename);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(fis, passwd.toCharArray());
        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, passwd.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
    }

    public static void main(String args[]) throws Exception {
        System.setProperty("test.java.opts",
                    "-Dtest.src=" + System.getProperty("test.src") +
                            " -Djava.security.debug=keystore");

        System.out.println("test.java.opts: " +
                System.getProperty("test.java.opts"));
        try {
            //initialize the KeyStore
            initContext();

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    Utils.addTestJavaOpts("LogKeyStorePathVerifier"));
            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            // Check for the presence of new message and verify the
            // keystore name in debug logs
            output.shouldContain("Loaded \"keystore\" keystore in PKCS12 format");
        } catch (Exception e) {
            throw e;
        }
        return;
    }
}
