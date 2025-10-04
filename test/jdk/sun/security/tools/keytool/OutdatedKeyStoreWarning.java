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
 *         JKS and JCEKS keystore
 * @library /test/lib
 */

import java.nio.file.Path;

import jdk.test.lib.SecurityTools;
import jdk.test.lib.util.JarUtils;

public class OutdatedKeyStoreWarning {

    public static void main(String[] args) throws Exception {
        String[] ksTypes = {"JKS", "JCEKS"};

        for (String type : ksTypes) {
            String ksFile = type.toLowerCase() + ".ks";
            String KS_WARNING = type + " uses outdated cryptographic algorithms and " +
                    "will be removed in a future release. Migrate to PKCS12 using:";

            SecurityTools.keytool(String.format(
                    "-genkeypair -keystore %s -storetype %s -storepass changeit " +
                    "-keypass changeit -keyalg ec -alias a1 -dname CN=me",
                    ksFile, type.toLowerCase()))
                    .shouldContain("Warning:")
                    .shouldContain(KS_WARNING)
                    .shouldMatch("keytool -importkeystore -srckeystore." +
                            "*-destkeystore.*-deststoretype pkcs12")
                    .shouldHaveExitValue(0);

            JarUtils.createJarFile(Path.of("unsigned.jar"), Path.of("."),
                    Path.of(ksFile));

            SecurityTools.jarsigner(String.format(
                    "-keystore %s -storetype %s -storepass changeit -signedjar signed.jar " +
                    "unsigned.jar a1",
                    ksFile, type.toLowerCase()))
                    .shouldContain("Warning:")
                    .shouldContain(KS_WARNING)
                    .shouldMatch("keytool -importkeystore -srckeystore." +
                            "*-destkeystore.*-deststoretype pkcs12")
                    .shouldHaveExitValue(0);

            SecurityTools.jarsigner(String.format(
                    "-verify -keystore %s -storetype %s -storepass changeit signed.jar",
                    ksFile, type.toLowerCase()))
                    .shouldContain("Warning:")
                    .shouldContain(KS_WARNING)
                    .shouldMatch("keytool -importkeystore -srckeystore." +
                            "*-destkeystore.*-deststoretype pkcs12")
                    .shouldHaveExitValue(0);
        }
    }
}
