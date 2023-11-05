/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5052433 8315042
 * @summary Verify that generateCRL and generateCRLs methods do not throw
 *          NullPointerException. They should throw CRLException instead.
 * @library /test/lib
 */
import java.security.NoSuchProviderException;
import java.security.cert.*;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import jdk.test.lib.Utils;

public class UnexpectedNPE {
    static CertificateFactory cf = null;

    public static void main(String[] av ) throws CertificateException,
            NoSuchProviderException {
        byte[] encoded_1 = { 0x00, 0x00, 0x00, 0x00 };
        byte[] encoded_2 = { 0x30, 0x01, 0x00, 0x00 };
        byte[] encoded_3 = { 0x30, 0x01, 0x00 };
        byte[] encoded_4 = Base64.getDecoder().decode(
                "MAsGCSqGSMP7TQEHAjI1Bgn///////8wCwUyAQ==");

        cf = CertificateFactory.getInstance("X.509", "SUN");

        run(encoded_1);
        run(encoded_2);
        run(encoded_3);
        run(encoded_4);
    }

    private static void run(byte[] buf) {
        Utils.runAndCheckException(
                () -> cf.generateCRL(new ByteArrayInputStream(buf)),
                CRLException.class);
        Utils.runAndCheckException(
                () -> cf.generateCRLs(new ByteArrayInputStream(buf)),
                CRLException.class);
    }
}
