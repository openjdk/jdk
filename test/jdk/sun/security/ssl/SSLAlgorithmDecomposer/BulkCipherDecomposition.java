/*
 * Copyright (c) 2026, IBM Corporation. All rights reserved.
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
 * @bug 8387124
 * @summary Verify SSLAlgorithmDecomposer bulk cipher decomposition
 * @library /javax/net/ssl/TLSCommon
 * @run main/othervm
 *      --add-opens java.base/sun.security.ssl=ALL-UNNAMED
 *      BulkCipherDecomposition
 */

import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class BulkCipherDecomposition {

    private static void testDecomposition(Object instance, Method decomposeMethod, String suite)
            throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) decomposeMethod.invoke(instance, suite);

        String expectedBulk = extractBulkCipher(suite);

        System.out.println("=================================================");
        System.out.println("Testing suite   : " + suite);
        System.out.println("Expected bulk   : " + expectedBulk);
        System.out.println("Decomposition   : " + result);

        if (!result.contains(expectedBulk)) {
            throw new RuntimeException(
                    "Missing bulk cipher decomposition\n" +
                            "Suite: " + suite + "\n" +
                            "Expected: " + expectedBulk + "\n" +
                            "Actual: " + result);
        }
    }

    /**
     * Separator used in TLS cipher suite names to mark the start of
     * the bulk cipher component (e.g. TLS_RSA_WITH_AES_128_CBC_SHA).
     */
    private static final String WITH = "_WITH_";

    private static String extractBulkCipher(String suite) {
        if (suite.contains(WITH)) {
            String after = suite.substring(suite.indexOf(WITH) + WITH.length());
            int last = after.lastIndexOf('_');
            return after.substring(0, last);
        } else {
            int first = suite.indexOf('_');
            int last = suite.lastIndexOf('_');
            return suite.substring(first + 1, last);
        }
    }

    private static String[] getCipherSuites() throws NoSuchAlgorithmException {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        return Arrays.stream(engine.getSupportedCipherSuites())
                .map(CipherSuite::cipherSuite)
                .filter(cs -> cs != CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV)
                .map(CipherSuite::name)
                .toArray(String[]::new);
    }

    public static void main(String[] args) throws Exception {
        // disabledAlgorithms limits supported suites; clear to list all
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        Class<?> c = Class.forName("sun.security.ssl.SSLAlgorithmDecomposer");
        var ctor = c.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();
        Method decomposeMethod = c.getDeclaredMethod("decompose", String.class);
        decomposeMethod.setAccessible(true);

        for (String suite : getCipherSuites()) {
            testDecomposition(instance, decomposeMethod, suite);
        }

        System.out.println("PASS");
    }
}
