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
 *
 */

/*
 * @test id=serial
 * @summary Ensures that value arrays can get arraycopied properly with Serial.
 *          This test will likely crash if that is not the case.
 * @bug 8370479
 * @enablePreview
 * @requires vm.flagless
 * @library /test/lib /
 * @modules java.base/jdk.internal.value
            java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run junit/othervm/timeout=480 -Xint -XX:+UseSerialGC -XX:+UseCompressedOops -Xlog:gc*=info
                                  -XX:+UseCompressedClassPointers
                                  -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
                                  runtime.valhalla.inlinetypes.FlatArrayCopyingTest
 */

/*
 * @test id=parallel
 * @summary Ensures that value arrays can get arraycopied properly with Parallel.
 *          This test will likely crash if that is not the case.
 * @bug 8370479
 * @enablePreview
 * @requires vm.flagless
 * @library /test/lib /
 * @modules java.base/jdk.internal.value
            java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run junit/othervm/timeout=480 -Xint -XX:+UseParallelGC -XX:+UseCompressedOops -Xlog:gc*=info
                                  -XX:ParallelGCThreads=1 -XX:-UseDynamicNumberOfGCThreads
                                  -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
                                  runtime.valhalla.inlinetypes.FlatArrayCopyingTest
 */

/*
 * @test id=g1
 * @summary Ensures that value arrays can get arraycopied properly with G1.
 *          This test will likely crash if that is not the case.
 * @bug 8370479
 * @enablePreview
 * @requires vm.flagless
 * @library /test/lib /
 * @modules java.base/jdk.internal.value
            java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run junit/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions
                                  -Xint -XX:+UseG1GC -XX:+UseCompressedOops -Xlog:gc*=info
                                  -XX:ParallelGCThreads=1 -XX:ConcGCThreads=1 -XX:-UseDynamicNumberOfGCThreads
                                  -XX:-G1UseConcRefinement -XX:+UseCompressedClassPointers
                                  -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
                                  runtime.valhalla.inlinetypes.FlatArrayCopyingTest
 */

package runtime.valhalla.inlinetypes;

import java.util.Arrays;
import jdk.internal.value.ValueClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jdk.test.whitebox.WhiteBox;

import static org.junit.jupiter.api.Assertions.*;

public final class FlatArrayCopyingTest {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static value record Element(Identity underlying) {}
    public static class Identity {}

    @Test
    public void testCopyingOften() {
        Object[] array = ValueClass.newNullableAtomicArray(Element.class, 16);
        for (int i = 0; i < 1_000_000; i++) {
            if (i == array.length) {
                array = Arrays.copyOf(array, array.length << 1);
            }
            // Do a very "random" full GC.
            if (i == 5123123) {
                WB.fullGC();
            }
            array[i] = new Element(new Identity());
        }
        // Smallest power of 2 that fits 1 million elements.
        assertEquals(1_048_576, array.length);
    }

}
