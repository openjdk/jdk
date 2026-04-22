/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8341834 8343747
 * @summary Replicate node at a VectorCast (ConvL2I) causes superword to fail
 * @run main/othervm -XX:CompileCommand=compileonly,TestReplicateAtConv::test -Xcomp TestReplicateAtConv
 * @run main/othervm -XX:CompileCommand=compileonly,TestReplicateAtConv::test -Xcomp -XX:MaxVectorSize=8 TestReplicateAtConv
 */

public class TestReplicateAtConv {
    public static long val = 0;

    public static void test() {
        int array[] = new int[500];

        for (int i = 0; i < 100; i++) {
            for (long l = 100; l > i; l--) {
                val = 42 + (l + i);
                array[(int)l] = (int)l - (int)val;
            }
        }
    }

    public static void main(String[] args) {
        test();
    }
}
