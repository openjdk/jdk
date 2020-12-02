/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8257594
 * @summary Test that failing checkcast does not trigger repeated recompilation until cutoff is hit.
 * @requires vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:CompileCommand=dontinline,compiler.uncommontrap.TestNullAssertAtCheckCast::test*
 *                   compiler.uncommontrap.TestNullAssertAtCheckCast
 */

package compiler.uncommontrap;

import sun.hotspot.WhiteBox;

import java.lang.reflect.Method;

public class TestNullAssertAtCheckCast {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int COMP_LEVEL_FULL_OPTIMIZATION = 4;

    static Long cast(Object obj) {
        return (Long)obj;
    }

    static void test1() {
        try {
            // Always fails
            cast(new Integer(0));
        } catch (ClassCastException cce) {
            // Ignored
        }
    }
    
    static void arrayStore(Object[] array) {
        array[0] = new Integer(42);
    }
    
    static void test2() {
        try {
            // Always fails
            arrayStore(new Long[1]);
        } catch (ArrayStoreException cce) {
            // Ignored
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 10_000_000; ++i) {
            test1();
            test2();
        }
        Method method = TestNullAssertAtCheckCast.class.getDeclaredMethod("test1");
        if (!WB.isMethodCompilable(method, COMP_LEVEL_FULL_OPTIMIZATION, false)) {
            throw new RuntimeException("TestNullAssertAtCheckCast::test1 not compilable");
        }
        method = TestNullAssertAtCheckCast.class.getDeclaredMethod("test2");
        if (!WB.isMethodCompilable(method, COMP_LEVEL_FULL_OPTIMIZATION, false)) {
            throw new RuntimeException("TestNullAssertAtCheckCast::test2 not compilable");
        }
    }
}

