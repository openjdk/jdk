/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Signature;

public class PSSUtil {

    /**
     * ALGORITHM name, fixed as RSA for PKCS11
     */
    private static final String KEYALG = "RSA";
    private static final String SIGALG = "RSASSA-PSS";

    public static enum AlgoSupport {
        NO, MAYBE, YES
    };

    public static boolean isSignatureSupported(Provider p) {
        try {
            Signature.getInstance(SIGALG, p);
            return true;
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Skip testing " + SIGALG +
                " due to no support");
            return false;
        }
    }

    public static AlgoSupport isHashSupported(Provider p, String... hashAlgs) {

        AlgoSupport status = AlgoSupport.YES;
        for (String h : hashAlgs) {
            String sigAlg = (h.startsWith("SHA3-") ?
                    h : h.replace("-", "")) + "with" + SIGALG;
            try {
                Signature.getInstance(sigAlg, p);
                // Yes, proceed to check next hash algorithm
                continue;
            } catch (NoSuchAlgorithmException e) {
                // continue trying other checks
            }
            try {
                MessageDigest.getInstance(h, p);
                status = AlgoSupport.MAYBE;
            } catch (NoSuchAlgorithmException e) {
                // if not supported as a standalone digest algo, chance of it
                // being supported by PSS is very very low
                return AlgoSupport.NO;
            }
        }
        return status;
    }

    public static KeyPair generateKeys(Provider p, int size)
            throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEYALG, p);
        kpg.initialize(size);
        return kpg.generateKeyPair();
    }
}
