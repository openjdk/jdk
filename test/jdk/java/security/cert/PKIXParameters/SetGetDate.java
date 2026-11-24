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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertNull;

import java.security.KeyPairGenerator;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.util.Date;
import java.util.Set;

/*
 * @test
 * @bug 8394608
 * @summary Check PKIXParameters date set/get/reset
 * @library /test/lib
 */

public class SetGetDate {

    public static void main(String[] args) throws Exception {
        Set<TrustAnchor> anchors = Set.of(new TrustAnchor(
                "CN=Test CA",
                KeyPairGenerator.getInstance("EC")
                        .generateKeyPair().getPublic(),
                null));

        checkDate(new PKIXParameters(anchors));
        checkDate(new PKIXBuilderParameters(anchors, null));
    }

    private static void checkDate(PKIXParameters params) {
        assertNull(params.getDate(), "Default date must be null");

        for (long time : new long[]{0L, 86400000L}) {
            Date expected = new Date(time);
            Date supplied = new Date(time);

            params.setDate(supplied);
            assertEquals(expected, params.getDate(),
                    "setDate() must preserve the supplied value");

            supplied.setTime(time + 1000L);
            assertEquals(expected, params.getDate(),
                    "setDate() must copy its argument");

            Date returned = params.getDate();
            returned.setTime(time + 2000L);
            assertEquals(expected, params.getDate(),
                    "getDate() must return a copy");

            params.setDate(null);
            assertNull(params.getDate(),
                    "setDate(null) must clear the previous date");
        }
    }
}
