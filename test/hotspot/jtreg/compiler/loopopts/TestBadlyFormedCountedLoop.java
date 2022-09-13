/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8273115
 * @summary CountedLoopEndNode::stride_con crash in debug build with -XX:+TraceLoopOpts
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+TraceLoopOpts -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileOnly=TestBadlyFormedCountedLoop.main TestBadlyFormedCountedLoop
 */

public class TestBadlyFormedCountedLoop {
    static int y;
    static int[] A = new int[1];

    public static void main(String[] args) {
        for (int i = 0; i < 10; i+=2) {
            int k;
            int j;
            for (j = 1; (j += 3) < 5; ) {
                A[0] = 0;
                for (k = j; k < 5; k++) {
                    y++;
                }
            }
            y = j;
        }
    }

}
