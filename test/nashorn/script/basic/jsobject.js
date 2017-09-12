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
 * JSObject tests
 *
 * @test
 * @run
 */

var m = new javax.script.ScriptEngineManager();
var e = m.getEngineByName("nashorn");

e.eval("obj = { foo:'hello', 0: 'world', func: function(x) { return x.toUpperCase() } } ");
var obj = e.get("obj");


// try various getters
if (obj.foo != 'hello') {
    fail("obj.foo does have expected value");
}

function checkPropGetter(obj, prop, expected) {
    if (obj[prop] != expected) {
        fail(prop + " does not have value: " + expected);
    }
}

checkPropGetter(obj, "foo", "hello");
checkPropGetter(obj, 0, "world");
checkPropGetter(obj, "0", "world");

// try various setters

obj.foo = "HELLO";
if (obj.foo != "HELLO") {
    fail("obj.foo set does not work as expected");
}

function checkPropSetter(obj, prop, newValue) {
    obj[prop] = newValue;
    checkPropGetter(obj, prop, newValue);
}

checkPropSetter(obj, "foo", "NASHORN");
checkPropSetter(obj, 0, "ECMASCRIPT");
checkPropSetter(obj, "0", "CHANGED");

function callFunc(input, expected) {
   if (obj.func(input) != expected) {
       fail("obj.func(..) does not work as expected");
   }
}

callFunc("nashorn", "NASHORN");
callFunc("javascript", "JAVASCRIPT");
callFunc("hello", "HELLO");

var Func = obj.func;

function callWithoutObject(input, expected) {
   if (Func(input) != expected) {
       fail("obj.func(..) does not work as expected");
   }
}

callWithoutObject("nashorn", "NASHORN");
callWithoutObject("javascript", "JAVASCRIPT");
callWithoutObject("hello", "HELLO");
