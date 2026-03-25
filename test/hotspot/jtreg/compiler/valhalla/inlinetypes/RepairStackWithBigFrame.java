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

package compiler.valhalla.inlinetypes;

import static compiler.valhalla.inlinetypes.InlineTypes.*;

/*
 * @test
 * @bug 8367156
 * @summary On Aarch64, when the frame is very big and we need to repair it after
 *          scalarization of the arguments, we cannot use ldp to get the stack
 *          increment and rfp at the same time, since it only has a 7 bit offset.
 *          We use two ldr with 9-bit offsets instead.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run main/othervm
 *      -Xcomp
 *      -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.RepairStackWithBigFrame::test
 *      compiler.valhalla.inlinetypes.RepairStackWithBigFrame
 * @run main compiler.valhalla.inlinetypes.RepairStackWithBigFrame
 */

public class RepairStackWithBigFrame {
    static final MyValue1 testValue1 = MyValue1.createWithFieldsInline(rI, rL);

    public static void main(String[] args) {
        new RepairStackWithBigFrame().test(testValue1);
    }

    long test(MyValue1 arg) {
        MyAbstract vt1 = MyValue1.createWithFieldsInline(rI, rL);
        MyAbstract vt2 = MyValue1.createWithFieldsDontInline(rI, rL);
        MyAbstract vt3 = MyValue1.createWithFieldsInline(rI, rL);
        MyAbstract vt4 = arg;
        return ((MyValue1)vt1).hash() + ((MyValue1)vt2).hash() +
               ((MyValue1)vt3).hash() + ((MyValue1)vt4).hash();
    }
}
