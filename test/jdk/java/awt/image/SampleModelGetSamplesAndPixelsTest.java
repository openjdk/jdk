/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6185110
 * @summary Verify get/set/Pixels/Samples APIs for bad parameters.
 *
 * @run     main SampleModelGetSamplesAndPixelsTest
 */

import java.awt.image.BandedSampleModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.util.Vector;

public class SampleModelGetSamplesAndPixelsTest {

    static final int WIDTH = 100;
    static final int HEIGHT = 100;
    static final int DATATYPE = DataBuffer.TYPE_BYTE;
    static final int NUMBANDS = 4;
    static final int[] INTS = new int[WIDTH * HEIGHT + NUMBANDS];
    static final float[] FLOATS = new float[WIDTH * HEIGHT + NUMBANDS];
    static final double[] DOUBLES = new double[WIDTH * HEIGHT + NUMBANDS];
    static final int[][] COORDS = {
        { 1, 1, 1, 1, -1 }, // bad band
        { 1, 1, 1, 1, NUMBANDS }, // bad band
        { 1, 1, -1, 1, 0 }, // negative w
        { 1, 1, -1, -1, 0 }, // negative w and h
        { -4, 1, 1, 1, 0 }, // negative x
        { -4, -4, 1, 1, 0 }, // negative x and y
        { WIDTH+10, 0, 1, 1, 0 }, // x > width
        { 0, HEIGHT+10, 1, 1, 0 }, // y > height
        { WIDTH+10, HEIGHT+10, 1, 1, 0 }, // both x > width and y > height
    };

    public static void main(String[] args) {
        Vector<Class<? extends SampleModel>> classes = new Vector<Class<? extends SampleModel>>();

        classes.add(ComponentSampleModel.class);
        classes.add(MultiPixelPackedSampleModel.class);
        classes.add(SinglePixelPackedSampleModel.class);
        classes.add(BandedSampleModel.class);
        classes.add(PixelInterleavedSampleModel.class);

        for (Class<? extends SampleModel> c : classes) {
            doTest(c);
        }
    }

    static void noException(SampleModel sm) {
        System.err.println(sm);
        throw new RuntimeException("No expected exception");
    }

    private static void doTest(Class<? extends SampleModel> c) {
        System.out.println("Test for: " + c.getName());
        SampleModel sm = createSampleModel(c);
        doTestNull(sm);
        for (int i = 0; i < COORDS.length; i++) {
            int x = COORDS[i][0];
            int y = COORDS[i][1];
            int w = COORDS[i][2];
            int h = COORDS[i][3];
            int b = COORDS[i][4];
            doTest(sm, x, y, w, h, b);
        }
     }

    private static void doTestNull(SampleModel sm) {
        doTestNull(sm, INTS);
        doTestNull(sm, FLOATS);
        doTestNull(sm, DOUBLES);
    }

    private static void doTestNull(SampleModel sm, int[] INTS) {
        try {
            sm.getSamples(1, 1, 1, 1, 0, INTS, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.getSamples(1, 1, 1, 1, 0, INTS, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.getPixels(1, 1, 1, 1, INTS, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setPixels(1, 1, 1, 1, INTS, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void doTestNull(SampleModel sm, float[] FLOATS) {
        try {
            sm.getSamples(1, 1, 1, 1, 0, FLOATS, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.getSamples(1, 1, 1, 1, 0, FLOATS, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.getPixels(1, 1, 1, 1, FLOATS, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setPixels(1, 1, 1, 1, FLOATS, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void doTestNull(SampleModel sm, double[] DOUBLES) {
        try {
            sm.getSamples(1, 1, 1, 1, 0, DOUBLES, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.getSamples(1, 1, 1, 1, 0, DOUBLES, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.getPixels(1, 1, 1, 1, DOUBLES, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setPixels(1, 1, 1, 1, DOUBLES, null);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void doTest(SampleModel sm, int x, int y, int w, int h, int b) {
        doTest(sm, x, y, w, h, b, INTS);
        doTest(sm, x, y, w, h, b, FLOATS);
        doTest(sm, x, y, w, h, b, DOUBLES);
    }

    private static void doTest(SampleModel sm, int x, int y, int w, int h, int b, int[] INTS) {

        // Now test each API with a non-null buffer and the specified values.
        DataBuffer db = sm.createDataBuffer();

        try {
            sm.getSamples(x, y, w, h, b, INTS, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setSamples(x, y, w, h, b, INTS, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        if (b < 0 || b >= NUMBANDS) {
             return; // Values were to test illegal bands, skip the rest.
        }

        try {
            sm.getPixels(x, y, w, h, INTS, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setPixels(x, y, w, h, INTS, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void doTest(SampleModel sm, int x, int y, int w, int h, int b, float[] FLOATS) {

        // Now test each API with a non-null buffer and the specified values.
        DataBuffer db = sm.createDataBuffer();

        try {
            sm.getSamples(x, y, w, h, b, FLOATS, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setSamples(x, y, w, h, b, FLOATS, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        if (b < 0 || b >= NUMBANDS) {
             return; // Values were to test illegal bands, skip the rest.
        }

        try {
            sm.getPixels(x, y, w, h, FLOATS, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setPixels(x, y, w, h, FLOATS, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void doTest(SampleModel sm, int x, int y, int w, int h, int b, double[] DOUBLES) {

        // Now test each API with a non-null buffer and the specified values.
        DataBuffer db = sm.createDataBuffer();

        try {
            sm.getSamples(x, y, w, h, b, DOUBLES, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setSamples(x, y, w, h, b, DOUBLES, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        if (b < 0 || b >= NUMBANDS) {
             return; // Values were to test illegal bands, skip the rest.
        }

        try {
            sm.getPixels(x, y, w, h, DOUBLES, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setPixels(x, y, w, h, DOUBLES, db);
            noException(sm);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }

        try {
            sm.setDataElements(0, 0, null, db);
            noException(sm);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }
    }

    private static SampleModel createSampleModel(Class<? extends SampleModel> cls) {
        SampleModel res = null;

        if (cls == ComponentSampleModel.class) {
            res = new ComponentSampleModel(DATATYPE, WIDTH, HEIGHT, 4, WIDTH * 4, new int[] { 0, 1, 2, 3 } );
        } else if (cls == MultiPixelPackedSampleModel.class) {
            res = new MultiPixelPackedSampleModel(DATATYPE, WIDTH, HEIGHT, 4);
        } else if (cls == SinglePixelPackedSampleModel.class) {
            res = new SinglePixelPackedSampleModel(DATATYPE, WIDTH, HEIGHT,
                    new int[]{ 0xff000000, 0x00ff0000, 0x0000ff00, 0x000000ff });
        } else if (cls == BandedSampleModel.class) {
            res = new BandedSampleModel(DATATYPE, WIDTH, HEIGHT, NUMBANDS);
        } else if (cls == PixelInterleavedSampleModel.class) {
            res = new PixelInterleavedSampleModel(DATATYPE, WIDTH, HEIGHT, 4, WIDTH * 4, new int[] { 0, 1, 2, 3 });
        } else {
            throw new RuntimeException("Unknown class " + cls);
        }
        return res;
    }
}
