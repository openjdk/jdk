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
 * JDK-8049086: Minor API convenience functions on "Java" object
 *
 * @test
 * @run
 */

var System = Java.type("java.lang.System");
var out = System.out;
var println = out.println;
var getProperty = System.getProperty;
var File = Java.type("java.io.File")["(String)"];

print("println is java method? " + Java.isJavaMethod(println));
print("println is script function? " + Java.isScriptFunction(println));
print("getProperty is java method? " + Java.isJavaMethod(getProperty));
print("getProperty is script function? " + Java.isScriptFunction(getProperty));
print("File is java method? " + Java.isJavaMethod(File));
print("File is script function? " + Java.isScriptFunction(File));

print("eval is script function? " + Java.isScriptFunction(eval));
print("eval is java method? " + Java.isJavaMethod(eval));
function hello() {}
print("hello is script function? " + Java.isScriptFunction(hello));
print("hello is java method? " + Java.isJavaMethod(hello));

print("out is script object? " + Java.isScriptObject(out));
print("System is script object? " + Java.isScriptObject(System));
print("Object is script object? " + Java.isScriptObject(Object));
print("{} is script object? " + Java.isScriptObject({}));
print("/foo/ is script object? " + Java.isScriptObject(/foo/));

// Java function is anything whose 'typeof' is 'function' but it is not
// a script function! This includes:
// (a) Java methods (b) Java classes (as these respond to new)
// (c) FunctionalInterface objects (d) JSObjects that are 'functions'

print("java.awt.Color is java function? " + Java.isJavaFunction(java.awt.Color));
print("java.lang.Runnable instance is java function? "
    + Java.isJavaFunction(new java.lang.Runnable(function() {})));
print("eval is java function? " + Java.isJavaFunction(eval));
print("println is java function? " + Java.isJavaFunction(println));
print("getProperty is java function? " + Java.isJavaFunction(getProperty));

var JSObject = Java.type("jdk.nashorn.api.scripting.JSObject");
print("callable JSObject is function? " +
    Java.isJavaFunction(new JSObject() {
        isFunction: function() true,
        call: function() {}
    })
);

print("Non callable JSObject is function? " +
    Java.isJavaFunction(new JSObject() {
        isFunction: function() false,
    })
);

// synchronized function
var lock = new java.lang.Object();

print("lock is java object? " + Java.isJavaObject(lock));
print("eval is java object? " + Java.isJavaObject(eval));
print("{} is java object? " + Java.isJavaObject({}));
print("/foo/ is java object? " + Java.isJavaObject(/foo/));
print("[] is java object? " + Java.isJavaObject([]));
print("java.io.File is java object? " + Java.isJavaObject(java.io.File));

// synchornized function checks
Java.synchronized(function() {
    var th = new java.lang.Thread(Java.synchronized(function() {
        print("new thread");
        print("notifying..");
        lock.notifyAll();
    }, lock));
    th.start();
    print("about to wait..");
    lock.wait();
    th.join();
    print("done waiting!");
}, lock)();

// try Mozilla "sync" as well
load("nashorn:mozilla_compat.js");
sync(function() {
    var th = new java.lang.Thread(sync(function() {
        print("new thread");
        print("notifying..");
        lock.notifyAll();
    }, lock));
    th.start();
    print("about to wait..");
    lock.wait();
    th.join();
    print("done waiting!");
}, lock)();

function expectTypeError(func) {
    try {
        func();
        throw new Error("should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("Expected TypeError, got " +e);
        }
        print(e);
    }
}

expectTypeError(function() Java.synchronized(232));
expectTypeError(function() sync(232));
expectTypeError(function() Java.synchronized({}));
expectTypeError(function() sync({}));
expectTypeError(function() Java.synchronized([]));
expectTypeError(function() sync([]));
expectTypeError(function() Java.synchronized("hello"));
expectTypeError(function() sync("hello"));
expectTypeError(function() Java.synchronized(null));
expectTypeError(function() sync(null));
expectTypeError(function() Java.synchronized(undefined));
expectTypeError(function() sync(undefined));
