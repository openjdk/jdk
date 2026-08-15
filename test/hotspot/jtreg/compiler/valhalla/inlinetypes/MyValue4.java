/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.ForceInline;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

// Inline type definition with too many fields to return in registers
@LooselyConsistentValue
value class MyValue4 extends MyAbstract {
    @NullRestricted
    MyValue3 v1;
    @NullRestricted
    MyValue3 v2;

    @ForceInline
    public MyValue4(MyValue3 v1, MyValue3 v2) {
        this.v1 = v1;
        this.v2 = v2;
    }

    @ForceInline
    static MyValue4 setV1(MyValue4 v, MyValue3 v1) {
        return new MyValue4(v1, v.v2);
    }

    @ForceInline
    static MyValue4 setV2(MyValue4 v, MyValue3 v2) {
        return new MyValue4(v.v1, v2);
    }

    @ForceInline
    public static MyValue4 createDefault() {
        return new MyValue4(MyValue3.createDefault(), MyValue3.createDefault());
    }

    @ForceInline
    public static MyValue4 create() {
        MyValue4 v = createDefault();
        MyValue3 v1 = MyValue3.create();
        v = setV1(v, v1);
        MyValue3 v2 = MyValue3.create();
        v = setV2(v, v2);
        return v;
    }

    public void verify(MyValue4 other) {
        v1.verify(other.v1);
        v2.verify(other.v2);
    }

    @ForceInline
    public long hash() {
        return v1.hash() + v2.hash();
    }

    @Override
    public String toString() {
        return "MyValue4[v1=" + v1 + ", v2=" + v2 + "]";
    }
}
