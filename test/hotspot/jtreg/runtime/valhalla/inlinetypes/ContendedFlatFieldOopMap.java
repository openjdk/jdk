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
package runtime.valhalla.inlinetypes;

import jdk.internal.vm.annotation.Contended;
import java.lang.ref.WeakReference;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/*
 * @test ContendedFlatFieldOopMap
 * @library /test/lib
 * @requires vm.gc != "Z" & (vm.opt.RestrictContended == null | vm.opt.RestrictContended == "false")
 * @modules java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-RestrictContended
 *                   -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   runtime.valhalla.inlinetypes.ContendedFlatFieldOopMap
 */
public class ContendedFlatFieldOopMap {

    private static value class Payload {
        Object ref;

        Payload(Object ref) {
            this.ref = ref;
        }
    }

    private static class Holder {
        @Contended
        Payload payload;

        Holder(Object ref) {
            payload = new Payload(ref);
        }
    }

    private static Holder holder;

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        Object[] obj = new Object[1024 * 1024];
        WeakReference ref = new WeakReference(obj);
        holder = new Holder(obj);
        obj = null;

        Asserts.assertNotEquals(ref.get(), null);

        WB.fullGC();

        // obj must have stayed alive after we've run a GC. If it stayed alive,
        // it must mean that obj was reachable through the Holder class' OopMap.
        Asserts.assertNotEquals(ref.get(), null);
    }
}
