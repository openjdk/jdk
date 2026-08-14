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

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import compiler.lib.ir_framework.DontInline;
import compiler.lib.ir_framework.ForceInline;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

@LooselyConsistentValue
value class MyValue3Inline {
    float f7;
    double f8;

    @ForceInline
    public MyValue3Inline(float f7, double f8) {
        this.f7 = f7;
        this.f8 = f8;
    }

    @ForceInline
    static MyValue3Inline setF7(MyValue3Inline v, float f7) {
        return new MyValue3Inline(f7, v.f8);
    }

    @ForceInline
    static MyValue3Inline setF8(MyValue3Inline v, double f8) {
        return new MyValue3Inline(v.f7, f8);
    }

    @ForceInline
    public static MyValue3Inline createDefault() {
        return new MyValue3Inline(0, 0);
    }

    @ForceInline
    public static MyValue3Inline createWithFieldsInline(float f7, double f8) {
        MyValue3Inline v = createDefault();
        v = setF7(v, f7);
        v = setF8(v, f8);
        return v;
    }

    @Override
    public String toString() {
        return "MyValue3Inline[f7=" + f7 + ", f8=" + f8 + "]";
    }
}

// Inline type definition to stress test return of an inline type in registers
// (uses all registers of calling convention on x86_64)
@LooselyConsistentValue
public value class MyValue3 extends MyAbstract {
    char c;
    byte bb;
    short s;
    int i;
    long l;
    Object o;
    float f1;
    double f2;
    float f3;
    double f4;
    float f5;
    double f6;
    @NullRestricted
    MyValue3Inline v1;

    static final MyValue3 DEFAULT = new MyValue3((char)0, (byte)0, (short)0, 0, 0, null,
                                                 0, 0, 0, 0, 0, 0, new MyValue3Inline(0, 0));

    @ForceInline
    public MyValue3(char c, byte bb, short s, int i, long l, Object o,
                    float f1, double f2, float f3, double f4, float f5, double f6,
                    MyValue3Inline v1) {
        this.c = c;
        this.bb = bb;
        this.s = s;
        this.i = i;
        this.l = l;
        this.o = o;
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
        this.f4 = f4;
        this.f5 = f5;
        this.f6 = f6;
        this.v1 = v1;
    }

    @ForceInline
    static MyValue3 setC(MyValue3 v, char c) {
        return new MyValue3(c, v.bb, v.s, v.i, v.l, v.o, v.f1, v.f2, v.f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setBB(MyValue3 v, byte bb) {
        return new MyValue3(v.c, bb, v.s, v.i, v.l, v.o, v.f1, v.f2, v.f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setS(MyValue3 v, short s) {
        return new MyValue3(v.c, v.bb, s, v.i, v.l, v.o, v.f1, v.f2, v.f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setI(MyValue3 v, int i) {
        return new MyValue3(v.c, v.bb, v.s, i, v.l, v.o, v.f1, v.f2, v.f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setL(MyValue3 v, long l) {
        return new MyValue3(v.c, v.bb, v.s, v.i, l, v.o, v.f1, v.f2, v.f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setO(MyValue3 v, Object o) {
        return new MyValue3(v.c, v.bb, v.s, v.i, v.l, o, v.f1, v.f2, v.f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setF1(MyValue3 v, float f1) {
        return new MyValue3(v.c, v.bb, v.s, v.i, v.l, v.o, f1, v.f2, v.f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setF2(MyValue3 v, double f2) {
        return new MyValue3(v.c, v.bb, v.s, v.i, v.l, v.o, v.f1, f2, v.f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setF3(MyValue3 v, float f3) {
        return new MyValue3(v.c, v.bb, v.s, v.i, v.l, v.o, v.f1, v.f2, f3, v.f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setF4(MyValue3 v, double f4) {
        return new MyValue3(v.c, v.bb, v.s, v.i, v.l, v.o, v.f1, v.f2, v.f3, f4, v.f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setF5(MyValue3 v, float f5) {
        return new MyValue3(v.c, v.bb, v.s, v.i, v.l, v.o, v.f1, v.f2, v.f3, v.f4, f5, v.f6, v.v1);
    }

    @ForceInline
    static MyValue3 setF6(MyValue3 v, double f6) {
        return new MyValue3(v.c, v.bb, v.s, v.i, v.l, v.o, v.f1, v.f2, v.f3, v.f4, v.f5, f6, v.v1);
    }

    @ForceInline
    static MyValue3 setV1(MyValue3 v, MyValue3Inline v1) {
        return new MyValue3(v.c, v.bb, v.s, v.i, v.l, v.o, v.f1, v.f2, v.f3, v.f4, v.f5, v.f6, v1);
    }

    @ForceInline
    public static MyValue3 createDefault() {
        return new MyValue3((char)0, (byte)0, (short)0, 0, 0, null, 0, 0, 0, 0, 0, 0, MyValue3Inline.createDefault());
    }

    @ForceInline
    public static MyValue3 create() {
        java.util.Random r = Utils.getRandomInstance();
        MyValue3 v = createDefault();
        v = setC(v, (char)r.nextInt());
        v = setBB(v, (byte)r.nextInt());
        v = setS(v, (short)r.nextInt());
        v = setI(v, r.nextInt());
        v = setL(v, r.nextLong());
        v = setO(v, new Object());
        v = setF1(v, r.nextFloat());
        v = setF2(v, r.nextDouble());
        v = setF3(v, r.nextFloat());
        v = setF4(v, r.nextDouble());
        v = setF5(v, r.nextFloat());
        v = setF6(v, r.nextDouble());
        v = setV1(v, MyValue3Inline.createWithFieldsInline(r.nextFloat(), r.nextDouble()));
        return v;
    }

    @DontInline
    public static MyValue3 createDontInline() {
        return create();
    }

    @ForceInline
    public static MyValue3 copy(MyValue3 other) {
        MyValue3 v = createDefault();
        v = setC(v, other.c);
        v = setBB(v, other.bb);
        v = setS(v, other.s);
        v = setI(v, other.i);
        v = setL(v, other.l);
        v = setO(v, other.o);
        v = setF1(v, other.f1);
        v = setF2(v, other.f2);
        v = setF3(v, other.f3);
        v = setF4(v, other.f4);
        v = setF5(v, other.f5);
        v = setF6(v, other.f6);
        v = setV1(v, other.v1);
        return v;
    }

    @DontInline
    public void verify(MyValue3 other) {
        Asserts.assertEQ(c, other.c);
        Asserts.assertEQ(bb, other.bb);
        Asserts.assertEQ(s, other.s);
        Asserts.assertEQ(i, other.i);
        Asserts.assertEQ(l, other.l);
        Asserts.assertEQ(o, other.o);
        Asserts.assertEQ(f1, other.f1);
        Asserts.assertEQ(f2, other.f2);
        Asserts.assertEQ(f3, other.f3);
        Asserts.assertEQ(f4, other.f4);
        Asserts.assertEQ(f5, other.f5);
        Asserts.assertEQ(f6, other.f6);
        Asserts.assertEQ(v1.f7, other.v1.f7);
        Asserts.assertEQ(v1.f8, other.v1.f8);
    }

    @ForceInline
    public long hash() {
        return c +
            bb +
            s +
            i +
            l +
            o.hashCode() +
            Float.hashCode(f1) +
            Double.hashCode(f2) +
            Float.hashCode(f3) +
            Double.hashCode(f4) +
            Float.hashCode(f5) +
            Double.hashCode(f6) +
            Float.hashCode(v1.f7) +
            Double.hashCode(v1.f8);
    }

    @Override
    public String toString() {
        return "MyValue3[c=" + c + ", bb=" + bb + ", s=" + s + ", i=" + i + ", l=" + l + ", o=" + o +
                ", f1=" + f1 + ", f2=" + f2 + ", f3=" + f3 + ", f4=" + f4 + ", f5=" + f5 + ", f6=" + f6 + ", v1=" + v1 + "]";
    }
}

