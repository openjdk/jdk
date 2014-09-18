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
 * JDK-8043232: Index selection of overloaded java new constructors
 *
 * @test
 * @run
 */

// call explicit constructor
print(new (java.lang["String(char[],int,int)"])(['a','b', 'c', 'd'], 1, 3));
// print the constructor itself
print(java.lang["String(char[],int,int)"]);

// store constructor to call later
var Color = java.lang["String(char[],int,int)"];
// call stored constructor
print(new Color(['r','r', 'e', 'd'], 1, 3))

// check if default constructor works
var obj = new (java.lang["Object()"])();
if (obj.class != java.lang.Object.class) {
    fail("obj is a java.lang.Object");
}

// expected failure cases.
function checkIt(func) {
    try {
        func();
        throw new Error("should have thrown TypeError");
    } catch(e) {
        if (! (e instanceof TypeError)) {
            fail("Expected TypeError, got " + e);
        }
        print(e);
    }
}

// constructor of a non-existent class
checkIt(function() new (java.lang["NonExistent(String)"])());

// non-existent constructor of an existing class
checkIt(function() new (java.lang["Object(String)"])());

// garbage signature string
checkIt(function() new (java.lang["Object()xxxxx"])());
checkIt(function() new (java.lang["Object("])());
checkIt(function() new (java.lang["Object)"])());

var System = Java.type("java.lang.System");
// try to do 'new' on static method
checkIt(function() new (System.getProperty)("java.version"));

// try to do 'new' on an instance method
var println = System.out.println;
checkIt(function() new println("hello"));

// call constructor as normal method (without 'new')
checkIt(function() Color());

// try constructor on interface
checkIt(function() new java.lang["Runnable()"]);
checkIt(function() new java.lang["Runnable(int)"]);

// try constructor on abstrace class
try {
    new java.io["InputStream()"];
    throw new Error("should have thrown exception!");
} catch (e) {
    print(e);
}
