/*
  * Copyright (c) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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
  * @bug 8293978
  * @summary Crash in PhaseIdealLoop::verify_strip_mined_scheduling
  *
  * @run main/othervm -Xbatch TestDuplicateSimpleLoopBackedge
  *
  */

public class TestDuplicateSimpleLoopBackedge {
    static void zero(Byte[] a) {
        for (int e = 0; e < a.length; e++) {
            a[e] = 0;
        }
    }

    int foo(int g) {
        Byte h[] = new Byte[500];
        zero(h);
        short i = 7;
        while (i != 1) {
            i = (short)(i - 3);
        }
        return 0;
    }

    void bar(String[] k) {
        try {
            int l = 5;
            if (l < foo(l)) {
            }
        } catch (Exception m) {
        }
    }

    public static void main(String[] args) {
        TestDuplicateSimpleLoopBackedge n = new TestDuplicateSimpleLoopBackedge();
        for (int i = 0; i < 10000; ++i) {
            n.bar(args);
        }
    }
}
