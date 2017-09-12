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
 * Exercise all setters on standard objects.
 *
 * @test
 * @run
 */

function checkGetterSetter(obj, expectError) {
    while (obj != undefined && obj != null) {
        var properties = Object.getOwnPropertyNames(obj);
        for (var i in properties) {
            var prop = properties[i];
            try {
                if (!/\d.*/.test(prop)) {
                    eval("obj." + prop + " = " + "obj." + prop + ";");
                }
                obj[prop] = obj[prop];
            } catch (e) {
                if (!expectError || !(e instanceof TypeError)) {
                    fail(e + ": " + obj.toString() +"." + prop, e);
                }
            }
        }
        obj = Object.getPrototypeOf(obj);
    }
}

// objects
checkGetterSetter([2, 23]);
checkGetterSetter(new Boolean(true));
checkGetterSetter(new Date(0));
checkGetterSetter(new Error());
checkGetterSetter(new EvalError());
if (typeof JSAdapter != 'undefined') {
    checkGetterSetter(new JSAdapter({}));
}
if (typeof JavaImporter != 'undefined') {
    checkGetterSetter(new JavaImporter(java.io));
}
checkGetterSetter(function() {});
checkGetterSetter(new Number(42));
checkGetterSetter(new Object());
checkGetterSetter(new RangeError());
checkGetterSetter(new ReferenceError());
checkGetterSetter(/nashorn/);
checkGetterSetter(new String('hello'));
checkGetterSetter(new SyntaxError());
checkGetterSetter(new TypeError());
checkGetterSetter(new URIError());

// constructors and prototypes
checkGetterSetter(Array);
checkGetterSetter(Array.prototype);
checkGetterSetter(Boolean);
checkGetterSetter(Boolean.prototype);
checkGetterSetter(Error);
checkGetterSetter(Error.prototype);
checkGetterSetter(EvalError);
checkGetterSetter(EvalError.prototype);
checkGetterSetter(Function);
checkGetterSetter(Function.prototype);
if (typeof JSAdapter != 'undefined') {
    checkGetterSetter(JSAdapter);
    checkGetterSetter(JSAdapter.prototype);
}
if (typeof JavaImporter != 'undefined') {
    checkGetterSetter(JavaImporter);
    checkGetterSetter(JavaImporter.prototype);
}
checkGetterSetter(Number);
checkGetterSetter(Number.prototype);
checkGetterSetter(Object);
checkGetterSetter(Object.prototype);
checkGetterSetter(RangeError);
checkGetterSetter(RangeError.prototype);
checkGetterSetter(ReferenceError);
checkGetterSetter(ReferenceError.prototype);
checkGetterSetter(RegExp);
checkGetterSetter(RegExp.prototype);
checkGetterSetter(String);
checkGetterSetter(String.prototype);
checkGetterSetter(SyntaxError);
checkGetterSetter(SyntaxError.prototype);
checkGetterSetter(TypeError);
checkGetterSetter(TypeError.prototype);
checkGetterSetter(URIError);
checkGetterSetter(URIError.prototype);

// misc. objects
checkGetterSetter(this);

if (typeof Packages != 'undefined') {
    checkGetterSetter(Packages);
    checkGetterSetter(java);
    checkGetterSetter(javax);
}

if (typeof Java != 'undefined') {
    checkGetterSetter(Java);
    checkGetterSetter(Java.prototype);
}

if (typeof Debug != 'undefined') {
    checkGetterSetter(Debug);
}

checkGetterSetter((function() { return arguments; })());
// TypeError expected on certain property getter/setter for strict arguments
checkGetterSetter((function() { 'use strict'; return arguments; })(), true);
checkGetterSetter(JSON);
checkGetterSetter(JSON.prototype);
checkGetterSetter(Math);
checkGetterSetter(Math.prototype);
