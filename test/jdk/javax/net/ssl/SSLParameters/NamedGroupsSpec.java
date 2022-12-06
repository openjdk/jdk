/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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
 * @bug 8281236
 * @summary (D)TLS key exchange named groups
 */

import javax.net.ssl.SSLParameters;

public class NamedGroupsSpec {
    public static void main(String[] args) throws Exception {
        runTest(new String[] {
                        "x25519",
                        "secp256r1"
                },
                false);
        runTest(new String[] {
                        "x25519",
                        "x25519"
                },
                true);
        runTest(new String[] {
                        null
                },
                true);
        runTest(new String[] {
                        ""
                },
                true);
        runTest(new String[] {
                        "x25519",
                        ""
                },
                true);
        runTest(new String[] {
                        "x25519",
                        null
                },
                true);
    }

    private static void runTest(String[] namedGroups,
                                boolean exceptionExpected) throws Exception {
        SSLParameters sslParams = new SSLParameters();
        try {
            sslParams.setNamedGroups(namedGroups);
        } catch (Exception ex) {
            if (!exceptionExpected ||
                    !(ex instanceof IllegalArgumentException)) {
                throw ex;
            } else {  // Otherwise, swallow the exception and return.
                return;
            }
        }

        if (exceptionExpected) {
            throw new RuntimeException("Unexpected success!");
        }
    }
}
