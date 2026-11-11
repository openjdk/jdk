/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8373261
 * @enablePreview
 * @summary VerifyError: Strict fields not a subset of initial strict instance fields
 */

public class StrictFieldsVerifierErrorTest {
    static value class Val1 {
        int i1;
        int i2;
        int i3;
        int i4;

        public Val1() {
            this.i1 = 0;
            this.i2 = 0;
            this.i3 = 0;
            this.i4 = 0;
        }
    }

    static value class Val2 {
        int i1;
        Val1 val1;

        public Val2(boolean b) {
            this.i1 = 0;
            this.val1 = b ? null : new Val1();
        }
    }

    public static void main(String[] args) {
        Val2 val = new Val2(true);
    }
}
