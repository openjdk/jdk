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
 * @bug 8265372
 * @summary checking PKCS#9 encoding and decoding
 * @modules java.base/sun.security.pkcs:+open
 *          java.base/sun.security.util
 *          java.base/sun.security.x509
 */
import sun.security.pkcs.PKCS9Attribute;
import sun.security.pkcs.SignerInfo;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.KnownOIDs;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.BasicConstraintsExtension;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.X500Name;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;

import static sun.security.pkcs.PKCS9Attribute.*;

public class EncodeDecode {
    public static void main(String[] args) throws Exception {
        test(EMAIL_ADDRESS_OID, new String[]{"a@a.com", "b@b.org"},
            "301f06092a864886f70d010901311216076140612e636f6d16076240622e6f7267");
        test(UNSTRUCTURED_NAME_OID, new String[]{"a@a.com", "b@b.org"},
            "301f06092a864886f70d010902311216076140612e636f6d16076240622e6f7267");
        test(CONTENT_TYPE_OID, CONTENT_TYPE_OID,
            "301806092a864886f70d010903310b06092a864886f70d010903");
        test(MESSAGE_DIGEST_OID, new byte[10],
            "301906092a864886f70d010904310c040a00000000000000000000");
        test(SIGNING_TIME_OID, new Date(0),
            "301c06092a864886f70d010905310f170d3730303130313030303030305a");

        var sis = new SignerInfo[] {
            new SignerInfo(new X500Name("CN=x"),
                BigInteger.ONE,
                AlgorithmId.get("SHA-256"),
                AlgorithmId.get("Ed25519"),
                new byte[10])
        };
        test(COUNTERSIGNATURE_OID, sis,
            "304706092a864886f70d010906313a30380201013011300c310a30080603550403130178020101300d06096086480165030402010500300506032b6570040a00000000000000000000");

        test(CHALLENGE_PASSWORD_OID, "password",
            "301706092a864886f70d010907310a130870617373776f7264");
        test(UNSTRUCTURED_ADDRESS_OID, new String[]{"a@a.com", "b@b.org"},
            "301f06092a864886f70d010908311213076140612e636f6d13076240622e6f7267");

        var exts = new CertificateExtensions();
        exts.setExtension("bc", new BasicConstraintsExtension(true, true, 2));
        test(EXTENSION_REQUEST_OID, exts,
            "302306092a864886f70d01090e3116301430120603551d130101ff040830060101ff020102");

        var c = Class.forName("sun.security.pkcs.SigningCertificateInfo");
        var ctor = c.getDeclaredConstructor(byte[].class);
        ctor.setAccessible(true);
        // A SigningCertificateInfo with an empty ESSCertID
        var sci = ctor.newInstance((Object) new DerOutputStream()
            .write(DerValue.tag_Sequence, new DerOutputStream()
                .write(DerValue.tag_Sequence, new DerOutputStream()))
            .toByteArray());
        test(SIGNING_CERTIFICATE_OID, sci,
            "3013060b2a864886f70d010910020c310430023000");

        var onev = new DerOutputStream().write(DerValue.tag_Sequence,
                new DerOutputStream().putOctetString(new byte[10]))
            .toByteArray();

        test(SIGNATURE_TIMESTAMP_TOKEN_OID, onev,
            "301d060b2a864886f70d010910020e310e300c040a00000000000000000000");
        test(CMS_ALGORITHM_PROTECTION_OID, onev,
            "301b06092a864886f70d010934310e300c040a00000000000000000000");

        //Test whether unsupported OIDs are handled properly
        test(AlgorithmId.SHA_oid,
            new DerOutputStream().write(DerValue.tag_Set, new DerOutputStream().putBoolean(true)).toByteArray(),
            "300c06052b0e03021a31030101ff");
    }

    static void test(ObjectIdentifier oid, Object value, String expected) throws Exception {
        System.out.println("---------- " + KnownOIDs.findMatch(oid.toString()).name());
        var p9 = new PKCS9Attribute(oid, value);
        var enc = new DerOutputStream().write(p9).toByteArray();
        if (!HexFormat.of().formatHex(enc).equals(expected)) {
            throw new RuntimeException("encode unmatch");
        }
        var nv = new PKCS9Attribute(new DerValue(enc)).getValue();
        boolean equals;
        if (value instanceof SignerInfo[] si) {
            // equals not defined for SignerInfo
            equals = Arrays.toString(si).equals(Arrays.toString((SignerInfo[])nv));
        } else if (value instanceof byte[] bb) {
            equals = Arrays.equals(bb, (byte[]) nv);
        } else if (value.getClass().isArray()) {
            equals = Arrays.equals((Object[]) value, (Object[]) nv);
        } else if (value.getClass().getName().equals("sun.security.pkcs.SigningCertificateInfo")) {
            // equals not defined for SigningCertificateInfo
            equals = value.toString().equals(nv.toString());
        } else {
            equals = nv.equals(value);
        }
        if (!equals) {
            throw new RuntimeException("decode unmatch");
        }
    }
}