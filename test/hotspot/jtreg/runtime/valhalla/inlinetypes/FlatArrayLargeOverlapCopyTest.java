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
 *
 */

/*
 * @test
 * @summary Ensures large overlapping flat array copies do not overflow payload iterator offsets.
 * @enablePreview
 * @requires os.maxMemory >= 7G
 * @modules java.base/jdk.internal.value
 * @run main/othervm/timeout=240 -Xint -Xmx6G
 *                               runtime.valhalla.inlinetypes.FlatArrayLargeOverlapCopyTest
 */

package runtime.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;

public class FlatArrayLargeOverlapCopyTest {
    // Value[] is flattened with 8-byte elements.
    // Copying 290M elements makes length * elementSize exceed Integer.MAX_VALUE.
    private static final int LENGTH = 290_000_000;

    public static value record Value(int value) {}

    public static void main(String[] args) {
        Value[] array = new Value[LENGTH + 1];
        if (!ValueClass.isFlatArray(array)) {
            throw new RuntimeException("Expected Value[] to be flat");
        }

        array[0] = new Value(1);
        array[LENGTH - 1] = new Value(2);
        array[LENGTH] = new Value(3);

        System.arraycopy(array, 0, array, 1, LENGTH);

        if (array[1].value() != 1) {
            throw new RuntimeException("array[1]=" + array[1] + ", expected '1'");
        }
        if (array[LENGTH].value() != 2) {
            throw new RuntimeException("array[LENGTH]=" + array[LENGTH] + ", expected '2'");
        }

        array[1] = new Value(4);
        array[LENGTH] = new Value(5);

        System.arraycopy(array, 1, array, 0, LENGTH);

        if (array[0].value() != 4) {
            throw new RuntimeException("array[0]=" + array[0] + ", expected '4'");
        }
        if (array[LENGTH - 1].value() != 5) {
            throw new RuntimeException("array[LENGTH - 1]=" + array[LENGTH - 1] + ", expected '5'");
        }
    }
}
