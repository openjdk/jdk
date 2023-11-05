/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;

/**
 * @test
 * @bug 8234128
 * @summary Additional test for aliasNotInStore warning
 * @library /test/lib ../
 * @build jdk.test.lib.util.JarUtils
 * @run main AliasNotInStoreTest2
 */
public class AliasNotInStoreTest2 extends Test {

    /**
     * The test signs and verifies a jar that contains signed entries
     * that are not signed by any alias in keystore (aliasNotInStore).
     * Warning message is expected.
     */
    public static void main(String[] args) throws Throwable {
        AliasNotInStoreTest2 test = new AliasNotInStoreTest2();
        test.start();
    }

    private void start() throws Throwable {

        createAlias(CA_KEY_ALIAS, "-ext", "bc");
        createAlias(FIRST_KEY_ALIAS);

        issueCert(FIRST_KEY_ALIAS);

        JarUtils.createJar(UNSIGNED_JARFILE, FIRST_FILE);

        // sign jar with first key
        OutputAnalyzer analyzer = jarsigner(
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-signedjar", SIGNED_JARFILE,
                UNSIGNED_JARFILE,
                FIRST_KEY_ALIAS);

        checkSigning(analyzer);

        // remove signer
        keytool(
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-delete",
                "-alias", FIRST_KEY_ALIAS);

        // "not signed by any alias in the keystore" warning should be present
        analyzer = jarsigner(
                "-verify",
                "-keystore", KEYSTORE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                SIGNED_JARFILE);

        checkVerifying(analyzer, 0, ALIAS_NOT_IN_STORE_VERIFYING_WARNING);

        System.out.println("Test passed");
    }

}
