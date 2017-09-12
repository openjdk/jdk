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
 * Test for in
 *
 * @test
 * @run
 */

var a = [10, 20, 30, 40];
var o = {a: 100, b: 200, c: 300};
var j = new java.util.ArrayList();
j.add("apple");
j.add("bear");
j.add("car");
var ja = j.toArray();
var s = "apple,bear,car".split(",");

for (i in a) print(i, a[i]);
for each (i in a) print(i);
for (i in o) print(i, o[i]);
for each (i in j) print(i);
for (i in ja) print(i, ja[i]);
for each (i in ja) print(i);
for (i in s) print(i, s[i]);
for each (i in s) print(i);

// 'each' is a contextual keyword. Ok to use as identifier elsewhere..
var each = "This is each";
print(each);
