/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8337664 8341059 8361212
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

    private static final String CERT_PATH = "chains" + File.separator + "entrust";

    // Each of the roots have a test certificate chain stored in a file
    // named "<root>-chain.pem".
    private static final String[] ROOTS_TO_TEST = new String[]{
            "entrustevca", "entrustrootcaec1", "entrustrootcag2",
            "entrustrootcag4", "entrust2048ca"};

    // Date when the restrictions take effect
    private static final ZonedDateTime DISTRUST_DATE =
            LocalDate.of(2024, 11, 12).atStartOfDay(ZoneOffset.UTC);

    public static void main(String[] args) throws Exception {
        Distrust distrust = new Distrust(args);

        X509TrustManager[] tms = new X509TrustManager[]{
                distrust.getTMF("PKIX", null),
                distrust.getTMF("SunX509", null)
        };

        Date notBefore = distrust.getNotBefore(DISTRUST_DATE);
        distrust.testCertificateChain(CERT_PATH, notBefore, tms, ROOTS_TO_TEST);
    }
}
