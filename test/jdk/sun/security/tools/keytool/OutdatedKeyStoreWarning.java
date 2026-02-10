/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8353749
 * @summary Validate that keytool and jarsigner emit warnings for
 *         JKS and JCEKS keystore with java.security.debug=keystore
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @run main/othervm -Djava.security.debug=keystore OutdatedKeyStoreWarning
 */

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Locale;

import jdk.test.lib.SecurityTools;
import jdk.test.lib.util.JarUtils;

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

public class OutdatedKeyStoreWarning {

    private static final String KS_WARNING1 =
            "uses outdated cryptographic algorithms and will be removed " +
            "in a future release. Migrate to PKCS12 using:";

    private static final String KS_WARNING2=
            "keytool -importkeystore -srckeystore <keystore> " +
            "-destkeystore <keystore> -deststoretype pkcs12";

    public static void main(String[] args) throws Exception {
        String[] ksTypes = {"JKS", "JCEKS"};

        for (String type : ksTypes) {
            String ksFile = type.toLowerCase() + ".ks";
            String cmdWarning = type + " " + KS_WARNING1;

            checkWarnings(type, () -> {
                SecurityTools.keytool(String.format(
                        "-genkeypair -keystore %s -storetype %s -storepass changeit " +
                        "-keypass changeit -keyalg ec -alias a1 -dname CN=me " +
                        "-J-Djava.security.debug=keystore",
                        ksFile, type.toLowerCase()))
                        .shouldContain("Warning:")
                        .shouldContain(cmdWarning)
                        .shouldContain(KS_WARNING2)
                        .shouldHaveExitValue(0);
            });

            JarUtils.createJarFile(Path.of("unsigned.jar"), Path.of("."), Path.of(ksFile));
            checkWarnings(type, () -> {
                SecurityTools.jarsigner(String.format(
                        "-keystore %s -storetype %s -storepass changeit -signedjar signed.jar " +
                        "unsigned.jar a1 " +
                        "-J-Djava.security.debug=keystore",
                        ksFile, type.toLowerCase()))
                        .shouldContain("Warning:")
                        .shouldContain(cmdWarning)
                        .shouldContain(KS_WARNING2)
                        .shouldHaveExitValue(0);
            });

            checkWarnings(type, () -> {
                SecurityTools.jarsigner(String.format(
                        "-verify -keystore %s -storetype %s -storepass changeit signed.jar " +
                        "-J-Djava.security.debug=keystore",
                        ksFile, type.toLowerCase()))
                        .shouldContain("Warning:")
                        .shouldContain(cmdWarning)
                        .shouldContain(KS_WARNING2)
                        .shouldHaveExitValue(0);
            });
        }

        for (String type : ksTypes) {
            checkStoreAPIWarning(type);
        }
    }

    private static void checkWarnings(String type, RunnableWithException r) throws Exception {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        PrintStream origOut = System.out;

        try {
            PrintStream pStream = new PrintStream(bOut);
            System.setErr(pStream);
            System.setOut(pStream);
            r.run();
        } finally {
            System.setErr(origErr);
            System.setOut(origOut);
        }

        String msg = bOut.toString();
        if (!msg.contains("WARNING: " + type.toUpperCase(Locale.ROOT)) ||
                !msg.contains(KS_WARNING1) ||
                !msg.contains(KS_WARNING2) ||
                !msg.contains("Warning:")) {
            throw new RuntimeException("Expected warning not found for " + type + ":\n" + msg);
        }
    }

    // Test case for: KeyStore.getInstance("JKS" or "JCEKS"), load(null, null), and
    // store it where warning should be emitted.
    private static void checkStoreAPIWarning(String type) throws Exception {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        PrintStream origOut = System.out;

        try {
            PrintStream pStream = new PrintStream(bOut);
            System.setErr(pStream);
            System.setOut(pStream);

            KeyStore ks = KeyStore.getInstance(type);
            ks.load(null, null);

            CertAndKeyGen cag = new CertAndKeyGen("EC", "SHA256withECDSA");
            cag.generate("secp256r1");
            X509Certificate cert = cag.getSelfCertificate(new X500Name("CN=one"), 3600);
            ks.setKeyEntry("dummy", cag.getPrivateKey(), "changeit".toCharArray(),
                    new Certificate[] {cert});

            try (FileOutputStream fos = new FileOutputStream(type.toLowerCase() +
                    "_storeAPI.ks")) {
                ks.store(fos, "changeit".toCharArray());
            }
        } finally {
            System.setErr(origErr);
            System.setOut(origOut);
        }

        String msg = bOut.toString();
        if (!msg.contains("WARNING: " + type.toUpperCase(Locale.ROOT)) ||
                !msg.contains(KS_WARNING1) ||
                !msg.contains(KS_WARNING2)) {
            throw new RuntimeException("Expected warning not found for KeyStore.store() API (" +
                    type + "):\n" + msg);
        }
    }

    @FunctionalInterface
    interface RunnableWithException {
        void run() throws Exception;
    }
}
