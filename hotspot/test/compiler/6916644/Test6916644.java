/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/**
 * @test
 * @bug 6916644
 * @summary C2 compiler crash on x86
 *
 * @run main/othervm -Xcomp -XX:CompileOnly=Test6916644.test Test6916644
 */

public class Test6916644 {
    static int result;
    static int i1;
    static int i2;

    static public void test(double d) {
        result = (d <= 0.0D) ? i1 : i2;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100000; i++) {
            // use an alternating value so the test doesn't always go
            // the same direction.  Otherwise we won't transform it
            // into a cmove.
            test(i & 1);
        }
    }
}
