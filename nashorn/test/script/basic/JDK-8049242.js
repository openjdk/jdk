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
 * JDK-8049242: Explicit constructor overload selection should work with StaticClass as well
 *
 * @test
 * @run
 */

// call explicit constructor
print(new (Java.type("java.awt.Color")["(int,int,int)"])(255,0,255));
// print the constructor itself
print(Java.type("java.awt.Color")["(int,int,int)"]);

// store constructor to call later
var Color = Java.type("java.awt.Color")["(int,int,int)"];
// call stored constructor
print(new Color(33, 233, 2))

// check if default constructor works
var obj = new (Java.type("java.lang.Object")["()"])();
if (obj.class != Java.type("java.lang.Object").class) {
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

// garbage signature string
checkIt(function() new (Java.type("java.lang.Object")["()xxxxx"])());
checkIt(function() new (Java.type("java.lang.Object")["("])());
checkIt(function() new (Java.type("java.lang.Object")[")"])());

// call constructor as normal method (without 'new')
checkIt(function() Color());

// try constructor on interface
checkIt(function() new (Java.type("java.lang.Runnable"))["()"]);
checkIt(function() new (Java.type("java.lang.Runnable"))["(int)"]);

// try constructor on abstrace class
try {
    new (Java.type("java.io.InputStream"))["()"];
    throw new Error("should have thrown exception!");
} catch (e) {
    print(e);
}
