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
 * @run main/othervm LogKeyStorePathVerifier launch
 */

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;

public class LogKeyStorePathVerifier {

    static String pathToStores = "../../../javax/net/ssl/etc/";
    static String keyStoreFile = "keystore";
    static String passwd = "passphrase";
    static String defaultCACertsName = "cacerts";
    static String fisKeyStoreName = "FileInputStreamKeyStore";
    static String bisKeyStoreName = "BufferedInputStreamKeyStore";
    static String bbisKeyStoreName = "BufferedBufferedInputStreamKeyStore";
    // JDK-8344924: Introduced a new behavior where default CA certificates
    // are loaded even when a custom keystore is specified during the first
    // TrustManagerFactory.init() call.
    // This test validates the behavior by first loading and verifying the
    // default certificates in the initial instance, followed by checking the
    // custom keystore in subsequent initialization.
    static String defaultCACerts
            = System.getProperty("java.home") + File.separator + "lib"
            + File.separator + "security" + File.separator + defaultCACertsName;
    static Path keyStorePath = Path.of (System.getProperty("test.src", "."),
            pathToStores, keyStoreFile);

    static void initContext() throws Exception {
        Files.copy(keyStorePath, Path.of(fisKeyStoreName),
                            StandardCopyOption.REPLACE_EXISTING);
        Files.copy(keyStorePath, Path.of(bisKeyStoreName),
                            StandardCopyOption.REPLACE_EXISTING);
        Files.copy(keyStorePath, Path.of(bbisKeyStoreName),
                            StandardCopyOption.REPLACE_EXISTING);
        try (FileInputStream dfis = new FileInputStream(defaultCACerts);
            FileInputStream fis = new FileInputStream(fisKeyStoreName);
            BufferedInputStream bis = new BufferedInputStream(
            new FileInputStream(bisKeyStoreName));
            BufferedInputStream bbis = new BufferedInputStream(
            new BufferedInputStream(new FileInputStream(bbisKeyStoreName)))) {
                loadAndTestKeyStore(dfis);
                loadAndTestKeyStore(fis);
                loadAndTestKeyStore(bis);
                // Test nested wrappers on FIStream with BIStream
                loadAndTestKeyStore(bbis);
        }
    }

    static void loadAndTestKeyStore(InputStream is) throws Exception {
        System.err.println("Testing with InputStream: " +
                                        is.getClass().getName());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(is, passwd.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, passwd.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
    }

    public static void main(String args[]) throws Exception {
        if (args.length == 0) {
            // Being run via ProcessBuilder, call method to
            // generated debug output
            initContext();
        } else {
            var output = ProcessTools.executeTestJava(
                    "-Djavax.net.debug=trustmanager",
                    "-Djava.security.debug=pkcs12",
                    "-Dtest.src=" + System.getProperty("test.src", "."),
                    "LogKeyStorePathVerifier");
            // Check for the presence of new message and verify
            // the keystore name in debug logs
            output.shouldContain("PKCS12KeyStore: loading "
                                + "\"" + defaultCACertsName +"\" keystore")
                .shouldContain("PKCS12KeyStore: loading "
                                + "\"" + fisKeyStoreName +"\" keystore")
                .shouldContain("Initializing with the keystore: \""
                                + fisKeyStoreName + "\""
                                + " in pkcs12 format from SunJSSE provider")
                .shouldContain("PKCS12KeyStore: loading "
                                + "\"" + bisKeyStoreName +"\" keystore")
                .shouldContain("Initializing with the keystore: \""
                                + bisKeyStoreName + "\""
                                + " in pkcs12 format from SunJSSE provider")
                .shouldContain("PKCS12KeyStore: loading "
                                + "\"" + bbisKeyStoreName +"\" keystore")
                .shouldContain("Initializing with the keystore: \""
                                + bbisKeyStoreName +"\""
                                + " in pkcs12 format from SunJSSE provider");
        }
    }
}
