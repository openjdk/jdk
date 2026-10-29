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

package compiler.c1;

import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @bug 8395981
 * @summary Test C1's constant long shift in GC barrier.
 * @enablePreview
 * @requires vm.compiler1.enabled
 * @modules java.base/jdk.internal.vm.annotation
 * @library /test/lib /compiler/whitebox /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+VerifyAfterGC -Xbatch -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,${test.main.class}::store
 *                   ${test.main.class}
 */
public class TestLongShiftInBarrier {
    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static value record TwoOops(Object x, Object y) { }

    @NullRestricted
    TwoOops field;

    TestLongShiftInBarrier() {
        field = new TwoOops(null, null);
        super();
    }

    void store(TwoOops value) {
        // The post-barrier of the long-sized store extracts oops 'x' and 'y' by shifting
        field = value;
    }

    public static void main(String[] args) {
        TestLongShiftInBarrier holder = new TestLongShiftInBarrier();
        TwoOops value = new TwoOops(null, null);
        for (int i = 0; i < 20_000; i++) {
            holder.store(value);
        }
        // Move holder to old
        WB.fullGC();

        // Store a young 'value' into old holder to trigger the post-barrier
        value = new TwoOops(null, new Object());
        holder.store(value);

        // Trigger collection of young which will move the object referenced
        // by 'value.y' and should therefore update 'y' accordingly. If the
        // post-barrier does not work as expected, 'y' will not be updated
        // thus contain a stale reference that is dereferenced below.
        WB.youngGC();

        Asserts.assertEQ(holder.field, value);
    }
}
