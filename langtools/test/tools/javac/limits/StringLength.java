/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4309152 4805490
 * @summary Compiler silently generates bytecode that exceeds VM limits
 * @author gafter
 *
 * @compile/fail StringLength.java
 */

class StringLength {
    public static final String l5e0 = "abcde";
    public static final String l1e1 = l5e0 + l5e0;
    public static final String l3e1 = l1e1 + l1e1 + l1e1;
    public static final String l1e2 = l3e1 + l3e1 + l3e1 + l1e1;
    public static final String l5e2 = l1e2 + l1e2 + l1e2 + l1e2 + l1e2;
    public static final String l1e3 = l5e2 + l5e2;
    public static final String l5e3 = l1e3 + l1e3 + l1e3 + l1e3 + l1e3;
    public static final String l1e4 = l5e3 + l5e3;
    public static final String l6e4 = l1e4 + l1e4 + l1e4 + l1e4 + l1e4 + l1e4;

    public static final String l65530 = l6e4 + l5e3 + l5e2 + l3e1;
    public static String X = l65530 + "abcdef"; // length 65536
    public static void main(String[] args) {
        System.out.println(X.length());
    }
}
