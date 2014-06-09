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

/**
 * Check bitwise AND on various (hex) int values.
 *
 * @test
 * @run
 */

print(0x00000000 & 0xffffffff);
print(0x11111111 & 0xffffffff);
print(0x22222222 & 0xffffffff);
print(0x33333333 & 0xffffffff);
print(0x44444444 & 0xffffffff);
print(0x55555555 & 0xffffffff);
print(0x66666666 & 0xffffffff);
print(0x77777777 & 0xffffffff);
print(0x88888888 & 0xffffffff);
print(0x99999999 & 0xffffffff);
print(0xaaaaaaaa & 0xffffffff);
print(0xbbbbbbbb & 0xffffffff);
print(0xcccccccc & 0xffffffff);
print(0xdddddddd & 0xffffffff);
print(0xeeeeeeee & 0xffffffff);
print(0xffffffff & 0xffffffff);

// try non-literal value as well
var a = 0xffffff00;
print(a);
print(a & 0xff); // expected 0
