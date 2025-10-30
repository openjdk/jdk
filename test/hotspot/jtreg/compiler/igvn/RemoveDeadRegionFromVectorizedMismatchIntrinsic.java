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
 * @bug 8359121
 * @summary Region node introduced by ArraysSupport.mismatch must be disconnected,
 *          and not just put aside as dead: when simplifying
 *          Proj -> Region -> If -> ...
 *          into
 *          Proj -> If -> ...
 *               -> Region
 *          the dead Region node must be removed from Proj's outputs.
 * @modules java.base/jdk.internal.util
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.igvn.RemoveDeadRegionFromVectorizedMismatchIntrinsic::test
 *                   compiler.igvn.RemoveDeadRegionFromVectorizedMismatchIntrinsic
 * @run main compiler.igvn.RemoveDeadRegionFromVectorizedMismatchIntrinsic
 */
package compiler.igvn;

import jdk.internal.util.ArraysSupport;

public class RemoveDeadRegionFromVectorizedMismatchIntrinsic {
    public static void main(String[] args) {
        ArraysSupport.mismatch(new int[0], new int[0], 0);  // loads ArraysSupport
        test(new byte[0], new byte[0]);
    }

    public static int test(byte[] a, byte[] b) {
        int i = ArraysSupport.vectorizedMismatch(a, 0, b, 0, 0, 0);
        return i >= 0 ? i : 0;
    }
}
