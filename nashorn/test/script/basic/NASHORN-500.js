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
 * NASHORN-500 :  Using ScriptRuntime.toString for error reporting from runtime is problematic
 *
 * @test
 * @run
 */

var origToString = Object.prototype.toString;
Object.prototype.toString = function () { 
    return this.myStr();
}

try {
    print({});
    fail("#1 toString should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#2 TypeError expected, got " + e);
    }
} finally {
    Object.prototype.toString = origToString;
}

try {
    print({ toString: function() { return this.foo(); } });
    fail("#3 toString should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#4 TypeError expected, got " + e);
    }
}

try {
    var e = new Error();
    e.toString = function() { return this.foo(); }
    var lineNumber = __LINE__;
    throw e;
} catch (e) {
    if (e.nashornException.lineNumber !== lineNumber  + 1) {
        fail("#5 exception throw line number is incorrect");
    }
}

