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
 * @bug 8373878
 * @summary Test various interactions with type speculation and flat in array properties which lead to inconsistencies.
 *
 * @enablePreview
 * @run main/othervm -Xbatch -XX:CompileOnly=${test.main.class}::* -XX:-UseOnStackReplacement -XX:TypeProfileLevel=222
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestFlatInArraySpeculation {
    static boolean flag;
    static Object oFld = new Object();
    static Object oFld2;
    static double dFld;

    public static void main(String[] args) {
        MaybeFlat1 mf1 = new MaybeFlat1(1);
        MaybeFlat2 mf2 = new MaybeFlat2(2);
        Flat1 f1 = new Flat1(3);
        Flat2 f2 = new Flat2(4);
        X x = new X();
        Y y = new Y();

        flag = false;
        for (int i = 0; i < 10000; i++) {
            // Profiles to only return X or Y.
            xOrY(x);
            xOrY(y);

            // Profiles to only return Flat1 or Flat2.
            flat1OrFlat2(f1);
            flat1OrFlat2(f2);

            // Profile that we see MaybeFlat1 or X
            testTypeNotFlatButKlassMaybeFlat1(mf1);
            testTypeNotFlatButKlassMaybeFlat1(x);
            testTypeNotFlatButKlassMaybeFlat2(mf1);

            // Profile that we see MaybeFlat1 or Flat1
            testTypeFlatButKlassMaybeFlat1(mf1);
            testTypeFlatButKlassMaybeFlat1(f1);
            testTypeFlatButKlassMaybeFlat2(mf1);

            // Profile that we see Flat1 or X
            testSuperNotFlatSubFlat(f1);
            testSuperNotFlatSubFlat(x);

            // Profile that we see Flat1 or Flat2
            testSuperFlatSubNotFlat(f1);
            testSuperFlatSubNotFlat(f2);
        }

        flag = true;
        for (int i = 0; i < 10000; i++) {
            test1aWrapper(x);
            test1aWrapper(y);
            test1bWrapper(x);
            test1bWrapper(y);
            test2aWrapper(f1);
            test2aWrapper(f2);
            test2bWrapper(f1);
            test2bWrapper(f2);
            test3Wrapper(x);
            test3Wrapper(y);
            test4Wrapper(f1);
            test4Wrapper(f2);
        }
    }

    static Object xOrY(Object o) {
        o.equals(oFld);
        return o;
    }

    static Object flat1OrFlat2(Object o) {
        o.equals(oFld);
        return o;
    }

    static void test1aWrapper(Object parm) {
        parm = xOrY(parm); // Phi[X (not flat in array), Y (not flat in array)] = Phi (not flat in array)
        testTypeNotFlatButKlassMaybeFlat1(parm);
    }

    static void testTypeNotFlatButKlassMaybeFlat1(Object o) {
        if (o instanceof MaybeFlat1) { // o: speculated to be X or Y -> not flat in array.
            // CheckCast[MaybeFlat1] -> maybe flat in array -> contradicts not flat in array.
            // But it is okay to propagate not flat in array here: If during runtime, we actually have MaybeFlat1
            // then we would trap earlier because 'o' is speculated to be X or Y.
            I i = (MaybeFlat1)o;
            oFld2 = i.getClass();
        }
    }

    static void test1bWrapper(Object parm) {
        parm = xOrY(parm); // Phi[X (not flat in array), Y (not flat in array)] = Phi (not flat in array)
        testTypeNotFlatButKlassMaybeFlat2(parm);
    }

    static void testTypeNotFlatButKlassMaybeFlat2(Object o) {
        if (flag) {
            return;
        }
        // o: X or Y -> not flat in array (1)
        get(o); // o returned again from get(): speculated to be MaybeFlat1 -> maybe flat in array
        if (o.getClass() == MaybeFlat1.class) { // receiver speculated to be MaybeFlat1 -> clash with not flat in array (1)
            dFld = 34;
        }
    }

    static void test2aWrapper(Object parm) {
        parm = flat1OrFlat2(parm); // Phi[Flat1 (flat in array), Flat2 (flat in array)] = Phi (flat in array)
        testTypeFlatButKlassMaybeFlat1(parm);
    }

    static void testTypeFlatButKlassMaybeFlat1(Object o) {
        if (o instanceof MaybeFlat1) { // o: speculated to be Flat1 or Flat2 -> flat in array.
            I i = (MaybeFlat1)o; // CheckCast[MaybeFlat1] -> maybe flat in array
            oFld2 = i.getClass();
        }
    }

    static void test2bWrapper(Object parm) {
        parm = flat1OrFlat2(parm); // Phi[Flat1 (flat in array), Flat2 (flat in array)] = Phi (flat in array)
        testTypeNotFlatButKlassMaybeFlat2(parm);
    }

    static void testTypeFlatButKlassMaybeFlat2(Object o) {
        if (flag) {
            return;
        }
        // o: Flat1 or Flat2 -> flat in array (1)
        get(o); // o returned again from get(): speculated to be MaybeFlat1 -> maybe flat in array
        if (o.getClass() == MaybeFlat1.class) { // receiver speculated to be MaybeFlat1 -> clash flat in array (1)
            dFld = 34;
        }
    }

    static void test3Wrapper(Object parm) {
        parm = xOrY(parm); // Phi[X (not flat in array), Y (not flat in array)] = Phi (not flat in array)
        testSuperNotFlatSubFlat(parm);
    }

    static void testSuperNotFlatSubFlat(Object o) {
        // o speculated to be X or Y: not flat in array
        // Flat1: flat in array -> o and Flat cannot subtype either other, and thus we can fold the if-branch away.
        // Note: We could be calling test3Wrapper() with a Flat1 and then it seems to be wrong to fold the if-branch.
        //       However, in this case, we would trap where we speculated that o is X or Y, so this is fine.
        if (o instanceof Flat1) {
            I i = (Flat1)o;
            oFld2 = i.getClass();
        }
    }

    static void test4Wrapper(Object parm) {
        parm = flat1OrFlat2(parm); // Phi[Flat1 (flat in array), Flat2 (flat in array)] = Phi (flat in array)
        testSuperFlatSubNotFlat(parm);
    }

    // Currently already handled.
    static void testSuperFlatSubNotFlat(Object o) {
        if (o instanceof X) { // o speculated: flat in array, X: not flat in array -> cannot subtype and can be folded.
            X x = (X)o;
            oFld2 = x.getClass();
        }
    }

    public static Object get(Object o) {
        return o;
    }


    static class X {
        public boolean equals(Object o) {
            return false;
        }
    }

    static class Y {
        public boolean equals(Object o) {
            return false;
        }
    }

    interface I {}

    static value class Flat1 implements I {
        int x;

        Flat1(int x) {
            this.x = x;
        }

        public boolean equals(Object o) {
            return false;
        }
    }

    static value class Flat2 implements I {
        int x;

        Flat2(int x) {
            this.x = x;
        }

        public boolean equals(Object o) {
            return false;
        }
    }

    static value class MaybeFlat1 implements I {
        long x;

        MaybeFlat1(long x) {
            this.x = x;
        }
    }

    static value class MaybeFlat2 implements I {
        long x;

        MaybeFlat2(long x) {
            this.x = x;
        }
    }
}
