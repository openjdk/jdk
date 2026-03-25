/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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

package compiler.valhalla.inlinetypes;

/**
 * @test TestBufferTearingC1
 * @key randomness
 * @summary Tests for C1 missing barriers when buffering value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:+UseFieldFlattening -XX:+UseArrayFlattening
 *                   compiler.valhalla.inlinetypes.TestBufferTearingC1
 * @run main/othervm -XX:+UseFieldFlattening -XX:+UseArrayFlattening
 *                   -XX:TieredStopAtLevel=1
 *                   compiler.valhalla.inlinetypes.TestBufferTearingC1
 */

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

@LooselyConsistentValue
value class PointBufferTearingC1 {
    public int x, y;

    public PointBufferTearingC1(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static final PointBufferTearingC1 DEFAULT = new PointBufferTearingC1(0, 0);
}

@LooselyConsistentValue
value class Rect {
    public PointBufferTearingC1 a, b;

    public Rect(PointBufferTearingC1 a, PointBufferTearingC1 b) {
        this.a = a;
        this.b = b;
    }

    public static final Rect DEFAULT = new Rect(PointBufferTearingC1.DEFAULT, PointBufferTearingC1.DEFAULT);
}

public class TestBufferTearingC1 {

    public static PointBufferTearingC1[] points = (PointBufferTearingC1[])ValueClass.newNullRestrictedNonAtomicArray(PointBufferTearingC1.class, 1, PointBufferTearingC1.DEFAULT);
    public static Rect rect = new Rect(new PointBufferTearingC1(1, 1), new PointBufferTearingC1(2, 2));
    public static Rect[] rects = (Rect[])ValueClass.newNullRestrictedNonAtomicArray(Rect.class, 1, Rect.DEFAULT);

    static {
        points[0] = new PointBufferTearingC1(1, 1);
        rects[0] = rect;
    }

    public static Object ref1 = points[0];
    public static Object ref2 = rect.a;
    public static Object ref3 = rects[0].a;

    static volatile boolean running = true;

    public static void writeRefs(int iter) {
        ref1 = points[0];    // Indexed load of flattened array
        ref2 = rect.a;       // Load from flattened field
        ref3 = rects[0].a;   // Indexed load (delayed) followed by flattened field access

        points[0] = new PointBufferTearingC1(iter, iter);
        rect = new Rect(new PointBufferTearingC1(iter, iter), new PointBufferTearingC1(iter + 1, iter + 1));
        rects[0] = rect;
    }

    private static void checkMissingBarrier() {
        while (running) {
            // Each refN holds a "buffered" reference created when reading a
            // flattened field or array element.  It should not be possible to
            // read through this reference and see the intermediate
            // zero-initialised state of the object (i.e. there should be a
            // store-store barrier after copying the flattened field contents
            // before the store that publishes it).

            if (((PointBufferTearingC1)ref1).x == 0 || ((PointBufferTearingC1)ref1).y == 0) {
                throw new IllegalStateException();
            }

            if (((PointBufferTearingC1)ref2).x == 0 || ((PointBufferTearingC1)ref2).y == 0) {
                throw new IllegalStateException();
            }

            if (((PointBufferTearingC1)ref3).x == 0 || ((PointBufferTearingC1)ref3).y == 0) {
                throw new IllegalStateException();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(TestBufferTearingC1::checkMissingBarrier);
            threads[i].start();
        }

        for (int i = 1; i < 1_000_000; i++) {
            writeRefs(i);
        }

        running = false;

        for (int i = 0; i < 10; i++) {
            threads[i].join();
        }
    }
}
