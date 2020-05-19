/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4162868 8130181 8242151
 * @modules java.base/sun.security.x509
 * @modules java.base/sun.security.util
 * @run main/othervm ExtensibleAlgorithmId
 * @summary Algorithm Name-to-OID mapping needs to be made extensible.
 */

// Run in othervm, coz AlgorithmId.oidTable is only initialized once

import java.security.*;
import sun.security.x509.AlgorithmId;

public class ExtensibleAlgorithmId {

    public static void main(String[] args) throws Exception {
        TestProvider p = new TestProvider();
        Security.addProvider(p);
        AlgorithmId algid = AlgorithmId.getAlgorithmId(TestProvider.ALG_NAME);
        String oid = algid.getOID().toString();
        if (!oid.equals(TestProvider.ALG_OID)) {
            throw new Exception("Provider alias oid not used, found " + oid);
        }
        String name = algid.getName();
        if (!name.equalsIgnoreCase(TestProvider.ALG_NAME)) {
            throw new Exception("provider alias name not used, found " + name);
        }
        String alias = "Alg.Alias.Signature.OID." + oid;
        String stdAlgName = p.getProperty(alias);
        if (stdAlgName == null ||
                !stdAlgName.equalsIgnoreCase(TestProvider.ALG_NAME)) {
            throw new Exception("Wrong OID");
        }
    }

    static class TestProvider extends Provider {

        static String ALG_OID = "1.2.3.4.5.6.7.8.9.0";
        static String ALG_NAME = "XYZ";

        public TestProvider() {
        super("Dummy", "1.0", "XYZ algorithm");

            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {

                    put("Signature." + ALG_NAME, "test.xyz");
                    // preferred OID
                    put("Alg.Alias.Signature.OID." + ALG_OID,
                        ALG_NAME);
                    put("Alg.Alias.Signature.9.8.7.6.5.4.3.2.1.0",
                        ALG_NAME);
                    return null;
                }
            });
        }
    }
}
