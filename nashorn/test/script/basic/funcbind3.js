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
 * Test the assumptions about bound function prototypes
 *
 * @test
 * @run
 */

function printAssumption(__x__) {
  print(__x__ + ": " + eval(__x__));
}

function f() { }

var b = f.bind(null)

var x = new f()
var y = new b()

printAssumption("x instanceof f")
printAssumption("x instanceof b")
printAssumption("y instanceof f")
printAssumption("y instanceof b")

print("\nChanging prototype\n");

f.prototype=new Object()

printAssumption("x instanceof f")
printAssumption("x instanceof b")
printAssumption("y instanceof f")
printAssumption("y instanceof b")

print("\Bound function prototype\n");

printAssumption("f.hasOwnProperty('prototype')")
printAssumption("b.hasOwnProperty('prototype')")
