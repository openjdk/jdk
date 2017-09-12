/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8035312_2 - length setter and iterators
 *
 * @test
 * @run
 */

"use strict"

function printArray(a,n) {
    print("PRINT_ARRAY CALLED: length = " + a.length);
    print();

    print("INDEXED");
    for (var x = 0; x<n; x++) {
	print("\t" + x + ":"+a[x]);
    }
    print("KEYS");
    for (var key in a) {
	print("\t" + key + ";" + a[key]);
    }
}

var b = [1,2,3];

Object.defineProperty(b, "length", { writable: false });
var high = 8;
b[high] = high;

printArray(b, high + 5);

var c = [1,2,3];
c[high] = high;
print();
print("element[" + high + "]: " + c.length + " " + c[high]);
print("Resetting length");
c.length = 3;
print("element[" + high + "]: " + c.length + " " + c[high]);
print();

printArray(c, high + 5);
print();
