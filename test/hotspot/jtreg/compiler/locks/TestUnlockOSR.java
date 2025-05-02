/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8316746
 * @summary During OSR, locks get transferred from interpreter frame.
 *          Check that unlocking 2 such locks works in the OSR compiled nmethod.
 *          Some platforms verify that the unlocking happens in the corrent order.
 *
 * @run main/othervm -Xbatch TestUnlockOSR
 */

public class TestUnlockOSR {
    static void test_method(Object a, Object b, int limit) {
        synchronized(a) { // allocate space for monitors
            synchronized(b) {
            }
        } // free space to test allocation in reused space
        synchronized(a) { // reuse the space
            synchronized(b) {
                for (int i = 0; i < limit; i++) {}
            }
        }
    }

    public static void main(String[] args) {
        Object a = new TestUnlockOSR(),
               b = new TestUnlockOSR();
        // avoid uncommon trap before last unlocks
        for (int i = 0; i < 100; i++) { test_method(a, b, 0); }
        // trigger OSR
        test_method(a, b, 100000);
    }
}
