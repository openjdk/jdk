/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8032011
 * @summary biased locking's revoke_bias locks monitor in compiled frame with eliminated lock
 * @run main/othervm -XX:-UseOnStackReplacement -XX:CompileCommand=dontinline,TestLockEliminatedAtDeopt$A.m2 -XX:-BackgroundCompilation -XX:BiasedLockingStartupDelay=0 TestLockEliminatedAtDeopt
 *
 */

public class TestLockEliminatedAtDeopt {

    static class A {
        void m() {
        }

        // This lock is not eliminated but biased to main thread on
        // first call
        synchronized void m2(boolean trap) {
            if (trap) {
                new B();
            }
        }
    }

    static class B extends A {
        void m() {
        }
    }

    static void m1(boolean trap) {
        A a = new A();
        // This lock is eliminated by c2
        synchronized(a) {
            a.m2(trap);
            a.m();
        }
    }

     public static void main(String[] args) {
        for (int i = 0; i < 20000; i++) {
            m1(false);
        }
        // Trigger uncommon trap in A.m2() (class unloaded) and
        // deoptimization of m1() (CHA invalidated). Uncommon trap
        // code locks monitor in m1's frame where's it's eliminated.
        m1(true);
    }
}
