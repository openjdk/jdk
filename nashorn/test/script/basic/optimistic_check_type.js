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
 * JDK-8036987 : Implement tests that checks static types in the compiled code
 * @test
 * @run
 */

var inspect = Java.type("jdk.nashorn.test.tools.StaticTypeInspector").inspect
var a=3,b,c,z,y;

// Testing arithmetic operators
print(inspect(y*z, "undefined value multiplication by undefined value"))
print(inspect(y/z, "undefined value division by undefined value"))

var x = { a: 2, b:1 }
print(inspect(x.a*x.b, "int multiplication by int"))
print(inspect(x.a/x.b, "int division by int without remainder"))

x.a = 7;
x.b = 2;
print(inspect(x.a/x.b, "int division by int with remainder"))
print(inspect(x.a%x.b, "int modulus by int"))
print(inspect(x.a+x.b, "int plus int"))

x.a = Number.MAX_VALUE;
x.b = Number.MAX_VALUE;
print(inspect(x.a*x.b, "max value multiplication by max value"))

x.a = Number.POSITIVE_INFINITY;
x.b = Number.POSITIVE_INFINITY;
print(inspect(x.a*x.b, "infinity multiplication by infinity"))

x.a = -1;
x.b = Number.POSITIVE_INFINITY;
print(inspect(x.a/x.b, "-1 division by infinity"))

x.a = Number.POSITIVE_INFINITY;
x.b = 0;
print(inspect(x.a/x.b, "infinity division by zero"))

x.a = Number.POSITIVE_INFINITY;
x.b = 'Hello';
print(inspect(x.a/x.b, "infinity division by String"))

// Testing nested functions and return value 
function f() {
    var x = 2, y = 1;
    function g() {
        print(inspect(x, "outer local variable"));
        print(inspect(a, "global variable"));
        print(inspect(x*y, "outer local variable multiplication by outer local variable"));
        print(inspect(a*b, "global variable multiplication by global variable undefined"));
    }
    g()
}
f()

function f1(a,b,c) {
    d = (a+b) * c;
    print(inspect(c, "c"));
    print(inspect(a+b, "a+b"));
    print(inspect(d, "d"));
}
f1()


function f2(a,b) {
    d = a && b;
    print(inspect(d, "d"));
}
f2()