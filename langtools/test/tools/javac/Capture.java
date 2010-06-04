/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4441338 4994508
 * @summary Captured variable synthetic parameters should be passed before explicit params.
 * @author gafter
 *
 * @compile -source 1.4 -target 1.4 Capture.java
 * @run main Capture
 */

public class Capture {
    final int k;
    Capture(int n) {
        k = n;
    }
    public static void main(String args[]) {
        final int j;
        int k1 = new Capture(2 + (j=3)){
                         int get () {return k+j;}
                     }.get();
        if (k1 != 8) throw new Error("k1 = " + k1);
    }
}
