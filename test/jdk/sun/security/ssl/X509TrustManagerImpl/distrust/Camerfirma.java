/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.security.Security;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

/*
 * @test
 * @bug 8346587 8350498
 * @summary Check that TLS Server certificates chaining back to distrusted
 *          Camerfirma root are invalid
 * @library /test/lib
 * @modules java.base/sun.security.validator
 * @run main/othervm Camerfirma after policyOn invalid
 * @run main/othervm Camerfirma after policyOff valid
 * @run main/othervm Camerfirma before policyOn valid
 * @run main/othervm Camerfirma before policyOff valid
 */

public class Camerfirma {

    private static final String CERT_PATH = "chains" + File.separator + "camerfirma";

    // Each of the roots have a test certificate chain stored in a file
    // named "<root>-chain.pem".
    private static final String ROOT_TO_TEST = "camerfirmachambersca";

    // Date after the restrictions take effect
    private static final ZonedDateTime DISTRUST_DATE =
            LocalDate.of(2025, 04, 16).atStartOfDay(ZoneOffset.UTC);

    public static void main(String[] args) throws Exception {

        // All the test certificates are signed with SHA-1, so we need
        // to remove the constraint that disallows SHA-1 certificates.
        String prop = Security.getProperty("jdk.certpath.disabledAlgorithms");
        String newProp = prop.replace(", SHA1 jdkCA & usage TLSServer", "");
        Security.setProperty("jdk.certpath.disabledAlgorithms", newProp);

        Distrust distrust = new Distrust(args);

        X509TrustManager[] tms = new X509TrustManager[]{
                distrust.getTMF("PKIX", null),
                distrust.getTMF("SunX509", null)
        };

        Date notBefore = distrust.getNotBefore(DISTRUST_DATE);
        distrust.testCertificateChain(CERT_PATH, notBefore, tms, ROOT_TO_TEST);
    }
}
