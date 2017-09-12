/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Read fully parameter test
 *
 * @test
 * @option -scripting
 * @run
 */

var str = __FILE__;
var first = readFully(str);
print(typeof str);

var str2 = __FILE__.substring(0,5);
var str3 = __FILE__.substring(5);
print(typeof str2);
print(typeof str3);

var cons = str2 + str3;
print(typeof cons);

var second = readFully(cons);

var f = new java.io.File(str);
print(typeof f);
var third = readFully(f);

print(first.length() == second.length());
print(first.length() == third.length());

