/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8208350 8163327
 * @summary Disable all DES, RC4, and 3DES/DesEde cipher suites
 * @run main/othervm NoDesRC4DesEdeCiphSuite
 */

/*
 * SunJSSE does not support dynamic system properties, no way to re-use
 * system properties in samevm/agentvm mode.
 */

import java.security.Security;
import java.util.Arrays;
import java.util.List;

public class NoDesRC4DesEdeCiphSuite extends AbstractDisableCipherSuites {


    // These are some groups of Cipher Suites by names and IDs
    private static final List<Integer> DES_CS_LIST = Arrays.asList(
        0x0009, 0x0015, 0x0012, 0x001A, 0x0008, 0x0014, 0x0011, 0x0019
    );
    private static final String[] DES_CS_LIST_NAMES = new String[] {
        "SSL_RSA_WITH_DES_CBC_SHA",
        "SSL_DHE_RSA_WITH_DES_CBC_SHA",
        "SSL_DHE_DSS_WITH_DES_CBC_SHA",
        "SSL_DH_anon_WITH_DES_CBC_SHA",
        "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA"
    };
    private static final List<Integer> RC4_CS_LIST = Arrays.asList(
        0xC007, 0xC011, 0x0005, 0xC002, 0xC00C, 0x0004, 0xC016, 0x0018,
        0x0003, 0x0017
    );
    private static final String[] RC4_CS_LIST_NAMES = new String[] {
        "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
        "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
        "SSL_RSA_WITH_RC4_128_SHA",
        "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
        "TLS_ECDH_RSA_WITH_RC4_128_SHA",
        "SSL_RSA_WITH_RC4_128_MD5",
        "TLS_ECDH_anon_WITH_RC4_128_SHA",
        "SSL_DH_anon_WITH_RC4_128_MD5",
        "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
        "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5"
    };
    private static final List<Integer> DESEDE_CS_LIST = Arrays.asList(
        0xC008, 0xC012, 0x0016, 0x0013, 0xC003, 0xC00D, 0x000A
    );
    private static final String[] DESEDE_CS_LIST_NAMES = new String[] {
        "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
    };


    public static void main(String[] args) throws Exception {
        boolean allGood = true;
        String disAlg = Security.getProperty("jdk.tls.disabledAlgorithms");
        System.err.println("Disabled Algs: " + disAlg);
        NoDesRC4DesEdeCiphSuite test = new NoDesRC4DesEdeCiphSuite();

        // Disabled DES tests
        allGood &= test.testDefaultCase(DES_CS_LIST);
        allGood &= test.testEngAddDisabled(DES_CS_LIST_NAMES, DES_CS_LIST);
        allGood &= test.testEngOnlyDisabled(DES_CS_LIST_NAMES);

        // Disabled RC4 tests
        allGood &= test.testDefaultCase(RC4_CS_LIST);
        allGood &= test.testEngAddDisabled(RC4_CS_LIST_NAMES, RC4_CS_LIST);
        allGood &= test.testEngOnlyDisabled(RC4_CS_LIST_NAMES);

        // Disabled 3DES tests
        allGood &= test.testDefaultCase(DESEDE_CS_LIST);
        allGood &= test.testEngAddDisabled(DESEDE_CS_LIST_NAMES, DESEDE_CS_LIST);
        allGood &= test.testEngOnlyDisabled(DESEDE_CS_LIST_NAMES);

        if (allGood) {
            System.err.println("All tests passed");
        } else {
            throw new RuntimeException("One or more tests failed");
        }
    }
}
