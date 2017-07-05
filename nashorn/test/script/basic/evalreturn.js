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
 * eval return values test.
 *
 * @test
 * @run 
 */

var x = 0;
print(eval("for(var x = 0; x < 100 ; x++) x;"));
print(eval("if (x == 1) { 'hello' } else { 'world' }"));
print(eval("do { x = 1 } while (false);"));
print(eval("var x = 1"));
print(eval("if (x == 0) { 'hello' }"));
print(eval("if (x == 1) { 'hello' }"));

var i;
var j;
str1 = '';
str2 = '';
x = 1;
y = 2;

for (i in this) {
    str1 += i;
}

eval('for(j in this){\nstr2+=j;\n}');

if (!(str1 === str2)){
    print("scope chain is broken");
    print("str1 = "+str1);
    print("str2 = "+str2);
    print("they should be the same");
    throw "error";
}

print("Scoping OK");

var f = eval("function cookie() { print('sweet and crunchy!'); } function cake() { print('moist and delicious!'); }");
print(f);
f();
var g = eval("function cake() { print('moist and delicious!'); } function cookie() { print('sweet and crunchy!'); }");
print(g);
g();
 



