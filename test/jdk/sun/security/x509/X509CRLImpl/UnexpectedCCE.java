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
 * @bug 8336665
 * @summary Verify that generateCRLs method does not throw ClassCastException.
 *          It should throw CRLException instead.
 * @library /test/lib
 */
import java.security.NoSuchProviderException;
import java.security.cert.*;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import jdk.test.lib.Utils;

public class UnexpectedCCE {
    static CertificateFactory cf = null;

    public static void main(String[] av ) throws CertificateException,
           NoSuchProviderException {

        // Fuzzed data input stream looks like an x509.OIDName
        // in the CertificateIssuerExtension. A CRLException is thrown
        // because an X500Name is expected.
        byte[] encoded_1 = Base64.getDecoder().decode("""
            MIIBljCCAVMCAQEwCwYHKoZIzjgEAwUAMC0xEzARBgoJkiaJk/IsZAEZEwNjb20xFjA\
            UBgoJkiaJjvIsZAEZEwZ0ZXN0Q0EXDTAzMDcxNTE2MjAwNVoXDTAzMDcyMDE2MjAwNV\
            owgdIwUwIBBBcNMDMwNzE1MTYyMDAzWjA/MD0GA1UdHQEB/wQzMDGILzETMBEGCgmSJ\
            omT8ixkARkMA2NvbTEYMBYGCgmSJomT8ixkARkTCGNlcnRzUlVTMBICAQMXDTAzMDcx\
            NTE2MjAwNFowUwIBAhcNMDMwNzE1MTYyMDA0WjA/MD0GA1UdIQEB/wQzMDEwGAYDVQQ\
            DExEwDyqGMDEUMgAwgDAuRQA1MRYGCgmSJomT8ixkARkTCG15VGVzdENBMBICAQEXDT\
            AzMDcxNTE2MjAwNFqgHzAdMA8GA1UdHAEB/wQFMAOEAf8wCgYDVR0UAwACAQIwCwYHK\
            oZIzjgEAwUAAzAAMC0CFBaZDryEEOr8Cw7sOAAAAKaDgtHcAhUAkUenJpwYZgS6IPjy\
            AjZG+RfHdO4=""");

        // Fuzzed data input stream looks like an x509.X400Address
        // in the CertificateIssuerExtension. A CRLException is thrown
        // because an X500Name is expected.
        byte[] encoded_2 = Base64.getDecoder().decode("""
            MIIBljCCAVMCAQEwCwYHKoZIzjgEAwUAMC0xEzARBgoJkiaJk/IsZAEZEwNjb20xFjA\
            UBgoJkiaJk/IsZAEZEwZ0ZXN0J0EXDTAzMDcxNTE2MjAwNVoXDTAzMDcyMDE2MjAwNV\
            owgdIwUwIBBBcNMDMwNzE1MTYyMDA0WjA/MD0GA1UdHQEB/wQzMDGkLzETMBEGCgmSJ\
            omT8ixkARkTA2NvbTEYMBYGCgmSJomT8ixkARkTCGNlcnRzUlVTMBICAQMXDTAzMDcx\
            NTE2MjAwNFowUwIBAhcNMDMwNzE1MTYyMDA0WjA/MD0GA1UdHQEB/wQzMDGjLzETMBE\
            GCgmSJomT8ixkARkTA2NvGG0wMRYGCgmSJomT8ixkARkTCG15VGVzdENBMBICAQEXDT\
            AzMDcxNTE2MjAwNVqgHzAdMGAGA1UdHAEB/wQFMAOEAf8wCgYDVR0UBAMCAQIwCwYHK\
            oZIzjgEAwUAAzAAMC0CFBaZDryEEOr8Cw7sJa07gqaDgtHcAhUAkUenJpwYZgS6IPjy\
            AjZG+RfHdO4=""");

        cf = CertificateFactory.getInstance("X.509", "SUN");

        run(encoded_1);
        run(encoded_2);
    }

    private static void run(byte[] buf) {
        Utils.runAndCheckException(
                () -> cf.generateCRLs(new ByteArrayInputStream(buf)),
                CRLException.class);
    }
}
