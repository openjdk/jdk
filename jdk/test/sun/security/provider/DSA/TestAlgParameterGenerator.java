/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7044060
 * @summary verify that DSA parameter generation works
 * @run main/othervm/timeout=300 TestAlgParameterGenerator
 */
import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;

public class TestAlgParameterGenerator {

    private static void checkParamStrength(AlgorithmParameters param,
                                           int strength) throws Exception {
        String algo = param.getAlgorithm();
        if (!algo.equalsIgnoreCase("DSA")) {
            throw new Exception("Unexpected type of parameters: " + algo);
        }
        DSAParameterSpec spec = param.getParameterSpec(DSAParameterSpec.class);
        int valueL = spec.getP().bitLength();
        if (strength != valueL) {
            System.out.println("Expected " + strength + " but actual " + valueL);
            throw new Exception("Wrong P strength");
        }
    }
    private static void checkParamStrength(AlgorithmParameters param,
                                           DSAGenParameterSpec genParam)
        throws Exception {
        String algo = param.getAlgorithm();
        if (!algo.equalsIgnoreCase("DSA")) {
            throw new Exception("Unexpected type of parameters: " + algo);
        }
        DSAParameterSpec spec = param.getParameterSpec(DSAParameterSpec.class);
        int valueL = spec.getP().bitLength();
        int strength = genParam.getPrimePLength();
        if (strength != valueL) {
            System.out.println("P: Expected " + strength + " but actual " + valueL);
            throw new Exception("Wrong P strength");
        }
        int valueN = spec.getQ().bitLength();
        strength = genParam.getSubprimeQLength();
        if (strength != valueN) {
            System.out.println("Q: Expected " + strength + " but actual " + valueN);
            throw new Exception("Wrong Q strength");
        }
    }

    public static void main(String[] args) throws Exception {
        AlgorithmParameterGenerator apg =
            AlgorithmParameterGenerator.getInstance("DSA", "SUN");

        long start, stop;
        // make sure no-init still works
        start = System.currentTimeMillis();
        AlgorithmParameters param = apg.generateParameters();
        stop = System.currentTimeMillis();
        System.out.println("Time: " + (stop - start) + " ms.");
        checkParamStrength(param, 1024);

        // make sure the old model works
        int[] strengths = { 512, 768, 1024 };
        for (int i = 0; i < strengths.length; i++) {
            int sizeP = strengths[i];
            System.out.println("Generating " + sizeP + "-bit DSA Parameters");
            start = System.currentTimeMillis();
            apg.init(sizeP);
            param = apg.generateParameters();
            stop = System.currentTimeMillis();
            System.out.println("Time: " + (stop - start) + " ms.");
            checkParamStrength(param, sizeP);
        }

        // now the newer model
        DSAGenParameterSpec spec1 = new DSAGenParameterSpec(1024, 160);
        DSAGenParameterSpec spec2 = new DSAGenParameterSpec(2048, 224);
        DSAGenParameterSpec spec3 = new DSAGenParameterSpec(2048, 256);
        //DSAGenParameterSpec spec4 = new DSAGenParameterSpec(3072, 256);
        DSAGenParameterSpec[] specSet = {
            spec1, spec2, spec3//, spec4
        };
        for (int i = 0; i < specSet.length; i++) {
            DSAGenParameterSpec genParam = specSet[i];
            System.out.println("Generating (" + genParam.getPrimePLength() +
                               ", " + genParam.getSubprimeQLength() +
                               ") DSA Parameters");
            start = System.currentTimeMillis();
            apg.init(genParam, null);
            param = apg.generateParameters();
            stop = System.currentTimeMillis();
            System.out.println("Time: " + (stop - start) + " ms.");
            checkParamStrength(param, genParam);
        }
    }
}
