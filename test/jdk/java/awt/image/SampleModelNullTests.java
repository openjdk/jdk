/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8390320
 * @summary Verify NPE for SampleModel methods called with zero dimensions and null args
 *
 * @run     main SampleModelNullTests
 */

import java.awt.image.BandedSampleModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.SampleModel;
import java.awt.image.PixelInterleavedSampleModel;

public class SampleModelNullTests {

    public static void main(String[] args) {

        SampleModel model = new BandedSampleModel(DataBuffer.TYPE_BYTE, 2, 2, 1);
        test(model);
        model = new ComponentSampleModel(DataBuffer.TYPE_BYTE, 2, 2, 1, 2, new int[] { 0 });
        test(model);
        model = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, 2, 2, 1, 2, new int[] { 0 });
        test(model);
        model = new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, 2, 2, 8);
        test(model);
    }

    private static void expectNPE(String description, boolean npe, Runnable operation) {
        try {
            operation.run();
            if (npe) {
                throw new RuntimeException("No NPE for " + description);
            }
        } catch (NullPointerException expected) {
            if (!npe) {
                throw new RuntimeException("Unexpected NPE for " + description);
            }
        }
    }

    static void test(SampleModel model) {
        DataBuffer data = model.createDataBuffer();
        byte[] binput = new byte[1];
        byte[] null_binput = null;
        int[] iinput = new int[1];
        int[] null_iinput = null;
        float[] finput = new float[1];
        float[] null_finput = null;
        double[] dinput = new double[1];
        double[] null_dinput = null;

        int[][] wh_array = { { 0, 0}, { 0, 1}, { 1, 0 }, {1, 1} };
        for (int[] wh : wh_array) {
            int w = wh[0];
            int h = wh[1];

            String test = "Byte data elements for " + model;
            expectNPE(test, true, () -> model.setDataElements(0, 0, w, h, null_binput, null));
            expectNPE(test, true, () -> model.setDataElements(0, 0, w, h, null_binput, data));
            expectNPE(test, true, () -> model.setDataElements(0, 0, w, h, binput, null));
            expectNPE(test, false, () -> model.setDataElements(0, 0, w, h, binput, data));
            expectNPE(test, true, () -> model.getDataElements(0, 0, w, h, binput, null));
            expectNPE(test, false, () -> model.getDataElements(0, 0, w, h, null_binput, data));

            test = "Int samples for " + model;
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, null_iinput, null));
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, null_iinput, data));
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, iinput, null));
            expectNPE(test, false, () -> model.setSamples(0, 0, w, h, 0, iinput, data));
            expectNPE(test, true, () -> model.getSamples(0, 0, w, h, 0, iinput, null));
            expectNPE(test, false, () -> model.getSamples(0, 0, w, h, 0, null_iinput, data));

            test = "Float samples for " + model;
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, null_finput, null));
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, null_finput, data));
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, finput, null));
            expectNPE(test, false, () -> model.setSamples(0, 0, w, h, 0, finput, data));
            expectNPE(test, true, () -> model.getSamples(0, 0, w, h, 0, finput, null));
            expectNPE(test, false, () -> model.getSamples(0, 0, w, h, 0, null_finput, data));

            test = "Double samples for " + model;
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, null_dinput, null));
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, null_dinput, data));
            expectNPE(test, true, () -> model.setSamples(0, 0, w, h, 0, dinput, null));
            expectNPE(test, false, () -> model.setSamples(0, 0, w, h, 0, dinput, data));
            expectNPE(test, true, () -> model.getSamples(0, 0, w, h, 0, dinput, null));
            expectNPE(test, false, () -> model.getSamples(0, 0, w, h, 0, null_dinput, data));

            test = "Int pixels for " + model;
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, null_iinput, null));
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, null_iinput, data));
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, iinput, null));
            expectNPE(test, false, () -> model.setPixels(0, 0, w, h, iinput, data));
            expectNPE(test, true, () -> model.getPixels(0, 0, w, h, iinput, null));
            expectNPE(test, false, () -> model.getPixels(0, 0, w, h, null_iinput, data));

            test = "Float pixels for " + model;
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, null_finput, null));
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, null_finput, data));
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, finput, null));
            expectNPE(test, false, () -> model.setPixels(0, 0, w, h, finput, data));
            expectNPE(test, true, () -> model.getPixels(0, 0, w, h, finput, null));
            expectNPE(test, false, () -> model.getPixels(0, 0, w, h, null_finput, data));

            test = "Double pixels for " + model;
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, null_dinput, null));
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, null_dinput, data));
            expectNPE(test, true, () -> model.setPixels(0, 0, w, h, dinput, null));
            expectNPE(test, false, () -> model.setPixels(0, 0, w, h, dinput, data));
            expectNPE(test, true, () -> model.getPixels(0, 0, w, h, dinput, null));
            expectNPE(test, false, () -> model.getPixels(0, 0, w, h, null_dinput, data));

        }
    }
}
