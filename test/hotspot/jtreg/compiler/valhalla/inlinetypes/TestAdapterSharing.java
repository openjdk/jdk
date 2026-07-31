/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.NullRestricted;

/**
 * @test
 * @bug 8348547
 * @summary Test that adapters are not shared between calls with different shapes
 * @requires vm.debug == true
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:+VerifyAdapterSharing compiler.valhalla.inlinetypes.TestAdapterSharing
 */
public class TestAdapterSharing {
    static abstract value class MyAbstract {
        int i;

        MyAbstract(int i) {
            this.i = i;
        }
    }
    static value class MyValue1 extends MyAbstract {
        MyValue1(int i) {
            super(i);
        }

        void test() {}
    }

    static value class Empty {
    }

    public static value class MyValue2 {
        @NullRestricted
        Empty e;
        int i;

        MyValue2(int i) {
            this.e = new Empty();
            this.i = i;
        }

        void test() {}
    }

    public static void main(String[] args) {
        MyValue1 v1 = new MyValue1(0);
        v1.test();
        MyValue2 v2 = new MyValue2(0);
        v2.test();
    }
}
