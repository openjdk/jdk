/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262891 8290709
 * @summary Check the pattern domination error are reported correctly.
 * @compile/fail/ref=Domination.out -XDrawDiagnostics Domination.java
 * @compile/fail/ref=DominationWithPP.out --enable-preview --source ${jdk.version} -XDrawDiagnostics Domination.java
 */
public class Domination {
    int testDominatesError1(Object o) {
        switch (o) {
            case CharSequence cs: return 0;
            case String s: return 1;
            case Object x: return -1;
        }
    }

    int testDominatesError2(Object o) {
        switch (o) {
            case CharSequence cs: return 0;
            case String s when s.isEmpty(): return 1;
            case Object x: return -1;
        }
    }

    int testDominatesError3(Object o) {
        switch (o) {
            case CharSequence cs when true: return 0;
            case String s when s.isEmpty(): return 1;
            case Object x: return -1;
        }
    }

    int testNotDominates1(Object o) {
        switch (o) {
            case CharSequence cs when cs.length() == 0: return 0;
            case String s: return 1;
            case Object x: return -1;
        }
    }

    int testDominatesStringConstant(String str) {
        switch (str) {
            case String s: return 1;
            case "": return -1;
        }
    }

    int testDominatesStringConstant2(String str) {
        switch (str) {
            case String s when s.isEmpty(): return 1;
            case "": return -1;
        }
    }

    int testDominatesStringConstant3(String str) {
        switch (str) {
            case String s when !s.isEmpty(): return 1;
            case "": return -1;
        }
    }

    int testDominatesIntegerConstant(Integer i) {
        switch (i) {
            case Integer j: return 1;
            case 0: return -1;
        }
    }

    int testDominatesIntegerConstant2(Integer i) {
        switch (i) {
            case Integer j when j == 0: return 1;
            case 0: return -1;
        }
    }

    int testDominatesIntegerConstant3(Integer i) {
        switch (i) {
            case Integer j when j == 1: return 1;
            case 0: return -1;
        }
    }

    int testDominatesEnumConstant() {
        enum E {
            A, B;
        }
        E e = E.A;
        switch (e) {
            case E d: return 1;
            case A: return -1;
        }
    }

    int testDominatesEnumConstant2() {
        enum E {
            A, B;
        }
        E e = E.A;
        switch (e) {
            case E d when d == E.A: return 1;
            case A: return -1;
        }
    }

    int testDominatesEnumConstant3() {
        enum E {
            A, B;
        }
        E e = E.A;
        switch (e) {
            case E d when d == E.B: return 1;
            case A: return -1;
        }
    }

    int testRecordPatternsDominated1() {
        record R(int a) {}
        Object o = null;
        switch (o) {
            case R r: return 1;
            case R(int a): return -1;
        }
    }

    int testRecordPatternsDominated2() {
        record R(int a) {}
        Object o = null;
        switch (o) {
            case R(int a): return 1;
            case R(int a): return -1;
        }
    }

    int testRecordPatternsDominated3() {
        record R(int a) {}
        Object o = null;
        switch (o) {
            case R r when guard(): return 1;
            case R(int a): return -1;
        }
    }

    int testRecordPatternsDominated4() {
        record R(int a) {}
        Object o = null;
        switch (o) {
            case R(int a) when guard(): return 1;
            case R(int a): return -1;
        }
    }

    boolean guard() {
        return false;
    }

    int testRecordPatternsDominated5() {
        record R(int a) {}
        Object o = null;
        switch (o) {
            case R r: return 1;
            case R(int a): return -1;
        }
    }

    int testRecordPatternsDominated6() {
        record R(int a) {}
        Object o = null;
        switch (o) {
            case R(int a): return 1;
            case R(int a): return -1;
        }
    }

    int testRecordPatternsDominated7() {
        record R(int a) {}
        Object o = null;
        switch (o) {
            case R r when true: return 1;
            case R(int a): return -1;
        }
    }

    int testRecordPatternsDominated8() {
        record R(int a) {}
        Object o = null;
        switch (o) {
            case R(int a) when true: return 1;
            case R(int a): return -1;
        }
    }

    int testNotDominates2(Integer x) {
        switch (x) {
            case Integer i: return i;
            case null : return -1;
        }
    }

    int testCasePatternDominatedbyPreceedingUnconditionalCasePattern () {
        interface A {}
        interface B {}
        A aa = new A() {};
        switch (aa) {
            case A a : return 1;
            case B b : return -1;
        }
    }
}
