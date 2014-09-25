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
 * Simple benchmark to measure push/pop specialized method performance
 */

var a = [];

var RESULT = 15;

function bench() {
    var sum = 0;
    for (var i=0;i<10;i++) {
	a.push(i);
    }
    for (var i=0;i<10;i++) {
	sum |= a.pop();
    }
    return sum;
}

function runbench() {
    var sum = 0;
    for (var iters = 0; iters<1e8; iters++) {
	sum |= bench();
    }
    return sum;
}

var d = new Date;
var res = runbench();
print((new Date - d) + " ms");
print();
if (res != RESULT) {
    print("ERROR: Wrong result - should be " + RESULT);
} else {
    print("Verified OK - result is correct");
}
