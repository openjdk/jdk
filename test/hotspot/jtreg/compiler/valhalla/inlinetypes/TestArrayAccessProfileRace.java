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

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Method;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @key randomness
 * @summary Test racy array access profiling.
 * @bug 8381373
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:CompileCommand=compileonly,${test.main.class}::profileArray
 *                   ${test.main.class}
 */
public class TestArrayAccessProfileRace {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int C1_FULL_PROFILE = 3;
    private static final int C2 = 4;
    private static volatile boolean startProfiling;

    @LooselyConsistentValue
    static value class ArrayAccessValue {
        int x;

        ArrayAccessValue(int x) {
            this.x = x;
        }
    }

    // Trigger array load/store profiling
    static int profileArray(Object[] array, Object val) {
        int sum = 0;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        sum += ((ArrayAccessValue)array[0]).x;
        array[0] = val;
        return sum;
    }

    public static void main(String[] args) throws Exception {
        // Create an array with random properties
        Object[] array;
        Object val = new ArrayAccessValue(42);
        int arrayKind = Utils.getRandomInstance().nextInt(5);
        switch (arrayKind) {
            case 0:
                array = new ArrayAccessValue[1];
                array[0] = val;
                break;
            case 1:
                array = ValueClass.newNullRestrictedNonAtomicArray(ArrayAccessValue.class, 1, val);
                break;
            case 2:
                array = ValueClass.newNullRestrictedAtomicArray(ArrayAccessValue.class, 1, val);
                break;
            case 3:
                array = ValueClass.newNullableAtomicArray(ArrayAccessValue.class, 1);
                array[0] = val;
                break;
            case 4:
                array = ValueClass.newReferenceArray(ArrayAccessValue.class, 1);
                array[0] = val;
                break;
            default:
                throw new RuntimeException("Unexpected arrayKind: " + arrayKind);
        }
        Method method = TestArrayAccessProfileRace.class.getDeclaredMethod("profileArray", Object[].class, Object.class);

        for (int i = 0; i < 100; i++) {
            // Reset profile information
            startProfiling = false;
            WB.deoptimizeMethod(method);
            WB.clearMethodState(method);

            // C1 compile the profiling method and mark the profile
            // as mature so that C2 will consume it immediately
            WB.enqueueMethodForCompilation(method, C1_FULL_PROFILE);
            WB.markMethodProfiled(method);

            // Profile asynchronously to the C2 compilation
            Thread profiler = new Thread(() -> {
                while (!startProfiling) {
                    Thread.onSpinWait();
                }
                profileArray(array, val);
            });
            profiler.start();

            // Start profiling and C2 compilation in the hope
            // that this will trigger the race.
            startProfiling = true;
            WB.enqueueMethodForCompilation(method, C2);
            profiler.join();
        }
    }
}

