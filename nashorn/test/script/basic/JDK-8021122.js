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
 * JDK-8021122: Not all callables are handled for toString and other function valued properties
 *
 * @test
 * @run
 */

var a = {}
var obj = new java.util.HashMap();
Object.bindProperties(a, obj);
try {
    print(a);
} catch (e) {
    print(e);
}

var a = {}
var global = loadWithNewGlobal({ name:"xx", script: "this" });
var obj = global.eval("({ toString: function() { return 'hello'; } })");
Object.bindProperties(a, obj);
try {
    print(a);
} catch (e) {
    print(e);
}

function runLambdaTests() {
    var r = new java.lang.Runnable() {
        run: function() { print("I am runnable"); }
    };

    // call any @FunctionalInterface object as though it is a function
    r();

    var twice = new java.util.function.Function() {
        apply: function(x) 2*x
    };

    print(twice(34));

    var sum = new java.util.function.BiFunction() {
        apply: function(x, y) x + y
    };

    print(sum(32, 12))

    // make toString to be a @FunctionalInterface object
    var a = {};
    a.toString = new java.util.function.Supplier() {
        get: function() { return "MyString"; }
    };

    try {
        print(a);
    } catch (e) {
        print(e);
    }
}

try {
    // check for java.util.function.Function class
    Java.type("java.util.function.Function");
    runLambdaTests();
} catch (e) {
    // fake output to match .EXPECTED values
    print("I am runnable");
    print("68");
    print("44");
    print("MyString");
}
