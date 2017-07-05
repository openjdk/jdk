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
 * JDK-8022731: NativeArguments has wrong implementation of isMapped()
 *
 * @test
 * @run
 */

Object.defineProperty(Object.prototype, "0", {value: "proto"});

function test0(a, b) {
    Object.defineProperty(arguments, "1", {get: function() { return "get" }});
    return arguments[0];
}

function test1(a, b) {
    Object.defineProperty(arguments, "0", {get: function() { return "get" }});
    return a;
}

function test2(a, b) {
    Object.defineProperty(arguments, "0", {value: "value"});
    delete arguments[0];
    return a;
}

function test3(a, b) {
    arguments[1] = "arg1";
    return b;
}

function test4(a, b) {
    b = "b";
    return arguments[1];
}

function test5(a, b) {
    Object.defineProperty(arguments, "0", {value: "value"});
    arguments[0] = "new";
    return a;
}

function test6(a, b) {
    Object.defineProperty(arguments, "0", {value: "value"});
    arguments[0] = "new";
    delete arguments[0];
    return a;
}

function test7(a, b) {
    Object.defineProperty(arguments, "0", {value: "value", writable: false});
    arguments[0] = "new";
    return a;
}

print(test0());
print(test0("p1", "p2"));
print(test1());
print(test1("p1"));
print(test2());
print(test2("p1"));
print(test3());
print(test3(1, 2));
print(test4());
print(test4("p1", "p2"));
print(test5());
print(test5("p1"));
print(test6());
print(test6("p1"));
print(test7());
print(test7("p1"));
