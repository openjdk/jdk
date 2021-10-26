/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6890872 8168882 8257722
 * @summary keytool -printcert to recognize signed jar files
 * @library /test/lib
 * @build jdk.test.lib.SecurityTools
 *        jdk.test.lib.util.JarUtils
 *        jdk.test.lib.Utils
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Platform
 *        jdk.test.lib.process.*
 * @run main ReadJar
 */

import java.nio.file.Files;
import java.nio.file.Paths;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;
import java.nio.file.Path;

public class ReadJar {

    static OutputAnalyzer kt(String cmd, String ks) throws Exception {
        return SecurityTools.keytool("-storepass changeit " + cmd +
                " -keystore " + ks);
    }

    static void gencert(String owner, String cmd) throws Exception {
        kt("-certreq -alias " + owner + " -file tmp.req", "ks");
        kt("-gencert -infile tmp.req -outfile tmp.cert " + cmd, "ks");
        kt("-importcert -alias " + owner + " -file tmp.cert", "ks");
    }

    public static void main(String[] args) throws Throwable {
        testWithMD5();
        testCertOutput();
    }

    // make sure that -printcert option works
    // if a weak algorithm was used for signing a jar
    private static void testWithMD5() throws Throwable {
        // create jar files
        JarUtils.createJar("test_md5.jar", "test");
        JarUtils.createJar("test_rsa.jar", "test");

        // create a keystore and generate keys for jar signing
        Files.deleteIfExists(Paths.get("keystore"));

        OutputAnalyzer out = SecurityTools.keytool("-genkeypair "
                + "-keystore keystore -storepass password "
                + "-keypass password -keyalg rsa -alias rsa_alias -dname CN=A");
        System.out.println(out.getOutput());
        out.shouldHaveExitValue(0);

        out = SecurityTools.jarsigner("-keystore keystore -storepass password "
                + "test_rsa.jar rsa_alias");
        System.out.println(out.getOutput());
        out.shouldHaveExitValue(0);

        printCert("test_rsa.jar");

        out = SecurityTools.jarsigner("-keystore keystore -storepass password "
                + "-sigalg MD5withRSA -digestalg MD5 test_md5.jar rsa_alias");
        System.out.println(out.getOutput());
        out.shouldHaveExitValue(0);

        printCert("test_md5.jar");
    }

    private static void printCert(String jar) throws Throwable {
        OutputAnalyzer out = SecurityTools.keytool("-printcert -jarfile " + jar);
        System.out.println(out.getOutput());
        out.shouldHaveExitValue(0);
        out.shouldNotContain("Not a signed jar file");

        out = SecurityTools.keytool("-printcert -rfc -jarfile " + jar);
        System.out.println(out.getOutput());
        out.shouldHaveExitValue(0);
        out.shouldNotContain("Not a signed jar file");
    }

    private static void testCertOutput() throws Throwable {
        kt("-genkeypair -keyalg rsa -alias ca1 -dname CN=CA1 -ext bc:c " +
                "-keysize 512", "ks");
        kt("-genkeypair -keyalg rsa -alias e1 -dname CN=E1", "ks");
        gencert("e1", "-alias ca1 -ext san=dns:e1");

        JarUtils.createJarFile(Path.of("a.jar"), Path.of("."), Path.of("ks"));
        // sign the JAR file
        SecurityTools.jarsigner("-keystore ks -storepass changeit " +
                " a.jar e1")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-printcert -jarfile a.jar")
                .shouldNotContain("Signature:")
                .shouldContain("Signer #1:")
                .shouldContain("Certificate #1:")
                .shouldContain("Certificate #2:")
                .shouldNotContain("Signer #2:")
                .shouldMatch("The certificate #2 of 2 of signer #1 uses a 512-bit RSA key.*is disabled")
                .shouldHaveExitValue(0);

        kt("-genkeypair -keyalg rsa -alias ca2 -dname CN=CA2 -ext bc:c " +
                "-sigalg SHA1withRSA", "ks");
        kt("-genkeypair -keyalg rsa -alias e2 -dname CN=E2", "ks");
        gencert("e2", "-alias ca2 -ext san=dns:e2");

        // sign the JAR file again with different signer
        SecurityTools.jarsigner("-keystore ks -storepass changeit " +
                " a.jar e2")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-printcert -jarfile a.jar")
                .shouldNotContain("Signature:")
                .shouldContain("Signer #1:")
                .shouldContain("Certificate #1:")
                .shouldContain("Certificate #2:")
                .shouldContain("Signer #2:")
                .shouldMatch("The certificate #2 of 2 of signer #1 uses the SHA1withRSA.*will be disabled")
                .shouldMatch("The certificate #2 of 2 of signer #2 uses a 512-bit RSA key.*is disabled")
                .shouldHaveExitValue(0);
    }
}
