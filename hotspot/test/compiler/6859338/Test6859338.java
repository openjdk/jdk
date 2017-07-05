/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6859338
 * @summary Assertion failure in sharedRuntime.cpp
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions  -XX:-InlineObjectHash -Xbatch -XX:-ProfileInterpreter Test6859338
 */

public class Test6859338 {
    static Object[] o = new Object[] { new Object(), null };
    public static void main(String[] args) {
        int total = 0;
        try {
            // Exercise the implicit null check in the unverified entry point
            for (int i = 0; i < 40000; i++) {
                int limit = o.length;
                if (i < 20000) limit = 1;
                for (int j = 0; j < limit; j++) {
                    total += o[j].hashCode();
                }
            }

        } catch (NullPointerException e) {
            // this is expected.  A true failure causes a crash
        }
    }
}
