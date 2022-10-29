/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8076190 8242151 8153005 8266182
 * @summary This is java keytool <-> openssl interop test. This test generates
 *          some openssl keystores on the fly, java operates on it and
 *          vice versa.
 *
 *          Note: This test executes some openssl command, so need to set
 *          openssl path using system property "test.openssl.path" or it should
 *          be available in /usr/bin or /usr/local/bin
 *          Required OpenSSL version : OpenSSL 1.1.*
 *
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.util
 * @library /test/lib
 * @library /sun/security/pkcs11/
 * @run main/othervm/timeout=600 KeytoolOpensslInteropTest
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.security.OpensslArtifactFetcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Objects;

import static jdk.test.lib.security.DerUtils.*;
import static sun.security.util.KnownOIDs.*;
import static sun.security.pkcs.ContentInfo.*;

public class KeytoolOpensslInteropTest {

    public static void main(String[] args) throws Throwable {
        String opensslPath = OpensslArtifactFetcher.getOpenssl1dot1dotStar();
        if (opensslPath != null) {
            // if preferred version of openssl is available perform all
            // keytool <-> openssl interop tests
            generateInitialKeystores(opensslPath);
            testWithJavaCommands();
            testWithOpensslCommands(opensslPath);
        } else {
            // since preferred version of openssl is not available skip all
            // openssl command dependent tests with a warning
            System.out.println("\n\u001B[31mWarning: Can't find openssl "
                    + "(version 1.1.*) binary on this machine, please install"
                    + " and set openssl path with property "
                    + "'test.openssl.path'. Now running only half portion of "
                    + "the test, skipping all tests which depends on openssl "
                    + "commands.\u001B[0m\n");
            // De-BASE64 textual files in ./params to `pwd`
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                    Path.of(System.getProperty("test.src"), "params"),
                    p -> !p.getFileName().toString().equals("README"))) {
                stream.forEach(p -> {
                    try (InputStream is = Files.newInputStream(p);
                        OutputStream os = Files.newOutputStream(
                                p.getFileName())) {
                        Base64.getMimeDecoder().wrap(is).transferTo(os);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            testWithJavaCommands();
        }
    }

    private static void generateInitialKeystores(String opensslPath)
            throws Throwable {
        keytool("-keystore ks -keyalg ec -genkeypair -storepass"
                + " changeit -alias a -dname CN=A").shouldHaveExitValue(0);

        ProcessTools.executeCommand(opensslPath, "pkcs12", "-in", "ks",
                "-nodes", "-out", "kandc", "-passin", "pass:changeit")
                .shouldHaveExitValue(0);

        ProcessTools.executeCommand(opensslPath, "pkcs12", "-export", "-in",
                "kandc", "-out", "os2", "-name", "a", "-passout",
                "pass:changeit", "-certpbe", "NONE", "-nomac")
                .shouldHaveExitValue(0);

        ProcessTools.executeCommand(opensslPath, "pkcs12", "-export", "-in",
                "kandc", "-out", "os3", "-name", "a", "-passout",
                "pass:changeit", "-certpbe", "NONE")
                .shouldHaveExitValue(0);

        ProcessTools.executeCommand(opensslPath, "pkcs12", "-export", "-in",
                "kandc", "-out", "os4", "-name", "a", "-passout",
                "pass:changeit", "-certpbe", "PBE-SHA1-RC4-128", "-keypbe",
                "PBE-SHA1-RC4-128", "-macalg", "SHA224")
                .shouldHaveExitValue(0);

        ProcessTools.executeCommand(opensslPath, "pkcs12", "-export", "-in",
                "kandc", "-out", "os5", "-name", "a", "-passout",
                "pass:changeit", "-certpbe", "AES-256-CBC", "-keypbe",
                "AES-256-CBC", "-macalg", "SHA512")
                .shouldHaveExitValue(0);
    }

    private static void testWithJavaCommands() throws Throwable {
        byte[] data;

        // openssl -> keytool interop check
        // os2. no cert pbe, no mac.
        check("os2", "a", null, "changeit", true, true, true);
        check("os2", "a", "changeit", "changeit", true, true, true);
        // You can even load it with a wrong storepass, controversial
        check("os2", "a", "wrongpass", "changeit", true, true, true);

        // os3. no cert pbe, has mac. just like JKS
        check("os3", "a", null, "changeit", true, true, true);
        check("os3", "a", "changeit", "changeit", true, true, true);
        // Cannot load with a wrong storepass, same as JKS
        check("os3", "a", "wrongpass", "-", IOException.class, "-", "-");

        // os4. non default algs
        check("os4", "a", "changeit", "changeit", true, true, true);
        check("os4", "a", "wrongpass", "-", IOException.class, "-", "-");
        // no storepass no cert
        check("os4", "a", null, "changeit", true, false, true);

        // os5. strong non default algs
        check("os5", "a", "changeit", "changeit", true, true, true);
        check("os5", "a", "wrongpass", "-", IOException.class, "-", "-");
        // no storepass no cert
        check("os5", "a", null, "changeit", true, false, true);

        // keytool

        // Current default pkcs12 setting
        keytool("-importkeystore -srckeystore ks -srcstorepass changeit "
                + "-destkeystore ksnormal -deststorepass changeit");

        data = Files.readAllBytes(Path.of("ksnormal"));
        checkInt(data, "22", 10000); // Mac ic
        checkAlg(data, "2000", SHA_256); // Mac alg
        checkAlg(data, "110c010c01000", PBES2); // key alg
        checkInt(data, "110c010c01001011", 10000); // key ic
        checkAlg(data, "110c10", ENCRYPTED_DATA_OID);
        checkAlg(data, "110c110110", PBES2); // cert alg
        check("ksnormal", "a", "changeit", "changeit", true, true, true);
        check("ksnormal", "a", null, "changeit", true, false, true);
        check("ksnormal", "a", "wrongpass", "-", IOException.class, "-", "-");

        // Import it into a new keystore with legacy algorithms
        keytool("-importkeystore -srckeystore ksnormal -srcstorepass changeit "
                + "-destkeystore kslegacyimp -deststorepass changeit "
                + "-J-Dkeystore.pkcs12.legacy");
        data = Files.readAllBytes(Path.of("kslegacyimp"));
        checkInt(data, "22", 100000); // Mac ic
        checkAlg(data, "2000", SHA_1); // Mac alg
        checkAlg(data, "110c010c01000", PBEWithSHA1AndDESede); // key alg
        checkInt(data, "110c010c010011", 50000); // key ic
        checkAlg(data, "110c110110", PBEWithSHA1AndRC2_40); // cert alg
        checkInt(data, "110c1101111", 50000); // cert ic

        // Add a new entry with password-less settings, still has a storepass
        keytool("-keystore ksnormal -genkeypair -keyalg DSA "
                + "-storepass changeit -alias b -dname CN=b "
                + "-J-Dkeystore.pkcs12.certProtectionAlgorithm=NONE "
                + "-J-Dkeystore.pkcs12.macAlgorithm=NONE");
        data = Files.readAllBytes(Path.of("ksnormal"));
        checkInt(data, "22", 10000); // Mac ic
        checkAlg(data, "2000", SHA_256); // Mac alg
        checkAlg(data, "110c010c01000", PBES2); // key alg
        checkInt(data, "110c010c01001011", 10000); // key ic
        checkAlg(data, "110c010c11000", PBES2); // new key alg
        checkInt(data, "110c010c11001011", 10000); // new key ic
        checkAlg(data, "110c10", ENCRYPTED_DATA_OID);
        checkAlg(data, "110c110110", PBES2); // cert alg
        check("ksnormal", "b", null, "changeit", true, false, true);
        check("ksnormal", "b", "changeit", "changeit", true, true, true);

        // Different keypbe alg, no cert pbe and no mac
        keytool("-importkeystore -srckeystore ks -srcstorepass changeit "
                + "-destkeystore ksnopass -deststorepass changeit "
                + "-J-Dkeystore.pkcs12.keyProtectionAlgorithm=PBEWithSHA1AndRC4_128 "
                + "-J-Dkeystore.pkcs12.certProtectionAlgorithm=NONE "
                + "-J-Dkeystore.pkcs12.macAlgorithm=NONE");
        data = Files.readAllBytes(Path.of("ksnopass"));
        shouldNotExist(data, "2"); // no Mac
        checkAlg(data, "110c010c01000", PBEWithSHA1AndRC4_128);
        checkInt(data, "110c010c010011", 10000);
        checkAlg(data, "110c10", DATA_OID);
        check("ksnopass", "a", null, "changeit", true, true, true);
        check("ksnopass", "a", "changeit", "changeit", true, true, true);
        check("ksnopass", "a", "wrongpass", "changeit", true, true, true);

        // Add a new entry with normal settings, still password-less
        keytool("-keystore ksnopass -genkeypair -keyalg DSA "
                + "-storepass changeit -alias b -dname CN=B");
        data = Files.readAllBytes(Path.of("ksnopass"));
        shouldNotExist(data, "2"); // no Mac
        checkAlg(data, "110c010c01000", PBEWithSHA1AndRC4_128);
        checkInt(data, "110c010c010011", 10000);
        checkAlg(data, "110c010c11000", PBES2);
        checkInt(data, "110c010c11001011", 10000);
        checkAlg(data, "110c10", DATA_OID);
        check("ksnopass", "a", null, "changeit", true, true, true);
        check("ksnopass", "b", null, "changeit", true, true, true);

        keytool("-importkeystore -srckeystore ks -srcstorepass changeit "
                + "-destkeystore ksnewic -deststorepass changeit "
                + "-J-Dkeystore.pkcs12.macIterationCount=5555 "
                + "-J-Dkeystore.pkcs12.certPbeIterationCount=6666 "
                + "-J-Dkeystore.pkcs12.keyPbeIterationCount=7777");
        data = Files.readAllBytes(Path.of("ksnewic"));
        checkInt(data, "22", 5555); // Mac ic
        checkAlg(data, "2000", SHA_256); // Mac alg
        checkAlg(data, "110c010c01000", PBES2); // key alg
        checkInt(data, "110c010c01001011", 7777); // key ic
        checkAlg(data, "110c110110", PBES2); // cert alg
        checkInt(data, "110c110111011", 6666); // cert ic

        // keypbe alg cannot be NONE
        keytool("-keystore ksnewic -genkeypair -keyalg DSA "
                + "-storepass changeit -alias b -dname CN=B "
                + "-J-Dkeystore.pkcs12.keyProtectionAlgorithm=NONE")
                .shouldContain("NONE AlgorithmParameters not available")
                .shouldHaveExitValue(1);

        // new entry new keypbe alg (and default ic), else unchanged
        keytool("-keystore ksnewic -genkeypair -keyalg DSA "
                + "-storepass changeit -alias b -dname CN=B "
                + "-J-Dkeystore.pkcs12.keyProtectionAlgorithm=PBEWithSHA1AndRC4_128");
        data = Files.readAllBytes(Path.of("ksnewic"));
        checkInt(data, "22", 5555); // Mac ic
        checkAlg(data, "2000", SHA_256); // Mac alg
        checkAlg(data, "110c010c01000", PBES2); // key alg
        checkInt(data, "110c010c01001011", 7777); // key ic
        checkAlg(data, "110c010c11000", PBEWithSHA1AndRC4_128); // new key alg
        checkInt(data, "110c010c110011", 10000); // new key ic
        checkAlg(data, "110c110110", PBES2); // cert alg
        checkInt(data, "110c110111011", 6666); // cert ic

        // Check KeyStore loading multiple keystores
        KeyStore ks = KeyStore.getInstance("pkcs12");
        try (FileInputStream fis = new FileInputStream("ksnormal");
                FileOutputStream fos = new FileOutputStream("ksnormaldup")) {
            ks.load(fis, "changeit".toCharArray());
            ks.store(fos, "changeit".toCharArray());
        }
        data = Files.readAllBytes(Path.of("ksnormaldup"));
        checkInt(data, "22", 10000); // Mac ic
        checkAlg(data, "2000", SHA_256); // Mac alg
        checkAlg(data, "110c010c01000", PBES2); // key alg
        checkInt(data, "110c010c01001011", 10000); // key ic
        checkAlg(data, "110c010c11000", PBES2); // new key alg
        checkInt(data, "110c010c11001011", 10000); // new key ic
        checkAlg(data, "110c10", ENCRYPTED_DATA_OID);
        checkAlg(data, "110c110110", PBES2); // cert alg
        checkInt(data, "110c110111011", 10000); // cert ic

        try (FileInputStream fis = new FileInputStream("ksnopass");
             FileOutputStream fos = new FileOutputStream("ksnopassdup")) {
            ks.load(fis, "changeit".toCharArray());
            ks.store(fos, "changeit".toCharArray());
        }
        data = Files.readAllBytes(Path.of("ksnopassdup"));
        shouldNotExist(data, "2"); // no Mac
        checkAlg(data, "110c010c01000", PBEWithSHA1AndRC4_128);
        checkInt(data, "110c010c010011", 10000);
        checkAlg(data, "110c010c11000", PBES2);
        checkInt(data, "110c010c11001011", 10000);
        checkAlg(data, "110c10", DATA_OID);

        try (FileInputStream fis = new FileInputStream("ksnewic");
             FileOutputStream fos = new FileOutputStream("ksnewicdup")) {
            ks.load(fis, "changeit".toCharArray());
            ks.store(fos, "changeit".toCharArray());
        }
        data = Files.readAllBytes(Path.of("ksnewicdup"));
        checkInt(data, "22", 5555); // Mac ic
        checkAlg(data, "2000", SHA_256); // Mac alg
        checkAlg(data, "110c010c01000", PBES2); // key alg
        checkInt(data, "110c010c01001011", 7777); // key ic
        checkAlg(data, "110c010c11000", PBEWithSHA1AndRC4_128); // new key alg
        checkInt(data, "110c010c110011", 10000); // new key ic
        checkAlg(data, "110c110110", PBES2); // cert alg
        checkInt(data, "110c110111011", 6666); // cert ic

        // Check keytool behavior

        // ksnormal has password

        keytool("-list -keystore ksnormal")
                .shouldContain("WARNING WARNING WARNING")
                .shouldContain("Certificate chain length: 0");

        SecurityTools.setResponse("changeit");
        keytool("-list -keystore ksnormal")
                .shouldNotContain("WARNING WARNING WARNING")
                .shouldContain("Certificate fingerprint");

        // ksnopass is password-less

        keytool("-list -keystore ksnopass")
                .shouldNotContain("WARNING WARNING WARNING")
                .shouldContain("Certificate fingerprint");

        // -certreq prompts for keypass
        SecurityTools.setResponse("changeit");
        keytool("-certreq -alias a -keystore ksnopass")
                .shouldContain("Enter key password for <a>")
                .shouldContain("-----BEGIN NEW CERTIFICATE REQUEST-----")
                .shouldHaveExitValue(0);

        // -certreq -storepass works fine
        keytool("-certreq -alias a -keystore ksnopass -storepass changeit")
                .shouldNotContain("Enter key password for <a>")
                .shouldContain("-----BEGIN NEW CERTIFICATE REQUEST-----")
                .shouldHaveExitValue(0);

        // -certreq -keypass also works fine
        keytool("-certreq -alias a -keystore ksnopass -keypass changeit")
                .shouldNotContain("Enter key password for <a>")
                .shouldContain("-----BEGIN NEW CERTIFICATE REQUEST-----")
                .shouldHaveExitValue(0);

        // -importkeystore prompts for srckeypass
        SecurityTools.setResponse("changeit", "changeit");
        keytool("-importkeystore -srckeystore ksnopass "
                + "-destkeystore jks3 -deststorepass changeit")
                .shouldContain("Enter key password for <a>")
                .shouldContain("Enter key password for <b>")
                .shouldContain("2 entries successfully imported");

        // ksnopass2 is ksnopass + 2 cert entries

        ks = KeyStore.getInstance(new File("ksnopass"), (char[])null);
        ks.setCertificateEntry("aa", ks.getCertificate("a"));
        ks.setCertificateEntry("bb", ks.getCertificate("b"));
        try (FileOutputStream fos = new FileOutputStream("ksnopass2")) {
            ks.store(fos, null);
        }

        // -importkeystore prompts for srckeypass for private keys
        // and no prompt for certs
        SecurityTools.setResponse("changeit", "changeit");
        keytool("-importkeystore -srckeystore ksnopass2 "
                + "-destkeystore jks5 -deststorepass changeit")
                .shouldContain("Enter key password for <a>")
                .shouldContain("Enter key password for <b>")
                .shouldNotContain("Enter key password for <aa>")
                .shouldNotContain("Enter key password for <bb>")
                .shouldContain("4 entries successfully imported");

        // ksonlycert has only cert entries

        ks.deleteEntry("a");
        ks.deleteEntry("b");
        try (FileOutputStream fos = new FileOutputStream("ksonlycert")) {
            ks.store(fos, null);
        }

        // -importkeystore does not prompt at all
        keytool("-importkeystore -srckeystore ksonlycert "
                + "-destkeystore jks6 -deststorepass changeit")
                .shouldNotContain("Enter key password for <aa>")
                .shouldNotContain("Enter key password for <bb>")
                .shouldContain("2 entries successfully imported");

        // create a new password-less keystore
        keytool("-keystore ksnopass -exportcert -alias a -file a.cert -rfc");

        // Normally storepass is prompted for
        keytool("-keystore kscert1 -importcert -alias a -file a.cert -noprompt")
                .shouldContain("Enter keystore password:");
        keytool("-keystore kscert2 -importcert -alias a -file a.cert -noprompt "
                + "-J-Dkeystore.pkcs12.certProtectionAlgorithm=NONE")
                .shouldContain("Enter keystore password:");
        keytool("-keystore kscert3 -importcert -alias a -file a.cert -noprompt "
                + "-J-Dkeystore.pkcs12.macAlgorithm=NONE")
                .shouldContain("Enter keystore password:");
        // ... but not if it's password-less
        keytool("-keystore kscert4 -importcert -alias a -file a.cert -noprompt "
                + "-J-Dkeystore.pkcs12.certProtectionAlgorithm=NONE "
                + "-J-Dkeystore.pkcs12.macAlgorithm=NONE")
                .shouldNotContain("Enter keystore password:");

        // still prompt for keypass for genkeypair and certreq
        SecurityTools.setResponse("changeit", "changeit");
        keytool("-keystore ksnopassnew -genkeypair -keyalg DSA "
                + "-alias a -dname CN=A "
                + "-J-Dkeystore.pkcs12.certProtectionAlgorithm=NONE "
                + "-J-Dkeystore.pkcs12.macAlgorithm=NONE")
                .shouldNotContain("Enter keystore password:")
                .shouldContain("Enter key password for <a>");
        keytool("-keystore ksnopassnew -certreq -alias a")
                .shouldNotContain("Enter keystore password:")
                .shouldContain("Enter key password for <a>");
        keytool("-keystore ksnopassnew -list -v -alias a")
                .shouldNotContain("Enter keystore password:")
                .shouldNotContain("Enter key password for <a>");

        // params only read on demand

        // keyPbeIterationCount is used by -genkeypair
        keytool("-keystore ksgenbadkeyic -genkeypair -keyalg DSA "
                + "-alias a -dname CN=A "
                + "-storepass changeit "
                + "-J-Dkeystore.pkcs12.keyPbeIterationCount=abc")
                .shouldContain("keyPbeIterationCount is not a number: abc")
                .shouldHaveExitValue(1);

        keytool("-keystore ksnopassnew -exportcert -alias a -file a.cert");

        // but not used by -importcert
        keytool("-keystore ksimpbadkeyic -importcert -alias a -file a.cert "
                + "-noprompt -storepass changeit "
                + "-J-Dkeystore.pkcs12.keyPbeIterationCount=abc")
                .shouldHaveExitValue(0);

        // None is used by -list
        keytool("-keystore ksnormal -storepass changeit -list "
                + "-J-Dkeystore.pkcs12.keyPbeIterationCount=abc "
                + "-J-Dkeystore.pkcs12.certPbeIterationCount=abc "
                + "-J-Dkeystore.pkcs12.macIterationCount=abc")
                .shouldHaveExitValue(0);
    }

    private static void testWithOpensslCommands(String opensslPath)
            throws Throwable {

        OutputAnalyzer output1 = ProcessTools.executeCommand(opensslPath,
                "pkcs12", "-in", "ksnormal", "-passin", "pass:changeit",
                "-info", "-nokeys", "-nocerts");
        output1.shouldHaveExitValue(0)
            .shouldMatch("MAC:.*sha256.*Iteration 10000")
            .shouldContain("Shrouded Keybag: PBES2, PBKDF2, AES-256-CBC,"
                    + " Iteration 10000, PRF hmacWithSHA256")
            .shouldContain("PKCS7 Encrypted data: PBES2, PBKDF2, AES-256-CBC,"
                    + " Iteration 10000, PRF hmacWithSHA256");

        OutputAnalyzer output2 = ProcessTools.executeCommand(opensslPath,
                "pkcs12", "-in", "ksnormaldup", "-passin", "pass:changeit",
                "-info", "-nokeys", "-nocerts");
        output2.shouldHaveExitValue(0);
        if(!output1.getStderr().equals(output2.getStderr())) {
            throw new RuntimeException("Duplicate pkcs12 keystores"
                    + " ksnormal & ksnormaldup show different info");
        }

        output1 = ProcessTools.executeCommand(opensslPath, "pkcs12", "-in",
                "ksnopass", "-passin", "pass:changeit", "-info", "-nokeys",
                "-nocerts");
        output1.shouldNotHaveExitValue(0);

        output1 = ProcessTools.executeCommand(opensslPath, "pkcs12", "-in",
                "ksnopass", "-passin", "pass:changeit", "-info", "-nokeys",
                "-nocerts", "-nomacver");
        output1.shouldHaveExitValue(0)
            .shouldNotContain("PKCS7 Encrypted data:")
            .shouldContain("Shrouded Keybag: PBES2, PBKDF2, AES-256-CBC,"
                    + " Iteration 10000, PRF hmacWithSHA256")
            .shouldContain("Shrouded Keybag: pbeWithSHA1And128BitRC4,"
                    + " Iteration 10000");

        output2 = ProcessTools.executeCommand(opensslPath, "pkcs12", "-in",
                "ksnopassdup", "-passin", "pass:changeit", "-info", "-nokeys",
                "-nocerts", "-nomacver");
        output2.shouldHaveExitValue(0);
        if(!output1.getStderr().equals(output2.getStderr())) {
            throw new RuntimeException("Duplicate pkcs12 keystores"
                    + " ksnopass & ksnopassdup show different info");
        }

        output1 = ProcessTools.executeCommand(opensslPath, "pkcs12", "-in",
                "ksnewic", "-passin", "pass:changeit", "-info", "-nokeys",
                "-nocerts");
        output1.shouldHaveExitValue(0)
            .shouldMatch("MAC:.*sha256.*Iteration 5555")
            .shouldContain("Shrouded Keybag: PBES2, PBKDF2, AES-256-CBC,"
                    + " Iteration 7777, PRF hmacWithSHA256")
            .shouldContain("Shrouded Keybag: pbeWithSHA1And128BitRC4,"
                    + " Iteration 10000")
            .shouldContain("PKCS7 Encrypted data: PBES2, PBKDF2, AES-256-CBC,"
                    + " Iteration 6666, PRF hmacWithSHA256");

        output2 = ProcessTools.executeCommand(opensslPath, "pkcs12", "-in",
                "ksnewicdup", "-passin", "pass:changeit", "-info", "-nokeys",
                "-nocerts");
        output2.shouldHaveExitValue(0);
        if(!output1.getStderr().equals(output2.getStderr())) {
            throw new RuntimeException("Duplicate pkcs12 keystores"
                    + " ksnewic & ksnewicdup show different info");
        }
    }

    /**
     * Check keystore loading and key/cert reading.
     *
     * @param keystore the file name of keystore
     * @param alias the key/cert to read
     * @param storePass store pass to try out, can be null
     * @param keypass key pass to try, can not be null
     * @param expectedLoad expected result of keystore loading, true if non
     *                     null, false if null, exception class if exception
     * @param expectedCert expected result of cert reading
     * @param expectedKey expected result of key reading
     */
    private static void check(
            String keystore,
            String alias,
            String storePass,
            String keypass,
            Object expectedLoad,
            Object expectedCert,
            Object expectedKey) {
        KeyStore ks = null;
        Object actualLoad, actualCert, actualKey;
        String label = keystore + "-" + alias + "-" + storePass + "-" + keypass;
        try {
            ks = KeyStore.getInstance(new File(keystore),
                    storePass == null ? null : storePass.toCharArray());
            actualLoad = ks != null;
        } catch (Exception e) {
            e.printStackTrace(System.out);
            actualLoad = e.getClass();
        }
        Asserts.assertEQ(expectedLoad, actualLoad, label + "-load");

        // If not loaded correctly, skip cert/key reading
        if (!Objects.equals(actualLoad, true)) {
            return;
        }

        try {
            actualCert = (ks.getCertificate(alias) != null);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            actualCert = e.getClass();
        }
        Asserts.assertEQ(expectedCert, actualCert, label + "-cert");

        try {
            actualKey = (ks.getKey(alias, keypass.toCharArray()) != null);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            actualKey = e.getClass();
        }
        Asserts.assertEQ(expectedKey, actualKey, label + "-key");
    }

    private static OutputAnalyzer keytool(String s) throws Throwable {
        return SecurityTools.keytool(s);
    }
}
