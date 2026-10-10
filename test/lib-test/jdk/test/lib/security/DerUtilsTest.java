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

import java.io.IOException;
import java.util.HexFormat;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.security.DerUtils;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.KnownOIDs;
import sun.security.util.ObjectIdentifier;

/*
 * @test
 * @bug 8381049
 * @library /test/lib
 * @modules java.base/sun.security.util
 * @summary Tests DerUtils navigation, assertions, and editing helpers
 */
public class DerUtilsTest {

    public static void main(String[] args) throws Exception {
        //0000:0015  [] SEQUENCE
        //0002:0004  [0]     OID 1.2.3
        //0006:0003  [1]     INTEGER 1
        //0009:000C  [2]     OCTET STRING
        //              >>> into 10 octets
        //000B:000A  [2c]         SEQUENCE
        //000D:0005  [2c0]             OID 2.5.4.3 (CommonName)
        //0012:0003  [2c1]             INTEGER 2
        byte[] der = bytes("30 13 06022a03 020101 04 0a 30 08 0603550403 020102");

        // Test innerDerValue
        Asserts.assertEQ(DerUtils.innerDerValue(der, "0").getOID(),
                ObjectIdentifier.of("1.2.3"));
        Asserts.assertEQ(DerUtils.innerDerValue(der, "1").getInteger(), 1);
        Asserts.assertEQ(DerUtils.innerDerValue(der, "2c0").getOID(),
                ObjectIdentifier.of(KnownOIDs.CommonName));
        Asserts.assertEQ(DerUtils.innerDerValue(der, "2c1").getInteger(), 2);
        Asserts.assertTrue(DerUtils.innerDerValue(der, "3") == null);

        // Test checks
        DerUtils.checkAlg(der, "0", ObjectIdentifier.of("1.2.3"));
        DerUtils.checkInt(der, "1", 1);
        DerUtils.checkAlg(der, "2c0", ObjectIdentifier.of(KnownOIDs.CommonName));
        DerUtils.checkInt(der, "2c1", 2);
        DerUtils.shouldNotExist(der, "3");

        // Test edit
        der = DerUtils.edit(der, "0", oidValue("1.2.3.4"));
        Asserts.assertEqualsByteArray(
                bytes("30 14 06032a0304 020101 04 0a 30 08 0603550403 020102"), der);

        der = DerUtils.edit(der, "2c1", intValue(8));
        Asserts.assertEqualsByteArray(
                bytes("30 14 06032a0304 020101 04 0a 30 08 0603550403 020108"), der);

        der = DerUtils.edit(der, "1", null);
        Asserts.assertEqualsByteArray(
                bytes("30 11 06032a0304 04 0a 30 08 0603550403 020108"), der);

        // Test insert
        der = DerUtils.insert(der, "0", oidValue("1.2.5"));
        Asserts.assertEqualsByteArray(
                bytes("30 15 06022a05 06032a0304 04 0a 30 08 0603550403 020108"), der);

        der = DerUtils.insert(der, "2c1", intValue(9));
        Asserts.assertEqualsByteArray(
                bytes("30 18 06022a05 06032a0304 04 0d 30 0b 0603550403 020109 020108"), der);

        der = DerUtils.insert(der, "2c1", oidValue("1.2.6"));
        Asserts.assertEqualsByteArray(
                bytes("30 1c 06022a05 06032a0304 04 11 30 0f 0603550403 06022a06 020109 020108"), der);

        // Cannot insert into a position ends with "c"
        var derClone = der.clone(); // non-final reference cannot be used in lambda
        Utils.runAndCheckException(() -> DerUtils.insert(derClone, "2c",
                oidValue("1.2.7")), IOException.class);
    }

    static DerValue oidValue(String oid) throws IOException {
        return DerValue.wrap(new DerOutputStream()
                .putOID(ObjectIdentifier.of(oid)).toByteArray());
    }

    static DerValue intValue(int value) throws IOException {
        return DerValue.wrap(new DerOutputStream()
                .putInteger(value).toByteArray());
    }

    public static byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex.replace(" ", ""));
    }
}
