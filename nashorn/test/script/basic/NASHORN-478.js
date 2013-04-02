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
 * NASHORN-478 : Provide mozilla compatibility script for nashorn
 *
 * @test
 * @run
 */

// compatibility script is loaded using "nashorn:" pseudo URL scheme
try {
    load('nashorn:mozilla_compat.js');
} catch(e) {
}

var obj = {};
if (obj.__proto__ !== Object.prototype) {
    fail("#1 obj.__proto__ read not supported");
}

function fooGetter() { return 3.14; }
function fooSetter(x) {}

Object.defineProperty(obj, "foo", { set: fooSetter, get: fooGetter });
if (!obj.__lookupGetter__ || obj.__lookupGetter__('foo') !== fooGetter) {
    fail("#2 Object.prototype.__lookupGetter__ not supported");
}

if (!obj.__lookupSetter__ || obj.__lookupSetter__('foo') !== fooSetter) {
    fail("#3 Object.prototype.__lookupSetter__ not supported");
}

function barGetter() { return 42; }

if (obj.__defineGetter__) {
    obj.__defineGetter__("bar", barGetter);
}

if (obj.bar !== 42) {
    fail("#4 Object.prototype.__defineGetter__ not supported");
}

var barSetterCalled = false;
function barSetter(x) {
    barSetterCalled = true;
}

if (obj.__defineSetter__) {
    obj.__defineSetter__("bar", barSetter);
}

obj.bar = 'hello';
if (! barSetterCalled) {
    fail("#5 Object.prototype.__defineSetter__ not supported");
}

var obj = { bar: 343, foo : new Boolean(true) };
obj.self = obj;
if (!obj.toSource ||
    obj.toSource() !== '({bar:343, foo:(new Boolean(true)), self:{}})') {
    fail("#6 Object.prototype.toSource method failed");
}

// check String html generation methods
if (!'sss'.anchor || "sss".anchor("foo") !== '<a name="foo">sss</a>') {
    fail("#7 String.prototype.anchor method failed");
}

if (!'hello'.blink || "hello".blink() !== '<blink>hello</blink>') {
    fail("#8 String.prototype.blink method failed");
}

if (!'hello'.fixed || "hello".fixed() !== '<tt>hello</tt>') {
    fail("#9 String.prototype.fixed method failed");
}

if (!'ss'.link || "ss".link('foo') !== '<a href="foo">ss</a>') {
    fail("#10 String.prototype.link method failed");
}

if (typeof importClass != 'function') {
    fail("#11 importClass function not defined");
}

importClass(java.util.HashMap);
if (typeof HashMap != 'function') {
    fail("#12 global.importClass method failed");
}
var m = new HashMap();
m.put('foo', 'bar');
if (m.toString() != '{foo=bar}') {
    fail("#13 global.importClass failed to work");
}

