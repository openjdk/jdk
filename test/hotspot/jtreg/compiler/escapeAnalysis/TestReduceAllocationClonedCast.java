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
 * @bug 8385993
 * @summary C2: Incorrect escape analysis in reduce allocation merges causes NPE
 * @run shell/othervm -Xbatch -XX:-UseDeepIGVNRevisit -XX:CompileCommand=compileonly,${test.main.class}.* ${test.src}
 */

package compiler.escapeAnalysis;


// java -Xbatch -XX:-UseDeepIGVNRevisit -XX:CompileCommand=compileonly,Test.* Test.java
// This will trigger an assertion. Uncomment comments to get an NPE only with C2 compilation.
public class Test {
    public static void main(String[] args) {
        for (int i = 0; i < 8_000; i++) {
            test();
        }
        System.out.println("DONE");
    }
        
    static int test() {
        int val = 0;
        A a = getA(Integer.valueOf(14));
        if (a != null) {
            val += a.getA1();
        }
        return val;
    }

    static A getA(Object obj) {
        java.util.Random rnd = new java.util.Random();
        int rndInt= rnd.nextInt(100);
        if (rndInt < 15) {
            return null;
        }

        A retA = new A(rndInt/*, rnd.nextInt(100), rnd.nextInt(100)*/);
        if (obj != null) {
            B b = new B(retA);
            retA = b.getAFromB(obj);
        }

        return retA;
    }

    static class A {
        final Integer a1;
        //final int a2;
        //final long a3;

        A(int a1/*, int a2, long a3*/) {
            this.a1 = a1;
            //this.a2 = a2;
            //this.a3 = a3;
        }

        int getA1() {
            return a1.intValue();
        }

    }

    static class B {
        final A a;
        B (A a) {
            this.a = a;
        }

        A getAFromB(Object obj) {
            return a;
        }
    }
}

