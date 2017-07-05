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
 * NASHORN-95 :  function length is always -1 when it uses "arguments" regardless of actual formals specified.
 *
 *
 * @test
 * @run
 */

function func1() {
    print(arguments);
}

function func2(x, y) {
    print(arguments);
}

function func3(x, y, z) {
    print(arguments);
}

// more than arglimit number of arguments - so uses varargs in impl..
function func4(a, b, c, d, e, f, g, h, i, j) {}

print("func1.length = " + func1.length);
print("func2.length = " + func2.length);
print("func3.length = " + func3.length);
print("func4.length = " + func4.length);
