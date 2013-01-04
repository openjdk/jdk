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
 * Test to check [[Class]] internal property for various standard objects.
 *
 * @test
 * @run
 */

function checkClass(obj, expected) {
    var str = Object.prototype.toString.call(obj);
    if (str != expected) {
        fail("expected " + expected + ", got " + str);
    }
}

// objects
checkClass([2, 23], "[object Array]");
checkClass(new Boolean(true), "[object Boolean]");
checkClass(new Date(0), "[object Date]");
checkClass(new Error(), "[object Error]");
checkClass(new EvalError(), "[object Error]");
if (typeof JSAdapter != 'undefined') {
    checkClass(new JSAdapter({}), "[object JSAdapter]");
}
if (typeof JavaImporter != 'undefined') {
    checkClass(new JavaImporter(java.io), "[object JavaImporter]");
}
checkClass(function() {}, "[object Function]");
checkClass(new Number(42), "[object Number]");
checkClass(new Object(), "[object Object]");
checkClass(new RangeError(), "[object Error]");
checkClass(new ReferenceError(), "[object Error]");
checkClass(/nashorn/, "[object RegExp]");
checkClass(new String('hello'), "[object String]");
checkClass(new SyntaxError(), "[object Error]");
checkClass(new TypeError(), "[object Error]");
checkClass(new URIError(), "[object Error]");

// constructors and prototypes
checkClass(Array, "[object Function]");
checkClass(Array.prototype, "[object Array]");
checkClass(Boolean, "[object Function]");
checkClass(Boolean.prototype, "[object Boolean]");
checkClass(Date, "[object Function]");
checkClass(Date.prototype, "[object Date]");
checkClass(Error, "[object Function]");
checkClass(Error.prototype, "[object Error]");
checkClass(EvalError, "[object Function]");
checkClass(EvalError.prototype, "[object Error]");
checkClass(Function, "[object Function]");
checkClass(Function.prototype, "[object Function]");
if (typeof JSAdapter != 'undefined') {
    checkClass(JSAdapter, "[object Function]");
    checkClass(JSAdapter.prototype, "[object JSAdapter]");
}
if (typeof JavaImporter != 'undefined') {
    checkClass(JavaImporter, "[object Function]");
    checkClass(JavaImporter.prototype, "[object JavaImporter]");
}
checkClass(Number, "[object Function]");
checkClass(Number.prototype, "[object Number]");
checkClass(Object, "[object Function]");
checkClass(Object.prototype, "[object Object]");
checkClass(RangeError, "[object Function]");
checkClass(RangeError.prototype, "[object Error]");
checkClass(ReferenceError, "[object Function]");
checkClass(ReferenceError.prototype, "[object Error]");
checkClass(RegExp, "[object Function]");
checkClass(RegExp.prototype, "[object RegExp]");
checkClass(String, "[object Function]");
checkClass(String.prototype, "[object String]");
checkClass(SyntaxError, "[object Function]");
checkClass(SyntaxError.prototype, "[object Error]");
checkClass(TypeError, "[object Function]");
checkClass(TypeError.prototype, "[object Error]");
checkClass(URIError, "[object Function]");
checkClass(URIError.prototype, "[object Error]");

// misc. objects
checkClass(this, "[object global]");
checkClass(this.prototype, "[object Undefined]");

if (typeof Packages != 'undefined') {
    checkClass(Packages, "[object JavaPackage]");
    checkClass(java, "[object JavaPackage]");
    checkClass(javax, "[object JavaPackage]");
}

if (typeof Java != 'undefined') {
    checkClass(Java, "[object Java]");
    checkClass(Java.prototype, "[object Undefined]");
}

if (typeof Debug != 'undefined') {
    checkClass(Debug, "[object Debug]");
}

checkClass((function() { return arguments; })(), "[object Arguments]");
// strict arguments implementation is different.
checkClass((function() { 'use strict'; return arguments; })(), "[object Arguments]");
checkClass(JSON, "[object JSON]");
checkClass(JSON.prototype, "[object Undefined]");
checkClass(Math, "[object Math]");
checkClass(Math.prototype, "[object Undefined]");
