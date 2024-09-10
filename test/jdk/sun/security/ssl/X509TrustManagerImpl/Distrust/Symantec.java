/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.security.*;
import java.time.*;
import java.util.*;


/**
 * @test
 * @bug 8207258 8216280
 * @summary Check that TLS Server certificates chaining back to distrusted
 *          Symantec roots are invalid
 * @library /test/lib
 * @modules java.base/sun.security.validator
 * @run main/othervm Symantec after policyOn invalid
 * @run main/othervm Symantec after policyOff valid
 * @run main/othervm Symantec before policyOn valid
 * @run main/othervm Symantec before policyOff valid
 */

public class Symantec {

    private static final String certPath = "certs" + File.separator +  "Symantec";

    // Each of the roots have a test certificate chain stored in a file
    // named "<root>-chain.pem".
    private static final String[] rootsToTest = new String[] {
        "geotrustprimarycag2", "geotrustprimarycag3",
        "geotrustuniversalca",
            "thawteprimaryrootca",
            "thawteprimaryrootcag2",
        "thawteprimaryrootcag3", "verisignclass3g3ca", "verisignclass3g4ca",
        "verisignclass3g5ca", "verisignuniversalrootca"
    };

    // Each of the subCAs with a delayed distrust date have a test certificate
    // chain stored in a file named "<subCA>-chain.pem".
    private static String[] subCAsToTest = new String[]{"appleistca8g1"};

    private static String[] CAWithParamsToTest = new String[]{"verisignuniversalrootca"};

    // A date that is after the restrictions take affect
    private static final Date APRIL_17_2019 =
        Date.from(LocalDate.of(2019, 4, 17)
                           .atStartOfDay(ZoneOffset.UTC)
                           .toInstant());

    // A date that is a second before the restrictions take affect
    private static final Date BEFORE_APRIL_17_2019 =
        Date.from(LocalDate.of(2019, 4, 17)
                           .atStartOfDay(ZoneOffset.UTC)
                           .minusSeconds(1)
                           .toInstant());

    // A date that is after the subCA restrictions take affect
    private static final Date JANUARY_1_2020 =
        Date.from(LocalDate.of(2020, 1, 1)
                           .atStartOfDay(ZoneOffset.UTC)
                           .toInstant());

    // A date that is a second before the subCA restrictions take affect
    private static final Date BEFORE_JANUARY_1_2020 =
        Date.from(LocalDate.of(2020, 1, 1)
                           .atStartOfDay(ZoneOffset.UTC)
                           .minusSeconds(1)
                           .toInstant());

    public static void main(String[] args) throws Exception {
        boolean before = args[0].equals("before");
        boolean policyOn = args[1].equals("policyOn");
        boolean isValid = args[2].equals("valid");

        X509TrustManager[] tms = new X509TrustManager[] {
                Distrust.getTMF("PKIX", null),
                Distrust.getTMF("SunX509", null)
        };

        if (!policyOn) {
            // disable policy (default is on)
            Security.setProperty("jdk.security.caDistrustPolicies", "");
        }

        Date notBefore = before ? BEFORE_APRIL_17_2019 : APRIL_17_2019;
        Distrust.testCertificateChain(certPath, rootsToTest, notBefore, isValid, tms);

        // test chain if params are passed to TrustManager
        System.err.println("Testing verisignuniversalrootca with params");
        X509TrustManager[] tmsWithParams = new X509TrustManager[] {
                Distrust.getTMF("PKIX", Distrust.getParams())
        };
        Distrust.testCertificateChain(certPath, CAWithParamsToTest, notBefore, isValid, tmsWithParams);

        Distrust.testCodeSigningChain(certPath, "verisignclass3g5ca-codesigning", new Date(1544197375493L));

        notBefore = before ? BEFORE_JANUARY_1_2020 : JANUARY_1_2020;
        Distrust.testCertificateChain(certPath, subCAsToTest, notBefore, isValid, tms);
    }
}
