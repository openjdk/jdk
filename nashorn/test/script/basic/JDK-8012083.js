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
 * JDK-8012083 - array literals can only be subject to constant evaluation under very special
 * circumstances.
 *
 * @test
 * @run
 */


var w00t = 17;
print(+[w00t]);

var empty = [];
print(empty == false);

print([] == false);
print([] === false);
print(!![]);

print(~[]);
print(![]);
print(![17]);
print(![17,1,2]);

var one = 1;
var two = 2;
var a1 = [one];
var a2 = [two];
print(+a1 + +a2); //3

var x = 1;
print(+["apa"]);
print(+[]);  //0
print(+[1]); //1
print(+[x]); //1
print(+[1,2,3]); //NaN
var a = [];
var b = [1];
print(a/b);
print(++[[]][+[]]+[+[]]); //10
print(+[] == 0);

var first = [![]+[]][+[]][+[]]+[![]+[]][+[]][+!+[]]+[!+[]+[]][+![]][+![]]+[![]+[]][+[]][+!+[]]+[![]+[]][+[]][+!+[]+!+[]];
var second =(![]+[])[+[]]+(![]+[])[+!+[]]+([![]]+[][[]])[+!+[]+[+[]]]+(![]+[])[!+[]+!+[]];

print(first + " " + second);

