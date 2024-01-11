/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8301788
 * @library /test/lib
 * @summary AlgorithmId should keep lowercase characters from 3rd party providers
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 */
import jdk.test.lib.Asserts;
import sun.security.x509.AlgorithmId;

import java.security.Provider;
import java.security.Security;
import java.util.Locale;

public class Uppercase {

    private static final String OID = "2.3.4.5.8301788";
    private static final String ALG = "Oolala";

    public static void main(String[] args) throws Exception {
        Security.addProvider(new ProviderImpl());
        Asserts.assertEQ(AlgorithmId.get(ALG).getOID().toString(), OID);
        Asserts.assertEQ(AlgorithmId.get(ALG.toUpperCase(Locale.ROOT)).getOID().toString(), OID);
        Asserts.assertEQ(AlgorithmId.get(OID).getName(), ALG);
    }

    public static class ProviderImpl extends Provider {
        public ProviderImpl() {
            super("ProviderImpl", "1.0", "ProviderImpl");
            // It does not matter if we really provide an implementation
            put("MessageDigest." + ALG, "Uppercase$MessageDigestImpl");
            put("Alg.Alias.MessageDigest.OID." + OID, ALG);
        }
    }
}
