/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;

/**
 * @test
 * @bug 8246797
 * @summary Ensures OAEPParameters read correct encoding and
 * reject encoding with invalid ordering
 */

public class OAEPOrder {
    public static void main(String[] args) throws Exception {
        // Do not use default fields
        OAEPParameterSpec spec = new OAEPParameterSpec(
                "SHA-384", "MGF1", MGF1ParameterSpec.SHA384,
                new PSource.PSpecified(new byte[10]));
        AlgorithmParameters alg = AlgorithmParameters.getInstance("OAEP");
        alg.init(spec);
        byte[] encoded = alg.getEncoded();

        // Extract the fields inside encoding
        // [0] HashAlgorithm
        byte[] a0 = Arrays.copyOfRange(encoded, 2, encoded[3] + 4);
        // [1] MaskGenAlgorithm + [2] PSourceAlgorithm
        byte[] a12 = Arrays.copyOfRange(encoded, 2 + a0.length, encoded.length);

        // and rearrange [1] and [2] before [0]
        System.arraycopy(a12, 0, encoded, 2, a12.length);
        System.arraycopy(a0, 0, encoded, 2 + a12.length, a0.length);

        AlgorithmParameters alg2 = AlgorithmParameters.getInstance("OAEP");
        try {
            alg2.init(encoded);
            throw new RuntimeException("Should fail");
        } catch (IOException ioe) {
            // expected
        }
    }
}
