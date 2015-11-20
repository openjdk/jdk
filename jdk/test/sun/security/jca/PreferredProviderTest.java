/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8076359 8133151
 * @summary Test the value for new jdk.security.provider.preferred security property
 * @requires os.name == "SunOS"
 */

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public class PreferredProviderTest {

    private static final List<DataTuple> SPARC_DATA = Arrays.asList(
            new DataTuple("SHA-256", "SUN"), new DataTuple("SHA-384", "SUN"),
            new DataTuple("SHA-512", "SUN"));
    private static final List<DataTuple> X86_DATA = Arrays
            .asList(new DataTuple("RSA", "SunRsaSign"));

    public void RunTest(String type)
            throws NoSuchAlgorithmException, NoSuchPaddingException {
        String preferredProvider = Security
                .getProperty("jdk.security.provider.preferred");
        String actualProvider = null;
        if (type.equals("sparcv9")) {
            if (!preferredProvider.equals(
                    "AES:SunJCE, SHA-256:SUN, SHA-384:SUN, SHA-512:SUN")) {
                throw new RuntimeException(
                        "Test Failed: wrong jdk.security.provider.preferred "
                                + "value on solaris-sparcv9");
            }
            for (DataTuple dataTuple : SPARC_DATA) {
                MessageDigest md = MessageDigest
                        .getInstance(dataTuple.algorithm);
                actualProvider = md.getProvider().getName();
                if (!actualProvider.equals(dataTuple.provider)) {
                    throw new RuntimeException(String.format(
                            "Test Failed:Got wrong "
                                    + "provider from Solaris-sparcv9 platform,"
                                    + "Expected Provider: %s, Returned Provider: %s",
                            dataTuple.provider, actualProvider));
                }
            }
        } else if (type.equals("amd64")) {
            if (!preferredProvider.equals("AES:SunJCE, RSA:SunRsaSign")) {
                throw new RuntimeException(
                        "Test Failed: wrong jdk.security.provider.preferred "
                                + "value on solaris-x86");
            }
            for (DataTuple dataTuple : X86_DATA) {
                KeyFactory keyFactory = KeyFactory
                        .getInstance(dataTuple.algorithm);
                actualProvider = keyFactory.getProvider().getName();
                if (!actualProvider.equals(dataTuple.provider)) {
                    throw new RuntimeException(String.format(
                            "Test Failed:Got wrong "
                                    + "provider from Solaris-x86 platform,"
                                    + "Expected Provider: %s, Returned Provider: %s",
                            dataTuple.provider, actualProvider));
                }
            }
        } else {
            throw new RuntimeException("Test Failed: wrong platform value");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        actualProvider = cipher.getProvider().getName();
        if (!actualProvider.equals("SunJCE")) {
            throw new RuntimeException(String.format(
                    "Test Failed:Got wrong provider from Solaris-%s platform, "
                            + "Expected Provider: SunJCE, Returned Provider: %s",
                    type, actualProvider));
        }

        MessageDigest md = MessageDigest.getInstance("MD5");
        actualProvider = md.getProvider().getName();
        if (!actualProvider.equals("OracleUcrypto")) {
            throw new RuntimeException(String.format(
                    "Test Failed:Got wrong provider from Solaris-%s platform,"
                            + "Expected Provider: OracleUcrypto, Returned Provider: %s",
                    type, actualProvider));
        }
    }

    private static class DataTuple {
        private final String provider;
        private final String algorithm;

        private DataTuple(String algorithm, String provider) {
            this.algorithm = algorithm;
            this.provider = provider;
        }
    }

    public static void main(String[] args)
            throws NoSuchAlgorithmException, NoSuchPaddingException {

        String arch = System.getProperty("os.arch");
        PreferredProviderTest pp = new PreferredProviderTest();
        pp.RunTest(arch);
    }
}

