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
 * NASHORN-234 : Inherited accessor property is not handled as per the spec.
 *
 * @test
 * @run
 */

function MyCons() {};

var setterCalled = false;
Object.defineProperty(MyCons.prototype, 'foo', {
    set: function(x) { setterCalled = true; },
    get: function() { return  42; }
});

var myObj = new MyCons();

myObj.foo = 34; // has to call setter for inherited 'foo'
if (myObj.hasOwnProperty("foo")) {
    fail("'foo' is own property of myObj");
}
if (! setterCalled) {
    fail("inherited setter not called");
}

// Now try 'dynamic' property access.
setterCalled = false;
var propName = "foo";
myObj[propName] = 42; // has to call setter for inherited 'foo'
if (myObj.hasOwnProperty("foo")) {
    fail("'foo' is own property of myObj");
}
if (! setterCalled) {
    fail("inherited setter not called");
}
