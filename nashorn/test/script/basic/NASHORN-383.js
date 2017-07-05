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
 * NASHORN-383 : JSAdapter __get__ is not called from a callsite exercised earlier by an array object
 *
 * @test
 * @run
 */

function func(obj) {
    return obj[0];
}

var arr = [3, 4];
if (arr[0] !== func(arr)) {
    fail("#1 func does not return array element");
}

var getterCalled = false;
var res = func(new JSAdapter() {
   __get__: function(name) {
       getterCalled = true;
       return name;
   }
});

if (! getterCalled) {
    fail("#2 __get__ not called");
}

if (res !== 0) {
    fail("#3 __get__ did not return '0'");
}
