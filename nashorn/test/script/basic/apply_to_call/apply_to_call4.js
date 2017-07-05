/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * apply_to_call4.js - various escaping args patterns that prevent the optimization from being applied
 * calling call)
 *
 * @test
 * @run
 */

print("start");

var x = {
    a : 0,
    b : 0,
    c : 0,
    initialize : function(x,y,z) {
    this.a = x;
    this.b = y;
    this.c = z;
    }
};

function f(x) {
    print("this is a black hole - arguments escape");
}

function test() {
    f(arguments);
    x.initialize.apply(x, arguments);
}

test(4711,23,17);
print(x.a);
print(x.b);
print(x.c);

function test2() {
    arguments[0] = 17;
    x.initialize.apply(x, arguments);
}

test2(1,2,3);
print(x.a);
print(x.b);
print(x.c);

function test3() {
    var escape = arguments[0];
    f(escape);
    x.initialize.apply(x, arguments);
}

test3("alpha", "beta", "gamma");
print(x.a);
print(x.b);
print(x.c);

function test4() {
    var escape = arguments.length;
    f(escape);
    x.initialize.apply(x, arguments);
}

test4(1.2, 2.3, 3.4);
print(x.a);
print(x.b);
print(x.c);

function test5() {
    x.initialize.apply(x, arguments, 17);
}

print("test 5 done");
test5(11, 22);
print("a="+typeof(x.a));
print(x.b);
print(x.c);

print("Now it's time for transforms");

function test6() {
    x.initialize.apply(x, arguments);
}

test6(19, 20, 21);
print(x.a);
print(x.b);
print(x.c);

function test7() {
    x.initialize.apply(x, arguments);
}

test7(1, 2.2, 17, 18);
print(x.a);
print(x.b);
print(x.c);

print("Should have transformed");

function test8() {
    var apply = f;
    x.initialize.apply(x, arguments);
}

test8(7,8,9);
print(x.a);
print(x.b);
print(x.c);
