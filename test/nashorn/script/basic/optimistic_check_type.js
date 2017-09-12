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
 * @test
 * @bug 8036987, 8037572
 * @summary Implement tests that checks static types in the compiled code
 * @option --optimistic-types=true
 * @run
 */

var inspect = Java.type("jdk.nashorn.test.tools.StaticTypeInspector").inspect
var a=3, b=2.3, c=true, d;
var x = { a: 2, b:0, c:undefined}
var trees = new Array("redwood", "bay", "cedar", "oak");

// Testing conditional operator
print(inspect("" ? b : x.a, "ternary operator"))
var b1 = b;
print(inspect(x.b ? b1 : x.a, "ternary operator"))
var b2 = b;
print(inspect(c ? b2 : a, "ternary operator"))
var b3 = b;
print(inspect(!c ? b3 : a, "ternary operator"))
var b4 = b;
print(inspect(d ? b4 : x.c, "ternary operator"))
print(inspect(x.c ? a : c, "ternary operator"))
print(inspect(c ? d : a, "ternary operator"))
var b5 = b;
print(inspect(c ? +a : b5, "ternary operator"))

// Testing format methods
print(inspect(b.toFixed(2), "global double toFixed()"))
print(inspect(b.toPrecision(2)/1, "global double toPrecision() divided by 1"))
print(inspect(b.toExponential(2), "global double toExponential()"))

// Testing arrays
print(inspect(trees[1], "member object"))
trees[1] = undefined;
print(inspect(trees[1], "member undefined"))
var b6=b;
print(inspect(1 in trees ? b6 : a, "conditional on array member"))
delete trees[2]
var b7=b;
print(inspect(2 in trees ? b7 : a, "conditional on array member"))
print(inspect(3 in trees ? trees[2]="bay" : a, "conditional on array member"))
var b8=b;
print(inspect("oak" in trees ? b8 : a, "conditional on array member"))

// Testing nested functions and return value
function f1() {
    var x = 2, y = 1;
    function g() {
        print(inspect(x, "outer local variable"));
        print(inspect(a, "global variable"));
        print(inspect(x*y, "outer local int multiplication by outer local int"));
        print(inspect(a*d, "global int multiplication by global undefined"));
    }
    g()
}
f1()

function f2(a,b,c) {
    g = (a+b) * c;
    print(inspect(c, "local undefined"));
    print(inspect(a+b, "local undefined addition local undefined"));
    print(inspect(g, "local undefined multiplication by undefined"));
}
f2()

function f3(a,b) {
    g = a && b;
    print(inspect(g, "local undefined AND local undefined"));
    print(inspect(c||g, "global true OR local undefined"));
}
f3()

function f4() {
    var x = true, y = 0;
    function g() {
        print(inspect(x+y, "outer local true addition local int"));
        print(inspect(a+x, "global int addition outer local true"));
        print(inspect(x*y, "outer local true multiplication by outer local int"));
        print(inspect(y*d, "outer local int multiplication by global undefined"));
    }
    g()
}
f4()
