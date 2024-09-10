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

import java.io.File;
import java.time.*;
import java.util.*;
import javax.net.ssl.*;

/**
 * @test
 * @bug 8337664
 * @summary Check that TLS Server certificates chaining back to distrusted
 *          Entrust roots are invalid
 * @library /test/lib
 * @modules java.base/sun.security.validator
 * @run main/othervm Entrust after policyOn invalid
 * @run main/othervm Entrust after policyOff valid
 * @run main/othervm Entrust before policyOn valid
 * @run main/othervm Entrust before policyOff valid
 */

public class Entrust {

    private static final String certPath = "chains" + File.separator + "entrust";

    // Each of the roots have a test certificate chain stored in a file
    // named "<root>-chain.pem".
    private static String[] rootsToTest = new String[]{
            "entrustevca", "entrustrootcaec1", "entrustrootcag2", "entrustrootcag4",
            "entrust2048ca", "affirmtrustcommercialca", "affirmtrustnetworkingca",
            "affirmtrustpremiumca", "affirmtrustpremiumeccca"};

    // A date that is after the restrictions take effect
    private static final Date NOVEMBER_1_2024 =
            Date.from(LocalDate.of(2024, 11, 1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant());

    // A date that is a second before the restrictions take effect
    private static final Date BEFORE_NOVEMBER_1_2024 =
            Date.from(LocalDate.of(2024, 11, 1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .minusSeconds(1)
                    .toInstant());

    public static void main(String[] args) throws Exception {
        boolean before = args[0].equals("before");
        boolean policyOn = args[1].equals("policyOn");
        boolean isValid = args[2].equals("valid");

        X509TrustManager[] tms = new X509TrustManager[]{
                Distrust.getTMF("PKIX", null),
                Distrust.getTMF("SunX509", null)
        };

        if (!policyOn) {
            // disable policy (default is on)
            Distrust.disableDistrustPolicy();
        }

        Date notBefore = before ? BEFORE_NOVEMBER_1_2024 : NOVEMBER_1_2024;
        Distrust.testCertificateChain(certPath, notBefore, isValid, tms, rootsToTest);
    }
}
