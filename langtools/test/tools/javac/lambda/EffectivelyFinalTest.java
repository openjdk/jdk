/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Integrate efectively final check with DA/DU analysis
 * @compile/fail/ref=EffectivelyFinalTest01.out -XDallowEffectivelyFinalInInnerClasses -XDrawDiagnostics EffectivelyFinalTest.java
 * @compile/fail/ref=EffectivelyFinalTest02.out -source 7 -Xlint:-options -XDrawDiagnostics EffectivelyFinalTest.java
 */
class EffectivelyFinalTest {

    void m1(int x) {
        int y = 1;
        new Object() { { System.out.println(x+y); } }; //ok - both x and y are EF
    }

    void m2(int x) {
        int y;
        y = 1;
        new Object() { { System.out.println(x+y); } }; //ok - both x and y are EF
    }

    void m3(int x, boolean cond) {
        int y;
        if (cond) y = 1;
        new Object() { { System.out.println(x+y); } }; //error - y not DA
    }

    void m4(int x, boolean cond) {
        int y;
        if (cond) y = 1;
        else y = 2;
        new Object() { { System.out.println(x+y); } }; //ok - both x and y are EF
    }

    void m5(int x, boolean cond) {
        int y;
        if (cond) y = 1;
        y = 2;
        new Object() { { System.out.println(x+y); } }; //error - y not EF
    }

    void m6(int x) {
        new Object() { { System.out.println(x+1); } }; //error - x not EF
        x++;
    }

    void m7(int x) {
        new Object() { { System.out.println(x=1); } }; //error - x not EF
    }

    void m8() {
        int y;
        new Object() { { System.out.println(y=1); } }; //error - y not EF
    }
}
