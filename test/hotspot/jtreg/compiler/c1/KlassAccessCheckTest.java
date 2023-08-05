/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8293044
 * @requires vm.compiler1.enabled
 * @compile KlassAccessCheckPackagePrivate.jasm
 * @compile KlassAccessCheck.jasm
 * @run main/othervm -Xbatch -XX:TieredStopAtLevel=1 compiler.c1.KlassAccessCheckTest
 */

package compiler.c1;

public class KlassAccessCheckTest {
    static void test(Runnable r) {
        for (int i = 0; i < 1000; ++i) {
            try {
                r.run();
                throw new AssertionError("No IllegalAccessError thrown");
            } catch (IllegalAccessError e) {
                // Expected
            } catch (AssertionError e) {
                throw e; // rethrow
            } catch (Throwable e) {
                throw new AssertionError("Wrong exception thrown", e);
            }
        }
    }

    public static void main(String[] args) {
        test(() -> KlassAccessCheck.testNewInstance());
        test(() -> KlassAccessCheck.testNewArray());
        test(() -> KlassAccessCheck.testMultiNewArray());
        test(() -> KlassAccessCheck.testCheckCast(42));
        test(() -> KlassAccessCheck.testCheckCastArr(new Integer[0]));
        test(() -> KlassAccessCheck.testInstanceOf(42));
        test(() -> KlassAccessCheck.testInstanceOfArr(new Integer[0]));
        System.out.println("TEST PASSED");
    }
}
