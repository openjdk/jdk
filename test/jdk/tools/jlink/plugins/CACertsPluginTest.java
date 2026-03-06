/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Enumeration;

import jtreg.SkippedException;
import jdk.test.lib.Asserts;
import jdk.test.lib.security.SecurityUtils;
import tests.Helper;

/* @test
 * @bug 8377102
 * @summary Test the --cacerts plugin
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.*
 * @run main CACertsPluginTest
 */

public class CACertsPluginTest {

    private static Helper helper;

    private static String CACERTS_PATH = "lib/security/cacerts";

    public static void main(String[] args) throws Throwable {

        helper = Helper.newHelper();
        if (helper == null) {
            throw new SkippedException("Test not run");
        }

        KeyStore jdkCacerts = SecurityUtils.getCacertsKeyStore();
        Enumeration<String> aliases = jdkCacerts.aliases();
        String alias1 = aliases.nextElement();
        String alias2 = aliases.nextElement();

        // test one alias
        test("testOne", jdkCacerts, alias1);

        // test two aliases
        test("testTwo", jdkCacerts, alias1, alias2);

        // test illegal/bad options
        testBadOptions();
    }

    private static void test(String module, KeyStore jdkCacerts,
            String... aliases) throws Exception {

        helper.generateDefaultJModule(module);

        String option = toOption(aliases);
        Path image = helper.generateDefaultImage(
                new String[] { "--cacerts", option }, module).assertSuccess();
        helper.checkImage(image, module, null, null,
                new String[] { CACERTS_PATH });

        KeyStore imageCacerts = KeyStore.getInstance(
                image.resolve(CACERTS_PATH).toFile(), (char[]) null);

        Asserts.assertEquals(imageCacerts.size(), aliases.length);
        for (String alias : aliases) {
            Asserts.assertTrue(imageCacerts.isCertificateEntry(alias));
            Asserts.assertEquals(
                    jdkCacerts.getCertificate(alias),
                    imageCacerts.getCertificate(alias));
        }
    }

    private static void testBadOptions() throws Exception {

        String module = "testBad";
        helper.generateDefaultJModule(module);
        helper.generateDefaultImage(new String[]
                { "--cacerts", "bogus-alias" }, module)
                .assertFailure("alias bogus-alias does not exist");
    }

    private static String toOption(String... aliases) {
        int max = aliases.length - 1;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; ; i++) {
            sb.append(aliases[i]);
            if (i == max) {
                return sb.toString();
            }
            sb.append(",");
        }
    }
}
