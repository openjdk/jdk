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

import compiler.lib.ir_framework.DontInline;
import compiler.lib.ir_framework.ForceInline;

value class MyValueClass2Inline {
    double d;
    long l;

    @ForceInline
    public MyValueClass2Inline(double d, long l) {
        this.d = d;
        this.l = l;
    }

    @ForceInline
    static MyValueClass2Inline setD(MyValueClass2Inline v, double d) {
        return new MyValueClass2Inline(d, v.l);
    }

    @ForceInline
    static MyValueClass2Inline setL(MyValueClass2Inline v, long l) {
        return new MyValueClass2Inline(v.d, l);
    }

    @ForceInline
    public static MyValueClass2Inline createDefault() {
        return new MyValueClass2Inline(0, 0);
    }

    @ForceInline
    public static MyValueClass2Inline createWithFieldsInline(double d, long l) {
        MyValueClass2Inline v = MyValueClass2Inline.createDefault();
        v = MyValueClass2Inline.setD(v, d);
        v = MyValueClass2Inline.setL(v, l);
        return v;
    }

    @Override
    public String toString() {
        return "MyValueClass2Inline[d=" + d + ", l=" + l + "]";
    }
}

public value class MyValueClass2 extends MyAbstract {
    int x;
    byte y;
    MyValueClass2Inline v;

    @ForceInline
    public MyValueClass2(int x, byte y, MyValueClass2Inline v) {
        this.x = x;
        this.y = y;
        this.v = v;
    }

    @ForceInline
    public static MyValueClass2 createDefaultInline() {
        return new MyValueClass2(0, (byte)0, null);
    }

    @ForceInline
    public static MyValueClass2 createWithFieldsInline(int x, long y, double d) {
        MyValueClass2 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, (byte)x);
        v = setV(v, MyValueClass2Inline.createWithFieldsInline(d, y));
        return v;
    }

    @ForceInline
    public static MyValueClass2 createWithFieldsInline(int x, double d) {
        MyValueClass2 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, (byte)x);
        v = setV(v, MyValueClass2Inline.createWithFieldsInline(d, InlineTypes.rL));
        return v;
    }

    @DontInline
    public static MyValueClass2 createWithFieldsDontInline(int x, double d) {
        MyValueClass2 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, (byte)x);
        v = setV(v, MyValueClass2Inline.createWithFieldsInline(d, InlineTypes.rL));
        return v;
    }

    @ForceInline
    public long hash() {
        return x + y + (long)v.d + v.l;
    }

    @DontInline
    public long hashInterpreted() {
        return x + y + (long)v.d + v.l;
    }

    @ForceInline
    public void print() {
        System.out.print("x=" + x + ", y=" + y + ", d=" + v.d + ", l=" + v.l);
    }

    @ForceInline
    static MyValueClass2 setX(MyValueClass2 v, int x) {
        return new MyValueClass2(x, v.y, v.v);
    }

    @ForceInline
    static MyValueClass2 setY(MyValueClass2 v, byte y) {
        return new MyValueClass2(v.x, y, v.v);
    }

    @ForceInline
    static MyValueClass2 setV(MyValueClass2 v, MyValueClass2Inline vi) {
        return new MyValueClass2(v.x, v.y, vi);
    }

    @Override
    public String toString() {
        return "MyValueClass2[x=" + x + ", y=" + y + ", v=" + v + "]";
    }
}
