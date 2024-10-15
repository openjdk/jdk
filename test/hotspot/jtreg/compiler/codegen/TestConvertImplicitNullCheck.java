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
 * @bug 8318562
 * @run main/othervm/timeout=200 -XX:CompileCommand=compileonly,TestConvertImplicitNullCheck::test -XX:-TieredCompilation -Xbatch TestConvertImplicitNullCheck
 * @summary Exercise float to double conversion with implicit null check
 *
 */


public class TestConvertImplicitNullCheck {

    float f = 42;

    static double test(TestConvertImplicitNullCheck t) {
        return t.f; // float to double conversion with implicit null check of 't'
    }

    public static void main(String[] args) {
        // Warmup to trigger C2 compilation
        TestConvertImplicitNullCheck t = new TestConvertImplicitNullCheck();
        for (int i = 0; i < 50_000; ++i) {
            test(t);
        }
        // implicit null check
        try {
            test(null);
            throw new RuntimeException("Test failed as no NullPointerException is thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }
}
