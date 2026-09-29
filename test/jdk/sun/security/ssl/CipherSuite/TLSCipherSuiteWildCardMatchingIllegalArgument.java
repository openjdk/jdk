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
 * @bug 8341964
 * @summary Add mechanism to disable different parts of TLS cipher suite
 * @run testng/othervm TLSCipherSuiteWildCardMatchingIllegalArgument
 */

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.security.Security;

import javax.net.ssl.SSLContext;

/**
 * SSLContext loads "jdk.tls.disabledAlgorithms" system property statically
 * when it's being loaded into memory, so we can't call
 * Security.setProperty("jdk.tls.disabledAlgorithms") more than once per test
 * class. Thus, we need a separate test class each time we need to modify
 * "jdk.tls.disabledAlgorithms" config value for testing.
 */
public class TLSCipherSuiteWildCardMatchingIllegalArgument {

    private static final String SECURITY_PROPERTY =
            "jdk.tls.disabledAlgorithms";
    private static final String TEST_ALGORITHMS = "ECDHE_*_WITH_AES_256_GCM_*";

    @BeforeTest
    void setUp() throws Exception {
        Security.setProperty(SECURITY_PROPERTY, TEST_ALGORITHMS);
    }

    @Test
    public void testChainedBefore() throws Exception {
        try {
            SSLContext.getInstance("TLS");
            fail("No IllegalArgumentException was thrown");
        } catch (ExceptionInInitializerError e) {
            assertEquals(IllegalArgumentException.class,
                         e.getCause().getClass());
            assertEquals("Wildcard pattern must start with \"TLS_\"",
                         e.getCause().getMessage());
        }
    }
}
