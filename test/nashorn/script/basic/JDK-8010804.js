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
 * JDK-8010804: Review long and integer usage conventions
 *
 * @test
 * @run
 */

var x = [];
print(x.length);
x[4294967294] = 1;
print(x.length);
x[4294967295] = 1;
print(x.length);
print(x.slice(4294967293).length);
print(x.slice(4294967294).length);
print(x.slice(4294967295).length);
print(x.slice(4294967296).length);

print(x.slice(-4294967293).length);
print(x.slice(-4294967294).length);
print(x.slice(-4294967295).length);
print(x.slice(-4294967296).length);

print(x.slice(0, 4294967293).length);
print(x.slice(0, 4294967294).length);
print(x.slice(0, 4294967295).length);
print(x.slice(0, 4294967296).length);

print(x.slice(0, -4294967293).length);
print(x.slice(0, -4294967294).length);
print(x.slice(0, -4294967295).length);
print(x.slice(0, -4294967296).length);

print(x.slice(9223371036854775807).length);
print(x.slice(9223372036854775807).length);
print(x.slice(9223373036854775807).length);
print(x.slice(9223374036854775807).length);

print(x.slice(-9223371036854775807).length);
print(x.slice(-9223372036854775807).length);
print(x.slice(-9223373036854775807).length);
print(x.slice(-9223374036854775807).length);

print(x.slice(-9223371036854775807, 1).length);
print(x.slice(-9223372036854775807, 1).length);
print(x.slice(-9223373036854775807, 1).length);
print(x.slice(-9223374036854775807, 1).length);

print(x.slice(-9223371036854775807, -1).length);
print(x.slice(-9223372036854775807, -1).length);
print(x.slice(-9223373036854775807, -1).length);
print(x.slice(-9223374036854775807, -1).length);

print(x.slice(Infinity).length);
print(x.slice(Infinity, Infinity).length);
print(x.slice(Infinity, -Infinity).length);
print(x.slice(-Infinity).length);
print(x.slice(-Infinity, Infinity).length);
print(x.slice(-Infinity, -Infinity).length);

var d = new Date();
d.setYear(Infinity);
print(d);
