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
 * no_line_numbers.js - make sure that switching off line number generation
 * doesn't break. Otherwise, this is just NASHORN-73, a unit test particularly
 * prone to label bugs in CodeGenerator
 *
 * @test
 * @run
 * @option --debug-lines=false
 */

print("x = " + x);
do {
   break;
   var x;
} while (true);


print("y = " + y);
while (true) {
    break;
    var y;
}

print("z = " + z);
for ( ; ; ) {
   break;
   var z;
   print("THIS SHOULD NEVER BE PRINTED!");
}

while (true) {
    break;
    if (true) { 
	var s; 
    }
}

print("s = "+s);

print("u = "+u);
for ( ; ; ) {
    break;
    while (true) {
	do {
	    var u;
	} while (true);
    }    
}

function terminal() {
    print("r = "+r);
    print("t = "+t);
    for (;;) {
	var r;
	return;
	var t;
	print("THIS SHOULD NEVER BE PRINTED!");
    }
    print("NEITHER SHOULD THIS");
}

terminal();

function terminal2() {
    print("q = "+q);
    for (;;) {
	return;
	print("THIS SHOULD NEVER BE PRINTED!");
    }
    print("NEITHER SHOULD THIS");
}

try { 
    terminal2();
} catch (e) {
    print(e);
}

function scope2() {
    var b = 10;
    print("b = "+b);
}

scope2();

try {
    print("b is = "+b);
}  catch (e) {
    print(e);
}
	

function disp_a() {
    var a = 20;
    print("Value of 'a' inside the function " + a);
}
	
var a = 10;

disp_a();

print("Value of 'a' outside the function " + a);
