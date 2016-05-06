/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8051408
 * @summary Make sure DrbgParameters coded as specified
 * @library /test/lib/share/classes
 */

import jdk.test.lib.Asserts;

import java.security.DrbgParameters;
import java.util.Arrays;

import static java.security.DrbgParameters.Capability.*;

public class DrbgParametersSpec {

    public static void main(String args[]) throws Exception {

        byte[] p, np1, np2;

        // Capability
        Asserts.assertTrue(PR_AND_RESEED.supportsPredictionResistance());
        Asserts.assertTrue(PR_AND_RESEED.supportsReseeding());
        Asserts.assertFalse(RESEED_ONLY.supportsPredictionResistance());
        Asserts.assertTrue(RESEED_ONLY.supportsReseeding());
        Asserts.assertFalse(NONE.supportsPredictionResistance());
        Asserts.assertFalse(NONE.supportsReseeding());

        // Instantiation
        p = "Instantiation".getBytes();
        DrbgParameters.Instantiation ins = DrbgParameters
                .instantiation(192, RESEED_ONLY, p);
        Asserts.assertTrue(ins.getStrength() == 192);
        Asserts.assertTrue(ins.getCapability() == RESEED_ONLY);
        np1 = ins.getPersonalizationString();
        np2 = ins.getPersonalizationString();
        // Getter outputs have same content but not the same object
        Asserts.assertTrue(Arrays.equals(np1, p));
        Asserts.assertTrue(Arrays.equals(np2, p));
        Asserts.assertNE(np1, np2);
        // Changes to original input has no affect on object
        p[0] = 'X';
        np2 = ins.getPersonalizationString();
        Asserts.assertTrue(Arrays.equals(np1, np2));

        ins = DrbgParameters.instantiation(-1, NONE, null);
        Asserts.assertNull(ins.getPersonalizationString());

        // NextBytes
        p = "NextBytes".getBytes();
        DrbgParameters.NextBytes nb = DrbgParameters
                .nextBytes(192, true, p);
        Asserts.assertTrue(nb.getStrength() == 192);
        Asserts.assertTrue(nb.getPredictionResistance());
        np1 = nb.getAdditionalInput();
        np2 = nb.getAdditionalInput();
        // Getter outputs have same content but not the same object
        Asserts.assertTrue(Arrays.equals(np1, p));
        Asserts.assertTrue(Arrays.equals(np2, p));
        Asserts.assertNE(np1, np2);
        // Changes to original input has no affect on object
        p[0] = 'X';
        np2 = nb.getAdditionalInput();
        Asserts.assertTrue(Arrays.equals(np1, np2));

        // Reseed
        p = "Reseed".getBytes();
        DrbgParameters.Reseed rs = DrbgParameters
                .reseed(true, p);
        Asserts.assertTrue(rs.getPredictionResistance());
        np1 = rs.getAdditionalInput();
        np2 = rs.getAdditionalInput();
        // Getter outputs have same content but not the same object
        Asserts.assertTrue(Arrays.equals(np1, p));
        Asserts.assertTrue(Arrays.equals(np2, p));
        Asserts.assertNE(np1, np2);
        // Changes to original input has no affect on object
        p[0] = 'X';
        np2 = rs.getAdditionalInput();
        Asserts.assertTrue(Arrays.equals(np1, np2));
    }
}
