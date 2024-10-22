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
 * @bug 8342838
 * @summary ECDHE algorithm can't be disabled for TLSv1.3 cipher suites
 * @run testng/othervm DisableKeyExchangeAlgorithmOfCipherSuiteTLSv13
 */

import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.security.Security;
import java.util.List;

public class DisableKeyExchangeAlgorithmOfCipherSuiteTLSv13 extends NoDesRC4DesEdeCiphSuite {

    private static final String SECURITY_PROPERTY = "jdk.tls.disabledAlgorithms";
    private static final String TEST_ALGORITHMS = "ECDHE";
    private static final String[] CIPHER_SUITES = new String[] {
        "TLS_AES_256_GCM_SHA384",
        "TLS_AES_128_GCM_SHA256",
        "TLS_CHACHA20_POLY1305_SHA256"
    };
    static final List<Integer> CIPHER_SUITES_IDS = List.of(
        0x1302,
        0x1301,
        0x1303
    );

    @Override
    protected String getProtocol() {
        return "TLSv1.3";
    }

    @BeforeTest
    void setUp() throws Exception {
        Security.setProperty(SECURITY_PROPERTY, TEST_ALGORITHMS);
    }

    @Test
    public void testDefault() throws Exception {
        assertTrue(testDefaultCase(CIPHER_SUITES_IDS));
    }

    @Test
    public void testAddDisabled() throws Exception {
        assertTrue(testEngAddDisabled(CIPHER_SUITES, CIPHER_SUITES_IDS));
    }

    @Test
    public void testOnlyDisabled() throws Exception {
        assertTrue(testEngOnlyDisabled(CIPHER_SUITES));
    }
}
