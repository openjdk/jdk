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
 * @bug 8385469
 * @summary Test static call resolution of a value class method with inline arguments while the class is being initialized
 * @enablePreview
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestValueInvokeStaticDuringClinit::test
 *                   compiler.valhalla.inlinetypes.TestValueInvokeStaticDuringClinit
 */

package compiler.valhalla.inlinetypes;

public class TestValueInvokeStaticDuringClinit {
    static value class MyValueClass {
        int x;

        static {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
            }
        }

        MyValueClass(int x) {
            this.x = x;
        }

        static void verify(MyValueClass val, int expected) {
            if (val != null && val.x != expected) {
                throw new RuntimeException("bad value");
            }
        }
    }

    public static void test() {
        MyValueClass.verify(null, 0);
        return;
    }

    public static void main(String[] args) throws Exception {
        Thread initThread = Thread.ofPlatform().start(() -> MyValueClass.verify(null, 0));

        Thread.sleep(1000); // wait for initThread to reach MyValueClass.<clinit>
        test();
        initThread.join();
    }
}
