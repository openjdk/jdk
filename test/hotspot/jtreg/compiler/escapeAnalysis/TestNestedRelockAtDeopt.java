/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8324174
 * @summary During deoptimization locking and unlocking for nested locks are executed in incorrect order.
 * @requires vm.compMode != "Xint"
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -Xmx128M
 *                   -XX:CompileCommand=exclude,TestNestedRelockAtDeopt::main TestNestedRelockAtDeopt
 */

import java.util.ArrayList;
public class TestNestedRelockAtDeopt {

    static final int CHUNK = 1000;
    static ArrayList<Object> arr = null;

    public static void main(String[] args) {
        arr = new ArrayList<>();
        try {
            while (true) {
                test1();
            }
        } catch (OutOfMemoryError oom) {
            arr = null; // Free memory
            System.out.println("OOM caught in test1");
        }
        arr = new ArrayList<>();
        try {
            while (true) {
                test2();
            }
        } catch (OutOfMemoryError oom) {
            arr = null; // Free memory
            System.out.println("OOM caught in test2");
        }
        arr = new ArrayList<>();
        TestNestedRelockAtDeopt obj = new TestNestedRelockAtDeopt();
        try {
            while (true) {
                test3(obj);
            }
        } catch (OutOfMemoryError oom) {
            arr = null; // Free memory
            System.out.println("OOM caught in test3");
        }
        arr = new ArrayList<>();
        try {
            while (true) {
                test4(obj);
            }
        } catch (OutOfMemoryError oom) {
            arr = null; // Free memory
            System.out.println("OOM caught in test4");
        }
    }

    // Nested locks in one method
    static void test1() { // Nested lock in one method
        synchronized (TestNestedRelockAtDeopt.class) {
            synchronized (new TestNestedRelockAtDeopt()) { // lock eliminated - not escaped allocation
                synchronized (TestNestedRelockAtDeopt.class) { // nested lock eliminated
                    synchronized (new TestNestedRelockAtDeopt()) { // lock eliminated - not escaped allocation
                        synchronized (TestNestedRelockAtDeopt.class) { // nested lock eliminated
                            arr.add(new byte[CHUNK]);
                        }
                    }
                }
            }
        }
    }

    // Nested locks in inlined method
    static void foo() {
        synchronized (new TestNestedRelockAtDeopt()) {  // lock eliminated - not escaped allocation
            synchronized (TestNestedRelockAtDeopt.class) {  // nested lock eliminated when inlined
                arr.add(new byte[CHUNK]);
            }
        }
    }

    static void test2() {
        synchronized (TestNestedRelockAtDeopt.class) {
            synchronized (new TestNestedRelockAtDeopt()) {  // lock eliminated - not escaped allocation
                synchronized (TestNestedRelockAtDeopt.class) { // nested lock eliminated
                    foo(); // Inline
                }
            }
        }
    }

    // Nested locks in one method
    static void test3(TestNestedRelockAtDeopt obj) {
        synchronized (TestNestedRelockAtDeopt.class) {
            synchronized (obj) { // lock not eliminated - external object
                synchronized (TestNestedRelockAtDeopt.class) { // nested lock eliminated
                    synchronized (obj) { // nested lock eliminated
                        synchronized (TestNestedRelockAtDeopt.class) { // nested lock eliminated
                            arr.add(new byte[CHUNK]);
                        }
                    }
                }
            }
        }
    }

    // Nested locks with different objects in inlined method
    static void bar(TestNestedRelockAtDeopt obj) {
        synchronized (obj) {  // nested lock eliminated when inlined
            synchronized (TestNestedRelockAtDeopt.class) {  // nested lock eliminated when inlined
                arr.add(new byte[CHUNK]);
            }
        }
    }

    static void test4(TestNestedRelockAtDeopt obj) {
        synchronized (TestNestedRelockAtDeopt.class) {
            synchronized (obj) {  // lock not eliminated - external object
                synchronized (TestNestedRelockAtDeopt.class) { // nested lock eliminated
                    bar(obj); // Inline
                }
            }
        }
    }
}
