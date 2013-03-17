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
 * Checks for binary addition operator.
 *
 * @test
 * @run
 */

// number addition
var x = Math.PI + Math.E;
print(typeof(x));
print(x);

// string concatenation
x = "hello, " + "world";
print(typeof(x));
print(x);

// string + number
x = "E is " + Math.E;
print(typeof(x));
print(x);

// number + string
x = Math.PI + " is PI";
print(typeof(x));
print(x);

// number + undefined
x = Math.E + undefined;
print(typeof(x));
print(x);

x = undefined + Math.PI;
print(typeof(x));
print(x);

// object with "valueOf" method added to number
var obj = {
    valueOf: function() { return 44.55; }
};

x = 45.66 + obj;
print(typeof(x));
print(x);

x = obj + 3.14;
print(typeof(x));
print(x);

// object with "toString" method added to number
var obj2 = {
    toString: function() { return "obj2.toString"; }
};

x = "hello, " + obj2;
print(typeof(x));
print(x);

x = obj2 + " hello";
print(typeof(x));
print(x);
