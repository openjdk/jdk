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
 * JDK-8047764: Indexed or polymorphic set on global affects Object.prototype
 *
 * @test
 * @run
 */

// Same as JDK-8047764.js but running in strict mode
"use strict";

// Test global set operation on properties defined in Object.prototype

Object.defineProperty(Object.prototype, "prop1", { get: function() { return 1; }, set: function(v) { print("setting prop1: " + v); }});
Object.defineProperty(Object.prototype, "prop2", { value: 1, writable: false, configurable: false });

try {
    prop1 = 1;
    print("prop 1: " + prop2);
} catch (e) {
    print(e.name);
}

try {
    prop2 = 2;
    print("prop 2: " + prop2);
} catch (e) {
    print(e.name);
}

// Make sure various ways of setting global toString don't affect Object.prototype.toString

function checkToString() {
    print(global);
    print(Object.prototype);
    print(global.toString === Object.prototype.toString);
    print(objProtoToString === Object.prototype.toString);
}

var global = this;
var objProtoToString = Object.prototype.toString;
global["toString"] = function() { return "global toString 1"; };
checkToString();
global.toString = function() { return "global toString 2"; };
checkToString();
toString = function() { return "global toString 3"; };
checkToString();
