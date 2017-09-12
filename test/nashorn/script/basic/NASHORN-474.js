/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * NASHORN-474 : long index support for sparse arrays.
 *
 * @test
 * @run
 */

var a = [];
a[1] = 2;
a[420000000] = 42;
a[-1] = 13;

print([a[420000000], a[1], a[-1]]);
print(a.length);

var b = [];
b[0xfffffffe] = 0xfe;
b[0xffffffff] = 0xff;
b[-1] = -13;
print([b[4294967294], b[4294967295], b[-1]]);
print(b.length);

var c = [,1];
c[0x8ffffff0] = 2;
c[0x8ffffff1] = 3;
c[0x8fffffef] = 4;
c[0] = 5;
c[2] = 6;
c[0x8ffffff3] = 7;
print([c[0],c[1],c[2]] + ";" + [c[0x8fffffef],c[0x8ffffff0],c[0x8ffffff1],c[0x8ffffff2],c[0x8ffffff3]]);
print(c.length);
