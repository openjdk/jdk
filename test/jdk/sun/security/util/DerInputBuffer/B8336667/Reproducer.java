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
 * @bug 8336667
 * @summary Ensure the unused bytes are calculated correctly when converting
 *          indefinite length BER to DER
 */
import java.io.ByteArrayInputStream;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.util.Base64;

public class Reproducer {
    private static final String INPUT = """
            MIIBljCCAVMwgAaB/////////yb////////////////////9////AgDv////////////////////
            /////2RjPWNvbf////8k/////////yb///////////////////9vbf////8k/////////yb/////
            ////////////////////AgD/////////////b23/////JP////////8m/////yf/////////////
            /////wIA//////////////////////////////////////8AAABl//////8m/////////y1CRUdJ
            TiA9Y290cnVlVlZWVlZWVlZWVjEAAAAAAAAArQdVUwNVBAsTA0RvRDEaMBhAA1UAAAAAAAAAAAAA
            AAAAAAAAAAAAAAAAAAEXDTAzMDcxNTE2MjAwNFqgHzAdMA8GA1UdHAEB/wQFMAPyAf8wCgYDVR0P
            BAMCAQIwCwYHKoZIzjgEAwUAAzBkARkTA2NvbTEYMBYGCgmSJomT8ixkARkTCG15VGVzdENBMBIC
            AQHyAjZG+RfHdO4=""";

    Reproducer(byte[] data) {
        try {
            java.security.cert.CertificateFactory.
                    getInstance("X.509").generateCRLs(new ByteArrayInputStream(data));
        } catch (CertificateException | CRLException e) {
            if (System.getProperty("dbg", "false").equals("true")) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] a) throws Exception {
        byte[] decodedBytes = Base64.getMimeDecoder().decode(INPUT);
        new Reproducer(decodedBytes);
    }
}
