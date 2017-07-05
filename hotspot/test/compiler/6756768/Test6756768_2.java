/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

/**
 * @test
 * @bug 6756768
 * @summary C1 generates invalid code
 *
 * @run main/othervm -Xcomp Test6756768_2
 */

class Test6756768_2a {
    static int var = ++Test6756768_2.var;
}

public class Test6756768_2 {
    static int var = 1;

    static Object d2 = null;

    static void test_static_field() {
        int v = var;
        int v2 = Test6756768_2a.var;
        int v3 = var;
        var = v3;
    }

    public static void main(String[] args) {
        var = 1;
        test_static_field();
        if (var != 2) {
            throw new InternalError();
        }
    }
}
