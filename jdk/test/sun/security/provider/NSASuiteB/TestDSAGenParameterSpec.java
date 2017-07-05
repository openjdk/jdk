/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.DSAGenParameterSpec;
import java.security.spec.DSAParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.List;

/*
 * @test
 * @bug 8075286
 * @key intermittent
 * @summary Verify that DSAGenParameterSpec can and can only be used to generate
 *          DSA within some certain range of key sizes as described in the class
 *          specification (L, N) as (1024, 160), (2048, 224), (2048, 256) and
 *          (3072, 256) should be OK for DSAGenParameterSpec. But the real
 *          implementation SUN doesn't support (3072, 256).
 * @run main TestDSAGenParameterSpec
 */
public class TestDSAGenParameterSpec {

    private static final String ALGORITHM_NAME = "DSA";
    private static final String PROVIDER_NAME = "SUN";

    private static final List<DataTuple> DATA = Arrays.asList(
            new DataTuple(1024, 160, true, true),
            new DataTuple(2048, 224, true, true),
            new DataTuple(2048, 256, true, true),
            new DataTuple(3072, 256, true, false),
            new DataTuple(1024, 224),
            new DataTuple(2048, 160),
            new DataTuple(4096, 256),
            new DataTuple(512, 160),
            new DataTuple(3072, 224));

    private static void testDSAGenParameterSpec(DataTuple dataTuple)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidParameterSpecException, InvalidAlgorithmParameterException {
        System.out.printf("Test case: primePLen=%d, " + "subprimeQLen=%d%n",
                dataTuple.primePLen, dataTuple.subprimeQLen);

        AlgorithmParameterGenerator apg =
                AlgorithmParameterGenerator.getInstance(ALGORITHM_NAME,
                        PROVIDER_NAME);

        DSAGenParameterSpec genParamSpec = createGenParameterSpec(dataTuple);
        // genParamSpec will be null if IllegalAE is thrown when expected.
        if (genParamSpec == null) {
            return;
        }

        try {
            apg.init(genParamSpec, null);
            AlgorithmParameters param = apg.generateParameters();

            checkParam(param, genParamSpec);
            System.out.println("Test case passed");
        } catch (InvalidParameterException ipe) {
            // The DSAGenParameterSpec API support this, but the real
            // implementation in SUN doesn't
            if (!dataTuple.isSunProviderSupported) {
                System.out.println("Test case passed: expected "
                        + "InvalidParameterException is caught");
            } else {
                throw new RuntimeException("Test case failed.", ipe);
            }
        }
    }

    private static void checkParam(AlgorithmParameters param,
            DSAGenParameterSpec genParam) throws InvalidParameterSpecException,
                    NoSuchAlgorithmException, NoSuchProviderException,
                    InvalidAlgorithmParameterException {
        String algorithm = param.getAlgorithm();
        if (!algorithm.equalsIgnoreCase(ALGORITHM_NAME)) {
            throw new RuntimeException(
                    "Unexpected type of parameters: " + algorithm);
        }

        DSAParameterSpec spec = param.getParameterSpec(DSAParameterSpec.class);
        int valueL = spec.getP().bitLength();
        int strengthP = genParam.getPrimePLength();
        if (strengthP != valueL) {
            System.out.printf("P: Expected %d but actual %d%n", strengthP,
                    valueL);
            throw new RuntimeException("Wrong P strength");
        }

        int valueN = spec.getQ().bitLength();
        int strengthQ = genParam.getSubprimeQLength();
        if (strengthQ != valueN) {
            System.out.printf("Q: Expected %d but actual %d%n", strengthQ,
                    valueN);
            throw new RuntimeException("Wrong Q strength");
        }

        if (genParam.getSubprimeQLength() != genParam.getSeedLength()) {
            System.out.println("Defaut seed length should be the same as Q.");
            throw new RuntimeException("Wrong seed length");
        }

        // use the parameters to generate real DSA keys
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM_NAME,
                PROVIDER_NAME);
        keyGen.initialize(spec);
        keyGen.generateKeyPair();
    }

    private static DSAGenParameterSpec createGenParameterSpec(
            DataTuple dataTuple) {
        DSAGenParameterSpec genParamSpec = null;
        try {
            genParamSpec = new DSAGenParameterSpec(dataTuple.primePLen,
                    dataTuple.subprimeQLen);
            if (!dataTuple.isDSASpecSupported) {
                throw new RuntimeException(
                        "Test case failed: the key length must not supported");
            }
        } catch (IllegalArgumentException e) {
            if (!dataTuple.isDSASpecSupported) {
                System.out.println("Test case passed: expected "
                        + "IllegalArgumentException is caught");
            } else {
                throw new RuntimeException("Test case failed: unexpected "
                        + "IllegalArgumentException is thrown", e);
            }
        }

        return genParamSpec;
    }

    public static void main(String[] args) throws Exception {
        for (DataTuple dataTuple : DATA) {
            testDSAGenParameterSpec(dataTuple);
        }
        System.out.println("All tests passed");
    }

    private static class DataTuple {

        private int primePLen;
        private int subprimeQLen;
        private boolean isDSASpecSupported;
        private boolean isSunProviderSupported;

        private DataTuple(int primePLen, int subprimeQLen,
                boolean isDSASpecSupported, boolean isSunProviderSupported) {
            this.primePLen = primePLen;
            this.subprimeQLen = subprimeQLen;
            this.isDSASpecSupported = isDSASpecSupported;
            this.isSunProviderSupported = isSunProviderSupported;
        }

        private DataTuple(int primePLen, int subprimeQLen) {
            this(primePLen, subprimeQLen, false, false);
        }
    }
}
