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
 * @summary check SSLParameters.setNamedGroups() implementation
 */

import javax.net.ssl.SSLParameters;
import java.util.Arrays;

public class NamedGroupsSpec {
    public static void main(String[] args) throws Exception {
        runTest(null,             // null array should be allowed.
                false);
        runTest(new String[] {    // empty array should be allowed
                    // blank line
                },
                false);
        runTest(new String[] {    // multiple elements should be fine
                        "x25519",
                        "secp256r1"
                },
                false);
        runTest(new String[] {    // no duplicate element should be allowed
                        "x25519",
                        "x25519"
                },
                true);
        runTest(new String[] {    // no null element should be allowed
                        null
                },
                true);
        runTest(new String[] {    // no blank element should be allowed
                        ""
                },
                true);
        runTest(new String[] {    // no blank element should be allowed
                        "x25519",
                        ""
                },
                true);
        runTest(new String[] {    // no null element should be allowed.
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

        // Check if the getNamedGroups() method returns the same elements.
        String[] configuredNamedGroups = sslParams.getNamedGroups();
        if (!Arrays.equals(namedGroups, configuredNamedGroups)) {
            throw new RuntimeException(
                    "SSLParameters.getNamedGroups() method does not return "
                  + "the same elements as set with setNamedGroups()");
        }
    }
}
