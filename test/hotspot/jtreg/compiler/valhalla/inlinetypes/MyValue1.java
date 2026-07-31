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

import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.DontInline;
import compiler.lib.ir_framework.ForceCompileClassInitializer;
import compiler.lib.ir_framework.ForceInline;

import java.util.Arrays;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

@LooselyConsistentValue
@ForceCompileClassInitializer
public value class MyValue1 extends MyAbstract {
    static int s;
    static final long sf = InlineTypes.rL;
    int x;
    long y;
    short z;
    Integer o;
    int[] oa;
    @NullRestricted
    MyValue2 v1;
    @NullRestricted
    MyValue2 v2;
    @NullRestricted
    static final MyValue2 v3 = MyValue2.createWithFieldsInline(InlineTypes.rI, InlineTypes.rD);
    MyValue2 v4;
    @NullRestricted
    MyValue2 v5;
    int c;

    static final MyValue1 DEFAULT = createDefaultInline();

    @ForceInline
    public MyValue1(int x, long y, short z, Integer o, int[] oa, MyValue2 v1, MyValue2 v2, MyValue2 v4, MyValue2 v5, int c) {
        s = 0;
        this.x = x;
        this.y = y;
        this.z = z;
        this.o = o;
        this.oa = oa;
        this.v1 = v1;
        this.v2 = v2;
        this.v4 = v4;
        this.v5 = v5;
        this.c = c;
    }

    @DontInline
    static MyValue1 createDefaultDontInline() {
        return createDefaultInline();
    }

    @ForceInline
    static MyValue1 createDefaultInline() {
        return new MyValue1(0, 0, (short)0, null, null, MyValue2.createDefaultInline(), MyValue2.createDefaultInline(), null, MyValue2.createDefaultInline(), 0);
    }

    @DontInline
    static MyValue1 createWithFieldsDontInline(int x, long y) {
        return createWithFieldsInline(x, y);
    }

    @ForceInline
    static MyValue1 createWithFieldsInline(int x, long y) {
        MyValue1 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, y);
        v = setZ(v, (short)x);
        // Don't use Integer.valueOf here to avoid control flow added by Integer cache check
        v = setO(v, new Integer(x));
        int[] oa = {x};
        v = setOA(v, oa);
        v = setV1(v, MyValue2.createWithFieldsInline(x, y, InlineTypes.rD));
        v = setV2(v, MyValue2.createWithFieldsInline(x + 1, y + 1, InlineTypes.rD + 1));
        v = setV4(v, MyValue2.createWithFieldsInline(x + 2, y + 2, InlineTypes.rD + 2));
        v = setV5(v, MyValue2.createWithFieldsInline(x + 3, y + 3, InlineTypes.rD + 3));
        v = setC(v, (int)(x+y));
        return v;
    }

    // Hash only primitive and inline type fields to avoid NullPointerException
    @ForceInline
    public long hashPrimitive() {
        return s + sf + x + y + z + c + v1.hash() + v2.hash() + v3.hash() + v5.hash();
    }

    @ForceInline
    public long hash() {
        long res = hashPrimitive();
        try {
            res += o;
        } catch (NullPointerException npe) {}
        try {
            res += oa[0];
        } catch (NullPointerException npe) {}
        try {
            res += v4.hash();
        } catch (NullPointerException npe) {}
        return res;
    }

    @DontCompile
    public long hashInterpreted() {
        return s + sf + x + y + z + o + oa[0] + c + v1.hashInterpreted() + v2.hashInterpreted() + v3.hashInterpreted() + v4.hashInterpreted() + v5.hashInterpreted();
    }

    @ForceInline
    static MyValue1 setX(MyValue1 v, int x) {
        return new MyValue1(x, v.y, v.z, v.o, v.oa, v.v1, v.v2, v.v4, v.v5, v.c);
    }

    @ForceInline
    static MyValue1 setY(MyValue1 v, long y) {
        return new MyValue1(v.x, y, v.z, v.o, v.oa, v.v1, v.v2, v.v4, v.v5, v.c);
    }

    @ForceInline
    static MyValue1 setZ(MyValue1 v, short z) {
        return new MyValue1(v.x, v.y, z, v.o, v.oa, v.v1, v.v2, v.v4, v.v5, v.c);
    }

    @ForceInline
    static MyValue1 setO(MyValue1 v, Integer o) {
        return new MyValue1(v.x, v.y, v.z, o, v.oa, v.v1, v.v2, v.v4, v.v5, v.c);
    }

    @ForceInline
    static MyValue1 setOA(MyValue1 v, int[] oa) {
        return new MyValue1(v.x, v.y, v.z, v.o, oa, v.v1, v.v2, v.v4, v.v5, v.c);
    }

    @ForceInline
    static MyValue1 setC(MyValue1 v, int c) {
        return new MyValue1(v.x, v.y, v.z, v.o, v.oa, v.v1, v.v2, v.v4, v.v5, c);
    }

    @ForceInline
    static MyValue1 setV1(MyValue1 v, MyValue2 v1) {
        return new MyValue1(v.x, v.y, v.z, v.o, v.oa, v1, v.v2, v.v4, v.v5, v.c);
    }

    @ForceInline
    static MyValue1 setV2(MyValue1 v, MyValue2 v2) {
        return new MyValue1(v.x, v.y, v.z, v.o, v.oa, v.v1, v2, v.v4, v.v5, v.c);
    }

    @ForceInline
    static MyValue1 setV4(MyValue1 v, MyValue2 v4) {
        return new MyValue1(v.x, v.y, v.z, v.o, v.oa, v.v1, v.v2, v4, v.v5, v.c);
    }

    @ForceInline
    static MyValue1 setV5(MyValue1 v, MyValue2 v5) {
        return new MyValue1(v.x, v.y, v.z, v.o, v.oa, v.v1, v.v2, v.v4, v5, v.c);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MyValue1 v)) {
            return false;
        }

        return this.x == v.x && this.y == v.y && this.z == v.z && this.o == v.o && Arrays.equals(this.oa, v.oa) &&
                this.v1 == v.v1 && this.v2 == v.v2 && this.v4 == v.v4 && this.v5 == v.v5 && this.c == v.c;
    }

    @Override
    public String toString() {
        return "MyValue1[x=" + x + ", y=" + y + ", z=" + z + ", o=" + o + ", oa=" + Arrays.toString(oa) +
                ", v1=" + v1 + ", v2=" + v2 + ", v4=" + v4 + ", v5=" + v5 + ", c=" + c + "]";
    }
}
