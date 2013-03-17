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
 * NASHORN-418 : When a data descriptor property is changed to an accessor descriptor property, new configurable, enumerable flags are not set
 *
 * @test
 * @run
 */

var obj = { foo: 33 };

var oldDesc = Object.getOwnPropertyDescriptor(obj, "foo");
if (! oldDesc.enumerable) {
    fail("#1 'foo' is not enumerable");
}

if (! oldDesc.configurable) {
    fail("#2 'foo' is not configurable");
}

Object.defineProperty(obj, "foo", {
    enumerable: false, configurable: false,
    get: function() { return 'hello' } });

var newDesc = Object.getOwnPropertyDescriptor(obj, "foo");
if (newDesc.enumerable) {
    fail("#3 'foo' is enumerable");
}

if (newDesc.configurable) {
    fail("#4 'foo' is configurable");
}

var obj2 = {};

Object.defineProperty(obj2, "foo", {
    value : 34, writable: false, configurable: true });

var setterCalled = true;
// descriptor type change, writable is not inherited
Object.defineProperty(obj2, "foo", {
  get: undefined, set: function(x) { setterCalled = true } });

// can still attempt to write
obj2.foo = 44;
if (! setterCalled) {
    fail("#5 obj2.foo setter not called");
}

var obj3 = {};

Object.defineProperty(obj3, "foo", {
  get: undefined, set: function(x) { }, configurable: true });

Object.defineProperty(obj3, "foo", { value: 33 });

var desc = Object.getOwnPropertyDescriptor(obj3, "foo");
if (desc.writable) {
    fail("#6 obj3.foo is writable");
}
