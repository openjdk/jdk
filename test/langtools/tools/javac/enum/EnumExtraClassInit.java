/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8219412
 * @summary Verify switched-on enum classes are not initialized until actual 'first use'
 */

public class EnumExtraClassInit {

    static boolean Ainit;
    static boolean Binit;

    enum A {
        X;
        static {
            Ainit = true;
        }
    }

    enum B {
        X;
        static {
            Binit = true;
        }
    }

    static void testA(A x) {
        switch (x) {
        }
    }

    static void testB(A x) {
        switch (x) {
        }
    }

    public static void main(String[] args) {
        testA(A.X);
        if (!Ainit || Binit)
            throw new RuntimeException("Ainit=" + Ainit + ", Binit=" + Binit);
    }
}
