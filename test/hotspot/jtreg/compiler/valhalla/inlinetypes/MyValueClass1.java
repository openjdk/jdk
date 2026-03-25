/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

@ForceCompileClassInitializer
public value class MyValueClass1 extends MyAbstract {
    static int s;
    static long sf = InlineTypes.rL;
    int x;
    long y;
    short z;
    Integer o;
    int[] oa;
    MyValueClass2 v1;
    MyValueClass2 v2;
    static MyValueClass2 v3 = MyValueClass2.createWithFieldsInline(InlineTypes.rI, InlineTypes.rD);
    MyValueClass2 v4;
    int c;

    @ForceInline
    public MyValueClass1(int x, long y, short z, Integer o, int[] oa, MyValueClass2 v1, MyValueClass2 v2, MyValueClass2 v4, int c) {
        s = 0;
        this.x = x;
        this.y = y;
        this.z = z;
        this.o = o;
        this.oa = oa;
        this.v1 = v1;
        this.v2 = v2;
        this.v4 = v4;
        this.c = c;
    }

    @DontInline
    static MyValueClass1 createDefaultDontInline() {
        return createDefaultInline();
    }

    @ForceInline
    static MyValueClass1 createDefaultInline() {
        return new MyValueClass1(0, 0, (short)0, null, null, null, null, null, 0);
    }

    @DontInline
    static MyValueClass1 createWithFieldsDontInline(int x, long y) {
        return createWithFieldsInline(x, y);
    }

    @ForceInline
    static MyValueClass1 createWithFieldsInline(int x, long y) {
        MyValueClass1 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, y);
        v = setZ(v, (short)x);
        // Don't use Integer.valueOf here to avoid control flow added by Integer cache check
        v = setO(v, new Integer(x));
        int[] oa = {x};
        v = setOA(v, oa);
        v = setV1(v, MyValueClass2.createWithFieldsInline(x, y, InlineTypes.rD));
        v = setV2(v, MyValueClass2.createWithFieldsInline(x + 1, y + 1, InlineTypes.rD + 1));
        v = setV4(v, MyValueClass2.createWithFieldsInline(x + 2, y + 2, InlineTypes.rD + 2));
        v = setC(v, (int)(x+y));
        return v;
    }

    // Hash only primitive and inline type fields to avoid NullPointerException
    @ForceInline
    public long hashPrimitive() {
        return s + sf + x + y + z + c + v1.hash() + v2.hash() + v3.hash();
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
        return s + sf + x + y + z + o + oa[0] + c + v1.hashInterpreted() + v2.hashInterpreted() + v3.hashInterpreted() + v4.hashInterpreted();
    }

    @ForceInline
    public void print() {
        System.out.print("s=" + s + ", sf=" + sf + ", x=" + x + ", y=" + y + ", z=" + z + ", o=" + (o != null ? (Integer)o : "NULL") + ", oa=" + (oa != null ? oa[0] : "NULL") + ", v1[");
        v1.print();
        System.out.print("], v2[");
        v2.print();
        System.out.print("], v3[");
        v3.print();
        System.out.print("], v4[");
        v4.print();
        System.out.print("], c=" + c);
    }

    @ForceInline
    static MyValueClass1 setX(MyValueClass1 v, int x) {
        return new MyValueClass1(x, v.y, v.z, v.o, v.oa, v.v1, v.v2, v.v4, v.c);
    }

    @ForceInline
    static MyValueClass1 setY(MyValueClass1 v, long y) {
        return new MyValueClass1(v.x, y, v.z, v.o, v.oa, v.v1, v.v2, v.v4, v.c);
    }

    @ForceInline
    static MyValueClass1 setZ(MyValueClass1 v, short z) {
        return new MyValueClass1(v.x, v.y, z, v.o, v.oa, v.v1, v.v2, v.v4, v.c);
    }

    @ForceInline
    static MyValueClass1 setO(MyValueClass1 v, Integer o) {
        return new MyValueClass1(v.x, v.y, v.z, o, v.oa, v.v1, v.v2, v.v4, v.c);
    }

    @ForceInline
    static MyValueClass1 setOA(MyValueClass1 v, int[] oa) {
        return new MyValueClass1(v.x, v.y, v.z, v.o, oa, v.v1, v.v2, v.v4, v.c);
    }

    @ForceInline
    static MyValueClass1 setC(MyValueClass1 v, int c) {
        return new MyValueClass1(v.x, v.y, v.z, v.o, v.oa, v.v1, v.v2, v.v4, c);
    }

    @ForceInline
    static MyValueClass1 setV1(MyValueClass1 v, MyValueClass2 v1) {
        return new MyValueClass1(v.x, v.y, v.z, v.o, v.oa, v1, v.v2, v.v4, v.c);
    }

    @ForceInline
    static MyValueClass1 setV2(MyValueClass1 v, MyValueClass2 v2) {
        return new MyValueClass1(v.x, v.y, v.z, v.o, v.oa, v.v1, v2, v.v4, v.c);
    }

    @ForceInline
    static MyValueClass1 setV4(MyValueClass1 v, MyValueClass2 v4) {
        return new MyValueClass1(v.x, v.y, v.z, v.o, v.oa, v.v1, v.v2, v4, v.c);
    }

    @DontInline
    void dontInline(MyValueClass1 arg) {

    }

    @Override
    public String toString() {
        return "MyValueClass1[s=" + s + ", sf=" + sf + ", x=" + x + ", y=" + y + ", z=" + z + ", o=" + o + ", oa=" + Arrays.toString(oa) +
                ", v1=" + v1 + ", v2=" + v2 + ", v4=" + v4 + ", c=" + c + "]";
    }
}
