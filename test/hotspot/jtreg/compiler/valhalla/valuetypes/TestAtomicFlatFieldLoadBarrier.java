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

package compiler.valhalla.valuetypes;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/*
 * @test id=plain
 * @bug 8397208
 * @summary Test GC barriers when buffering at atomic flat field loads.
 * @enablePreview
 * @library /test/lib /compiler/whitebox /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   ${test.main.class}
 */

/*
 * @test id=serial
 * @bug 8397208
 * @summary Test GC barriers when buffering at atomic flat field loads.
 * @enablePreview
 * @library /test/lib /compiler/whitebox /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @requires vm.gc.Serial
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -Xmn4m -XX:+UseSerialGC
 *                   -XX:CompileCommand=compileonly,${test.main.class}::load
 *                   ${test.main.class}
 */
public class TestAtomicFlatFieldLoadBarrier {
    static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    static value class ValueWithOop {
        int[] array;

        ValueWithOop(int[] array) {
            this.array = array;
        }
    }

    static Object sink;
    ValueWithOop field;

    TestAtomicFlatFieldLoadBarrier(int value) {
        field = new ValueWithOop(new int[] {value});
    }

    Object load() {
        // Loading flat 'field' requires buffering which needs a GC barrier to store 'array'
        return field;
    }

    public static void main(String[] args) {
        TestAtomicFlatFieldLoadBarrier t = new TestAtomicFlatFieldLoadBarrier(42);
        int[] expectedArray = t.field.array;
        // Make sure that 'load' is compiled
        for (int i = 0; i < 10_000; i++) {
            t.load();
        }

        // We want to create a buffer in the old generation with an 'array'
        // field that points to an array in the young generation.

        // Fill up most of young and old and then trigger a full GC to
        // convince the GC to allocate new buffers in old.
        int youngSize = (int)(long)WHITE_BOX.getSizeTVMFlag("NewSize");
        sink = new Object[] {new byte[youngSize / 2], new byte[youngSize]};
        WHITE_BOX.fullGC();

        // Each load creates a new buffer. The first ones might still end up in the young
        // generation until it's occupied and they end up in old. A young collection then
        // moves 'expectedArray' into old but without a proper GC post-barrier, the GC
        // won't update the reference from 'value.array' and a stale reference remains.
        for (int i = 0; i < 100_000; i++) {
            ValueWithOop value = (ValueWithOop)t.load();
            Asserts.assertSame(expectedArray, value.array, "Stale reference?");
        }
    }
}

