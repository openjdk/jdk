/*
 * Copyright (c) 2015 SAP SE. All rights reserved.
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

/* @test
 * @bug 8139258
 * @summary Regression test for 8139258 which failed to properly pass float args
 *          to a jni function on ppc64le.
 * @run main/othervm -Xint Test15FloatJNIArgs
 * @run main/othervm -XX:+TieredCompilation -Xcomp Test15FloatJNIArgs
 * @run main/othervm -XX:-TieredCompilation -Xcomp Test15FloatJNIArgs
 */

public class Test15FloatJNIArgs {
    static {
        try {
            System.loadLibrary("Test15FloatJNIArgs");
        } catch (UnsatisfiedLinkError e) {
            System.out.println("could not load native lib: " + e);
        }
    }

    public static native float add15floats(
        float f1, float f2, float f3, float f4,
        float f5, float f6, float f7, float f8,
        float f9, float f10, float f11, float f12,
        float f13, float f14, float f15);

    static void test() throws Exception {
        float sum = Test15FloatJNIArgs.add15floats(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                                                   1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
        if (sum != 15.0f) {
            throw new Error("Passed 15 times 1.0f to jni function which didn't add them properly: " + sum);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 200; ++i) {
            test();
        }
    }
}
