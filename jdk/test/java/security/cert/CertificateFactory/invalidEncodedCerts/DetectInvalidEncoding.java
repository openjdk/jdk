/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4776466
 * @summary check that CertificateFactory rejects invalid encoded X.509 certs
 */

import java.io.*;
import java.security.cert.*;

public class DetectInvalidEncoding {

    public static void main(String[] args) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        File f = new File
            (System.getProperty("test.src", "."), "invalidcert.pem");
        InputStream inStream = new FileInputStream(f);
        try {
            X509Certificate cert =
                (X509Certificate) cf.generateCertificate(inStream);
        } catch (CertificateParsingException ce) {
            return;
        }
        throw new Exception("CertificateFactory.generateCertificate() did not "
            + "throw CertificateParsingException on invalid X.509 cert data");
    }
}
