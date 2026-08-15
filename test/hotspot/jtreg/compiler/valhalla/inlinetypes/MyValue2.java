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

import compiler.lib.ir_framework.DontInline;
import compiler.lib.ir_framework.ForceInline;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

@LooselyConsistentValue
value class MyValue2Inline {
    double d;
    long l;

    @ForceInline
    public MyValue2Inline(double d, long l) {
        this.d = d;
        this.l = l;
    }

    @ForceInline
    static MyValue2Inline setD(MyValue2Inline v, double d) {
        return new MyValue2Inline(d, v.l);
    }

    @ForceInline
    static MyValue2Inline setL(MyValue2Inline v, long l) {
        return new MyValue2Inline(v.d, l);
    }

    @ForceInline
    public static MyValue2Inline createDefault() {
        return new MyValue2Inline(0, 0);
    }

    @ForceInline
    public static MyValue2Inline createWithFieldsInline(double d, long l) {
        MyValue2Inline v = MyValue2Inline.createDefault();
        v = MyValue2Inline.setD(v, d);
        v = MyValue2Inline.setL(v, l);
        return v;
    }

    @Override
    public String toString() {
        return "MyValue2Inline[d=" + d + ", l=" + l + "]";
    }
}

@LooselyConsistentValue
public value class MyValue2 extends MyAbstract {
    int x;
    byte y;
    @NullRestricted
    MyValue2Inline v;

    static final MyValue2 DEFAULT = createDefaultInline();

    @ForceInline
    public MyValue2(int x, byte y, MyValue2Inline v) {
        this.x = x;
        this.y = y;
        this.v = v;
    }

    @ForceInline
    public static MyValue2 createDefaultInline() {
        return new MyValue2(0, (byte)0, MyValue2Inline.createDefault());
    }

    @ForceInline
    public static MyValue2 createWithFieldsInline(int x, long y, double d) {
        MyValue2 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, (byte)x);
        v = setV(v, MyValue2Inline.createWithFieldsInline(d, y));
        return v;
    }

    @ForceInline
    public static MyValue2 createWithFieldsInline(int x, double d) {
        MyValue2 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, (byte)x);
        v = setV(v, MyValue2Inline.createWithFieldsInline(d, InlineTypes.rL));
        return v;
    }

    @DontInline
    public static MyValue2 createWithFieldsDontInline(int x, double d) {
        MyValue2 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, (byte)x);
        v = setV(v, MyValue2Inline.createWithFieldsInline(d, InlineTypes.rL));
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
    static MyValue2 setX(MyValue2 v, int x) {
        return new MyValue2(x, v.y, v.v);
    }

    @ForceInline
    static MyValue2 setY(MyValue2 v, byte y) {
        return new MyValue2(v.x, y, v.v);
    }

    @ForceInline
    static MyValue2 setV(MyValue2 v, MyValue2Inline vi) {
        return new MyValue2(v.x, v.y, vi);
    }

    @Override
    public String toString() {
        return "MyValue2[x=" + x + ", y=" + y + ", v=" + v + "]";
    }
}
